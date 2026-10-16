package com.amwolfstein.sistrum.integrations.net

import com.amwolfstein.sistrum.BuildConfig
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** FR-012 / T021. Every URL is intercepted by a fake connection factory; no DNS or TLS is used. */
class HttpFetcherTest {
    @Test fun rejectsHttpBeforeOpeningTransport() = runBlocking {
        val transport = Transport()
        assertKind("InvalidUrl", fetch(transport.fetcher(), "http://example.test/private?token=secret"))
        assertTrue(transport.opened.isEmpty())
    }

    @Test fun rejectsHttpRedirectBeforeOpeningItsTarget() = runBlocking {
        val transport = Transport(Connection(code = 302, location = "http://example.test/insecure"))
        assertKind("InvalidUrl", fetch(transport.fetcher(), HTTPS))
        assertEquals(listOf(HTTPS), transport.opened)
        transport.assertReleased()
    }

    @Test fun boundsRedirectLoops() = runBlocking {
        val transport = Transport(*Array(3) { Connection(code = 302, location = HTTPS) })
        assertKind("RedirectLimit", fetch(transport.fetcher(), HTTPS))
        assertEquals(3, transport.opened.size) // initial request plus two permitted redirects
        transport.assertReleased()
    }

    @Test fun resolvesRelativeHttpsRedirectAndReturnsFinalBytes() = runBlocking {
        val bytes = byteArrayOf(0, 1, -1, 127)
        val transport = Transport(Connection(code = 307, location = "/final"), Connection(body = bytes))
        val result = fetch(transport.fetcher(), HTTPS)
        assertBody(bytes, result)
        assertEquals(listOf(HTTPS, "https://example.test/final"), transport.opened)
        transport.assertReleased()
    }

    @Test fun connectTimeoutIsConfiguredAndReturnedAsTypedFailure() = runBlocking {
        val connection = Connection(connectFailure = SocketTimeoutException("connect timed out"))
        val transport = Transport(connection)
        assertKind("Timeout", fetch(transport.fetcher(), HTTPS))
        assertEquals(1_000, connection.connectTimeout)
        assertEquals(2_000, connection.readTimeout)
        transport.assertReleased()
    }

    @Test fun readTimeoutIsConfiguredAndReturnedAsTypedFailure() = runBlocking {
        val connection = Connection(stream = throwingStream(SocketTimeoutException("read timed out")))
        val transport = Transport(connection)
        assertKind("Timeout", fetch(transport.fetcher(), HTTPS))
        assertEquals(1_000, connection.connectTimeout)
        assertEquals(2_000, connection.readTimeout)
        assertTrue(connection.streamClosed)
        transport.assertReleased()
    }

    @Test fun declaredOversizedBodyIsRefusedBeforeReading() = runBlocking {
        val connection = Connection(body = ByteArray(5), length = 5)
        val transport = Transport(connection)
        assertKind("TooLarge", fetch(transport.fetcher(), HTTPS, maxBytes = 4))
        assertEquals(0, connection.bytesRead)
        transport.assertReleased()
    }

    @Test fun unknownLengthBodyOverCapIsTypedTooLargeAndReadingIsBounded() = runBlocking {
        val connection = Connection(body = ByteArray(100), length = -1)
        val transport = Transport(connection)
        assertKind("TooLarge", fetch(transport.fetcher(), HTTPS, maxBytes = 4))
        assertTrue("Read at most cap + one sentinel byte", connection.bytesRead in 1..5)
        assertTrue(connection.streamClosed)
        transport.assertReleased()
    }

    @Test fun enforcesActualSizeEvenWhenContentLengthLies() = runBlocking {
        val connection = Connection(body = ByteArray(6), length = 2)
        val transport = Transport(connection)
        assertKind("TooLarge", fetch(transport.fetcher(), HTTPS, maxBytes = 4))
        assertTrue(connection.bytesRead in 1..5)
        assertTrue(connection.streamClosed)
        transport.assertReleased()
    }

    @Test fun exactCapAndEmptyBodySucceedIncludingZeroByteCap() = runBlocking {
        for ((bytes, cap) in listOf(byteArrayOf(5, 6, 7, 8) to 4, byteArrayOf() to 0)) {
            val connection = Connection(body = bytes, length = -1)
            val transport = Transport(connection)
            assertBody(bytes, fetch(transport.fetcher(), HTTPS, maxBytes = cap))
            assertTrue(connection.streamClosed)
            transport.assertReleased()
        }
    }

    @Test fun non2xxPreservesStatusWithoutReadingErrorBody() = runBlocking {
        for (code in listOf(400, 401, 404, 429, 500, 503)) {
            val connection = Connection(code = code)
            val transport = Transport(connection)
            val result = fetch(transport.fetcher(), HTTPS)
            assertKind("HttpError", result)
            assertEquals(code, property(result, "Code"))
            assertEquals(0, connection.bytesRead)
            transport.assertReleased()
        }
    }

    @Test fun cancellationFromConnectPropagatesUnchanged() = runBlocking {
        val cancelled = CancellationException("owner cancelled")
        val transport = Transport(Connection(connectFailure = cancelled))
        assertCancellation(cancelled) { fetch(transport.fetcher(), HTTPS) }
        transport.assertReleased()
    }

    @Test fun cancellationFromBodyPropagatesAndClosesStream() = runBlocking {
        val cancelled = CancellationException("body cancelled")
        val connection = Connection(stream = throwingStream(cancelled))
        val transport = Transport(connection)
        assertCancellation(cancelled) { fetch(transport.fetcher(), HTTPS) }
        assertTrue(connection.streamClosed)
        transport.assertReleased()
    }

    @Test fun cancellingAnActiveRequestCannotReturnSuccess() = runBlocking {
        val reading = CountDownLatch(1)
        val release = CountDownLatch(1)
        val connection = Connection(length = -1, stream = object : InputStream() {
            override fun read(): Int {
                reading.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "Test did not release read" }
                return -1
            }
        })
        val transport = Transport(connection)
        val fetcher = transport.fetcher()
        val returned = AtomicReference<Any?>()
        val job = launch(Dispatchers.Default) { returned.set(fetch(fetcher, HTTPS)) }
        try {
            assertTrue("Request must enter body read", reading.await(5, TimeUnit.SECONDS))
            job.cancel()
        } finally {
            release.countDown()
        }
        withTimeout(5_000) { job.join() }
        assertNull("Cancelled fetch must throw rather than return a result", returned.get())
        assertTrue(connection.streamClosed)
        transport.assertReleased()
    }

    @Test fun fixedUserAgentCannotBeOverriddenAndOtherHeadersAreForwarded() = runBlocking {
        val connection = Connection()
        val transport = Transport(connection)
        assertBody(byteArrayOf(), fetch(transport.fetcher(), HTTPS, mapOf("user-agent" to "override", "Accept" to "application/json")))
        val agents = connection.requestProperties.filterKeys { it.equals("User-Agent", ignoreCase = true) }.values.flatten()
        assertEquals(listOf("Sistrum-Android/${BuildConfig.VERSION_NAME}"), agents)
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        assertFalse("Redirects must be inspected before opening their targets", connection.instanceFollowRedirects)
        transport.assertReleased()
    }

    @Test fun errorLogsContainNeitherFullUrlsNorTokensEvenInExceptionMessages() = runBlocking {
        val first = "$HTTPS?token=SECRET_INITIAL"
        val target = "https://example.test/next?access_token=SECRET_REDIRECT"
        val transport = Transport(
            Connection(code = 302, location = target),
            Connection(connectFailure = IOException("Failed fetching $target from $first")),
        )
        assertKind("NetworkError", fetch(transport.fetcher(), first))
        assertTrue("Failure must produce a diagnostic", transport.logs.isNotEmpty())
        val logs = transport.logs.joinToString("\n")
        for (secret in listOf(first, target, "SECRET_INITIAL", "SECRET_REDIRECT")) assertFalse(logs, logs.contains(secret))
        transport.assertReleased()
    }

    @Test fun scriptedFakeImplementsFetcherAndRecordsImmutableRequests() = runBlocking {
        // Resolve the real result type first so this is also an expected assertion failure before T022.
        val success = fetcherClass("FetchResult\$Success").getDeclaredConstructor(ByteArray::class.java)
            .newInstance(byteArrayOf(9))
        val fake = FakeHttpFetcher()
        fake.enqueue(success)
        val cancelled = CancellationException("scripted cancellation")
        fake.enqueueReply { throw cancelled }
        val fetcher: Any = fake.asFetcher()
        val headers = mutableMapOf("Accept" to "text/plain")
        assertBody(byteArrayOf(9), fetch(fetcher, HTTPS, headers, 8))
        headers.clear()
        assertEquals(listOf(FakeHttpFetcher.Request(HTTPS, mapOf("Accept" to "text/plain"), 8)), fake.requests)
        assertCancellation(cancelled) { fetch(fetcher, HTTPS) }
        assertEquals(2, fake.requests.size)
    }

    private class Transport(vararg val connections: Connection) {
        val opened = mutableListOf<String>()
        val logs = mutableListOf<String>()

        fun fetcher(): Any {
            val factory: (URL) -> HttpURLConnection = { url ->
                val index = opened.size
                opened.add(url.toString())
                assertTrue("Unexpected connection $index", index < connections.size)
                connections[index]
            }
            val logger: (String) -> Unit = { logs.add(it) }
            return fetcherClass("UrlConnectionFetcher").getDeclaredConstructor(
                kotlin.jvm.functions.Function1::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                kotlin.jvm.functions.Function1::class.java,
            ).newInstance(factory, 1_000, 2_000, 2, logger)
        }

        fun assertReleased() {
            assertTrue("No connection exercised", opened.isNotEmpty())
            connections.take(opened.size).forEach { assertTrue("Connection was not disconnected", it.disconnected) }
        }
    }

    private class Connection(
        private val code: Int = 200,
        private val body: ByteArray = byteArrayOf(),
        private val location: String? = null,
        private val length: Int = body.size,
        private val connectFailure: Exception? = null,
        private val stream: InputStream = ByteArrayInputStream(body),
    ) : HttpURLConnection(URL(HTTPS)) {
        var disconnected = false
        var streamClosed = false
        var bytesRead = 0
        override fun connect() { connectFailure?.let { throw it } }
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode(): Int { connect(); return code }
        override fun getHeaderField(name: String?): String? = when {
            name.equals("Location", true) -> location
            name.equals("Content-Length", true) && length >= 0 -> length.toString()
            else -> null
        }
        override fun getContentLength() = length
        override fun getContentLengthLong() = length.toLong()
        override fun getInputStream(): InputStream = object : InputStream() {
            override fun read(): Int = stream.read().also { if (it >= 0) bytesRead++ }
            override fun read(bytes: ByteArray, offset: Int, count: Int): Int =
                stream.read(bytes, offset, count).also { if (it > 0) bytesRead += it }
            override fun close() { streamClosed = true; stream.close() }
        }
        override fun getErrorStream() = inputStream
    }

    private fun assertKind(kind: String, result: Any) {
        assertEquals(kind, result.javaClass.simpleName)
        assertTrue("Result must be a FetchResult", fetcherClass("FetchResult").isInstance(result))
    }

    private fun property(result: Any, name: String): Any = result.javaClass.getMethod("get$name").invoke(result)
    private fun assertBody(bytes: ByteArray, result: Any) {
        assertKind("Success", result)
        assertArrayEquals(bytes, property(result, "Bytes") as ByteArray)
    }

    private suspend fun assertCancellation(expected: CancellationException, action: suspend () -> Any) {
        try {
            action()
            fail("CancellationException was swallowed")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
    }

    private fun throwingStream(error: Exception) = object : InputStream() {
        override fun read(): Int = throw error
    }

    private companion object { const val HTTPS = "https://example.test/start" }
}
