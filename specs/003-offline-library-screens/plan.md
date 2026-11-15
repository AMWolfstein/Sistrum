# Implementation Plan: Offline library detail screens

**Branch**: `wip` | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md)

## Summary

Reuse the pages that an extension (Apple Music) search opens — `ArtistScreen`,
`AlbumScreen`, `PlaylistScreen`, which `ExtensionArtistScreen`/`ExtensionAlbumScreen`/
`ExtensionPlaylistScreen` fill from the extension — and feed them library data through a
small local data layer, with online-only parts hidden in library mode. Delete the old
offline artist and album pages, move library playlists and Loved songs to the playlist
design with their management actions, trim the library overview and Songs page, unify
the library track row, make track menus and the Now Playing sheet offline-only, and
port two Mornye lyric changes by hand.

## Technical Context

**Language/Version**: Dart 3.13.7, Flutter 3.47.8
**Primary Dependencies**: flutter_riverpod, sqflite (library and statistics databases)
**Storage**: library database (`LibraryDatabase`), listening statistics
(`ListeningStatisticsStore`), collections database (playlists, Loved)
**Testing**: `flutter analyze`, `flutter test`
**Target Platform**: Android arm64
**Constraints**: the extension (online) pages keep working until the next mission;
Mornye look unchanged apart from the requested changes.

## Constitution Check

- I. Preserve behaviour: only the requested screens change; playback, decoders and
  stored data untouched. PASS.
- II. Plan substantial work: multi-screen redesign and two ports, this plan. PASS.
- III. One task, one reviewed commit: fourteen sequential tasks. PASS.
- IV. Honest verification: baseline recorded; no device claim. PASS.
- V. Simple tooling: no new dependency. PASS.

## Repository evidence

- **Baseline**: `wip` at `b397464`, clean tree. Last full run on this tree: `flutter
  analyze` no issues; `flutter test` exit 0, 0 failed, 10 skipped (3910 passed by the
  JSON count that excludes hidden and `loading` entries).
- **Map**: `code_tools.py update` succeeded: 34310 symbols, 1707 sources, 0 partial,
  0 unindexed, 0 rejected cross-language calls.
- **Apple Music pages**: search results push `ExtensionArtistScreen`,
  `ExtensionAlbumScreen`, `ExtensionPlaylistScreen`
  (`home_tab_search_results.dart:695,722,746`; `clickable_metadata.dart:415-466`),
  which render `ArtistScreen` (`extension_collection_screens.dart:653`), `AlbumScreen`
  (`:360`) and `PlaylistScreen` (`:490`).
- **Artist page** (`artist_screen.dart` `_buildPage` 618-812): `MornyeArtistSurface`/
  header (`mornye_artist_header.dart`, `artist_screen_widgets.dart` 30-170: share,
  favourite star, download, shuffle), `ArtistFeaturedAlbumCard` (Latest Release, with
  a download button), Popular (`_buildPopularSection`), Releases, Albums, Singles &
  EPs, Compilations (`albumType == 'compilation'`, 604), discography download
  (`artist_screen_discography.dart`), selection bar with download and add to playlist,
  monthly listeners, concerts.
- **Old offline artist page**: `MornyeLibraryScreen(page: MornyeLibraryPage.albums,
  artist: name)` (`mornye_library_screen.dart:28,62,100-115`), opened from the Artists
  list (`:373`, `:489`).
- **Album pages**: online `AlbumScreen` (`album_screen.dart`, rows `TrackListTile`
  593-660, header `AlbumDetailHeader` with download/love/add to playlist); offline
  `DownloadedAlbumScreen` and `LocalAlbumScreen` (rows `AlbumTrackTile`, Play/Shuffle
  via `AlbumPlayActions` `album_detail_header.dart:402`). Offline entry points:
  `mornye_library_screen.dart:120,132`, `queue_tab_navigation.dart:201,242`,
  `player_track_navigation.dart:52,76`, `home_tab_recent.dart:311`,
  `library_search_results.dart:371,381`.
- **Playlists/Loved**: online `PlaylistScreen` (header `AlbumDetailHeader`, rows
  `TrackListTile`, selection: download only); offline `LibraryTracksFolderScreen` modes
  `playlist`, `loved`, `wishlist` (`queue_tab_navigation.dart:275-310`) with Play/
  Shuffle, rename, cover change/remove, reorder, remove, export M3U, delete files and
  a selection bar (play, play next, queue, add to playlist, remove, delete).
- **Library**: overview shelf titled `libraryRecentlyAdded`
  (`mornye_library_screen.dart:460-480`) and a sort option of the same name (`:153`);
  Songs = `QueueTab(librarySection: 'all')` (`main_shell.dart:895`) with a Create
  playlist button in its count header (`queue_tab_filter_widgets.dart:289-300`) and
  drag-to-playlist (`:263`, `queue_tab_navigation.dart:442`).
- **Rows**: Songs/Folders/library playlists use `TrackCard` with cover
  (`queue_tab_item_widgets.dart:89`, `library_directory_view.dart:512,551`,
  `library_tracks_folder_screen.dart:1179`); the online album row is `TrackListTile`
  (title, artist plus badges, preview button, quick actions).
- **Menus**: `TrackCollectionQuickActions.showTrackOptionsSheet`
  (`track_collection_quick_actions.dart`: play, add to playlist, love, wishlist,
  download, quality variant, copy/share link, go to album) used by `TrackListTile` and
  the artist Popular rows; `library_track_menu.dart` (play, next, queue, add to
  playlist, save offline, network upload, details, re-enrich, convert, server delete,
  delete); Now Playing sheet `mornye_player_actions_sheet.dart` (go to album/artist,
  favourite star, add to playlist, save offline, share, lyrics share, details,
  equalizer, sleep timer, volume, external player); metadata page menu
  `track_metadata_screen_menu.dart` has `trackRemoveFromDevice`.
- **Love star**: `mornye_player_favorite_button.dart:71`,
  `mornye_player_actions_sheet.dart:82`, `track_collection_quick_actions.dart:111`;
  artist favourite star in `artist_screen_widgets.dart:92,152`.
- **Local data**: `LibraryDatabase` album queries by key and by album/artist,
  `album_artist` with fallback to `artist_name` (`library_database.dart:567`),
  `libraryBrowseProvider` with an `artist` filter, `splitArtistNames`/
  `primaryArtistName` (`utils/artist_utils.dart`), `ListeningStatisticsStore.load`/
  `loadReplayableTracks` returning per-track `plays`.
- **Mornye #844** (`a45631a`, 11 files): applies cleanly only to
  `aligned_lyric_pronunciation.dart`, `player_lyrics_line_style.dart`,
  `player_lyrics_section.dart`; `lyrics_share_screen.dart`, `lyrics_editor_sheet.dart`,
  `player_lyrics.dart`, `player_lyrics_word_sweep.dart` and the three tests need a
  hand port; `desktop/desktop_mini_player.dart` does not exist here.
- **Mornye #847** (net of a reverted commit, 12 files): new
  `player_lyric_preview.dart` and its test, `playerShowLyricSnippet` setting, a Lyrics
  settings switch with hard-coded English, `mornye_player_layout.dart` and
  `now_playing_screen.dart` wiring; it also adds `.prathxm/` to `.gitignore`, disables
  Android lint in `build.gradle.kts` and edits the deleted `material_player_layout.dart`.
- **AMWolfstein/Private `new-blur`** (read-only clone): Tag separators
  (`tag_credit_settings.dart`, `library_tag_credits.dart` TEMP table, settings page,
  credit picker) and `metadata_index_reconciler.dart`; its tree is restructured, so
  every piece is ported by hand (research R7). This tree has `metadataSeparators`
  (save-time, `metadata_separator_settings_page.dart`, read by download embedding),
  `splitArtistNames` in `utils/artist_utils.dart`, a `genre` column and a genre
  filter but no Genres page, and re-upserts the row after a metadata save
  (`track_metadata_actions.dart:151`) without refreshing the cover.
- **Tests touching these screens**: `local_album_playback_controls_test`,
  `downloaded_album_now_playing_test`, `downloaded_album_reenrich_test`,
  `local_album_metadata_navigation_test`, `album_track_metadata_test`,
  `album_replaygain_action_test`, `artist_album_selection_test`,
  `artist_featured_album_card_test`, `mornye_library_browse_test`,
  `playlist_playback_test`, `collection_*_test`, `liked_local_tracks_test`,
  `library_playlist_drag_test`, `track_list_tile_selection_test`,
  `detail_route_loading_test`, `player_lyrics_*_test`, `aligned_lyric_pronunciation_test`.
- **Limitations**: the map does not resolve provider reads or navigation closures as
  edges; entry points were found by search. Album-level actions of the deleted pages
  (re-enrich, ReplayGain, metadata navigation) must be inventoried in T003 before
  deletion and kept where they work offline.

## Task design

T012 tag credits → T014 metadata reconciler → T001 data layer → T002 artist → T003 album → T004 playlists/Loved → T005 library
overview and Songs → T013 Genres → T006 rows → T007 menus → T008 Now Playing → T009 #844 → T010 #847
→ T011 [FINAL]. Library mode is a data source passed to the existing pages (no copy of
the pages); online-only widgets are hidden when the source is the library.

## Complexity Tracking

None.
