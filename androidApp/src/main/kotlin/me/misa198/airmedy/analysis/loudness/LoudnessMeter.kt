package me.misa198.airmedy.analysis.loudness

/**
 * BS.1770-4 meter over interleaved float PCM (nominal ±1.0). Port of WaxFlow dsp/loudness (T051a).
 *
 * This is the T050a stub: it pins the API the tests in
 * `androidApp/src/test/.../analysis/loudness` are written against. It deliberately returns the
 * documented empty values so the value-expecting tests fail on assertions before the port lands.
 *
 * The intended constructor contract (not enforced by the stub) is rate > 0 and channels in 1..8;
 * see the invalid-construction test.
 */
class LoudnessMeter(val sampleRate: Int, val channels: Int) {
    /**
     * Consumes [frames] frames starting at frame [offset] of [interleaved], an interleaved buffer of
     * [channels]-sample frames; any chunk length is valid, including a single frame.
     */
    fun process(interleaved: FloatArray, offset: Int, frames: Int) {}

    /** Drains the true-peak tail; not terminal (process may follow). */
    fun flush() {}

    /** Null when no 400 ms block passes the −70 LUFS gate (never ±Inf/NaN). */
    fun integratedLufs(): Double? = null

    /** Null for digital silence. */
    fun truePeakDbtp(): Double? = null

    fun samplePeakDbfs(): Double? = null

    /** Gated 400 ms block channel-sum powers, as WaxFlow's m.blocks. */
    fun blockPowers(): DoubleArray = DoubleArray(0)

    fun histogram(): LoudnessHistogram = LoudnessHistogram()

    /** Full meter state. */
    fun checkpoint(): ByteArray = ByteArray(0)

    companion object {
        fun restore(state: ByteArray): LoudnessMeter = LoudnessMeter(48000, 2)

        /** BS.1770 gating over block powers (absolute −70 LUFS, relative −10 LU): WaxFlow integratedOf. */
        fun integratedOfBlockPowers(powers: DoubleArray): Double? = null
    }
}

/**
 * K-weighting biquad coefficients for [rate]: shelf b0,b1,b2,a1,a2 then high-pass b0,b1,b2,a1,a2.
 * At 48000 Hz this must reproduce the published BS.1770-4 table (pinned by the test).
 */
internal fun kWeightingCoefficients(rate: Int): DoubleArray = DoubleArray(10)
