# Research index — 001-media3-migration

External reports (marked "external research") are condensed leads to verify, not facts
(constitution Principle 7).

| File | What | Status |
|---|---|---|
| `discovery.md` | Phase 0 discovery: architecture, behaviours to preserve, format inventory, risks | Own work, 2026-09-30 |
| `graph-baseline.md` | Graphify baseline for the playback path | Own work |
| `formats-first-search.md` | First search for AIFF/APE/WavPack/DSD/WMA on Media3 | External, 2026-10-01; partly superseded |
| `ape-wma-sources.md` | Open-source APE and WMA/ASF decoders in any language | External, 2026-10-04 |
| `ndk-free-routes.md` | NDK-free routes for WavPack and WMA (WaxFlow found) | External, 2026-10-04 |
| `go-bridge.md` | Bridging WaxFlow via gomobile / WASM / Kotlin port | External, 2026-10-04; gomobile = 002 fallback only |
| `pure-kotlin-formats.md` | Pure-Kotlin route per format, Media3 Patterns A/B | External, 2026-10-05 |
| `crossfade-single-player.md` | Crossfade inside one player vs A/B; prior art | External, 2026-10-05 |
| `dynamics-processing-session.md` | One DynamicsProcessing on a session shared by two players (AOSP evidence) | External, 2026-10-05 |
| `platform-codecs-cph2307.md` | Platform audio decoders on the CPH2307 (no ALAC) | Own measurement, 2026-10-05 |
| `tarsosdsp-evaluation.md` | TarsosDSP for on-device analysis | External, 2026-10-01; for the analyzer |
| `analyzer-future.md` | On-device analysis, true LUFS + Mood Radio (future) | Record only |
| `future-settings.md` | Settings after the migration (future) | Record only |
| `home-mixes.md` | Home-screen auto playlists (future) | Record only |

## History: the Jellyfin decoder plan (dropped 2026-10-05)

Until 2026-10-05 the plan was Media3 plus Jellyfin's `media3-ffmpeg-decoder` for codecs the platform lacks,
with AIFF/APE/WavPack/DSD/WMA routed per track to the native player. A spike branch
(`spike/b-jellyfin-decoder`) explored it; no branch or findings from it exist in this repository, and none
are carried forward. The owner replaced it with the Decoder Registry plus Kotlin providers (001), Kotlin
ports of WaxFlow's decoders (002) and removal of all native code (003).
