# Tasks: Kotlin decoders, tags, analysis and DSP in the app (002)

**Input**: spec.md, plan.md, research.md (R1–R12), data-model.md, contracts/, quickstart.md
**Executor**: Codex, autonomous mode (`AGENTS.md`), one fresh session per task via
`.claude/skills/delegate-task/scripts/milestone-run.sh`. App root below: `androidApp/src/main/kotlin/com/amwolfstein/sistrum/`
(= `app/`), host tests `androidApp/src/test/kotlin/com/amwolfstein/sistrum/` (= `test/`), device tests
`androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/` (= `androidTest/`).

**Rules for every task**
- Quick gate (`verify.sh --quick`) except the last task of each milestone, which runs the full gate
  (full unit suites, `--lint`, `assembleDevQa`) and the milestone's device classes on the `.qa` build only.
- `[TESTS-FIRST]`: write only the tests; gate with `--expect-fail`; the next task makes them pass without
  modifying them. `[HIGH-RISK]`: gpt-6.1-sol high effort (runner picks it).
- Device classes written in a milestone are only RUN in its last task (exception: T023 probe).
- Module APIs: read the module README (`codecs/`, `metadata/`, `analysis/`, `dsp/`) before using a module.
- Oracle corpora: `~/.cache/sistrum-waxflow-oracle`, `~/.cache/sistrum/oracle-corpus` (env `SISTRUM_CORPUS`,
  `WAXFLOW_CORPUS`, `WAXFLOW_ALAC_PACKETS`); a test whose corpus is absent fails with the rebuild command, it is
  never silently skipped.

## Phase 1: Setup

- [ ] T001 Add `implementation(project(":codecs"))`, `":metadata"`, `":analysis"`, `":dsp"` to `androidApp/build.gradle.kts` (research R1); add host test `test/ModuleWiringTest.kt` that touches one public type of each module; confirm `:androidApp:assembleProdRelease` (unsigned) still builds with R8 and record the result in HANDOFF

## Phase 2: M1 — Decoders in playback (US1, P1)

**Goal**: every `:codecs` format plays on Media3 through the registry (FR-001…009). **Independent test**: corpus
plays on `.qa`; offline decode = oracle hashes; joins ≤ 2 frames.

- [ ] T002 [TESTS-FIRST] [HIGH-RISK] [US1] Write `test/player/decoders/kotlin/KotlinDecodingExtractorTest.kt` for the generic Pattern A adapter (contracts/kotlin-provider.md) against a fake `CodecStream`: magic-byte `sniff`; `AUDIO_RAW` format with the stream's PCM encoding, rate and channels; `durationUs` from the exact sample count; sample-exact `SeekMap` seek; exact total frames emitted (gapless, no padding); typed refusal surfaced via `DecoderProvider.refusal`; no per-block allocation in steady state (allocated-bytes check over ≥ 100 blocks)
- [ ] T003 [HIGH-RISK] [US1] Implement `app/player/decoders/kotlin/CodecStream.kt` (adapter interface: info, decodeBlock, seekSample, refusal), `ExtractorRandomAccessSource.kt` (codecs `RandomAccessSource` over Media3 input/file) and `KotlinDecodingExtractor.kt`; make T002 pass without modifying it
- [ ] T004 [US1] WavPack template: `app/player/decoders/kotlin/KotlinWavPackProvider.kt` (CodecStream over `container/wv/Wv`), registered in `app/player/decoders/DecoderRegistry.kt` after `PlatformProvider`; host test `test/player/decoders/kotlin/KotlinWavPackProviderTest.kt`: every WavPack corpus file decoded through the provider equals the committed oracle PCM hashes (reuse `codecs` fixtures), refusals logged with provider id; add a host real-time-factor check (decode speed after warm-up) and record it in HANDOFF as the early speed signal
- [ ] T005 [US1] APE + Musepack providers `app/player/decoders/kotlin/KotlinApeProvider.kt`, `KotlinMusepackProvider.kt` + registration + oracle-hash host tests `test/player/decoders/kotlin/KotlinApeMusepackProviderTest.kt` (same pattern as T004)
- [ ] T006 [US1] WMA family provider `app/player/decoders/kotlin/KotlinWmaProvider.kt` (ASF demux; WMA 1/2, Lossless, Pro, Voice; refusals by name per codec) + registration + oracle-hash host tests `test/player/decoders/kotlin/KotlinWmaProviderTest.kt`
- [ ] T007 [US1] DSD provider `app/player/decoders/kotlin/KotlinDsdProvider.kt` (DSF, DFF; WavPack-DSD through the WavPack provider's DSD output) with seek pre-roll (FR-004) and rate conversion above 192 kHz via `:dsp` HQ resampler (research R5); host tests `test/player/decoders/kotlin/KotlinDsdProviderTest.kt` against the DSD fixtures within `docs/dsd/ORACLE.md` tolerance
- [ ] T008 [US1] ADPCM / G.711 / compressed AIFF-C provider `app/player/decoders/kotlin/KotlinPcmVariantsProvider.kt` (MS-ADPCM and IMA variants in WAV/AIFF-C `ima4`, A-law/µ-law incl. AIFF-C `alaw`/`ulaw`), lifting 001's refusal of compressed AIFF-C in `app/player/decoders/aiff/` only where the Kotlin decoder supports it; platform keeps priority where it decodes; host tests `test/player/decoders/kotlin/KotlinPcmVariantsProviderTest.kt`
- [ ] T009 [US1] ALAC fallback (research R2 Pattern B): `app/player/decoders/kotlin/KotlinAlacProvider.kt` supplies an audio renderer with a Kotlin `SimpleDecoder` for `audio/alac` used only when `CodecProbe` finds no platform ALAC decoder; host test `test/player/decoders/kotlin/KotlinAlacProviderTest.kt` (packet dumps from `WAXFLOW_ALAC_PACKETS` → oracle hashes)
- [ ] T010 [US1] Multichannel (research R4): channel placement with `:dsp` `Media3Layout.forMask` and downmix with `Matrix.forLayouts` when the device output has fewer channels, inside the adapter (`KotlinDecodingExtractor.kt` or a new `app/player/decoders/kotlin/ChannelMapper.kt`); host tests `test/player/decoders/kotlin/ChannelMapperTest.kt` with 5.1/3ch/6ch-reordered corpus layouts
- [ ] T011 [US1] Offline decode and failure log: `app/analysis/OfflineDecoder.kt` uses the registry's providers (FR-009) so newly covered formats get loudness analysis; decode-failure log entries name the provider and refusal (FR-005); host tests `test/analysis/OfflineDecoderKotlinFormatsTest.kt`
- [ ] T012 [US1] Write device tests (not run yet) `androidTest/player/KotlinFormatsPlaybackTest.kt`: each covered format's corpus file plays on Media3; offline decode on device = oracle hash; gapless joins ≤ 2 frames for WavPack, APE, ALAC, Musepack; seek ≤ 300 ms per format; crossfade between two Kotlin-decoded tracks with no underrun; push corpus files with the existing test-corpus procedure (`scripts/test-corpus/README.md`)
- [ ] T013 [US1] M1 full gate + phone check: full unit suites + all module tests, `--lint`, `assembleDevQa`; on `.qa` run `KotlinFormatsPlaybackTest` plus 001 regression classes `Media3EngineGaplessTest`, `Media3CrossfadeTest`, `Media3EngineCoreTest`, `NormalizationLevelDeviceTest`; bisect any failure over M1's commits; milestone report in HANDOFF (SC-001, SC-002 results)

## Phase 3: M2 — Night mode and silence-aware crossfade (US4, P2)

**Goal**: FR-019…021. **Independent test**: loudness range on/off; fade position vs last audible sample.

- [ ] T014 [TESTS-FIRST] [HIGH-RISK] [US4] Write `test/player/media3/FloatChainNightModeTest.kt` (contracts/sink-chain.md): OFF output bit-identical to the 001 chain; ON reduces loudness range of the test signal by ≥ 30 %; output never above the limiter ceiling; ON/OFF switch uses a 50 ms crossfade (no step > the bound set in the test); both A/B players follow the one setting
- [ ] T015 [HIGH-RISK] [US4] Implement the Night mode stage (`:dsp` `gain.Compressor` voice preset, before the limiter) in `app/player/media3/FloatChainAudioSink.kt`, setting `nightMode` (default `false`, data-model.md) in the existing playback preferences, wired through `Media3Engine.kt`; make T014 pass without modifying it
- [ ] T016 [US4] Silence profiles in the analysis pass (research R6): `app/analysis/TrackAnalyzer.kt` / `AnalysisMeasurer.kt` run `:dsp` `silence.Detector` on the same decoded PCM; write `silenceLeadMs`, `silenceTailMs`, `silenceVersion = "silence-1"` (contracts/analysis-document.md); threshold −60 dBFS, minimum 1.0 s unless the detector's ported defaults differ (then D-entry); version change → re-analysis once; host tests `test/analysis/SilenceProfileTest.kt`
- [ ] T017 [TESTS-FIRST] [HIGH-RISK] [US4] Write `test/player/media3/TransitionControllerSilenceTest.kt`: fade trigger moves earlier by the outgoing `silenceTailMs`; incoming starts at its `silenceLeadMs`; no profile or toggle off → exactly 001 timing; tracks shorter than fade + silence; manual skip, repeat-one and gapless (crossfade off) unchanged
- [ ] T018 [HIGH-RISK] [US4] Implement silence-aware timing in `app/player/media3/TransitionController.kt`, profiles carried on the playback item from `app/player/PlaybackCoordinator.kt` (read from analysis documents), setting `crossfadeSkipSilence` (default `true`); make T017 pass without modifying it; 001 `Media3CrossfadeTest` host/device expectations unchanged for tracks without profiles
- [ ] T019 [US4] Settings UI: "Night mode" toggle (playback settings) and "Skip silence in crossfade" toggle (crossfade settings), strings in `values/strings.xml` and `values-ar/strings.xml` (`ArabicTranslationCompletenessTest` passes); Night mode shown as Media3-only like normalization
- [ ] T020 [US4] Write device tests (not run yet) `androidTest/player/NightModeDeviceTest.kt` and `androidTest/player/SilenceCrossfadeDeviceTest.kt` (capture sink measurements as in 001's crossfade tests: SC-008, SC-009)
- [ ] T021 [US4] M2 full gate + phone check: full suites, `--lint`, `assembleDevQa`; on `.qa` run T020 classes plus `Media3CrossfadeTest`, `Media3EngineCoreTest`, `Media3LimiterTest`; bisect on failure; HANDOFF milestone report; add owner listening checks (Night mode, crossfade feel) to the manual list

## Phase 4: M3 — Scan path and tags (US2, P1)

**Goal**: FR-010…015. **Independent test**: corpus in a chosen folder appears with reference tags.

- [ ] T022 [US2] MediaStore probe (research R8; this task may use the phone, `.qa` only): `androidTest/sync/MediaStoreFormatProbeTest.kt` records, for every covered format in the corpus folder, presence in `MediaStore.Audio`, in `MediaStore.Files`, and whether the app can open it by path; run it on the CPH2307 (and emulator API 31/34/36 if available, else NOT RUN); write results and the chosen mechanism into `research.md` R8 and a D-entry
- [ ] T023 [TESTS-FIRST] [HIGH-RISK] [US2] Write `test/sync/ExtraFormatScanTest.kt` (fake media sources): files missing from the audio index but in the chosen folders are admitted when a provider supports them; no duplicate rows when indexed; scan filter folders respected; unsupported/damaged files counted in the skip summary; scan continues after a bad file
- [ ] T024 [HIGH-RISK] [US2] Implement the extra-format pass per T022's decision in `app/sync/MediaStoreLibraryScanner.kt` / `LibraryScanRunner.kt` (and the folder-grant UI only if the decision is SAF); make T023 pass without modifying it
- [ ] T025 [TESTS-FIRST] [US2] Write `test/sync/MetadataTagRoutingTest.kt`: for APEv2 (APE/WavPack/Musepack), ASF, DSF/DFF, Matroska/WebM, AIFF-C corpus files, the library fields (title, artists, album, album artist, track/disc, year, genre, artwork presence) equal the WaxLabel oracle results in `docs/waxlabel/oracle-step*.json`; gain tags parsed like `TagGainSource`; exactly one file open per tag read; 001 formats still read by `EmbeddedTagReader` unchanged
- [ ] T026 [US2] Implement routing in `app/sync/EmbeddedTagReader.kt` to `:metadata` `Metadata.read` (`Pictures.METADATA_ONLY`) for those containers (research R9), gain values into `app/player/normalization/TagGainSource.kt`, `CurrentMetadataSchemaVersion` 7 → 8 in `MediaStoreLibraryScanner.kt`; make T025 pass without modifying it
- [ ] T027 [US2] Write device test (not run yet) `androidTest/sync/ExtraFormatScanDeviceTest.kt`: corpus folder scan → every supported file listed once with reference tags (SC-003, SC-004), including the scan-time comparison on a 1,000-file generated library
- [ ] T028 [US2] M3 full gate + phone check: full suites, `--lint`, `assembleDevQa`; on `.qa` run T027 plus 001 scan classes (`MediaStoreLibraryScannerTest` device variants present in androidTest); bisect on failure; HANDOFF report; owner manual check: rescan of a real library

## Phase 5: M4 — Mood Radio (US3, P2)

**Goal**: FR-016…018. **Independent test**: analysed tracks → Mood Radio queue per mood.

- [ ] T029 [TESTS-FIRST] [US3] Write `test/analysis/MoodAnalysisTest.kt`: the analysis pass feeds `:analysis` `PcmAnalyzer` with the loudness PCM and writes `energy`, `danceability`, `brightness`, `tempo`, `moodRaw`, `moodVersion` (contracts/analysis-document.md); values equal `:analysis` results for the same PCM; percentiles document `__mood_percentiles__` written after a batch; `moodVersion` change re-analyses once; interrupted analysis resumes without redoing finished tracks
- [ ] T030 [US3] Implement mood features + percentiles in `app/analysis/TrackAnalyzer.kt`, `AnalysisMeasurer.kt`, `LoudnessAnalysisWorker.kt` (research R11), respecting `AnalysisConcurrency.kt`; make T029 pass without modifying it
- [ ] T031 [US3] Mood Radio entry points: make every Mood Radio UI entry (`app/ui/components/TrackContextMenu.kt`, `App.kt`, settings) available on the Media3 engine when `SyncDatabase.moodRadioEligibleTrackIds` is non-empty; host tests for the gating and `sharedLogic` `MoodRadioTest` unchanged and passing
- [ ] T032 [US3] Write device test (not run yet) `androidTest/mood/MoodRadioDeviceTest.kt`: analyse a small corpus on device, start Mood Radio per mood, queue built and refilled (SC-007)
- [ ] T033 [US3] M4 full gate + phone check: full suites, `--lint`, `assembleDevQa`; on `.qa` run T032 plus `LoudnessAnalysis*` device classes; bisect on failure; HANDOFF report; owner manual check: Mood Radio by ear

## Phase 6: M5 — CUE sheets (US5, P3)

**Goal**: FR-022…024. **Independent test**: cue images → per-track library rows, gapless album.

- [ ] T034 [TESTS-FIRST] [HIGH-RISK] [US5] Write `test/sync/SyncDatabaseMigration14To15Test.kt` and `test/sync/CueTrackDaoTest.kt`: v14 → v15 adds nullable `cueSourcePath` (String?), `cueIndex` (Int?, 1-based), `cueStartMs` / `cueEndMs` (Long?, start inclusive, end exclusive, end null = to end of file) (data-model.md); existing rows, history, play counts, hidden state and analysis documents survive; cue track ids `<file track id>#<cueIndex>`
- [ ] T035 [HIGH-RISK] [US5] Implement the v15 entity fields and migration in `app/sync/SyncDatabase.kt`; make T034 pass without modifying it
- [ ] T036 [US5] Cue scan: external `.cue` next to the file and embedded cue sheets (`:metadata` CUESHEET) parsed with the `:codecs` CUE parser into cue rows; the plain file row hidden (not deleted) while the cue is valid; invalid/mismatched cue → plain file kept + skip summary entry; host tests `test/sync/CueScanTest.kt` (non-UTF-8, INDEX 00 pregaps, multiple FILE entries, missing file)
- [ ] T037 [HIGH-RISK] [US5] Cue playback: `MediaItem.ClippingConfiguration` for cue rows in `app/player/media3/Media3Engine.kt` / `app/player/PlaybackCoordinator.kt` item building; seek bar shows the cue track's length; statistics and Last.fm use the cue track; consecutive cue tracks of one file join gaplessly (measure; if > 2 frames, record value + D-entry, don't fake); host tests `test/player/CuePlaybackTest.kt`
- [ ] T038 [US5] Write device test (not run yet) `androidTest/player/CueSheetDeviceTest.kt`: cue track starts within 1 frame of its index, album joins ≤ 2 frames (SC-010)
- [ ] T039 [US5] M5 full gate + phone check: full suites, `--lint`, `assembleDevQa`; on `.qa` run T038 plus `Media3EngineGaplessTest`; bisect on failure; HANDOFF report

## Phase 7: M6 — Performance gate (US6, P1 gate)

- [ ] T040 [US6] Write `androidTest/regression/Regression002MeasurementTest.kt` (research R12): per-format real-time factor after warm-up, two-decoder crossfade headroom, steady CPU vs FLAC on Media3, battery drain, memory, seek latency, analysis throughput, Night mode cost; each method time-boxed (≤ 10 min, 3 runs, median)
- [ ] T041 [US6] Run T040 on the CPH2307 `.qa` build (release-like build as in 001 T062g where needed); record results in `specs/002-kotlin-decoders/research/regression-002.md` and HANDOFF against SC-005/SC-006; failing formats reported with numbers for the owner (no fallback switched on)

## Phase 8: Polish

- [ ] T042 Docs: update `CLAUDE.md` (Playback, Library scan, Known state), `specs/002-kotlin-decoders/quickstart.md`, third-party notices for every newly linked module; full regression like 001 T063 (host suites, lint, all device classes on `.qa`, known baseline failures listed) and the Checkpoint 002 report in HANDOFF

## Dependencies & order

T001 → M1 (T002 → T003 → T004 → T005…T011 → T012 → T013) → M2 (T014 → T015; T016 → T017 → T018; T019; T020 → T021)
→ M3 (T022 → T023 → T024; T025 → T026; T027 → T028) → M4 (T029 → T030 → T031 → T032 → T033)
→ M5 (T034 → T035 → T036 → T037 → T038 → T039) → M6 (T040 → T041) → T042.
M2 needs M1's providers only for its device checks; M4 needs M1 (formats) and T016 (shared analysis pass).
Runner order = task id order; no parallel runs (one laptop, one branch).

## Implementation strategy

MVP = M1 (US1): every format plays. Each milestone is shippable on its own after its phone check.
