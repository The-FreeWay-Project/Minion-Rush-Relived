package de.freeway.mrr.patcher.pipeline

import java.io.File
import java.io.IOException

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PatchApplierTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun fixture(): Pair<PatchApplier, PatchWorkspace> {
        val ws = PatchWorkspace(File(tmp.root, "patch-workspace")).ensure()
        return PatchApplier(ws) to ws
    }

    @Test
    fun appliesVerifiedFileByMovingItIntoApplied() {
        val (applier, ws) = fixture()
        val verified = ws.verifiedFile("packs/demo/core.bin")
        verified.parentFile.mkdirs()
        verified.writeText("new content")

        val target = applier.apply(verified, "packs/demo/core.bin")

        assertEquals(File(ws.appliedDir, "packs/demo/core.bin").absoluteFile, target.absoluteFile)
        assertEquals("new content", target.readText())
        assertFalse(verified.exists())
    }

    @Test
    fun backsUpPreviouslyAppliedFileBeforeOverwriting() {
        val (applier, ws) = fixture()
        val applied = ws.appliedFile("packs/demo/core.bin")
        applied.parentFile.mkdirs()
        applied.writeText("old content")
        val verified = ws.verifiedFile("packs/demo/core.bin")
        verified.parentFile.mkdirs()
        verified.writeText("new content")

        applier.apply(verified, "packs/demo/core.bin")

        assertEquals("new content", applied.readText())
        val backup = ws.backupFile("packs/demo/core.bin")
        assertTrue(backup.isFile)
        assertEquals("old content", backup.readText())
        assertFalse(verified.exists())
    }

    @Test
    fun replacingTwiceKeepsTheMostRecentPreviousVersionAsBackup() {
        val (applier, ws) = fixture()
        val applied = ws.appliedFile("f.bin")
        applied.parentFile.mkdirs()
        applied.writeText("v1")
        val verified = ws.verifiedFile("f.bin")
        verified.parentFile.mkdirs()

        verified.writeText("v2")
        applier.apply(verified, "f.bin")
        verified.writeText("v3")
        applier.apply(verified, "f.bin")

        assertEquals("v3", applied.readText())
        assertEquals("v2", ws.backupFile("f.bin").readText())
    }

    @Test
    fun rejectsUnsafeRelativePath() {
        val (applier, _) = fixture()
        val outside = tmp.newFile("outside.bin")
        outside.writeText("must not move")
        try {
            applier.apply(outside, "../outside.bin")
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Unsafe patch path"))
        }
        assertTrue(outside.exists())
    }

    @Test
    fun rejectsMissingVerifiedFile() {
        val (applier, ws) = fixture()
        try {
            applier.apply(ws.verifiedFile("never-downloaded.bin"), "never-downloaded.bin")
            fail("expected IOException")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("never-downloaded.bin"))
        }
    }
}
