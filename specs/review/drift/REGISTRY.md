# Drift registry

Cross-era drift found by `codebase-archaeologist` (read-only). One row per finding; the "Plan" column says where it
gets fixed. Re-check `Fixed` rows at each audit.

First audit: 2026-10-14, after spec 002 M1 (HEAD f079d94). Paths: `app/` = `androidApp/src/main/kotlin/com/amwolfstein/sistrum/`.

| ID | Finding | Files | Type | Severity | Status | Plan |
|---|---|---|---|---|---|---|
| D-01 | Loudness `ANALYZER_VERSION` unchanged although M1 changed the decode path; a `failed` analysis is never retried while the version stays, so tracks M1 made decodable can stay unmeasured | app/analysis/LoudnessModels.kt:9, LoudnessStore.kt, app/sync/SyncDatabase.kt `pendingTrackRows` | Fallback / identity | Moderate | Open (verified by Claude) | T013-fix |
| D-06 | A failed re-analysis overwrites a good analysis document, so the measured gain is lost | app/analysis/LoudnessStore.kt `writeResult`, SyncDatabase `activeAnalyses` | State / fallback | Moderate | Open | T013-fix |
| D-02 | DFF with generic/mixed channel IDs refused in the app: codec exposes `channelIndexMask`, `KotlinDsdProvider` passes `channelMask ushr 2` (0 or wrong bit count) → `ChannelLayoutException` | codecs/.../container/dsd/Dsd.kt:25-29, app/player/decoders/kotlin/KotlinDsdProvider.kt:39, ChannelMapper.kt | Duplicate contract / dead field | Moderate | Open (verified by Claude) | Later task (M5 or cleanup); support source-order placement or document refusal |
| D-03 | ReplayGain/R128/Sound Check tags not delivered for Kotlin-decoded formats (extractor `Format` has no metadata) → tag normalization blind until analysis measures them; three tag readers (EmbeddedTagReader, TagGainSource/Media3, :metadata) | app/player/decoders/kotlin/KotlinDecodingExtractor.kt:99-107, app/player/normalization/TagGainSource.kt | Duplicate tag readers | Moderate | Open | M3 (:metadata wiring) |
| D-04 | Two loudness/true-peak meters (app WaxFlow BS.1770 port vs :analysis FFmpeg-derived) give different numbers by definition | app/analysis/loudness/*, analysis/.../Loudness.kt, analysis/SISTRUM-PATCHES.md | Duplicate implementation | Moderate (latent) | Open | Before M4 wires :analysis: name values distinctly or pick one; parity test |
| D-05 | Loudness meter channel weights from channel count only; decoders place by real mask (3ch FL,FR,LFE weighted wrong) | app/analysis/loudness/LoudnessMeter.kt:350-381 | Units | Moderate-low | Possible, unconfirmed | Before M4 |
| D-07 | Scanner leaves duration 0 for files only Kotlin decoders read (MediaStore/MMR can't parse) | app/sync/MediaStoreLibraryScanner.kt:414-425 | Fallback | Moderate | Possible, unconfirmed | M3 (registry duration probe) |
| D-10 | Session limiter configured for 2 channels while ChannelMapper can emit up to 8; `maxOutputChannels` falls back to 8 below API 33 | app/player/media3/LimiterSession.kt:51, Media3PlayerFactory.kt:304-322 | Fallback / units | Moderate-low | Possible, unconfirmed | T013 device check with 5.1 material; perf phase |
| D-08 | `sha256`/`fingerprint` names hold `identityHash(path|size|mtime)`, not a content hash; KDoc wrong | app/sync/MediaStoreLibraryScanner.kt:207, app/analysis/LoudnessModels.kt:25 | Naming / docs | Cosmetic | Open | Cleanup (no semantic change without a Room plan) |
| D-09 | Decoder-table rows aiff/{ima4,alaw,ulaw}, wav/{alaw,ulaw,ima,msadpcm} never hit by the scan gate (scanner codec == format outside MP4) | app/player/decoders/DecoderRegistry.kt:132-160 | Dead config | Cosmetic | Open | Cleanup |
| D-11 | Module APIs with no app consumer yet: :metadata, :analysis (Aubio, Spectral, PcmAnalyzer, Mood), :dsp Limiter/Compressor/silence Detector; package `com.amwolfstein.sistrum.analysis` shared by app and :analysis | modules | Dead / duplicate | Cosmetic | Open (expected at M1) | Wire in M2–M4 or remove before 003 |
| D-12 | `PendingTrack.durationMs` never read | app/analysis/LoudnessModels.kt:18 | Dead code | Cosmetic | Open | Cleanup |

## Checked consistent (2026-10-14)

Gain units and the -18 LUFS reference across measured and tag sources (R128 Q7.8 shift included); dBTP → linear; time
units (frames ↔ µs ↔ ms) in resume, capture and extractor; 8-bit PCM bias; ALAC/AIFF valid-bit alignment; analysis
`writeResult` transaction + fingerprint guard; engine switch triggers rescan; scan gate, player and offline decode share
`defaultDecoderProviders` (no duplicate registry); single AIFF audio path since T012a.
