package de.freeway.mrr.patcher.pipeline

import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Streaming SHA-256 verification of downloaded patch files. Comparisons go
 * through [MessageDigest.isEqual]; a blank expected hash never passes.
 */
class PatchVerifier {

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") {
            String.format(Locale.ROOT, "%02x", it)
        }
    }

    fun verify(file: File, expectedSha256: String): Boolean {
        val expected = expectedSha256.trim().lowercase(Locale.ROOT).removePrefix("sha256:")
        if (expected.isEmpty() || !file.isFile) return false
        val actual = sha256Hex(file)
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.US_ASCII),
            actual.toByteArray(Charsets.US_ASCII),
        )
    }
}
