package de.freeway.mrr.android.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val username: String, val password: String)

@Serializable
data class AccountResponse(
    @SerialName("account_id") val accountId: Long,
    val username: String,
)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_in") val expiresIn: Long = 0,
)

@Serializable
data class ProfilePlayerResponse(
    @SerialName("player_id") val playerId: String,
    @SerialName("display_name") val displayName: String,
    val level: Int,
    val coins: Int,
)

@Serializable
data class ProfileResponse(
    @SerialName("account_id") val accountId: Long,
    val username: String,
    val player: ProfilePlayerResponse? = null,
)

@Serializable
data class PlayerRequest(
    @SerialName("player_id") val playerId: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class PlayerResponse(
    val id: Long,
    @SerialName("player_id") val playerId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class PlayerStateRequest(val experience: Int, val level: Int, val coins: Int)

@Serializable
data class PlayerStateResponse(
    @SerialName("player_id") val playerId: Long,
    val experience: Int,
    val level: Int,
    val coins: Int,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class ErrorResponse(val detail: String = "")

@Serializable
data class HealthResponse(val status: String, val service: String)
