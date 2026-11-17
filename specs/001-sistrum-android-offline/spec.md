# M001 — Sistrum identity, then Android ARM64 offline player

Draft for review. Product baseline `2b5332c` on `reboot`. Artwork reference `origin/wip` at `ba0e973` (approved Sistrum images).

## User scenarios and acceptance

1. **Same app behavior, Sistrum identity.** On a clean install during the first phase, the launcher, splash, app title, About, notifications and docs display the Sistrum product identity. Artwork comes from `wip`. Define package names, version and app-owned runtime identifiers from the current codebase, then make them consistent across Flutter, Kotlin and Rust. Features still present in the imported app continue to behave as before the rename. Scope is shared code (Flutter, Kotlin, Rust) and the Android phone app; Android TV, iOS, macOS, Windows and Linux are left untouched and removed later.
2. **Consistent artwork.** Use the Sistrum mark, colors and visual variants from `wip`. Respect the original dimensions, aspect ratio, framing and transparency of each existing destination. A square source placed in a portrait destination is padded or recomposed from the reference artwork while preserving the mark's proportions. Verify that adaptive launcher masks, splash, notification glyph and in-app layouts render correctly.
3. **Offline player on Android phones.** After phase one is complete, first launch, local file selection, scan, browsing and playback work in airplane mode. Keep local playlists, favorites, history, queue, local art/lyrics, Android media controls and ordinary Bluetooth/headset output. Remove accounts, online discovery, streaming, downloads, NAS, Cast, Link, extensions, telemetry and update calls. Ship only `arm64-v8a`.
4. **Development-only data.** The app has no current users. Start with a clean install under the Sistrum ID and initialize the current local schema. No Mornye package upgrade, signing continuity, legacy DB migration or remote-data compatibility work is required. Audio remains in user-selected storage. Routine Sistrum persistence and fresh local backup/restore still work.

## Theme identity

The Mornye theme keeps its name, classes, persisted selection, tokens and Glass behavior. App labels, identifiers and branding become Sistrum. Preserve existing theme options through this mission.

## Release acceptance

- Phase-one identity acceptance passes before any offline removal begins.
- Final merged release manifest has no app Internet permission and no obsolete online or non-phone components. No app-initiated network traffic occurs in retained flows.
- APK and any AAB-derived APKs contain AArch64 native libraries only. Check 4 KB and 16 KB device/page-size compatibility, offline background playback and permission denial/regrant.
