package com.amwolfstein.sistrum.analysis

import com.amwolfstein.sistrum.analysis.loudness.LoudnessHistogram
import com.amwolfstein.sistrum.player.decoders.FormatKey
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SilenceProfileTest {
    @Test fun edgesJoinAcrossUnevenChunksAtSeveralRatesAndChannelCounts() {
        for (rate in listOf(8000, 44100, 192000)) for (channels in listOf(1, 2, 3)) {
            val pcm = FloatArray(rate * 5 * channels)
            for (f in rate until rate * 3) pcm[f * channels + channels - 1] = -0.25f
            val decoder = Decoder(pcm, rate, channels)
            val result = TrackAnalyzer(decoder, bufferFrames = 4093).measure("x", null)
            assertEquals(SilenceProfile(1000, 2000), result.silence)
            assertEquals(1, decoder.calls)
        }
    }

    @Test fun strictThresholdAndExactOneSecondMinimum() {
        val rate = 8000
        for ((value, expected) in listOf(0.000999f to 1000L, 0.001f to 0L, -0.001f to 0L)) {
            val result = TrackAnalyzer(Decoder(FloatArray(rate) { value }, rate, 1)).measure("x", null)
            assertEquals(SilenceProfile(expected, expected), result.silence)
        }
        val short = TrackAnalyzer(Decoder(FloatArray(rate - 1), rate, 1)).measure("x", null)
        assertEquals(SilenceProfile(0, 0), short.silence)
    }

    @Test fun interiorSilenceIsNotAnEdgeAndLongStreamsDoNotExhaustSpanStorage() {
        val pcm = FloatArray(48000 * 4) { if (it % 2 == 0) 0f else 0.1f }
        assertEquals(SilenceProfile(0, 0), TrackAnalyzer(Decoder(pcm, 48000, 1), bufferFrames = 31).measure("x", null).silence)
        val interior = FloatArray(48000 * 4)
        interior[0] = 0.2f
        interior[interior.lastIndex] = 0.2f
        assertEquals(SilenceProfile(0, 0), TrackAnalyzer(Decoder(interior, 48000, 1)).measure("x", null).silence)
    }

    @Test fun emptyAndFullySilentStreams() {
        assertEquals(SilenceProfile(0, 0), TrackAnalyzer(Decoder(FloatArray(0), 48000, 2)).measure("x", null).silence)
        assertEquals(SilenceProfile(2500, 2500), TrackAnalyzer(Decoder(FloatArray(120000), 48000, 1)).measure("x", null).silence)
    }

    @Test fun cancellationCheckpointResumesRunsAndLoudnessWithoutSecondFullDecode() {
        val rate = 8000
        val pcm = FloatArray(rate * 5)
        for (f in rate * 2 until rate * 3) pcm[f] = 0.1f
        for (cut in listOf(rate, rate * 3 + rate / 2, pcm.size)) {
            var checkpoint: MeterResume? = null
            val partial = TrackAnalyzer(Decoder(pcm.copyOf(cut), rate, 1, DecodeOutcome.Cancelled), bufferFrames = 997)
                .measure("x", null, onCheckpoint = { checkpoint = it })
            assertNull(partial.silence)
            val restored = MeterResumeCodec.decode(MeterResumeCodec.encode(checkpoint!!))!!
            val decoder = Decoder(pcm, rate, 1)
            val result = TrackAnalyzer(decoder, bufferFrames = 997).measure("x", null, resume = restored)
            assertEquals(cut * 1_000_000L / rate, decoder.start)
            assertEquals(SilenceProfile(2000, 2000), result.silence)
            val full = TrackAnalyzer(Decoder(pcm, rate, 1), bufferFrames = 997).measure("x", null)
            assertEquals(full.integratedLufs, result.integratedLufs)
            assertEquals(full.histogram!!.encode(), result.histogram!!.encode())
        }
    }

    @Test fun resumedEntirelySilentTrackAtEofKeepsBothEdges() {
        var checkpoint: MeterResume? = null
        TrackAnalyzer(Decoder(FloatArray(96000), 48000, 1, DecodeOutcome.Cancelled))
            .measure("x", null, onCheckpoint = { checkpoint = it })
        val result = TrackAnalyzer(Decoder(FloatArray(96000), 48000, 1))
            .measure("x", null, resume = checkpoint)
        assertEquals(SilenceProfile(2000, 2000), result.silence)
    }

    @Test fun failureNeverPublishesPartialProfile() {
        val decoder = Decoder(FloatArray(96000), 48000, 1, DecodeOutcome.Failed("fixture", "corrupt input"))
        assertNull(TrackAnalyzer(decoder).measure("x", null).silence)
    }

    @Test fun completedProfilesFlowThroughMeasurerToBothDocumentStatuses() {
        for (silent in listOf(false, true)) {
            val pcm = FloatArray(48000 * 4)
            if (!silent) for (f in 48000 until 96000) pcm[f] = 0.1f
            val measurer = AnalyzerTrackMeasurer { TrackAnalyzer(Decoder(pcm, 48000, 1)) }
            val result = measurer.measure(PendingTrack("A", "x", "hash", "wav", "pcm", 4000), null, {}, { false })!!
            val json = analysisDocument(result, "now")
            assertEquals(if (silent) "silent" else "ok", json["status"]!!.jsonPrimitive.content)
            assertEquals(if (silent) 4000L else 1000L, json["silenceLeadMs"]!!.jsonPrimitive.long)
            assertEquals(if (silent) 4000L else 2000L, json["silenceTailMs"]!!.jsonPrimitive.long)
            assertEquals("silence-1", json["silenceVersion"]!!.jsonPrimitive.content)
            assertFalse(needsSilenceAnalysis(json, "hash"))
        }
    }

    @Test fun absentAndOldVersionsRefreshOnceAndFailedRefreshKeepsOriginalProfile() {
        val legacy = Json.parseToJsonElement("""{"status":"ok","source_fingerprint":"hash","loudness_lufs":-14.0}""").jsonObject
        for (previous in listOf(legacy, JsonObject(legacy + ("silenceVersion" to JsonPrimitive("silence-0"))))) {
            assertTrue(needsSilenceAnalysis(previous, "hash"))
            val result = TrackLoudnessResult.Measured("A", "hash", -14.0, -1.0, -2.0, LoudnessHistogram(), SilenceProfile(1000, 2000))
            val updated = analysisDocument(result, "now")
            assertFalse(needsSilenceAnalysis(updated, "hash"))
            val failed = analysisDocument(TrackLoudnessResult.Failed("A", "hash", "fixture", "bad"), "now", previous)
            for ((key, value) in previous) assertEquals(value, failed[key])
            assertFalse(needsSilenceAnalysis(failed, "hash"))
            assertFalse("failure cannot claim a new measured version", failed["silenceVersion"]?.jsonPrimitive?.content == "silence-1")
        }
        assertFalse(needsSilenceAnalysis(legacy, "different-source"))
        assertFalse(needsSilenceAnalysis(null, "hash"))
    }

    @Test fun corruptAndOldCheckpointVersionsAreRejected() {
        val bytes = MeterResumeCodec.encode(MeterResume(10, 48000, 1, byteArrayOf(1), SilenceResume(11, 0)))
        assertNull(MeterResumeCodec.decode(bytes))
        bytes[0] = 1
        assertNull(MeterResumeCodec.decode(bytes))
    }

    private class Decoder(
        val pcm: FloatArray, val rate: Int, val channels: Int,
        val outcome: DecodeOutcome? = null,
    ) : PcmDecoder {
        var calls = 0
        var start = -1L
        override fun decode(path: String, key: FormatKey?, consumer: PcmConsumer, isCancelled: () -> Boolean, startUs: Long): DecodeOutcome {
            calls++
            start = startUs
            var offset = (startUs * rate / 1_000_000).toInt()
            while (offset < pcm.size / channels) {
                val count = minOf(1237, pcm.size / channels - offset)
                consumer.accept(pcm.copyOfRange(offset * channels, (offset + count) * channels), count, rate, channels)
                offset += count
            }
            return outcome ?: DecodeOutcome.Completed(rate, channels, (pcm.size / channels).toLong(), "fixture")
        }
    }
}
