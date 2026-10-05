# Contract — `PlayerEngine` (internal seam, ADR-001)

Kotlin shape; names may be refined in the task, semantics may not. All calls come from the service's single
command consumer (FR-081); implementations are not required to be thread-safe beyond that.

```kotlin
interface PlayerEngine : Closeable {
    val kind: EngineKind                       // Native | Media3
    val events: Flow<EngineEvent>

    suspend fun prepare(item: PlaybackItem, gain: ItemGain, startPositionMs: Long, startPaused: Boolean)
    suspend fun preloadNext(item: PlaybackItem, gain: ItemGain)   // gapless candidate; Media3 may defer B (ADR-003)
    fun clearPreloaded()
    fun hasPreloaded(): Boolean

    fun play(); fun pause(); fun seekTo(positionMs: Long)
    fun positionMs(): Long; fun durationMs(): Long

    fun beginCrossfade(durationMs: Long): Boolean  // false = nothing preloaded or fade running (no-op)
    fun isCrossfading(): Boolean
    fun snapCrossfade()                            // outgoing stops now; incoming continues at full level

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
    data class Error(val provider: String, val format: String, val cause: Throwable) : EngineEvent
}
```

Rules:
- Exactly one `TransitionStarted` or `GaplessAdvanced` per automatic advance, in order (FR-089).
- `prepare` failure throws after releasing anything it created (FR-086); "playing" only after output started
  (FR-090).
- `LegacyNativeEngine`: `ItemGain` → the existing native dB parameter; `setDsp` → `GlobalDspConfig`;
  events from polls. No behaviour change.
- `Media3Engine`: ignores `ItemGain`; resolves each item's gain itself when the track's `Format` is known
  (`GainSource` → `itemGainDb`, contracts/gain-source.md) and applies it in `GainProcessor`; `setNormalization`
  retargets the current and prepared players with ramps; `setDsp` → per-player processors; limiter on the session.
  (Amended 2026-10-05 at `/speckit-tasks`: the service cannot read tags before the engine parses the file.)
