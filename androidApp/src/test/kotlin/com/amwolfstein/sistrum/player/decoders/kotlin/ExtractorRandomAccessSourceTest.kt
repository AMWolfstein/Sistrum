@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import androidx.media3.common.C
import androidx.media3.extractor.ExtractorInput
import androidx.media3.test.utils.FakeExtractorInput
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

class ExtractorRandomAccessSourceTest {
    private val data = ByteArray(150_000) { (it * 31).toByte() }
    private fun input() = FakeExtractorInput.Builder().setData(data).build()

    @Test fun positionalReadsHandleForwardGapsBackwardReadsAndDestinationBounds() {
        ExtractorRandomAccessSource(input()).use { source ->
            assertEquals(data.size.toLong(), source.length)
            for (position in longArrayOf(90_000, 3, 149_990, 0, 65_530)) {
                val destination = ByteBuffer.allocate(25).also { it.position(4); it.limit(20) }
                val count = minOf(16, data.size - position.toInt())
                assertEquals(count, source.read(position, destination))
                assertEquals(4 + count, destination.position())
                assertEquals(20, destination.limit())
                assertArrayEquals(data.copyOfRange(position.toInt(), position.toInt() + count),
                    destination.array().copyOfRange(4, 4 + count))
            }
            assertEquals(-1, source.read(source.length, ByteBuffer.allocate(1)))
            assertEquals(0, source.read(source.length, ByteBuffer.allocate(0)))
            assertThrows(IllegalArgumentException::class.java) { source.read(-1, ByteBuffer.allocate(1)) }
        }
    }

    @Test fun unknownLengthIsResolvedWithoutLosingBytes() {
        val input = object : ExtractorInput by input() { override fun getLength() = C.LENGTH_UNSET.toLong() }
        ExtractorRandomAccessSource(input).use { source ->
            assertEquals(data.size.toLong(), source.length)
            val destination = ByteBuffer.allocate(20)
            assertEquals(20, source.read(5, destination))
            assertArrayEquals(data.copyOfRange(5, 25), destination.array())
        }
    }

    @Test fun rebindingAfterMedia3ReopensAtZeroCanExtendTheCache() {
        ExtractorRandomAccessSource(input()).use { source ->
            source.read(100, ByteBuffer.allocate(4))
            source.bind(input())
            val destination = ByteBuffer.allocate(10)
            assertEquals(10, source.read(100_000, destination))
            assertArrayEquals(data.copyOfRange(100_000, 100_010), destination.array())
            source.bind(input())
            destination.clear()
            assertEquals(10, source.read(0, destination))
            assertArrayEquals(data.copyOfRange(0, 10), destination.array())
        }
    }

    @Test fun rebindingAtResumePositionReadsOnlyNewBytes() {
        ExtractorRandomAccessSource(input()).use { source ->
            assertEquals(0L, source.resumePosition)
            assertEquals(4, source.read(100, ByteBuffer.allocate(4)))
            assertEquals(104L, source.resumePosition)
            val delegate = input().apply { setPosition(104) }
            var bytesRead = 0
            val resumed = object : ExtractorInput by delegate {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    delegate.read(buffer, offset, length).also { if (it > 0) bytesRead += it }
                override fun skipFully(length: Int): Unit = error("resume must not skip cached bytes")
            }
            source.bind(resumed)
            val destination = ByteBuffer.allocate(10)
            assertEquals(10, source.read(104, destination))
            assertArrayEquals(data.copyOfRange(104, 114), destination.array())
            assertEquals(10, bytesRead)
            assertEquals(114L, source.resumePosition)
        }
    }

    @Test fun fileChannelReadsKeepCallerOwnershipAndSupportPositionsBeyondTwoGiB() {
        val file = File.createTempFile("sistrum-source-test-", ".bin")
        try {
            RandomAccessFile(file, "rw").use { handle ->
                val position = Int.MAX_VALUE.toLong() + 15
                handle.seek(position)
                handle.write(byteArrayOf(4, 5, 6))
                val channel = handle.channel
                channel.position(7)
                ExtractorRandomAccessSource(channel).use { source ->
                    assertEquals(position + 3, source.length)
                    val destination = ByteBuffer.allocateDirect(3)
                    assertEquals(3, source.read(position, destination))
                    destination.flip()
                    assertEquals(4, destination.get().toInt())
                    assertEquals(5, destination.get().toInt())
                    assertEquals(6, destination.get().toInt())
                    assertEquals(7L, channel.position())
                }
                assertTrue(channel.isOpen)
            }
        } finally { assertTrue(file.delete()) }
    }

    @Test fun ownedCacheIsDeletedOnCloseAndFurtherReadsFail() {
        val file = File.createTempFile("sistrum-source-test-", ".bin")
        val source = ExtractorRandomAccessSource(input(), file)
        source.read(0, ByteBuffer.allocate(4))
        assertTrue(file.length() >= 4)
        source.close()
        source.close()
        assertFalse(file.exists())
        assertThrows(IOException::class.java) { source.read(0, ByteBuffer.allocate(4)) }
    }

    @Test fun interruptedCacheReadsSurviveRebindingAndBackwardSeek() {
        val delegate = input()
        val input = object : ExtractorInput by delegate {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                Thread.currentThread().interrupt()
                return delegate.read(buffer, offset, length)
            }
        }
        ExtractorRandomAccessSource(input).use { source ->
            try {
                val destination = ByteBuffer.allocate(16)
                assertEquals(16, source.read(0, destination))
                assertTrue(Thread.currentThread().isInterrupted)
                assertArrayEquals(data.copyOfRange(0, 16), destination.array())
            } finally { Thread.interrupted() }
            source.bind(input())
            val destination = ByteBuffer.allocateDirect(16)
            Thread.currentThread().interrupt()
            try { assertEquals(16, source.read(0, destination)) }
            finally { Thread.interrupted() }
            destination.clear()
            assertEquals(16, source.read(100_000, destination))
            destination.flip()
            val actual = ByteArray(16).also { destination.get(it) }
            assertArrayEquals(data.copyOfRange(100_000, 100_016), actual)
        }
    }

    @Test fun failedConstructorDeletesItsCacheAndPreservesTheCause() {
        val file = File.createTempFile("sistrum-source-test-", ".cache")
        val failure = IOException("length unavailable")
        val input = object : ExtractorInput by input() { override fun getLength(): Long = throw failure }
        assertSame(failure, assertThrows(IOException::class.java) { ExtractorRandomAccessSource(input, file) })
        assertFalse(file.exists())
    }

    @Test fun staleSweepOnlyDeletesCodecCaches() {
        val directory = java.nio.file.Files.createTempDirectory("sistrum-cache-test-").toFile()
        try {
            val stale = File(directory, "sistrum-codec-stale.cache").apply { writeText("stale") }
            val other = File(directory, "another.cache").apply { writeText("keep") }
            val partialName = File(directory, "sistrum-codec-keep.bin").apply { writeText("keep") }
            ExtractorRandomAccessSource.sweepStaleCaches(directory)
            assertFalse(stale.exists())
            assertEquals("keep", other.readText())
            assertEquals("keep", partialName.readText())
        } finally { directory.deleteRecursively() }
    }

    @Test fun undeletableCacheDoesNotMakeCloseThrow() {
        val actual = File.createTempFile("sistrum-source-test-", ".cache")
        val file = object : File(actual.path) { override fun delete() = false }
        try {
            val source = ExtractorRandomAccessSource(input(), file)
            source.read(0, ByteBuffer.allocate(4))
            source.close()
            assertTrue(file.exists())
            assertThrows(IOException::class.java) { source.read(0, ByteBuffer.allocate(4)) }
        } finally { assertTrue(actual.delete()) }
    }

    @Test fun deletionPermissionFailureDoesNotReplaceAConstructorFailureOrMakeCloseThrow() {
        val actual = File.createTempFile("sistrum-source-test-", ".cache")
        val file = object : File(actual.path) { override fun delete(): Boolean = throw SecurityException("denied") }
        val failure = IOException("length unavailable")
        try {
            val failed = object : ExtractorInput by input() { override fun getLength(): Long = throw failure }
            assertSame(failure, assertThrows(IOException::class.java) { ExtractorRandomAccessSource(failed, file) })
            val source = ExtractorRandomAccessSource(input(), file)
            source.close()
            assertTrue(actual.exists())
        } finally { assertTrue(actual.delete()) }
    }

    @Test fun transportFailuresPropagateWithTheirIdentity() {
        val failure = IOException("source disconnected")
        val input = object : ExtractorInput by input() {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw failure
        }
        ExtractorRandomAccessSource(input).use { source ->
            assertSame(failure, assertThrows(IOException::class.java) { source.read(0, ByteBuffer.allocate(4)) })
        }
    }
}
