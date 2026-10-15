package com.amwolfstein.sistrum.ui.theme

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReducedMotionTest {
    @Test
    fun zeroScaleReducesMotionAndPositiveScalesKeepMotion() = runTest {
        var scale = 0f
        var changed: () -> Unit = {}
        val values = mutableListOf<Boolean>()
        val job = launch {
            reducedMotionFlow({ scale }) { callback ->
                changed = callback
                val unregister = {}
                unregister
            }.collect { values.add(it) }
        }
        runCurrent()
        assertEquals(listOf(true), values)
        for (enabledScale in listOf(0.5f, 1f, 2f)) {
            scale = enabledScale
            changed()
            runCurrent()
            assertFalse(values.last())
        }
        scale = 0f
        changed()
        runCurrent()
        assertTrue(values.last())
        job.cancel()
    }

    @Test
    fun observerRegistersBeforeInitialReadAndUnregistersOnCancellation() = runTest {
        val events = mutableListOf<String>()
        val job = launch {
            reducedMotionFlow(
                readScale = { events.add("read"); 1f },
                observeChanges = {
                    events.add("register")
                    val unregister = { events.add("unregister"); Unit }
                    unregister
                },
            ).collect {}
        }
        runCurrent()
        assertEquals(listOf("register", "read"), events)
        job.cancel()
        runCurrent()
        assertEquals(listOf("register", "read", "unregister"), events)
    }

    @Test
    fun motionDurationsFollowTheFoundationDefaultsAndBecomeZeroWhenReduced() {
        for (palette in listOf(LightColors, DarkColors)) {
            val normal = sistrumTokens(palette, reducedMotion = false)
            assertEquals(SistrumMotion(150, 300, 500), normal.motion)
            val reduced = sistrumTokens(palette, reducedMotion = true)
            assertEquals(SistrumMotion(0, 0, 0), reduced.motion)
            assertEquals(normal.colors, reduced.colors)
            assertEquals(normal.spacing, reduced.spacing)
            assertEquals(normal.shapes, reduced.shapes)
        }
    }
}
