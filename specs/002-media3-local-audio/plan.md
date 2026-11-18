# Implementation plan

## Baseline and sequencing

Planning snapshot `1e4a706c4ba3ec1da359409090b2d65825db656c` on `ccr-460f213c-xciod8`. M001-T001 through T004 are complete and M001-T005 through T007 are reserved. Refresh the implementation baseline after M001-T007, then recheck surviving feature paths, manifest, plugin registrations and locks before applying this plan. The explicit playback and processing removals start in M002.

## Design

- **Playback.** Keep the existing Dart queue and playback orchestration and Android Media3 MethodChannel. Attach the Kotlin providers at the current `Media3Sources.kt` extractor factory. Test descriptor ownership, seek and gapless boundaries before removing the old deck. Keep current DSP behavior and audio focus. Remove old-engine and exclusive-output settings from UI and runtime. Old stored values must no longer change routing, disable DSP or trigger permission prompts.
- **Android integration.** Keep current media session/notification contracts. Port retained vendored `audio_service` Java code and retained Java tests to Kotlin while preserving package names, manifest classes, callbacks and lifecycle. Generated SDK/Flutter code and the JDK follow their Android toolchains.
- **Metadata.** Extend the current Rust tag reader/writer across the same Dart/Kotlin bridge. Use WaxLabel behavior and format fixtures for gaps. Evaluate the Rust DSF/DFF crates when existing readers lack fields or resilient ID3 handling. Preserve unknown metadata, refuse unsupported writes before changing a file and verify audio essence. Use the existing storage commit path for SAF, whose document-provider capabilities differ from ordinary file renames.
- **Analysis.** Reuse SistrumKotlin's offline PCM decode and loudness components where behavior matches the current retained feature. Keep existing Sistrum Rust analysis logic where suitable. Inventory probe, integrity/Hi-Res, normalization, and any retained comparison actions before replacing dependencies. Compare results with original fixtures and the WaxFlow reference. Keep decoding bounded and cancellable.
- **CUE.** Preserve the current physical split UI and library import. Use Rust sample-boundary splitting and the required FLAC encoding from the WaxFlow reference. Verify CUE frame-to-sample calculations, output lengths, order, metadata, artwork, collision handling, cancellation and partial failures. Preserve the existing stream-copy output policy where supported with the necessary demux/mux logic. Report unsupported inputs before altering files. Keep the source album untouched. Remove general-purpose conversion UI and commands.
- **Packaging.** Remove obsolete Dart/Gradle, native registrations, vendored code and CMake/build inputs after verifying all callers have replacements. Audit APK/AAB artifacts and native loader contracts, including FFmpeg, obsolete C++ targets and the three direct CMake fetches. Keep the Rust bridge used by local features.

## Source references

| Source | Reviewed revision | Use |
|---|---|---|
| [SistrumKotlin](https://github.com/AMWolfstein/SistrumKotlin) | `88e59879402cd23696a222c9a395592fa7fd1ea3` | Kotlin extractors and codecs, offline PCM, loudness and test fixtures. Confirm current Media3 API compatibility and carry GPL-3.0 notices. |
| [WaxLabel](https://github.com/ColeSpringer/WaxLabel) | `a075b7bd091bff36a02d58b1cdf4b2e6f5eb8942` | MIT metadata behavior, format limits and audio-essence verification. |
| [WaxFlow](https://github.com/ColeSpringer/WaxFlow) | `d774b197b0c2b7bad5f972ca0000920d60a9fa81` | MIT analysis reference and selected local CUE splitting/encoding logic. |
| [dsf](https://github.com/AMWolfstein/dsf), [dff](https://github.com/AMWolfstein/dff) | `2007f6b8344cbcddd712622a08beffd92adf3bcd`, `9edb8506290f9f1db0788dd78d4300ff29e9a9ff` | Rust metadata reader references. Cargo declares MIT/Apache-2.0. Record format limits and preserve applicable notices. |

## Repository evidence

The current Media3 source factory supports SAF descriptor leases, and the legacy Dart deck still owns specialized output routing. The Android CMake target links libFLAC, Oboe and libWavPack. The current CUE action calls `FFmpegConversion.splitCueToTracks`, and library playback excludes virtual CUE rows. The FLAC-copy playback fallback in `music_player_formats.dart` serves AVPlayer on iOS/macOS, which M001 retires. Verify any additional FFmpeg call after the M001 baseline is available.

Full CodeGraph refresh passed. Scope was 2,574 files and 1,234 exclusions. The map extracted 2,426 sources, 55,637 symbols and 208,462 relationships, with no partial or unindexed sources reported. Queries for `Media3Sources.kt` and `splitCueToTracks` located the source and export boundaries. The map does not establish generated binding reachability, Android provider behavior, device codec support or packaging. Verify those through direct source, build and device checks after M001.
