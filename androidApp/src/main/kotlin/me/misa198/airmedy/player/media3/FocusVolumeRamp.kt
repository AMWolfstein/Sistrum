package me.misa198.airmedy.player.media3

import kotlin.math.max
import kotlin.math.min

/**
 * Pure Kotlin focus-gain ramp mirroring the native engine's `next_focus_gain`
 * (`androidApp/src/main/cpp/ffmpeg_player.cpp`): the gain moves linearly toward its
 * target, full scale over [DownFullScaleMs] when ducking and over [UpFullScaleMs] when
 * restoring, and clamps exactly at the target.
 *
 * A [setTarget] captures the value at that instant as the new ramp start, so a retarget
 * mid-ramp continues from the current value instead of jumping.
 */
internal class FocusVolumeRamp(initial: Float = 1f) {

    private var startValue: Float = initial.coerceIn(0f, 1f)
    private var targetValue: Float = startValue
    private var startMs: Long = 0L

    /** Sets the target (clamped to 0..1), starting the ramp from the value at [nowMs]. */
    fun setTarget(target: Float, nowMs: Long) {
        val clamped = target.coerceIn(0f, 1f)
        startValue = valueAt(nowMs)
        startMs = nowMs
        targetValue = clamped
    }

    /**
     * Value of the ramp at [nowMs]: linear from the start value toward the target at
     * slope 1/[DownFullScaleMs] per ms when decreasing and 1/[UpFullScaleMs] per ms when
     * increasing, clamped exactly at the target.
     */
    fun valueAt(nowMs: Long): Float {
        if (startValue == targetValue) return targetValue
        val elapsed = (nowMs - startMs).coerceAtLeast(0L)
        val fullScaleMs = if (targetValue < startValue) DownFullScaleMs else UpFullScaleMs
        val progress = elapsed.toFloat() / fullScaleMs.toFloat()
        return if (targetValue < startValue) {
            max(targetValue, startValue - progress)
        } else {
            min(targetValue, startValue + progress)
        }
    }

    /** True once [valueAt] has reached the target. */
    fun isSettled(nowMs: Long): Boolean = valueAt(nowMs) == targetValue

    companion object {
        /** Full-scale duck time, matching `kFocusDuckFadeOutMs` in the native engine. */
        const val DownFullScaleMs = 120

        /** Full-scale restore time, matching `kFocusDuckFadeInMs` in the native engine. */
        const val UpFullScaleMs = 240
    }
}
