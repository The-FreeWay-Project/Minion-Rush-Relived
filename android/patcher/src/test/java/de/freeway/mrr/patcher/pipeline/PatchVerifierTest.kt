package de.freeway.mrr.patcher.pipeline

import java.io.File
import java.nio.charset.StandardCharsets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PatchVerifierTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val verifier = PatchVerifier()

    private fun file(name: String, content: String): File =
        tmp.newFile(name).apply { writeBytes(content.toByteArray(StandardCharsets.UTF_8)) }

    @Test
    fun matchesKnownSha256Vector() {
        val abc = file("abc.txt", "abc")
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            verifier.sha256Hex(abc),
        )
        assertTrue(
            verifier.verify(
                abc,
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ),
        )
    }

    @Test
    fun matchesDespiteCaseAndPrefixDifferences() {
        val abc = file("abc.txt", "abc")
        assertTrue(
            verifier.verify(
                abc,
                "SHA256:BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
            ),
        )
        assertTrue(
            verifier.verify(
                abc,
                "  ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad  ",
            ),
        )
    }

    @Test
    fun rejectsWrongHash() {
        val abc = file("abc.txt", "abc")
        assertFalse(verifier.verify(abc, "0".repeat(64)))
        assertFalse(verifier.verify(abc, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ae"))
    }

    @Test
    fun rejectsBlankHashAndMissingFile() {
        val abc = file("abc.txt", "abc")
        assertFalse(verifier.verify(abc, ""))
        assertFalse(verifier.verify(abc, "   "))
        assertFalse(verifier.verify(File(tmp.root, "nope.bin"), "a".repeat(64)))
    }

    @Test
    fun hashesLargeFilesInChunks() {
        val big = tmp.newFile("big.bin")
        val chunk = ByteArray(64 * 1024) { (it % 251).toByte() }
        big.outputStream().use { out ->
            repeat(64) { out.write(chunk) }
        }
        val hex = verifier.sha256Hex(big)
        assertEquals(64, hex.length)
        assertTrue(hex.all { it in "0123456789abcdef" })
        assertTrue(verifier.verify(big, hex))
    }
}
