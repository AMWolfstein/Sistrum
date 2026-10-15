# Research — 003 native removal (short)

R1 **Inventory source.** `scripts/build-ffmpeg-android.sh` builds FFmpeg 8.1.2 with `--disable-everything --enable-decoders
--enable-demuxers --enable-parsers` (line 56-59), so "everything FFmpeg plays" = every demuxer/decoder in `allformats.c` /
`allcodecs.c` of that source, not a short list. Cross it with audio-only entries and with what the scanner can admit
(`MediaStoreLibraryScanner`: MediaStore `is_music`/audio MIME, generic MIME `audio/ffmpeg`). Provider column comes from
`player/decoders/DecoderRegistry.kt` (Kotlin), Media3 built-in extractors/MediaCodec (platform), else none.
R2 **What loads the native library.** Only `FfmpegDecoder` (`System.loadLibrary("airmedy_player")`); no other `external fun`.
Analysis (`app/analysis/OfflineDecoder.kt`) already uses Media3 renderers; the string `"ffmpeg"` in `LoudnessStoreTest`
is a stored provider label, check before touching.
R3 **Engine choice touch points.** `MainActivity` (normalization availability), `PlaybackService` (factory),
`LibraryScanRunner` (scan gate), `AppDestinationContent` (two preference reads), `DeveloperContent` (row), `PlaybackCoordinator`
(five `EngineKind.Media3` branches, gain Unity vs native), `Regression001MeasurementTest` (native arm).
R4 **Build wiring.** `androidApp/build.gradle.kts`: `ndkVersion`, `defaultConfig.externalNativeBuild` + `ndk.abiFilters`,
`android.externalNativeBuild.cmake.path`. 16 KB alignment/ABI work only applies while a `.so` ships.
R5 **Third-party `.so`.** Unknown until the APK is unzipped; checked in M2/M3 gates. No assumption made.
R6 **Provenance.** `eq_golden.json` and `tools/eq-golden/*` name `cpp/ffmpeg_player.cpp` (D-9). KDoc in `FocusVolumeRamp.kt`,
`BiquadEqualizer.kt`, `PlaybackCoordinator.kt` cite native behaviour; reword, keep the facts.
