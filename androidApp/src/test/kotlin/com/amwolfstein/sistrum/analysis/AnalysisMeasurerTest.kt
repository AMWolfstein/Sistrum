package com.amwolfstein.sistrum.analysis

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin
import com.amwolfstein.sistrum.analysis.loudness.LoudnessMeter
import com.amwolfstein.sistrum.player.decoders.FormatKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for [MeterResumeCodec] and [AnalyzerTrackMeasurer]. */
class AnalysisMeasurerTest {

    // ------------------------------------------------------------------
    // Codec
    // ------------------------------------------------------------------

    @Test
    fun codecRoundTrip() {
        val meter = LoudnessMeter(48_000, 2)
        meter.process(signal(48_000, 2, 48_000), 0, 48_000)
        val resume = MeterResume(48_000, 48_000, 2, meter.checkpoint())

        val decoded = MeterResumeCodec.decode(MeterResumeCodec.encode(resume))
        assertNotNull(decoded)
        assertEquals(48_000L, decoded!!.framesMetered)
        assertEquals(48_000, decoded.sampleRate)
        assertEquals(2, decoded.channels)
        assertArrayEquals(resume.meterState, decoded.meterState)
    }

    @Test
    fun codecRejectsWrongVersion() {
        val bytes = MeterResumeCodec.encode(MeterResume(0, 48_000, 2, ByteArray(0)))
        bytes[0] = 9
        assertNull(MeterResumeCodec.decode(bytes))
    }

    @Test
    fun codecRejectsTruncation() {
        val bytes = MeterResumeCodec.encode(MeterResume(100, 48_000, 2, ByteArray(10)))
        assertNull(MeterResumeCodec.decode(bytes.copyOf(bytes.size - 3)))
    }

    @Test
    fun codecRejectsBadChannels() {
        assertNull(MeterResumeCodec.decode(MeterResumeCodec.encode(MeterResume(0, 48_000, 0, ByteArray(0)))))
        assertNull(MeterResumeCodec.decode(MeterResumeCodec.encode(MeterResume(0, 48_000, 9, ByteArray(0)))))
    }

    @Test
    fun codecRejectsNegativeFrames() {
        assertNull(MeterResumeCodec.decode(MeterResumeCodec.encode(MeterResume(-1, 48_000, 2, ByteArray(0)))))
    }

    // ------------------------------------------------------------------
    // Outcome mapping
    // ------------------------------------------------------------------

    @Test(timeout = 10_000)
    fun completedWithValuesMapsToMeasured() {
        val track = pendingTrack()
        val samples = signal(48_000, 2, 48_000)
        val decoder = FakePcmDecoder(DecodeOutcome.Completed(48_000, 2, 48_000, "fake"), samples, 2, 48_000)
        val measurer = AnalyzerTrackMeasurer { TrackAnalyzer(decoder, poolBuffers = 4, bufferFrames = 4096) }

        val result = measurer.measure(track, null, onCheckpoint = {}, isCancelled = { false })
        assertTrue("expected Measured, got $result", result is TrackLoudnessResult.Measured)
        val measured = result as TrackLoudnessResult.Measured
        assertEquals(track.trackId, measured.trackId)
        assertEquals(track.fingerprint, measured.fingerprint)

        val direct = LoudnessMeter(48_000, 2)
        direct.process(samples, 0, samples.size / 2)
        direct.flush()
        assertEquals(direct.integratedLufs(), measured.integratedLufs)
        assertEquals(direct.truePeakDbtp(), measured.truePeakDbtp)
        assertEquals(direct.samplePeakDbfs(), measured.samplePeakDbfs)
        assertEquals(direct.histogram().encode(), measured.histogram.encode())
    }

    @Test(timeout = 10_000)
    fun completedSilentMapsToSilent() {
        val track = pendingTrack()
        val decoder = FakePcmDecoder(DecodeOutcome.Completed(48_000, 2, 0, "fake"))
        val measurer = AnalyzerTrackMeasurer { TrackAnalyzer(decoder) }

        val result = measurer.measure(track, null, onCheckpoint = {}, isCancelled = { false })
        assertEquals(TrackLoudnessResult.Silent(track.trackId, track.fingerprint), result)
    }

    @Test(timeout = 10_000)
    fun failedOutcomeMapsToFailed() {
        val track = pendingTrack()
        val decoder = FakePcmDecoder(DecodeOutcome.Failed("prov", "boom"))
        val measurer = AnalyzerTrackMeasurer { TrackAnalyzer(decoder) }

        assertEquals(
            TrackLoudnessResult.Failed(track.trackId, track.fingerprint, "prov", "boom"),
            measurer.measure(track, null, onCheckpoint = {}, isCancelled = { false }),
        )

        val nullProvider = FakePcmDecoder(DecodeOutcome.Failed(null, "boom"))
        val measurer2 = AnalyzerTrackMeasurer { TrackAnalyzer(nullProvider) }
        assertEquals(
            TrackLoudnessResult.Failed(track.trackId, track.fingerprint, "unknown", "boom"),
            measurer2.measure(track, null, onCheckpoint = {}, isCancelled = { false }),
        )
    }

    @Test(timeout = 10_000)
    fun unsupportedOutcomeMapsToFailed() {
        val track = pendingTrack()
        val decoder = FakePcmDecoder(DecodeOutcome.Unsupported("no codec"))
        val measurer = AnalyzerTrackMeasurer { TrackAnalyzer(decoder) }

        assertEquals(
            TrackLoudnessResult.Failed(track.trackId, track.fingerprint, "none", "no codec"),
            measurer.measure(track, null, onCheckpoint = {}, isCancelled = { false }),
        )
    }

    @Test(timeout = 10_000)
    fun cancelledOutcomeMapsToNull() {
        val track = pendingTrack()
        val decoder = FakePcmDecoder(DecodeOutcome.Cancelled)
        val measurer = AnalyzerTrackMeasurer { TrackAnalyzer(decoder) }

        assertNull(measurer.measure(track, null, onCheckpoint = {}, isCancelled = { false }))
    }

    // ------------------------------------------------------------------
    // Resume + checkpoint wiring
    // ------------------------------------------------------------------

    @Test(timeout = 10_000)
    fun checkpointsAreEncodedWithTheCodec() {
        val track = pendingTrack()
        val samples = signal(48_000, 2, 48_000)
        val decoder = FakePcmDecoder(DecodeOutcome.Cancelled, samples, 2, 48_000)
        val measurer = AnalyzerTrackMeasurer { TrackAnalyzer(decoder, poolBuffers = 4, bufferFrames = 4096) }

        val checkpoints = mutableListOf<ByteArray>()
        val result = measurer.measure(track, null, onCheckpoint = { checkpoints.add(it) }, isCancelled = { false })
        assertNull("a cancelled measurement returns null", result)
        assertEquals("one final checkpoint", 1, checkpoints.size)

        val decoded = MeterResumeCodec.decode(checkpoints.single())
        assertNotNull(decoded)
        assertEquals(48_000L, decoded!!.framesMetered)
        assertEquals(48_000, decoded.sampleRate)
        assertEquals(2, decoded.channels)
    }

    @Test(timeout = 10_000)
    fun undecodableResumeMeasuresFromZero() {
        val samples = signal(24_000, 2, 48_000)
        val startUsRef = AtomicLong(-1)
        val decoder = FakePcmDecoder(
            DecodeOutcome.Completed(48_000, 2, 24_000, "fake"),
            samples,
            2,
            48_000,
            startUsRef = startUsRef,
        )
        val measurer = AnalyzerTrackMeasurer { TrackAnalyzer(decoder, poolBuffers = 4, bufferFrames = 4096) }

        val result = measurer.measure(pendingTrack(), byteArrayOf(0, 1, 2), onCheckpoint = {}, isCancelled = { false })
        assertTrue("expected Measured, got $result", result is TrackLoudnessResult.Measured)
        assertEquals("undecodable resume must start decoding at 0", 0L, startUsRef.get())
    }

    @Test(timeout = 10_000)
    fun formatKeyNullWhenFormatOrCodecBlank() {
        val keyRef = AtomicReference<FormatKey?>()
        val decoder = FakePcmDecoder(DecodeOutcome.Cancelled, keyRef = keyRef)
        val measurer = AnalyzerTrackMeasurer { TrackAnalyzer(decoder) }

        measurer.measure(pendingTrack().copy(format = ""), null, onCheckpoint = {}, isCancelled = { false })
        assertNull(keyRef.get())

        measurer.measure(pendingTrack().copy(codec = ""), null, onCheckpoint = {}, isCancelled = { false })
        assertNull(keyRef.get())
    }

    @Test(timeout = 10_000)
    fun formatKeyPresentWhenBothSet() {
        val keyRef = AtomicReference<FormatKey?>()
        val decoder = FakePcmDecoder(DecodeOutcome.Cancelled, keyRef = keyRef)
        val measurer = AnalyzerTrackMeasurer { TrackAnalyzer(decoder) }

        measurer.measure(pendingTrack(), null, onCheckpoint = {}, isCancelled = { false })
        assertEquals(FormatKey("flac", "flac"), keyRef.get())
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun pendingTrack() = PendingTrack(
        trackId = "t1",
        audioPath = "/audio/t1.flac",
        fingerprint = "fp-1",
        format = "flac",
        codec = "flac",
        durationMs = 1_000L,
    )

    private fun signal(frames: Int, channels: Int, rate: Int): FloatArray {
        val out = FloatArray(frames * channels)
        for (f in 0 until frames) {
            val v = (0.1 * sin(2.0 * PI * 440.0 * f / rate)).toFloat()
            for (c in 0 until channels) out[f * channels + c] = v
        }
        return out
    }

    private class FakePcmDecoder(
        private val outcome: DecodeOutcome,
        private val samples: FloatArray = FloatArray(0),
        private val channels: Int = 2,
        private val rate: Int = 48_000,
        private val keyRef: AtomicReference<FormatKey?> = AtomicReference(),
        private val startUsRef: AtomicLong = AtomicLong(-1),
    ) : PcmDecoder {
        override fun decode(
            path: String,
            key: FormatKey?,
            consumer: PcmConsumer,
            isCancelled: () -> Boolean,
            startUs: Long,
        ): DecodeOutcome {
            keyRef.set(key)
            startUsRef.set(startUs)
            val totalFrames = samples.size / channels
            var off = 0
            while (off < totalFrames) {
                if (isCancelled()) return DecodeOutcome.Cancelled
                val take = minOf(4096, totalFrames - off)
                val chunk = FloatArray(take * channels)
                samples.copyInto(chunk, 0, off * channels, (off + take) * channels)
                consumer.accept(chunk, take, rate, channels)
                off += take
            }
            return outcome
        }
    }
}
