# ADR-005 — Tag-based normalization and the gain source

Status: Accepted (owner, 2026-10-05); **pending device spike S5** (Opus header gain) · Date: 2026-10-05 · Spec: US6, FR-040…047a, SC-004 ·
Research: D9

## Context

Normalization is broken for everyone (no analysis data). Stage 1 reads gain tags at playback time on the new
engine. Later, on-device analysis becomes a second source.

## Decision

- `GainSource` interface (contract: `contracts/gain-source.md`) → `GainInfo` (track/album gain and peak, tag
  form, found). 001 implements `TagGainSource` only. Resolution order fixed in one place for later:
  tags → analysis → unity + untagged pre-amp.
- `TagGainSource` = port of Rhythm's `ReplayGainUtil` (pin `ef16e7b`, GPL-3.0-or-later): TXXX (MP3), Vorbis
  comments (FLAC/Ogg), `R128_*` (Opus, Q7.8 / 256), iTunNORM and `com.apple.iTunes:replaygain_*` (M4A), plus the
  forms Rhythm also reads (RVA2/RVAD/RGAD). Keys case-insensitive. Precedence ReplayGain → R128 → Sound Check.
- Gain math (pure Kotlin, unit-tested): tag gain vs −18 LUFS, adjusted by `REPLAYGAIN_REFERENCE_LOUDNESS` when
  present (tag gain + (−18 − reference)); R128 + 5 dB; global pre-amp =
  target − (−18); untagged = 0 dB + untagged pre-amp; album mode = album gain if present else track gain;
  clip prevention = cap so tagged peak (album peak in album mode) × gain ≤ 1.0; non-finite → untagged.
- Applied in each player's `GainProcessor` (ADR-004) with ramps from the current actual gain (FR-046a).
  Rhythm's knee compressor is not ported; the session limiter does that job.
- Opus header output gain: verified on device (S5) that the platform decoder applies it exactly once; if it
  doesn't, `GainProcessor` adds it (FR-041).
- Settings: enabled only with the new engine selected (FR-045); `NormalizationPreferences` + untagged pre-amp.

## Reference loudness (owner decision, 2026-10-05)

`REPLAYGAIN_REFERENCE_LOUDNESS` is honoured when present, as Rhythm does (`ReplayGainUtil.kt:291-299`):
adjusted gain = tag gain + (−18 − reference). Without the tag, gains are taken against −18, and R128 against −23
with +5 dB. Constitution and spec FR-041 updated.

## Consequences

- The analysis feature later plugs in as a second `GainSource` with no processor change.
- `VolumeNormalization.kt` (sharedLogic) stays for the native engine's analysis-based path (unchanged).
