package me.misa198.airmedy.player.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.ToFloatPcmAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * JVM tests pinning [PcmToFloatProcessor]'s bit-exact conversion against Media3's
 * [ToFloatPcmAudioProcessor] and its inactivity for encodings it does not handle.
 */
@OptIn(UnstableApi::class)
class PcmToFloatProcessorTest {

    private val rate = 48000
    private val channels = 2

    @Test
    fun sixteenBitMatchesToFloatBitForBit() {
        val rng = Random(0x5EED)
        val values = mutableListOf(0, 1, -1, 32767, -32768, 12345)
        repeat(1000) { values += rng.nextInt(65536) - 32768 }
        val shorts = ShortArray(values.size) { values[it].toShort() }

        val actual = convert(C.ENCODING_PCM_16BIT, shortBytes(shorts))
        val expected = reference(C.ENCODING_PCM_16BIT, shortBytes(shorts))

        assertArrayEquals(expected, actual, 0f)
    }

    @Test
    fun twentyFourBitMatchesToFloatBitForBit() {
        val rng = Random(0xBEEF)
        val values = mutableListOf(
            0, 1, -1, 127, 128, 255, 256, 257, 65535, 65536,
            0x7FFFFF, -0x800000, 0x010000, 0x010001, 0x010002, 0x010003,
        )
        repeat(1000) { values += rng.nextInt(0x1000000) - 0x800000 }

        val actual = convert(C.ENCODING_PCM_24BIT, pcm24Bytes(values.toIntArray()))
        val expected = reference(C.ENCODING_PCM_24BIT, pcm24Bytes(values.toIntArray()))

        assertArrayEquals(expected, actual, 0f)
    }

    @Test
    fun thirtyTwoBitMatchesToFloatBitForBit() {
        val rng = Random(0xFEED)
        val values = mutableListOf(0, 1, -1, 255, 256, 65535, 65536, 0x12345678, Int.MAX_VALUE, Int.MIN_VALUE)
        repeat(1000) { values += rng.nextInt() }

        val actual = convert(C.ENCODING_PCM_32BIT, int32Bytes(values.toIntArray()))
        val expected = reference(C.ENCODING_PCM_32BIT, int32Bytes(values.toIntArray()))

        assertArrayEquals(expected, actual, 0f)
    }

    @Test
    fun floatAndEightBitInputAreInactive() {
        assertInactive(C.ENCODING_PCM_FLOAT)
        assertInactive(C.ENCODING_PCM_8BIT)
    }

    @Test
    fun outputFrameCountMatchesInput() {
        val samples = ShortArray(20 * channels) { (it - 20).toShort() }
        val bytes = shortBytes(samples)

        val actual = convert(C.ENCODING_PCM_16BIT, bytes)

        assertEquals(samples.size, actual.size)
        assertEquals(samples.size / channels, actual.size / channels)
    }

    @Test
    fun reusesArraysAcrossVaryingBufferSizes() {
        val processor = PcmToFloatProcessor()
        processor.configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)

        val large = ShortArray(4096) { ((it * 37) % 20000 - 10000).toShort() }
        val largeBytes = shortBytes(large)
        processor.queueInput(directBuffer(largeBytes))
        assertArrayEquals(
            reference(C.ENCODING_PCM_16BIT, largeBytes),
            toFloatArray(processor.getOutput()),
            0f,
        )

        val small = ShortArray(16) { (it - 8).toShort() }
        val smallBytes = shortBytes(small)
        processor.queueInput(directBuffer(smallBytes))
        assertArrayEquals(
            reference(C.ENCODING_PCM_16BIT, smallBytes),
            toFloatArray(processor.getOutput()),
            0f,
        )
    }

    private fun assertInactive(encoding: Int) {
        val processor = PcmToFloatProcessor()
        processor.configure(AudioProcessor.AudioFormat(rate, channels, encoding))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        assertFalse("processor should be inactive for encoding $encoding", processor.isActive)
    }

    private fun convert(encoding: Int, bytes: ByteArray): FloatArray {
        val processor = PcmToFloatProcessor()
        processor.configure(AudioProcessor.AudioFormat(rate, channels, encoding))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        processor.queueInput(directBuffer(bytes))
        return toFloatArray(processor.getOutput())
    }

    private fun reference(encoding: Int, bytes: ByteArray): FloatArray {
        val processor = ToFloatPcmAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(rate, channels, encoding))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        processor.queueInput(directBuffer(bytes))
        return toFloatArray(processor.getOutput())
    }

    private fun directBuffer(bytes: ByteArray): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
            put(bytes)
            position(0)
        }

    private fun toFloatArray(buffer: ByteBuffer): FloatArray {
        val copy = buffer.duplicate().order(ByteOrder.nativeOrder())
        val out = FloatArray(copy.remaining() / 4)
        copy.asFloatBuffer().get(out)
        return out
    }

    private fun shortBytes(values: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 2).order(ByteOrder.nativeOrder())
        buffer.asShortBuffer().put(values)
        return buffer.array()
    }

    private fun int32Bytes(values: IntArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.nativeOrder())
        buffer.asIntBuffer().put(values)
        return buffer.array()
    }

    private fun pcm24Bytes(values: IntArray): ByteArray {
        val bytes = ByteArray(values.size * 3)
        for (i in values.indices) {
            val value = values[i]
            bytes[i * 3] = (value and 0xFF).toByte()
            bytes[i * 3 + 1] = ((value shr 8) and 0xFF).toByte()
            bytes[i * 3 + 2] = ((value shr 16) and 0xFF).toByte()
        }
        return bytes
    }
}
