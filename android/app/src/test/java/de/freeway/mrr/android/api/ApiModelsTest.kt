package de.freeway.mrr.android.api

import kotlinx.serialization.json.Json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun loginResponseParsesSnakeCase() {
        val parsed = json.decodeFromString<LoginResponse>(
            """{"access_token":"tok-123","token_type":"bearer","expires_in":86400}"""
        )
        assertEquals("tok-123", parsed.accessToken)
        assertEquals("bearer", parsed.tokenType)
        assertEquals(86400L, parsed.expiresIn)
    }

    @Test
    fun loginResponseIgnoresUnknownFields() {
        val parsed = json.decodeFromString<LoginResponse>(
            """{"access_token":"tok","token_type":"bearer","expires_in":1,"brand_new_field":true}"""
        )
        assertEquals("tok", parsed.accessToken)
    }

    @Test
    fun accountResponseParsesAccountId() {
        val parsed = json.decodeFromString<AccountResponse>(
            """{"account_id":42,"username":"alice"}"""
        )
        assertEquals(42L, parsed.accountId)
        assertEquals("alice", parsed.username)
    }

    @Test
    fun profileResponseParsesNestedPlayer() {
        val parsed = json.decodeFromString<ProfileResponse>(
            """
            {
              "account_id": 7,
              "username": "alice",
              "player": {
                "player_id": "player-0001",
                "display_name": "alice",
                "level": 3,
                "coins": 75
              }
            }
            """.trimIndent()
        )
        assertEquals(7L, parsed.accountId)
        assertEquals("alice", parsed.username)
        assertEquals("player-0001", parsed.player?.playerId)
        assertEquals(3, parsed.player?.level)
        assertEquals(75, parsed.player?.coins)
    }

    @Test
    fun profileResponseWithoutPlayerIsNull() {
        val parsed = json.decodeFromString<ProfileResponse>(
            """{"account_id":1,"username":"bob"}"""
        )
        assertNull(parsed.player)
    }

    @Test
    fun playerResponseParsesSnakeCase() {
        val parsed = json.decodeFromString<PlayerResponse>(
            """
            {
              "id": 10,
              "player_id": "p-1",
              "display_name": "Speedy",
              "created_at": "2026-09-27T10:00:00Z"
            }
            """.trimIndent()
        )
        assertEquals(10L, parsed.id)
        assertEquals("p-1", parsed.playerId)
        assertEquals("Speedy", parsed.displayName)
        assertEquals("2026-09-27T10:00:00Z", parsed.createdAt)
    }

    @Test
    fun playerStateResponseParsesSnakeCase() {
        val parsed = json.decodeFromString<PlayerStateResponse>(
            """
            {
              "player_id": 10,
              "experience": 100,
              "level": 3,
              "coins": 75,
              "updated_at": "2026-09-27T10:05:00Z"
            }
            """.trimIndent()
        )
        assertEquals(10L, parsed.playerId)
        assertEquals(100, parsed.experience)
        assertEquals(3, parsed.level)
        assertEquals(75, parsed.coins)
        assertEquals("2026-09-27T10:05:00Z", parsed.updatedAt)
    }

    @Test
    fun errorResponseParsesDetail() {
        val parsed = json.decodeFromString<ErrorResponse>(
            """{"detail":"Invalid username or password"}"""
        )
        assertEquals("Invalid username or password", parsed.detail)
    }

    @Test
    fun errorResponseDefaultsToEmptyDetail() {
        val parsed = json.decodeFromString<ErrorResponse>("""{}""")
        assertEquals("", parsed.detail)
    }

    @Test
    fun healthResponseParses() {
        val parsed = json.decodeFromString<HealthResponse>(
            """{"status":"ok","service":"mrr"}"""
        )
        assertEquals("ok", parsed.status)
        assertEquals("mrr", parsed.service)
    }
}
