# M003 task log

Baseline: `b397464` on `wip` (see plan.md, Repository evidence).

Baseline checks on `b397464`: `flutter analyze` no issues; full `flutter test` exit 0,
0 failed, 10 skipped (3910 passed by the JSON count excluding hidden and `loading`
entries).

## M003-T012

Tag separators for artist and genre credits (FR-015, FR-016, FR-017). Port of
AMWolfstein/Private `new-blur` (`fbd8c4d6` tip; commits `3cec4f8f` .. `6a8c363a`)
by hand, without composer, Album Artists, Appears On or Save with (owner decisions).
Uncommitted; independent review not run (owner instruction); `task complete` not run.

Map refresh (first action, at HEAD `a44317b`): `python3 workflow/codegraph/code_tools.py
update` -> 34310 symbols, 1707 sources, 0 partial, 0 unindexed, 0 rejected cross-language
calls. Map queries on that snapshot: `query --symbol splitArtistNames` (artist_utils.dart
L25, 9 related symbols), `query --symbol MetadataSeparators` (metadata_separators.dart L2),
`query --symbol navigateToArtistCredits` (clickable_metadata.dart L132). Caller searches
with `rg` on `splitArtistNames`, `metadataSeparators`, `writerFields`, `formatDownloadTagFields`,
`navigateToArtist`, `LibraryQueueStore(`, `QueueLibraryDbQuery(`, `albumArtist`, `_separator`.

Files changed:
- New: `lib/models/tag_credit_settings.dart` (Private core model, copied and kept);
  `lib/services/library_tag_credits.dart` (splitter `splitTagCredits`, `TagCreditField`
  artist/album_artist/genre, `TagCreditRules`, `TagCreditScope`, derived TEMP table
  `library_tag_credits` with columns source/row_id/field/name/name_key/position, indexes
  `(field, name_key, source)` and `(source, row_id)`, per-connection cache via Expando);
  `lib/providers/library_tag_credits_provider.dart` (`tagCreditRulesProvider`,
  `tagCreditScopeProvider` = rules + `localLibraryIndexRevision:downloadHistory.loadedIndexVersion`);
  `lib/screens/settings/tag_separator_settings_page.dart` (Private settings page: Artists and
  Genre chip sheets, All, Add separator, Keep together; no Save with).
- Deleted: `lib/models/metadata_separators.dart`, `lib/screens/settings/metadata_separator_settings_page.dart`,
  `test/metadata_separators_test.dart`, `test/metadata_separator_settings_test.dart` (replaced by
  `test/tag_separator_settings_test.dart`), the save-time case of `test/download_metadata_embedding_test.dart`.
- Modified: `lib/models/settings.dart` (`tagCredits` replaces `metadataSeparators`),
  `lib/models/settings.g.dart` (regenerated: `dart run build_runner build --delete-conflicting-outputs`),
  `lib/models/library_database_models.dart` (`QueueLibraryDbQuery.tagCredits`),
  `lib/providers/settings_provider.dart` (Private delimiter/keep-together actions; `setMetadataSeparator` removed),
  `lib/services/library_database.dart` (`_queueRead(credits:)` builds the table before album-artist
  filters and the artist list), `lib/services/library_queue_store.dart` (artist list from credits,
  album-artist filter via credits for local and download rows), `lib/providers/library_browse_provider.dart`,
  `lib/services/media_library_browser.dart` and `lib/services/music_player_runtime.dart` (Android Auto
  artist list passes the rules, scope version null = rebuild), `lib/utils/artist_utils.dart`
  (`splitArtistNames` deleted; `primaryArtistName`, `splitArtistTagValues` use the splitter),
  `lib/controllers/clickable_metadata.dart` (Go to artist splits with the rules; picker unchanged),
  `lib/controllers/player_track_navigation.dart` (Now Playing title uses `navigateToArtistCredits`),
  `lib/providers/music_player_provider.dart` (artist shuffle uses credits), `lib/services/music_player_autoplay.dart`
  and `lib/services/music_player_service.dart` (`_autoplayGenres` uses the splitter, default genre rules),
  `lib/providers/download_queue_policy.dart` and `lib/providers/download_queue_provider.dart` (batch album
  artist credits use the rules), `lib/services/download_metadata_embedding.dart`,
  `lib/services/download_request_payload.dart`, `lib/services/audio_metadata_mapper.dart` (Save with
  writer keys and their readers removed; under default settings the same tags are written),
  `lib/screens/settings/metadata_settings_page.dart`, `lib/utils/settings_search_catalog.dart`,
  21 ARB files (removed `metadataSeparators`, `metadataSeparatorsDescription`, `metadataSeparatorSource`,
  `metadataSeparatorComma/Semicolon/Slash/Custom/Preview` where present; added `tagSeparators*` and
  `tagKeepTogether*`; `metadataSeparatorInvalid` kept, still read). `flutter gen-l10n` run.
- Tests: new `test/library_tag_credits_test.dart` (splitter rules, Keep together, genres, credits
  table rebuild rules, null version), `test/tag_credit_settings_test.dart` (model; old backup with
  `metadataSeparators` still loads), `test/tag_separator_settings_test.dart` (Private settings test at
  390x844 and 844x390); ported: `test/artist_utils_test.dart`, `test/text_and_format_utils_test.dart`,
  `test/artist_credits_navigation_test.dart` (+ Keep together picker case), `test/media_library_browser_test.dart`.
  Pinned SQL/plan tests updated for the intended artist query and index: `test/library_queue_store_sql_test.dart`
  (36 filter digests), `test/library_index_plans_test.dart` (credits key index), with explicit credits priming in
  `test/library_album_filter_test.dart`, `test/library_queue_store_test.dart`, `test/library_shadow_snapshot_test.dart`,
  `test/library_browse_sqlite_paging_test.dart` (direct store callers).

Checks (this session):
- Found and fixed while porting: Private's `_isWord` built its RegExp without `unicode: true`, so `x`
  matched inside words (`Example artist 0` -> `E`, `ample artist 0`). Regression case added; the
  search-results paint fingerprint test (`test/home_search_narrow_layout_test.dart`) failed on that
  and passes now (HEAD baseline checked in a scratch copy of HEAD: pass).
- Credits key index needed `source` for the planner to seek it (planner probe in scratchpad).
- `flutter analyze` (whole project incl. integration_test and test): No issues found (after each change;
  final run after the last edit: No issues found).
- `flutter gen-l10n`: exit 0 (existing ICU plural warnings only).
- `dart format` on changed Dart files: 0 changed.
- Full `flutter test --reporter json` (scratchpad JSON; baseline 3910 passed / 10 skipped by the JSON count
  excluding hidden and loading entries): 3931 passed, 0 failed, 10 skipped (`success: true`, run after the last edit; report `/tmp/claude-0/-home-user-Sistrum/6f2a99b7-2c8e-50d8-b242-f10db4d01b53/scratchpad/full_test4.json`).
- `python3 workflow/work.py validate`: PASS (registry 3 missions, 14 reserved, 29 completed).

Not ported (deliberate):
- Appears On shelf (Private `afeefe70` shelf) and the artist picture rule (`3cec4f8f`): T002 owns the artist page.
- Composer delimiter, Go to composer, Browse by composer, the `composer` search keyword: owner says no composer.
- Album Artists list: owner decision; the Artists section is shared by artist and album artist.
- Save with: `metadataSeparators` model, setters, page, strings, settings-search wording, download
  `metadata_separators` payload key, embedding `*_separator` writer keys and `formatDownloadTagFields`.
- Private master split switch: not in Private's final state (not ported).
- Genre page and genre filter (T013). The Genre section is stored and fills the credits table, but
  no screen reads genre credits until T013.
- Private `metadata_index_reconciler` (`148e103b`): T014.
- Rust writer/downloads readers of `metadata_separators` and `*_separator` (`rust_backend/crates/core/src/downloads.rs`,
  `rust_backend/crates/core/src/tags/write/artists.rs`, `rust_backend/crates/core/tests/artist_tag_mode.rs`):
  untouched (no Rust build was run). They have no Dart producer any more; removing them is a follow-up.

Deviations and behaviour changes to review:
- Credits come from the visible library and the downloads table (Private had only the library), keyed
  by source and row id, because the library artist list and album pages read both.
- Non-library callers use the default rules rather than the settings: `splitArtistTagValues` (listening
  statistics, rewind, mixes, Vorbis artist writer), `primaryArtistName` in search and edit helpers,
  autoplay and mixes genre split. Only the library screens, Go to artist, the artist shuffle, the
  artist list/album filters, and download batch album artists read the settings rules.
- Default delimiters (owner list) change what splits: `x` is a chip, so `Malcolm X` -> `Malcolm`
  unless Keep together lists it; `AC/DC` -> `AC`, `DC`; `featured`/`feature` no longer split (only
  `feat.`, `ft.`, `featuring`, `with`, `x`); `and` no longer splits when artist ids are present (the
  old `creditedArtistCount` rule is gone).
- Table freshness: a scope with a version rebuilds only when the version or rules change. The Android
  Auto browser has no index revision and passes no version, so it rebuilds each time it lists artists.
- `TagCreditSettings.isDefault` removed (no reader after the Save with removal).

Unverified:
- No device or widget check of the settings sheets beyond `test/tag_separator_settings_test.dart`.
- Translations: the 17 new strings are English in all 21 ARB files (es, pt and zh had none of the old keys).
- Rebuild cost on a large library is not measured (the table is rebuilt on index revision or settings change).
- Independent review: not run.

Open owner decisions:
- Accept the default-delimiter effects above (`Malcolm X`, `AC/DC`, `featured`) or add chips/Keep together defaults.
- Whether the Genre section should drive autoplay and mixes now (currently default genre rules) or wait for T013.
- Whether non-library callers should follow the settings rules too (needs plumbing into statistics and the writers).
- Whether to remove the Rust `metadata_separators`/`*_separator` code in a follow-up with a Rust build.
- Translations for the new strings.

## M003-T012 Review round 1

Implementer fix of review round 1 at HEAD `a44317b`, uncommitted. No commit, push, `task complete`
or reviewer run (owner instruction). Supersedes the earlier "Deviations" bullet that said the Vorbis
artist writer used default rules, and the "Not ported" bullet that left the Rust separator readers
for a follow-up.

Fixes:
- D1 Android Auto albums and artist songs ignored the settings rules. `lib/services/media_library_browser.dart`
  `_albumItems` (L343), `_artistSongs` (L463) now pass `tagCredits: TagCreditScope(rules: tagRules())`,
  as the artist list already did (L363). All three lookups use the same rules.
- D2 Split-Vorbis writer used default rules. `lib/utils/artist_utils.dart` `splitArtistTagValues` takes
  `rules:` (L93-97; default kept for listening, rewind, mixes and statistics, which are not writers).
  `lib/services/audio_metadata_mapper.dart` `buildVorbisMetadataEntries`, `appendVorbisMetadataArguments`
  and `_appendVorbisArtistEntries` take a required `tagRules` (L201, L228, L405) and pass it on (L420).
  The rules are threaded through every caller that reaches the FFmpeg Vorbis writer, as required
  parameters so a missed caller fails to compile: `ffmpeg_metadata.dart` `embedMetadata` (L131) and
  `embedMetadataToOpus` (L275); `ffmpeg_conversion.dart` `convertAudioFormat` (L223) and
  `_convertToLossless` (L370); `ffmpeg_reenrich.dart` `applyFfmpegReEnrichResult` (L39);
  `download_metadata_embedding.dart` (writer typedef, constructor, `_embedWithFFmpeg`, call site, from
  `settings.tagCredits`); `metadata_persistence_service.dart` (`MetadataSaveRequest.tagRules`,
  `MetadataEmbed`); `track_metadata_edit_sheet.dart`, `track_metadata_lyrics.dart` (both Opus writes and
  the network save), `track_metadata_convert.dart` and `batch_track_actions.dart` (`readTagRules`),
  `local_track_batch_actions.dart` (`applyFfmpeg` typedef and call). Joined-only callers pass the
  default with a comment: `player_lyrics_persistence.dart` (joined mode never splits) and
  `download_queue_provider_finalization.dart` (empty metadata, nothing is written). The native FLAC
  path (`PlatformBridge.rewriteSplitArtistTags` in `download_metadata_embedding.dart`) is unchanged.
- D3 Save-with readers on the Rust side. Removed: `crates/core/src/tags/write/artists.rs`
  (`tag_separator`; `artist_mode_fields` keeps only primary mode, same output because an empty separator
  was a no-op); `crates/core/src/tags/write.rs` (`*_separator` skip, separator reads in the Vorbis loop
  and in the split condition; split-Vorbis now always splits, which is what an empty separator did);
  `crates/core/src/tags/separators.rs` deleted with its `mod`/`pub use` in `crates/core/src/tags.rs`
  (`valid_separator`, `format_metadata_tag_value`, no other caller); `crates/core/src/downloads.rs`
  (`metadata_separators` field and key); `crates/extensions/src/backend/downloads/metadata.rs`
  (`*_separator` fields from `metadata_separators`).

Tests added or changed:
- `test/media_library_browser_test.dart` "an artist from the artist list finds its albums and songs under
  the settings rules" (L352): Keep together `Tyler, The Creator`, `x` chip off so `Malcolm X` stays whole.
  The fake album query now splits album artists with the request's rules (as the store does), and the test
  asserts the request carries the rules. Without the fix it fails (albums empty).
- `test/maintainability_contracts_test.dart` "split Vorbis artists follow the rules passed in, not the
  defaults" (L305): `AC/DC` under default delimiters -> `AC`, `DC`; with `/` off -> `AC/DC`; a custom `|`
  rule splits `Band | Guest`, the default rules do not. Without the fix it fails (`Expected: ['AC/DC']`,
  `Actual: ['AC', 'DC']`).
- `test/download_metadata_embedding_test.dart` "the FFmpeg fallback writes with the settings tag rules"
  (L280): the writer receives the rules derived from the settings.
- `rust_backend/crates/core/tests/artist_tag_mode.rs`: the three separator tests are replaced by
  `split_vorbis_writers_keep_each_value_across_formats`, `download_embed_splits_artists_by_mode` and
  `legacy_metadata_separators_in_a_payload_are_ignored` (an old payload key is ignored).
- Existing tests updated for the new parameters (no expectation changed): `ffmpeg_opus_metadata_test.dart`,
  `local_track_batch_actions_test.dart`, `network_batch_re_enrich_test.dart`, `metadata_persistence_service_test.dart`,
  `network_file_editor_test.dart`, `library_track_conversion_test.dart`.

Checks (this round):
- `flutter analyze` (project, incl. tests and integration_test): No issues found.
- `dart format --set-exit-if-changed` on all changed Dart files: exit 0 (6 files reformatted before the run).
- `flutter gen-l10n`: not run (no ARB change this round).
- Full `flutter test --reporter json`: success true, 3941 passed, 0 failed, 10 skipped. JSON:
  `/tmp/claude-0/-home-user-Sistrum/6f2a99b7-2c8e-50d8-b242-f10db4d01b53/scratchpad/review1_full.json`.
- `cargo test --release -p sistrum-core -p sistrum-extensions --offline`: exit 0, every target 0 failed
  (core lib 146, `artist_tag_mode` 6, others pass). `--release` reused the existing `rust_backend/target/release`
  artifacts, so no debug build and no new disk pressure.
- `rustfmt --check` on the changed Rust files: the only diff is the import order in
  `crates/extensions/src/backend/downloads/metadata.rs` (lines 2 and 8), which HEAD already has (checked
  against `git show HEAD:`), so not from this change. Not reformatted.
- `python3 workflow/work.py validate`: PASS (3 missions, 14 reserved, 29 completed).

Not verified:
- Android Kotlin: removed in F1 (see "Review round 1" below). Not built or run (Gradle not run).
- Rules propagation is compile-enforced at every writer caller, and tested only for the download fallback;
  conversion, re-enrich, persistence and lyrics callers have no behaviour test of the rules.
- No device, Android Auto or widget run. `cargo clippy` not run. Rebuild cost (O6) not measured.

F1: dead Kotlin reader removed. The `metadata_separators` payload key is no longer sent by anything, so
`android/app/src/main/kotlin/com/amwolfstein/sistrum/NativeFinalizerMedia.kt`:
- removed the `metadataTagValue` import and `val separators = input.request.optJSONObject("metadata_separators") ?: JSONObject()`;
- ffmpeg fallback `metadataPairs` now uses `"artist" to artistTagValue(artist, artistTagMode)`,
  `"album_artist" to artistTagValue(albumArtist, artistTagMode)`, `"composer" to composer`, `"genre" to genre`.
- `JSONObject` import kept (still used by the native `fields` builder).

`NativeFinalizationPolicy.kt`: `metadataListSeparator` and `metadataTagValue` deleted (no other caller in `android/`).
`NativeFinalizationPolicyTest.kt`: `metadataSeparatorsPreserveNamesAndSourceDefaults` deleted with them.
Not built (Gradle not run); edited regions re-read for brace balance and unused imports.

Open owner decisions (decisions, not defects):
- O1 Default artist delimiters split `Malcolm X` -> `Malcolm` and `AC/DC` -> `AC`, `DC`. Accept and document,
  or drop `x` and `/` from the defaults (then Keep together covers the exceptions).
- O2 `and` no longer splits when artist IDs are present (the old `creditedArtistCount` rule is gone). The
  composite-ID case is `lib/controllers/clickable_metadata.dart` L750-775: positional id mapping for a
  multi-name tag. Decide whether a tag with ids should still split on `and`.
- O3 Autoplay and mixes genres use the default genre rules (`music_player_autoplay.dart`,
  `music_player_service.dart`), not the settings. Decide now or with T013.
- O4 The settings page is a summary list (Private design). The acceptance text mentions a preview, which is
  not built. Decide whether the preview is in scope for M003-T012.
- O5 The 17 new strings are English in all 21 ARB files. Decide on translations.
- O6 The rebuild cost is unmeasured. The credits table is rebuilt on each download-history version bump
  (and on every Android Auto artist list, which has no version). Measure on a large library before release.
- O7 (new) Kotlin `metadata_separators` reader and its test: removed under F1 (no follow-up needed).

## M003-T014

Refresh the library row after a metadata save (FR-019). Port of Private `148e103b`
(`MetadataIndexReconciler`), adapted by hand. Base `2399ca6`; uncommitted until completion.

Map: refreshed first at `2399ca6` (34431 symbols, 1709 sources, 0 partial, 0 unindexed) and again on the
final tree for the evidence below (`/tmp/claude-0/-home-user-Sistrum/6f2a99b7-2c8e-50d8-b242-f10db4d01b53/scratchpad/t014/final/map_update.json`: 34475 symbols, 1712 sources, 0 partial,
0 unindexed, 0 rejected).

What changed:
- New `lib/services/metadata_index_reconciler.dart`: `reconcile(item, {tags})` refuses CUE virtual paths;
  uses the screen's tags or rereads the file; a reader error map or a failed read is stale `read`; maps the
  tags onto the row (identity and audio facts kept), extracts the cover (a failed extraction keeps the old
  one), upserts with `upsertBatchExcludingHistory`; a failed upsert, or a row skipped because Download
  History owns it, is stale `database`.
- `lib/screens/track_metadata_actions.dart`: a local save reconciles its row with the tags the screen
  already read; the snackbar comes after the reconcile and says `snackbarMetadataSavedIndexStale` when the
  index is stale; a stale index is logged at warning level. `_syncDownloadHistoryMetadata` still runs for
  download rows and network rows, as at HEAD; local non-network rows skip it. Network rows keep their own
  `_syncNetworkLibraryMetadata` path.
- `lib/screens/track_metadata_screen.dart`: Edit metadata (and so Embed lyrics) is hidden for CUE virtual
  tracks; Share file refuses them with the same check.
- 21 ARB files: `snackbarMetadataSavedIndexStale`, English in all (no translations supplied).
- Tests: `test/metadata_index_reconciler_test.dart` (9), `test/track_metadata_cue_edit_test.dart` (2),
  `test/library_loading_test.dart` (reload bumps the index revision and the credits scope version, so the
  `library_tag_credits` table is rebuilt on the next query).

Checks on the final tree (saved under `/tmp/claude-0/-home-user-Sistrum/6f2a99b7-2c8e-50d8-b242-f10db4d01b53/scratchpad/t014/final/`):
- `flutter analyze`: No issues found, exit 0 (`analyze.log`).
- `dart format --set-exit-if-changed` on the six changed Dart files: exit 0 (`format.log`).
- Full `flutter test --reporter json`: exit 0; 3953 passed (7 of them `loading` entries, 3946 without),
  10 skipped, 0 failed (`full.json`, `full_counts.txt`). The three task test files: 9, 2 and 18 passed
  (`targeted_counts.txt`).
- `python3 workflow/work.py validate`: PASS (`validate.json`).

Not verified: the edit sheet save flow has no widget harness, so the network sync condition and the warning
log are checked by reading only; no device run, no APK.

Owner decisions (open):
- A cover extracted before a skipped or failed upsert stays on disk until the next scan prunes it.
- A save that finishes during a library load may be re-queried only at the next load (existing reload rule).
- A stale local save writes neither the new row nor explicit/hasLyrics.
- When the screen's reread fails, the stale snackbar can take about 2 s (two retry chains).
- Embed lyrics is hidden for CUE virtual tracks.
- Translations of the new string.

Review: rounds 1 and 2 fixed code and test findings; the owner authorized extra rounds after round 3, and
those rounds found record-text issues only. This section was rewritten from the saved evidence above.

## M003-T001

Library data for the detail screens (data only, no UI). Base `ea46941`.

Map: refreshed first at `ea46941` (`/tmp/claude-0/-home-user-Sistrum/6f2a99b7-2c8e-50d8-b242-f10db4d01b53/scratchpad/t001/map_start.log`: 34475 symbols, 1712 sources, 0 partial,
0 unindexed) and on the final tree (`/tmp/claude-0/-home-user-Sistrum/6f2a99b7-2c8e-50d8-b242-f10db4d01b53/scratchpad/t001/map_update.json`: 34560 symbols, 1717 sources, 0 partial,
0 unindexed, 0 rejected).

What changed:
- `QueueLibraryDbQuery.artistCredit` (`LibraryArtistCredit`: credited, albumArtist, featured) and the role
  filter in `library_queue_store.dart`; the default (credited) keeps the existing SQL.
- `HistoryDatabase.getAlbumTracksByKey`; `readPlaysForTracks` in `listening_statistics.dart` (all-time
  plays per track key, chunked).
- `lib/utils/release_type_utils.dart` `isSingleRelease`, moved out of the downloader (which now calls it).
- `lib/services/library_detail_data.dart` and `lib/providers/library_detail_provider.dart`:
  - `libraryArtistOverviewProvider`: albums and singles where the artist is album artist, Latest Release
    (newest release date; undated never qualifies) and the artist image (its cover). Used by T002.
  - `libraryArtistFeaturedTracksProvider`: tracks where the artist is a track artist but not the album
    artist (Compilations). Used by T002.
  - `libraryArtistPopularTracksProvider`: most played (statistics on), filled at random to 5. Used by T002.
  - `libraryAlbumTracksProvider`: an album's tracks by album key, downloaded and scanned. Used by T003.
  - `libraryPlaylistTracksProvider`, `libraryLovedTracksProvider`: existing collection rows. Used by T004.
- `LibraryDatabase.getQueueLocalAlbumTracksByKey` drops scanned songs that a download shadows
  (`libraryNotShadowedSql`, as the queue pages do), so a downloaded file that is also scanned is listed once.
- Album tracks take `includeLocal`; `libraryAlbumTracksProvider` watches `localLibraryEnabled`.
- Tests: `test/library_detail_data_test.dart` (17 tests), `test/release_type_utils_test.dart` (4 tests).

Checks on the final tree (saved under `/tmp/claude-0/-home-user-Sistrum/6f2a99b7-2c8e-50d8-b242-f10db4d01b53/scratchpad/t001/`):
- `flutter analyze lib test integration_test`: No issues found, exit 0 (`analyze.log`).
- `dart format --set-exit-if-changed --output=none` on the 12 changed Dart files: exit 0 (`format.log`).
- `flutter test --reporter expanded` on the two task test files and `test/library_queue_store_sql_test.dart` (pinned SQL): 22 passed, exit 0 (`targeted.log`).
- Full `flutter test --reporter json`: exit 0 (`full.exit`); 3974 passed (7 `loading` entries, 3967 without), 10 skipped, 0 failed; 3984 non-hidden `testDone` (`full.json`, `full_counts.txt`).
- `python3 workflow/codegraph/code_tools.py update`: 34560 symbols, 1717 sources, 0 partial, 0 unindexed (`map_update.json`).
- `python3 workflow/work.py validate`: PASS (`validate.json`).

Not verified: no UI uses these providers yet (T002 to T004); no device run.

Open decisions (owner):
- Popular shows 5 tracks; Latest Release counts singles and EPs as well as albums (by release date).
- Plays are all-time and matched by file path.
- Downloaded and scanned copies of one album key are merged into one album.
- Scanned album covers are local file paths (T002 must show local paths).
- Playlist and Loved providers are not refreshed by collection edits (T004 handles refresh).
- Library tracks mapped to `Track` drop album type, total tracks and genre.

## M003-T002

Artist screen reads the library (FR-001, FR-002, FR-009, FR-023). Base `e8e659c`.
Uncommitted; independent review not run; `task complete` not run.

Map: refreshed first (`scratchpad/t002/map_start.json`: 34560 symbols, 1717 sources, 0 partial,
0 unindexed) and at the end (`map_update.json`: 34616 symbols, 1719 sources, 0 partial, 0 unindexed).

What changed:
- Release type (owner rule, replaces T001's owned-count rule): `libraryReleaseKind` in
  `lib/utils/release_type_utils.dart` (enum single/ep/album). Total tag 1-3 Single, 4-6 EP,
  7+ Album; no tag: name ending " - Single" / " - EP", else Album. `isSingleRelease` deleted.
  The downloader's `_shouldTreatAsSingleRelease` is restored to its pre-T001 body (identical
  to `ea46941`), so the downloader rule is unchanged.
- `library_queue_store.dart`: album aggregate selects `MAX(total_tracks)` (history and local).
  `library_detail_data.dart`: `ArtistAlbum.totalTracks` is the tag; `downloadedAlbumKeys` added to
  the overview; `loadLibraryArtistTracks` (all credited songs) shares `_creditedSongs` with Popular.
- `ArtistScreen(library: true)`: `artist_screen.dart`, new part `artist_screen_library.dart`,
  header/card branches in `artist_screen_widgets.dart`. Header image = imageCover, play button
  replaces download, Shuffle and favourite stay; Latest Release (play / open); Popular rows (tap
  plays); Albums; Singles & EPs; Compilations (horizontal track cards, tap plays). Empty sections
  hidden. No Releases, listeners, share, discography download, selection or concerts.
- `ArtistFeaturedAlbumCard`: `onDownload` / `onPlay`, exactly one.
- Entry points: `openLibraryArtist` (`clickable_metadata.dart`) used by Go to artist
  (`navigateToArtistCredits`, `extensionId` removed), the Artists list and the search artist rows
  (`mornye_library_screen.dart`, `queue_tab.dart`). The artist-filtered library page is gone
  (`artist` removed from the library screen and from `LibraryBrowseRequest`).
- Strings: none removed (no ARB change).

Open decisions:
- tasks.md's note "duration only when the whole release is owned" is not implemented; the owner's
  rule in the brief is the numeric tag rule. Owner to confirm.
- Album tap on the library artist page is interim: `DownloadedAlbumScreen` or `LocalAlbumScreen`
  by `downloadedAlbumKeys`. T003 replaces it and removes `downloadedAlbumKeys`.
- `ClickableArtistName` on library rows still uses the online `navigateToArtist` lookup; only the
  three listed entry points changed. Revisit with T006/T007 or TOR014.
- Library artist favourite star keys by name with the default provider id (existing semantics).
- Recently visited skips library artists (intentional).
- Popular shows five rows without paging in library mode; online keeps its pager.
- Downloader release rule has no direct unit test (none existed before T001).
- No device or screenshot check.

Checks (saved under scratchpad/t002/):
- `analyze.log`: `flutter analyze lib test integration_test`, No issues found, exit 0.
- `format.log`: `dart format --set-exit-if-changed` on 26 changed Dart files, 0 changed, exit 0.
- `targeted.log`: task test files, 80 passed, 1 skipped, 0 failed (before the final digest update).
- `library_artist_screen_test.dart` 7 widget tests pass; `release_type_utils_test.dart` and
  `library_detail_data_test.dart` pass; SQL digest pins updated in `library_queue_store_sql_test.dart`
  (51) and `library_album_filter_test.dart` (19), both pass.
- `full.json`, `full.exit` (0), `full_counts.txt`: full `flutter test --reporter json` on the final
  tree: 3974 passed excluding `loading` entries (7 loading, all passed), 10 skipped, 0 failed.
  The T001 baseline on the same count basis was 3967 (3974 including loading).
- `validate.json`: `python3 workflow/work.py validate`: PASS (3 missions, 13 reserved, 32 completed).
- `map_update.json`: 34616 symbols, 1719 sources, 0 partial, 0 unindexed.

## M003-T003

Album screen reads the library (FR-003, FR-013, FR-014). Base `d695ac2`.
Uncommitted; independent review not run; `task complete` not run.

Map: `scratchpad/t003/map_start.json` (34616 symbols, 1719 sources, 0 partial, 0 unindexed); end `map_update.json` (34551, 1719, 0, 0).

What changed:
- Data: `loadLibraryAlbumItems` returns `UnifiedLibraryItem`s (files kept for offline actions); `libraryAlbumTracksProvider` maps them to Track for T002. New `libraryAlbumItemsProvider` (watches the history index like the old provider); `libraryAlbumTracksProvider` derives from it, so T002 is unchanged.
- `AlbumScreen.library(albumKey:)`: `album_screen.dart` plus part `album_screen_library.dart`. The online path only gained null guards.
- Header: cover, title, artist, year, count, minutes, quality from the tracks. `CollectionHeaderActions.playActions` replaces the download pill; `AlbumPlayActions(compact: true)` (sized Play pill, round Shuffle) sits beside Love and Add to playlist.
- Rows: `AlbumTrackTile`; offline selection (`LibraryTrackSelectionBar` + `LibrarySelectionPlaybackActions`).
- `playableFromUnified` moved to `playable_media.dart`; `PlaybackController.playLibraryQueue`.
- Entry points: `mornye_library_screen` (`_openAlbum`), `queue_tab_navigation` (2), `player_track_navigation`, `home_tab_recent`, `library_search_results`, `artist_screen_library`. `downloadedAlbumKeys` removed from `LibraryArtistOverview`.
- Go to album: `localAlbumFor` returns the playing row (it was null when every scanned copy was shadowed, so the online page opened); the page opens by its album key.
- Deleted: `downloaded_album_screen.dart`, `local_album_screen.dart`, `downloadedAlbumTracksProvider`, `DownloadedAlbumTracksRequest`, string `downloadedAlbumDownloadedCount` (21 ARBs, gen-l10n), two names in the `design_system_test` screen list.

Action table (DA = DownloadedAlbumScreen, LA = LocalAlbumScreen):

| Action | Old | Library page |
|---|---|---|
| Play, Shuffle (header) | DA, LA | kept, compact |
| Play/pause current row, row highlight | DA, LA | kept |
| Save offline (header, selection) | DA, LA | kept |
| Network upload (header, selection) | DA, LA | kept |
| Row tap: metadata, prev/next, cover refresh | DA, LA | kept |
| Selection: play, play next, queue, add to playlist | DA, LA | kept |
| Selection: re-enrich, convert, ReplayGain, delete | DA, LA | kept |
| Disc separators, select all | DA, LA | kept |
| Cover (embedded or folder), motion banner | DA, LA | kept |
| Header meta: count, minutes, quality | DA, LA | kept, year added |
| Cue tracks: snackbar, shuffle skips, delete removes row | LA | kept |
| Format label on rows | LA | kept |
| Loading indicator, Retry | LA | kept; skeleton and header before data dropped |
| Collection suggestions (extension lookups) | DA, LA | dropped, online |
| Queue selected as FLAC (provider download) | LA | dropped, online |
| Download (online header and selection) | online | dropped |
| Open in other services, share link, quality picker | online | dropped |
| Love, Add to playlist (header) | online | kept |

Open decisions (owner):
- Save offline and network upload are kept as offline actions (no internet needed). Confirm.
- Until data arrives the page shows only the loading indicator, because the title comes from the tracks.
- Metadata prev/next stays within one source (downloaded or scanned) in a mixed album.
- The cover comes from the first song: embedded art for downloads, folder art for scanned.
- `libraryAlbumTracksProvider` (Track form) stays for T002 in `artist_screen_library.dart`; `loadLibraryAlbumTracks` was deleted, its tests now call `loadLibraryAlbumItems`.

Tests:
- Ported, behaviour kept: `downloaded_album_now_playing_test`, `downloaded_album_reenrich_test`, `local_album_playback_controls_test`, `local_album_metadata_navigation_test`, `album_replaygain_action_test`, `collapsing_title_rebuild_test`, `local_library_artwork_test`, `motion_header_banner_test`.
- Ported, expectation changed: `detail_route_loading_test` (entrance shows loading, then rows; Retry kept).
- Deleted tests: none.
- New: `test/library_album_screen_test.dart`: compact Play and Shuffle, Love and Add to playlist present, no Download; rows show number, title and artist; selection bar offline only; entry points (source scan of the six entry files and `artist_screen.dart`, no old names).
- Shadowed duplicate listed once: T001 `test/library_detail_data_test.dart` "a downloaded file that is also scanned is listed once" (data layer). The album loader tests there now call `loadLibraryAlbumItems`; `loadLibraryAlbumTracks` was deleted (no production caller).

Checks (`scratchpad/t003/`):
- `gen_l10n.log`: `flutter gen-l10n` exit 0.
- `analyze.log`: `flutter analyze lib test integration_test`: no issues, exit 0.
- `format.log`: `dart format --set-exit-if-changed` on the 32 changed Dart files (`changed_dart_files.txt`): 0 changed, exit 0.
- `targeted.log`: task-related test files, the 163 tests reported (incl. `library_detail_data_test.dart`): all passed, exit 0.
- `full.json`, `full.exit` (0), `full_counts.txt`: 3985 passed including the 7 `loading` entries (all pass), 10 skipped, 0 failed. Excluding `loading`: 3978; T002 logged 3974, so +4 = the new file.
- `validate.json`: PASS (3 missions, 12 reserved, 33 completed; T002 logged 13 and 32 when it ran).

## M003-T004

Playlists and Loved songs use the playlist design (FR-004). Base `baa7749`.
Uncommitted; independent review not run; `task complete` not run.

Map: refreshed first (`scratchpad/t004/map_start.json`: 34551 symbols, 1719 sources, 0 partial, 0 unindexed); end `map_update.json` (34654, 1723, 0, 0).

What changed:
- `PlaylistScreen.libraryPlaylist(libraryPlaylistId:)` and `PlaylistScreen.libraryLoved()`; body in part `playlist_screen_library.dart`. Extension path untouched.
- Header: cover, title, count; `CollectionHeaderActions` with compact Play and Shuffle, Love all, Add to playlist; no Download. Appbar: save offline, export M3U8, edit order, rename and cover (playlist only).
- Rows: `AlbumTrackTile` (number, title, artist). Tap opens metadata; long-press selects; play button plays from that row.
- Shared: `lib/controllers/collection_order_editing.dart` (reorder mixin), `lib/widgets/collection_track_actions.dart` (selection bar, `CollectionTrackActions` mixin, M3U export, play/shuffle, metadata open, empty state, cover helpers).
- `LibraryTracksFolderScreen` is wishlist-only (no mode/playlistId). `LibraryTracksFolderMode.playlist` removed.
- T001 providers now return keyed entries: `libraryPlaylistEntriesProvider`, `libraryLovedEntriesProvider`; loaders `loadPlaylistEntries`/`loadLovedEntries` added.
- Refresh: `ref.listen` on the collection state reloads rows after any edit, including edits from other screens.
- Entry points: `queue_tab_navigation.dart`, `library_search_results.dart`. Home recents need no change (library playlists are never recorded as recents).
- `AlbumPlayActions` compact wrapped in `FittedBox` (shared with T003) to stop Arabic overflow.
- Strings: no ARB change; every key the removed code used still has a reader. Loved removal label is now `trackOptionRemoveFromLoved`.

Action table (old library playlist/Loved folder -> new page):

| Action | Result |
|---|---|
| Play, Shuffle (header) | kept, compact |
| Download All (header) | dropped, online download |
| Love all, Add to playlist (header) | added (album design) |
| Save offline (appbar) | kept |
| Export M3U8 (appbar) | kept |
| Rename playlist, change/remove cover (playlist) | kept |
| Edit order, drag, discard, save | kept; back during Edit order asks to discard a changed order and stays, as the Wishlist does (an unchanged order leaves edit mode) |
| Row tap: metadata; long-press: selection | kept |
| Row play button | kept, now always shown (was only for in-library tracks) |
| In-library badge on rows | dropped (album row design) |
| Selection: play, play next, add to queue | kept |
| Selection: add to playlist | kept |
| Selection: remove from playlist / Loved | kept (Loved label "Remove from Loved") |
| Selection: delete files | kept |
| Selection: download | not on these lists (wishlist only, unchanged) |
| Empty states | kept |
| Extension suggestions (playlist) | dropped, online |

Deleted tests:
- `test/library_tracks_folder_data_test.dart` "newest-first seeds track additions, edits, order and empty lists" (suggestion order, dropped).
- Its newestFirst assertions removed from "unchanged collection snapshot does no additional full-list reads" (kept).

Ported tests (behaviour kept): `collection_reorder_ui_test` (Loved), `liked_local_tracks_test`, `collapsing_title_rebuild_test` (library playlist), `playlist_playback_test` (per-list removal loop; header and row expectations), `library_detail_data_test` (entry types).

New: `test/library_playlist_screen_test.dart`, 13 tests: header design and no Download, Loved design, empty states, rename, cover sheet, reorder, removal refresh for playlist and Loved, outside-edit refresh, back during Edit order (changed order shows the discard dialog and stays; unchanged order leaves edit mode), Wishlist still opens.

Open decisions (owner):
1. Rows use the album design (number, title, artist) rather than FR-007's small cover. T006 applies FR-007 to these pages; owner to confirm.
2. Download All dropped (online). Save offline kept. Extension suggestions dropped (online).
3. Love all and Add to playlist added to the header. On Loved, Love all toggles removal.
4. Row play button always shown; in-library badge removed.
5. Loved keeps the heart fallback cover (old behaviour).
6. T001 providers renamed and retyped to keyed entries (remove and reorder need the keys).
7. `AlbumPlayActions` compact `FittedBox` also changes the T003 album page.

Checks (`scratchpad/t004/`):
- `analyze.log`: `flutter analyze lib test integration_test`, No issues found, exit 0.
- `format.log`: `dart format --set-exit-if-changed --output=none` on the 21 changed Dart files (`changed_dart_files.txt`), 0 changed, exit 0. `format_write.log` and `format_check_before.log` record the formatting run.
- `targeted.log`: 21 task and related test files, 147 passed, exit 0.
- `full.json`, `full.exit` (0), `full_counts.txt`: 3997 passed (including 7 `loading` entries, all passed), 10 skipped, 0 failed; 4007 non-hidden testDone. Baseline 3985 passed incl 7 loading. Difference +12 = 13 new tests minus 1 deleted.
- `validate.json`: `python3 workflow/work.py validate`: PASS (3 missions, 11 reserved, 34 completed).
- `map_update.json`: 34654 symbols, 1723 sources, 0 partial, 0 unindexed.
- gen-l10n: no ARB change, no log.

## M003-T005

Library overview and Songs page (FR-005, FR-006, FR-014). Base `b242ea8`.
Uncommitted; independent review not run; `task complete` not run (tasks.md checkbox left for it).

Map: refreshed first (`scratchpad/t005/map_start.json`: 34095 symbols, 1702 sources, 0 partial, 0 unindexed); end `map_update.json` (34122, 1701, 0, 0).

What changed:
- Overview (`MornyeLibraryScreen`, overview page): the Recently Added header and album grid are gone. The overview no longer reads `libraryBrowseProvider`, so it shows no empty-collection text and its pull-to-refresh does nothing. The sort option "Recently Added" on the Albums page stays (`libraryRecentlyAdded` still read there).
- Overview now shows: the Open audio, search and Downloads actions; category rows Playlists, Artists, Albums, Songs, Folders; the Rewind banner when listening statistics are on. Albums are opened from the Albums row.
- Songs (`QueueTab`, section `all`): Play and Shuffle (`AlbumPlayActions`, non-compact, as Folders) sit above the count row, shown when the list has tracks. Play sets shuffle off and plays from the top; Shuffle sets it on and starts at a random track. Both use the list as filtered, sorted and searched. If the list is larger than the loaded pages, the full filtered list is read in one query (`_QueueLibraryPageRequest.unpaged`); otherwise the loaded list plays.
- Songs header: Create playlist removed. Playlists page Create playlist kept (its only way to create a playlist; the Songs header was not one).
- Drag-to-playlist removed: `LibraryPlaylistDragSource`, `_selectDraggedTrack`, `_startLibraryTrackDrag`, `_endLibraryTrackDrag`, `_buildDragFeedback`, `_isDraggingLibraryTrack`, `playlistDragEnabled`. Songs rows now use the normal long-press selection.
- `_playLibraryItem` became `_playLibraryItems(libraryItems, {start, shuffle})`; row play is unchanged.

Deleted (no reader left):
- `lib/widgets/library_playlist_drag_source.dart`.
- `test/library_playlist_drag_test.dart` (2 tests, list and grid: hold selects, drop on playlist). Behaviour removed, not ported. Plain long-press selection is not covered by this file; existing selection tests cover it.
- Strings: none. No ARB change, no gen-l10n run (`collectionCreatePlaylist` is read by Playlists; `libraryRecentlyAdded` by the sort chooser).

Tests:
- Changed `test/mornye_library_browse_test.dart` "Library separates browsing and downloads": overview asserts no Recently Added, no `Album 0`, no grid (was: header and 2-column grid).
- New in that file: "Albums sort still offers Recently Added".
- New `test/library_songs_page_test.dart` (4): Play and Shuffle above the count; Play and Shuffle play the list in order (shuffle flag, queue titles, start index); no Create playlist and no `LongPressDraggable` on Songs; Playlists keeps Create playlist and shows no Songs Play or Shuffle.

Open decisions (owner):
1. Overview is categories only, since the album grid was its only content under that header. An empty library shows no message on the overview (the Albums page still does).
2. Full-list Play on a library with unloaded pages is untested by widget tests (the fixture has no DB rows); it is covered by analyze and the full run only.
3. Shuffle is the labelled pill of AlbumPlayActions (as in Folders), so it has no tooltip; the test finds it by label.
4. Songs long-press now selects a row (drag owned it before).

Checks (`scratchpad/t005/`):
- `analyze.log`: `flutter analyze lib test integration_test`: No issues found, exit 0 (after removing two unused imports from the new test).
- `format.log`: `dart format --set-exit-if-changed --output=none` on the 9 changed Dart files (`changed_dart_files.txt`): 0 changed, exit 0. `format_write.log` is the formatting run.
- `targeted.log`: 16 related test files (library, queue, collection and playback tests): 97 passed, exit 0.
- `full.json`, `full.exit` (0), `full_counts.txt`: 3936 passed excluding the 7 `loading` entries (all passed), 10 skipped, 0 failed; 3953 non-hidden testDone. Baseline 3933, plus 5 new, minus 2 deleted = 3936.
- `validate.json`: `python3 workflow/work.py validate`: PASS (3 missions, 9 reserved, 36 completed).

## M003-TOR014

Online versions of the converted screens removed (FR-020, FR-014). Base `ac2162f`.
Uncommitted; independent review not run; `task complete` not run.

Map: start `scratchpad/tor014/map_start.json` (34654 symbols, 1723 sources, 0 partial); end `map_update.json` (34095, 1702, 0, 0).

Deleted (files): `lib/screens/extension_collection_screens.dart` (ExtensionArtist/Album/PlaylistScreen),
`artist_screen_discography.dart`, `artist_concerts_screen.dart`, `concert_detail_screen.dart`,
`widgets/artist_concerts_button.dart`, `services/concert_calendar_service.dart`, `utils/ttl_cache.dart`,
`widgets/album_description.dart`, `widgets/collection_suggestions_section.dart`,
`widgets/cross_extension_share_sheet.dart`, `widgets/track_list_tile.dart`,
`providers/collection_suggestions_provider.dart`, `services/collection_suggestions.dart`,
`services/cross_extension_share_service.dart`, `docs/artist_concerts.md`.
Review-fix deletions (dead after this change): `track_detail_actions.dart` `buildTrackListFooter` and
`formatReleaseDate` (and its adaptive_layout import); `animation_utils.dart` `SkeletonCrossfade` and
`AlbumTrackListSkeleton` (two test uses removed; other assertions kept); `artist_concert.dart` `ConcertDetail`.
No ARB key became unused (lost-key scan: 0).
Rewritten to library-only: `ArtistScreen({artistName})` (library flag removed); `AlbumScreen.library`
and `PlaylistScreen.libraryPlaylist/libraryLoved` kept as the only constructors (named constructors kept).
Trimmed: `mornye_artist_header.dart` (logoUrl, listeners, badge, logoSource removed; online-only).
Strings: 40 ARB keys with no reader removed from all 21 ARBs; `flutter gen-l10n` run.

Entry points: `navigateToArtist`, `navigateToAlbum`, `navigateToTrackAlbum`, `navigateToArtistCredits`
(Now Playing, track menus, Go to artist) open library pages. Lookups: `libraryHasArtist`,
`libraryAlbumKeyFor` (`services/library_detail_data.dart`, database reads only). Not in library: message
"{type} information not available" (existing string), no page, no backend call. `ClickableAlbumName`
deleted (no readers); `ClickableArtistName` takes the name only. Favourite artists open the library page.

Home/Search call sites left for TOR015 (removed navigation; surfaces otherwise intact):
- `home_tab.dart`: `_navigateToDetailIfNeeded` removed with its two calls; export/import of extension screens removed.
- `home_tab_recent.dart`: `_navigateToRecentItem`: artist and playlist taps inert; album only the library branch.
- `home_tab_intro.dart`: extension playlist card has no tap (`_card` onTap optional).
- `home_tab_explore.dart`: album/playlist/artist taps and Go to album inert; `_showMissingExploreProviderMessage` removed.
- `home_tab_search_results.dart`: `_navigateToExtension{Album,Playlist,Artist}`, `_extensionSourceOrWarn` removed; rows not tappable.
- `home_tab_search_wide.dart`: top album/playlist cards and cover cells not tappable.
- `home_tab_widgets.dart`: `_CollectionItemWidget` tap removed.
Also left for TOR015 (no page reader now): `TrackState` URL-fetch artist/album fields and their writes in
`track_provider.dart`; `models/artist_concert.dart` (read by TrackState); `RecentAccess` store and
`recordArtist/Album/PlaylistAccess` (no lib writer).

Deleted tests (online behaviour): `artist_concerts_test.dart` (8), `concert_detail_screen_test.dart` (3),
`album_description_test.dart` (4), `collection_suggestions_widget_test.dart` (1), `ttl_cache_test.dart` (3),
`collection_suggestions_test.dart` (18), `artist_album_selection_test.dart` (2), `detail_page_navigation_test.dart` (3);
single cases in `collection_network_cover_test` ("Go to album leaves a network cover behind"),
`track_list_tile_selection_test` (2 row tests), `collection_track_rebuild_test` ("queue progress ..."
and the playlist variant of "Love All isolates"), `collapsing_title_rebuild_test` (album, playlist),
`album_track_metadata_test` (online cases), `collection_track_metadata_test` (online entries),
`mornye_detail_headers_test` (2 logo cases), `home_search_tabs_test` and `home_search_wide_layout_test`
(guard assertions for removed taps).

Ported tests: `collection_selection_bar_test` (library album and playlist select-all); `album_track_metadata_test`
(disc groups, library album); `track_list_tile_selection_test` (navbar case, library album); `collection_track_rebuild_test`
(Love All isolation, library album); `collapsing_title_rebuild_test` (artist to library artist);
`collection_track_metadata_test` (2 unit tests of `UnifiedLibraryItem.toTrack`); `mornye_detail_headers_test`
(artwork accents kept); `library_artist_screen_test` (constructor); `design_system_test` (source checks on library parts).
New: `test/library_navigation_test.dart` (6 tests: lookups; Go to artist/album open library pages; missing artist/album
message and no backend call; track without album name).

Open decisions:
- FR-013 conflict: the owner's TOR014 request removes the Apple Music pages now; FR-013 updated in spec.md.
- Album lookup by name: when titles match but no artist matches, the first title match is used.
- Library headers and album cards drop the disabled DownloadableCover wrapper (no visual change; "Download cover" semantics label goes).
- Library tracks never carried genre/label/copyright/quality; those online-only checks are dropped.
- Library playlist "Love All isolates the page" not ported (Loved page reloads rows by design).
- Pre-existing orphans left alone: `lib/widgets/lastfm_settings.dart`, `lib/widgets/pinned_section_sliver_list.dart`.
- `tasks.md` checkbox left open (no `task complete`).

Checks (`scratchpad/tor014/`):
- `map_start.json`, `map_update.json`: ok; `gen_l10n.log`: exit 0 (pre-existing ICU warnings in ar ARB).
- `analyze.log`: `flutter analyze lib test integration_test`: No issues found.
- `format.log`: `dart format --set-exit-if-changed --output=none` on `changed_dart_files.txt` (41 files): exit 0.
- `targeted.log`: `loading_layout_regression_test`, `mornye_loading_and_chrome_test`, `library_navigation_test`, `track_list_tile_selection_test`: All tests passed (17).
- `full.json`, `full.exit` (0), `full_counts.txt`: 3933 passed excluding loading (3950 non-hidden testDone; 7 loading entries, all passed), 10 skipped, 0 failed.
- `validate.json`: PASS (3 missions, 10 reserved, 35 completed).

## M003-T013

Library Genres page (FR-018). Base `0eb2df9`. Uncommitted; independent review not run;
`task complete` not run (tasks.md checkbox left for it).

Map: refreshed first (`scratchpad/t013/map_start.json`: 34122 symbols, 1701 sources, 0 partial,
0 unindexed); end `map_update.json` (34227, 1705, 0, 0, 0 rejected).

What changed:
- Overview: a Genres row after Songs (`Icons.category_outlined`) opens the genres page.
- Genres page (`lib/screens/library_genres_screen.dart`): one row per genre from the genre
  credits, sorted by name, with a cover from one of its songs (a stored cover path or URL only,
  no embedded-cover lookup) and "N tracks". The search toggle filters by normalised name.
  Empty, retry and loading states follow the Artists list.
- Genre songs (`lib/screens/library_genre_screen.dart`): title is the genre, a count line, compact
  `AlbumPlayActions` (Play, Shuffle), then flat `TrackCard` rows. Tap plays from the row; Shuffle
  turns shuffle on and starts at a random song (the Songs rule).
- Data (`lib/services/library_detail_data.dart`): `LibraryGenreEntry`, `loadLibraryGenres`,
  `loadLibraryGenreItems`. Providers (`lib/providers/library_detail_provider.dart`):
  `libraryGenresProvider`, `libraryGenreItemsProvider`, keyed by `tagCreditScopeProvider`
  (rules and index versions) and `localLibraryEnabled`.
- SQL (`lib/services/library_queue_store.dart`): `artistPage` and the new `genrePage` share
  `_creditPage` (artist SQL text unchanged). `_creditFilter` is shared by the album-artist and
  new `QueueLibraryDbQuery.genre` filters on the history and scanned track rows.
  `lib/services/library_database.dart`: `getQueueGenrePage`; `_creditsFor` (was
  `_albumArtistCredits`) also ensures credits for a genre filter.
- l10n: `libraryGenres` = "Genres" in all 21 ARB files, English everywhere (no translation
  existed); `@libraryGenres` description in `app_en.arb`. `flutter gen-l10n` run.
- Tests: new `test/library_genres_test.dart` (9 unit: split, sort, custom delimiter, Keep together
  ignored, empty skipped, counts and case, local off, downloaded plus scanned, one listing per
  file) and `test/library_genres_page_test.dart` (3 widget: overview row, genres list and search,
  genre page Play/Shuffle and row play).

Not changed (see open decisions 1 and 2):
- The Songs filter sheet (`lib/screens/queue_tab_filter_sheet.dart`) has no genre-value filter.
  Its only genre entry is `missing-genre`, a presence check. No ad-hoc genre matching was found
  in the filter, so nothing was replaced. No splitter lost its last reader, so nothing was deleted.

Checks (`scratchpad/t013/`):
- `gen_l10n.log`: exit 0.
- `analyze.log`: `flutter analyze lib test integration_test`: No issues found, exit 0 (final run,
  after the last edit).
- `format.log`: `dart format --set-exit-if-changed` on `changed_dart_files.txt` (10 files): exit 0,
  0 changed (final run).
- `targeted.log`: 13 library test files (pinned SQL and plan tests, T001 and T005 tests, the two
  new files, overview and Songs widget tests): 91 passed, 0 failed. It ran before test-only
  import and type edits; the full run covers the final tree.
- `full.json`, `full.exit` (0), `full_counts.txt`: 3948 passed excluding the 7 `loading` entries
  (all passed), 10 skipped, 0 failed; 3965 non-hidden testDone. Baseline 3936 plus 12 new.
- `validate.json`: `python3 workflow/work.py validate`: PASS (3 missions, 8 reserved, 37 completed).

Open decisions (owner):
1. The filter genre item. The task names a genre filter that "matches a split credit" in the
   Songs filter sheet. None exists. Options: (a) add a genre-value list to the sheet (new UI, then
   the matching widget test can be written); (b) make `missing-genre` read the credits (differs
   only for tags with delimiters alone). Neither was done.
2. Home mixes (`library_mixes.dart` `_familiesOf`) still split genres with their own regex, not
   the Genre delimiters. It has live readers, so it stays.
3. Genre rows are flat `TrackCard`s without selection; T006 aligns library rows.
4. The genre list is read whole, not paged; not measured on a large library.
5. No device or walkthrough run.

## M003-TOR015

Offline Home and library-only Search (FR-021, FR-022). Base `d6c428d`. Uncommitted; independent review
not run; `task complete` not run (tasks.md checkbox left open).

Map: start `scratchpad/tor015/map_start.json` (34227 symbols, 1705 sources, 0 partial); end
`map_update.json` (33623 symbols, 1682 sources, 0 partial, 0 unindexed).

Home (browse), in order: Top Picks (library mixes only), Recently Played or Continue Listening, New Releases
(new: albums, singles and EPs with a release date, favoured artists first, then release date desc;
`loadLibraryNewReleases`), On Repeat, Your Top Artists (new: all-time statistics, in-library artists, image from
T001 `imageCover`), Recently Added, Your Playlists (new), Genres (new, T013 provider; "see all" opens the Genres
page). An empty library shows "Your music will appear here" and the open-audio button.
Search: library only, via `LibrarySearchResults(shelves: true)`. Artist hits show `imageCover`. Clear and the
mic (recognition now searches the library) work.

Deleted (lib): `screens/home_tab_explore.dart`, `home_tab_recent.dart`, `home_tab_search_results.dart`,
`home_tab_search_wide.dart`; `providers/explore_provider.dart`, `home_top_picks_provider.dart`,
`recent_access_provider.dart`, `track_provider.dart` (TrackState, search, URL fetch; ArtistAlbum moved to
`models/artist_album.dart`); `controllers/home_search_logic.dart`; `models/artist_concert.dart`;
`services/explore_cache_store.dart`; `utils/extension_country_name.dart`; `widgets/explore_featured_section.dart`,
`home_feed_region_setting.dart`. Settings: `homeFeedProvider` (old JSON key ignored), `defaultSearchTab`,
`hasSearchedBefore` (fields, setters, default-tab picker). In `app_state_database.dart`: recent-access tables
(creation, row methods, legacy migration). `PlatformBridge.getExtensionHomeFeed`, `cancelExtensionHomeFeedRequests`,
`findURLHandler`, `handleURLWithExtension` with its URL cache (its spill-file test case now runs through the metadata lookup only); extension `hasHomeFeed`, `homeFeedRegionSetting`, `setHomeFeedRegion`; explore cache row; home-feed
settings rows and the country-option UI branch. 48 ARB keys with no reader removed from all 21 ARBs. Shared-link
URL handling removed (`_handleSharedUrl`, URL parts of the share service); shared audio intake kept
(`lib/services/share_intent_service.dart`, audio branch only, kept as a modified file, not deleted; `main.dart` matches HEAD).

Added: `lib/services/retired_explore_cache.dart` (startup clears the explore cache blob and its two preference keys,
called from settings load); `models/artist_album.dart`; the three Home providers and four shelves; strings
`homeNewReleases`, `homeTopArtists`, `homeYourPlaylists` in all 21 ARBs (English).

Kept, with reasons: `settings.searchProvider` and its row (weekly releases, download default and extension priority
read it); `customSearchWithExtension` (weekly releases, metadata autofill); CSV import on the Search bar (extension
matching, not Home/Search content); the three leftover recent-access tables on existing installs (not dropped;
schema version stays 5; nothing reads them); `ArtistAlbum.providerId` (artist widgets read it); native Kotlin/Rust
`getExtensionHomeFeed`, `findURLHandler`, `handleURLWithExtension` and MainActivity URL forwarding (no native file changed).

Tests deleted (declaration counts at HEAD, not executed cases): `explore_cache_store_test` (6),
`explore_featured_section_test` (5), `home_feed_region_test` (8), `home_search_narrow_layout_test` (1),
`home_search_wide_layout_test` (2), `home_top_picks_test` (13), `live_search_cancellation_test` (3),
`recent_access_clear_test` (3), `recent_access_persistence_test` (6), `track_provider_context_test` (1),
`track_provider_url_failure_test` (2), `test/support/search_results_fixture.dart`; the "home search logic" group in
`maintainability_contracts_test` (5 cases); the Home=true variant in `extension_icon_decode_test` (1 case).
`home_search_tabs_test` (13 declarations) was split: ported to `home_offline_test` the resume card, the
Continue-Listening-until-Recently-Played-settles test (2 cases) and the local-playback test; dropped the rest
(explore feed layout, startup loading (3), feed-disabled startup, restored feed, Home layout menu over explore
sections, Search keeping its query, failed-verification retry, collection sections, Search glass and scroll results,
Search recent items), because they test the removed feed or online search.
Ported: `app_settings_model_test` (old-settings JSON test added), `library_mixes_test` (album-hint expectation now
"no plays"), `library_mix_ui_test`, `main_shell_keyboard_test`, `extension_icon_decode_test`,
`cache_management_page_test` (explore row removed), `library_artist_screen_test`, `artist_album_sort_test`,
`artist_featured_album_card_test`, `collapsing_title_rebuild_test` (imports), `library_album_screen_test` (entry
list), `integration_test/mornye_screens_performance_test.dart` (online search measurement removed).
New: `test/home_offline_test.dart` (13 widget tests: shelf order with Top Picks and Recently Played first, empty
library, four shelf taps, Search library-only with no extension build, artist image, Search clear, and the three
ported Continue Listening cases); `test/retired_explore_cache_test.dart` (1); `library_detail_data_test.dart` (+2 New
Releases ordering).

Open decisions (owner):
1. Shared http/Spotify links no longer open Search; shared audio files still open. Native MainActivity still forwards URLs.
2. Mixes lose the recently-visited album hint (no store writer since TOR014).
3. New Releases: albums of the most-played artists first, then release date desc.
4. Top Artists uses all-time statistics.
5. Kept: `searchProvider` setting and row, CSV import on the Search bar, music recognition mic.
6. Native `getExtensionHomeFeed` and `findURLHandler` handlers left in Kotlin and Rust.

Checks (`scratchpad/tor015/`):
- `analyze.log`: `flutter analyze lib test integration_test`: No issues found, exit 0.
- `format.log` (`changed_dart_files.txt`, 47 files): `dart format --set-exit-if-changed`: exit 0, 0 changed.
- `gen_l10n.log`: exit 0.
- `targeted.log`: 22 touched or new test files: 178 passed, exit 0.
- `full.json`, `full.exit` (0), `full_counts.txt`: 3875 passed excluding 7 `loading` entries (all passed), 10 skipped,
  0 failed; 3892 non-hidden testDone. Baseline 3948 passed, so 73 fewer from the deleted tests above.
- Earlier full runs: `library_album_screen_test` source list fixed. `library_loading_test` "a superseded summary cannot
  restore stale lookup sets" and `track_metadata_lyrics_test` "lyrics embed preserves tags ... explicit=false" failed
  once with no exception text in the JSON; both passed alone and in the final full run. Cause not established;
  intermittent order or load dependence is suspected.
- `map_update.json`: 33623 symbols, 1682 sources, 0 partial, 0 unindexed.
- `validate.json`: PASS (3 missions, 7 reserved, 38 completed).

## M003-T006

FR-007: library track rows show only a cover, the title and the artist.

Changed:
- `AlbumTrackTile` (`lib/widgets/album_track_tile.dart`) takes an optional `cover` in place of the track number, and an optional `onPlay`, so library rows can use it without a play button.
- Songs list (`queue_tab_item_widgets.dart`, `_buildUnifiedLibraryItem`): artist only. Source badge, date, quality and offline label removed; play or missing-file trailing kept.
- Songs completion rows (`queue_tab_collection_items.dart`, `_buildBridgeItem`): artist only; the list-style quality badge branch is removed. The grid keeps its badge.
- Folders (`library_directory_view.dart`, `_songRow`): `AlbumTrackTile` with the cover; quality removed from the artist line.
- Library playlists and Loved (`playlist_screen_library.dart`, `_libraryRow`): cover from the new `CollectionTrackCover` (`collection_track_actions.dart`) instead of the track number.
- ARB: `librarySourceDownloaded` removed from all 21 ARB files (no reader left). `libraryInLibrary` stays (Wishlist badge).
- Wishlist (`LibraryTracksFolderScreen`) and `InLibraryBadge` untouched.
- Tests: rows assert their exact text (title, artist) and a cover (`library_songs_page_test.dart`, `library_directory_view_test.dart`, `library_playlist_screen_test.dart`). The two playlist selection-removal tests use a 430x1400 surface because the cover rows are taller.

Checks (evidence in `/tmp/claude-0/-home-user-Sistrum/6f2a99b7-2c8e-50d8-b242-f10db4d01b53/scratchpad/t006`):
- `map_start.json`, `map_update.json`: update succeeded before and after, 0 partial, 0 unindexed sources.
- `gen_l10n.log`: exit 0 after the ARB change.
- `analyze.log`: `flutter analyze lib test integration_test`: no issues.
- `format.log`: `dart format` on the 10 changed Dart files: 0 changed.
- `targeted.log`: 19 touched test files (targeted_files.txt): 113 passed, exit 0; `targeted_new.log` 27 passed.
- `full.json`, `full.exit` (1), `full_counts.txt`: 3893 non-hidden results: 3882 passed, 10 skipped, 1 failed. The failure is `initial index cannot overwrite a newer source remove` in `library_loading_test.dart`, a known intermittent file; it passed when re-run alone (`loading_rerun_1.log`, `loading_rerun_2.log`). The three new row tests passed.
- `validate.json`: PASS.

Not run: independent review, `task complete`, commit, push.

## M003-T007

FR-008 / User Story 5: track menus on this feature's pages offer only offline options.

Changed:
- Library track menu (`lib/widgets/track_lists/library_track_menu.dart`, search results): removed Save offline, Upload to network storage, Re-enrich, Delete offline server copy. Added Go to Album and Go to Artist (single track; artist via `navigateToArtistCredits`, so several credits offer a choice).
- Quick actions (`lib/widgets/track_collection_quick_actions.dart`): removed Download, Download another quality, Add/Remove Wishlist, Share, Copy link; added Go to Artist. Kept Play (local), Go to Album, Add to playlist, Favorite, copy name/artist. Its only production caller was already gone at HEAD; kept because the goal names it and tests use it.
- Selection bars (`LibraryTrackSelectionBar`, `LibrarySelectionPlaybackActions`): removed FLAC upgrade, Re-enrich, Save offline, Upload. Callers updated: queue tab, library mixes, album, folder and search screens.
- Folders: network-folder download button removed. Library album header: download and upload buttons removed (US2). Library playlist header: Save offline removed; Export M3U kept.
- Dead code deleted: `local_track_redownload_service.dart`, `track_collection_action_policy.dart` (+ test), `queueLocalTracksAsFlac`, `matchLocalTracksForFlac`, `NetworkOfflineButton`, `NetworkUploadButton`, unused imports.
- ARB (all 21 files): removed `queueFlacAction`, `queueFlacConfirmMessage`, `queueFlacNoReliableMatches`, `queueFlacQueuedWithSkipped`, `collectionAddedToWishlist`, `collectionRemovedFromWishlist`, `trackOptionAddToWishlist`, `trackOptionRemoveFromWishlist`, `trackOptionDownloadQualityVariant`, `trackOptionCopyLink`. No other key lost its last Dart reader.
- Tests: `mornye_collection_sheets_test.dart` (offline-only quick actions; replaces the copy-link tests), `selection_action_grid_test.dart`, `library_selection_playback_actions_test.dart`, `local_track_batch_actions_test.dart`, `library_search_results_test.dart`, `downloaded_album_reenrich_test.dart` (album re-enrich case removed), `network_upload_actions_test.dart` (upload offers removed; folder browser and offer tests kept). New `library_track_menu_offline_test.dart` asserts offline entries and absence of removed ones. Deleted `library_search_reenrich_test.dart` (only tested the removed search-menu re-enrich).

Kept, out of scope: Now Playing sheet (T008), Wishlist (`LibraryTracksFolderScreen`, its download helpers and strings), track metadata page menu (save offline / upload / delete server, not in T007's page list, flagged for review), `allowQualityVariants` setting (still read by the download queue and settings), library-wide re-enrich (`library_re_enrich_all.dart`).

Checks (evidence in `/tmp/claude-0/-home-user-Sistrum/6f2a99b7-2c8e-50d8-b242-f10db4d01b53/scratchpad/t007`):
- `gen_l10n.log`: `flutter gen-l10n` exit 0 after the ARB change.
- `analyze.log`: `flutter analyze lib test integration_test`: no issues (final).
- `format.log`: `dart format` on the changed Dart files (`changed_dart_files.txt`).
- `targeted.log`: first targeted run, 4 failures (re-enrich and upload expectations, fixed as above). `fix_rerun.log`: the two edited files pass.
- `full.json`, `full.exit` (0), `full_counts.txt`: 3883 results: 3873 passed, 10 skipped, 0 failed. Baseline 3882 passed; 10 tests removed with their code, one new menu test added, two quick-action tests merged into one, and the intermittent `library_loading_test` now passes. Known intermittent `library_loading_test` and `track_metadata_lyrics_test` passed in this run.
- `validate.json`: PASS.
- `map_update.json`: update exit 0, 0 partial, 0 unindexed sources.

Not run: independent review, `task complete`, commit, push.

Follow-up after review: deleted dead code left by the menu removals: `AlbumPlayActions.onDownload` (field and button branch), `LibraryCollectionsState.isInWishlist`, `LibraryCollectionsNotifier.toggleWishlist`, `LibraryCollectionsDatabase.upsertWishlistEntry`/`deleteWishlistEntry` (the Wishlist screen still uses `removeWishlistTracks`/`deleteWishlistTracks`). The toggle tests in `library_collections_mutation_test.dart` now run on Loved. Re-checked after the edit: analyze clean, that test file 24 passed.
