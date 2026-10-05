package me.misa198.airmedy.spikes

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Spike S1 test code for specs/001-media3-migration (ADR-003), not app code.
 *
 * Instrumented tests for the S1 spike harness: verifies the equal-power per-sample fade curve, the
 * "snap" flush behavior, and provides a listening-run entry point driven by instrumentation args.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class FadeSpikeTest {

    @Test
    fun curveMatchesEqualPower() {
        assertEqualPowerCurve(C.ENCODING_PCM_FLOAT, SpikeFadeProcessor.Role.Outgoing, 1e-6)
        assertEqualPowerCurve(C.ENCODING_PCM_FLOAT, SpikeFadeProcessor.Role.Incoming, 1e-6)
        assertEqualPowerCurve(C.ENCODING_PCM_16BIT, SpikeFadeProcessor.Role.Incoming, 1e-6)
    }

    @Test(timeout = 60_000)
    fun snapDiscardsFadedAudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileA = File(context.cacheDir, "spike_tone_a.wav")
        val fileB = File(context.cacheDir, "spike_tone_b.wav")
        writeSineWav(fileA, 20)
        writeSineWav(fileB, 20)

        val rig = SpikeCrossfadeRig(context)
        try {
            rig.mode = SpikeCrossfadeRig.Mode.Processor
            rig.setMedia(fileA.absolutePath, fileB.absolutePath)
            rig.awaitReady()
            rig.playA(0L)
            SystemClock.sleep(2_000)
            rig.startCrossfade(8_000L)
            SystemClock.sleep(2_400)
            val preSnapGeneration = rig.teeB.flushGeneration.get()
            rig.snap("seek")
            SystemClock.sleep(1_500)

            val reference = rig.teeA.maxPeak()
            assertTrue("outgoing player never produced audio", reference > 0f)
            val postSnap = rig.teeB.records().filter { it.generation > preSnapGeneration }
            assertFalse("incoming player produced no buffers after the snap", postSnap.isEmpty())
            for (record in postSnap) {
                val db = 20.0 * log10(record.peak.toDouble() / reference.toDouble())
                assertTrue(
                    "post-snap buffer peak ${record.peak} is ${db} dB from the unfaded " +
                        "reference $reference (generation ${record.generation})",
                    db >= -0.5
                )
            }
            Log.i(
                "S1",
                "outgoingLeadMs=${rig.outgoingLeadMs()} incomingStartOffsetMs=${rig.incomingStartOffsetMs()}"
            )
        } finally {
            rig.release()
        }
    }

    @Test
    fun listeningRun() {
        val args = InstrumentationRegistry.getArguments()
        val trackA = args.getString("trackA")
        val trackB = args.getString("trackB")
        assumeTrue("listening run requires trackA and trackB instrumentation args", trackA != null && trackB != null)
        val fadeMs = args.getString("fadeMs")?.toLongOrNull() ?: 12_000L
        val modeArg = args.getString("mode") ?: "processor"
        val snapAt = args.getString("snapAt")?.toFloatOrNull()
        val snapKind = args.getString("snapKind") ?: "pause"

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rig = SpikeCrossfadeRig(context)
        rig.mode = if (modeArg == "volume") {
            SpikeCrossfadeRig.Mode.Volume
        } else {
            SpikeCrossfadeRig.Mode.Processor
        }
        var snapped = false
        try {
            rig.setMedia(trackA!!, trackB!!)
            rig.awaitReady(30_000L)
            val startPosition = (rig.durationA() - fadeMs - 5_000L).coerceAtLeast(0L)
            rig.playA(startPosition)
            SystemClock.sleep(5_000)
            rig.startCrossfade(fadeMs)
            if (snapAt != null) {
                val snapDelayMs = (fadeMs * snapAt).toLong()
                SystemClock.sleep(snapDelayMs)
                rig.snap(snapKind)
                snapped = true
                SystemClock.sleep(fadeMs - snapDelayMs)
            } else {
                SystemClock.sleep(fadeMs)
            }
            SystemClock.sleep(5_000)

            val offset = rig.incomingStartOffsetMs()
            val leadMs = rig.outgoingLeadMs()
            val snapField = if (snapped) snapKind else "none"
            val line = "S1RESULT mode=$modeArg fadeMs=$fadeMs snap=$snapField " +
                "incomingStartOffsetMs=$offset outgoingLeadMs=$leadMs"
            Log.i("S1", line)
            val bundle = Bundle().apply {
                putString("mode", modeArg)
                putLong("fadeMs", fadeMs)
                putString("snap", snapField)
                putLong("incomingStartOffsetMs", offset)
                putLong("outgoingLeadMs", leadMs)
            }
            InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle)
        } finally {
            rig.release()
        }
    }

    private fun assertEqualPowerCurve(
        encoding: Int,
        role: SpikeFadeProcessor.Role,
        floatTolerance: Double,
    ) {
        val processor = SpikeFadeProcessor()
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, encoding))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        processor.startFade(role, 1_000L)

        val total = 48_000
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val bytesPerFrame = bytesPerSample * 2
        val sizes = intArrayOf(333, 4096, 17)
        val feedFrames = total + 100
        var at = 0L
        var sizeIndex = 0
        while (at < feedFrames) {
            val frames = minOf(sizes[sizeIndex % sizes.size].toLong(), feedFrames - at).toInt()
            val buffer = ByteBuffer.allocateDirect(frames * bytesPerFrame).order(ByteOrder.LITTLE_ENDIAN)
            if (encoding == C.ENCODING_PCM_FLOAT) {
                repeat(frames * 2) { buffer.putFloat(1.0f) }
            } else {
                repeat(frames * 2) { buffer.putShort(Short.MAX_VALUE) }
            }
            buffer.flip()
            processor.queueInput(buffer)
            val out = processor.getOutput()
            for (f in 0 until frames) {
                val expected = equalPowerGain(role, at + f, total)
                for (c in 0 until 2) {
                    if (encoding == C.ENCODING_PCM_FLOAT) {
                        val sample = out.getFloat()
                        assertEquals(expected.toDouble(), sample.toDouble(), floatTolerance)
                    } else {
                        val sample = out.getShort().toInt()
                        val expectedShort = (Short.MAX_VALUE.toInt() * expected).roundToInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        assertEquals(expectedShort.toDouble(), sample.toDouble(), 1.0)
                    }
                }
            }
            at += frames
            sizeIndex++
        }
        processor.reset()
    }

    private fun equalPowerGain(role: SpikeFadeProcessor.Role, at: Long, total: Int): Float {
        if (at >= total) return if (role == SpikeFadeProcessor.Role.Outgoing) 0f else 1f
        val phase = at.toFloat() / total.toFloat() * (PI / 2).toFloat()
        return if (role == SpikeFadeProcessor.Role.Outgoing) cos(phase) else sin(phase)
    }

    private fun writeSineWav(file: File, seconds: Int) {
        val sampleRate = 48_000
        val channels = 2
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8
        val blockAlign = channels * bytesPerSample
        val numFrames = sampleRate * seconds
        val dataSize = numFrames * blockAlign
        val amplitude = 0.5
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * blockAlign)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        for (i in 0 until numFrames) {
            val value = (sin(2.0 * PI * 440.0 * i / sampleRate) * amplitude * 32767.0)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            buffer.putShort(value)
            buffer.putShort(value)
        }
        file.outputStream().use { it.write(buffer.array()) }
    }
}
