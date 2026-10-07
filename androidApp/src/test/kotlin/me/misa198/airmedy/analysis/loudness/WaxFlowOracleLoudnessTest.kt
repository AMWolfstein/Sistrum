package me.misa198.airmedy.analysis.loudness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

/**
 * The WaxFlow differential oracle for the loudness meter (see docs/waxflow/ORACLE.md).
 *
 * Reads the committed fixtures file from the test classpath and the corpus from `SISTRUM_CORPUS`. If
 * either is absent the test is skipped with a message. For every `ok` row that names a present RIFF
 * WAV (PCM 16/24/32 or IEEE float 32) it decodes the file with the minimal reader below, measures it
 * with [LoudnessMeter], and compares integrated loudness to 0.01 LU and true peak to 0.05 dB.
 * `group` rows are checked by merging the members' [LoudnessHistogram]s against the group value.
 *
 * Every row is visited and mismatches are collected, so the summary counts print even when the test
 * fails (against the T050a stubs it must fail on an assertion, not skip and not crash).
 */
class WaxFlowOracleLoudnessTest {
    @Test
    fun matchesWaxFlowOracleFixtures() {
        val resource = javaClass.classLoader.getResource("waxflow/oracle-fixtures.tsv")
        Assume.assumeTrue(
            "waxflow/oracle-fixtures.tsv is not on the test classpath; run scripts/waxflow-oracle.sh",
            resource != null,
        )
        val corpusPath = System.getenv("SISTRUM_CORPUS")
        Assume.assumeTrue("SISTRUM_CORPUS is not set; skipping the loudness oracle", !corpusPath.isNullOrBlank())
        val corpus = File(corpusPath!!)
        Assume.assumeTrue("SISTRUM_CORPUS=$corpusPath is not a directory", corpus.isDirectory)

        val rows = parseFixtures(resource!!.readText(Charsets.UTF_8))
        Assume.assumeTrue("oracle fixtures file has no data rows", rows.isNotEmpty())
        val shaByFile = rows.filter { it.status == "ok" && it.fileSha256 != null }
            .associate { it.file to it.fileSha256!! }

        var okRows = 0
        var compared = 0
        var skipped = 0
        var groupsCompared = 0
        var groupsSkipped = 0
        val mismatches = mutableListOf<String>()

        for (row in rows) {
            when (row.status) {
                "ok" -> {
                    okRows++
                    val file = File(corpus, row.file)
                    if (!file.isFile) {
                        skipped++
                        continue
                    }
                    if (!isRiffWave(file)) {
                        skipped++
                        continue
                    }
                    if (row.fileSha256 != null && sha256(file) != row.fileSha256) {
                        println("oracle: ${row.file}: file_sha256 differs from the fixtures, skipping")
                        skipped++
                        continue
                    }
                    val wav = readWav(file)
                    if (wav == null) {
                        println("oracle: ${row.file}: not a supported RIFF WAV, skipping")
                        skipped++
                        continue
                    }
                    val meter = measureWav(wav)
                    mismatchIntegrated(row.file, row.integratedLufs, meter.integratedLufs())?.let { mismatches += it }
                    mismatchTruePeak(row.file, row.truePeakDbtp, meter.truePeakDbtp())?.let { mismatches += it }
                    compared++
                }

                "group" -> {
                    val members = row.members?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
                    if (members.isEmpty()) continue
                    val memberFiles = members.map { File(corpus, it) }
                    if (memberFiles.any { !it.isFile }) {
                        println("oracle group ${row.file}: a member is missing, skipping")
                        groupsSkipped++
                        continue
                    }
                    val memberShaMismatch = members.any { member ->
                        val expectedSha = shaByFile[member] ?: return@any false
                        sha256(File(corpus, member)) != expectedSha
                    }
                    if (memberShaMismatch) {
                        println("oracle group ${row.file}: a member's file_sha256 differs, skipping")
                        groupsSkipped++
                        continue
                    }
                    val wavs = memberFiles.map { readWav(it) }
                    if (wavs.any { it == null }) {
                        println("oracle group ${row.file}: members are not all supported WAVs, skipping")
                        groupsSkipped++
                        continue
                    }
                    val album = LoudnessHistogram()
                    for (wav in wavs) {
                        album.merge(measureWav(wav!!).histogram())
                    }
                    val expected = row.integratedLufs
                    if (expected == null) continue
                    val got = album.integratedLufs()
                    if (expected.isFinite()) {
                        if (got == null) {
                            mismatches += "group ${row.file}: integrated is null, want $expected"
                        } else if (abs(expected - got) > 0.05) {
                            mismatches += "group ${row.file}: integrated $got, want $expected"
                        }
                    } else if (got != null && got != Double.NEGATIVE_INFINITY) {
                        mismatches += "group ${row.file}: expected silence but integrated = $got"
                    }
                    groupsCompared++
                }
            }
        }

        println(
            "oracle: $okRows ok rows, $compared compared, $skipped skipped; " +
                "$groupsCompared groups compared, $groupsSkipped skipped; " +
                "${mismatches.size} mismatches",
        )
        Assume.assumeTrue(
            "oracle: nothing comparable (every row skipped; corpus drifted from the fixtures?)",
            compared + groupsCompared > 0,
        )
        assertTrue(
            "oracle mismatches (${mismatches.size}):\n" +
                mismatches.take(8).joinToString("\n", prefix = "  "),
            mismatches.isEmpty(),
        )
    }

    /**
     * The reader must sign-extend sample data: a 16-bit WAV carrying -32768, -1, 0, 1, 32767 decodes
     * to -1.0, -1/32768, 0, 1/32768, 32767/32768, and a 24-bit one likewise. This only exercises the
     * reader in this file, so it passes against the T050a stubs.
     */
    @Test
    fun wavReaderSignExtends16And24BitSamples() {
        val signed16 = intArrayOf(-32768, -1, 0, 1, 32767)
        val decoded16 = readWavBytes(wavBytes(1, 16, 1, 48000, signed16.map { int16Le(it) }.flattenBytes()))
        assertNotNull("16-bit WAV did not decode", decoded16)
        val wav16 = decoded16!!
        assertEquals("16-bit sample count", signed16.size, wav16.samples.size)
        for (i in signed16.indices) {
            assertEquals("16-bit sample $i", signed16[i].toDouble() / 32768.0, wav16.samples[i].toDouble(), 0.0)
        }

        val signed24 = intArrayOf(-8388608, -1, 8388607)
        val decoded24 = readWavBytes(wavBytes(1, 24, 1, 48000, signed24.map { int24Le(it) }.flattenBytes()))
        assertNotNull("24-bit WAV did not decode", decoded24)
        val wav24 = decoded24!!
        assertEquals("24-bit sample count", signed24.size, wav24.samples.size)
        for (i in signed24.indices) {
            assertEquals("24-bit sample $i", signed24[i].toDouble() / 8388608.0, wav24.samples[i].toDouble(), 0.0)
        }
    }

    private fun measureWav(wav: Wav): LoudnessMeter {
        val meter = LoudnessMeter(wav.rate, wav.channels)
        val chunk = 4800
        var frame = 0
        while (frame < wav.frames) {
            val take = minOf(chunk, wav.frames - frame)
            meter.process(wav.samples, frame, take)
            frame += take
        }
        meter.flush()
        return meter
    }

    private fun mismatchIntegrated(name: String, expected: Double?, got: Double?): String? {
        if (expected == null) return null
        if (expected.isFinite()) {
            if (got == null) return "$name: integrated is null, want $expected"
            if (abs(expected - got) > 0.01) return "$name: integrated $got, want $expected"
            return null
        }
        return if (got == null || got == Double.NEGATIVE_INFINITY) null
        else "$name: expected silence but integrated = $got"
    }

    private fun mismatchTruePeak(name: String, expected: Double?, got: Double?): String? {
        if (expected == null) return null
        if (expected.isFinite()) {
            if (got == null) return "$name: true peak is null, want $expected"
            if (abs(expected - got) > 0.05) return "$name: true peak $got, want $expected"
            return null
        }
        return if (got == null || got == Double.NEGATIVE_INFINITY) null
        else "$name: expected silence but true peak = $got"
    }

    // -----------------------------------------------------------------------
    // Fixtures parsing (tsv: a comment header, a column header, then rows).
    // Unknown columns are ignored.
    // -----------------------------------------------------------------------

    private data class OracleRow(
        val file: String,
        val fileSha256: String?,
        val status: String,
        val integratedLufs: Double?,
        val truePeakDbtp: Double?,
        val members: String?,
    )

    private fun parseFixtures(text: String): List<OracleRow> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val headerLine = lines.firstOrNull { !it.startsWith("#") } ?: return emptyList()
        val header = headerLine.split('\t')
        val rows = mutableListOf<OracleRow>()
        for (line in lines) {
            if (line.startsWith("#") || line == headerLine) continue
            val cells = line.split('\t')
            fun cell(name: String): String? {
                val index = header.indexOf(name)
                return if (index in cells.indices) cells[index].ifBlank { null } else null
            }
            val file = cell("file") ?: continue
            rows += OracleRow(
                file = file,
                fileSha256 = cell("file_sha256"),
                status = cell("status") ?: "",
                integratedLufs = parseLoudness(cell("integrated_lufs")),
                truePeakDbtp = parseLoudness(cell("true_peak_dbtp")),
                members = cell("members"),
            )
        }
        return rows
    }

    private fun parseLoudness(value: String?): Double? {
        if (value == null) return null
        return when (value.trim()) {
            "-Inf" -> Double.NEGATIVE_INFINITY
            "+Inf", "Inf" -> Double.POSITIVE_INFINITY
            "NaN" -> Double.NaN
            else -> value.trim().toDoubleOrNull()
        }
    }

    // -----------------------------------------------------------------------
    // Minimal RIFF WAV reader: PCM 16/24/32 and IEEE float 32, interleaved
    // little-endian. Returns null for anything else (including RF64, whose
    // data size 0xFFFFFFFF is not supported here).
    // -----------------------------------------------------------------------

    private data class Wav(val rate: Int, val channels: Int, val frames: Int, val samples: FloatArray)

    private fun readWav(file: File): Wav? = readWavBytes(file.readBytes())

    private fun readWavBytes(bytes: ByteArray): Wav? {
        if (bytes.size < 12) return null
        if (readIntLe(bytes, 0) != 0x46464952) return null // "RIFF"
        if (readIntLe(bytes, 8) != 0x45564157) return null // "WAVE"

        var format = -1
        var channels = 0
        var rate = 0
        var bits = 0
        var dataStart = -1
        var dataSize = 0
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = readIntLe(bytes, pos + 4)
            if (size < 0) return null // 0xFFFFFFFF (RF64) or otherwise malformed
            val body = pos + 8
            if (body.toLong() + size.toLong() > bytes.size.toLong()) return null // data larger than the file
            when (id) {
                "fmt " -> {
                    if (body + 16 > bytes.size) return null
                    format = readShortLe(bytes, body)
                    channels = readShortLe(bytes, body + 2)
                    rate = readIntLe(bytes, body + 4)
                    bits = readShortLe(bytes, body + 14)
                    if (format == 0xFFFE && body + 26 <= bytes.size) {
                        format = readShortLe(bytes, body + 24) // WAVE_FORMAT_EXTENSIBLE sub-format
                    }
                }
                "data" -> {
                    dataStart = body
                    dataSize = size
                }
            }
            pos = body + size + (size and 1)
        }
        if (format < 0 || channels <= 0 || rate <= 0 || dataStart < 0) return null

        val bytesPerSample = bits / 8
        if (bytesPerSample <= 0) return null
        val frameBytes = bytesPerSample * channels
        if (frameBytes <= 0) return null
        val frames = dataSize / frameBytes
        val samples = FloatArray(frames * channels)

        when {
            format == 3 && bits == 32 -> for (i in samples.indices) {
                samples[i] = Float.fromBits(readIntLe(bytes, dataStart + i * 4))
            }

            format == 1 && bits == 16 -> for (i in samples.indices) {
                samples[i] = readShortLe(bytes, dataStart + i * 2).toShort().toFloat() / 32768f
            }

            format == 1 && bits == 24 -> for (i in samples.indices) {
                val o = dataStart + i * 3
                var v = (bytes[o].toInt() and 0xFF) or
                    ((bytes[o + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[o + 2].toInt() and 0xFF) shl 16)
                if ((v and 0x800000) != 0) v = v or 0xFF000000.toInt()
                samples[i] = v.toFloat() / 8388608f
            }

            format == 1 && bits == 32 -> for (i in samples.indices) {
                samples[i] = readIntLe(bytes, dataStart + i * 4).toFloat() / 2147483648f
            }

            else -> return null
        }
        return Wav(rate, channels, frames, samples)
    }

    private fun isRiffWave(file: File): Boolean {
        if (file.length() < 12) return false
        val header = ByteArray(12)
        file.inputStream().use { input ->
            var read = 0
            while (read < header.size) {
                val n = input.read(header, read, header.size - read)
                if (n < 0) return false
                read += n
            }
        }
        return readIntLe(header, 0) == 0x46464952 && readIntLe(header, 8) == 0x45564157
    }

    private fun readIntLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun readShortLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    // -----------------------------------------------------------------------
    // Synthetic WAV construction, for the reader test above.
    // -----------------------------------------------------------------------

    private fun int16Le(value: Int): ByteArray =
        byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

    private fun int24Le(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
        )

    private fun int32Le(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )

    private fun List<ByteArray>.flattenBytes(): ByteArray {
        val out = ByteArray(sumOf { it.size })
        var at = 0
        for (part in this) {
            part.copyInto(out, at)
            at += part.size
        }
        return out
    }

    private fun wavBytes(format: Int, bits: Int, channels: Int, rate: Int, data: ByteArray): ByteArray {
        val blockAlign = channels * (bits / 8)
        val out = java.io.ByteArrayOutputStream()
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(int32Le(36 + data.size))
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        out.write(int32Le(16))
        out.write(int16Le(format))
        out.write(int16Le(channels))
        out.write(int32Le(rate))
        out.write(int32Le(rate * blockAlign))
        out.write(int16Le(blockAlign))
        out.write(int16Le(bits))
        out.write("data".toByteArray(Charsets.US_ASCII))
        out.write(int32Le(data.size))
        out.write(data)
        return out.toByteArray()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
