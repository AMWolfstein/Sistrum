# Contract — Gain source and gain math (ADR-005)

Revised 2026-10-08 (owner decisions 2026-10-07, M6-2): measured loudness is the first source, tags the fallback.

```kotlin
enum class GainForm { Measured, ReplayGain, R128, SoundCheck, None }

data class GainInfo(
    val trackGainDb: Float?, val albumGainDb: Float?,   // against −18 LUFS. Measured: −18 − L (L = integrated LUFS).
                                                        // Tags: REPLAYGAIN_REFERENCE_LOUDNESS applied
                                                        // (+ (−18 − ref)) when present; R128 converted +5 dB
    val trackPeak: Float?, val albumPeak: Float?,       // linear, 1.0 = full scale. Measured: 10^(dBTP/20) (true peak);
                                                        // tags: the tagged peak
    val form: GainForm,
)

fun interface GainSource { fun gainFor(item: PlaybackItem, format: androidx.media3.common.Format?): GainInfo? }

// MeasuredGainSource: PlaybackItem.analysis (existing read side, read only) + the album-loudness snapshot
//   (contracts/loudness-analysis.md). Returns null when the track has no measured loudness.
// TagGainSource: Rhythm ReplayGainUtil port. Returns null when no usable tag.
// GainResolver: the only place the order lives:
//   MeasuredGainSource → TagGainSource → GainInfo(form = None)

fun itemGainDb(info: GainInfo, settings: NormalizationSettings): Float   // pure, unit-tested
```

Resolution rules:
- The source is chosen **per track as a whole**. A track with measured loudness uses only measured values: the album
  value if its album record is complete, else its own track value. It never mixes a tag album gain with measured
  track loudness.
- A track analyzed as silent, too short (< 400 ms) or failed has no measured loudness → tags → `None`.
- A track not analyzed yet → tags → `None`.
- A track whose analysis finishes while it plays keeps its gain until its next start (no mid-track source change).

`itemGainDb` rules: disabled → 0; base = album value (album mode, if present) else track value, else 0 + untagged
pre-amp (`None` only); + global pre-amp (target − (−18)), so a measured track lands on target − L; clip prevention
(only when `preventClip` is on) caps so peak × 10^(gain/20) ≤ 1 (album peak in album mode); NaN/±Inf inputs →
treated as absent; result clamped to −30…+20 dB.

"Prevent clipping" off: no peak cap here, and the engine sets the session limiter stage to neutral parameters
(FR-053; the effect itself stays enabled).
