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
