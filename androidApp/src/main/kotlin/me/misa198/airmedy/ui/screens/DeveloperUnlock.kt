package me.misa198.airmedy.ui.screens

/**
 * Counts consecutive taps that arrive within [maxGapMs] of each other. A tap
 * that lands later than the gap restarts the count at one. [tap] returns true
 * exactly on the tap that completes [required] consecutive taps and false for
 * every other tap, including any tap after the counter has unlocked.
 */
internal class DeveloperUnlockCounter(
    private val required: Int = 7,
    private val maxGapMs: Long = 1_000,
) {
    private var consecutiveTaps = 0
    private var lastTapMs: Long? = null
    private var unlocked = false

    fun tap(nowMs: Long): Boolean {
        if (unlocked) return false
        val previousTapMs = lastTapMs
        consecutiveTaps = if (previousTapMs == null || nowMs - previousTapMs > maxGapMs) {
            1
        } else {
            consecutiveTaps + 1
        }
        lastTapMs = nowMs
        if (consecutiveTaps >= required) {
            unlocked = true
            return true
        }
        return false
    }
}
