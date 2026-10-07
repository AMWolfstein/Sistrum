package me.misa198.airmedy.analysis.loudness

/**
 * Counts of gated 400 ms blocks in 0.1 LU bins from −70 to +5 LUFS (750 bins); ours, not ported.
 *
 * This is the T050a stub: it pins the API the album/group tests are written against and returns the
 * documented empty values until T051a implements it.
 */
class LoudnessHistogram {
    val blockCount: Long get() = 0

    fun addBlockPower(power: Double) {}

    fun merge(other: LoudnessHistogram) {}

    /** Gating on bin centres (libebur128 histogram method). */
    fun integratedLufs(): Double? = null

    /** Sparse, base64. */
    fun encode(): String = ""

    companion object {
        fun decode(text: String): LoudnessHistogram = LoudnessHistogram()
    }
}
