package de.freeway.mrr.android.api

import java.util.concurrent.TimeUnit

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Builds the Retrofit client for the MRR server. The base URL is configurable
 * (default {@code http://10.0.2.2:8080/} for the emulator — see
 * gradle.properties {@code mrr.baseUrl}).
 */
object ApiClient {

    fun create(baseUrl: String, tokenProvider: () -> String?): MrrApi {
        val json = Json {
            ignoreUnknownKeys = true
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenProvider))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MrrApi::class.java)
    }
}
