<p align="center"><img src="androidApp/src/main/res/drawable-nodpi/sistrum_about_app_icon.png" alt="Sistrum icon" width="160"></p>

# Sistrum

A music player for Android that plays **your own music files**, offline, with
high-quality playback, and works for every library and every phone.

## Goal

Sistrum exists to be the best way to listen to a local music collection on Android:

- **Every format plays.** FLAC, ALAC, WAV, AIFF, MP3, AAC, Opus, Vorbis, WavPack,
  DSD and more, tagged or untagged, however the library was built.
- **Playback done right.** Gapless albums, smooth crossfades, loudness
  normalization measured on the phone itself (tags as a fallback), an equalizer
  and a limiter that prevents clipping.
- **Private and offline.** No account and no cloud. Your library, play history and
  statistics stay on the device. Online features (Last.fm scrobbling, lyrics) are
  optional.
- **Built for everyone.** No assumptions about one person's formats, tagging tools
  or phone. Fully localized, including complete Arabic with right-to-left layout.
- **Simple, maintainable code.** Playback is moving to Android Media3 with decoders
  written in Kotlin, ending with no native code at all.

## Status

Version **1.0**, in active development on the `feature/media3-migration` branch.

| Stage | What it does | State |
|---|---|---|
| 001 Media3 migration | Media3/ExoPlayer engine next to the old one, crossfade, normalization, equalizer, MediaSession | nearly done |
| 002 Kotlin decoders | Kotlin decoders for the formats Android can't play itself (e.g. WavPack, DSD) | planned |
| 003 Native removal | Remove the old native player and all native code | planned |

Requirements: Android 12 (API 31) or newer, 64-bit ARM (`arm64-v8a`).

## License and credits

Sistrum is free software under the [GNU General Public License v3.0](LICENSE).

It is a fork of [Airmedy](https://github.com/misa198/airmedy) by misa198. Please
consider sponsoring the original author from the About screen.
