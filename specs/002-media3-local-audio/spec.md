# M002 — Media3 playback and offline audio processing

## Mission boundary

M001 owns the offline product transition. M001-T005 removes online services and dependencies, explicitly including network streaming, network storage and online previews. M001-T006 owns the Android ARM64 platform cleanup and M001-T007 verifies the offline release. M002 starts from the verified M001 result and changes how retained local features are implemented.

## User scenarios and acceptance

1. **Play local music with one engine.** Media3 ExoPlayer handles all playback in Sistrum. Preserve local file and SAF playback, queue restore, gapless, AutoMix, EQ, speed/pitch, Skip Silence behavior, audio focus, media controls and the Mornye theme. Keep ordinary Android output, including Bluetooth and USB DAC output through the system. Remove the engine selector, legacy playback and special output modes including USB Bit-Perfect, Direct USB, AAudio Exclusive and DoP.
2. **Keep playback of WavPack and DSD files.** Play WavPack and supported DSF/DFF through Media3 using Kotlin providers from SistrumKotlin. DSD is decoded to PCM during playback. Unsupported codec variants get a clear error and keep the queue recoverable.
3. **Keep local editing and analysis.** Keep the retained audio analysis actions and metadata editing for tags, artwork and embedded lyrics within the supported format matrix. Preserve encoded audio during metadata-only edits. Use the existing Rust backend and Kotlin decoding/analysis as appropriate.
4. **Keep physical CUE splitting.** Split into Tracks creates separate playable files with per-track metadata and available cover art, writes to the selected storage destination, and adds completed outputs to the local library. Use current output naming and format behavior, including FLAC output for FLAC/WAV/APE/WavPack sources. General format conversion leaves the product.
5. **Simplify app sources and packaging.** Remove FFmpeg runtime dependencies, obsolete C/C++ audio and Java sources maintained in this repo. Preserve Android media session, notifications, lock-screen controls and compatible Android build tooling. Remove CMake fetches for libFLAC, Oboe and libWavPack.

## Release acceptance

All retained workflows work offline on supported Android ARM64. Play representative local files and test library/queue persistence, background controls, tag edits and CUE output from normal storage and supported SAF destinations. Verify release dependency and packaging evidence for removed libraries and the Android 4 KB/16 KB requirements inherited from M001.
