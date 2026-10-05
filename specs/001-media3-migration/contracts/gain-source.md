# Contract — Gain source and gain math (ADR-005)

```kotlin
enum class TagForm { ReplayGain, R128, SoundCheck, None }

data class GainInfo(
    val trackGainDb: Float?, val albumGainDb: Float?,   // against −18 LUFS: REPLAYGAIN_REFERENCE_LOUDNESS applied
                                                        // (+ (−18 − ref)) when present; R128 converted +5 dB
    val trackPeak: Float?, val albumPeak: Float?,       // linear, 1.0 = full scale
    val form: TagForm,
)

fun interface GainSource { fun gainFor(item: PlaybackItem, format: androidx.media3.common.Format?): GainInfo? }

// 001: TagGainSource. Later: AnalysisGainSource. Resolution order lives in one place:
// tags → analysis → GainInfo(None) (unity + untagged pre-amp).

fun itemGainDb(info: GainInfo, settings: NormalizationSettings): Float   // pure, unit-tested
```

`itemGainDb` rules: disabled → 0; base = album (album mode, if present) else track, else 0 + untagged pre-amp;
+ global pre-amp (target − (−18)); clip prevention caps so peak × 10^(gain/20) ≤ 1 (album peak in album mode);
NaN/±Inf inputs → treated as absent; result clamped to a sane range (e.g. −30…+20 dB).
