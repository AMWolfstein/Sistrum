package me.misa198.airmedy.player.dsp

internal data class BiquadCoefficients(
    val b0: Float,
    val b1: Float,
    val b2: Float,
    val a1: Float,
    val a2: Float,
)

internal object BiquadDesign {
    val FrequenciesHz: FloatArray =
        floatArrayOf(32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    const val Q: Float = 1f

    /** Stub for T043 (tests first); implemented in T044. */
    fun peaking(centerHz: Float, gainDb: Float, sampleRate: Int): BiquadCoefficients? = null
}

internal class BiquadEqualizer(val channelCount: Int, val sampleRate: Int) {
    /** Stub for T043 (tests first); implemented in T044. */
    val isActive: Boolean = false

    /** Stub for T043 (tests first); implemented in T044. */
    fun setGains(gainsDb: FloatArray) {}

    /** Stub for T043 (tests first); implemented in T044. */
    fun process(buffer: FloatArray, offset: Int, frameCount: Int) {}

    /** Stub for T043 (tests first); implemented in T044. */
    fun reset() {}
}
