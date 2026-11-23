# M002 tasks

Superseded by [M003](../003-mpv-audio-kit-playback/tasks.md); do not start these tasks. Start after M001-T007 is complete. Run tasks sequentially with a fresh repository map per task, targeted checks and independent review. Test commands use the toolchain and Android module retained by M001.

- [ ] M002-T001 Integrate and verify Kotlin codecs in the Media3 source factory
  - Paths include `android/app/src/main/kotlin/com/amwolfstein/sistrum/Media3Sources.kt`, `Media3Player.kt` beside it, the retained Gradle files and Media3 tests. Reuse SistrumKotlin providers for WavPack and DSF/DFF. Compare ALAC/APE/Musepack/WMA coverage where needed to preserve supported formats.
  - Verify ordinary FLAC, WavPack PCM, DSF/DFF to PCM, seek, descriptor release, malformed input and gapless handoffs through Media3 on Android.
- [ ] M002-T002 Make Media3 the sole playback engine and retire specialized output
  - Depends on T001. Update `lib/services/music_player_runtime.dart`, `music_playback_deck.dart`, `media3_playback_deck.dart`, `lib/models/settings.dart`, `lib/screens/settings/playback_settings_page.dart` and related Android bridge/manifest callers. Remove playback switching and USB/AAudio/DoP entry points and settings, including stale values affecting sound controls.
  - Run focused playback tests and exercise local/SAF queue restore, gapless, AutoMix, DSP, Bluetooth, standard DAC output and background playback.
- [ ] M002-T003 Replace FFmpeg local metadata and cover operations
  - Paths include `lib/services/metadata_persistence_service.dart`, `metadata_cover_resources.dart`, `rust_backend/crates/core/src/tags/` and `rust_backend/crates/mobile/src/tags.rs`. Preserve the storage bridge and produce the read/write capability matrix.
  - Run Rust tag tests and Dart metadata tests. Verify multi-value tags, cover art, embedded lyrics, unchanged audio payload, write failure and SAF recovery.
- [ ] M002-T004 Replace FFmpeg local audio analysis
  - Paths include `lib/services/audio_analysis_runner.dart`, `audio_analysis_ffmpeg.dart`, `rust_backend/crates/core/src/native_audio_analysis.rs` and Android decoder integration. Preserve actions and outputs retained by M001.
  - Compare reference fixtures, bit depth/sample rate and loudness/peak results. Verify bounded cancellation, unsupported formats and decoder resource release.
- [ ] M002-T005 Preserve CUE file export without FFmpeg and remove format conversion
  - Depends on T003 and relevant decoders from T001/T004. Update `lib/screens/track_metadata_convert.dart`, `lib/services/cue_split_library_service.dart`, `ffmpeg_conversion.dart`, and the Rust CUE path. Preserve physical export, output format rules, artwork and library registration. Remove general conversion controls and callers.
  - Test exact FLAC sample boundaries, other retained output formats, SAF export, collisions and partial failure, and verify the original file stays intact.
- [ ] M002-T006 Remove Java sources and retire obsolete build dependencies
  - Depends on T002–T005. Port retained Java sources under `third_party/audio_service/android/` and Android instrumented tests to Kotlin. Retire `android/app/src/main/cpp/`, FFmpeg vendoring and unused dependencies, lock entries and native build inputs in Gradle, pubspec, scripts and CI. Audit other remaining first-party C/C++ and Java files against the Android-only baseline.
  - Verify Gradle compilation, manifests, bridge registrations, native dependency lists and 16 KB alignment through the existing packaging checks. Verify app background controls and notifications.
- [ ] M002-T007 [FINAL] Verify cumulative offline playback and local audio processing
  - Run the workflow validator, Flutter analysis and relevant suites, Kotlin and Rust tests, and device regression coverage for local playback and storage, metadata and CUE. Inspect an authorized release artifact for FFmpeg, legacy engine artifacts and removed CMake/native libraries. Record unavailable device/format coverage explicitly.
