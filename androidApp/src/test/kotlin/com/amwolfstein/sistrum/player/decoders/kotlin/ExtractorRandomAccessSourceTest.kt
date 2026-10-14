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
