package de.freeway.mrr.patcher.pipeline

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

import kotlinx.coroutines.runBlocking

import de.freeway.mrr.patcher.api.PatchFile
import de.freeway.mrr.patcher.api.PatchManifest
import de.freeway.mrr.patcher.data.PatchApiException
import de.freeway.mrr.patcher.data.PatchRepository
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PatchManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private class ScriptedRepository : PatchRepository {
        val queue = mutableListOf<Any>()
        var lastInstalled: String? = null

        fun enqueueManifest(manifest: PatchManifest) {
            queue += manifest
        }

        fun enqueueError(error: Exception) {
            queue += error
        }

        override suspend fun fetchManifest(installedVersion: String): PatchManifest {
            lastInstalled = installedVersion
            val next = queue.removeAt(0)
            if (next is Exception) throw next
            @Suppress("UNCHECKED_CAST")
            return next as PatchManifest
        }
    }

    private fun manifest(
        version: String = "A1.1.0",
        message: String = "Update to A1.1.0 available.",
        files: List<PatchFile> = emptyList(),
    ) = PatchManifest(
        version = version,
        channel = "stable",
        platform = "android",
        serverVersion = "0.3.0",
        message = message,
        files = files,
    )

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { String.format(Locale.ROOT, "%02x", it) }

    private fun workspace() = PatchWorkspace(File(tmp.root, "patch-workspace"))

    private fun manager(
        repository: PatchRepository,
        ws: PatchWorkspace? = null,
        baseUrl: String = "",
    ) = PatchManager(
        repository = repository,
        installedVersion = "A1.0.0",
        baseUrl = baseUrl,
        downloader = HttpPatchDownloader(),
        applier = ws?.let { PatchApplier(it) },
    )

    @Test
    fun checkWithMatchingManifestGoesUpToDateAndOnline() {
        val repo = ScriptedRepository().apply {
            enqueueManifest(manifest(version = "A1.0.0", message = "MRR Patcher is up to date."))
            enqueueManifest(manifest(version = "A1.0.0", message = "MRR Patcher is up to date."))
        }
        val manager = manager(repo)

        runBlocking {
            manager.check()
            manager.check()
        }

        val state = manager.state.value
        assertEquals(PatchState.UP_TO_DATE, state.state)
        assertEquals("MRR Patcher is up to date.", state.message)
        assertEquals("0.3.0", state.serverVersion)
        assertEquals(true, state.serverOnline)
        assertNull(state.availableVersion)
        assertEquals("A1.0.0", repo.lastInstalled)
    }

    @Test
    fun checkWithNewerManifestGoesUpdateAvailable() {
        val repo = ScriptedRepository().apply { enqueueManifest(manifest()) }
        val manager = manager(repo)

        runBlocking { manager.check() }

        val state = manager.state.value
        assertEquals(PatchState.UPDATE_AVAILABLE, state.state)
        assertEquals("Update to A1.1.0 available.", state.message)
        assertEquals("A1.1.0", state.availableVersion)
        assertEquals(true, state.serverOnline)
    }

    @Test
    fun networkFailureFailsAndMarksServerOffline() {
        val repo = ScriptedRepository().apply {
            enqueueError(PatchApiException.Network(IOException("refused")))
        }
        val manager = manager(repo)

        runBlocking { manager.check() }

        val state = manager.state.value
        assertEquals(PatchState.FAILED, state.state)
        assertTrue(state.message.contains("erreichbar"))
        assertEquals(false, state.serverOnline)
    }

    @Test
    fun serverErrorFailsAndIsRetriableByANewCheck() {
        val repo = ScriptedRepository().apply {
            enqueueError(PatchApiException.Server(500))
            enqueueManifest(manifest(version = "A1.0.0", message = "MRR Patcher is up to date."))
        }
        val manager = manager(repo)

        runBlocking { manager.check() }
        assertEquals(PatchState.FAILED, manager.state.value.state)
        assertTrue(manager.state.value.message.contains("500"))

        runBlocking { manager.check() }
        assertEquals(PatchState.UP_TO_DATE, manager.state.value.state)
        assertEquals(true, manager.state.value.serverOnline)
    }

    @Test
    fun unparsableManifestVersionFailsWithManifestMessage() {
        val repo = ScriptedRepository().apply { enqueueManifest(manifest(version = "banana")) }
        val manager = manager(repo)

        runBlocking { manager.check() }

        assertEquals(PatchState.FAILED, manager.state.value.state)
        assertTrue(manager.state.value.message.contains("Manifest"))
    }

    @Test
    fun fullRunDownloadsVerifiesAndAppliesThroughRelativeUrl() {
        val content = "patched-content-v2"
        server.enqueue(MockResponse().setBody(content))
        val ws = workspace()
        val repo = ScriptedRepository().apply {
            enqueueManifest(
                manifest(
                    files = listOf(
                        PatchFile(
                            path = "packs/demo/core.bin",
                            url = "files/core.bin",
                            size = content.length.toLong(),
                            sha256 = sha256(content),
                        ),
                    ),
                ),
            )
        }
        val manager = manager(repo, ws, baseUrl = server.url("/").toString())

        runBlocking {
            manager.check()
            manager.runPatch()
        }

        val state = manager.state.value
        assertEquals(PatchState.SUCCESS, state.state)
        assertEquals("Patch erfolgreich angewendet.", state.message)
        assertNull(state.progress)
        assertEquals(content, File(ws.appliedDir, "packs/demo/core.bin").readText())
        assertTrue(ws.incomingDir.walkTopDown().none { it.isFile })
        assertTrue(ws.verifiedDir.walkTopDown().none { it.isFile })
        assertTrue(ws.backupDir.walkTopDown().none { it.isFile })
    }

    @Test
    fun shaMismatchFailsAndClearsIncoming() {
        val content = "served-but-wrong-hash"
        server.enqueue(MockResponse().setBody(content))
        val ws = workspace()
        val repo = ScriptedRepository().apply {
            enqueueManifest(
                manifest(
                    files = listOf(
                        PatchFile(
                            path = "packs/demo/core.bin",
                            url = server.url("/files/core.bin").toString(),
                            size = content.length.toLong(),
                            sha256 = "0".repeat(64),
                        ),
                    ),
                ),
            )
        }
        val manager = manager(repo, ws)

        runBlocking {
            manager.check()
            manager.runPatch()
        }

        assertEquals(PatchState.FAILED, manager.state.value.state)
        assertTrue(manager.state.value.message.contains("SHA-256 mismatch"))
        assertTrue(ws.incomingDir.walkTopDown().none { it.isFile })
        assertFalse(File(ws.appliedDir, "packs/demo/core.bin").exists())
        assertNull(manager.state.value.progress)
    }

    @Test
    fun emptyFileListFailsInsteadOfSilentlySucceeding() {
        val ws = workspace()
        val repo = ScriptedRepository().apply { enqueueManifest(manifest(files = emptyList())) }
        val manager = manager(repo, ws)

        runBlocking {
            manager.check()
            manager.runPatch()
        }

        assertEquals(PatchState.FAILED, manager.state.value.state)
        assertTrue(manager.state.value.message.contains("keine Patch-Dateien"))
    }

    @Test
    fun interruptedDownloadFailsWithoutLeftovers() {
        server.enqueue(
            MockResponse()
                .setBody("partial")
                .setHeader("Content-Length", "1048576")
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END)
        )
        val ws = workspace()
        val content = "interrupted-content"
        val repo = ScriptedRepository().apply {
            enqueueManifest(
                manifest(
                    files = listOf(
                        PatchFile(
                            path = "packs/demo/core.bin",
                            url = server.url("/files/core.bin").toString(),
                            size = content.length.toLong(),
                            sha256 = sha256(content),
                        ),
                    ),
                ),
            )
        }
        val manager = manager(repo, ws)

        runBlocking {
            manager.check()
            manager.runPatch()
        }

        assertEquals(PatchState.FAILED, manager.state.value.state)
        assertTrue(manager.state.value.message.contains("erreichbar"))
        assertTrue(ws.incomingDir.walkTopDown().none { it.isFile })
        assertTrue(ws.verifiedDir.walkTopDown().none { it.isFile })
        assertFalse(File(ws.appliedDir, "packs/demo/core.bin").exists())
    }

    @Test
    fun unsafeManifestPathFails() {
        val ws = workspace()
        val repo = ScriptedRepository().apply {
            enqueueManifest(
                manifest(
                    files = listOf(
                        PatchFile(
                            path = "../evil.bin",
                            url = server.url("/files/evil.bin").toString(),
                            size = 4,
                            sha256 = "0".repeat(64),
                        ),
                    ),
                ),
            )
        }
        val manager = manager(repo, ws)

        runBlocking {
            manager.check()
            manager.runPatch()
        }

        assertEquals(PatchState.FAILED, manager.state.value.state)
        assertTrue(manager.state.value.message.contains("Unsafe patch path"))
        assertFalse(File(tmp.root, "evil.bin").exists())
    }

    @Test
    fun verifyOnlyRunFromUpToDateGoesStraightToSuccess() {
        val repo = ScriptedRepository().apply {
            enqueueManifest(manifest(version = "A1.0.0", message = "MRR Patcher is up to date."))
        }
        val manager = manager(repo)

        runBlocking {
            manager.check()
            manager.runPatch()
        }

        val state = manager.state.value
        assertEquals(PatchState.SUCCESS, state.state)
        assertEquals("Already up to date.", state.message)
        assertNull(state.progress)
    }

    @Test
    fun runPatchWithoutCheckedManifestRechecksFirst() {
        val repo = ScriptedRepository().apply { enqueueManifest(manifest(files = emptyList())) }
        val manager = manager(repo)

        runBlocking { manager.runPatch() }

        assertEquals(PatchState.FAILED, manager.state.value.state)
        assertTrue(manager.state.value.message.contains("keine Patch-Dateien"))
        assertEquals("A1.0.0", repo.lastInstalled)
    }
}

