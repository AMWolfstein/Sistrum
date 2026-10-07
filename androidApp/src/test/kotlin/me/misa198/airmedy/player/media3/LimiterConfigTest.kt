package me.misa198.airmedy.player.media3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-Kotlin unit tests for [LimiterConfigs] and [LimiterController] (T046). Every test uses only
 * fakes and asserts at the end that no effect ever received `setEnabled(false)`.
 */
class LimiterConfigTest {

    private val frameMs = 8f

    @Test
    fun activeConfigIsLimiterOnlyWithExplicitParameters() {
        val config = LimiterConfigs.active(frameDurationMs = frameMs)

        assertEquals(2, config.channelCount)
        assertFalse(config.preEqInUse)
        assertFalse(config.mbcInUse)
        assertFalse(config.postEqInUse)
        assertTrue(config.limiterInUse)
        assertTrue(config.limiterEnabled)
        assertEquals(LimiterParams(0f, 1f, 60f, 10f, -1f, 0f, 0), config.limiter)
        assertTrue(config.limiter.thresholdDb < 0f)
        assertEquals(frameMs, config.frameDurationMs, 0f)
    }

    @Test
    fun offConfigKeepsLimiterInChainWithNeutralParameters() {
        val config = LimiterConfigs.neutral(frameDurationMs = frameMs)

        assertTrue(config.limiterInUse)
        assertTrue(config.limiterEnabled)
        assertEquals(LimiterParams(0f, 1f, 60f, 1f, 0f, 0f, 0), config.limiter)
        assertFalse(config.preEqInUse)
        assertFalse(config.mbcInUse)
        assertFalse(config.postEqInUse)
    }

    @Test
    fun startCreatesEnablesAndReportsControlled() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val states = mutableListOf<LimiterState>()
        val controller = LimiterController(42, frameMs, factory, scheduler) { states += it }

        factory.effects += FakeEffect(initialHasControl = true)
        controller.start(clipPrevention = true)

        assertEquals(LimiterState(true, true), controller.state)
        assertEquals(listOf(LimiterState(true, true)), states)
        assertEquals(listOf(true), factory.createdEffects[0].setEnabledCalls)
        assertEquals(LimiterConfigs.active(frameMs).limiter, factory.createdConfigs[0].limiter)
        assertNoDisable(factory)
    }

    @Test
    fun creationFailureReportsUnavailableAndDoesNotThrow() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, frameMs, factory, scheduler) {}

        factory.throwOnCreate = true
        controller.start(clipPrevention = true)

        assertEquals(LimiterState(false, false), controller.state)
        assertTrue("no probing at start after a failed create", scheduler.tasks.isEmpty())
        assertNoDisable(factory)
    }

    @Test
    fun clipPreventionToggleAppliesNeutralAndActiveNeverDisables() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, frameMs, factory, scheduler) {}

        factory.effects += FakeEffect(initialHasControl = true)
        controller.start(clipPrevention = true)
        val effect = factory.createdEffects[0]

        controller.setClipPrevention(false)
        assertEquals(LimiterConfigs.neutral(frameMs).limiter, effect.appliedParams.last())

        controller.setClipPrevention(true)
        assertEquals(LimiterConfigs.active(frameMs).limiter, effect.appliedParams.last())

        assertNoDisable(factory)
    }

    @Test
    fun controlLostWhileEnabledKeepsEffectAndRegainReappliesCurrentParams() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, frameMs, factory, scheduler) {}

        factory.effects += FakeEffect(initialHasControl = true, initialEnabled = true)
        controller.start(clipPrevention = true)
        val effect = factory.createdEffects[0]

        effect.hasControlValue = false
        effect.listener!!.onControlStatusChange(false)
        assertEquals(LimiterState(true, false), controller.state)
        assertFalse(effect.released)
        assertTrue("no probe while the effect still reports enabled", scheduler.tasks.isEmpty())

        controller.setClipPrevention(false)

        effect.hasControlValue = true
        effect.listener!!.onControlStatusChange(true)
        assertEquals(LimiterState(true, true), controller.state)
        assertEquals(LimiterConfigs.neutral(frameMs).limiter, effect.appliedParams.last())
        assertNoDisable(factory)
    }

    @Test
    fun controlLostWhileDisabledReleasesAndProbesUntilControl() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, frameMs, factory, scheduler) {}

        factory.effects += FakeEffect(initialHasControl = true, initialEnabled = true)
        factory.effects += FakeEffect(initialHasControl = false)
        factory.effects += FakeEffect(initialHasControl = true)
        controller.start(clipPrevention = true)
        val initial = factory.createdEffects[0]

        initial.hasControlValue = false
        initial.enabledValue = false
        initial.listener!!.onControlStatusChange(false)

        assertTrue(initial.released)
        assertEquals(LimiterState(false, false), controller.state)
        assertEquals(1, scheduler.pending().size)
        assertEquals(5_000L, scheduler.pending().first().delayMs)

        scheduler.runNext()
        val probe1 = factory.createdEffects[1]
        assertTrue("probe without control should be released", probe1.released)
        assertEquals("probe without control should reschedule", 1, scheduler.pending().size)

        scheduler.runNext()
        val probe2 = factory.createdEffects[2]
        assertEquals(LimiterState(true, true), controller.state)
        assertEquals(listOf(true), probe2.setEnabledCalls)
        assertEquals(LimiterConfigs.active(frameMs).limiter, probe2.appliedParams.last())
        assertTrue("no further probe once control is regained", scheduler.pending().isEmpty())
        assertNoDisable(factory)
    }

    @Test
    fun enabledGetterThrowingOnLossIsTreatedAsPossibleMute() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, frameMs, factory, scheduler) {}

        factory.effects += FakeEffect(initialHasControl = true, initialEnabled = true, enabledThrows = true)
        factory.effects += FakeEffect(initialHasControl = true)
        controller.start(clipPrevention = true)
        val initial = factory.createdEffects[0]

        initial.hasControlValue = false
        initial.listener!!.onControlStatusChange(false)

        assertTrue(initial.released)
        assertEquals(LimiterState(false, false), controller.state)
        assertEquals(1, scheduler.pending().size)
        assertNoDisable(factory)
    }

    @Test
    fun unexpectedDisableIsTreatedAsPossibleMute() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, frameMs, factory, scheduler) {}

        factory.effects += FakeEffect(initialHasControl = true, initialEnabled = true)
        factory.effects += FakeEffect(initialHasControl = true)
        controller.start(clipPrevention = true)
        val initial = factory.createdEffects[0]

        initial.listener!!.onEnableStatusChange(false)

        assertTrue(initial.released)
        assertEquals(LimiterState(false, false), controller.state)
        assertEquals(1, scheduler.pending().size)
        assertNoDisable(factory)
    }

    @Test
    fun createdWithoutControlWaitsForRegain() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, frameMs, factory, scheduler) {}

        factory.effects += FakeEffect(initialHasControl = false)
        controller.start(clipPrevention = true)
        val effect = factory.createdEffects[0]

        assertEquals(LimiterState(true, false), controller.state)
        assertFalse(effect.released)
        assertTrue("no probe when created without control", scheduler.tasks.isEmpty())

        effect.hasControlValue = true
        effect.listener!!.onControlStatusChange(true)
        assertEquals(LimiterState(true, true), controller.state)
        assertEquals(LimiterConfigs.active(frameMs).limiter, effect.appliedParams.last())
        assertNoDisable(factory)
    }

    @Test
    fun releaseCancelsProbeAndIgnoresLateCallbacks() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, frameMs, factory, scheduler) {}

        factory.effects += FakeEffect(initialHasControl = true, initialEnabled = true)
        controller.start(clipPrevention = true)
        val effect = factory.createdEffects[0]

        effect.hasControlValue = false
        effect.enabledValue = false
        effect.listener!!.onControlStatusChange(false)
        assertEquals(1, scheduler.pending().size)

        factory.effects += FakeEffect(initialHasControl = true)
        controller.release()
        assertTrue("scheduled probe should be cancelled", scheduler.tasks.first().cancelled)
        controller.release()

        effect.listener!!.onControlStatusChange(true)
        effect.listener!!.onEnableStatusChange(false)
        assertEquals(LimiterState(false, false), controller.state)

        val createdBefore = factory.createdEffects.size
        scheduler.tasks.first().runEvenIfCancelled()
        assertEquals("cancelled probe must not create an effect", createdBefore, factory.createdEffects.size)
        assertEquals(LimiterState(false, false), controller.state)

        controller.start(clipPrevention = true)
        controller.setClipPrevention(false)
        assertEquals("no effect may be created after release", createdBefore, factory.createdEffects.size)
        assertTrue("nothing may be applied after release", factory.createdEffects.all { it.appliedParams.isEmpty() })

        assertNoDisable(factory)
    }

    @Test
    fun regainEnablesEffectCreatedWithoutControl() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, frameMs, factory, scheduler) {}

        factory.effects += FakeEffect(initialHasControl = false)
        controller.start(clipPrevention = true)
        val effect = factory.createdEffects[0]
        assertEquals(LimiterState(true, false), controller.state)

        effect.hasControlValue = true
        effect.listener!!.onControlStatusChange(true)

        assertEquals(LimiterState(true, true), controller.state)
        assertEquals(listOf(true, true), effect.setEnabledCalls)
        assertEquals(LimiterConfigs.active(frameMs).limiter, effect.appliedParams.last())
        assertNoDisable(factory)
    }

    @Test
    fun effectCallThrowingAtStartDoesNotReachCaller() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, frameMs, factory, scheduler) {}

        factory.effects += FakeEffect(initialHasControl = true, setEnabledThrows = true)
        controller.start(clipPrevention = true)
        val effect = factory.createdEffects[0]

        assertTrue(effect.released)
        assertEquals(LimiterState(false, false), controller.state)
        assertEquals(1, scheduler.pending().size)
        assertEquals(5_000L, scheduler.pending().first().delayMs)
        assertNoDisable(factory)
    }

    @Test
    fun applyThrowingOnClipPreventionToggleReleasesAndProbes() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, frameMs, factory, scheduler) {}

        factory.effects += FakeEffect(initialHasControl = true, applyThrows = true)
        controller.start(clipPrevention = true)
        val effect = factory.createdEffects[0]
        assertEquals(LimiterState(true, true), controller.state)

        controller.setClipPrevention(false)

        assertTrue(effect.released)
        assertEquals(LimiterState(false, false), controller.state)
        assertEquals(1, scheduler.pending().size)
        assertNoDisable(factory)
    }

    @Test
    fun releaseThrowingIsIgnored() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, frameMs, factory, scheduler) {}

        factory.effects += FakeEffect(initialHasControl = true, releaseThrows = true)
        controller.start(clipPrevention = true)
        val effect = factory.createdEffects[0]

        controller.release()

        assertTrue(effect.released)
        assertNoDisable(factory)
    }

    private fun assertNoDisable(factory: FakeFactory) {
        for (effect in factory.createdEffects) {
            assertTrue("effect must never receive setEnabled(false)", effect.setEnabledCalls.none { !it })
        }
    }

    private class FakeEffect(
        initialHasControl: Boolean,
        initialEnabled: Boolean = true,
        var enabledThrows: Boolean = false,
        var setEnabledThrows: Boolean = false,
        var applyThrows: Boolean = false,
        var releaseThrows: Boolean = false,
    ) : LimiterEffect {
        lateinit var config: LimiterConfig
        var listener: LimiterEffectListener? = null
        val setEnabledCalls = mutableListOf<Boolean>()
        val appliedParams = mutableListOf<LimiterParams>()
        var hasControlValue = initialHasControl
        var enabledValue = initialEnabled
        var released = false
            private set

        override val enabled: Boolean
            get() {
                if (enabledThrows) throw IllegalStateException("enabled read failed")
                return enabledValue
            }

        override fun hasControl(): Boolean = hasControlValue

        override fun setEnabled(enabled: Boolean) {
            setEnabledCalls += enabled
            enabledValue = enabled
            if (setEnabledThrows) throw IllegalStateException("setEnabled failed")
        }

        override fun apply(params: LimiterParams) {
            appliedParams += params
            if (applyThrows) throw IllegalStateException("apply failed")
        }

        override fun release() {
            released = true
            if (releaseThrows) throw IllegalStateException("release failed")
        }
    }

    private class FakeFactory : LimiterEffectFactory {
        val effects = mutableListOf<FakeEffect>()
        var throwOnCreate = false
        private var nextEffect = 0
        val createdEffects = mutableListOf<FakeEffect>()
        val createdConfigs = mutableListOf<LimiterConfig>()

        override fun create(sessionId: Int, config: LimiterConfig, listener: LimiterEffectListener): LimiterEffect {
            if (throwOnCreate) throw RuntimeException("create failed")
            val effect = effects[nextEffect++]
            effect.config = config
            effect.listener = listener
            createdEffects += effect
            createdConfigs += config
            return effect
        }
    }

    private class FakeScheduler : LimiterScheduler {
        data class Task(val delayMs: Long, val run: () -> Unit) {
            var cancelled = false
            var executed = false

            fun runEvenIfCancelled() {
                executed = true
                run.invoke()
            }
        }

        val tasks = mutableListOf<Task>()

        override fun schedule(delayMs: Long, task: () -> Unit): () -> Unit {
            val t = Task(delayMs, task)
            tasks += t
            return { t.cancelled = true }
        }

        fun pending(): List<Task> = tasks.filter { !it.cancelled && !it.executed }

        fun runNext() {
            val task = pending().firstOrNull() ?: error("no pending scheduled task")
            task.executed = true
            task.run()
        }
    }
}
