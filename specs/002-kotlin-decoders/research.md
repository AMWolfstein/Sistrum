# Research and decisions — 002

Each entry: Decision / Rationale / Alternatives. Choices made without the owner follow the recommended option
(owner plan 2026-10-14) and are also listed in `DECISIONS.md` when they are owner-visible.

## R1 Module wiring
**Decision**: `androidApp` depends on `:codecs`, `:metadata`, `:analysis`, `:dsp` (`implementation(project(...))`);
keep rules for reflection-free code are not needed, but add an R8 smoke check (prodRelease minify builds and a host
test loads each provider).
**Rationale**: modules are pure JVM, JDK 21 bytecode compatible with AGP desugaring. **Alternatives**: AAR wrappers —
no benefit.

## R2 Media3 pattern
**Decision**: Pattern A for every `:codecs` stream format — one generic `KotlinDecodingExtractor` that opens the
codec's stream API over a Media3 `ExtractorInput`-backed random-access source, emits `AUDIO_RAW` (16/24/32-bit int
or float PCM as the codec reports), a `SeekMap` from exact sample counts, and gapless padding from exact lengths.
Pattern B (custom MIME + `SimpleDecoder` renderer) only for ALAC inside MP4/M4A when the platform has no ALAC
decoder (Media3's MP4 extractor already demuxes it).
**Rationale**: 001's AIFF provider proved Pattern A with our float sink and offline decoder; the imported APIs are
stream-level (`open → decodeBlock → seekSample`), so one adapter covers WavPack, APE, Musepack, WMA (ASF), DSD,
ADPCM/G.711 and AIFF-C. **Alternatives**: Pattern B for all — needs per-codec packetization the modules don't expose.

## R3 Provider order and detection
**Decision**: one provider per family (`KotlinWavPackProvider`, `KotlinApeProvider`, `KotlinMusepackProvider`,
`KotlinWmaProvider`, `KotlinDsdProvider`, `KotlinPcmVariantsProvider` for ADPCM/G.711/AIFF-C, `KotlinAlacProvider`),
registered after `PlatformProvider` (platform first). Detection by magic bytes in the extractor's `sniff`, not by
file extension. Refusals use the codec's typed refusal text through `DecoderProvider.refusal`.

## R4 Multichannel
**Decision**: the adapter maps declared channel masks with `:dsp` `Media3Layout.forMask`; when the output device
reports fewer channels than the stream, downmix with `Matrix.forLayouts` to the device count before the sink. The
existing session limiter stays last. **Alternatives**: rely on Android's downmix — unknown per device (Principle 10).

## R5 Sample rates
**Decision**: the adapter emits the codec's rate when ≤ 192 kHz; above that (DSD-derived rates), resample with the
`:dsp` HQ resampler to the highest supported rate in the same family (176.4 kHz for 44.1k multiples, 192 kHz for 48k)
or lower if the device's `AudioTrack` refuses it. Analysis keeps `AnalysisResampler`.

## R6 Silence-aware crossfade
**Decision**: silence profiles (leading/trailing silence, `silence-1`) are computed by the `:dsp` `Detector` during
the existing background analysis pass (same decode as loudness) and stored in the track's analysis document.
`TransitionController` subtracts the outgoing trailing silence from the fade trigger and starts the incoming player
at its leading-silence end. Threshold −60 dBFS, minimum duration 1.0 s (detector defaults ported from WaxFlow unless
they differ; D-entry if changed). No profile → 001 timing. A toggle "Skip silence in crossfade" (default ON) under the
crossfade settings. **Alternatives**: real-time detection while playing — can't see the tail in time for the trigger.

## R7 Night mode
**Decision**: `FloatChainAudioSink` gets an optional `Compressor` (`voice` preset) stage before the limiter, switched
by a global setting "Night mode" (default OFF), with a 50 ms crossfade between bypass and active to avoid clicks.
Off = stage removed (bit-identical to 001). Applies to both A/B players.

## R8 Scan path for unindexed formats
**Decision**: first task of M3 probes, on the `.qa` build (CPH2307) and on emulator images API 31/34/36 where
available, which corpus formats appear in `MediaStore.Audio`, which in `MediaStore.Files`, and what the app can open
by path with its current permissions. Recommended mechanism (taken unless the probe rules it out):
`MediaStore.Files` query for the registry's extensions inside the folders the scan filter includes, admitted rows
read by path like other files. If the platform hides them from this app entirely, fall back to a SAF folder grant
("Add folder for other formats") whose documents are scanned and played by content URI — recorded as a D-entry, and
BLOCKED if it needs an ADR change. **Alternatives**: `MANAGE_EXTERNAL_STORAGE` — not acceptable for a store app.

## R9 Tags
**Decision**: keep `EmbeddedTagReader` for the containers it already reads (no 001 behaviour change); route APEv2
(APE/WavPack/Musepack), ASF, DSF/DFF, Matroska/WebM and AIFF-C through `:metadata` `Metadata.read` with
`Pictures.METADATA_ONLY`, mapping `TagVocabulary` to the same fields and the same gain parsing as `TagGainSource`.
One file open per read. Bump `CurrentMetadataSchemaVersion` 7 → 8 in the task that changes reading.
**Alternatives**: jaudiotagger-kt (NOTES) — superseded by the imported WaxLabel port with oracle parity.

## R10 CUE sheets
**Decision**: Room v14 → v15 adds nullable `cueStartMs`, `cueEndMs`, `cueIndex` (track rows) and a `cueSourcePath`;
cue tracks get ids `<file id>#<n>`; the plain file row is hidden (not deleted) while a valid cue exists. Playback uses
Media3 `ClippingConfiguration`; consecutive clips of the same file are queued so the join is gapless (measured; if the
clip boundary can't reach ≤ 2 frames, the task records the measured value and a D-entry instead of faking).
Embedded cue sheets come from `:metadata` (CUESHEET tags) and external `.cue` next to the file, parsed by `:codecs`.

## R11 Mood features
**Decision**: the analysis pass feeds `:analysis` `PcmAnalyzer` with the same decoded PCM as loudness; results
(`energy`, `danceability`, `brightness`, `tempo` plus raw features and `moodVersion`) go into the existing analysis
document. Corpus percentiles are recomputed (`computeCorpusPercentiles`) after each analysis batch and stored once per
library. `SyncDatabase.moodRadioEligibleTrackIds` already enables Mood Radio when the four keys exist.

## R12 Performance gate
**Decision**: extend `regression/Regression001MeasurementTest` with a per-format real-time-factor method (offline
decode after warm-up), a two-decoder crossfade run, CPU/battery/memory as in 001 T062g, plus analysis throughput and
Night mode cost. Time-box: 3 runs, median, no run over 10 minutes.
