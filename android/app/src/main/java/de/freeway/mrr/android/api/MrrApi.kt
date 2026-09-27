package de.freeway.mrr.android.api

import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * MRR API v1 as consumed by the Android reference client. The Authorization
 * header is added centrally by [AuthInterceptor].
 */
interface MrrApi {

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): AccountResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("api/v1/auth/logout")
    suspend fun logout()

    @GET("api/v1/profile")
    suspend fun profile(): ProfileResponse

    @GET("api/v1/players")
    suspend fun players(): List<PlayerResponse>

    @POST("api/v1/players")
    suspend fun createPlayer(@Body body: PlayerRequest): PlayerResponse

    @DELETE("api/v1/players/{playerId}")
    suspend fun deletePlayer(@Path("playerId") playerId: String)

    @GET("api/v1/player/state")
    suspend fun getPlayerState(): PlayerStateResponse

    @PUT("api/v1/player/state")
    suspend fun putPlayerState(@Body body: PlayerStateRequest): PlayerStateResponse
}

/**
 * Adds {@code Authorization: Bearer <token>} when a session token is stored.
 * The token itself is never logged or written to any output.
 */
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
