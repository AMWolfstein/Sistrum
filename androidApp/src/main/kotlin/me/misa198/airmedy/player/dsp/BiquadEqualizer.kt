package me.misa198.airmedy.player.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

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

    /**
     * Designs an RBJ peaking EQ band using the exact float arithmetic of the native engine
     * (`configure_eq` in ffmpeg_player.cpp). A zero gain yields `null` so the band can be skipped.
     */
    fun peaking(centerHz: Float, gainDb: Float, sampleRate: Int): BiquadCoefficients? {
        if (gainDb == 0f) return null
        val omega = 2f * PI.toFloat() * centerHz / sampleRate.toFloat()
        val alpha = sin(omega) / (2f * Q)
        val a = 10f.pow(gainDb / 40f)
        val a0 = 1f + alpha / a
        val b0 = (1f + alpha * a) / a0
        val b1 = (-2f * cos(omega)) / a0
        val b2 = (1f - alpha * a) / a0
        val a1 = (-2f * cos(omega)) / a0
        val a2 = (1f - alpha / a) / a0
        return BiquadCoefficients(b0, b1, b2, a1, a2)
    }
}

/**
 * A 10-band peaking equalizer matching the native engine: the same band frequencies, the same
 * transposed direct form II biquad cascade, and the same per-channel filter state.
 *
 * Gain changes are click-free: after the first processed block a [setGains] call starts a linear
 * interpolation of every band's coefficients from their current values to the new targets over
 * `TRANSITION_FRAMES` frames, keeping the `z1`/`z2` state so there is never a step. Setting gains
 * before the first [process] call, or right after [reset], applies immediately with no transition.
 *
 * Transitions to and from 0 dB move only the numerator while the poles are frozen, so the response
 * blends linearly between the peaking filter and unity instead of jumping:
 * - active -> 0 dB: the numerator ramps from the peaking `b` to `a` (unity), then the band keeps
 *   running an input-free state recurrence (`y = x + z1`, `z1' = -a1*z1 + z2`, `z2' = -a2*z1`)
 *   until its state decays below [DRAIN_EPSILON] or `maxDrainFrames` (1 s) elapse, and is then
 *   dropped. Running the recurrence without the live input is what lets the state actually decay
 *   (with the live input, float rounding re-excites it and low bands never settle).
 * - 0 dB -> active: the numerator ramps from unity (`b == a` with the target poles) to the target.
 */
internal class BiquadEqualizer(val channelCount: Int, val sampleRate: Int) {

    private val bands = BiquadDesign.FrequenciesHz.size
    private val transitionFrames = maxOf(1, (0.02f * sampleRate).roundToInt())
    private val maxDrainFrames = sampleRate

    private val targetGainsDb = FloatArray(bands)

    private val targetB0 = FloatArray(bands)
    private val targetB1 = FloatArray(bands)
    private val targetB2 = FloatArray(bands)
    private val targetA1 = FloatArray(bands)
    private val targetA2 = FloatArray(bands)
    private val targetActive = BooleanArray(bands)

    private val curB0 = FloatArray(bands)
    private val curB1 = FloatArray(bands)
    private val curB2 = FloatArray(bands)
    private val curA1 = FloatArray(bands)
    private val curA2 = FloatArray(bands)
    private val curActive = BooleanArray(bands)

    private val startB0 = FloatArray(bands)
    private val startB1 = FloatArray(bands)
    private val startB2 = FloatArray(bands)
    private val startA1 = FloatArray(bands)
    private val startA2 = FloatArray(bands)

    private val z1 = FloatArray(bands * channelCount)
    private val z2 = FloatArray(bands * channelCount)

    /**
     * A band whose target is 0 dB is not stepped straight to inactive: it first blends to unity
     * (`b == a`, poles fixed) and then runs an input-free recurrence on its own state until that
     * state has decayed, so neither the numerator step nor the state clear can click.
     */
    private val draining = BooleanArray(bands)
    private val drainFrames = IntArray(bands)

    private var transitionRemaining = 0
    private var transitionElapsed = 0
    private var hasProcessed = false

    init {
        // Inactive bands hold identity coefficients so a 0 -> non-zero transition starts from unity.
        for (i in 0 until bands) {
            curB0[i] = 1f
            startB0[i] = 1f
            targetB0[i] = 1f
        }
    }

    /** True while any band is active or a gain transition is still interpolating. */
    val isActive: Boolean
        get() {
            for (i in 0 until bands) if (curActive[i]) return true
            return transitionRemaining > 0
        }

    /** Test-only view of one band's state; a draining band counts as active. */
    internal fun isBandActiveForTest(index: Int): Boolean = curActive[index]

    fun setGains(gainsDb: FloatArray) {
        require(gainsDb.size == bands) { "expected $bands EQ gains, got ${gainsDb.size}" }
        if (targetGainsDb.contentEquals(gainsDb)) return

        System.arraycopy(gainsDb, 0, targetGainsDb, 0, bands)
        for (i in 0 until bands) {
            val c = BiquadDesign.peaking(BiquadDesign.FrequenciesHz[i], gainsDb[i], sampleRate)
            if (c != null) {
                targetActive[i] = true
                targetB0[i] = c.b0
                targetB1[i] = c.b1
                targetB2[i] = c.b2
                targetA1[i] = c.a1
                targetA2[i] = c.a2
            } else {
                targetActive[i] = false
                targetB0[i] = 1f
                targetB1[i] = 0f
                targetB2[i] = 0f
                targetA1[i] = 0f
                targetA2[i] = 0f
            }
        }

        if (!hasProcessed) {
            applyImmediately()
        } else {
            beginTransition()
        }
    }

    fun process(buffer: FloatArray, offset: Int, frameCount: Int) {
        hasProcessed = true
        if (!isActive) return
        for (f in 0 until frameCount) {
            if (transitionRemaining > 0) advanceTransition()
            val base = offset + f * channelCount
            for (ch in 0 until channelCount) {
                var sample = buffer[base + ch]
                for (band in 0 until bands) {
                    if (!curActive[band]) continue
                    val stateIndex = band * channelCount + ch
                    val z1v = z1[stateIndex]
                    val z2v = z2[stateIndex]
                    if (draining[band]) {
                        // b == a, so the live input cancels: run the state-only recurrence so it
                        // decays geometrically instead of being re-excited by the signal.
                        sample += z1v
                        z1[stateIndex] = -curA1[band] * z1v + z2v
                        z2[stateIndex] = -curA2[band] * z1v
                        if (ch == 0) drainFrames[band]++
                    } else {
                        val y = curB0[band] * sample + z1v
                        z1[stateIndex] = curB1[band] * sample - curA1[band] * y + z2v
                        z2[stateIndex] = curB2[band] * sample - curA2[band] * y
                        sample = y
                    }
                }
                buffer[base + ch] = sample
            }
        }
        settleDrainingBands()
    }

    /** Deactivates a band once its (unity) state has decayed enough that dropping it cannot click. */
    private fun settleDrainingBands() {
        for (i in 0 until bands) {
            if (!draining[i]) continue
            val base = i * channelCount
            var maxState = 0f
            for (ch in 0 until channelCount) {
                val a = abs(z1[base + ch])
                if (a > maxState) maxState = a
                val b = abs(z2[base + ch])
                if (b > maxState) maxState = b
            }
            if (maxState < DRAIN_EPSILON || drainFrames[i] >= maxDrainFrames) {
                draining[i] = false
                curActive[i] = false
                drainFrames[i] = 0
                for (ch in 0 until channelCount) {
                    z1[base + ch] = 0f
                    z2[base + ch] = 0f
                }
            }
        }
    }

    fun reset() {
        for (i in 0 until bands * channelCount) {
            z1[i] = 0f
            z2[i] = 0f
        }
        targetGainsDb.fill(0f)
        for (i in 0 until bands) {
            targetActive[i] = false
            targetB0[i] = 1f
            targetB1[i] = 0f
            targetB2[i] = 0f
            targetA1[i] = 0f
            targetA2[i] = 0f

            curActive[i] = false
            curB0[i] = 1f
            curB1[i] = 0f
            curB2[i] = 0f
            curA1[i] = 0f
            curA2[i] = 0f

            startB0[i] = 1f
            startB1[i] = 0f
            startB2[i] = 0f
            startA1[i] = 0f
            startA2[i] = 0f

            draining[i] = false
            drainFrames[i] = 0
        }
        transitionRemaining = 0
        transitionElapsed = 0
        hasProcessed = false
    }

    private fun applyImmediately() {
        for (i in 0 until bands) {
            curActive[i] = targetActive[i]
            curB0[i] = targetB0[i]
            curB1[i] = targetB1[i]
            curB2[i] = targetB2[i]
            curA1[i] = targetA1[i]
            curA2[i] = targetA2[i]
            draining[i] = false
            drainFrames[i] = 0
        }
        transitionRemaining = 0
        transitionElapsed = 0
    }

    private fun beginTransition() {
        for (i in 0 until bands) {
            startB0[i] = curB0[i]
            startB1[i] = curB1[i]
            startB2[i] = curB2[i]
            startA1[i] = curA1[i]
            startA2[i] = curA2[i]

            if (draining[i] && !targetActive[i]) {
                // This band's own target is still 0 dB: leave it draining untouched so dragging
                // other bands cannot restart or prolong it. Freeze it out of the blend.
                targetB0[i] = curB0[i]
                targetB1[i] = curB1[i]
                targetB2[i] = curB2[i]
                targetA1[i] = curA1[i]
                targetA2[i] = curA2[i]
                continue
            }

            draining[i] = false

            if (targetActive[i]) {
                if (!curActive[i]) {
                    // 0 dB -> active: start at unity with the target poles so only the
                    // numerator moves (b == a at t = 0). The (zeroed) state starts clean.
                    startB0[i] = 1f
                    startB1[i] = targetA1[i]
                    startB2[i] = targetA2[i]
                    startA1[i] = targetA1[i]
                    startA2[i] = targetA2[i]
                    val base = i * channelCount
                    for (ch in 0 until channelCount) {
                        z1[base + ch] = 0f
                        z2[base + ch] = 0f
                    }
                }
                curActive[i] = true
            } else if (curActive[i]) {
                // active -> 0 dB: freeze the current (start) poles and move only the numerator
                // to match them (b == a -> unity). The band stays active and drains afterwards.
                targetB0[i] = 1f
                targetB1[i] = startA1[i]
                targetB2[i] = startA2[i]
                targetA1[i] = startA1[i]
                targetA2[i] = startA2[i]
                curActive[i] = true
            } else {
                curActive[i] = false
            }
        }
        transitionElapsed = 0
        transitionRemaining = transitionFrames
    }

    private fun advanceTransition() {
        transitionElapsed++
        if (transitionElapsed >= transitionFrames) {
            for (i in 0 until bands) {
                curB0[i] = targetB0[i]
                curB1[i] = targetB1[i]
                curB2[i] = targetB2[i]
                curA1[i] = targetA1[i]
                curA2[i] = targetA2[i]
                if (targetActive[i]) {
                    curActive[i] = true
                    draining[i] = false
                } else if (curActive[i]) {
                    // Finished blending to unity; keep the band alive until its state decays.
                    if (!draining[i]) drainFrames[i] = 0
                    curActive[i] = true
                    draining[i] = true
                } else {
                    curActive[i] = false
                    draining[i] = false
                }
            }
            transitionRemaining = 0
            transitionElapsed = 0
        } else {
            val t = transitionElapsed.toFloat() / transitionFrames.toFloat()
            for (i in 0 until bands) {
                curB0[i] = startB0[i] + (targetB0[i] - startB0[i]) * t
                curB1[i] = startB1[i] + (targetB1[i] - startB1[i]) * t
                curB2[i] = startB2[i] + (targetB2[i] - startB2[i]) * t
                curA1[i] = startA1[i] + (targetA1[i] - startA1[i]) * t
                curA2[i] = startA2[i] + (targetA2[i] - startA2[i]) * t
            }
        }
    }

    private companion object {
        /** A draining band is dropped only once every state term is below this magnitude. */
        const val DRAIN_EPSILON = 1e-6f
    }
}
