@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.analysis

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import com.amwolfstein.sistrum.analysis.loudness.LoudnessMeter
import com.amwolfstein.sistrum.player.NormalizationSettings
import com.amwolfstein.sistrum.player.PlaybackItem
import com.amwolfstein.sistrum.player.TrackAnalysis
import com.amwolfstein.sistrum.player.decoders.ProcessCodecProbe
import com.amwolfstein.sistrum.player.decoders.defaultDecoderProviders
import com.amwolfstein.sistrum.player.dsp.GainProcessor
import com.amwolfstein.sistrum.player.media3.ProviderExtractorsFactory
import com.amwolfstein.sistrum.player.media3.ProviderRecorder
import com.amwolfstein.sistrum.player.normalization.AlbumLoudnessLookup
import com.amwolfstein.sistrum.player.normalization.GainForm
import com.amwolfstein.sistrum.player.normalization.GainResolver
import com.amwolfstein.sistrum.player.normalization.MeasuredGainSource
import com.amwolfstein.sistrum.player.normalization.TagGainSource
import com.amwolfstein.sistrum.player.normalization.itemGainDb
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** SC-004: meter actual GainProcessor output, without an audio output or device volume. */
@RunWith(AndroidJUnit4::class)
class NormalizationLevelDeviceTest {
    @Test(timeout = 600_000)
    fun measuredCorpusPlaysAtTarget() = checkCorpus(measured = true)

    @Test(timeout = 600_000)
    fun taggedCorpusLevelsAgree() = checkCorpus(measured = false)

    private fun checkCorpus(measured: Boolean) {
        val files = File(CORPUS_DIR).listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
        assumeTrue("missing or empty corpus: $CORPUS_DIR", files.isNotEmpty())
        val failures = mutableListOf<String>()
        val taggedLevels = mutableListOf<Pair<String, Double>>()
        val settings = NormalizationSettings(enabled = true, targetLufs = -14f, preventClip = true)
        val resolver = GainResolver(MeasuredGainSource(AlbumLoudnessLookup { null }), TagGainSource())
        val decoder = OfflineDecoder(context())
        val analyzer = TrackAnalyzer(decoder)
        for (file in files) {
            val fields = mutableListOf("file=${file.name}", "case=${if (measured) "measured" else "tags"}")
            val excluded = EXCLUDED_NAMES.any { file.name.contains(it, ignoreCase = true) }
            try {
                // Preparing the source discovers Format.metadata without audio decoding.
                val format = readFormat(file)
                val item = PlaybackItem(file.name, file.name, "", file.absolutePath)
                val tagForm = resolver.resolve(item, format).form
                fields += "tagForm=$tagForm"
                if (excluded) {
                    fields += "form=$tagForm"
                    fields += "status=excluded"
                    continue
                }
                val analysis = if (measured) {
                    val measurement = analyzer.measure(file.absolutePath, null)
                    val lufs = measurement.integratedLufs
                    val peak = measurement.truePeakDbtp
                    if (measurement.outcome !is DecodeOutcome.Completed ||
                        lufs == null || peak == null || !lufs.isFinite() || !peak.isFinite()
                    ) {
                        fields += "form=Measured"
                        fields += "status=silent_or_failed_measurement"
                        fields += "outcome=${measurement.outcome}"
                        fields += "inputLufs=$lufs"
                        fields += "inputTruePeakDbtp=$peak"
                        continue
                    }
                    TrackAnalysis(lufs.toFloat(), peak.toFloat())
                } else null
                val info = resolver.resolve(item.copy(analysis = analysis), format)
                val gain = itemGainDb(info, settings, settings.untaggedPreampDb)
                // Removing only the peak cap distinguishes clipping prevention from gain bounds.
                val uncapped = itemGainDb(info, settings.copy(preventClip = false), settings.untaggedPreampDb)
                val capped = gain < uncapped
                fields += "form=${info.form}"
                fields += "gainDb=$gain"
                fields += "capped=$capped"
                val output = measureOutput(decoder, file, gain)
                fields += "outputLufs=${output.lufs}"
                fields += "deltaToTarget=${output.lufs?.minus(settings.targetLufs)}"
                fields += "outputTruePeakDbtp=${output.peak}"
                fields += "outcome=${output.outcome}"
                if (output.outcome !is DecodeOutcome.Completed) {
                    fields += "status=failed_measurement"
                    continue
                }
                val lufs = output.lufs
                val peak = output.peak
                if (lufs == null || !lufs.isFinite() || peak == null || !peak.isFinite()) {
                    fields += "status=silent_or_failed_measurement"
                    continue
                }
                fields += "status=measured"
                // Without a known peak the gain cannot be capped; FR-044 leaves those to the session limiter (SC-011).
                val peakKnown = listOf(info.trackPeak, info.albumPeak).any { it != null && it.isFinite() && it > 0f }
                fields += "peakKnown=$peakKnown"
                if (peakKnown && peak > 0.1) {
                    failures += "${file.name}: ${info.form} outputTruePeakDbtp=$peak exceeds 0.1 dBTP (capped=$capped)"
                }
                if (measured && !capped && abs(lufs - settings.targetLufs) > 1.0) {
                    failures += "${file.name}: outputLufs=$lufs, deltaToTarget=${lufs - settings.targetLufs} exceeds ±1 dB"
                }
                if (!measured && info.form in TAG_FORMS && !capped) {
                    val offset = EXPECTED_OFFSET_DB[file.name] ?: 0.0
                    fields += "expectedLufs=${settings.targetLufs + offset}"
                    taggedLevels += file.name to (lufs - offset)
                }
            } catch (e: Exception) {
                if (e is InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw e
                }
                fields += "status=${if (excluded) "excluded" else "failed_measurement"}"
                fields += "error=${e.javaClass.simpleName}: ${e.message}"
            } finally {
                report(fields)
            }
        }
        if (!measured && taggedLevels.isNotEmpty()) {
            val spread = taggedLevels.maxOf { it.second } - taggedLevels.minOf { it.second }
            if (spread > 2.0) {
                failures += "tagged uncapped spread=$spread exceeds 2.0 dB (relative to each expected level): " +
                    taggedLevels.joinToString { "${it.first}=${it.second}" }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private data class Output(val outcome: DecodeOutcome, val lufs: Double?, val peak: Double?)

    /** Two decodes in the measured case, one in the tags case; buffers reused across blocks. */
    private fun measureOutput(decoder: OfflineDecoder, file: File, gainDb: Float): Output {
        val processor = GainProcessor()
        var meter: LoudnessMeter? = null
        var input = AudioProcessor.EMPTY_BUFFER
        var samplesOut = FloatArray(0)
        fun drain() {
            val output = processor.output.order(ByteOrder.nativeOrder())
            val count = output.remaining() / Float.SIZE_BYTES
            if (count == 0) return
            if (samplesOut.size < count) samplesOut = FloatArray(count)
            output.asFloatBuffer().get(samplesOut, 0, count)
            val activeMeter = checkNotNull(meter)
            check(count % activeMeter.channels == 0) { "partial output frame" }
            activeMeter.process(samplesOut, 0, count / activeMeter.channels)
        }
        try {
            val outcome = decoder.decode(file.absolutePath, null, { samples, frames, rate, channels ->
                if (meter == null) {
                    processor.configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_FLOAT))
                    processor.setTargetGainDb(gainDb)
                    processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
                    meter = LoudnessMeter(rate, channels)
                }
                check(meter.sampleRate == rate && meter.channels == channels) { "PCM format changed" }
                val count = frames * channels
                val bytes = count * Float.SIZE_BYTES
                if (input.capacity() < bytes) input = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
                input.clear()
                input.asFloatBuffer().put(samples, 0, count)
                input.limit(bytes)
                processor.queueInput(input)
                check(!input.hasRemaining()) { "unconsumed processor input" }
                drain()
            })
            if (outcome !is DecodeOutcome.Completed) return Output(outcome, null, null)
            if (meter != null) {
                processor.queueEndOfStream()
                drain()
                check(processor.isEnded) { "undrained processor output" }
                meter.flush()
            }
            return Output(outcome, meter?.integratedLufs(), meter?.truePeakDbtp())
        } finally {
            processor.reset()
        }
    }

    /** Source preparation only: extract tags without creating renderers or decoding PCM. */
    private fun readFormat(file: File): Format {
        val thread = HandlerThread("normalization-metadata").apply { start() }
        val handler = Handler(thread.looper)
        val ready = CountDownLatch(1)
        val format = AtomicReference<Format?>()
        val error = AtomicReference<Exception?>()
        var source: MediaSource? = null
        var period: MediaPeriod? = null
        val loadingInfo = LoadingInfo.Builder().setPlaybackPositionUs(0).build()
        fun failed(e: Exception) {
            error.set(e)
            ready.countDown()
        }
        val caller = MediaSource.MediaSourceCaller { mediaSource, timeline ->
            if (period == null && !timeline.isEmpty) {
                try {
                    val created = mediaSource.createPeriod(
                        MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0)),
                        DefaultAllocator(true, 64 * 1024), 0,
                    )
                    period = created
                    created.prepare(object : MediaPeriod.Callback {
                        override fun onPrepared(mediaPeriod: MediaPeriod) {
                            val groups = mediaPeriod.trackGroups
                            for (i in 0 until groups.length) {
                                val group = groups[i]
                                if (group.type == C.TRACK_TYPE_AUDIO) {
                                    format.set(group.getFormat(0))
                                    break
                                }
                            }
                            ready.countDown()
                        }
                        override fun onContinueLoadingRequested(mediaPeriod: MediaPeriod) {
                            if (ready.count > 0) {
                                try { mediaPeriod.continueLoading(loadingInfo) } catch (e: Exception) { failed(e) }
                            }
                        }
                    }, 0)
                } catch (e: Exception) {
                    failed(e)
                }
            }
        }
        val poll = object : Runnable {
            override fun run() {
                if (ready.count == 0L) return
                try {
                    source?.maybeThrowSourceInfoRefreshError()
                    period?.maybeThrowPrepareError()
                    handler.postDelayed(this, 100)
                } catch (e: Exception) {
                    failed(e)
                }
            }
        }
        handler.post {
            try {
                val extractors = ProviderExtractorsFactory(
                    defaultDecoderProviders(ProcessCodecProbe), recorder = ProviderRecorder(),
                )
                source = DefaultMediaSourceFactory(context(), extractors)
                    .createMediaSource(MediaItem.fromUri(Uri.fromFile(file)))
                source.prepareSource(caller, PlayerId.UNSET, DefaultBandwidthMeter.Builder(context()).build())
                handler.post(poll)
            } catch (e: Exception) {
                failed(e)
            }
        }
        try {
            check(ready.await(10, TimeUnit.SECONDS)) { "audio Format retrieval timed out" }
            error.get()?.let { throw it }
            return checkNotNull(format.get()) { "no audio Format" }
        } finally {
            val released = CountDownLatch(1)
            handler.post {
                handler.removeCallbacks(poll)
                try {
                    try { period?.let { source?.releasePeriod(it) } } finally { source?.releaseSource(caller) }
                } finally {
                    released.countDown()
                }
            }
            try {
                check(released.await(10, TimeUnit.SECONDS)) { "metadata source release timed out" }
            } finally {
                thread.quitSafely()
                thread.join(10_000)
            }
        }
    }

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun report(fields: List<String>) {
        val line = fields.joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ') }
        Log.i("NormalizationLevel", line)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("bench", line) })
    }

    private companion object {
        const val CORPUS_DIR = "/sdcard/Music/SistrumTestCorpus"
        val EXCLUDED_NAMES = listOf("malformed", "non_finite", "out_of_range", "missing_unit")
        val TAG_FORMS = setOf(GainForm.ReplayGain, GainForm.R128, GainForm.SoundCheck)
        /** R128 tags written before the OpusHead +4 dB patch; the decoder applies the header gain once (ADR-005). */
        val EXPECTED_OFFSET_DB = mapOf("opus_r128_header_plus4.opus" to 4.0)
    }
}
