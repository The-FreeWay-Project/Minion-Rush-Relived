package de.freeway.mrr.patcher.pipeline

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

import de.freeway.mrr.patcher.data.PatchApiException
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Streaming file download behind the pipeline. Implementations write to
 * [target] and report `(bytesDone, bytesTotal)` per chunk (`bytesTotal` is
 * -1 when the server did not declare a length).
 */
interface PatchDownloader {

    suspend fun download(
        url: String,
        target: File,
        onProgress: (bytesDone: Long, bytesTotal: Long) -> Unit,
    )
}

/**
 * OkHttp-based downloader: 8 KiB streaming, cooperative cancellation and a
 * guaranteed delete of a partial target on any failure. Plain HTTP or HTTPS -
 * no pinning, no trust-all shortcuts.
 */
class HttpPatchDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) : PatchDownloader {

    override suspend fun download(
        url: String,
        target: File,
        onProgress: (bytesDone: Long, bytesTotal: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            throw PatchApiException.Network(e)
        }
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PatchApiException.Server(response.code)
                }
                val body = response.body
                    ?: throw PatchApiException.Network(IOException("Empty response body"))
                val total = body.contentLength()
                target.parentFile?.mkdirs()
                val buffer = ByteArray(8 * 1024)
                var done = 0L
                body.byteStream().use { input ->
                    FileOutputStream(target).use { output ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            onProgress(done, total)
                        }
                        output.flush()
                    }
                }
            }
        } catch (e: CancellationException) {
            target.delete()
            throw e
        } catch (e: PatchApiException) {
            target.delete()
            throw e
        } catch (e: IOException) {
            target.delete()
            throw PatchApiException.Network(e)
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }
}


