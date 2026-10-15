package com.amwolfstein.sistrum.player.media3

import com.amwolfstein.sistrum.analysis.SilenceProfile
import com.amwolfstein.sistrum.player.PlaybackItem
import com.amwolfstein.sistrum.player.media3.TransitionCommand.*
import java.lang.reflect.InvocationTargetException
import org.junit.Assert.*
import org.junit.Test

/**
 * T017 / FR-020..021 / D-37. T018 must pass these unchanged.
 * Reflection makes absent additive hooks assertion failures, without production stubs or a legacy fallback.
 * Timing inputs are already on the processed timeline (position + engine fade lead), as in 001.
 * These are controller policy/command tests; engine seek execution and captured PCM remain device checks.
 */
class TransitionControllerSilenceTest {
    private val a = PlaybackItem("a", "A", "artist", "/a")
    private val b = PlaybackItem("b", "B", "artist", "/b")

    @Test
    fun skipSilenceDefaultsOn() {
        assertEquals(true, property(TransitionSettings(6_000L), "getCrossfadeSkipSilence"))
    }

    @Test
    fun outgoingTailMovesPreparationAndFadeEarlierByExactlyThreeSeconds() {
        val outgoing = profiled(a, leadMs = 1_000L, tailMs = 3_000L)
        val incoming = profiled(b, leadMs = 0L, tailMs = 5_000L)
        val controller = TransitionController(TransitionSettings(6_000L))
        // 180000 - 3000 - 6000 = 171000; prebuffer must also advance from 11000 to 14000 remaining.
        assertCommands(controller.onPosition(outgoing, 14_001L, incoming, false))
        assertEquals(TransitionPhase.Scheduled, controller.phase)
        assertNull(controller.incomingItem)
        assertPrepared(controller.onPosition(outgoing, 14_000L, incoming, false), incoming)
        assertEquals(TransitionPhase.Preparing, controller.phase)
        assertNull(timing(controller, outgoing, 170_999L, 180_000L))
        assertEquals(6_000L, timing(controller, outgoing, 171_000L, 180_000L))
        assertCommands(controller.onIncomingReady())
        assertFade(controller.beginFade(6_000L), 6_000L, 0L)
        assertEquals(6_000L, controller.runningFadeMs)
    }

    @Test
    fun incomingStartsAtItsLeadAndKeepsThe001DurationAndHandover() {
        val incoming = profiled(b, leadMs = 2_000L, tailMs = 4_000L)
        val controller = TransitionController(TransitionSettings(6_000L))
        assertPrepared(controller.onPosition(a, 11_000L, incoming, false), incoming)
        assertEquals(incoming, controller.incomingItem)
        assertCommands(controller.onIncomingReady())
        assertFade(controller.beginFade(6_000L), 6_000L, 2_000L)
        assertEquals(TransitionPhase.Transitioning, controller.phase)
        assertEquals(6_000L, controller.runningFadeMs)
        assertCommands(controller.onFadeComplete(), ReleaseOutgoing)
        assertIdle(controller)
        assertCommands(controller.onFadeComplete())
    }

    @Test
    fun incomingLeadAloneDoesNotAdvanceTheOutgoingTrigger() {
        val incoming = profiled(b, leadMs = 2_000L, tailMs = 9_000L)
        val controller = TransitionController(TransitionSettings(6_000L))
        assertCommands(controller.onPosition(a, 11_001L, incoming, false))
        assertPrepared(controller.onPosition(a, 11_000L, incoming, false), incoming)
        assertNull(timing(controller, a, 173_999L, 180_000L))
        assertEquals(6_000L, timing(controller, a, 174_000L, 180_000L))
        assertCommands(controller.onIncomingReady())
        assertFade(controller.beginFade(6_000L), 6_000L, 2_000L)
    }

    @Test
    fun outgoingTailWorksWhenIncomingHasNoProfile() {
        val outgoing = profiled(a, leadMs = 0L, tailMs = 3_000L)
        val controller = TransitionController(TransitionSettings(6_000L))
        assertPrepared(controller.onPosition(outgoing, 14_000L, b, false), b)
        assertNull(timing(controller, outgoing, 170_999L, 180_000L))
        assertEquals(6_000L, timing(controller, outgoing, 171_000L, 180_000L))
    }

    @Test
    fun absentProfilesKeepExact001TimingWithoutWaitingForAnalysis() {
        val controller = TransitionController(TransitionSettings(6_000L))
        assertLegacyTiming(controller, a)
        assertCommands(controller.onPosition(a, 11_001L, b, false))
        assertPrepared(controller.onPosition(a, 11_000L, b, false), b)
        // No asynchronous profile or analysis dependency is needed to begin the legacy fade.
        assertCommands(controller.onIncomingReady())
        assertFade(controller.beginFade(6_000L), 6_000L, 0L)
    }

    @Test
    fun measuredZeroEdgesKeepExact001Timing() {
        val outgoing = profiled(a, leadMs = 0L, tailMs = 0L)
        val incoming = profiled(b, leadMs = 0L, tailMs = 0L)
        val controller = TransitionController(TransitionSettings(6_000L))
        assertLegacyTiming(controller, outgoing)
        assertCommands(controller.onPosition(outgoing, 11_001L, incoming, false))
        assertPrepared(controller.onPosition(outgoing, 11_000L, incoming, false), incoming)
        assertCommands(controller.onIncomingReady())
        assertFade(controller.beginFade(6_000L), 6_000L, 0L)
    }

    @Test
    fun toggleOffIgnoresBothProfilesAndKeepsExact001Timing() {
        val outgoing = profiled(a, leadMs = 1_000L, tailMs = 3_000L)
        val incoming = profiled(b, leadMs = 2_000L, tailMs = 4_000L)
        val controller = TransitionController(settings(skipSilence = false))
        assertLegacyTiming(controller, outgoing)
        assertCommands(controller.onPosition(outgoing, 14_000L, incoming, false))
        assertEquals(TransitionPhase.Scheduled, controller.phase)
        assertCommands(controller.onPosition(outgoing, 11_001L, incoming, false))
        assertPrepared(controller.onPosition(outgoing, 11_000L, incoming, false), incoming)
        assertCommands(controller.onIncomingReady())
        assertFade(controller.beginFade(6_000L), 6_000L, 0L)
    }

    @Test
    fun trackShorterThanFadePlusSilenceKeepsThe001HalfTrackCap() {
        val outgoing = profiled(a, leadMs = 0L, tailMs = 3_000L)
        val controller = TransitionController(TransitionSettings(6_000L))
        // 8 s total, 3 s tail: retain the 4 s half-track cap, start at 1 s, end at audible 5 s.
        assertNull(timing(controller, outgoing, 999L, 8_000L))
        assertEquals(4_000L, timing(controller, outgoing, 1_000L, 8_000L))
        assertEquals(401L, timing(controller, outgoing, 4_599L, 8_000L))
        assertNull(timing(controller, outgoing, 4_600L, 8_000L))
        assertNull(timing(controller, outgoing, 5_000L, 8_000L))
        assertNull(timing(controller, outgoing, 8_000L, 8_000L))
    }

    @Test
    fun audibleRemainderShorterThanFadeClampsAtTrackStart() {
        val outgoing = profiled(a, leadMs = 0L, tailMs = 4_000L)
        val controller = TransitionController(TransitionSettings(6_000L))
        // A 5 s track with only 1 s before its tail cannot supply a 2.5 s fade or a negative start.
        assertEquals(1_000L, timing(controller, outgoing, 0L, 5_000L))
        assertEquals(401L, timing(controller, outgoing, 599L, 5_000L))
        assertNull(timing(controller, outgoing, 600L, 5_000L))
        assertNull(timing(controller, outgoing, 1_000L, 5_000L))
    }

    @Test
    fun entirelySilentOutgoingTrackNeverStartsAnEmptyOrNegativeFade() {
        val outgoing = profiled(a, leadMs = 5_000L, tailMs = 5_000L)
        val controller = TransitionController(TransitionSettings(6_000L))
        for (position in listOf(0L, 1L, 2_500L, 4_999L, 5_000L, 5_001L)) {
            assertNull("silent outgoing position $position", timing(controller, outgoing, position, 5_000L))
        }
    }

    @Test
    fun tracksBelowTwoSecondsKeepThe001AutomaticFadeExclusion() {
        val outgoing = profiled(a, leadMs = 0L, tailMs = 1_000L)
        val controller = TransitionController(TransitionSettings(6_000L))
        for (position in listOf(0L, 1L, 499L, 500L, 899L, 1_000L, 1_899L, 1_900L)) {
            assertNull("short track position $position", timing(controller, outgoing, position, 1_900L))
        }
    }

    @Test
    fun lateTickUsesProcessedAudibleRemainderAndRetains401MillisecondBoundary() {
        val outgoing = profiled(a, leadMs = 1_000L, tailMs = 3_000L)
        val controller = TransitionController(TransitionSettings(6_000L))
        // Player 171650 + processing lead 600 = 172250; audible end 177000 leaves 4750 ms.
        assertEquals(4_750L, timing(controller, outgoing, 171_650L + 600L, 180_000L))
        assertEquals(401L, timing(controller, outgoing, 176_599L, 180_000L))
        assertNull(timing(controller, outgoing, 176_600L, 180_000L))
        assertNull(timing(controller, outgoing, 177_000L, 180_000L))
        assertNull(timing(controller, outgoing, 180_001L, 180_000L))
    }

    @Test
    fun manualHardCutStillStartsAtZeroWithProfiles() {
        val incoming = profiled(b, leadMs = 2_000L, tailMs = 4_000L)
        val controller = TransitionController(TransitionSettings(6_000L))
        assertCommands(controller.onManualSkip(incoming), HardCut(incoming, 0L))
        assertIdle(controller)
    }

    @Test
    fun manualFadeKeepsIts001ClampAndStartsAtZeroWithProfiles() {
        val incoming = profiled(b, leadMs = 2_000L, tailMs = 4_000L)
        for ((configured, expected) in listOf(300L to 500L, 1_200L to 1_200L, 6_000L to 2_000L)) {
            val controller = TransitionController(TransitionSettings(configured, crossfadeOnManualSkip = true))
            assertPrepared(controller.onManualSkip(incoming), incoming)
            assertFade(controller.onIncomingReady(), expected, 0L)
            assertEquals(expected, controller.runningFadeMs)
            assertCommands(controller.onFadeComplete(), ReleaseOutgoing)
            assertIdle(controller)
        }
    }

    @Test
    fun manualFadeReusingAutomaticIncomingStillRestartsAtZero() {
        val outgoing = profiled(a, leadMs = 0L, tailMs = 3_000L)
        val incoming = profiled(b, leadMs = 2_000L, tailMs = 0L)
        val controller = TransitionController(TransitionSettings(6_000L, crossfadeOnManualSkip = true))
        assertPrepared(controller.onPosition(outgoing, 14_000L, incoming, false), incoming)
        assertCommands(controller.onIncomingReady())
        assertFade(controller.onManualSkip(incoming), 2_000L, 0L)
        assertEquals(TransitionPhase.Transitioning, controller.phase)
        assertEquals(2_000L, controller.runningFadeMs)
    }

    @Test
    fun repeatOneEnabledKeeps001TimingAndStartsFromZeroWithProfiles() {
        val item = profiled(a, leadMs = 2_000L, tailMs = 3_000L)
        val controller = TransitionController(TransitionSettings(6_000L))
        assertLegacyTiming(controller, item, repeatOne = true)
        assertCommands(controller.onPosition(item, 14_000L, item, true))
        assertCommands(controller.onPosition(item, 11_001L, item, true))
        assertPrepared(controller.onPosition(item, 11_000L, item, true), item)
        assertCommands(controller.onIncomingReady())
        assertFade(controller.beginFade(6_000L), 6_000L, 0L)
    }

    @Test
    fun repeatOneDisabledKeepsGaplessPolicyWithProfiles() {
        val item = profiled(a, leadMs = 2_000L, tailMs = 3_000L)
        val controller = TransitionController(TransitionSettings(6_000L, repeatOneCrossfade = false))
        for (remaining in listOf(60_000L, 14_000L, 11_000L, 9_000L, 6_000L, 0L)) {
            assertCommands(controller.onPosition(item, remaining, item, true))
            assertIdle(controller)
            assertNull(timing(controller, item, 180_000L - remaining, 180_000L, repeatOne = true))
        }
    }

    @Test
    fun crossfadeOffNeverPreparesOrAppliesSilenceOffsets() {
        val outgoing = profiled(a, leadMs = 1_000L, tailMs = 3_000L)
        val incoming = profiled(b, leadMs = 2_000L, tailMs = 4_000L)
        val controller = TransitionController(TransitionSettings(0L))
        for (remaining in listOf(60_000L, 14_000L, 11_000L, 8_000L, 3_000L, 0L)) {
            assertCommands(controller.onPosition(outgoing, remaining, incoming, false))
            assertIdle(controller)
            assertNull(timing(controller, outgoing, 180_000L - remaining, 180_000L))
        }
        assertCommands(controller.onManualSkip(incoming), HardCut(incoming, 0L))
    }

    @Test
    fun unreadyIncomingRetainsThe001GaplessFallbackCommandOrder() {
        val outgoing = profiled(a, leadMs = 0L, tailMs = 3_000L)
        val incoming = profiled(b, leadMs = 2_000L, tailMs = 0L)
        val controller = TransitionController(TransitionSettings(6_000L))
        assertPrepared(controller.onPosition(outgoing, 14_000L, incoming, false), incoming)
        assertCommands(controller.beginFade(6_000L), ReleaseIncoming, AppendGapless(incoming))
        assertIdle(controller)
    }

    @Test
    fun legacyNoProfileCommandsRemainUnchanged() {
        // Runs today: a positive legacy control independent of every new reflection hook.
        val controller = TransitionController(TransitionSettings(6_000L))
        assertCommands(controller.onPosition(a, 11_001L, b, false))
        assertCommands(controller.onPosition(a, 11_000L, b, false), PrepareIncoming(b))
        assertCommands(controller.onIncomingReady())
        assertCommands(controller.beginFade(6_000L), StartFade(6_000L))
        assertCommands(controller.onFadeComplete(), ReleaseOutgoing)
        assertIdle(controller)
    }

    private fun assertLegacyTiming(controller: TransitionController, item: PlaybackItem, repeatOne: Boolean = false) {
        // Explicit 001 oracle: duration >= 2000; min(configured, duration/2); remaining in 401..cap.
        for ((duration, position, expected) in listOf(
            Triple(0L, 0L, null), Triple(1_900L, 0L, null),
            Triple(2_000L, 999L, null), Triple(2_000L, 1_000L, 1_000L),
            Triple(2_000L, 1_599L, 401L), Triple(2_000L, 1_600L, null),
            Triple(8_000L, 3_999L, null), Triple(8_000L, 4_000L, 4_000L),
            Triple(180_000L, 173_999L, null), Triple(180_000L, 174_000L, 6_000L),
            Triple(180_000L, 176_000L, 4_000L), Triple(180_000L, 179_599L, 401L),
            Triple(180_000L, 179_600L, null), Triple(180_000L, 180_000L, null),
        )) {
            assertEquals("001 duration=$duration position=$position repeat=$repeatOne", expected,
                timing(controller, item, position, duration, repeatOne))
        }
    }

    private fun profiled(item: PlaybackItem, leadMs: Long, tailMs: Long): PlaybackItem {
        val constructor = PlaybackItem::class.java.declaredConstructors.singleOrNull {
            it.parameterCount == 11 && it.parameterTypes.last() == SilenceProfile::class.java
        }
        assertNotNull("T018: append nullable silence: SilenceProfile? = null to PlaybackItem", constructor)
        return invoke {
            constructor!!.newInstance(item.trackId, item.title, item.artist, item.audioPath, item.artworkPath,
                item.albumId, item.analysis, item.album, item.albumArtist, item.trackNumber,
                SilenceProfile(leadMs, tailMs)) as PlaybackItem
        }
    }

    private fun settings(skipSilence: Boolean): TransitionSettings {
        val constructor = TransitionSettings::class.java.declaredConstructors.singleOrNull {
            it.parameterTypes.contentEquals(arrayOf(Long::class.javaPrimitiveType, Long::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType))
        }
        assertNotNull("T018: append crossfadeSkipSilence: Boolean = true to TransitionSettings", constructor)
        return invoke { constructor!!.newInstance(6_000L, 5_000L, true, false, skipSilence) as TransitionSettings }
    }

    private fun timing(
        controller: TransitionController,
        current: PlaybackItem,
        positionMs: Long,
        durationMs: Long,
        repeatOne: Boolean = false,
    ): Long? {
        val method = TransitionController::class.java.declaredMethods.singleOrNull {
            it.name == "automaticFadeDurationMs" && it.parameterTypes.contentEquals(arrayOf(
                PlaybackItem::class.java, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType))
        }
        assertNotNull("T018: automaticFadeDurationMs(current, processedPositionMs, durationMs, repeatOne): Long?", method)
        return invoke { method!!.invoke(controller, current, positionMs, durationMs, repeatOne) as Long? }
    }

    private fun assertPrepared(commands: List<TransitionCommand>, item: PlaybackItem) {
        assertCommands(commands, PrepareIncoming(item))
    }

    private fun assertFade(commands: List<TransitionCommand>, durationMs: Long, positionMs: Long) {
        assertEquals("one start command", 1, commands.size)
        assertTrue("start fade", commands.single() is StartFade)
        val command = commands.single() as StartFade
        assertEquals(durationMs, command.durationMs)
        assertEquals(positionMs, property(command, "getPositionMs"))
    }

    private fun property(receiver: Any, getter: String): Any? {
        val method = receiver.javaClass.declaredMethods.singleOrNull { it.name == getter && it.parameterCount == 0 }
        assertNotNull("T018: ${receiver.javaClass.simpleName}.$getter", method)
        return invoke { method!!.invoke(receiver) }
    }

    private fun <T> invoke(block: () -> T): T = try {
        block()
    } catch (error: InvocationTargetException) {
        throw error.targetException
    }

    private fun assertCommands(actual: List<TransitionCommand>, vararg expected: TransitionCommand) {
        assertEquals(expected.toList(), actual)
    }

    private fun assertIdle(controller: TransitionController) {
        assertEquals(TransitionPhase.Idle, controller.phase)
        assertNull(controller.incomingItem)
        assertNull(controller.runningFadeMs)
    }
}
