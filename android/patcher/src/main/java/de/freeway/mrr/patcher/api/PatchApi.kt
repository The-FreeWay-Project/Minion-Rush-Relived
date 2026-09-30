package de.freeway.mrr.patcher.api

import java.util.concurrent.TimeUnit

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Update-manifest API of the MRR server. The base URL is configured in exactly
 * one place (BuildConfig.MRR_BASE_URL — see patcher/build.gradle.kts, gradle
 * property `mrr.patcher.baseUrl`). The endpoint is public: no Authorization
 * header is involved.
 */
interface PatchApi {

    @GET("api/v1/patch/manifest")
    suspend fun manifest(@Query("installed") installedVersion: String): PatchManifest
}

/**
 * Builds the Retrofit client for the MRR Patcher. Same stack as the main app
 * (Retrofit + kotlinx-serialization + OkHttp), minus the auth interceptor.
 */
object PatchClient {

    fun create(baseUrl: String): PatchApi {
        val json = Json {
            ignoreUnknownKeys = true
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PatchApi::class.java)
    }
}
