package me.misa198.airmedy.analysis

import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import me.misa198.airmedy.player.decoders.FormatKey
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented resume test: an uninterrupted [TrackAnalyzer] measurement vs one cancelled at ~40% of
 * the file's frames then resumed from its final checkpoint, over an [OfflineDecoder]. Reports one
 * tab-separated line per file with `sendStatus` and `Log.i`. Run by the orchestrator on a phone.
 */
@RunWith(AndroidJUnit4::class)
class AnalysisResumeDeviceTest {

    @Test(timeout = 120_000)
    fun resumeOpus() = resumeForFormat("opus_untagged.opus")

    @Test(timeout = 120_000)
    fun resumeMp3() = resumeForFormat("mp3_untagged.mp3")

    @Test(timeout = 120_000)
    fun resumeAac() = resumeForFormat("m4a_aac_untagged.m4a")

    @Test(timeout = 120_000)
    fun resumeFlac() = resumeForFormat("flac_untagged.flac")

    private fun resumeForFormat(name: String) {
        val file = File(CORPUS_DIR, name)
        assumeTrue("missing corpus file: $name", file.isFile)

        // 1. Uninterrupted measurement gives the reference values and the total frame count.
        val uninterrupted = TrackAnalyzer(OfflineDecoder(context())).measure(file.absolutePath, null)
        val outcome = uninterrupted.outcome
        assumeTrue("uninterrupted decode failed: $outcome", outcome is DecodeOutcome.Completed)
        val totalFrames = (outcome as DecodeOutcome.Completed).frames
        val fullLufs = uninterrupted.integratedLufs
        val fullTp = uninterrupted.truePeakDbtp
        assertNotNull("uninterrupted loudness should be non-null", fullLufs)
        assertNotNull("uninterrupted true peak should be non-null", fullTp)

        // 2. Cancel at ~40% of the frames and collect the final checkpoint.
        val cutAt = totalFrames * 2 / 5
        val cutCounter = AtomicLong(0)
        val checkpoints = mutableListOf<MeterResume>()
        val cancelled = TrackAnalyzer(countingDecoder(OfflineDecoder(context()), cutCounter)).measure(
            file.absolutePath,
            null,
            isCancelled = { cutCounter.get() >= cutAt },
            onCheckpoint = { checkpoints.add(it) },
        )
        assumeTrue("cancelled run should be Cancelled, got ${cancelled.outcome}", cancelled.outcome is DecodeOutcome.Cancelled)
        val resume = checkpoints.lastOrNull()
        assumeTrue("cancelled run should leave a checkpoint", resume != null)

        // 3. Resume from the checkpoint; count the frames decoded after the resume.
        val resumedCounter = AtomicLong(0)
        val resumed = TrackAnalyzer(countingDecoder(OfflineDecoder(context()), resumedCounter)).measure(
            file.absolutePath,
            null,
            resume = resume,
        )
        assumeTrue("resumed run should complete, got ${resumed.outcome}", resumed.outcome is DecodeOutcome.Completed)
        val resumedLufs = resumed.integratedLufs
        val resumedTp = resumed.truePeakDbtp
        assertNotNull("resumed loudness should be non-null", resumedLufs)
        assertNotNull("resumed true peak should be non-null", resumedTp)

        val dLu = abs(fullLufs!! - resumedLufs!!)
        val dTp = abs(fullTp!! - resumedTp!!)
        val frameDelta = abs(resume!!.framesMetered + resumedCounter.get() - totalFrames)

        report(
            name,
            "fullLufs=$fullLufs",
            "resumedLufs=$resumedLufs",
            "dLu=$dLu",
            "fullTp=$fullTp",
            "resumedTp=$resumedTp",
            "dTp=$dTp",
            "framesBeforeCut=${resume.framesMetered}",
            "framesDecodedAfterResume=${resumedCounter.get()}",
            "totalFrames=$totalFrames",
            "frameDelta=$frameDelta",
        )

        assertTrue("|Δintegrated| ≤ 0.01 LU (got $dLu)", dLu <= 0.01)
        assertTrue("|Δtrue peak| ≤ 0.05 dB (got $dTp)", dTp <= 0.05)
        assertTrue("resumed run decoded fewer frames than the whole file", resumedCounter.get() < totalFrames)
        // MP3 has no exact sample index: Media3 seeks by an estimated byte position (CBR / Xing TOC), so the resumed
        // timestamps are approximate. Allow two MP3 frames there; the loudness tolerance above still applies.
        val maxFrameDelta = if (name.endsWith(".mp3")) 2 * 1152 else 64
        assertTrue(
            "|framesBeforeCut + framesDecodedAfterResume - framesUninterrupted| ≤ $maxFrameDelta (got $frameDelta)",
            frameDelta <= maxFrameDelta,
        )
    }

    /** Wraps [delegate] so the number of frames it hands to [PcmConsumer] is counted into [counter]. */
    private fun countingDecoder(delegate: PcmDecoder, counter: AtomicLong): PcmDecoder =
        object : PcmDecoder {
            override fun decode(
                path: String,
                key: FormatKey?,
                consumer: PcmConsumer,
                isCancelled: () -> Boolean,
                startUs: Long,
            ): DecodeOutcome = delegate.decode(
                path,
                key,
                { samples, frames, rate, channels ->
                    counter.addAndGet(frames.toLong())
                    consumer.accept(samples, frames, rate, channels)
                },
                isCancelled,
                startUs,
            )
        }

    private fun report(vararg fields: String) {
        val line = fields.joinToString("\t")
        Log.i(TAG, line)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("bench", line) })
    }

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        const val TAG = "AnalysisResume"
        const val CORPUS_DIR = "/sdcard/Music/SistrumTestCorpus"
    }
}
