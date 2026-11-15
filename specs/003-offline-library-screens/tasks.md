# M003 tasks: Offline library detail screens

Input: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md).
Follow `.specify/project-policy.md`. Read plan.md's Repository evidence first.

## Execution rules

Sequential tasks; no parallel markers. The **first action in every task**, including
[FINAL], is `python3 workflow/codegraph/code_tools.py update`; inspect scope and
coverage before edits and stop if it fails. Record refresh evidence in `task-log.md`
inside the task's single commit. At most three review rounds per task and one commit
after its checks and an independent review pass; push to `wip` only after that
commit. No dead code is left: whatever a task removes takes its strings (all 21 ARB
files), settings, assets and tests with it (FR-014). The extension (Apple Music)
pages keep working (FR-013).

Common checks: `flutter gen-l10n` (after ARB changes), `flutter analyze`, full
`flutter test` (baseline 0 failed, 10 skipped), and the task's searches.

Execution order (IDs are allocator order, not run order): T012, T014, T001, T002,
T003, T004, T005, T013, T006, T007, T008, T009, T010, T011.

## Implementation

- [ ] M003-T012 Tag separators for artist and genre credits
  - Acceptance: FR-015, FR-016, FR-017. Port the final Tag separators of
    AMWolfstein/Private `new-blur` (`docs/TAG_CREDITS_PLAN.md`,
    `lib/core/models/tag_credit_settings.dart`,
    `lib/services/library/browse/library_tag_credits.dart`, commits `3cec4f8f`,
    `3d9ad545`, `dd36cad5`, `afeefe70`, `9d8d3a0e`, `e65b64f4`, `49397af5`, `6a8c363a`)
    without composer and without an Album Artists list: `TagCreditSettings` (artist
    and genre delimiters with their choices and defaults, custom delimiters, Keep
    together), one splitter (whole-word word delimiters, backslash escape, bracket
    trim, Keep together, newline always splits, de-duplicate by normalised key), the
    derived TEMP table `library_tag_credits` rebuilt when the library version or the
    rules change (no `library` schema change, no rescan), the settings page (Artists
    for artist and album artist, Genre; chips, All, Add separator, Keep together,
    preview), the Artists list reading track-artist credits (featured artists get a
    page), and Go to artist splitting the credit (one artist opens, several open a
    picker; song menu and Now Playing title). The old splitters it replaces
    (`splitArtistNames` users, ad-hoc genre splits) and the save-time Save with
    (`metadataSeparators`, `metadata_separator_settings_page.dart`, its strings and
    settings-search entries) are deleted; old backups still load (unknown keys are
    ignored). Tests ported and adapted.
  - Paths: `lib/models/`, `lib/providers/settings_provider.dart`, `lib/services/library*`,
    `lib/utils/artist_utils.dart`, `lib/screens/settings/`, `lib/screens/mornye_library_screen.dart`,
    `lib/controllers/clickable_metadata.dart`, download embedding/payload users of
    `metadataSeparators`, ARB files, tests.

- [ ] M003-T014 Refresh the library row after a metadata save
  - Acceptance: FR-019. Port Private's `MetadataIndexReconciler` (`148e103b`): after
    Edit metadata saves, reread the tags, map them onto the scanned row, refresh the
    extracted cover, upsert the index and report a stale index with its own message;
    the credits cache is invalidated for the changed row. Tests ported.
  - Paths: `lib/screens/track_metadata_actions.dart`, `track_metadata_screen.dart`,
    a new `lib/services/metadata_index_reconciler.dart`, ARB files, tests.

- [ ] M003-T001 Library data for the detail screens
  - Acceptance: library queries and mappers (to `Track`/`ArtistAlbum`) for: an
    artist's albums and singles, newest album by release date (undated never
    qualifies), its cover as artist image, featured tracks (artist among
    the track-artist credits of `library_tag_credits` from T012, not the album artist), most-played tracks
    from listening statistics filled with random tracks, an album's tracks by key,
    playlist and Loved tracks. Unit tests for each rule, including undated albums and
    a track that is both album and featured artist.
  - Paths: `lib/services/library_database*.dart`, `lib/providers/` (new library detail
    providers), `lib/utils/artist_utils.dart` (reuse), tests.

- [ ] M003-T002 Artist screen reads the library
  - Acceptance: FR-001, FR-002, FR-009 (library entry points). `ArtistScreen` in
    library mode: header play button instead of download, Latest Release card plays
    instead of downloading, Popular, Albums, Singles & EPs, Compilations; no Releases,
    monthly listeners, share/open in other services, discography download, download
    selection, concerts. The artist-filtered `MornyeLibraryScreen` albums page is
    deleted; the Artists list and library Go to artist open the new page.
  - Paths: `lib/screens/artist_screen*.dart`, `lib/widgets/mornye_artist_header.dart`,
    `lib/widgets/artist_featured_album_card.dart`, `lib/screens/mornye_library_screen.dart`,
    `lib/controllers/clickable_metadata.dart`, tests.

- [ ] M003-T003 Album screen reads the library
  - Acceptance: FR-003. `AlbumScreen` in library mode with compact Play and Shuffle
    (`AlbumPlayActions`) beside Love and Add to playlist; `DownloadedAlbumScreen`,
    `LocalAlbumScreen` deleted; all entry points (plan.md list) open it. First
    inventory the deleted pages' album actions and keep those that work offline.
  - Paths: `lib/screens/album_screen.dart`, `lib/widgets/album_detail_header.dart`,
    `lib/screens/downloaded_album_screen.dart`, `lib/screens/local_album_screen.dart`,
    the entry-point files, tests.

- [ ] M003-T004 Playlists and Loved songs use the playlist design
  - Acceptance: FR-004. Library playlists and Loved songs open `PlaylistScreen` in
    library mode with every current offline action (Play, Shuffle, rename, cover
    change/remove, reorder, remove, export M3U, selection actions, delete files);
    `LibraryTracksFolderScreen` keeps only the Wishlist mode.
  - Paths: `lib/screens/playlist_screen.dart`, `lib/screens/library_tracks_folder_screen.dart`,
    `lib/screens/queue_tab_navigation.dart`, home recents, tests.

- [ ] M003-T005 Library overview and Songs page
  - Acceptance: FR-005, FR-006. No Recently Added shelf (the sort option stays); the
    Songs page loses Create playlist and drag-to-playlist and gains Play and Shuffle at
    the top (same widget as Folders).
  - Paths: `lib/screens/mornye_library_screen.dart`, `lib/screens/queue_tab*.dart`, tests.

- [ ] M003-T013 Library Genres page
  - Acceptance: FR-018. The library overview gets a Genres row opening a page of
    genres read from the genre credits (Genre delimiters), each opening its songs
    with Play and Shuffle and the library track row; the genre filter uses the same
    credits.
  - Paths: `lib/screens/mornye_library_screen.dart`, a new genres screen,
    `lib/screens/queue_tab_filter_sheet.dart`, ARB files, tests.

- [ ] M003-T006 Library track rows use the album row
  - Acceptance: FR-007. Songs, folders, library playlists and Loved songs rows: small
    cover, title, artist only.
  - Paths: `lib/screens/queue_tab_item_widgets.dart`, `lib/widgets/library_directory_view.dart`,
    `lib/widgets/track_list_tile.dart` or `track_card.dart`, tests.

- [ ] M003-T007 Offline-only track menus
  - Acceptance: FR-008 for the pages of this feature, research R5 lists.
  - Paths: `lib/widgets/track_collection_quick_actions.dart`,
    `lib/widgets/track_lists/library_track_menu.dart`, `context_menu.dart`, tests.

- [ ] M003-T008 Now Playing artist link, heart and menu
  - Acceptance: FR-008, FR-009, FR-010. Go to artist opens the library artist page;
    Love is a heart wherever a song is loved (favourite artists keep the star); the
    "..." sheet drops Save offline and Share and adds Remove from device with the
    metadata page's confirmation.
  - Paths: `lib/widgets/mornye_player_actions_sheet.dart`,
    `lib/widgets/mornye_player_favorite_button.dart`,
    `lib/widgets/track_collection_quick_actions.dart`, `lib/screens/now_playing_screen.dart`,
    tests.

- [ ] M003-T009 Port right-to-left lyrics (Mornye #844)
  - Acceptance: FR-011, SC-004. Hand port of `a45631a` to the seven existing files and
    its three tests; extra tests for wrapped RTL lines, translations and pronunciation
    lines; LTR unchanged.
  - Paths: `lib/widgets/player_lyrics*.dart`, `aligned_lyric_pronunciation.dart`,
    `lyrics_editor_sheet.dart`, `lib/screens/lyrics_share_screen.dart`, tests.

- [ ] M003-T010 Port the Now Playing lyric preview (Mornye #847)
  - Acceptance: FR-012. `player_lyric_preview.dart`, `playerShowLyricSnippet`, a Lyrics
    settings switch with l10n keys in all 21 ARB files, wiring in
    `mornye_player_layout.dart`/`now_playing_screen.dart`; RTL direction and sweep from
    T009; reduced motion; its tests. RTL animation confirmed by widget tests: in an
    Arabic line the word-by-word highlight, feather and glow move from the right edge
    to the left, the line is right-aligned and its enter/exit transition follows the
    reading direction; a mixed RTL/LTR line keeps its first strong direction; LTR
    lines are unchanged. Not ported: `.gitignore`, lint switch-off,
    Material layout.
  - Paths: as listed, `lib/models/settings*.dart`, `lib/providers/settings_provider.dart`,
    `lib/screens/settings/lyrics_settings_page.dart`, tests.

## Final cumulative review

- [ ] M003-T011 [FINAL] Cumulative review
  - Acceptance: every FR and SC holds on the cumulative diff from `b397464`; all
    actionable findings fixed; full checks.
