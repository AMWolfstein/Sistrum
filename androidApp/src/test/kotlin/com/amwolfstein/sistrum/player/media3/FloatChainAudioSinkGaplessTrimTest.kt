package com.amwolfstein.sistrum.player.media3

import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import com.amwolfstein.sistrum.player.dsp.EqualizerProcessor
import com.amwolfstein.sistrum.player.dsp.GainProcessor
import com.amwolfstein.sistrum.player.dsp.PreampProcessor
import com.amwolfstein.sistrum.player.dsp.StereoWidthProcessor
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T062i: exact-frame encoder delay/padding guards at sink stream boundaries.
 *
 * Source references below are to the unmodified media3-exoplayer-1.11.1-sources.jar, under
 * androidx/media3/exoplayer/audio/ (MediaCodecRenderer.java is under ../mediacodec/).
 *
 * (i) Final EOS: configure(A), handleBuffer(A)*, playToEndOfStream() repeatedly, isEnded().
 * MediaCodecAudioRenderer.java:679-695 copies encoder delay/padding into the PCM format;
 * :722-727 configures the sink and :934-938 feeds decoded buffers. MediaCodecRenderer.java:
 * 2283-2287 handles an empty codec EOS (or :2337-2347 a final buffer carrying EOS), then
 * :2463-2466 calls renderToEndOfStream; MediaCodecAudioRenderer.java:999-1002 calls the sink's
 * playToEndOfStream. MediaCodecRenderer.java:963-966 repeats this on subsequent renders;
 * AudioSink.java:64-66,755-765 specifies draining and the isEnded completion check.
 * There is no intervening configure, flush, or synthetic empty handleBuffer call at final EOS.
 *
 * (ii) Reused codec, no output-format notification: configure(A), handleBuffer(A)*,
 * setOutputStreamOffsetUs(B), handleDiscontinuity(), handleBuffer(B)*, final EOS as in (i).
 * MediaCodecRenderer.java:2038-2045 processes the last A buffer, sets the new OutputStreamInfo,
 * then calls onProcessedStreamChange. Its :2534-2537 calls onOutputStreamOffsetUsChanged first;
 * MediaCodecAudioRenderer.java:1017-1019 forwards the offset, and :884-887 forwards discontinuity.
 * Neither callback configures the sink, flushes it, or signals EOS between A and B.
 *
 * Identical decoder PCM output alone does NOT guarantee (ii): MediaCodecRenderer.java:1809
 * marks an input-format notification, :1661-1665 queues that format, and :697-719 calls
 * onOutputFormatChanged for a queued format even without a new codec MediaFormat. The CPH2307
 * device call log on 2026-10-14 confirmed (iii) at the real gapless_mp3_1 -> 2 join, including
 * configure(B, encoderDelay=1105, encoderPadding=815). Path (ii) is retained only as callback
 * documentation, not tested as an MP3 trimming sequence. The zero-trim guard below retains a
 * generic no-configure continuity check.
 *
 * (iii) Output-format notification: handleBuffer(A)*, setOutputStreamOffsetUs(B),
 * handleDiscontinuity(), configure(B), handleBuffer(B)*, final EOS as in (i).
 * MediaCodecRenderer.java:2255-2257 records a codec output-format event; :2299 invokes
 * updateOutputFormatForTime before processing the next buffer, and :711-719 notifies the audio
 * renderer. MediaCodecAudioRenderer.java:643-695,722-727 configures the sink before B is fed.
 * The passing guard changes the PCM Format id (A to B) while keeping 16-bit stereo 48 kHz input;
 * it replays the resulting sink calls without constructing an Android MediaCodec.
 *
 * Seek: MediaCodecAudioRenderer.java:760-762 calls flush() on position reset.
 * TrimmingAudioProcessor.java:115-138 holds P frames; :161-168 drops them on EOS only if
 * reconfiguration is pending, otherwise :143-152 releases them. Its :172-188 flushes the tail
 * and re-arms start trimming only on reconfiguration. These tests observe delegate PCM rather
 * than reaching into processor state or directly calling its queueEndOfStream().
 */
@OptIn(UnstableApi::class)
class FloatChainAudioSinkGaplessTrimGuardTest {
    @Test
    // Moved from the expected-fail class (orchestrator, T062i review): passes today.
    fun finalEndOfStreamTrimsDelayAndPadding() {
        val replay = GaplessTrimReplay()
        replay.configure("A")
        replay.feed(offset = 100, firstFrame = 0, bufferFrames = intArrayOf(2, 3, 6, 9))
        replay.finish()

        replay.assertLifecycle("configure:A", "eos", "eos")
        replay.assertFrames("single stream must trim D=4 and P=3", frameSamples(100, 4, 17))
    }

    @Test
    fun outputFormatNotificationTrimsBothStreamsAtTheJoin() {
        val replay = GaplessTrimReplay()
        replay.configure("A")
        replay.feed(offset = 100, firstFrame = 0, bufferFrames = intArrayOf(2, 3, 6, 9))
        replay.streamChange()
        replay.configure("B")
        replay.feed(offset = 1000, firstFrame = 0, bufferFrames = intArrayOf(1, 4, 5, 7))
        replay.finish()

        replay.assertLifecycle(
            "configure:A", "offset:1000000", "discontinuity", "configure:B", "eos", "eos",
        )
        replay.assertFrames(
            "configured join must contain only A[D..N_A-P-1] then B[D..N_B-P-1]",
            frameSamples(100, 4, 17) + frameSamples(1000, 4, 14),
        )
    }

    @Test
    fun zeroDelayAndPaddingPreserveEveryFrameWithoutConfigure() {
        val replay = GaplessTrimReplay()
        replay.configure("A", delay = 0, padding = 0)
        replay.feed(offset = 100, firstFrame = 0, bufferFrames = intArrayOf(2, 3, 6, 9))
        replay.streamChange()
        replay.feed(offset = 1000, firstFrame = 0, bufferFrames = intArrayOf(1, 4, 5, 7))
        replay.finish()

        replay.assertLifecycle("configure:A", "offset:1000000", "discontinuity", "eos", "eos")
        replay.assertFrames(
            "FLAC-like join must preserve every frame in order",
            frameSamples(100, 0, 20) + frameSamples(1000, 0, 17),
        )
    }

    @Test
    fun seekFlushDoesNotRetrimDelayAndStillTrimsFinalPadding() {
        val replay = GaplessTrimReplay()
        replay.configure("A")
        replay.feed(offset = 100, firstFrame = 0, bufferFrames = intArrayOf(2, 3, 5))
        replay.assertFrames("before seek, start delay and held tail", frameSamples(100, 4, 7))
        replay.flush()
        replay.feed(offset = 100, firstFrame = 10, bufferFrames = intArrayOf(2, 3, 5))
        // Check the seek behavior independently before checking final EOS padding.
        val expected = frameSamples(100, 4, 7) + frameSamples(100, 10, 17)
        replay.assertFrames("seek must preserve frame 10 without trimming another delay", expected)
        replay.finish()

        replay.assertLifecycle("configure:A", "flush", "eos", "eos")
        replay.assertFrames("EOS after seek must still discard the last P=3 frames", expected)
    }
}

/** Frame index encoded in both channels, with distinct positive/negative stream offsets. */
private fun frameSamples(offset: Int, first: Int, endExclusive: Int): FloatArray =
    FloatArray((endExclusive - first) * 2) { sample ->
        val value = offset + first + sample / 2
        (if (sample % 2 == 0) value else -value) / 32768f
    }

@OptIn(UnstableApi::class)
private class GaplessTrimReplay {
    private val delegate = GaplessTrimRecordingSink()
    private val sink = FloatChainAudioSink(
        delegate,
        listOf<AudioProcessor>(GainProcessor(), StereoWidthProcessor(), EqualizerProcessor(), PreampProcessor()),
    )

    fun configure(id: String, delay: Int = 4, padding: Int = 3) {
        val format = Format.Builder()
            .setId(id)
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setSampleRate(48000)
            .setChannelCount(2)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setEncoderDelay(delay)
            .setEncoderPadding(padding)
            .build()
        sink.configure(AudioSink.AudioSinkConfig.Builder(format).build())
        assertEquals("wrapper owns start trim", 0, delegate.configs.last().format.encoderDelay)
        assertEquals("wrapper owns end trim", 0, delegate.configs.last().format.encoderPadding)
    }

    fun feed(offset: Int, firstFrame: Int, bufferFrames: IntArray) {
        var frame = firstFrame
        for (count in bufferFrames) {
            val buffer = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.LITTLE_ENDIAN)
            repeat(count) { index ->
                val value = (offset + frame + index).toShort()
                buffer.putShort(value)
                buffer.putShort((-value.toInt()).toShort())
            }
            buffer.flip()
            assertTrue("recording delegate accepts the entire buffer", sink.handleBuffer(buffer, frame * 1_000_000L / 48000, 1))
            assertEquals("input must be consumed", 0, buffer.remaining())
            frame += count
        }
    }

    fun streamChange() {
        // Ordering is significant: setOutputStreamInfo precedes onProcessedStreamChange.
        sink.setOutputStreamOffsetUs(1_000_000L)
        sink.handleDiscontinuity()
    }

    fun flush() = sink.flush()

    fun finish() {
        // A synchronous fake needs no retry loop; repeat to check EOS is idempotent.
        sink.playToEndOfStream()
        sink.playToEndOfStream()
        assertTrue("sink must report completion after final EOS", sink.isEnded)
        assertFalse("sink must have no undelivered data after final EOS", sink.hasPendingData())
    }

    fun assertLifecycle(vararg expected: String) {
        assertEquals("delegate lifecycle", expected.toList(), delegate.calls.filter { it != "buffer" })
    }

    fun assertFrames(message: String, expected: FloatArray) {
        val bytes = delegate.received.toByteArray()
        assertEquals("delegate receives complete float stereo frames", 0, bytes.size % 8)
        val actual = FloatArray(bytes.size / 4)
        ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer().get(actual)
        assertEquals("$message: frame count", expected.size / 2, actual.size / 2)
        assertArrayEquals("$message: both channels, including first/last frames and join", expected, actual, 0f)
    }
}

@OptIn(UnstableApi::class)
private class GaplessTrimRecordingSink : AudioSink {
    val configs = mutableListOf<AudioSink.AudioSinkConfig>()
    val calls = mutableListOf<String>()
    val received = ByteArrayOutputStream()
    private var ended = false

    override fun configure(config: AudioSink.AudioSinkConfig) {
        configs += config
        calls += "configure:${config.format.id}"
        ended = false
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        calls += "buffer"
        ended = false
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        received.write(bytes)
        return true
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        calls += "offset:$outputStreamOffsetUs"
    }

    override fun handleDiscontinuity() {
        calls += "discontinuity"
    }

    override fun playToEndOfStream() {
        calls += "eos"
        ended = true
    }

    override fun flush() {
        calls += "flush"
        ended = false
    }

    override fun reset() {
        calls += "reset"
        ended = false
    }

    override fun getFormatSupport(format: Format): Int =
        if (format.pcmEncoding == C.ENCODING_PCM_FLOAT) AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        else AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun supportsFormat(format: Format): Boolean = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED
    override fun hasPendingData(): Boolean = false
    override fun isEnded(): Boolean = ended
    override fun setListener(listener: AudioSink.Listener) = Unit
    override fun getCurrentPositionUs(initialize: Boolean): Long = 0L
    override fun play() = Unit
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
