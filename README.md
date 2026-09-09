# Sistrum

A local-library Android music player with a Material 3 Expressive UI.

[![License: GPLv3](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE)
[![minSdk](https://img.shields.io/badge/minSdk-24-brightgreen.svg)](app/build.gradle.kts)

## What is Sistrum

Sistrum is a local-library-only Android music player: it plays audio already on your device and doesn't talk to any streaming service. It's built with Jetpack Compose and a Material 3 Expressive UI, including dynamic theming seeded from whatever's currently playing. Sistrum is a fork of [Citole](https://github.com/bmaroti9/Citole) by Bence Maróti.

## The Name

> For over 7,000 years, the ancient Egyptians treated music as something sacred. Hathor, goddess of music, joy, and love, was depicted holding a distinctive ceremonial rattle — the **sistrum**: a metal frame strung with jingling bars, believed to ward off negative energy and summon harmony. Her son **Ihy**, god of music himself, was shown as a child holding the very same instrument. Temples like Dendera carved their columns in the shape of the sistrum itself — not mere decoration, but an architectural statement that music was foundational to existence.
>
> The name Sistrum wasn't chosen by accident. Like the ancient instrument that turned vibration into sound and sound into joy, this app exists to connect you to your music library — simply, smoothly, with nothing standing in between.

## Features

| Area | Highlights |
| --- | --- |
| Playback | On-device audio playback via Media3/ExoPlayer, background playback service, queue management with drag-to-reorder and swipe-to-remove, repeat modes, resumes the last queue on restart |
| Library | Automatic library scanning via MediaStore, browsing by album/artist/track, filtering by type (songs, podcasts, audiobooks), sorting, and a universal search across the whole library |
| Discovery | A "For You" tab with recently played and most played, plus a similarity-graph-based recommendation engine that suggests similar artists/albums and auto-extends the queue - tunable via a custom shuffle engine (discovery radius and queue trajectory) |
| Personalization | Material 3 Expressive UI with dynamic color seeded from the currently playing track's album art, playlists (including a built-in Favorites playlist) |

## Building

Requirements:
- JDK 17 or newer
- Android SDK with platform 37 installed (`compileSdk` 37; `minSdk` is 24 / Android 7.0)

```sh
git clone <this repository's URL>
cd Sistrum
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## License

Sistrum is licensed under the [GNU General Public License v3.0](LICENSE).

This project is a fork of [Citole](https://github.com/bmaroti9/Citole) by Bence Maróti, and inherits Citole's GPLv3 license terms.
