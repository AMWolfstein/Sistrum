package me.misa198.airmedy.analysis.loudness

import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

// ---------------------------------------------------------------------------
// Bin layout: 0.1 LU bins with centres at multiples of 0.1 LU from -70.0 to
// +5.0 (751 bins). Bin k covers [c - 0.05, c + 0.05) with centre
// c = -70.0 + 0.1 k; values above +5.05 are folded into the top bin.
// ---------------------------------------------------------------------------

private const val HIST_VERSION = 1
private const val BIN_BOTTOM = -70.0
private const val BIN_TOP = 5.0
private const val BIN_STEP = 0.1
private const val BIN_COUNT = 751
private const val TOP_BIN = BIN_COUNT - 1

private fun binCentreLoudness(k: Int): Double = BIN_BOTTOM + BIN_STEP * k

private fun binCentrePower(k: Int): Double =
    10.0.pow((binCentreLoudness(k) - LOUDNESS_OFFSET) / 10.0)

private fun binIndex(loudness: Double): Int {
    val scaled = (loudness - BIN_BOTTOM) / BIN_STEP
    var k = floor(scaled + 0.5).toInt()
    if (k < 0) k = 0
    if (k > TOP_BIN) k = TOP_BIN
    return k
}

private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
    var v = value
    while (true) {
        val b = (v and 0x7F).toInt()
        v = v ushr 7
        if (v != 0L) {
            out.write(b or 0x80)
        } else {
            out.write(b)
            return
        }
    }
}

private fun readVarint(bytes: ByteArray, pos: IntArray): Long {
    var result = 0L
    var shift = 0
    while (true) {
        if (pos[0] >= bytes.size) throw IllegalArgumentException("truncated varint")
        val b = bytes[pos[0]++].toInt() and 0xFF
        result = result or ((b and 0x7F).toLong() shl shift)
        if ((b and 0x80) == 0) return result
        shift += 7
        if (shift > 63) throw IllegalArgumentException("varint too long")
    }
}

/**
 * Counts of gated 400 ms blocks in 0.1 LU bins from -70 to +5 LUFS (751 bins).
 * Ours, not ported: the gating follows libebur128's histogram method, applied
 * to bin-centre powers (see https://github.com/jiixyj/libebur128).
 */
class LoudnessHistogram {
    private val bins = LongArray(BIN_COUNT)
    private var total = 0L

    val blockCount: Long get() = total

    /** Ignores non-finite powers and powers at or below the absolute gate. */
    fun addBlockPower(power: Double) {
        if (!power.isFinite() || power <= ABS_GATE_POWER) return
        val loudness = LOUDNESS_OFFSET + 10.0 * log10(power)
        bins[binIndex(loudness)]++
        total++
    }

    fun merge(other: LoudnessHistogram) {
        for (k in 0 until BIN_COUNT) bins[k] += other.bins[k]
        total += other.total
    }

    /**
     * BS.1770 gating on bin-centre powers: the absolute gate is applied on
     * entry; the relative gate sits 10 LU below the count-weighted mean power,
     * and the result is the mean of the bins whose centre loudness is at least
     * that threshold. Null when empty.
     */
    fun integratedLufs(): Double? {
        if (total == 0L) return null
        var meanPower = 0.0
        for (k in 0 until BIN_COUNT) {
            val c = bins[k]
            if (c != 0L) meanPower += c.toDouble() * binCentrePower(k)
        }
        meanPower /= total.toDouble()
        val relThreshold = LOUDNESS_OFFSET + 10.0 * log10(meanPower) - 10.0
        var gatedPower = 0.0
        var gatedCount = 0L
        for (k in 0 until BIN_COUNT) {
            val c = bins[k]
            if (c != 0L && binCentreLoudness(k) >= relThreshold) {
                gatedPower += c.toDouble() * binCentrePower(k)
                gatedCount += c
            }
        }
        if (gatedCount == 0L) return null
        return LOUDNESS_OFFSET + 10.0 * log10(gatedPower / gatedCount.toDouble())
    }

    /**
     * Sparse base64: a version byte, then varint pairs (bin-index delta from
     * the previous occupied bin, count) in ascending bin order.
     */
    fun encode(): String {
        val out = ByteArrayOutputStream()
        out.write(HIST_VERSION)
        var prev = -1
        for (k in 0 until BIN_COUNT) {
            val c = bins[k]
            if (c != 0L) {
                writeVarint(out, (k - prev).toLong())
                writeVarint(out, c)
                prev = k
            }
        }
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }

    companion object {
        /** Rejects malformed input with [IllegalArgumentException]. */
        fun decode(text: String): LoudnessHistogram {
            val bytes = try {
                Base64.getDecoder().decode(text)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("invalid base64 histogram", e)
            }
            if (bytes.isEmpty()) throw IllegalArgumentException("empty histogram")
            if ((bytes[0].toInt() and 0xFF) != HIST_VERSION) {
                throw IllegalArgumentException("histogram version ${bytes[0].toInt() and 0xFF}, want $HIST_VERSION")
            }
            val histogram = LoudnessHistogram()
            val pos = intArrayOf(1)
            var prev = -1
            while (pos[0] < bytes.size) {
                val delta = readVarint(bytes, pos)
                val count = readVarint(bytes, pos)
                if (delta <= 0L || delta > BIN_COUNT.toLong()) throw IllegalArgumentException("bad bin delta")
                if (count <= 0L) throw IllegalArgumentException("bad bin count")
                val k = prev + delta.toInt()
                if (k >= BIN_COUNT) throw IllegalArgumentException("bin out of range")
                histogram.bins[k] = count
                histogram.total += count
                prev = k
            }
            return histogram
        }
    }
}
