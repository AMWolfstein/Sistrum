package me.misa198.airmedy.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperUnlockTest {
    @Test
    fun sevenTapsWithinTheGapUnlockOnlyOnTheSeventh() {
        val counter = DeveloperUnlockCounter()

        val results = (0 until 7).map { index -> counter.tap(index * 100L) }

        assertTrue("only the final tap should unlock", results.dropLast(1).none { it })
        assertTrue(results.last())
    }

    @Test
    fun gapLongerThanMaxRestartsTheCount() {
        val counter = DeveloperUnlockCounter()
        counter.tap(0)
        counter.tap(100)
        counter.tap(200)

        assertFalse("a 1001 ms gap must restart the count", counter.tap(1_201))

        val results = (1 until 7).map { index -> counter.tap(1_201 + index * 100L) }
        assertTrue("still needs seven taps after the reset", results.dropLast(1).none { it })
        assertTrue(results.last())
    }

    @Test
    fun exactlyMaxGapStillCounts() {
        val counter = DeveloperUnlockCounter()

        val results = (0 until 7).map { index -> counter.tap(index * 1_000L) }

        assertTrue(results.dropLast(1).none { it })
        assertTrue(results.last())
    }

    @Test
    fun tapsAfterUnlockReturnFalse() {
        val counter = DeveloperUnlockCounter()
        (0 until 7).forEach { index -> counter.tap(index * 100L) }

        assertFalse(counter.tap(700))
    }

    @Test
    fun requiredOneUnlocksOnTheFirstTap() {
        val counter = DeveloperUnlockCounter(required = 1)

        assertTrue(counter.tap(0))
    }
}
