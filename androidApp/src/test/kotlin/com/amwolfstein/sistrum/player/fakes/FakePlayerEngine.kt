package com.amwolfstein.sistrum.player.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.amwolfstein.sistrum.player.EqualizerSettings
import com.amwolfstein.sistrum.player.NormalizationSettings
import com.amwolfstein.sistrum.player.PlaybackItem
import com.amwolfstein.sistrum.player.engine.EngineEvent
import com.amwolfstein.sistrum.player.engine.EngineKind
import com.amwolfstein.sistrum.player.engine.ItemGain
import com.amwolfstein.sistrum.player.engine.PlayerEngine

/**
 * Engine-neutral fake [PlayerEngine] for host characterization tests.
 *
 * Records every call so tests can assert on the coordinator's engine effects.
 * State-changing members are plain `var`s the test drives directly. Events queued
 * with [emit] are returned exactly once by the next [pollEvents] call.
 */
internal class FakePlayerEngine(
    private val durationFor: (PlaybackItem) -> Long = { DefaultDurationMs },
) : PlayerEngine {
    override var kind: EngineKind = EngineKind.Native
    override val events: Flow<EngineEvent> = emptyFlow()

    data class PrepareCall(
        val item: PlaybackItem,
        val gain: ItemGain,
        val startPositionMs: Long,
        val startPaused: Boolean,
    )

    var position: Long = 0L
    var duration: Long = DefaultDurationMs
    var fadeLead: Long = 0L
    var crossfading: Boolean = false
    var beginCrossfadeResult: Boolean = true
    var crossfadeToResult: Boolean = true
    var failPrepare: Throwable? = null
    /** Selectively fails prepare for an item, after the existing [failPrepare] hook. */
    var failPrepareFor: (PlaybackItem) -> Throwable? = { null }
    var failPreload: Throwable? = null

    /**
     * When true (default), an unpaused [prepare] and every [play] queue
     * [EngineEvent.OutputStarted] for the next [pollEvents]. Tests of FR-090 set
     * this false so the fake no longer reports output started on its own.
     */
    var autoOutputStarted: Boolean = true

    /** When set, [positionMs] throws it (FR-084: a throwing position tick). */
    var failPosition: Throwable? = null

    /**
     * Opt-in: when a successful [beginCrossfade] should mimic the native engine by
     * promoting the preloaded item and reporting [EngineEvent.TransitionStarted] on the
     * next [pollEvents]. The preloaded slot is retired (hasPreloaded becomes false) until
     * the coordinator preloads again, matching the native slot becoming the outgoing source.
     */
    var emitTransitionOnCrossfade: Boolean = false

    /** Opt-in to an incoming player's position on promotion; native characterization keeps its original position. */
    var crossfadeIncomingPosition: Long? = null

    /** Invoked inside [prepare] before any state changes, so tests can observe the coordinator mid-prepare. */
    var onPrepare: (() -> Unit)? = null

    var playing: Boolean = false
    var preparedItem: PlaybackItem? = null
    var preloadedItem: PlaybackItem? = null
    val prepareCalls = mutableListOf<PrepareCall>()
    /** Every attempted item, including prepares that throw before completing. */
    val prepareAttempts = mutableListOf<PlaybackItem>()
    val calls = mutableListOf<String>()
    val seeks = mutableListOf<Long>()
    val crossfades = mutableListOf<Long>()
    val crossfadeSettingsCalls = mutableListOf<Pair<Long, Boolean>>()
    val crossfadeOnSkipCalls = mutableListOf<Boolean>()
    val crossfadeToCalls = mutableListOf<Pair<PlaybackItem, Long>>()
    val focusGains = mutableListOf<Float>()
    val normalizationCalls = mutableListOf<NormalizationSettings>()
    val gainCalls = mutableListOf<Pair<ItemGain, ItemGain?>>()
    val preloadCalls = mutableListOf<Pair<PlaybackItem, ItemGain>>()
    var closed: Boolean = false

    private val pendingEvents = mutableListOf<EngineEvent>()

    /** Queues [engineEvents] to be delivered once by the next [pollEvents]. */
    fun emit(vararg engineEvents: EngineEvent) {
        pendingEvents += engineEvents
    }

    override fun pollEvents(): List<EngineEvent> {
        if (pendingEvents.isEmpty()) return emptyList()
        val drained = pendingEvents.toList()
        pendingEvents.clear()
        return drained
    }

    override suspend fun prepare(item: PlaybackItem, gain: ItemGain, startPositionMs: Long, startPaused: Boolean) {
        calls += "prepare"
        prepareAttempts += item
        onPrepare?.invoke()
        failPrepare?.let { throw it }
        failPrepareFor(item)?.let { throw it }
        preparedItem = item
        duration = durationFor(item)
        position = startPositionMs
        playing = !startPaused
        prepareCalls += PrepareCall(item, gain, startPositionMs, startPaused)
        if (!startPaused && autoOutputStarted) pendingEvents += EngineEvent.OutputStarted
    }

    override suspend fun preloadNext(item: PlaybackItem, gain: ItemGain) {
        calls += "preloadNext"
        failPreload?.let { throw it }
        preloadedItem = item
        preloadCalls += item to gain
    }

    override fun clearPreloaded() {
        calls += "clearPreloaded"
        preloadedItem = null
    }

    override fun hasPreloaded(): Boolean = preloadedItem != null

    override fun play() {
        calls += "play"
        playing = true
        if (autoOutputStarted) pendingEvents += EngineEvent.OutputStarted
    }

    override fun pause() {
        calls += "pause"
        playing = false
    }

    override fun seekTo(positionMs: Long) {
        calls += "seekTo"
        seeks += positionMs
        position = positionMs
    }

    override fun positionMs(): Long {
        failPosition?.let { throw it }
        return position
    }

    override fun durationMs(): Long = duration

    override fun fadeLeadMs() = fadeLead

    override fun beginCrossfade(durationMs: Long): Boolean {
        calls += "beginCrossfade"
        crossfades += durationMs
        if (!beginCrossfadeResult || !hasPreloaded() || crossfading) return false
        crossfading = true
        if (emitTransitionOnCrossfade) {
            val incoming = preloadedItem
            preloadedItem = null
            crossfadeIncomingPosition?.let { incomingPosition ->
                position = incomingPosition
                if (incoming != null) {
                    preparedItem = incoming
                    duration = durationFor(incoming)
                }
            }
            if (incoming != null) pendingEvents += EngineEvent.TransitionStarted(incoming, durationMs)
        }
        return true
    }

    override fun isCrossfading(): Boolean = crossfading

    override fun snapCrossfade() {
        calls += "snapCrossfade"
        crossfading = false
    }

    override suspend fun crossfadeTo(item: PlaybackItem, durationMs: Long): Boolean {
        crossfadeToCalls += item to durationMs
        if (!crossfadeToResult) return false
        preparedItem = item
        preloadedItem = null
        duration = durationFor(item)
        position = 0L
        crossfading = true
        return true
    }

    override fun setCrossfade(durationMs: Long, repeatOneCrossfade: Boolean, crossfadeOnSkip: Boolean) {
        crossfadeOnSkipCalls += crossfadeOnSkip
        // LegacyNativeEngine's setter is a no-op. Keep its effect log stable for T015,
        // while recording settings delivery for tests of the shared coordinator path.
        if (kind == EngineKind.Native) {
            crossfadeSettingsCalls += durationMs to repeatOneCrossfade
            return
        }
        calls += "setCrossfade"
        crossfadeSettingsCalls += durationMs to repeatOneCrossfade
    }

    override fun setFocusGain(gain: Float) {
        calls += "setFocusGain"
        focusGains += gain
    }

    override fun setDsp(settings: EqualizerSettings) {
        calls += "setDsp"
    }

    override fun setGains(current: ItemGain, preloaded: ItemGain?) {
        calls += "setGains"
        gainCalls += current to preloaded
    }

    override fun setNormalization(settings: NormalizationSettings) {
        calls += "setNormalization"
        normalizationCalls += settings
    }

    override fun close() {
        calls += "close"
        closed = true
        preloadedItem = null
        playing = false
        pendingEvents.clear()
    }

    companion object {
        const val DefaultDurationMs = 180_000L
    }
}

/** Creates a fresh [FakePlayerEngine] per call and keeps them in creation order. */
internal class FakeEngineFactory(
    private val durationFor: (PlaybackItem) -> Long = { FakePlayerEngine.DefaultDurationMs },
) : () -> PlayerEngine {
    val created = mutableListOf<FakePlayerEngine>()
    var kind: EngineKind = EngineKind.Native
    var fadeLead: Long = 0L

    /** Applied to every created engine before it is returned. */
    var onPrepare: (() -> Unit)? = null

    /** Applied to every created engine before it is returned. */
    var emitTransitionOnCrossfade: Boolean = false

    /** Applied on promotion to model a Media3 incoming player's position without changing native tests. */
    var crossfadeIncomingPosition: Long? = null

    /** Applied to every created engine before it is returned; used to force prepare failures. */
    var failPrepare: Throwable? = null

    /** Copied to every created engine; selectively fails only the chosen items. */
    var failPrepareFor: (PlaybackItem) -> Throwable? = { null }

    /** Applied to every created engine before it is returned; false disables auto OutputStarted. */
    var autoOutputStarted: Boolean = true

    /** Applied to every created engine before it is returned; makes [FakePlayerEngine.positionMs] throw. */
    var failPosition: Throwable? = null

    val current: FakePlayerEngine get() = created.last()

    override fun invoke(): PlayerEngine = FakePlayerEngine(durationFor).also { engine ->
        engine.kind = kind
        engine.fadeLead = fadeLead
        engine.onPrepare = onPrepare
        engine.emitTransitionOnCrossfade = emitTransitionOnCrossfade
        engine.crossfadeIncomingPosition = crossfadeIncomingPosition
        engine.failPrepare = failPrepare
        engine.failPrepareFor = failPrepareFor
        engine.autoOutputStarted = autoOutputStarted
        engine.failPosition = failPosition
        created += engine
    }
}
