# M003 — Local playback and audio processing through mpv_audio_kit

## Mission boundary

M003 replaces the direction of M002 (Media3 as the sole engine). M002 stays reserved and is not implemented. M001 still owns the offline Android ARM64 transition, including removal of online features and other platforms. M003 starts from the verified M001 result and changes how retained local playback, media session and audio processing are implemented. The Rust backend stays and keeps library, collections, backup, lyrics, palette and tag editing.

## User scenarios and acceptance

1. **Play local music with one engine.** libmpv, through a Sistrum fork of mpv_audio_kit in `third_party/mpv_audio_kit`, plays all music. Preserve local file and SAF playback, queue restore, gapless, AutoMix, EQ, speed/pitch, Skip Silence, ReplayGain/normalization, audio focus and the Mornye theme. Keep ordinary Android output, including Bluetooth and USB DAC through the system. Remove the engine selector, audioplayers, the Media3 ExoPlayer deck, media_kit and the specialized outputs (USB bit-perfect, AAudio, DoP). The spatial audio setting leaves the product because libmpv output does not apply it.
2. **Play every supported format.** FLAC, ALAC, WAV, AIFF, Opus, Ogg, MP3, AAC/M4A, WavPack, APE and DSF/DFF play through libmpv. DSD is decoded to PCM. An unsupported variant gets a clear error and keeps the queue recoverable.
3. **Keep the system media controls.** The notification, lock screen, headset and Bluetooth buttons, background playback, favorite and shuffle buttons and lock-screen lyrics keep working through the vendored session instead of audio_service. Android Auto shows the library, searches it and plays a chosen item. Unplugging headphones pauses. An AutoMix transition never drops or recreates the notification.
4. **Keep local editing and analysis.** Keep every tag editor field (title, artist, album, album artist, date, track and disc numbers and totals, genre, ISRC, lyrics, label, copyright, composer, comment, album type, explicit, UPC, cover, compilation and the artist tag mode) and the retained analysis actions: analysis card, audio compare, Hi-Res check, ReplayGain scan, haptics and song features. Metadata-only edits keep the encoded audio unchanged.
5. **Keep physical CUE splitting.** Split into Tracks creates separate playable files with per-track metadata and available cover art, writes to the selected storage destination and adds completed outputs to the library. Keep current naming and format behavior, including FLAC output for FLAC/WAV/APE/WavPack sources. General format conversion leaves the product.
6. **Smaller app and sources.** Remove FFmpeg kit, audio_service, audioplayers, media_kit, the Media3 playback code, the C/C++ native audio target with its CMake fetches of libFLAC, Oboe and libWavPack, and the Rust USB audio crate. The app ships one FFmpeg, inside libmpv.

## Release acceptance

All retained workflows work offline on supported Android ARM64. Play representative files of every format from normal storage and SAF, and test queue restore, gapless, AutoMix with the notification held, background controls, Android Auto (Desktop Head Unit or a car), tag edits, analysis actions and CUE output. The release APK contains no FFmpeg kit, audio_service, audioplayers, media_kit, ExoPlayer or removed native libraries, meets the Android 4 KB/16 KB requirements inherited from M001 including `libmpv.so`, and shows the libmpv LGPL notices. The measured arm64 release APK is smaller than the owner-measured 62.28 MB baseline.
