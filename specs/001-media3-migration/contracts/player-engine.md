# Contract — `PlayerEngine` (internal seam, ADR-001)

Kotlin shape; names may be refined in the task, semantics may not. All calls come from the service's single
command consumer (FR-081); implementations are not required to be thread-safe beyond that.

```kotlin
interface PlayerEngine : Closeable {
    val kind: EngineKind                       // Native | Media3
    val events: Flow<EngineEvent>
    fun pollEvents(): List<EngineEvent>        // synchronous drain on the command consumer (refined at T011)

    suspend fun prepare(item: PlaybackItem, gain: ItemGain, startPositionMs: Long, startPaused: Boolean)
    suspend fun preloadNext(item: PlaybackItem, gain: ItemGain)   // gapless candidate; Media3 may defer B (ADR-003)
    fun clearPreloaded()
    fun hasPreloaded(): Boolean

    fun play(); fun pause(); fun seekTo(positionMs: Long)
    fun positionMs(): Long; fun durationMs(): Long

    fun beginCrossfade(durationMs: Long): Boolean  // false = nothing preloaded or fade running (no-op)
    fun isCrossfading(): Boolean
    fun snapCrossfade()                            // outgoing stops now; incoming continues at full level
    fun setCrossfade(durationMs: Long, repeatOneCrossfade: Boolean, crossfadeOnSkip: Boolean = false)
                                                   // added T059 (2026-10-13), crossfadeOnSkip T060b: Media3 schedules B
                                                   // from it (ADR-003, FR-033, FR-031a); native: no-op; never alters a running fade
    suspend fun crossfadeTo(item: PlaybackItem, durationMs: Long): Boolean
                                                   // added T060b (FR-031a crossfade on skip): Media3 prepares B with [item]
                                                   // on demand and starts a [durationMs] fade into it once B is ready and
                                                   // session-verified, waiting at most 300 ms for READY (plus, on the first
                                                   // skip without a learned incoming start latency, at most 250 ms of
                                                   // silent warm-up, T060c); returns true when the fade
                                                   // started (B is then current; no TransitionStarted is emitted: the caller
                                                   // already moved the queue and publishes the switch itself); false = nothing
                                                   // changed (not ready in time, B failed, setting off) and the caller hard-cuts.
                                                   // Called while a fade runs: snaps it and returns false (hard cut).
                                                   // Native: always false.

    fun setFocusGain(gain: Float)                  // 1.0 or 0.2, ramped inside the engine
    fun setDsp(settings: EqualizerSettings)        // 10 bands, preamp, width
    fun setGains(current: ItemGain, preloaded: ItemGain?)   // native only: service-side analysis gain
    fun setNormalization(settings: NormalizationSettings)  // Media3: retargets both players, ramped (FR-046a)
}

sealed interface EngineEvent {
    data class TransitionStarted(val incoming: PlaybackItem, val fadeMs: Long) : EngineEvent   // at fade start
    data class GaplessAdvanced(val incoming: PlaybackItem) : EngineEvent
    data object Ended : EngineEvent                                  // current item ended, nothing preloaded
    data object OutputDisconnected : EngineEvent
    data object OutputStarted : EngineEvent                          // audio actually started (FR-090), refined at T018
    data class Error(val provider: String, val format: String, val cause: Throwable) : EngineEvent
}
```

Rules:
- `pollEvents()` (refinement 2026-10-05, T011; ADR-001 "events on the service ticker"): the service's single
  command consumer drains buffered events synchronously, in order, each exactly once, so transitions are
  handled inside the command lock as today. An implementation never delivers the same event through both
  `pollEvents()` and `events`. `LegacyNativeEngine`: everything via `pollEvents()`, `events` empty. `Media3Engine`
  (decided 2026-10-06, T020): also everything via `pollEvents()`, `events` empty — player listeners run on the
  engine's playback looper and append to a thread-safe buffer that `pollEvents()` drains; level checks that need the
  player (e.g. output started) are evaluated inside `pollEvents()` on the playback looper.
- Threading (`Media3Engine`, T020): every ExoPlayer is built on one process-wide playback `HandlerThread` looper
  (`Media3PlayerFactory`); seam calls arrive on the command consumer's thread and are marshalled onto that looper
  synchronously (bounded wait), so the seam stays synchronous as for the native engine.
- `OutputStarted` (refinement 2026-10-06, T018; FR-090 and the "playing only after output started" rule below): emitted
  once per start of output (after an unpaused `prepare` or a `play()`), through `pollEvents()`. The coordinator reports
  `Playing` only after it. `LegacyNativeEngine` has no native signal and emits it right after a successful unpaused
  prepare / `play()` (T027, no native change); Media3 emits it on the first advance of the rendered position (T020).
- `Ended` and `OutputDisconnected` are edge events (once per occurrence, re-armed by `prepare`); the service keeps
  its own pending flags where it needs today's level checks.
- Exactly one `TransitionStarted` or `GaplessAdvanced` per automatic advance, in order (FR-089).
- `prepare` failure throws after releasing anything it created (FR-086); "playing" only after output started
  (FR-090).
- `LegacyNativeEngine`: `ItemGain` → the existing native dB parameter; `setDsp` → `GlobalDspConfig`;
  events from polls. No behaviour change.
- `Media3Engine`: ignores `ItemGain`; resolves each item's gain itself when the track's `Format` is known
  (`GainSource` → `itemGainDb`, contracts/gain-source.md) and applies it in `GainProcessor`; `setNormalization`
  retargets the current and prepared players with ramps; `setDsp` → per-player processors; limiter on the session.
  (Amended 2026-10-05 at `/speckit-tasks`: the service cannot read tags before the engine parses the file.)
