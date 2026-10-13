package com.amwolfstein.sistrum.ui.navigation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Manually scrolling the lyrics used to switch every line to full brightness until a
 * tap or seek, with no way back on its own and no hint of the current line.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LyricsBrowseModeTest {

    @Test
    fun browseModeEndsAfterTheIdleTimeout() = runTest {
        val scrolling = MutableStateFlow(false)
        val idle = async { awaitLyricsBrowseIdle(scrolling) }
        advanceTimeBy(LyricsBrowseIdleTimeoutMs - 1)
        runCurrent()
        assertFalse("still browsing just before the timeout", idle.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(idle.isCompleted)
    }

    @Test
    fun scrollingAgainRestartsTheTimeout() = runTest {
        val scrolling = MutableStateFlow(false)
        val idle = async { awaitLyricsBrowseIdle(scrolling) }
        advanceTimeBy(3_000)
        scrolling.value = true
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertFalse("never idle while the list is still scrolling", idle.isCompleted)
        scrolling.value = false
        runCurrent()
        advanceTimeBy(LyricsBrowseIdleTimeoutMs - 1)
        runCurrent()
        assertFalse("countdown restarted from the last scroll", idle.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(idle.isCompleted)
    }

    @Test
    fun theCurrentLineStaysMostProminentWhileBrowsing() {
        assertEquals(1f, syncedLyricOpacity(distance = 0, focusMode = false))
        for (distance in 1..5) {
            val browsing = syncedLyricOpacity(distance, focusMode = false)
            assertTrue("line $distance away is dimmer than the current line", browsing < 1f)
            assertTrue("line $distance away is brighter than in focus mode", browsing > syncedLyricOpacity(distance, focusMode = true))
        }
    }
}
