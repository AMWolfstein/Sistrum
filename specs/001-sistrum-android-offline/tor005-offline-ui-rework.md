# M001-TOR005 — Rework the offline Library, collection screens, song rows, menus and settings

Owner extra of M001, run **after M001-TOR003 and before M001-TOR004** (the audit then reviews the
final code), as **one TOR, one reviewed commit**. Owner requested one TOR; the explicit-flag column
and the album-type grouping touch the library data contract, which `workflow/README.md` normally
sends to full planning. They are kept inside this TOR at the owner's request and recorded as such.

Map refresh first: `python3 workflow/codegraph/code_tools.py update` (1,427 sources extracted,
0 partial, 0 unindexed) at the start of planning. Baseline `6111960` (M001-TOR003).

Owner rule (confirmed twice): **every removal is total**. Convert track, Hi-res check, Cover resolution and
every other removed item go from the whole codebase, not only the screen: Dart services/controllers/providers,
platform-bridge calls, Kotlin and Rust code, FFmpeg command builders used only by them, tests, docs and l10n.

Owner rule: whatever is removed is removed completely, with no trace (code, providers, stored
preference reads, l10n keys in all ARB files, Kotlin/channel registrations, tests, docs). Stored
preference keys of removed options are deleted once on load (the TOR003 mechanism), nothing else
keeps their names.

## Owner decisions (answers to the planning questions)

1. About footer: `AMWolfstein` and `based on Mornye by` the author name already shown in the repo's About page.
2. Library search is removed from the Library screens; the bottom-bar Search tab stays.
3. Explicit (`E`) comes from the audio file's advisory tag: new Rust tag read and a library database column (migration).
4. Artist groups: a track whose album has `total tracks == 1` is a single; a track with a second artist other
   than the owner artist is a compilation entry under that second artist and also stays in its own place under the
   original artist.
5. Listening statistics become a fifth bottom-bar tab placed **before** Library.
6. The explicit `E` badge sits next to the song title area exactly as in the owner's reference screenshot
   (`E` chip after the artists line); the red "folder/downloaded" badge in that screenshot is not recreated
   (everything is local).

## Scope (all in this one TOR)

### A. Settings
- **Appearance**: `useSystemFont` is always on; custom fonts (setting, picker, files, loading code) removed;
  hero animation, history-view style (lists always) and continue-listening settings removed, behaviour fixed on.
- **Playback**: external player removed with every code path; listening statistics leave Settings for the new
  bottom-bar tab; its recording option is always on and the option removed.
- **Lyrics**: "lyrics on lock screen" removed (Dart, Kotlin media-session code, l10n).
- **Local library**: default view, audio quality and tapping-songs options removed; tapping a song always plays.
- **Pages**: Files & folders, Metadata and App pages removed (and their entries, search catalog, routes, l10n);
  detailed logging is always on (option removed, logger default fixed). Anything else living on those pages is
  listed and moved or removed explicitly in the task log.
- **About**: contribution, translators and special thanks removed; the footer shows `AMWolfstein` and
  `based on Mornye by <author>`.

### B. Library
- Remove search inside the Library, the Folders tab/entry and its options, and Recently added.
- **Loved songs** become a playlist in Playlists; Songs lists no playlists.
- Remove the Songs filters and add-playlist button. The album header's sort control is generalised to Artist,
  Songs, the Albums list and the other collection headers.
- Add a Play button beside Shuffle, as in the Folder tab.

### C. Song row, menu and metadata
- One row everywhere: left artwork (album screens: track number), two lines (title; artists only), right a
  play button in a circle and a three-dots button; `E` for explicit tracks.
- Three-dots menu = every option of the Track metadata screen toolbar (except Copy file path, removed), plus
  **Audio quality analysis**, which opens a bottom sheet like Now Playing's details and runs the analysis at once.
- Now Playing's three-dots menu gains **Delete from device** (same behaviour and confirm as the metadata screen).
- Remove the bottom sheet that opens when tapping a song in the Library, and the metadata sheet page.
- Remove **Convert track** (all callers), **Cover resolution** from Edit metadata, and the **Hi-res check** option.

### D. Collection screens (offline data, old online look)
- Restore the look of the pre-T005 online screens (`git show 071426f^:lib/screens/artist_screen.dart`,
  `album_screen.dart`, `widgets/album_description.dart`, `artist_featured_album_card.dart`) bound to local data and
  delete the current offline screens they replace.
- **Artist**: header image = latest release cover, Play (not Download); Latest release, Albums, Singles,
  Compilations from the library (decision 4); everything else removed.
- **Album**: as before; Play and Shuffle instead of Download; sort remains from the offline version.
- **Playlist, Loved songs, Genre**: same layout as the new Album; only difference: album rows show the track number,
  the others the cover.

### E. Data
- Rust tag reader exposes `explicit`; library database gets an `explicit` column (migration, back-fill on rescan).

### F. Cleanup, tests, docs
- Delete removed-feature l10n keys from all 21 ARB files (+ `flutter gen-l10n`), providers, settings fields
  (+ `settings.g.dart`), dead widgets, services, Kotlin and docs; migrate or delete tests; grep proofs per feature.

## Execution inside the one TOR

One reserved ID, one commit, one independent review (up to 3 rounds). Phases in order, each leaving
`flutter analyze --no-pub` clean; Haiku workers on disjoint file sets within a phase, the coordinator
compares tests with the TOR003 final run (3,913 results; 34 baseline environment failures):
P1 settings → P2 data (explicit) + song row + menu + metadata removals → P3 Library restructure →
P4 collection screens → P5 cleanup, tests, docs, grep proofs, full chunked test run, review.

## Acceptance and checks
- Every removed item has a grep proof of zero hits (identifiers, settings keys, l10n keys, Kotlin/channel names).
- `flutter analyze --no-pub` baseline only; `flutter test` chunked: no new failure against the TOR003 run.
- Tests for: row layout (number vs cover, `E`, play, menu), album-type grouping rule, explicit tag read and DB
  migration (Rust tests), preference cleanup, Loved-as-playlist, sort generalisation, menu contents (no copy path),
  quality-analysis sheet, delete-from-device in Now Playing.
- `cargo test` for the tag/DB changes; `:app:compileReleaseKotlin` after native removals.
- Not verified: device rendering, real audio files with advisory tags on a device, SAF delete on device.

## Risks
- The old online screens depended on network models and providers; they are templates, not drop-in files.
- Explicit/advisory tag variants differ by container (MP4 `rtng`, ID3 `TXXX:ITUNESADVISORY`, Vorbis `ITUNESADVISORY`):
  cover the common ones, record the rest.
- Removing the Files/Metadata/App pages must not strand needed functions (storage access, embed options): the
  task log lists each setting's fate before deletion.
