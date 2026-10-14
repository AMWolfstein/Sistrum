# Feature Specification: Kotlin decoders, tags, analysis and DSP in the app

**Feature Branch**: `feature/media3-migration` (no separate branch; owner plan 2026-10-14)
**Created**: 2026-10-14
**Status**: Draft
**Input**: `NOTES.md` (owner decisions 2026-10-05/13), `OWNER-PLAN-2026-10-14.md` Phase B, and the four modules imported
in Phase A (`:codecs`, `:metadata`, `:analysis`, `:dsp`; `IMPORT-LOG.md`).

Governing: constitution Principles 6 (format parity through the Decoder Registry), 8 (native code ends with 003),
10 (built for all users), 11 (Kotlin end state; WaxFlow is a reference). Spec 001's shipped behaviour and ADRs stay as
they are, except the owner-approved silence-aware crossfade (US4).

## Context

Phase A brought four pure-JVM modules into the repository, each already checked bit for bit (or within a stated
tolerance) against its reference implementation: decoders for WavPack, APE, ALAC, Musepack, ADPCM/G.711, WMA
1/2/Lossless/Pro/Voice, AIFF and DSD (DSF/DFF, WavPack DSD), plus a CUE sheet parser (`:codecs`); a read-only tag
reader for every container the app meets (`:metadata`); the Mood feature extractor (`:analysis`); and DSP kernels
(resamplers, near-silence detector, channel matrices, gain, voice compressor, true-peak limiter, FFT) (`:dsp`).
None of them is used by the app yet. 002 puts them to work. Removing FFmpeg and the native engine is spec 003;
writing tags is out of scope.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Play every format on the Media3 engine (Priority: P1)

A user with WavPack, APE, Musepack, WMA, DSD, ALAC (on a phone without an ALAC decoder), ADPCM/G.711 WAV or
compressed AIFF-C files picks one and it plays on the default (Media3) engine: correct sound, seeking, gapless joins,
crossfade, normalization and the session limiter, exactly as for FLAC.

**Why this priority**: today these files only play on the native fallback engine (or not at all). Without this, `main`
can't move to Media3-only (003), and 001 + 002 together are what restores format parity.

**Independent Test**: on the `.qa` build, play the test corpus file of each format; offline-decode each and compare
PCM with the reference decoder's output; play two same-format tracks back to back and measure the join.

**Acceptance Scenarios**:

1. **Given** a supported file in any newly covered format, **When** the user plays it on Media3, **Then** it plays, and
   its decoded PCM equals the reference decoder's output (bit-exact; DSD within the tolerance in `docs/dsd/ORACLE.md`).
2. **Given** two gapless tracks of a newly covered lossless format, **When** one ends, **Then** the next starts with no
   gap and no overlap beyond 2 frames.
3. **Given** a file the platform already decodes (e.g. ALAC on a phone that has a decoder), **When** it plays, **Then**
   the platform decoder is used (registry order: platform first, Kotlin second).
4. **Given** a file a Kotlin decoder refuses (e.g. a WMA Lossless mode the reference refuses), **When** the user plays
   it, **Then** the normal playback error shows and the decode-failure log names the provider and the refusal reason.
5. **Given** a multichannel file (e.g. 5.1 WavPack or DSD), **When** it plays, **Then** channels reach the output in
   the right speaker positions, or are downmixed to the device's channel count without clipping beyond the limiter.
6. **Given** a crossfade between two tracks that both use Kotlin decoders, **When** the fade runs, **Then** both decode
   in time (no underrun) and the fade sounds as with FLAC.

---

### User Story 2 - Find and tag the files the platform doesn't index (Priority: P1)

A user's library contains files Android's media index doesn't classify as audio (e.g. `.wv` on the CPH2307), or files
whose tags the current reader can't read (APEv2, ASF, DSF/DFF ID3, Matroska). After a scan they appear in the library
with correct title, artist, album, artwork, track numbers and gain tags.

**Why this priority**: a decoder is useless if the file never reaches the library (NOTES "Top-level design item").

**Independent Test**: put the corpus in a chosen folder on the `.qa` build, scan, and compare library rows with the
tag reader's reference results; check the skip summary.

**Acceptance Scenarios**:

1. **Given** a supported file the media index doesn't list, inside a folder the user chose, **When** the library scans,
   **Then** the file appears in the library.
2. **Given** files with APEv2, ASF, DSF/DFF ID3, Matroska or MP4 tags, **When** scanned, **Then** title, artists, album,
   album artist, track/disc numbers, year, genre, artwork and ReplayGain/Sound Check values match the reference
   reader, and gain tags feed normalization (no longer "untagged").
3. **Given** an existing library scanned by 001, **When** the app updates, **Then** affected files are re-read once
   (metadata schema version bump) and listening history, play counts and hidden state are kept.
4. **Given** a damaged or unsupported file, **When** scanned, **Then** the scan continues and the file is counted in
   the skip summary (001 FR-065/066) without crashing or stalling the scan.

---

### User Story 3 - Mood Radio is back (Priority: P2)

A user starts Mood Radio and gets a queue built from tracks that match the chosen mood, computed on the phone from the
user's own files.

**Why this priority**: a feature the fork lost (constitution: no feature loss); it depends on US1 decoders for full
coverage.

**Independent Test**: analyze a small library on the `.qa` build, start Mood Radio for each mood, and check that the
queue is built from tracks whose stored features match the mood rules.

**Acceptance Scenarios**:

1. **Given** a library, **When** background analysis runs, **Then** each track gets energy, danceability, brightness
   and tempo stored next to its loudness data, with the same values the reference implementation computes for the
   same decoded audio.
2. **Given** analyzed tracks, **When** the user starts Mood Radio, **Then** it builds and refills the queue as before
   the fork (001 FR-016), on the Media3 engine.
3. **Given** analysis is interrupted (app closed, phone busy), **When** it resumes, **Then** it continues without
   redoing finished tracks and without visible slowdown of playback.

---

### User Story 4 - Night mode and silence-aware crossfade (Priority: P2)

A user listening quietly turns on Night mode and loud passages no longer jump out over quiet ones. A user with crossfade
on hears the fade over music, not over a track's silent tail or lead-in.

**Why this priority**: new listening comfort built on the imported DSP; silence-aware crossfade is the one change to
001's shipped behaviour the owner approved.

**Independent Test**: measure loudness range of a test signal with Night mode off/on; measure where the fade starts
relative to the last audible sample on corpus tracks with long silent tails.

**Acceptance Scenarios**:

1. **Given** Night mode on, **When** a track with a wide dynamic range plays, **Then** its loudness range is reduced
   (voice compressor preset) and output still never exceeds the limiter ceiling.
2. **Given** Night mode off, **When** anything plays, **Then** output is identical to 001's (bit-exact through the
   chain).
3. **Given** crossfade on and the outgoing track ends with more than the minimum silence, **When** the fade is
   scheduled, **Then** it starts so that it overlaps the last audible audio, and the incoming track's leading silence is
   skipped.
4. **Given** a track with no detectable silence, **When** crossfade runs, **Then** timing is as in 001.

---

### User Story 5 - Albums stored as one file with a CUE sheet (Priority: P3)

A user has albums ripped as one big file plus a `.cue` sheet (or an embedded cue sheet). The library shows the separate
tracks with their titles, and each plays, seeks, scrobbles and counts as its own track; the album plays without gaps.

**Why this priority**: common in lossless collections (APE/WavPack/FLAC + CUE), but less common than plain files.

**Independent Test**: scan corpus images with cue sheets; play track 3 of an image; play the whole album and measure
the joins.

**Acceptance Scenarios**:

1. **Given** a file with a matching cue sheet, **When** scanned, **Then** the library shows one track per cue entry with
   the cue's titles/performers, and the whole file is not listed as one long track.
2. **Given** a cue track, **When** played, **Then** playback starts at its index position (within 1 frame) and ends
   at the next track's start; the seek bar shows the track's own length.
3. **Given** consecutive cue tracks, **When** the album plays, **Then** the joins are gapless (≤ 2 frames).
4. **Given** a cue sheet that doesn't match its file (missing file, out-of-range indexes), **When** scanned, **Then**
   the file is listed as a normal single track and the problem goes to the skip summary.

---

### User Story 6 - Proven fast enough on a real phone (Priority: P1, gate)

Before the owner relies on 002, decoding, analysis and DSP are measured on a real phone with the test build:
real-time factor, two decoders at once, CPU, battery and memory.

**Why this priority**: NOTES "Performance gate": pure-Kotlin decoding must hold up on a mid-range phone.

**Independent Test**: a scripted, time-boxed measurement run on the `.qa` build (3 runs, median, no run over 10 minutes).

**Acceptance Scenarios**:

1. **Given** the CPH2307 and the `.qa` build, **When** the gate runs, **Then** every format meets SC-005/SC-006 and the
   numbers are recorded in HANDOFF; a failing format is reported with its numbers (owner decides; no silent fallback).

### Edge Cases

- A Kotlin decoder throws mid-stream (corrupt block): playback shows the normal error for that track and moves on
  (001 FR-064); no crash, no stuck queue.
- Sample rates the device can't output (DSD-derived 352.8 kHz, 2.8 MHz-class rates after decimation, odd WavPack
  rates): resampled to a supported rate; analysis uses its own resampling path.
- Files over 2 GB, files on removable storage that disappears mid-play, and files changed between scan and play.
- Mono, 3–8 channel and unusual channel masks.
- Very short tracks (shorter than the crossfade or than the silence minimum).
- Cue sheets in non-UTF-8 encodings, with pregaps (INDEX 00), multiple FILE entries, or pointing at a file in another
  folder.
- Night mode toggled during a crossfade or mid-track.
- Native engine selected: new decoders, Night mode and silence-aware crossfade apply to Media3 only; the native engine
  keeps 001 behaviour (it is removed in 003).

## Requirements *(mandatory)*

### Functional Requirements

**Decoders in playback (M1)**

- **FR-001**: Every decoder in `:codecs` MUST be offered through 001's Decoder Registry for the formats it covers, with
  platform decoders keeping priority where the device has one (registry order unchanged).
- **FR-002**: Decoded audio MUST equal the reference decoder's output (PCM hashes in the committed oracle fixtures);
  DSD within the tolerance documented in `docs/dsd/ORACLE.md`.
- **FR-003**: Gapless metadata (encoder delay/padding, exact sample counts) MUST be honoured so joins between gapless
  tracks of these formats are ≤ 2 frames.
- **FR-004**: Seeking MUST land on the requested position (sample-exact where the format allows; DSD with filter
  pre-roll so no click is audible).
- **FR-005**: Files a decoder refuses MUST fail through the normal error path and be written to the decode-failure log
  with provider name and the specific refusal (001 FR-064/064a).
- **FR-006**: Multichannel output MUST place channels by their declared layout, or downmix with the energy-normalized
  matrices from `:dsp` when the device output has fewer channels; the existing session limiter stays the last stage.
- **FR-007**: Output sample rates the device can't play MUST be converted with the `:dsp` HQ resampler.
- **FR-008**: Decode loops MUST not allocate per block in steady state (buffers reused).
- **FR-009**: The Media3 offline decoder used by loudness analysis MUST use the same providers, so newly covered
  formats get loudness analysis and normalization like every other format.

**Library scan and tags (M3)**

- **FR-010**: The scan MUST find supported files in the user's chosen folders even when the platform media index doesn't
  list them, with no duplicate rows when it does list them.
- **FR-011**: Tags MUST be read with the `:metadata` reader for the containers the current reader doesn't cover (at
  least APEv2, ASF, DSF/DFF, Matroska, Musepack), keeping one file open per tag read and the 001 tag rules (MediaStore
  first; folder-name album exception T033a).
- **FR-012**: ReplayGain / Sound Check values from these containers MUST feed normalization (precedence unchanged:
  measured → tags → unity).
- **FR-013**: Changing what the scan reads MUST bump `CurrentMetadataSchemaVersion` so existing libraries re-read once.
- **FR-014**: Any Room schema change MUST ship with a migration and a migration test; listening history, play counts,
  hidden rows and analysis data MUST survive.
- **FR-015**: A file that fails tag reading MUST still be listed (with fallbacks) or counted in the skip summary; one
  bad file never stops the scan.

**Mood Radio (M4)**

- **FR-016**: Background analysis MUST compute energy, danceability, brightness and tempo with `:analysis` from the same
  decode path as loudness, storing them with the existing analysis data and the algorithm version.
- **FR-017**: Analysis MUST be resumable and bounded (no re-analysis of finished tracks; respects 001's analysis
  concurrency and battery rules).
- **FR-018**: Mood Radio MUST be available again on the Media3 engine and build/refill its queue from the stored
  features (001 FR-016 contract).

**Analysis decoders (M4b, owner 2026-10-14)**
- **FR-030**: The background analysis MUST be able to decode Opus, AAC (LC and HE-AAC with SBR/PS), Vorbis, MP3 and
  FLAC with Kotlin decoders ported from WaxFlow (decoder code only; WaxFlow's encoders are not ported), behind Media3's
  own file readers (Ogg, MP4/M4A, ADTS, MP3, FLAC, Matroska). Playback keeps the platform decoders for these formats.
- **FR-031**: For each of these formats the analysis MUST use the Kotlin decoder only if the phone benchmark shows it
  faster than the platform decoder; otherwise the platform decoder stays and the result is logged. A Kotlin decode
  error on a file falls back to the platform decoder for that file. Loudness results stay within 001's tolerances.

**DSP (M2)**

- **FR-019**: A Night mode setting (default off) MUST apply the `:dsp` voice compressor before the session limiter on
  the Media3 engine; off MUST leave the chain bit-identical to 001.
- **FR-020**: With crossfade on, the fade MUST be timed from the outgoing track's last audible audio and the incoming
  track's first audible audio, using the `:dsp` near-silence detector (minimum silence duration and threshold fixed in
  the plan); tracks without detectable silence keep 001 timing. (Owner-approved change to 001 behaviour.)
- **FR-021**: Silence detection MUST not delay the start of playback or the fade (results ready before they're needed,
  or 001 timing is used).

**CUE sheets (M5)**

- **FR-022**: External `.cue` files next to an audio file and embedded cue sheets MUST be parsed with the `:codecs` CUE
  parser and produce one library track per entry (title, performer, track number, start/end).
- **FR-023**: Cue tracks MUST play, seek, crossfade, scrobble and count in statistics as their own tracks, and play
  gaplessly in order.
- **FR-024**: An invalid or mismatched cue sheet MUST fall back to the plain single file and be reported in the skip
  summary.

**Performance gate (M6) and general**

- **FR-025**: A repeatable measurement on the `.qa` build MUST record, per format: real-time factor after warm-up,
  two-decoder crossfade headroom, CPU share, battery drain and memory, with Mood analysis and Night mode cost.
- **FR-026**: All device work MUST use the `.qa` build only; nothing installs, instruments or force-stops the owner's
  daily apps.
- **FR-027**: New user-visible strings MUST have Arabic translations (`ArabicTranslationCompletenessTest`).
- **FR-028**: Every ported file keeps its attribution header and third-party notices stay complete.
- **FR-029**: The native engine MUST keep working unchanged as the fallback until 003.

### Key Entities

- **Decoder provider**: one entry in the Decoder Registry per format family (name, formats/MIME, priority, refusals).
- **Library track**: a file, or one cue entry of a file (source file, start/end frames, cue index); carries tags,
  gain values and hidden state.
- **Analysis record**: per track — loudness (001) plus energy, danceability, brightness, tempo, algorithm version.
- **Silence profile**: per track — leading and trailing silence lengths, algorithm version (`silence-1`).
- **Decode-failure entry**: time, file, format, codec, provider, error (001 FR-064a).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100 % of corpus files of every covered format play on the Media3 engine on the `.qa` build, and 100 % of
  offline decodes match the reference PCM hashes (DSD within its stated tolerance).
- **SC-002**: Gapless joins between same-format tracks of every covered lossless format, and between consecutive cue
  tracks, are ≤ 2 frames.
- **SC-003**: 100 % of supported corpus files placed in a chosen folder appear in the library after one scan, including
  formats the media index skips; no duplicates.
- **SC-004**: Tag fields of scanned corpus files match the reference reader's results for 100 % of the fields the
  library shows; a full rescan of a 1,000-file library takes at most 20 % longer than in 001.
- **SC-005**: On the CPH2307, every format decodes at ≥ 5× real time after warm-up (WMA Voice and DSD256: ≥ 2×), and
  two decoders at once (crossfade) play without underruns.
- **SC-006**: Steady playback CPU for a Kotlin-decoded format is ≤ 1.5× that of FLAC on the Media3 engine (median of
  3 runs, same device and settings); seeking in any covered format completes within 300 ms (001 SC-006 target).
- **SC-007**: Mood features of corpus tracks match the reference implementation within the tolerances in
  `docs/analysis/ORACLE.md`; Mood Radio starts and refills from analyzed tracks for every mood.
- **SC-008**: Night mode reduces the loudness range of the test signal by at least 30 %; with Night mode off the chain
  output is bit-identical to 001.
- **SC-009**: On corpus tracks with ≥ 2 s trailing silence, the crossfade overlap contains ≤ 100 ms of silence; tracks
  without silence keep 001 timing within 10 ms.
- **SC-010**: Cue tracks start within 1 frame of their index; 100 % of invalid cue sheets fall back without crashing.
- **SC-012**: First-analysis time per audio second on the CPH2307 drops for every format kept on a Kotlin decoder
  (baseline 001 T051d: Opus 136, AAC 128, MP3 114, FLAC 48 ms; Vorbis measured in the same run).
- **SC-011**: Host test suites of all modules pass; lint shows no new error types; the owner's daily apps are never
  touched.

## Assumptions

- The Phase A modules are correct against their references (their oracle suites pass in `IMPORT-LOG.md`); 002 tests
  the wiring, not the algorithms again.
- The CPH2307 is the measurement device; results are a sample (Principle 10). A second device is used where available,
  otherwise NOT RUN with the reason.
- The owner supplies no real APE/DSD files; synthetic and reference corpora stand in (NOTES "Validation").
- Mood Radio's mood rules and queue logic are the pre-fork ones; only the analysis source is new.
- Night mode uses the imported `voice` compressor preset; no user-tunable parameters in 002.
- Kotlin Opus/AAC/Vorbis/MP3/FLAC decoders serve the analysis path only (M4b, FR-030/031); playback of these
  formats stays on the platform decoders.
- Out of scope: removing FFmpeg/native code and the NDK (003), writing tags, Media3 MediaSession, new settings beyond
  Night mode and silence-aware crossfade.
- Owner decisions without the owner present are taken with the recommended option and logged in `DECISIONS.md`.
