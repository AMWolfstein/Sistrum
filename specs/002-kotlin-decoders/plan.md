# Implementation Plan: Kotlin decoders, tags, analysis and DSP in the app

**Branch**: `feature/media3-migration` | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/002-kotlin-decoders/spec.md`

## Summary

Wire the four imported pure-JVM modules into the Android app. Decoders reach Media3 through 001's Decoder Registry as
decoding extractors (Pattern A) plus one renderer for ALAC-in-MP4; the scan gains a pass for files the media index
misses and uses `:metadata` for containers the current reader doesn't cover; background analysis adds Mood features
(Mood Radio comes back by itself once tracks carry them) and per-track silence profiles; the float sink gains Night
mode; crossfade timing uses silence profiles; CUE sheets become library tracks. A phone gate measures it all.

Execution (owner 2026-10-14): Codex runs every task in autonomous mode (`AGENTS.md`), one fresh session per task,
through `.claude/skills/delegate-task/scripts/milestone-run.sh`. Per-task gate = quick (`verify.sh --quick`: own
module tests + compile); each milestone ends with ONE full-gate + phone-check task (full unit suites, lint,
`assembleDevQa`, the milestone's instrumented classes on `.qa`, bisect on failure). Benchmarks only in M6.

## Technical Context

**Language/Version**: Kotlin (JDK 21), Android minSdk 31 / target 36
**Primary Dependencies**: Media3 1.11.1 (ExoPlayer, extractor API), Room, WorkManager; new project deps
`:codecs`, `:metadata`, `:analysis`, `:dsp` (pure JVM)
**Storage**: Room `SyncDatabase` (version 14); analysis documents (JSON per track) for loudness/mood/silence
**Testing**: JUnit host tests per module, androidApp host tests (Robolectric where present), instrumented tests on
`com.amwolfstein.sistrum.dev.qa` only; oracle fixtures from Phase A
**Target Platform**: Android phones; measurement device CPH2307 (Android 15)
**Project Type**: mobile app + pure-JVM library modules
**Performance Goals**: spec SC-005/SC-006 (≥ 5× real time per decoder, 2× for WMA Voice/DSD256; CPU ≤ 1.5× FLAC; seek ≤ 300 ms)
**Constraints**: no NDK/Go in new code; allocation-free decode loops; 001 behaviour unchanged except silence-aware
crossfade; daily apps never touched
**Scale/Scope**: ~10 format families, 6 milestones, ~55 tasks

## Constitution Check

| Principle | Status |
|---|---|
| 1 Staged, not destructive | PASS — native engine untouched; Media3 gets providers additively |
| 2 Preserve contracts | PASS — PlaybackController API unchanged; Mood Radio uses existing `startMoodRadio` |
| 3 No feature loss | PASS — restores Mood Radio |
| 4 No online playback | PASS |
| 5 Crossfade approach | PASS — A/B players kept; only fade timing changes (owner-approved, FR-020) |
| 6 Format parity via registry | PASS — every decoder is a registry provider, platform first |
| 7 Evidence over assumption | PASS — oracle fixtures; MediaStore behaviour probed before choosing the scan path (R8) |
| 8 Native ends with 003 | PASS — no native removal here |
| 9 Honest verification | PASS — NOT RUN rules in AGENTS.md |
| 10 Built for all users | PASS — scan path not tied to one device; second device where available |
| 11 Kotlin end state | PASS — all new code Kotlin/JVM |

Room: CUE tracks need a schema change (R10) → migration + migration test in the same task (FR-014).

## Project Structure

### Documentation (this feature)

```text
specs/002-kotlin-decoders/
├── spec.md  plan.md  research.md  data-model.md  quickstart.md  tasks.md
├── contracts/  (kotlin-provider.md, analysis-document.md, sink-chain.md)
├── HANDOFF.md  DECISIONS.md  IMPORT-LOG.md  NOTES.md  OWNER-PLAN-2026-10-14.md
```

### Source Code (repository root)

```text
codecs/ metadata/ analysis/ dsp/             # imported pure-JVM modules (Phase A); changes only if a task needs an API
androidApp/src/main/kotlin/com/amwolfstein/sistrum/
├── player/decoders/            # registry + providers; new: kotlin/ (adapter, one provider per family)
├── player/media3/              # Media3PlayerFactory, FloatChainAudioSink (Night mode), TransitionController (silence)
├── analysis/                   # TrackAnalyzer/AnalysisMeasurer: mood + silence alongside loudness
├── sync/                       # MediaStoreLibraryScanner (+ extra-format pass), EmbeddedTagReader (+ :metadata), CUE
└── ui/ settings                # Night mode toggle, silence-aware crossfade toggle
androidApp/src/test/…  androidApp/src/androidTest/…   # host and .qa device tests
```

**Structure Decision**: app code stays in `androidApp`; the modules stay Android-free. Adapters live on the app side.

## Milestones

- **M1 Decoders in playback** (US1, FR-001…009): module deps + R8 rules; generic decoding-extractor adapter (tests
  first); WavPack as template + early speed check on host; then providers in batches (APE+Musepack, WMA family,
  DSD (DSF/DFF), ADPCM/G.711/AIFF-C); ALAC renderer fallback; multichannel/resample; offline decoder; phone check.
- **M2 DSP** (US4, FR-019…021): Night mode in the sink (tests first); silence profiles in analysis; fade timing
  (tests first, HIGH-RISK); settings + strings; phone check.
- **M3 Scan + tags** (US2, FR-010…015): MediaStore probe → scan-path decision; extra-format pass; `:metadata` tag
  path for new containers; gain tags; schema version bump; phone check.
- **M4 Mood Radio** (US3, FR-016…018): mood features in the analysis pass; percentiles; Mood Radio entry points
  visible again; phone check.
- **M4b Analysis decoders** — MOVED to spec 004 (owner 2026-10-15, D-32). Was (FR-030/031, owner 2026-10-14): decoder-only ports of WaxFlow Opus, AAC, Vorbis, MP3,
  FLAC into `:codecs`; analysis-only Pattern B renderers behind Media3's extractors; per-format phone benchmark
  decides Kotlin vs platform.
- **M5 CUE** (US5, FR-022…024): Room migration (tests first); cue scan; clipped playback + gapless; stats/scrobble;
  phone check.
- **M6 Performance gate** (US6, FR-025): harness extensions, measurements, report.

## Complexity Tracking

| Item | Why needed | Simpler alternative rejected because |
|---|---|---|
| Room migration for CUE tracks | one library row per cue entry with start/end | encoding cue ranges in the path breaks history keys and queries |
| Extra scan pass | media index skips some formats (NOTES) | MediaStore-only scan never sees those files |
