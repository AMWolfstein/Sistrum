// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow dsp/loudness/loudness.go, fork github.com/AMWolfstein/WaxFlow at 446ca3124d890fd08caecdcbf8931a493d4ebd04,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).

package me.misa198.airmedy.analysis.loudness

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

// The gating windows in 100 ms sub-blocks: the 400 ms momentary window
// advances on the common 100 ms hop. The ring is kept at stSub slots to
// reproduce WaxFlow's window-summing arithmetic exactly, even though the
// 3 s short-term window (loudness range) is out of scope for Sistrum.
internal const val MOM_SUB = 4
internal const val ST_SUB = 30

/** Calibrates block loudness: L = LOUDNESS_OFFSET + 10 log10(power). */
internal const val LOUDNESS_OFFSET = -0.691

/** The absolute gate in LUFS; blocks at or below it never enter any measurement. */
internal const val ABS_GATE = -70.0

/** The absolute gate as a linear channel-sum power. */
internal val ABS_GATE_POWER = 10.0.pow((ABS_GATE - LOUDNESS_OFFSET) / 10.0)

private const val CHECKPOINT_VERSION = 1

// WAVE_FORMAT_EXTENSIBLE channel-mask bits, used only to map the default
// layout onto BS.1770 weights in ascending bit order.
private const val FRONT_LEFT = 1
private const val FRONT_RIGHT = 2
private const val FRONT_CENTER = 4
private const val LOW_FREQUENCY = 8
private const val BACK_LEFT = 16
private const val BACK_RIGHT = 32
private const val BACK_CENTER = 256
private const val SIDE_LEFT = 512
private const val SIDE_RIGHT = 1024

/**
 * BS.1770-4 meter over interleaved float PCM (nominal +-1.0). A faithful
 * Kotlin port of WaxFlow dsp/loudness: integrated loudness plus true peak and
 * sample peak, with no loudness range. Feed interleaved chunks to [process],
 * call [flush] after the last one, then read the results.
 *
 * Unlike WaxFlow's `Meter.Flush`, [flush] is not terminal: processing may
 * continue after it (as WaxFlow's `PeakMeter.Flush` allows), and a second
 * flush drains the new tail.
 */
class LoudnessMeter(val sampleRate: Int, val channels: Int) {
    init {
        require(sampleRate > 0) { "sample rate must be positive, was $sampleRate" }
        require(channels in 1..8) { "channel count must be in 1..8, was $channels" }
    }

    private val rate = sampleRate
    private val weights = channelWeights(channels)

    private val shelf: Biquad
    private val hp: Biquad
    private val state = Array(channels) { KState() }

    private val subLen = max(rate / 10, 1)
    private var subFill = 0
    private val subAcc = DoubleArray(channels)
    private val ring = Array(channels) { DoubleArray(ST_SUB) }
    private var ringPos = 0
    private var ringCnt = 0L

    private var blocks = DoubleArray(16)
    private var blocksSize = 0

    private val tp = TruePeak.newTruePeak(rate, channels)
    private var maxSP = 0.0
    private var maxTP = 0.0

    private var flushed = false

    init {
        val (s, h) = kWeighting(rate)
        shelf = s
        hp = h
    }

    /**
     * Consumes [frames] frames starting at frame [offset] of [interleaved], an
     * interleaved buffer of [channels]-sample frames; any chunk length is
     * valid, including a single frame.
     */
    fun process(interleaved: FloatArray, offset: Int, frames: Int) {
        flushed = false
        var off = 0
        while (off < frames) {
            var take = frames - off
            val rem = subLen - subFill
            if (take > rem) take = rem
            for (i in 0 until take) {
                val base = (offset + off + i) * channels
                for (c in 0 until channels) {
                    consume(c, interleaved[base + c].toDouble())
                }
            }
            subFill += take
            off += take
            if (subFill == subLen) finishSubBlock()
        }
    }

    /** Drains the true-peak tail; not terminal (process may follow). */
    fun flush() {
        if (flushed) return
        flushed = true
        val tp = this.tp
        if (tp != null) {
            val p = tp.drain()
            if (p > maxTP) maxTP = p
        }
    }

    /** Null when no 400 ms block passes the -70 LUFS gate (never +-Inf/NaN). */
    fun integratedLufs(): Double? = integratedOf(blocks, blocksSize)

    /** Null for digital silence. */
    fun truePeakDbtp(): Double? = dbOrNull(maxTP)

    fun samplePeakDbfs(): Double? = dbOrNull(maxSP)

    /** Gated 400 ms block channel-sum powers, as WaxFlow's m.blocks. */
    fun blockPowers(): DoubleArray = blocks.copyOf(blocksSize)

    fun histogram(): LoudnessHistogram {
        val h = LoudnessHistogram()
        for (i in 0 until blocksSize) h.addBlockPower(blocks[i])
        return h
    }

    /** Full meter state as a versioned little-endian byte array. */
    fun checkpoint(): ByteArray {
        val tp = this.tp
        val tpPresent = tp != null
        val size = 4 + 4 + 4 + 4 + 4 + 8 + 8 + 8 + 1 + 1 +
            channels * 4 * 8 +
            channels * 1 * 8 +
            channels * ST_SUB * 8 +
            4 + blocksSize * 8 +
            (if (tpPresent) channels * (2 * TP_TAPS * 8 + 4) else 0)
        val bb = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(CHECKPOINT_VERSION)
        bb.putInt(rate)
        bb.putInt(channels)
        bb.putInt(subFill)
        bb.putInt(ringPos)
        bb.putLong(ringCnt)
        bb.putDouble(maxSP)
        bb.putDouble(maxTP)
        bb.put(if (flushed) 1 else 0)
        bb.put(if (tpPresent) 1 else 0)
        for (c in 0 until channels) {
            val st = state[c]
            bb.putDouble(st.s1a)
            bb.putDouble(st.s1b)
            bb.putDouble(st.s2a)
            bb.putDouble(st.s2b)
        }
        for (c in 0 until channels) bb.putDouble(subAcc[c])
        for (c in 0 until channels) {
            val r = ring[c]
            for (k in 0 until ST_SUB) bb.putDouble(r[k])
        }
        bb.putInt(blocksSize)
        for (i in 0 until blocksSize) bb.putDouble(blocks[i])
        if (tp != null) {
            for (c in 0 until channels) {
                val h = tp.hist[c]
                for (i in h.indices) bb.putDouble(h[i])
                bb.putInt(tp.pos[c])
            }
        }
        return bb.array()
    }

    companion object {
        /**
         * Rebuilds a meter from [state] produced by [checkpoint]. Continues
         * bit-identically. Rejects a wrong version or length with
         * [IllegalArgumentException].
         */
        fun restore(state: ByteArray): LoudnessMeter {
            return try {
                val bb = ByteBuffer.wrap(state).order(ByteOrder.LITTLE_ENDIAN)
                val version = bb.getInt()
                if (version != CHECKPOINT_VERSION) {
                    throw IllegalArgumentException("loudness checkpoint version $version, want $CHECKPOINT_VERSION")
                }
                val rate = bb.getInt()
                val channels = bb.getInt()
                val meter = LoudnessMeter(rate, channels)
                val subFill = bb.getInt()
                val ringPos = bb.getInt()
                val ringCnt = bb.getLong()
                val maxSP = bb.getDouble()
                val maxTP = bb.getDouble()
                val flushed = bb.get().toInt() != 0
                val tpPresent = bb.get().toInt() != 0
                if (tpPresent != (meter.tp != null)) {
                    throw IllegalArgumentException("loudness checkpoint true-peak flag mismatch")
                }
                if (subFill < 0 || subFill > meter.subLen) throw IllegalArgumentException("loudness checkpoint has bad subFill")
                if (ringPos < 0 || ringPos >= ST_SUB) throw IllegalArgumentException("loudness checkpoint has bad ringPos")
                if (ringCnt < 0) throw IllegalArgumentException("loudness checkpoint has bad ringCnt")
                for (c in 0 until channels) {
                    val st = meter.state[c]
                    st.s1a = bb.getDouble()
                    st.s1b = bb.getDouble()
                    st.s2a = bb.getDouble()
                    st.s2b = bb.getDouble()
                }
                for (c in 0 until channels) meter.subAcc[c] = bb.getDouble()
                for (c in 0 until channels) {
                    val r = meter.ring[c]
                    for (k in 0 until ST_SUB) r[k] = bb.getDouble()
                }
                val blocksSize = bb.getInt()
                if (blocksSize < 0) throw IllegalArgumentException("loudness checkpoint has bad block count")
                val blocks = DoubleArray(max(blocksSize, 16))
                for (i in 0 until blocksSize) blocks[i] = bb.getDouble()
                meter.blocks = blocks
                meter.blocksSize = blocksSize
                if (tpPresent) {
                    val tp = meter.tp!!
                    for (c in 0 until channels) {
                        val h = tp.hist[c]
                        for (i in h.indices) h[i] = bb.getDouble()
                        tp.pos[c] = bb.getInt()
                    }
                }
                meter.subFill = subFill
                meter.ringPos = ringPos
                meter.ringCnt = ringCnt
                meter.maxSP = maxSP
                meter.maxTP = maxTP
                meter.flushed = flushed
                if (bb.remaining() != 0) throw IllegalArgumentException("loudness checkpoint has trailing bytes")
                meter
            } catch (e: BufferUnderflowException) {
                throw IllegalArgumentException("loudness checkpoint is truncated", e)
            }
        }

        /** BS.1770 gating over block powers (absolute -70 LUFS, relative -10 LU). */
        fun integratedOfBlockPowers(powers: DoubleArray): Double? = integratedOf(powers, powers.size)
    }

    private fun consume(c: Int, x: Double) {
        val a = abs(x)
        if (a > maxSP) maxSP = a
        val tp = this.tp
        if (tp != null) {
            val p = tp.push(c, x)
            if (p > maxTP) maxTP = p
        } else if (a > maxTP) {
            maxTP = a
        }
        // The two K-weighting stages, direct form II transposed.
        val st = state[c]
        val y = shelf.b0 * x + st.s1a
        st.s1a = shelf.b1 * x - shelf.a1 * y + st.s1b
        st.s1b = shelf.b2 * x - shelf.a2 * y
        val z = hp.b0 * y + st.s2a
        st.s2a = hp.b1 * y - hp.a1 * z + st.s2b
        st.s2b = hp.b2 * y - hp.a2 * z
        subAcc[c] += z * z
    }

    private fun finishSubBlock() {
        for (c in subAcc.indices) {
            ring[c][ringPos] = subAcc[c]
            subAcc[c] = 0.0
        }
        ringPos = (ringPos + 1) % ST_SUB
        ringCnt++
        subFill = 0
        if (ringCnt >= MOM_SUB) {
            val p = windowPower(MOM_SUB)
            if (p > ABS_GATE_POWER) addBlock(p)
        }
    }

    private fun windowPower(n: Int): Double {
        var sum = 0.0
        for (c in weights.indices) {
            val w = weights[c]
            if (w == 0.0) continue
            val ring = this.ring[c]
            var s = 0.0
            for (k in 1..n) {
                s += ring[(ringPos - k + ST_SUB) % ST_SUB]
            }
            sum += w * s
        }
        return sum / (n * subLen).toDouble()
    }

    private fun addBlock(p: Double) {
        if (blocksSize == blocks.size) {
            blocks = blocks.copyOf(blocks.size * 2)
        }
        blocks[blocksSize++] = p
    }
}

/**
 * K-weighting biquad coefficients for [rate]: shelf b0,b1,b2,a1,a2 then
 * high-pass b0,b1,b2,a1,a2. At 48000 Hz this reproduces the published
 * BS.1770-4 table.
 */
internal fun kWeightingCoefficients(rate: Int): DoubleArray {
    val (shelf, highpass) = kWeighting(rate)
    return doubleArrayOf(
        shelf.b0, shelf.b1, shelf.b2, shelf.a1, shelf.a2,
        highpass.b0, highpass.b1, highpass.b2, highpass.a1, highpass.a2,
    )
}

private fun dbOrNull(v: Double): Double? = if (v <= 0.0) null else 20.0 * log10(v)

private fun integratedOf(b: DoubleArray, n: Int): Double? {
    if (n == 0) return null
    var sum = 0.0
    for (i in 0 until n) sum += b[i]
    val thresh = sum / n.toDouble() / 10.0
    var gated = 0.0
    var g = 0
    for (i in 0 until n) {
        val p = b[i]
        if (p > thresh) {
            gated += p
            g++
        }
    }
    if (g == 0) return null
    return LOUDNESS_OFFSET + 10.0 * log10(gated / g.toDouble())
}

private fun channelWeights(channels: Int): DoubleArray {
    val layout = defaultLayout(channels)
    val w = DoubleArray(channels) { 1.0 }
    var c = 0
    var bit = 0
    while (bit < 32 && c < channels) {
        val mask = 1 shl bit
        if ((layout and mask) == 0) {
            bit++
            continue
        }
        when (mask) {
            LOW_FREQUENCY -> w[c] = 0.0
            BACK_LEFT, BACK_RIGHT, BACK_CENTER, SIDE_LEFT, SIDE_RIGHT -> w[c] = 1.41
        }
        c++
        bit++
    }
    return w
}

private fun defaultLayout(channels: Int): Int = when (channels) {
    1 -> FRONT_CENTER
    2 -> FRONT_LEFT or FRONT_RIGHT
    3 -> FRONT_LEFT or FRONT_RIGHT or FRONT_CENTER
    4 -> FRONT_LEFT or FRONT_RIGHT or BACK_LEFT or BACK_RIGHT
    5 -> FRONT_LEFT or FRONT_RIGHT or FRONT_CENTER or BACK_LEFT or BACK_RIGHT
    6 -> FRONT_LEFT or FRONT_RIGHT or FRONT_CENTER or LOW_FREQUENCY or BACK_LEFT or BACK_RIGHT
    7 -> FRONT_LEFT or FRONT_RIGHT or FRONT_CENTER or LOW_FREQUENCY or BACK_CENTER or SIDE_LEFT or SIDE_RIGHT
    8 -> FRONT_LEFT or FRONT_RIGHT or FRONT_CENTER or LOW_FREQUENCY or BACK_LEFT or BACK_RIGHT or SIDE_LEFT or SIDE_RIGHT
    else -> 0
}
