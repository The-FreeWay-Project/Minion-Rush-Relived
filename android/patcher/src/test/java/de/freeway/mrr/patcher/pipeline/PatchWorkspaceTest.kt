package de.freeway.mrr.patcher.pipeline

import java.io.File

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PatchWorkspaceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun workspace() = PatchWorkspace(File(tmp.root, "patch-workspace"))

    @Test
    fun ensureCreatesAllFourDirectories() {
        val ws = workspace().ensure()
        assertTrue(ws.incomingDir.isDirectory)
        assertTrue(ws.verifiedDir.isDirectory)
        assertTrue(ws.appliedDir.isDirectory)
        assertTrue(ws.backupDir.isDirectory)
        assertEquals("incoming", ws.incomingDir.name)
        assertEquals("verified", ws.verifiedDir.name)
        assertEquals("applied", ws.appliedDir.name)
        assertEquals("backup", ws.backupDir.name)
    }

    @Test
    fun nestsFileHandlesInsideTheirDirectories() {
        val ws = workspace().ensure()
        val part = ws.incomingPartFile("packs/demo/core.bin")
        assertEquals("core.bin.part", part.name)
        assertTrue(part.path.contains("incoming"))
        assertTrue(part.path.contains("packs${File.separator}demo"))
        assertTrue(ws.verifiedFile("a/b.bin").path.contains("verified"))
        assertTrue(ws.appliedFile("a/b.bin").path.contains("applied"))
        assertEquals("b.bin.bak", ws.backupFile("a/b.bin").name)
    }

    @Test
    fun rejectsUnsafePathsEverywhere() {
        val ws = workspace().ensure()
        listOf("../evil", "a/../evil", "./evil", "", "a\\b", "/abs", "a//b").forEach { bad ->
            listOf(
                runCatching { ws.incomingPartFile(bad) },
                runCatching { ws.verifiedFile(bad) },
                runCatching { ws.appliedFile(bad) },
                runCatching { ws.backupFile(bad) },
            ).forEach { result ->
                assertTrue("expected rejection for '$bad'", result.isFailure)
                assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            }
        }
    }

    @Test
    fun clearIncomingRemovesPartsButKeepsOtherDirs() {
        val ws = workspace().ensure()
        val part = ws.incomingPartFile("packs/demo/core.bin")
        part.parentFile.mkdirs()
        part.writeText("partial")
        val applied = ws.appliedFile("keep.bin")
        applied.parentFile.mkdirs()
        applied.writeText("keep me")

        ws.clearIncoming()

        assertFalse(part.exists())
        assertTrue(ws.incomingDir.isDirectory)
        assertTrue(applied.exists())
    }

    @Test
    fun isSafeMatchesTheStrictRules() {
        assertTrue(PatchPaths.isSafe("a/b/c.bin"))
        assertTrue(PatchPaths.isSafe("core.bin"))
        assertFalse(PatchPaths.isSafe(null))
        assertFalse(PatchPaths.isSafe(""))
        assertFalse(PatchPaths.isSafe("   "))
        assertFalse(PatchPaths.isSafe("/a"))
        assertFalse(PatchPaths.isSafe("a//b"))
        assertFalse(PatchPaths.isSafe("./a"))
        assertFalse(PatchPaths.isSafe("a/./b"))
        assertFalse(PatchPaths.isSafe("../a"))
        assertFalse(PatchPaths.isSafe("a/.."))
        assertFalse(PatchPaths.isSafe("a\\b"))
    }

    @Test
    fun requireSafeNamesTheOffender() {
        try {
            PatchPaths.requireSafe("../evil")
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("../evil"))
        }
    }
}
