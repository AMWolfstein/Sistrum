package com.amwolfstein.sistrum.integrations.net

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class HttpFetcherBoundaryTest {
    @Test fun malformedAndHostlessUrlsNeverOpenTransport() = runBlocking {
        val fetcher = UrlConnectionFetcher(openConnection = { error("Transport must not open") }, log = {})
        for (url in listOf("not a URL", "https:///", "file:///secret", "ftp://example.test/file")) {
            assertEquals(FetchResult.InvalidUrl, fetcher.get(url, emptyMap(), 4))
        }
    }

    @Test fun maximumCapDoesNotOverflowOrPreallocateTheCap() = runBlocking {
        val connection = Connection()
        val result = fetcher(connection).get(HTTPS, emptyMap(), Int.MAX_VALUE)
        assertArrayEquals(byteArrayOf(), (result as FetchResult.Success).bytes)
        assertTrue(connection.closed)
        assertTrue(connection.disconnected.await(1, TimeUnit.SECONDS))
    }

    @Test fun bodyIoFailureIsTypedAndReleasesResources() = runBlocking {
        val connection = Connection(object : InputStream() {
            override fun read(): Int = throw IOException("secret token")
        })
        assertEquals(FetchResult.NetworkError, fetcher(connection).get(HTTPS, emptyMap(), 4))
        assertTrue(connection.closed)
        assertTrue(connection.disconnected.await(1, TimeUnit.SECONDS))
    }

    @Test fun cancellationDisconnectsBlockedReadWithoutWaitingForReadTimeout() = runBlocking {
        val reading = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val connection = Connection(object : InputStream() {
            override fun read(): Int {
                reading.countDown()
                check(disconnected.await(5, TimeUnit.SECONDS)) { "Cancellation did not disconnect" }
                throw IOException("socket closed")
            }
        }, disconnected)
        val job = launch(Dispatchers.Default) { fetcher(connection).get(HTTPS, emptyMap(), 4) }
        assertTrue(reading.await(5, TimeUnit.SECONDS))
        job.cancel()
        withTimeout(5_000) { job.join() }
        assertTrue(job.isCancelled)
        assertTrue(connection.closed)
    }

    private fun fetcher(connection: Connection) = UrlConnectionFetcher(openConnection = { connection }, log = {})

    private class Connection(
        private val body: InputStream = ByteArrayInputStream(byteArrayOf()),
        val disconnected: CountDownLatch = CountDownLatch(1),
    ) : HttpURLConnection(URL(HTTPS)) {
        var closed = false
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { disconnected.countDown() }
        override fun getResponseCode() = 200
        override fun getContentLengthLong() = -1L
        override fun getInputStream(): InputStream = object : InputStream() {
            override fun read(): Int = body.read()
            override fun close() { closed = true; body.close() }
        }
    }

    private companion object { const val HTTPS = "https://example.test/body" }
}
