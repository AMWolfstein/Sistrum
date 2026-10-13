package com.amwolfstein.sistrum.analysis

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import com.amwolfstein.sistrum.analysis.loudness.LoudnessMeter
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented throughput benchmarks for the pipelined analyzer (T051d performance; the direct MediaCodec
 * decoder was measured and removed: no consistent gain, wrong FLAC output, no gapless trimming). Reads a fixed sample of corpus clips from `/sdcard/Music/SistrumTestCorpus` and reports
 * one tab-separated line per measurement with `sendStatus` and `Log.i`. Run by the orchestrator on a phone.
 */
@RunWith(AndroidJUnit4::class)
class AnalysisThroughputBenchmarkTest {

    @Test(timeout = 540_000)
    fun overheadOpus() = overheadForFormat("opus")

    @Test(timeout = 540_000)
    fun overheadFlac() = overheadForFormat("flac")

    @Test(timeout = 540_000)
    fun overheadMp3() = overheadForFormat("mp3")

    @Test(timeout = 540_000)
    fun overheadAac() = overheadForFormat("aac")

    @Test(timeout = 540_000)
    fun concurrency1() = concurrencyFor(1)

    @Test(timeout = 540_000)
    fun concurrency2() = concurrencyFor(2)

    @Test(timeout = 540_000)
    fun concurrency3() = concurrencyFor(3)

    // ---------------------------------------------------------------------
    // Benchmark implementations
    // ---------------------------------------------------------------------

    private fun overheadForFormat(format: String) {
        val files = presentFilesOfFormat(format)
        val exoDecode = ArrayList<Double>()
        val exoDecodeCpu = ArrayList<Double>()
        val exoInline = ArrayList<Double>()
        val exoInlineCpu = ArrayList<Double>()
        val analyzerExo = ArrayList<Double>()
        val analyzerExoCpu = ArrayList<Double>()

        repeat(3) {
            val a = aggregate(files) { path -> measureDecodeOnly(OfflineDecoder(context()), path) }
            exoDecode += a.wallMsPerSec
            exoDecodeCpu += a.cpuMsPerSec
            val c = aggregate(files) { path -> measureInlineMeter(OfflineDecoder(context()), path) }
            exoInline += c.wallMsPerSec
            exoInlineCpu += c.cpuMsPerSec
            val d = aggregate(files) { path -> measureTrackAnalyzer(TrackAnalyzer(OfflineDecoder(context())), path) }
            analyzerExo += d.wallMsPerSec
            analyzerExoCpu += d.cpuMsPerSec
        }

        report(
            "overhead",
            format,
            "exoDecodeMsPerSec=${median(exoDecode)}",
            "exoDecodeCpuMsPerSec=${median(exoDecodeCpu)}",
            "exoInlineMsPerSec=${median(exoInline)}",
            "exoInlineCpuMsPerSec=${median(exoInlineCpu)}",
            "analyzerExoMsPerSec=${median(analyzerExo)}",
            "analyzerExoCpuMsPerSec=${median(analyzerExoCpu)}",
        )
    }

    private fun concurrencyFor(n: Int) {
        val files = allPresentSampleFiles()
        val decoderFactory: () -> PcmDecoder = { OfflineDecoder(context()) }

        val firstTemp = batteryTempTenths()
        val cooled = waitForCoolDown(firstTemp)
        report("concurrency", "N=$n", "cooled=$cooled")

        val statuses = mutableListOf<Int>()
        val headrooms = mutableListOf<Float>()
        var minTemp = firstTemp
        var maxTemp = firstTemp
        var lastTemp = firstTemp
        var maxFreq = -1L
        val workerCpus = Array(n) { mutableSetOf<Int>() }

        val thermalStop = AtomicBoolean(false)
        val thermalSampler = Thread({
            while (!thermalStop.get()) {
                SystemClock.sleep(2_000)
                val t = batteryTempTenths()
                if (t != Int.MIN_VALUE) {
                    minTemp = minOf(minTemp, t)
                    maxTemp = maxOf(maxTemp, t)
                    lastTemp = t
                }
                statuses += thermalStatus()
                thermalHeadroom()?.let { headrooms += it }
                maxCpuFreqKhz()?.let { freq -> if (freq > maxFreq) maxFreq = freq }
            }
        }, "sistrum-thermal-sampler").apply { start() }

        val passDurations = ArrayList<Double>()
        val passMsPerAudioSec = ArrayList<Double>()
        val perFormatAgg = SAMPLE_FORMATS.associateWith { ArrayList<Double>() }

        repeat(3) {
            val queue = ConcurrentLinkedQueue<SampleFile>()
            queue.addAll(files)
            val workers = ArrayList<WorkerHandle>()
            for (i in 0 until n) {
                val tidRef = AtomicInteger(-1)
                val resultRef = AtomicReference<WorkerResult?>()
                val thread = Thread({
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                    tidRef.set(Process.myTid())
                    resultRef.set(runWorkerOnce(decoderFactory(), queue))
                }, "sistrum-worker-$i").apply { start() }
                workers += WorkerHandle(tidRef, resultRef, thread)
            }

            val cpuStop = AtomicBoolean(false)
            val cpuSampler = Thread({
                while (!cpuStop.get()) {
                    for (i in 0 until n) {
                        readCpuNumber(workers[i].tidRef.get())?.let { workerCpus[i].add(it) }
                    }
                    SystemClock.sleep(50)
                }
            }, "sistrum-worker-sampler").apply { start() }

            val passStart = SystemClock.elapsedRealtime()
            for (w in workers) w.thread.join()
            val passWallMs = SystemClock.elapsedRealtime() - passStart
            cpuStop.set(true)
            cpuSampler.join(2_000)

            val results = workers.map { it.resultRef.get() }
            val totalWall = results.sumOf { it?.wallMs ?: 0L }
            val totalAudio = results.sumOf { it?.audioSeconds ?: 0.0 }
            passDurations += passWallMs.toDouble()
            passMsPerAudioSec += if (totalAudio > 0) totalWall / totalAudio else 0.0

            val fmtWall = mutableMapOf<String, Long>()
            val fmtAudio = mutableMapOf<String, Double>()
            for (r in results) {
                for ((fmt, w) in r?.perFormatWall ?: emptyMap()) fmtWall[fmt] = (fmtWall[fmt] ?: 0L) + w
                for ((fmt, a) in r?.perFormatAudio ?: emptyMap()) fmtAudio[fmt] = (fmtAudio[fmt] ?: 0.0) + a
            }
            for (fmt in SAMPLE_FORMATS) {
                val w = fmtWall[fmt] ?: 0L
                val a = fmtAudio[fmt] ?: 0.0
                perFormatAgg[fmt]!!.add(if (a > 0) w / a else 0.0)
            }

            val totalCompleted = results.sumOf { it?.completed ?: 0 }
            assertTrue("pass analysed $totalCompleted of ${files.size} files", totalCompleted == files.size)
        }

        thermalStop.set(true)
        thermalSampler.join(2_000)

        report(
            "concurrency",
            "N=$n",
            "wallMs=${median(passDurations)}",
            "msPerAudioSec=${median(passMsPerAudioSec)}",
            "opusMsPerAudioSec=${median(perFormatAgg["opus"]!!)}",
            "flacMsPerAudioSec=${median(perFormatAgg["flac"]!!)}",
            "mp3MsPerAudioSec=${median(perFormatAgg["mp3"]!!)}",
            "aacMsPerAudioSec=${median(perFormatAgg["aac"]!!)}",
            "minTemp=$minTemp",
            "maxTemp=$maxTemp",
            "lastTemp=$lastTemp",
            "statusMin=${statuses.minOrNull() ?: -1}",
            "statusMax=${statuses.maxOrNull() ?: -1}",
            "headroomMin=${headrooms.minOrNull()?.toString() ?: "?"}",
            "headroomLast=${headrooms.lastOrNull()?.toString() ?: "?"}",
            "maxFreqKhz=${if (maxFreq < 0) "?" else maxFreq.toString()}",
            "workerCpus=${workerCpus.map { it.toSortedSet().joinToString(",") }.joinToString("|")}",
        )
    }

    // ---------------------------------------------------------------------
    // Measurement helpers
    // ---------------------------------------------------------------------

    private data class Timing(val wallMs: Long, val cpuMs: Long, val audioSeconds: Double)

    private data class Aggregate(val wallMsPerSec: Double, val cpuMsPerSec: Double)

    private fun aggregate(files: List<SampleFile>, measure: (String) -> Timing): Aggregate {
        var wallMs = 0L
        var cpuMs = 0L
        var audioSec = 0.0
        for (file in files) {
            val t = measure(file.path)
            wallMs += t.wallMs
            cpuMs += t.cpuMs
            audioSec += t.audioSeconds
        }
        return Aggregate(wallMs / audioSec, cpuMs / audioSec)
    }

    private fun measureDecodeOnly(decoder: PcmDecoder, path: String): Timing {
        var frames = 0L
        var rate = 0
        val cpuStart = Process.getElapsedCpuTime()
        val wallStart = SystemClock.elapsedRealtime()
        val outcome = decoder.decode(path, null, consumer = { _, f, r, _ ->
            frames += f
            rate = r
        })
        val wallMs = SystemClock.elapsedRealtime() - wallStart
        val cpuMs = Process.getElapsedCpuTime() - cpuStart
        assertTrue("decode failed: $outcome", outcome is DecodeOutcome.Completed)
        return Timing(wallMs, cpuMs, frames.toDouble() / rate.coerceAtLeast(1))
    }

    private fun measureInlineMeter(decoder: PcmDecoder, path: String): Timing {
        var frames = 0L
        var rate = 0
        val meterRef = AtomicReference<LoudnessMeter?>()
        val cpuStart = Process.getElapsedCpuTime()
        val wallStart = SystemClock.elapsedRealtime()
        val outcome = decoder.decode(path, null, consumer = { samples, f, r, c ->
            frames += f
            rate = r
            val meter = meterRef.get() ?: LoudnessMeter(r, c).also { meterRef.set(it) }
            meter.process(samples, 0, f)
        })
        meterRef.get()?.flush()
        val wallMs = SystemClock.elapsedRealtime() - wallStart
        val cpuMs = Process.getElapsedCpuTime() - cpuStart
        assertTrue("decode failed: $outcome", outcome is DecodeOutcome.Completed)
        return Timing(wallMs, cpuMs, frames.toDouble() / rate.coerceAtLeast(1))
    }

    private fun measureTrackAnalyzer(analyzer: TrackAnalyzer, path: String): Timing {
        val cpuStart = Process.getElapsedCpuTime()
        val wallStart = SystemClock.elapsedRealtime()
        val result = analyzer.measure(path, null)
        val wallMs = SystemClock.elapsedRealtime() - wallStart
        val cpuMs = Process.getElapsedCpuTime() - cpuStart
        val outcome = result.outcome
        assertTrue("measure failed: $outcome", outcome is DecodeOutcome.Completed)
        val completed = outcome as DecodeOutcome.Completed
        return Timing(wallMs, cpuMs, completed.frames.toDouble() / completed.sampleRate.coerceAtLeast(1))
    }

    private data class WorkerResult(
        val wallMs: Long,
        val audioSeconds: Double,
        val completed: Int,
        val perFormatWall: Map<String, Long>,
        val perFormatAudio: Map<String, Double>,
    )

    private data class WorkerHandle(val tidRef: AtomicInteger, val resultRef: AtomicReference<WorkerResult?>, val thread: Thread)

    private fun runWorkerOnce(decoder: PcmDecoder, queue: ConcurrentLinkedQueue<SampleFile>): WorkerResult {
        val analyzer = TrackAnalyzer(decoder)
        var wallMs = 0L
        var audioSeconds = 0.0
        var completed = 0
        val perFormatWall = mutableMapOf<String, Long>()
        val perFormatAudio = mutableMapOf<String, Double>()
        while (true) {
            val file = queue.poll() ?: break
            val start = SystemClock.elapsedRealtime()
            val result = analyzer.measure(file.path, null)
            val elapsed = SystemClock.elapsedRealtime() - start
            val outcome = result.outcome
            if (outcome is DecodeOutcome.Completed) {
                val secs = outcome.frames.toDouble() / outcome.sampleRate.coerceAtLeast(1)
                wallMs += elapsed
                audioSeconds += secs
                completed++
                perFormatWall[file.format] = (perFormatWall[file.format] ?: 0L) + elapsed
                perFormatAudio[file.format] = (perFormatAudio[file.format] ?: 0.0) + secs
            }
        }
        return WorkerResult(wallMs, audioSeconds, completed, perFormatWall, perFormatAudio)
    }

    // ---------------------------------------------------------------------
    // Fixed sample
    // ---------------------------------------------------------------------

    private data class SampleFile(val name: String, val format: String, val path: String)

    private fun presentFilesOfFormat(format: String): List<SampleFile> {
        val names = SAMPLE[format] ?: return emptyList()
        val present = names.filter { File(CORPUS_DIR, it).isFile }
        for (name in names) {
            if (!present.contains(name)) report("sample", name, "SKIP", "reason=missing")
        }
        assertTrue("format $format has no present files", present.isNotEmpty())
        return present.map { SampleFile(it, format, "$CORPUS_DIR/$it") }
    }

    private fun allPresentSampleFiles(): List<SampleFile> =
        SAMPLE_FORMATS.flatMap { presentFilesOfFormat(it) }

    // ---------------------------------------------------------------------
    // Device / thermal / affinity helpers
    // ---------------------------------------------------------------------

    private fun waitForCoolDown(firstTempTenths: Int): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 120_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (thermalStatus() == 0 && batteryTempTenths() <= firstTempTenths + 10) return true
            SystemClock.sleep(5_000)
        }
        return false
    }

    private fun thermalStatus(): Int {
        if (Build.VERSION.SDK_INT < 29) return 0
        val pm = context().getSystemService(Context.POWER_SERVICE) as PowerManager
        return try {
            pm.currentThermalStatus
        } catch (e: Exception) {
            0
        }
    }

    private fun thermalHeadroom(): Float? {
        if (Build.VERSION.SDK_INT < 30) return null
        val pm = context().getSystemService(Context.POWER_SERVICE) as PowerManager
        return try {
            pm.getThermalHeadroom(10)
        } catch (e: Exception) {
            null
        }
    }

    private fun batteryTempTenths(): Int {
        val battery = try {
            context().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
            null
        }
        return battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
    }

    private fun maxCpuFreqKhz(): Long? {
        val dir = File("/sys/devices/system/cpu")
        val freqs = mutableListOf<Long>()
        for (cpu in dir.listFiles() ?: emptyArray()) {
            val name = cpu.name
            if (!name.startsWith("cpu") || name.length <= 3) continue
            if (!name.substring(3).all { it.isDigit() }) continue
            try {
                freqs += File(cpu, "cpufreq/scaling_cur_freq").readText().trim().toLong()
            } catch (e: Exception) {
                // unreadable core, skip
            }
        }
        return if (freqs.isEmpty()) null else freqs.max()
    }

    private fun readCpuNumber(tid: Int): Int? {
        if (tid <= 0) return null
        return try {
            val stat = File("/proc/self/task/$tid/stat").readText()
            val closeParen = stat.lastIndexOf(')')
            val fields = stat.substring(closeParen + 2).split(' ').filter { it.isNotBlank() }
            if (fields.size <= 36) null else fields[36].toInt()
        } catch (e: Exception) {
            null
        }
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    private fun report(vararg fields: String) {
        val line = fields.joinToString("\t")
        Log.i(TAG, line)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("bench", line) })
    }

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        const val TAG = "AnalysisBench"
        const val CORPUS_DIR = "/sdcard/Music/SistrumTestCorpus"

        val SAMPLE_FORMATS = listOf("opus", "flac", "mp3", "aac")

        val SAMPLE = mapOf(
            "opus" to listOf(
                "opus_untagged.opus",
                "opus_r128.opus",
                "opus_header_plus4.opus",
                "opus_r128_header_plus4.opus",
            ),
            "flac" to listOf(
                "flac_untagged.flac",
                "gapless_flac_1.flac",
                "flac_24_96.flac",
            ),
            "mp3" to listOf(
                "mp3_untagged.mp3",
                "gapless_mp3_1.mp3",
                "mp3_rg_txxx.mp3",
            ),
            "aac" to listOf(
                "m4a_aac_untagged.m4a",
                "m4a_aac_both.m4a",
                "m4a_aac_itunnorm.m4a",
            ),
        )
    }
}
