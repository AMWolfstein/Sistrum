package me.misa198.airmedy.player.media3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-Kotlin unit tests for [LimiterController.setFrameDurationMs] (T048b). Uses only fakes and
 * asserts at the end that no effect ever received `setEnabled(false)`.
 */
class LimiterFrameDurationTest {

    @Test
    fun recreatesEffectWithNewDuration() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, 4f, factory, scheduler) {}

        factory.effects += FakeEffect(initialHasControl = true)
        controller.start(clipPrevention = true)
        val first = factory.createdEffects[0]
        assertEquals(LimiterState(true, true), controller.state)

        factory.effects += FakeEffect(initialHasControl = true)
        controller.setFrameDurationMs(40f)

        assertTrue("first effect should be released", first.released)
        assertEquals(2, factory.createdEffects.size)
        val second = factory.createdEffects[1]
        assertEquals(40f, factory.createdConfigs[1].frameDurationMs, 0f)
        assertEquals(listOf(true), second.setEnabledCalls)
        assertEquals(LimiterState(true, true), controller.state)
        assertNoDisable(factory)
    }

    @Test
    fun smallChangeIsIgnored() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, 40f, factory, scheduler) {}

        factory.effects += FakeEffect(initialHasControl = true)
        controller.start(clipPrevention = true)
        val countBefore = factory.createdEffects.size

        controller.setFrameDurationMs(40.3f)

        assertEquals(countBefore, factory.createdEffects.size)
        assertEquals(40f, controller.frameDurationMsForTest, 0f)
        assertNoDisable(factory)
    }

    @Test
    fun invalidValuesIgnored() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, 40f, factory, scheduler) {}

        factory.effects += FakeEffect(initialHasControl = true)
        controller.start(clipPrevention = true)
        val countBefore = factory.createdEffects.size

        for (bad in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            controller.setFrameDurationMs(bad)
        }

        assertEquals(countBefore, factory.createdEffects.size)
        assertEquals(40f, controller.frameDurationMsForTest, 0f)
        assertNoDisable(factory)
    }

    @Test
    fun afterReleaseIsNoOp() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, 4f, factory, scheduler) {}

        factory.effects += FakeEffect(initialHasControl = true)
        controller.start(clipPrevention = true)
        controller.release()
        val countBefore = factory.createdEffects.size

        controller.setFrameDurationMs(40f)

        assertEquals(countBefore, factory.createdEffects.size)
        assertNoDisable(factory)
    }

    @Test
    fun whileProbingStoredForNextCreation() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, 4f, factory, scheduler) {}

        factory.effects += FakeEffect(initialHasControl = true, setEnabledThrows = true)
        factory.effects += FakeEffect(initialHasControl = true)
        controller.start(clipPrevention = true)
        assertEquals(1, factory.createdEffects.size)
        assertEquals(1, scheduler.pending().size)

        controller.setFrameDurationMs(40f)
        assertEquals("no effect should be created while probing", 1, factory.createdEffects.size)
        assertEquals(40f, controller.frameDurationMsForTest, 0f)

        scheduler.runNext()
        val probe = factory.createdEffects[1]
        assertEquals(40f, factory.createdConfigs[1].frameDurationMs, 0f)
        assertEquals(listOf(true), probe.setEnabledCalls)
        assertEquals(LimiterState(true, true), controller.state)
        assertNoDisable(factory)
    }

    @Test
    fun recreateFailureSchedulesProbe() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val controller = LimiterController(42, 4f, factory, scheduler) {}

        factory.effects += FakeEffect(initialHasControl = true)
        controller.start(clipPrevention = true)
        assertEquals(LimiterState(true, true), controller.state)

        factory.throwOnCreate = true
        controller.setFrameDurationMs(40f)
        assertEquals(LimiterState(false, false), controller.state)
        assertEquals(1, scheduler.pending().size)

        factory.throwOnCreate = false
        factory.effects += FakeEffect(initialHasControl = true)
        scheduler.runNext()
        val probe = factory.createdEffects[1]
        assertEquals(40f, factory.createdConfigs[1].frameDurationMs, 0f)
        assertEquals(listOf(true), probe.setEnabledCalls)
        assertEquals(LimiterState(true, true), controller.state)
        assertNoDisable(factory)
    }

    @Test
    fun controlLostThenDurationChange() {
        val factory = FakeFactory()
        val scheduler = FakeScheduler()
        val states = mutableListOf<LimiterState>()
        val controller = LimiterController(42, 4f, factory, scheduler) { states += it }

        factory.effects += FakeEffect(initialHasControl = true, initialEnabled = true)
        factory.effects += FakeEffect(initialHasControl = true)
        controller.start(clipPrevention = true)
        val first = factory.createdEffects[0]

        first.hasControlValue = false
        first.listener!!.onControlStatusChange(false)
        assertEquals(LimiterState(true, false), controller.state)
        assertFalse("effect waiting for regain must not be released", first.released)

        controller.setFrameDurationMs(40f)

        assertTrue("effect should be released and re-created", first.released)
        assertEquals(2, factory.createdEffects.size)
        assertEquals(40f, factory.createdConfigs[1].frameDurationMs, 0f)
        assertEquals(LimiterState(true, true), controller.state)
        assertEquals(listOf(true), factory.createdEffects[1].setEnabledCalls)
        assertEquals("control state should be published", LimiterState(true, true), states.last())
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
