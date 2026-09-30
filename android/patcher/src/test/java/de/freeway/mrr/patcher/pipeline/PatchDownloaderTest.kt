package de.freeway.mrr.patcher.pipeline

import java.io.File

import kotlinx.coroutines.runBlocking

import de.freeway.mrr.patcher.data.PatchApiException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PatchDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val downloader = HttpPatchDownloader()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun target() = File(tmp.root, "nested/pack.bin")

    @Test
    fun downloadsAndReportsProgress() {
        val body = "hello-patch-content"
        server.enqueue(MockResponse().setBody(body))
        val target = target()
        val events = mutableListOf<Pair<Long, Long>>()

        runBlocking {
            downloader.download(server.url("/files/pack.bin").toString(), target) { done, total ->
                events += done to total
            }
        }

        assertEquals(body, target.readText())
        assertTrue(events.isNotEmpty())
        assertEquals(body.length.toLong(), events.last().first)
        assertEquals(body.length.toLong(), events.last().second)
    }

    @Test
    fun httpErrorBecomesServerExceptionAndDeletesTarget() {
        server.enqueue(MockResponse().setResponseCode(404))
        val target = target()

        try {
            runBlocking {
                downloader.download(server.url("/files/missing.bin").toString(), target) { _, _ -> }
            }
            fail("expected PatchApiException.Server")
        } catch (expected: PatchApiException.Server) {
            assertEquals(404, expected.status)
        }
        assertFalse(target.exists())
    }

    @Test
    fun unreachableServerBecomesNetworkException() {
        val url = server.url("/files/pack.bin").toString()
        server.shutdown()

        try {
            runBlocking {
                downloader.download(url, target()) { _, _ -> }
            }
            fail("expected PatchApiException.Network")
        } catch (expected: PatchApiException.Network) {
            assertTrue(expected.message!!.contains("erreichbar"))
        }
        assertFalse(target().exists())
    }

    @Test
    fun interruptedDownloadThrowsNetworkAndLeavesNoTarget() {
        server.enqueue(
            MockResponse()
                .setBody("partial")
                .setHeader("Content-Length", "1048576")
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END)
        )
        val target = target()

        try {
            runBlocking {
                downloader.download(server.url("/files/pack.bin").toString(), target) { _, _ -> }
            }
            fail("expected PatchApiException.Network")
        } catch (expected: PatchApiException.Network) {
            // The exception crosses coroutine boundaries, so kotlinx may put a
            // recovered copy on top; assert the user-visible behaviour instead
            // of the exact cause type.
            assertTrue(expected.message!!.contains("erreichbar"))
        }
        assertFalse(target.exists())
    }

    @Test
    fun invalidUrlBecomesNetworkException() {
        try {
            runBlocking {
                downloader.download("not a url", target()) { _, _ -> }
            }
            fail("expected PatchApiException.Network")
        } catch (expected: PatchApiException.Network) {
            assertTrue(expected.message!!.contains("erreichbar"))
        }
        assertFalse(target().exists())
    }
}







