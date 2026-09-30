package de.freeway.mrr.patcher.api

import kotlinx.serialization.json.Json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun manifestParsesServerResponse() {
        val parsed = json.decodeFromString<PatchManifest>(
            """
            {
              "version": "A1.0.0",
              "channel": "stable",
              "platform": "android",
              "serverVersion": "0.3.0",
              "message": "MRR Patcher is up to date.",
              "files": []
            }
            """.trimIndent()
        )
        assertEquals("A1.0.0", parsed.version)
        assertEquals("stable", parsed.channel)
        assertEquals("android", parsed.platform)
        assertEquals("0.3.0", parsed.serverVersion)
        assertEquals("MRR Patcher is up to date.", parsed.message)
        assertTrue(parsed.files.isEmpty())
    }

    @Test
    fun manifestIgnoresUnknownFields() {
        val parsed = json.decodeFromString<PatchManifest>(
            """
            {
              "version": "A1.0.0",
              "channel": "stable",
              "platform": "android",
              "serverVersion": "0.3.0",
              "message": "MRR Patcher is up to date.",
              "files": [],
              "brand_new_field": true
            }
            """.trimIndent()
        )
        assertEquals("A1.0.0", parsed.version)
    }

    @Test
    fun manifestFilesDefaultToEmptyWhenOmitted() {
        val parsed = json.decodeFromString<PatchManifest>(
            """{"version":"A1.0.0"}"""
        )
        assertEquals("A1.0.0", parsed.version)
        assertEquals("", parsed.message)
        assertTrue(parsed.files.isEmpty())
    }

    @Test
    fun patchFileParsesPathAndSha256() {
        val parsed = json.decodeFromString<PatchManifest>(
            """
            {
              "version": "A1.1.0",
              "channel": "stable",
              "platform": "android",
              "serverVersion": "0.3.0",
              "message": "Update to A1.1.0 available.",
              "files": [
                {
                  "path": "packs/demo/payload.bin",
                  "url": "https://example.invalid/payload.bin",
                  "size": 1234,
                  "sha256": "abc123"
                }
              ]
            }
            """.trimIndent()
        )
        assertEquals(1, parsed.files.size)
        assertEquals("packs/demo/payload.bin", parsed.files[0].path)
        assertEquals("https://example.invalid/payload.bin", parsed.files[0].url)
        assertEquals(1234L, parsed.files[0].size)
        assertEquals("abc123", parsed.files[0].sha256)
    }

    @Test
    fun manifestWithoutVersionFailsToParse() {
        try {
            json.decodeFromString<PatchManifest>(
                """{"channel":"stable","files":[]}"""
            )
            throw AssertionError("expected SerializationException for missing version")
        } catch (expected: kotlinx.serialization.SerializationException) {
            assertTrue(expected.message!!.contains("version"))
        }
    }
}
