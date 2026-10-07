package me.misa198.airmedy.player.media3

import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ToFloatPcmAudioProcessor
import com.google.common.primitives.ImmutableIntArray
import me.misa198.airmedy.player.dsp.EqualizerProcessor
import me.misa198.airmedy.player.dsp.GainProcessor
import me.misa198.airmedy.player.dsp.PreampProcessor
import me.misa198.airmedy.player.dsp.StereoWidthProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow

/**
 * JVM tests pinning the behaviour of [FloatChainAudioSink] (T048a). The class is a test-first stub
 * today, so these fail on assertions until T048b implements it.
 */
@OptIn(UnstableApi::class)
class FloatChainAudioSinkTest {

    private val rate = 48000

    @Test
    fun configureRewritesFormatToFloatAndCopiesConfig() {
        val fake = FakeAudioSink()
        val sink = FloatChainAudioSink(fake, standardChain())
        val format = pcmFormat(
            sampleRate = 96000,
            channelCount = 2,
            encoding = C.ENCODING_PCM_24BIT,
            encoderDelay = 576,
            encoderPadding = 1000,
        )
        val config = AudioSink.AudioSinkConfig.Builder(format)
            .setPreferredBufferSizeOverride(12345)
            .setOutputChannelMapping(ImmutableIntArray.of(1, 0))
            .setTimeline(Timeline.EMPTY)
            .build()

        sink.configure(config)

        assertEquals(1, fake.configs.size)
        val received = fake.configs.last()
        val out = received.format
        assertEquals(MimeTypes.AUDIO_RAW, out.sampleMimeType)
        assertEquals(C.ENCODING_PCM_FLOAT, out.pcmEncoding)
        assertEquals(96000, out.sampleRate)
        assertEquals(2, out.channelCount)
        // Encoder delay/padding are trimmed in the wrapper before the chain, as the int path did (owner 2026-10-07,
        // T048b round 4: the gapless characterization measures after the chain), so the inner sink trims nothing.
        assertEquals(0, out.encoderDelay)
        assertEquals(0, out.encoderPadding)
        assertEquals(12345, received.preferredBufferSizeOverride)
        assertEquals(ImmutableIntArray.of(1, 0), received.outputChannelMapping)
    }

    @Test
    fun sixteenBitBecomesExactFloat() {
        val samples = shortArrayOf(0, 1, -1, 32767, -32768, 12345)
        val bytes = shortBytes(samples)
        val expected = toFloatReference(rate, 2, C.ENCODING_PCM_16BIT, bytes)
        val fake = FakeAudioSink()
        val sink = FloatChainAudioSink(fake, standardChain())
        sink.configure(sinkConfig(pcmFormat(rate, 2, C.ENCODING_PCM_16BIT)))

        assertTrue(drive(sink, directBuffer(bytes)))

        assertArrayEquals(expected, asFloatArray(fake.received.toByteArray()), 0f)
    }

    @Test
    fun twentyFourBitKeepsFullResolution() {
        val values = intArrayOf(0, 1, -1, 127, 128, 255, 256, 257, 65535, 65536, 8388607, -8388608)
        val bytes = pcm24Bytes(values)
        val expected = toFloatReference(96000, 2, C.ENCODING_PCM_24BIT, bytes)
        val fake = FakeAudioSink()
        val sink = FloatChainAudioSink(fake, standardChain())
        sink.configure(sinkConfig(pcmFormat(96000, 2, C.ENCODING_PCM_24BIT)))

        assertTrue(drive(sink, directBuffer(bytes)))

        assertArrayEquals(expected, asFloatArray(fake.received.toByteArray()), 0f)
        for (i in expected.indices) {
            for (j in i + 1 until expected.size) {
                assertTrue(
                    "24-bit values ${values[i]} and ${values[j]} must convert to distinct floats",
                    expected[i].toRawBits() != expected[j].toRawBits(),
                )
            }
        }
    }

    @Test
    fun thirtyTwoBitIntKeepsFullResolution() {
        val values = intArrayOf(0, 1, -1, 255, 256, 65535, 65536, 16777215, 16777216, 12345678, Int.MAX_VALUE, Int.MIN_VALUE)
        val bytes = int32Bytes(values)
        val expected = toFloatReference(rate, 2, C.ENCODING_PCM_32BIT, bytes)
        val fake = FakeAudioSink()
        val sink = FloatChainAudioSink(fake, standardChain())
        sink.configure(sinkConfig(pcmFormat(rate, 2, C.ENCODING_PCM_32BIT)))

        assertTrue(drive(sink, directBuffer(bytes)))

        assertArrayEquals(expected, asFloatArray(fake.received.toByteArray()), 0f)
        for (i in expected.indices) {
            for (j in i + 1 until expected.size) {
                assertTrue(
                    "32-bit values ${values[i]} and ${values[j]} must convert to distinct floats",
                    expected[i].toRawBits() != expected[j].toRawBits(),
                )
            }
        }
    }

    @Test
    fun floatAboveFullScaleIsNotClipped() {
        val values = floatArrayOf(1.5f, -2.0f, 0.25f, -1.25f)
        val bytes = floatBytes(values)
        val fake = FakeAudioSink()
        val sink = FloatChainAudioSink(fake, standardChain())
        sink.configure(sinkConfig(pcmFormat(rate, 2, C.ENCODING_PCM_FLOAT)))

        assertTrue(drive(sink, directBuffer(bytes)))

        assertArrayEquals(values, asFloatArray(fake.received.toByteArray()), 0f)
    }

    @Test
    fun chainIsApplied() {
        val values = intArrayOf(1_000_000, -1_000_000, 500_000, -500_000, 0, 8_388_607)
        val bytes = pcm24Bytes(values)
        val reference = toFloatReference(rate, 2, C.ENCODING_PCM_24BIT, bytes)
        val gain = 10.0.pow(-6.0 / 20.0).toFloat()
        val preamp = PreampProcessor()
        preamp.setPreampDb(-6f)
        val fake = FakeAudioSink()
        val sink = FloatChainAudioSink(
            fake,
            listOf(GainProcessor(), StereoWidthProcessor(), EqualizerProcessor(), preamp),
        )
        sink.configure(sinkConfig(pcmFormat(rate, 2, C.ENCODING_PCM_24BIT)))

        assertTrue(drive(sink, directBuffer(bytes)))

        val actual = asFloatArray(fake.received.toByteArray())
        assertEquals(reference.size, actual.size)
        for (i in reference.indices) {
            val expected = reference[i] * gain
            assertEquals("sample $i", expected, actual[i], maxOf(1e-6f, abs(expected) * 1e-6f))
        }
    }

    @Test
    fun backpressureNeverReprocessesOrLosesData() {
        val bytes = shortBytes(ShortArray(480 * 2) { ((it * 37) % 2000 - 1000).toShort() })
        val config = sinkConfig(pcmFormat(rate, 2, C.ENCODING_PCM_16BIT))

        val unthrottledFake = FakeAudioSink()
        val unthrottledSink = FloatChainAudioSink(unthrottledFake, standardChain())
        unthrottledSink.configure(config)
        assertTrue(drive(unthrottledSink, directBuffer(bytes)))
        val expectedBytes = unthrottledFake.received.toByteArray()

        val counting = FrameCountingProcessor()
        val throttledFake = FakeAudioSink()
        throttledFake.maxBytesPerCall = 100
        val throttledSink = FloatChainAudioSink(throttledFake, standardChain() + counting)
        throttledSink.configure(config)

        assertTrue(drive(throttledSink, directBuffer(bytes)))

        assertArrayEquals(expectedBytes, throttledFake.received.toByteArray())
        assertEquals("counting processor saw each input frame exactly once", 480L, counting.framesSeen)
    }

    @Test
    fun flushDropsPendingOutput() {
        val first = pcm24Bytes(intArrayOf(1_000_000, -1_000_000, 500_000, -500_000))
        val second = pcm24Bytes(intArrayOf(8_388_607, -8_388_608, 300_000, -300_000))
        val fake = FakeAudioSink()
        val sink = FloatChainAudioSink(fake, standardChain())
        sink.configure(sinkConfig(pcmFormat(rate, 2, C.ENCODING_PCM_24BIT)))

        fake.maxBytesPerCall = 0
        assertFalse(sink.handleBuffer(directBuffer(first), 0L, 1))
        sink.flush()
        assertEquals(1, fake.flushCalls)

        fake.maxBytesPerCall = Int.MAX_VALUE
        assertTrue(drive(sink, directBuffer(second), presentationTimeUs = 1_000_000L))

        val expectedSecond = toFloatReference(rate, 2, C.ENCODING_PCM_24BIT, second)
        assertArrayEquals(expectedSecond, asFloatArray(fake.received.toByteArray()), 0f)
        val expectedFirst = toFloatReference(rate, 2, C.ENCODING_PCM_24BIT, first)
        assertFalse(
            "the dropped buffer must never reach the delegate",
            fake.received.toByteArray().contentEquals(floatBytes(expectedFirst)),
        )
    }

    @Test
    fun reconfigureDropsPendingOutput() {
        val first = pcm24Bytes(intArrayOf(1_000_000, -1_000_000, 500_000, -500_000))
        val second = pcm24Bytes(intArrayOf(8_388_607, -8_388_608, 300_000, -300_000))
        val fake = FakeAudioSink()
        val sink = FloatChainAudioSink(fake, standardChain())
        sink.configure(sinkConfig(pcmFormat(rate, 2, C.ENCODING_PCM_24BIT)))

        fake.maxBytesPerCall = 0
        assertFalse(sink.handleBuffer(directBuffer(first), 0L, 1))
        sink.configure(sinkConfig(pcmFormat(44100, 2, C.ENCODING_PCM_24BIT)))
        assertEquals(2, fake.configs.size)
        assertEquals(C.ENCODING_PCM_FLOAT, fake.configs.last().format.pcmEncoding)

        fake.maxBytesPerCall = Int.MAX_VALUE
        assertTrue(drive(sink, directBuffer(second), presentationTimeUs = 1_000_000L))

        val expectedSecond = toFloatReference(44100, 2, C.ENCODING_PCM_24BIT, second)
        assertArrayEquals(expectedSecond, asFloatArray(fake.received.toByteArray()), 0f)
    }

    @Test
    fun nonPcmIsForwardedUntouched() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AC3)
            .setSampleRate(48000)
            .setChannelCount(2)
            .build()
        val fake = FakeAudioSink()
        val sink = FloatChainAudioSink(fake, standardChain())
        sink.configure(sinkConfig(format))

        assertEquals(format, fake.configs.last().format)

        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        assertTrue(drive(sink, directBuffer(bytes)))
        assertArrayEquals(bytes, fake.received.toByteArray())

        val ac3Length = fake.received.size()
        val pcmBytes = pcm24Bytes(intArrayOf(1_000_000, -1_000_000, 500_000, -500_000, 250_000, -250_000))
        sink.configure(sinkConfig(pcmFormat(rate, 2, C.ENCODING_PCM_24BIT)))
        assertTrue(drive(sink, directBuffer(pcmBytes), presentationTimeUs = 2_000_000L))

        val lastConfig = fake.configs.last().format
        assertEquals(MimeTypes.AUDIO_RAW, lastConfig.sampleMimeType)
        assertEquals(C.ENCODING_PCM_FLOAT, lastConfig.pcmEncoding)

        val pcmReceived = fake.received.toByteArray().copyOfRange(ac3Length, fake.received.size())
        val expected = toFloatReference(rate, 2, C.ENCODING_PCM_24BIT, pcmBytes)
        assertArrayEquals(expected, asFloatArray(pcmReceived), 0f)
    }

    @Test
    fun endOfStreamWaitsForPendingOutput() {
        val bytes = shortBytes(ShortArray(64 * 2) { ((it * 13) % 500 - 250).toShort() })
        val fake = FakeAudioSink()
        val sink = FloatChainAudioSink(fake, standardChain())
        sink.configure(sinkConfig(pcmFormat(rate, 2, C.ENCODING_PCM_16BIT)))

        fake.maxBytesPerCall = 0
        val buffer = directBuffer(bytes)
        assertFalse(sink.handleBuffer(buffer, 0L, 1))
        assertTrue("pending output must be reported", sink.hasPendingData())

        fake.maxBytesPerCall = Int.MAX_VALUE
        assertTrue(sink.handleBuffer(buffer, 0L, 1))
        assertFalse("pending output must be delivered", sink.hasPendingData())
        sink.playToEndOfStream()
        assertEquals(1, fake.playToEndOfStreamCalls)
    }

    @Test
    fun formatSupportIsReportedAsFloatTranscoding() {
        val fake = FakeAudioSink(supportedEncodings = setOf(C.ENCODING_PCM_FLOAT))
        val sink = FloatChainAudioSink(fake, emptyList())

        assertEquals(
            AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING,
            sink.getFormatSupport(pcmFormat(rate, 2, C.ENCODING_PCM_24BIT)),
        )
        assertEquals(
            AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING,
            sink.getFormatSupport(pcmFormat(rate, 2, C.ENCODING_PCM_16BIT)),
        )
        assertEquals(
            AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY,
            sink.getFormatSupport(pcmFormat(rate, 2, C.ENCODING_PCM_FLOAT)),
        )
        assertTrue(sink.supportsFormat(pcmFormat(rate, 2, C.ENCODING_PCM_FLOAT)))
    }

    private fun standardChain(): List<AudioProcessor> = listOf(
        GainProcessor(),
        StereoWidthProcessor(),
        EqualizerProcessor(),
        PreampProcessor(),
    )

    private fun drive(
        sink: FloatChainAudioSink,
        buffer: ByteBuffer,
        presentationTimeUs: Long = 0L,
        maxIterations: Int = 100_000,
    ): Boolean {
        var iterations = 0
        while (!sink.handleBuffer(buffer, presentationTimeUs, 1)) {
            if (++iterations > maxIterations) error("handleBuffer never completed")
        }
        return true
    }

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

    private fun sinkConfig(
        format: Format,
        preferredBufferSizeOverride: Int = 0,
        outputChannelMapping: ImmutableIntArray = ImmutableIntArray.of(),
    ): AudioSink.AudioSinkConfig = AudioSink.AudioSinkConfig.Builder(format)
        .setPreferredBufferSizeOverride(preferredBufferSizeOverride)
        .setOutputChannelMapping(outputChannelMapping)
        .setTimeline(Timeline.EMPTY)
        .build()

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

    private fun int32Bytes(values: IntArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.nativeOrder())
        buffer.asIntBuffer().put(values)
        return buffer.array()
    }

    private fun floatBytes(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.nativeOrder())
        buffer.asFloatBuffer().put(values)
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

    @OptIn(UnstableApi::class)
    private class FrameCountingProcessor : BaseAudioProcessor() {

        var framesSeen = 0L
            private set

        override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
            if (inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
                throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
            }
            return inputAudioFormat
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            framesSeen += inputBuffer.remaining() / inputAudioFormat.bytesPerFrame
            val output = replaceOutputBuffer(inputBuffer.remaining())
            output.put(inputBuffer)
            output.flip()
        }
    }

    @OptIn(UnstableApi::class)
    private class FakeAudioSink(
        private val supportedEncodings: Set<Int> = setOf(C.ENCODING_PCM_FLOAT),
    ) : AudioSink {

        val configs = mutableListOf<AudioSink.AudioSinkConfig>()
        val received = ByteArrayOutputStream()
        var maxBytesPerCall: Int = Int.MAX_VALUE
        var flushCalls = 0
        var resetCalls = 0
        var playToEndOfStreamCalls = 0

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
            if (format.pcmEncoding in supportedEncodings) {
                AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
            } else {
                AudioSink.SINK_FORMAT_UNSUPPORTED
            }

        override fun supportsFormat(format: Format): Boolean =
            getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

        override fun playToEndOfStream() {
            playToEndOfStreamCalls++
        }

        override fun flush() {
            flushCalls++
        }

        override fun reset() {
            resetCalls++
        }

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
