package me.misa198.airmedy.player.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.misa198.airmedy.player.EqualizerSettings
import me.misa198.airmedy.player.NormalizationSettings
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.engine.EngineKind
import me.misa198.airmedy.player.engine.ItemGain
import me.misa198.airmedy.player.engine.PlayerEngine

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
    override val kind: EngineKind = EngineKind.Native
    override val events: Flow<EngineEvent> = emptyFlow()

    data class PrepareCall(
        val item: PlaybackItem,
        val gain: ItemGain,
        val startPositionMs: Long,
        val startPaused: Boolean,
    )

    var position: Long = 0L
    var duration: Long = DefaultDurationMs
    var crossfading: Boolean = false
    var beginCrossfadeResult: Boolean = true
    var failPrepare: Throwable? = null
    var failPreload: Throwable? = null

    /**
     * Opt-in: when a successful [beginCrossfade] should mimic the native engine by
     * promoting the preloaded item and reporting [EngineEvent.TransitionStarted] on the
     * next [pollEvents]. The preloaded slot is retired (hasPreloaded becomes false) until
     * the coordinator preloads again, matching the native slot becoming the outgoing source.
     */
    var emitTransitionOnCrossfade: Boolean = false

    /** Invoked inside [prepare] before any state changes, so tests can observe the coordinator mid-prepare. */
    var onPrepare: (() -> Unit)? = null

    var playing: Boolean = false
    var preparedItem: PlaybackItem? = null
    var preloadedItem: PlaybackItem? = null
    val prepareCalls = mutableListOf<PrepareCall>()
    val calls = mutableListOf<String>()
    val seeks = mutableListOf<Long>()
    val crossfades = mutableListOf<Long>()
    val focusGains = mutableListOf<Float>()
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
        onPrepare?.invoke()
        failPrepare?.let { throw it }
        preparedItem = item
        duration = durationFor(item)
        position = startPositionMs
        playing = !startPaused
        prepareCalls += PrepareCall(item, gain, startPositionMs, startPaused)
    }

    override suspend fun preloadNext(item: PlaybackItem, gain: ItemGain) {
        calls += "preloadNext"
        failPreload?.let { throw it }
        preloadedItem = item
    }

    override fun clearPreloaded() {
        calls += "clearPreloaded"
        preloadedItem = null
    }

    override fun hasPreloaded(): Boolean = preloadedItem != null

    override fun play() {
        calls += "play"
        playing = true
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

    override fun positionMs(): Long = position

    override fun durationMs(): Long = duration

    override fun beginCrossfade(durationMs: Long): Boolean {
        calls += "beginCrossfade"
        crossfades += durationMs
        if (!beginCrossfadeResult || !hasPreloaded() || crossfading) return false
        crossfading = true
        if (emitTransitionOnCrossfade) {
            val incoming = preloadedItem
            preloadedItem = null
            if (incoming != null) pendingEvents += EngineEvent.TransitionStarted(incoming, durationMs)
        }
        return true
    }

    override fun isCrossfading(): Boolean = crossfading

    override fun snapCrossfade() {
        calls += "snapCrossfade"
        crossfading = false
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
    }

    override fun setNormalization(settings: NormalizationSettings) {
        calls += "setNormalization"
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

    /** Applied to every created engine before it is returned. */
    var onPrepare: (() -> Unit)? = null

    /** Applied to every created engine before it is returned. */
    var emitTransitionOnCrossfade: Boolean = false

    val current: FakePlayerEngine get() = created.last()

    override fun invoke(): PlayerEngine = FakePlayerEngine(durationFor).also { engine ->
        engine.onPrepare = onPrepare
        engine.emitTransitionOnCrossfade = emitTransitionOnCrossfade
        created += engine
    }
}
