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
import me.misa198.airmedy.player.dsp.EqualizerProcessor
import me.misa198.airmedy.player.dsp.GainProcessor
import me.misa198.airmedy.player.dsp.PreampProcessor
import me.misa198.airmedy.player.dsp.StereoWidthProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests pinning [FloatChainAudioSink]'s trimming of encoder delay/padding before the DSP chain
 * (T048b round 4): the wrapper trims, so the inner sink receives delay/padding 0.
 */
@OptIn(UnstableApi::class)
class FloatChainAudioSinkTrimTest {

    private val rate = 48000

    @Test
    fun trimsEncoderDelayAndPadding() {
        val fake = FakeAudioSink()
        val sink = FloatChainAudioSink(fake, standardChain())
        sink.configure(sinkConfig(pcmFormat(rate, 2, C.ENCODING_PCM_16BIT, encoderDelay = 4, encoderPadding = 3)))
        assertEquals(0, fake.configs.last().format.encoderDelay)
        assertEquals(0, fake.configs.last().format.encoderPadding)

        // 20 stereo frames of distinct values.
        val firstSamples = ShortArray(40) { it.toShort() }
        val firstBytes = shortBytes(firstSamples)
        val allFloats = toFloatReference(rate, 2, C.ENCODING_PCM_16BIT, firstBytes)
        assertTrue(sink.handleBuffer(directBuffer(firstBytes), 0L, 1))

        // Trim 4 start frames and hold back 3 end frames: frames 4..16 (samples 8..33).
        assertArrayEquals(allFloats.copyOfRange(8, 34), asFloatArray(fake.received.toByteArray()), 0f)

        // Reconfigure with no delay/padding; the held-back 3 padding frames are dropped.
        sink.configure(sinkConfig(pcmFormat(rate, 2, C.ENCODING_PCM_16BIT)))
        assertEquals(0, fake.configs.last().format.encoderDelay)
        assertEquals(0, fake.configs.last().format.encoderPadding)

        val secondSamples = ShortArray(10) { (100 + it).toShort() }
        val secondBytes = shortBytes(secondSamples)
        assertTrue(sink.handleBuffer(directBuffer(secondBytes), 0L, 1))

        val received = asFloatArray(fake.received.toByteArray())
        assertEquals("held-back padding must never reach the delegate", 26 + 10, received.size)
        assertArrayEquals(
            toFloatReference(rate, 2, C.ENCODING_PCM_16BIT, secondBytes),
            received.copyOfRange(26, received.size),
            0f,
        )
    }

    @Test
    fun seekFlushDoesNotRetrimStart() {
        val fake = FakeAudioSink()
        val sink = FloatChainAudioSink(fake, standardChain())
        sink.configure(sinkConfig(pcmFormat(rate, 2, C.ENCODING_PCM_16BIT, encoderDelay = 4, encoderPadding = 0)))
        assertEquals(0, fake.configs.last().format.encoderDelay)
        assertEquals(0, fake.configs.last().format.encoderPadding)

        // 10 stereo frames; 4 are trimmed at the start.
        val firstSamples = ShortArray(20) { it.toShort() }
        val firstBytes = shortBytes(firstSamples)
        val allFloats = toFloatReference(rate, 2, C.ENCODING_PCM_16BIT, firstBytes)
        assertTrue(sink.handleBuffer(directBuffer(firstBytes), 0L, 1))
        assertArrayEquals(allFloats.copyOfRange(8, 20), asFloatArray(fake.received.toByteArray()), 0f)

        sink.flush()

        // Following buffer must NOT be start-trimmed again.
        val secondSamples = ShortArray(10) { (100 + it).toShort() }
        val secondBytes = shortBytes(secondSamples)
        assertTrue(sink.handleBuffer(directBuffer(secondBytes), 0L, 1))

        val received = asFloatArray(fake.received.toByteArray())
        assertEquals(12 + 10, received.size)
        assertArrayEquals(
            toFloatReference(rate, 2, C.ENCODING_PCM_16BIT, secondBytes),
            received.copyOfRange(12, received.size),
            0f,
        )
    }

    private fun standardChain(): List<AudioProcessor> = listOf(
        GainProcessor(),
        StereoWidthProcessor(),
        EqualizerProcessor(),
        PreampProcessor(),
    )

    private fun pcmFormat(
        sampleRate: Int,
        channelCount: Int,
        encoding: Int,
        encoderDelay: Int = 0,
        encoderPadding: Int = 0,
    ): Format = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setSampleRate(sampleRate)
        .setChannelCount(channelCount)
        .setPcmEncoding(encoding)
        .setEncoderDelay(encoderDelay)
        .setEncoderPadding(encoderPadding)
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

    @OptIn(UnstableApi::class)
    private class FakeAudioSink : AudioSink {

        val configs = mutableListOf<AudioSink.AudioSinkConfig>()
        val received = ByteArrayOutputStream()

        override fun configure(config: AudioSink.AudioSinkConfig) {
            configs += config
        }

        override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
            val copy = ByteArray(buffer.remaining())
            buffer.get(copy)
            received.write(copy)
            return true
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
