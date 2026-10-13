package com.amwolfstein.sistrum.analysis

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.amwolfstein.sistrum.player.dsp.PcmToFloatProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [CaptureAudioSink] driven directly (no ExoPlayer, no Robolectric).
 * Verifies the float conversion matches [PcmToFloatProcessor] bit for bit for the
 * 16/24/32-bit paths and is the identity for float, that position/ended/flush behave,
 * and that a mid-stream format change is reported.
 */
class CaptureAudioSinkTest {

    @Test
    fun intEncodingsConvertIdenticallyToPcmToFloatProcessor() {
        for ((encoding, samples) in intFixtures()) {
            val recorder = Recorder()
            val sink = CaptureAudioSink(recorder)
            sink.configure(config(pcmFormat(RATE, CHANNELS, encoding)))
            val buffer = ByteBuffer.wrap(samples).order(ByteOrder.LITTLE_ENDIAN)
            assertTrue("handleBuffer should consume ${encodingName(encoding)} input", sink.handleBuffer(buffer, 0L, 1))
            assertEquals("buffer should be fully consumed for ${encodingName(encoding)}", buffer.limit(), buffer.position())

            val expected = viaPcmToFloat(encoding, samples)
            assertTrue("expected ${encodingName(encoding)} conversion should be non-empty", expected.isNotEmpty())
            assertArrayEquals(
                "${encodingName(encoding)} conversion should match PcmToFloatProcessor",
                expected,
                recorder.all(),
                0f,
            )
            assertEquals(RATE, recorder.sampleRate)
            assertEquals(CHANNELS, recorder.channelCount)
        }
    }

    @Test
    fun floatInputIsIdentityAndStereoInterleavingPreserved() {
        val recorder = Recorder()
        val values = floatArrayOf(0.5f, -0.5f, 0.25f, -0.25f, 1f, -1f, 0f, 0.123456f)
        val bytes = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach { putFloat(it) }
        }.array()

        val sink = CaptureAudioSink(recorder)
        sink.configure(config(pcmFormat(RATE, 2, C.ENCODING_PCM_FLOAT)))
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertTrue(sink.handleBuffer(buffer, 0L, 1))
        assertEquals(buffer.limit(), buffer.position())

        assertArrayEquals("float input should pass through identically", values, recorder.all(), 0f)
        assertEquals(RATE, recorder.sampleRate)
        assertEquals(2, recorder.channelCount)
        for (i in values.indices) {
            assertEquals("stereo interleaving at sample $i", values[i], recorder.all()[i], 0f)
        }
    }

    @Test
    fun positionAdvancesEndedAndFlushReset() {
        val recorder = Recorder()
        val frames = 480
        val samples = ShortArray(frames * CHANNELS) { (it % 200).toShort() }
        val bytes = shortBytes(samples)
        val startUs = 1_000_000_000_000L

        val sink = CaptureAudioSink(recorder)
        sink.configure(config(pcmFormat(RATE, CHANNELS, C.ENCODING_PCM_16BIT)))
        assertEquals(
            "position should be CURRENT_POSITION_NOT_SET before data",
            AudioSink.CURRENT_POSITION_NOT_SET,
            sink.getCurrentPositionUs(false),
        )
        assertFalse("isEnded should be false before playToEndOfStream", sink.isEnded)

        sink.handleBuffer(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN), startUs, 1)
        val expectedPosition = startUs + frames * C.MICROS_PER_SECOND / RATE
        assertEquals("position should be startUs + frames * 1e6 / rate", expectedPosition, sink.getCurrentPositionUs(false))
        assertFalse("isEnded should still be false after handleBuffer", sink.isEnded)

        sink.playToEndOfStream()
        assertTrue("isEnded should be true after playToEndOfStream", sink.isEnded)

        sink.flush()
        assertEquals(
            "flush should reset position to CURRENT_POSITION_NOT_SET",
            AudioSink.CURRENT_POSITION_NOT_SET,
            sink.getCurrentPositionUs(false),
        )
        assertFalse("flush should clear isEnded", sink.isEnded)
    }

    /** Without pending data the renderer is never ready and a long decode deadlocks in BUFFERING (T051d, device). */
    @Test
    fun pendingDataFromFirstBufferUntilEndOfStream() {
        val bytes = shortBytes(ShortArray(480 * CHANNELS) { (it % 200).toShort() })
        val sink = CaptureAudioSink(Recorder())
        sink.configure(config(pcmFormat(RATE, CHANNELS, C.ENCODING_PCM_16BIT)))
        assertFalse("no pending data before the first buffer", sink.hasPendingData())

        sink.handleBuffer(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN), 1_000_000_000_000L, 1)
        assertTrue("pending data while the stream runs", sink.hasPendingData())

        sink.playToEndOfStream()
        assertFalse("no pending data after end of stream", sink.hasPendingData())

        sink.flush()
        assertFalse("no pending data after flush", sink.hasPendingData())
    }

    @Test
    fun dataAfterFormatChangeIsNotDelivered() {
        val recorder = Recorder()
        val sink = CaptureAudioSink(recorder)
        sink.configure(config(pcmFormat(RATE, CHANNELS, C.ENCODING_PCM_16BIT)))
        sink.handleBuffer(
            ByteBuffer.wrap(shortBytes(shortArrayOf(1, 2, 3, 4))).order(ByteOrder.LITTLE_ENDIAN),
            0L,
            1,
        )
        val deliveredBefore = recorder.all().size
        assertTrue("data should be delivered before a format change", deliveredBefore > 0)

        sink.configure(config(pcmFormat(96_000, CHANNELS, C.ENCODING_PCM_16BIT)))
        assertTrue("a different rate should report a format change", sink.formatChanged)

        sink.handleBuffer(
            ByteBuffer.wrap(shortBytes(shortArrayOf(5, 6, 7, 8))).order(ByteOrder.LITTLE_ENDIAN),
            0L,
            1,
        )
        assertEquals("no data should be delivered after a format change", deliveredBefore, recorder.all().size)
    }

    @Test
    fun nonPcmUnsupportedAndFormatChangeReported() {
        val recorder = Recorder()
        val sink = CaptureAudioSink(recorder)
        val nonPcm = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AC3)
            .setSampleRate(RATE)
            .setChannelCount(CHANNELS)
            .build()
        assertFalse("non-PCM format should not be supported", sink.supportsFormat(nonPcm))
        assertEquals(AudioSink.SINK_FORMAT_UNSUPPORTED, sink.getFormatSupport(nonPcm))

        sink.configure(config(pcmFormat(48_000, CHANNELS, C.ENCODING_PCM_16BIT)))
        sink.handleBuffer(
            ByteBuffer.wrap(shortBytes(ShortArray(CHANNELS))).order(ByteOrder.LITTLE_ENDIAN),
            0L,
            1,
        )
        assertFalse("no format change before a different rate", sink.formatChanged)

        sink.configure(config(pcmFormat(96_000, CHANNELS, C.ENCODING_PCM_16BIT)))
        assertTrue("a different rate after data was handled should report a format change", sink.formatChanged)
    }

    @Test
    fun sameRateReconfigureDoesNotReportFormatChange() {
        val recorder = Recorder()
        val sink = CaptureAudioSink(recorder)
        sink.configure(config(pcmFormat(RATE, CHANNELS, C.ENCODING_PCM_16BIT)))
        sink.handleBuffer(
            ByteBuffer.wrap(shortBytes(ShortArray(CHANNELS))).order(ByteOrder.LITTLE_ENDIAN),
            0L,
            1,
        )
        sink.configure(config(pcmFormat(RATE, CHANNELS, C.ENCODING_PCM_16BIT)))
        assertFalse("same rate/channels reconfigure should not report a format change", sink.formatChanged)
    }

    @Test
    fun startPositionDropsEarlierFrames() {
        val channels = CHANNELS
        val totalFrames = 960
        val perBufferFrames = 480
        val cutFrame = 600
        val values = FloatArray(totalFrames * channels) { it.toFloat() }
        val all = floatBytes(values)
        val perBufferBytes = perBufferFrames * channels * 4

        val recorder = Recorder()
        val startPositionUs = cutFrame.toLong() * C.MICROS_PER_SECOND / RATE
        val sink = CaptureAudioSink(recorder, startPositionUs = startPositionUs)
        sink.configure(config(pcmFormat(RATE, channels, C.ENCODING_PCM_FLOAT)))

        sink.handleBuffer(ByteBuffer.wrap(all, 0, perBufferBytes).order(ByteOrder.LITTLE_ENDIAN), 0L, 1)
        sink.handleBuffer(ByteBuffer.wrap(all, perBufferBytes, perBufferBytes).order(ByteOrder.LITTLE_ENDIAN), 0L, 1)

        val expected = values.copyOfRange(cutFrame * channels, totalFrames * channels)
        assertArrayEquals("first delivered sample must be at the start position", expected, recorder.all(), 0f)
        assertEquals((totalFrames - cutFrame).toLong(), sink.framesWritten)
    }

    @Test
    fun startPositionHonoursOutputStreamOffset() {
        val channels = CHANNELS
        val totalFrames = 960
        val perBufferFrames = 480
        val cutFrame = 600
        val offset = 1_000_000_000_000L
        val values = FloatArray(totalFrames * channels) { it.toFloat() }
        val all = floatBytes(values)
        val perBufferBytes = perBufferFrames * channels * 4

        val recorder = Recorder()
        val startPositionUs = cutFrame.toLong() * C.MICROS_PER_SECOND / RATE
        val sink = CaptureAudioSink(recorder, startPositionUs = startPositionUs)
        sink.configure(config(pcmFormat(RATE, channels, C.ENCODING_PCM_FLOAT)))
        sink.setOutputStreamOffsetUs(offset)

        sink.handleBuffer(ByteBuffer.wrap(all, 0, perBufferBytes).order(ByteOrder.LITTLE_ENDIAN), offset, 1)
        sink.handleBuffer(
            ByteBuffer.wrap(all, perBufferBytes, perBufferBytes).order(ByteOrder.LITTLE_ENDIAN),
            offset + perBufferFrames.toLong() * C.MICROS_PER_SECOND / RATE,
            1,
        )

        val expected = values.copyOfRange(cutFrame * channels, totalFrames * channels)
        assertArrayEquals("first delivered sample must be at the media start position", expected, recorder.all(), 0f)
        assertEquals((totalFrames - cutFrame).toLong(), sink.framesWritten)
    }

    @Test
    fun outputStreamOffsetDoesNotChangePosition() {
        val recorder = Recorder()
        val frames = 480
        val bytes = shortBytes(ShortArray(frames * CHANNELS) { (it % 200).toShort() })
        val startUs = 1_000_000_000_000L

        val sink = CaptureAudioSink(recorder)
        sink.configure(config(pcmFormat(RATE, CHANNELS, C.ENCODING_PCM_16BIT)))
        sink.handleBuffer(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN), startUs, 1)
        val before = sink.getCurrentPositionUs(false)
        sink.setOutputStreamOffsetUs(123_456L)
        val after = sink.getCurrentPositionUs(false)

        assertEquals("position should be unaffected by the output-stream offset", before, after)
        assertEquals(startUs + frames * C.MICROS_PER_SECOND / RATE, after)
    }

    @Test
    fun startPositionZeroIsUnchanged() {
        val channels = CHANNELS
        val frames = 480
        val values = FloatArray(frames * channels) { (it % 200).toFloat() }
        val bytes = floatBytes(values)

        val recorder = Recorder()
        val sink = CaptureAudioSink(recorder, startPositionUs = 0L)
        sink.configure(config(pcmFormat(RATE, channels, C.ENCODING_PCM_FLOAT)))
        sink.handleBuffer(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN), 0L, 1)

        assertArrayEquals(values, recorder.all(), 0f)
        assertEquals(frames.toLong(), sink.framesWritten)
    }

    private class Recorder : PcmConsumer {
        val chunks = mutableListOf<FloatArray>()
        var sampleRate = 0
        var channelCount = 0

        override fun accept(samples: FloatArray, frames: Int, sampleRate: Int, channels: Int) {
            this.sampleRate = sampleRate
            this.channelCount = channels
            chunks += samples.copyOf(frames * channels)
        }

        fun all(): FloatArray {
            val total = chunks.sumOf { it.size }
            val out = FloatArray(total)
            var at = 0
            for (chunk in chunks) {
                chunk.copyInto(out, at)
                at += chunk.size
            }
            return out
        }
    }

    private fun pcmFormat(rate: Int, channels: Int, encoding: Int): Format =
        Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setSampleRate(rate)
            .setChannelCount(channels)
            .setPcmEncoding(encoding)
            .build()

    private fun config(format: Format): AudioSink.AudioSinkConfig =
        AudioSink.AudioSinkConfig.Builder(format).build()

    private fun intFixtures(): List<Pair<Int, ByteArray>> = listOf(
        C.ENCODING_PCM_16BIT to shortBytes(
            shortArrayOf(0, -1, 32767, -32768, 1234, -5678, 255, -256),
        ),
        C.ENCODING_PCM_24BIT to int24Bytes(
            intArrayOf(0, -1, 8388607, -8388608, 1234567, -7654321, 255, -256),
        ),
        C.ENCODING_PCM_32BIT to int32Bytes(
            intArrayOf(0, -1, Int.MAX_VALUE, Int.MIN_VALUE, 123456789, -987654321, 255, -256),
        ),
    )

    private fun viaPcmToFloat(encoding: Int, bytes: ByteArray): FloatArray {
        val processor = PcmToFloatProcessor()
        processor.configure(AudioProcessor.AudioFormat(RATE, CHANNELS, encoding))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        processor.queueInput(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN))
        val out = processor.getOutput()
        val count = out.remaining() / 4
        val floats = FloatArray(count)
        out.order(ByteOrder.nativeOrder()).asFloatBuffer().get(floats)
        return floats
    }

    private fun shortBytes(shorts: ShortArray): ByteArray =
        ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            shorts.forEach { putShort(it) }
        }.array()

    private fun floatBytes(values: FloatArray): ByteArray =
        ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach { putFloat(it) }
        }.array()

    private fun int24Bytes(values: IntArray): ByteArray {
        val out = ByteArray(values.size * 3)
        for (i in values.indices) {
            val v = values[i]
            out[i * 3] = (v and 0xFF).toByte()
            out[i * 3 + 1] = ((v shr 8) and 0xFF).toByte()
            out[i * 3 + 2] = ((v shr 16) and 0xFF).toByte()
        }
        return out
    }

    private fun int32Bytes(values: IntArray): ByteArray =
        ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach { putInt(it) }
        }.array()

    private fun encodingName(encoding: Int): String = when (encoding) {
        C.ENCODING_PCM_16BIT -> "16-bit"
        C.ENCODING_PCM_24BIT -> "24-bit"
        C.ENCODING_PCM_32BIT -> "32-bit"
        else -> "encoding $encoding"
    }

    private companion object {
        const val RATE = 48_000
        const val CHANNELS = 2
    }
}
