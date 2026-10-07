package me.misa198.airmedy.player.media3

import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ToFloatPcmAudioProcessor
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests pinning the passthrough behaviour of [FloatChainAudioSink] when its pipeline has no
 * active processor (float input with an empty/all-inactive chain) — the input is forwarded straight
 * to the delegate instead of being silently dropped.
 */
@OptIn(UnstableApi::class)
class FloatChainAudioSinkPassthroughTest {

    private val rate = 48000

    @Test
    fun floatInputWithEmptyChainIsForwardedExactly() {
        val values = floatArrayOf(0.5f, -0.25f, 1.5f, -2.0f)
        val bytes = floatBytes(values)
        val fake = FakeAudioSink()
        val sink = FloatChainAudioSink(fake, emptyList())
        sink.configure(sinkConfig(floatFormat(rate, 2)))

        val buffer = directBuffer(bytes)
        assertTrue(sink.handleBuffer(buffer, 0L, 1))

        assertFalse("input buffer must be fully consumed", buffer.hasRemaining())
        assertArrayEquals(values, asFloatArray(fake.received.toByteArray()), 0f)
        assertEquals(C.ENCODING_PCM_FLOAT, fake.configs.last().format.pcmEncoding)
    }

    @Test
    fun floatInputWithEmptyChainHonoursBackpressure() {
        val values = floatArrayOf(0.5f, -0.25f, 1.5f, -2.0f)
        val bytes = floatBytes(values)
        val fake = FakeAudioSink()
        fake.maxBytesPerCall = 4
        val sink = FloatChainAudioSink(fake, emptyList())
        sink.configure(sinkConfig(floatFormat(rate, 2)))

        val buffer = directBuffer(bytes)
        var iterations = 0
        while (!sink.handleBuffer(buffer, 0L, 1)) {
            if (++iterations > 100) error("handleBuffer never completed")
        }

        assertFalse("input buffer must be fully consumed", buffer.hasRemaining())
        assertArrayEquals(values, asFloatArray(fake.received.toByteArray()), 0f)
    }

    @Test
    fun sixteenBitWithEmptyChainStillConverts() {
        val shorts = shortArrayOf(0, 16384, -32768, 32767)
        val bytes = shortBytes(shorts)
        val fake = FakeAudioSink()
        val sink = FloatChainAudioSink(fake, emptyList())
        sink.configure(sinkConfig(pcm16Format(rate, 2)))

        assertTrue(sink.handleBuffer(directBuffer(bytes), 0L, 1))

        val expected = toFloatReference(rate, 2, C.ENCODING_PCM_16BIT, bytes)
        assertArrayEquals(expected, asFloatArray(fake.received.toByteArray()), 0f)
        assertEquals(C.ENCODING_PCM_FLOAT, fake.configs.last().format.pcmEncoding)
    }

    private fun floatFormat(sampleRate: Int, channelCount: Int): Format = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setSampleRate(sampleRate)
        .setChannelCount(channelCount)
        .setPcmEncoding(C.ENCODING_PCM_FLOAT)
        .build()

    private fun pcm16Format(sampleRate: Int, channelCount: Int): Format = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setSampleRate(sampleRate)
        .setChannelCount(channelCount)
        .setPcmEncoding(C.ENCODING_PCM_16BIT)
        .build()

    private fun sinkConfig(format: Format): AudioSink.AudioSinkConfig =
        AudioSink.AudioSinkConfig.Builder(format).build()

    private fun toFloatReference(
        sampleRate: Int,
        channelCount: Int,
        encoding: Int,
        bytes: ByteArray,
    ): FloatArray {
        val processor = ToFloatPcmAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(sampleRate, channelCount, encoding))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        processor.queueInput(directBuffer(bytes))
        return asFloatArray(processor.getOutput())
    }

    private fun directBuffer(bytes: ByteArray): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
            put(bytes)
            position(0)
        }

    private fun asFloatArray(buffer: ByteBuffer): FloatArray {
        val copy = buffer.duplicate().order(ByteOrder.nativeOrder())
        val out = FloatArray(copy.remaining() / 4)
        copy.asFloatBuffer().get(out)
        return out
    }

    private fun asFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        val out = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(out)
        return out
    }

    private fun shortBytes(values: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 2).order(ByteOrder.nativeOrder())
        buffer.asShortBuffer().put(values)
        return buffer.array()
    }

    private fun floatBytes(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.nativeOrder())
        buffer.asFloatBuffer().put(values)
        return buffer.array()
    }

    @OptIn(UnstableApi::class)
    private class FakeAudioSink : AudioSink {

        val configs = mutableListOf<AudioSink.AudioSinkConfig>()
        val received = ByteArrayOutputStream()
        var maxBytesPerCall: Int = Int.MAX_VALUE

        override fun configure(config: AudioSink.AudioSinkConfig) {
            configs += config
        }

        override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
            if (maxBytesPerCall <= 0) return false
            val take = minOf(buffer.remaining(), maxBytesPerCall)
            if (take > 0) {
                val copy = ByteArray(take)
                buffer.get(copy)
                received.write(copy)
            }
            return !buffer.hasRemaining()
        }

        override fun getFormatSupport(format: Format): Int =
            if (format.pcmEncoding == C.ENCODING_PCM_FLOAT) {
                AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
            } else {
                AudioSink.SINK_FORMAT_UNSUPPORTED
            }

        override fun supportsFormat(format: Format): Boolean =
            getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

        override fun playToEndOfStream() = Unit

        override fun flush() = Unit

        override fun reset() = Unit

        override fun hasPendingData(): Boolean = false

        override fun isEnded(): Boolean = false

        override fun setListener(listener: AudioSink.Listener) = Unit

        override fun getCurrentPositionUs(initialize: Boolean): Long = 0L

        override fun play() = Unit

        override fun handleDiscontinuity() = Unit

        override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = Unit

        override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT

        override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) = Unit

        override fun getSkipSilenceEnabled(): Boolean = false

        override fun setAudioAttributes(audioAttributes: AudioAttributes) = Unit

        override fun getAudioAttributes(): AudioAttributes = AudioAttributes.DEFAULT

        override fun setAudioSessionId(audioSessionId: Int) = Unit

        override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = Unit

        override fun getAudioTrackBufferSizeUs(): Long = 0L

        override fun enableTunnelingV21() = Unit

        override fun disableTunneling() = Unit

        override fun setVolume(volume: Float) = Unit

        override fun pause() = Unit
    }
}
