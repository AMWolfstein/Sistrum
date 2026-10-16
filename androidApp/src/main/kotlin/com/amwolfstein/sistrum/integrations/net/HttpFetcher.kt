package com.amwolfstein.sistrum.integrations.net

import android.util.Log
import com.amwolfstein.sistrum.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

interface HttpFetcher {
    suspend fun get(url: String, headers: Map<String, String>, maxBytes: Int): FetchResult
}

sealed interface FetchResult {
    data class Success(val bytes: ByteArray) : FetchResult
    data class HttpError(val code: Int) : FetchResult
    data object TooLarge : FetchResult
    data object InvalidUrl : FetchResult
    data object RedirectLimit : FetchResult
    data object Timeout : FetchResult
    data object NetworkError : FetchResult
}

class UrlConnectionFetcher(
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
    private val maxRedirects: Int = 5,
    private val log: (String) -> Unit = { Log.w("UrlConnectionFetcher", it) },
) : HttpFetcher {
    init {
        require(connectTimeoutMs > 0 && readTimeoutMs > 0)
        require(maxRedirects >= 0)
    }

    override suspend fun get(url: String, headers: Map<String, String>, maxBytes: Int): FetchResult {
        require(maxBytes >= 0)
        return withContext(Dispatchers.IO) {
            // Disconnect the active socket even while a blocking connect/read is in progress.
            suspendCancellableCoroutine<Result<FetchResult>> { continuation ->
                val active = AtomicReference<HttpURLConnection?>()
                continuation.invokeOnCancellation { active.getAndSet(null)?.disconnect() }
                try {
                    val result = fetch(url, headers, maxBytes, continuation.context, active)
                    continuation.context.ensureActive()
                    continuation.resume(Result.success(result))
                } catch (cancelled: CancellationException) {
                    // Carry transport cancellation across the IO boundary without coroutine stack-trace
                    // recovery copying it; callers must receive the original exception instance.
                    continuation.resume(Result.failure(cancelled))
                } catch (failure: IOException) {
                    continuation.context.ensureActive()
                    val result = if (failure is SocketTimeoutException) FetchResult.Timeout else FetchResult.NetworkError
                    // Exception messages and URLs can contain credentials; log only the typed outcome.
                    continuation.resume(Result.success(refuse(result)))
                }
            }
        }.getOrThrow()
    }

    private fun fetch(
        url: String,
        headers: Map<String, String>,
        maxBytes: Int,
        context: CoroutineContext,
        active: AtomicReference<HttpURLConnection?>,
    ): FetchResult {
        var target = httpsUrl(url) ?: return refuse(FetchResult.InvalidUrl)
        var redirects = 0
        while (true) {
            context.ensureActive()
            val connection = openConnection(target)
            active.set(connection)
            try {
                context.ensureActive()
                connection.instanceFollowRedirects = false
                connection.connectTimeout = connectTimeoutMs
                connection.readTimeout = readTimeoutMs
                headers.forEach { (key, value) ->
                    if (!key.equals("User-Agent", ignoreCase = true)) connection.setRequestProperty(key, value)
                }
                connection.setRequestProperty("User-Agent", "Sistrum-Android/${BuildConfig.VERSION_NAME}")
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                        ?: return refuse(FetchResult.HttpError(code))
                    val next = httpsUrl(location, target) ?: return refuse(FetchResult.InvalidUrl)
                    if (redirects >= maxRedirects) return refuse(FetchResult.RedirectLimit)
                    target = next
                    redirects++
                    continue
                }
                if (code !in 200..299) return refuse(FetchResult.HttpError(code))
                if (connection.contentLengthLong > maxBytes.toLong()) return refuse(FetchResult.TooLarge)
                return connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
                    val buffer = ByteArray(8192)
                    while (true) {
                        context.ensureActive()
                        val remaining = maxBytes.toLong() - output.size() + 1
                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        context.ensureActive()
                        if (count == -1) break
                        if (count > maxBytes - output.size()) return@use refuse(FetchResult.TooLarge)
                        output.write(buffer, 0, count)
                    }
                    FetchResult.Success(output.toByteArray())
                }
            } finally {
                if (active.compareAndSet(connection, null)) connection.disconnect()
            }
        }
    }

    private fun httpsUrl(value: String, base: URL? = null): URL? = try {
        URL(base, value).takeIf { it.protocol.equals("https", ignoreCase = true) && it.host.isNotEmpty() }
    } catch (_: MalformedURLException) {
        null // The caller returns and logs InvalidUrl.
    }

    private fun refuse(result: FetchResult): FetchResult {
        log("HTTPS fetch refused: ${result.javaClass.simpleName}")
        return result
    }
}
