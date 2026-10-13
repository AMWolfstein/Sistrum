package com.amwolfstein.sistrum.lastfm

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The API 31-32 fallback must match InputStream.readNBytes (API 33) byte for byte. */
class ReadNBytesCompatTest {

    private val data = ByteArray(20_000) { (it * 7 + 3).toByte() }

    /** Returns at most [chunk] bytes per read() call, like a slow network stream. */
    private class ChunkedStream(bytes: ByteArray, private val chunk: Int) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        override fun read(): Int = delegate.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, minOf(len, chunk))
    }

    private fun assertMatchesJdk(bytes: ByteArray, len: Int, chunk: Int = Int.MAX_VALUE) {
        val expected = ChunkedStream(bytes, chunk).readNBytes(len)
        val actual = ChunkedStream(bytes, chunk).readNBytesFallback(len)
        assertContentEquals(expected, actual, "len=$len size=${bytes.size} chunk=$chunk")
    }

    @Test fun `stops at len when more data is available`() {
        assertMatchesJdk(data, 1_000)
        assertEquals(1_000, ByteArrayInputStream(data).readNBytesFallback(1_000).size)
    }

    @Test fun `returns fewer bytes only at EOF`() {
        assertMatchesJdk(data, 50_000)
        assertEquals(data.size, ByteArrayInputStream(data).readNBytesFallback(50_000).size)
    }

    @Test fun `exact length, empty stream, and zero len`() {
        assertMatchesJdk(data, data.size)
        assertMatchesJdk(ByteArray(0), 10)
        assertMatchesJdk(data, 0)
    }

    @Test fun `short reads are accumulated rather than returned early`() {
        assertMatchesJdk(data, 12_345, chunk = 1)
        assertMatchesJdk(data, 12_345, chunk = 997)
    }

    @Test fun `leaves the rest of the stream unread`() {
        val stream = ByteArrayInputStream(data)
        stream.readNBytesFallback(100)
        assertEquals(data[100].toInt() and 0xFF, stream.read())
    }

    @Test fun `negative len is rejected like readNBytes`() {
        assertFailsWith<IllegalArgumentException> { ByteArrayInputStream(data).readNBytesFallback(-1) }
    }

    @Test fun `avatar cap semantics hold (limit + 1 detects oversize)`() {
        val limit = 5_000
        assertEquals(limit + 1, ByteArrayInputStream(data).readNBytesFallback(limit + 1).size)
        assertEquals(limit, ByteArrayInputStream(data.copyOf(limit)).readNBytesFallback(limit + 1).size)
    }
}
