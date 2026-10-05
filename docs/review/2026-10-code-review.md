# Sistrum — Full Code Review (5 parts)

Reviewer persona: Code Reviewer (agency-agents), installed at `.claude/agents/code-reviewer.md`.
Method: static reading only. Nothing was built, run or edited. Every finding is an agent's claim and needs verification before fixing.
Markers: 🔴 blocker, 🟡 suggestion, 💭 nit.
Scope notes: part 5 (tests/config) sampled by grep and spot reads, not every file. `player/` and native code may change only via Media3 migration tasks (CLAUDE.md).

---

## Part 1 — sync/, lyrics/, lastfm/, settings/, device/, top-level files (all read)

Good: one file open per tag read; `writeLocalLibrary` transactional and keeps old library on empty scan; path-traversal guards on staged artwork; FTS input tokenised; Last.fm session key AES-GCM in Keystore; HTTP code handles cancellation.

### 🔴 Blockers
1. **Destructive Room fallback.** `SyncDatabase.kt:371` `.fallbackToDestructiveMigration()`, `exportSchema=false` (`:363`), migrations start at 2→3. A missing migration or downgrade wipes playlists, favorites (stored as mutations), listening stats, lyrics. Suggestion: drop blanket fallback (at most `...OnDowngrade`), `exportSchema=true`, add `MigrationTestHelper` tests.
2. **ID3v2.3 year lost when only `TYER` exists.** `EmbeddedTagReader.kt:1127-1136`; `legacyDateFrom` (`:1166`) returns null unless `TDAT` also present, so year stores 0. Scanner never reads MediaStore `YEAR` (`MediaStoreLibraryScanner.kt:165`). No `TYER` test. Fix: fall back to `values["TYER"]` (`TYE` for v2.2), add test, bump `CurrentMetadataSchemaVersion` to 7 (`MediaStoreLibraryScanner.kt:420`).
3. **Last.fm login CSRF via deep link.** `AndroidManifest.xml:25-45` exports activity with BROWSABLE `airmedy://lastfm/auth`; `MainActivity.kt:468-474` and `LastFmService.kt:120-126` accept any token with no pending connect. A web page can bind the app to an attacker's Last.fm account and scrobble the victim's history there. Fix: `authorizationUrl()` sets a pending flag/nonce with short expiry; `completeAuthorization` ignores callbacks when none pending, clears afterwards.
4. **Probable crash: `AndroidSyncRuntime` initialised only in `MainActivity.onCreate`** (`MainActivity.kt:133`). `PlaybackService.kt:97-106` calls `syncStore()` in its own `onCreate`; if the system restarts the service without the activity → `UninitializedPropertyAccessException`. Not verified whether the service is sticky. Fix: init in `Application.onCreate` or lazy holder; make `initialize` thread-safe (`AndroidSyncRuntime.kt:103`).

### 🟡 Suggestions
5. **Navigation loses params for nested same-type details.** `MainViewModel.kt:247-306` keeps one `selectedAlbumId` (same for artist/genre/composer/playlist). Album A → Artist → Album B, back → wrong/empty content. Fix: put id in stack entry (`StackPageEntry(page, argId)`).
6. **One bad cover aborts the whole scan.** `MediaStoreLibraryScanner.kt:243-249` rethrows any `ExecutionException`. Wrap per-album copy in `runCatching`; null-check `decodeSampled` (`:395`).
7. **Search polluted.** `SyncDatabase.kt:329`, `:1386` match across all FTS columns incl. `planId`, `entityType`, `entityId`. Use column-scoped terms.
8. **Library-wide JSON re-parse on every change.** `SyncDatabase.kt:570-631` parses every `rawJson` per emission; `:247`/`:934` each qualified play updates `sync_tracks.playCount` and re-emits the whole list; `MainActivity.kt:150` collects it at root; `:300` OR join prevents index use. Separate play counts from the heavy query, parse once, split the join.
9. **Tag-reader robustness/speed.** ID3 reads the full tag incl. cover art into memory per track (`EmbeddedTagReader.kt:164-175`, up to 24 MB); skip `APIC` by seeking. `:536` allocates `ByteArray(length)` from untrusted size; `:1030` negative length from v2.3 size >2 GB throws and discards the file's tags. Clamp both. ID3 extended headers not skipped (`:1176`).
10. **Cleartext + default-on third-party lookups.** `AndroidManifest.xml:13` `usesCleartextTraffic=true` only for `http://krcs.kugou.com` (`AndroidLyricsService.kt:275`); scope with network-security-config or use https. LRCLIB and Kugou on by default (`LyricsPreferences.kt`); title/artist/album/duration sent for every lyric-less track on every play (`MainActivity.kt:223`), no negative cache.
11. **Sidecar lyrics probably don't work.** `AndroidLyricsService.kt:130-143` reads `.lrc`/`.txt` via `java.io.File`; manifest has only `READ_MEDIA_AUDIO` (`AndroidManifest.xml:5`), which doesn't cover non-media files on API 31+. Not verified on device. Add size cap to `readText`.
12. **Scrobbles / love-unlove fire-and-forget.** `LastFmService.kt:165-176`, `:253-269`: failures dropped, offline listening never scrobbled; consider persistent retry queue. `:263` sends folder-name album ("Music") for untagged files. `:104-110`: `loaded` completes only after avatar refresh (`:193`), so `disconnect`/`setLoved`/`completeAuthorization` can wait 25 s+.
13. **Default separators split common names.** `ArtistSeparator.kt:37-41` includes "x", "with", ",", "/": "AC/DC", "Tyler, The Creator", "Malcolm X", "Earth, Wind & Fire" get split. Documented as deliberate but default for all users. Clearing genre delimiters stores artist defaults (`ArtistSeparator.kt:96`, `TagSeparatorPreferences.kt:295`), breaking "R&B/Soul".
14. **Track identity depends on MediaStore `_ID`.** Claim that ids survive reinstall (`MediaStoreLibraryScanner.kt:104`, `SyncDatabase.kt:802`) fails after re-index/SD change/restore: favorites, playlists, stats orphaned; `deleteProviderLyricsNotInPlan` (`SyncDatabase.kt:269`) drops lyrics for temporarily absent tracks. Consider path/hash key or orphan reconcile.
15. **Playlist mutation replay (minor).** `MOVE_TRACK` inserts non-members (`SyncDatabase.kt:1016-1024`); dedupe ids collapse ADD/REMOVE into one row and MOVE into another (`:1309-1315`); ordering relies on wall clock.
16. **Staged artwork write races.** `PlaylistArtworkStorage.kt:190-195`, `:218-223` skip write when hashed file exists and use fixed `$hash.tmp`; concurrent `cleanupUnusedPlaylistArtwork` (`SyncDatabase.kt:713`) can delete it after re-insert; failed write leaves `.tmp`. Use unique temp names and do the check in the same transaction/lock.

### 💭 Nits
- `PlaylistArtworkStorage.kt`: playlist/artist staging copy-pasted; redundant self-import `:7-8`.
- `AndroidLyricsService.kt:353` `toChar()` truncates entities above U+FFFF; use `Character.toChars`.
- `SyncDatabase.kt:925` `endReason!!` can NPE; `:752` `name!!`.
- `MainActivity.kt:251`, `:374` `startActivity(ACTION_VIEW)` without `ActivityNotFoundException` handling.
- `App.kt:253` greeting uses `LocalTime.now()` and goes stale; `App.kt:196` `rememberSaveable` for full-screen flag vs non-saved copy in `MainActivity.kt`.
- `allowBackup=true` without extraction rules: restore brings `device_identity` (cloned device id) and undecryptable Last.fm blob.
- Minimum scan duration 30 s (`MediaStoreLibraryScanner.kt:479`) silently drops interludes/skits.
Not flagged: `ScanFilter`, `ThemePreferences`, `LyricsPreferences`, `DeviceIdentity`.

---

## Part 2 — player/ and cpp/ (all read) — Media3 migration scope

Good: callback-quiescence gating before slot release; `finished`/`seek_pending` EOF guard; immutable DSP config pointer swap; decoder nulled before suspension in `playCurrent` (423); native lifetime in `Closeable`; consistent fd ownership.

### 🔴 Blockers
1. **`startForegroundService` without `startForeground`** (`PlaybackService.kt:233-269`, `PlaybackController.kt command()`). Every controller command (pause, seek, shuffle, repeat, stop) uses it, but `showForeground()` runs only after successful `playCurrent`/`restoreCurrent` (447, 476). `fail()` (644), `stopPlayback()` (603), `clearRestoredSession()` (689) only call `stopForeground(REMOVE)`; `playCurrent` returns early on "asset not available"/"focus not granted" (414, 417). A command to a cold service with empty queue hits none of them → `ForegroundServiceDidNotStartInTimeException` after ~5 s. Fix: `startForeground` with placeholder at top of `onStartCommand`, or plain `startService` for non-playback commands; `stopSelf()` after stop/fail/clear (ticker at 203 otherwise runs every 200 ms forever).
2. **`onDestroy` closes decoder while commands may use it** (`PlaybackService.kt:273-292`). `decoder?.close()` (281) and `currentSession()` (280) run without `commandMutex`; `scope.cancel()` is last (288). `nativeDestroy` does `delete engine` → use-after-free risk; `handle` is non-volatile. Fix: `scope.cancel()` first, then `runBlocking { commandMutex.withLock { ... } }`, then close. `runBlocking` in `onDestroy` and `onCreate` (103) blocks main thread on DB/DataStore (ANR); move recovery into restore job.
3. **Ring buffer not SPSC around seek** (`ffmpeg_player.cpp:167-181` vs `625-641`). JNI thread (`nativeSeekTo:640`) and decode thread both write `write_frame`; decode thread stores `read_frame = 0` (174), which the callback owns. Callback's `store(read+1)` can overwrite → `available()` reports almost the whole ring → up to ~2 s stale audio. Decoder keeps pushing stale samples until it reaches the seek (167). Fix: only decoder thread touches indices, with generation/epoch counter; callback outputs silence on epoch mismatch/`seek_pending` and acknowledges flush.

### 🟡 Suggestions
- **Command reordering** (`PlaybackService.kt:294-302`): `dispatch` does `scope.launch` on multi-threaded IO then waits on mutex; fairness ≠ launch order. LOSS_TRANSIENT/GAIN can swap → paused forever. Use single `Channel<Command>` consumer or single-thread dispatcher with `UNDISPATCHED`.
- **Slot race in `nativePreload`** (`ffmpeg_player.cpp:511-521`): `idle_slot_for_preload` computed before `wait_for_callback_quiescence`; if callback is in `promote_gapless`, incoming slot looks idle and is released while live. Wait first, then pick. `nativeBeginCrossfade` (562-565) should use compare-exchange; otherwise `active_slot` can become -1 (permanent silence). Narrow window.
- **Leaked native engine on restore failure** (`PlaybackService.kt:464-474`): `decoder = FfmpegDecoder().also { prepare }` assigns after lambda; if `prepare` throws, new decoder never closed (AAudio stream + engine leak). Mirror `playCurrent` try/catch (443-446). Rethrow `CancellationException` in `catch (Throwable)` (443, 479).
- **Intent size limit:** track ids go in intent extras via `startForegroundService`; 20k tracks ≈ 800 KB → `TransactionTooLargeException`. Use in-process holder. `play()` logs `request.trackIds[request.startIndex]` (line 24), throws on empty list.
- **Uncaught exceptions kill app** (`PlaybackService.kt:203-230` ticker, `dispatch` 301): no `CoroutineExceptionHandler`; wrap tick/command in try/catch.
- **Heavy work under mutex:** `publishQueue` (656-678) loads whole library (`tracks.first()`), rebuilds MediaSession queue and saves session on every command incl. seek drags; `AndroidPlaybackRuntime.resolve` linear scan. `preloadNext` runs `avformat_find_stream_info` inside lock.
- **Audio focus:** `playCurrent` requests focus even with `startPaused` (417); `fail()` never abandons focus; `stopPlayback` doesn't clear `resumeOnFocusGain`.
- **No decoder drain at EOF** (`ffmpeg_player.cpp:182-193`): no `avcodec_send_packet(NULL)`, no `swr_convert` flush; tail dropped. `swr_get_out_samples` <0 (196) → huge `output.resize` → uncaught `bad_alloc` → terminate.
- **Single-slot native transition event** (`ffmpeg_player.cpp:99`, `PlaybackService.kt:721-723`): two transitions in one tick overwrite; `consumeNativeTransition` returns early (723) if `preloadedItem` null → queue/native desync.
- **`nativePlay` ignores `requestStart` result** (606): UI shows Playing even if stream failed to start.
- **`onSkipToQueueItem` reads `queue.snapshot()` on binder thread without mutex** (166): data race.

### 💭 Nits
- `std::pow(10, ...)` per sample per slot (295, 390): compute gains once per callback. EQ +12 dB boosts have no headroom/limiter.
- Pause/resume cut instantly without ramp (358, 609-615); EQ state reset on config change (326) clicks.
- `dsp_configs` grows on every `setGlobalDspConfig`; `equalizerPreferences.settings` lacks `distinctUntilChanged` (130); crossfade flow re-runs `preloadNext` on any pref write.
- Unchecked: `av_seek_frame`, `swr_init`, non-EOF `av_read_frame` errors (170-173, 182); `output_rate` non-atomic read by `nativePreloadedPositionMs`.
- Concurrent `scope.launch { sessionStore.save }` (677) can land out of order.
- `AAudioStream_requestPause` used for `nativeStop` (617-624); unspecified `ch_layout` makes `swr_alloc_set_opts2` fail for some files.

---

## Part 3 — ui/components (48 files) + sharedLogic (all read)

Good: RTL handled carefully (`towardsEnd()`, mirrored fill, `LeftToRight` islands, `MirroredInRtlSymbols`); idle animations stop; queue/tracker pure and well tested.

### 🔴 Blocker
- **Wrong track plays in libraries >1000 tracks** — `PlaybackQueue.kt:79-82`, called from `LibraryTracksViewModel.kt:174-176`. `playbackRequestFor` sends all ids + tapped index; `play()` does `distinct().take(1000)` and `startIndex.coerceIn(0, lastIndex)`. Tap #1500 of 3000 plays #1000; duplicate ids before the index shift it too. Whole library in the service Intent can also exceed ~1 MB Binder limit (`collectionPlaybackRequestFor` already bounds its request). Fix: resolve start id before truncating; take a window around it. Add regression test with >1000 ids.

### 🟡 Suggestions
- `AnchoredPopupMenu.kt:57-76, 121-131`: `show()` ignores changes to `menu`/`onDismissRequest`; overlay refreshes only when `request?.id` changes → stale content/callbacks; anchor not followed.
- `AlphabeticalIndex.kt:82`: `if (entry == selectedEntry) return` stays true after finger lifts; tap "M", scroll away, tap "M" → nothing. Reset on gesture end.
- `PlaybackQueue.kt:73`: `restore()` fallback unreachable (`indexOf` → -1 is non-null) → `currentIndex = -1`. Use `.takeIf { it >= 0 }`.
- `PlaybackQueue.kt:242-250`: `reorderQueue` leaves `original`; toggling shuffle off undoes the user's reorder.
- `FindLyricsContent.kt:70-75, 84-89, 144`: no error handling in `scope.launch`; throw → crash; cancel → `searching` stuck true; no double-tap guard on `onSelected`. `TrackContextMenu` never passes `onSearchLyrics`/`onLyricsSelected` to its own sheet (214-221): silent no-op when `onBottomSheetRequested == null` (latent).
- `AirmedyTrackSlider.kt:135-137, 173-174`: accessibility `setProgress` never calls `onValueChangeFinished` (TalkBack can't commit seek); on release `dragPreviewValue = null` flickers back to stale value.
- `MaterialSymbols.kt:146`: icon size in `sp` scales with font size, breaking fixed 18/20dp slots and insets (46dp `Selection`/`TrackSortHeaderButton`, 36dp `ActionList`). Use `LocalDensity` `size.toSp()`.
- `TrackRow.kt:47, 76-86`: `LruCache(250)` counts entries not bytes (~115 MB at 480px RGB_565); failed decode never cached → artless rows reopen audio file repeatedly. Override `sizeOf`, add negative cache. `PlaylistArtwork` loads 128px for 110dp (blurry).
- `AirmedyBottomSheet.kt:160-180`: `pointerInput(dismissDragThreshold)` captures first-composition `requestDismiss`/`onDragDismiss`; use `rememberUpdatedState`.
- `LibraryTextFilter.kt:22`: local `inputValue` ignores parent resets.
- Recomposition: `metadataObject()` re-parses JSON per call (`SyncDatabase.kt:463`), un-remembered at `TrackInfoContent.kt:197`, `FindLyricsContent.kt:60` (every keystroke); `TrackContextMenu.kt:325-333` `in playlist.trackIds` (List) per row.
- `LyricsMatching.kt`: line 12 `removeFeaturedLyricsTitle` lacks word boundary after `fe?a?t` ("(Fat Joe Remix)", "(Fatboy Slim Remix)" stripped); line 24 candidate duration 0 rejected when track duration known; lines 8, 33 regexes rebuilt each call; score expects caller to pre-normalise title/artist but not candidate.
- `VolumeNormalization.kt:24-28` (deferred to Media3): Album mode clip prevention uses track's peak not album max; LUFS averaged arithmetically; no clamp/non-finite guard (digital silence → -inf → infinite gain).
- `ListeningTracker.kt:59`, `LastFmScrobbleTracker.kt:29`: qualification is position-based; seeking to 50% scrobbles with no listening. Matches desktop contract — confirm intent.
- `PlaybackService.kt:326` / `PlaybackQueue.next()` (152): Repeat One + Next replays same track; a test locks it in → likely deliberate but surprising.
- `LibrarySync.kt:34`: `ignoreUnknownKeys = false`; persisted manifests fail to decode after additive change/downgrade; desktop sync is removed.
- `Card.kt:36`: `.then(modifier)` after `clip`/`background`; caller padding/size/offset applies inside the card.
- `PlaybackQueue.kt:91` `playShuffled`: `selected.filter(active::contains)` O(n×1000) on a list; ignores `startIndex`.
- Layout/hit targets: `StackPageLayout.kt:279` `HeaderActionSlot` fixed 48dp squeezes 2+ actions; `AirmedyTextField.kt:132-142` clear button ~30dp wide; `AlphabeticalIndex` 18dp wide; `DiscGrid.kt:36`, `LibraryVirtualList.kt:160` keys not guaranteed unique (duplicate → crash); `AirmedyMarqueeText.kt:91` depends on internal annotation tag `androidx.compose.foundation.text.inlineContent`; pause mid-settle then resume jumps to offset 0.

### 💭 Nits
- Hard-coded units not localised: "kbps", "kHz", "-bit", "B/KB/MB" (`TrackInfoContent.kt:143-190`).
- Dangling `Divider` when only `removeFromQueue` enabled (`TrackContextMenu.kt:182-183`).
- `displayLocale()` builds a new `Locale` per `formatDisplay` call.
- `AnimatedSkipSymbol`/`AnimatedPlayPauseSymbol` allocate `Path`/`List`/`Pair` per frame; `roundedPath` duplicated.
- Album/Artist/Composer/Genre context menus near copies; `AlbumRow`/`ArtistRow`/`TrackRow` repeat layout.
- `"favorites"` literal repeated (`TrackContextMenu.kt:373`, `PlaylistRow.kt:92`, `PlaylistSync.kt:40`) despite `FavoritesPlaylistId`.
- `ListeningTracker` uses `activeAt == 0L` as inactive sentinel.
- Unused: `Selection.kt` (`colors`), `StackPageLayout.kt` (`DrawableRes`). `MoodRadio` doesn't guard NaN features.

---

## Part 4 — ui/screens (40), ui/navigation (14), ui/theme, LibraryAlphabeticalOrder.kt (all read)

Good: `runPlaylistWrite` handles failures without swallowing cancellation; lazy `tracksBy*` indexes prebuilt off-main; `NavigationChromeScrollAccumulator` keeps scroll state out of Compose; RTL (`towardsEnd`, `LeftToRight` transport, `absoluteOffset`); `LocalResources` used; time labels via `formatDisplay`.
Not verified: `ui/components`, `App.kt`, whether all strings exist in `values-ar`.

### 🔴 Blockers
1. **Wrong duration** — `navigation/FullScreenPlayer.kt:525-532`: fallback reads metadata "duration" ×1000 but `LibraryTrack.durationMillis()` (`SyncDatabase.kt:470`) is already ms → ~3000:00 for a 3-min track while Preparing; `toLongOrNull` fails on "180000.0". Use `contextTrack?.durationMillis()`.
2. **Stale callback reverts settings** — `screens/LufsTargetSlider.kt:44-59` (used `PlaybackSettingsContent.kt:198-202`): `pointerInput(enabled, isRtl)` keeps first-composition `updateAt`; `onTargetChanged` copies old `normalization` snapshot → toggling "prevent clip"/mode then dragging writes old values back. Use `rememberUpdatedState` or a `setTargetLufs` intent. `CrossfadeDurationSlider` is fine.
3. **Picker artwork decoded on main thread, uncaught exceptions** — `screens/CreatePlaylistDialog.kt:98-100`: `remember { openInputStream → decodeStream }` at full resolution (OOM/jank); `FileNotFoundException`/`SecurityException` in composition crashes. Use `produceState` off-thread with `inSampleSize` + try/catch.
4. **Comparator contract violation** — `LibraryAlphabeticalOrder.kt:11-16, 33-35`: fold-vs-raw decided by first char only; non-folded strings compare raw ("rz" < "ré"), folded compares normalised ("ŕm") → cycle; TimSort may throw "Comparison method violates its general contract" (needs ≥32 items + accent mix) and kill the `flowOn(Default)` pipeline. Always compare one key (`normalizedLibraryAlphabeticalText` or `java.text.Collator`). Related: `"\\p{M}+".toRegex()` recompiled per call (line 39, `LibraryTextSearch.kt:9`); hoist.

### 🟡 Suggestions
**Player recomposition/perf**
- `FullScreenPlayer.kt:510, 597-602, 524-532`, `FullScreenPlayerControls.kt:355`: `expansionProgress.value` read in composition → whole player recomposes every frame/tick, parsing metadata JSON, scanning `queueTracks`, rebuilding `trackInfoValues`. Use `graphicsLayer{}`/`offset{}` lambdas; `remember(contextTrack)`.
- Early `?: return` before `remember` (`FullScreenPlayer.kt:471`, `MiniPlayer.kt:113`) drops `Animatable`/`selectedPanel` when playback goes Idle.
- `FullScreenPlayerArtwork.kt:118-121`: per-frame state write during crossfade recomposes everything.

**Gesture / RTL**
- `FullScreenPlayerQueuePanel.kt:482`: `longPressX < width - handleWidth` assumes handle on physical right; wrong in RTL. Mirror via `towardsEnd()`/layout direction.
- `QueuePanel.kt:263-266`: `onReorder` runs even when order unchanged.
- `QueuePanel.kt:102`: `remember(queue.activeTrackIds)` loses local order if queue emits mid-drag.
- `MiniPlayer.kt:156-163`: `miniPlayerTopPx` is a `pointerInput` key and changes during chrome animation/scroll → cancels in-flight drag with no end callback; use `rememberUpdatedState`.

**Lyrics** (`FullScreenPlayerLyricsPanel.kt`)
- 106-117: duplicate-timestamp lines (`[00:10][01:30] chorus`) appended out of order; `indexOfLast` (283) assumes sorted. Use stable `sortedBy`.
- 87, 186: "/" splits ordinary lyrics ("AC/DC", "24/7", "and/or") into secondary line.
- 101-114: LRC `[offset:+N]` conventionally shifts earlier; code adds it — confirm direction.
- 383-414, 455: `isFollowingSelectedLine` can stick (effect keyed on `selectedLineIndex`; re-tapping same pending line never relaunches; browse mode blocked).

**Seek**: `FullScreenPlayerControls.kt:316-323` `awaitingSeekConfirmation` has no timeout; failed seek/paused stream pins the slider until track change.

**Navigation state** (`AppDestinationContent.kt`): 181-208 and `selected*Id` callbacks 538-671 use current selection not `currentPage` → during exit slide the outgoing details page renders empty state and fires actions with the new id. Line 383 `LaunchedEffect(..., settingsScrollState.value)` relaunches every scroll frame; use `snapshotFlow`.

**ViewModels / data**
- `Artist/Genre/ComposerDetailsViewModel.kt:85-95`: lists built only from tracks whose album id is in `allAlbums` → tracks with blank/unknown album vanish from page and play/queue actions.
- `PlaylistDetailsViewModel.kt:133`: `associateBy` over whole library in composition and `play()`. `LibraryPlaylistsViewModel.kt:62`, `:190`: `playlistArtworkPaths` per playlist rebuilds full map (O(playlists×tracks)), recomputed on every play-count change. Hoist `tracksById`; compute selected-id state in VM flow.
- `LibrarySearchContent.kt:106, 130-131`: per card per recomposition `allTracks.filter{}` + two `firstOrNull{ it in playlist.trackIds }` scans O(N×M); compute album tracks only when `contextAlbumId` matches. `:161` fixed `183.dp` clips with large font scale.
- Queue size not bounded outside "play all": `playbackRequestFor` (`LibraryTracksViewModel.kt:174`), `AlbumDetailsViewModel:47/60`, details/playlist `play()`; `MaxPlaybackQueueSize` = 1000 (`PlaybackQueue.kt:7`), truncation point not found by this reviewer (see Part 3 blocker). Route everything through one windowing helper.
- `PlaylistDetailsContent.kt:171`: `items(key = id)` crashes if playlist holds a track twice; confirm store dedupes.
- `LibraryPlaylistsViewModel.kt:52, 100`: `createdPlaylistIds` is an unbuffered `MutableSharedFlow` → event lost if nothing collecting. Use `Channel` or `replay=1`.

**Scan / lifecycle**
- `LibraryScanContent.kt:62, 160`, `TagSeparatorsContent.kt:156, 203`: scan runs in `rememberCoroutineScope`; leaving the page cancels it mid-write.
- `LibraryScanContent.kt:171-205`: `runCatching` swallows `CancellationException`; failed scan returns to idle with only a log line. Move scan to VM/`AndroidSyncRuntime` scope, rethrow cancellation, show error.
- `ScanFilterContent.kt:89-94`: folder map over all tracks on UI thread in `remember`, again per emission; add/remove is read-modify-write outside DataStore transaction → quick taps lose updates.

**Slider write amplification**: `LufsTargetSlider.kt:46`, `CrossfadeDurationSlider.kt:57` write settings on every pointer move even if rounded value unchanged; guard with `if (new != current)`.

**About links** (`AboutContent.kt:25-30`): GitHub, License, GitHub Sponsors, Ko-fi, BuyMeACoffee, Patreon all point to upstream misa198, not AMWolfstein/Sistrum. Upstream credit is fine; primary link should be this repo; sponsor rows may be read as supporting Sistrum.

**Insights**: `InsightViewModel.kt:115` default `InsightUiState()` with no loaded flag → "no data" flashes first. `InsightContent.kt:224-225`: genres with all-zero seconds pass `values.isEmpty()` → all-zero donut to Vico; known empty-data crash may apply; filter zero values.

### 💭 Nits
- Four details VMs/screens copy-paste (indexes, comparators, `*AlbumIdentifier`); `formatAlbumTotalDuration` ≡ `formatPlaylistTotalDuration`.
- Tracks/Albums filter immediately; Artists/Genres/Composers use `debouncedLibrarySearchQuery`.
- `LibraryTracksViewModel.kt:95-97` re-sorts "recent" on every filter keystroke.
- Untranslated/hardcoded: `"--:--"` (Controls:415), `"E"` badge (Metadata:49), preview samples in TagSeparators, `"favorites"` (`InsightViewModel:163`, use `FavoritesPlaylistId`).
- `PlaybackSettingsContent.kt:140`: EQ band contentDescription "Enable equalizer 60 Hz"; describe the band.
- Unused `val colors` (`InsightContent.kt:212`); `dragOffset` in full-screen swipe dead (`verticalDragOffset`, Gestures:570, never updated → "mostly horizontal" check never fires).
- Metadata duplicates `TrackContextMenu` in compact and full variants with one shared `expanded` → both can pop up during crossfade.
- Mini-player "Settings → Scan filter" gives no rescan hint, unlike Tag Separators.

---

## Part 5 — tests, manifest, build, scripts (sampled via grep/spot reads)

Good: no `@Ignore`, no `Thread.sleep`; `ScanProjectionTest` documents the Android 12 empty-library regression; `EmbeddedTagReaderTest` asserts one-open-per-read (~:586); coroutine tests use `setMain/resetMain`; secrets not in git (Last.fm key/secret and keystore passwords from `local.properties`/env); release minify+shrink on.

### 🔴 Blockers
1. **Destructive Room migration, no migration tests** — `SyncDatabase.kt:362-371`: version 13, `exportSchema=false`, 11 migrations, no `MigrationTestHelper`, no `schemas/`. Set `exportSchema=true` + `room.schemaLocation`; test chain endpoints 2→13; drop or limit the destructive fallback to rebuildable scan tables.
2. **`usesCleartextTraffic="true"` app-wide** — `AndroidManifest.xml:12`. Only plain-HTTP use: `http://krcs.kugou.com/search` (`AndroidLyricsService.kt:275`). All traffic incl. Last.fm session-key/signed calls can be downgraded/sniffed. Remove and add `network_security_config` for `krcs.kugou.com` only, or drop the provider if it has HTTPS.

### 🟡 Suggestions
- `allowBackup="true"`, no `dataExtractionRules`/`fullBackupContent` (`AndroidManifest.xml:11`): Room DB, Last.fm session, prefs cloud-backed-up/adb-pullable. Add rules or set false (Android 12+ needs `dataExtractionRules`).
- Exported `MainActivity` with BROWSABLE VIEW `airmedy://lastfm/auth` (`AndroidManifest.xml:30-40`): handler must treat token as untrusted (see Part 1 #3, confirmed there). Custom schemes can be hijacked; consider App Link.
- `LASTFM_API_SECRET` in BuildConfig (`androidApp/build.gradle.kts ~:130`, used `LastFmService.kt:81`): extractable from APK; inherent to Last.fm signing, don't treat as confidential. `proguard-rules.pro` only has a JNI keep rule with minify on; no release-build smoke test → minify-only crash uncaught.
- FFmpeg tarball has no checksum (`scripts/build-ffmpeg-android.sh:10, 34`): pin and verify SHA-256 before extraction. `ndkVersion` hardcoded in gradle ("30.0.15729638") and separately `NDK_VERSION` in the script; derive from one source.
- Instrumented tests: 111 `onRoot()`/`assertExists()` uses (existence-only; pass on wrong content, likely hide stale-text failures). Ad hoc `waitForIdle()`: `MiniPlayerTest.kt:79,127,152,191,247,306`; `AirmedyBottomSheetTest.kt:58,100`; `TrackContextBottomSheetTest.kt:115`. Prefer `waitUntil` on observable condition.
- Weak assertions: `LibrarySearchViewModelTest.kt:57-59` only `isNotEmpty()`; `ListeningRetentionTest.kt:75` existence-only.
- Coverage gaps: no end-to-end `MediaStoreLibraryScanner` test (diffing, removed files); no test that a `CurrentMetadataSchemaVersion` bump triggers rescan; no queue mutation/reorder/shuffle/restore tests (matters before Media3, tests-first); no Room migration tests; no denied-`READ_MEDIA_AUDIO` flow test.
- Gradle: font-subset exec task is cached but inputs omit python interpreter and fonttools versions (stale font possible); `versionCode`/`versionName` hardcoded; release signing silently falls back to unsigned if any `MOBILE_KEYSTORE_*` is missing — fail loudly in CI.

### 💭 Nits
- `screenOrientation="portrait"` locked (`AndroidManifest.xml:30`): lint flags large screens/foldables.
- No tracked `.gitignore` (by design via `.git/info/exclude`); consider minimal one for `local.properties`, `*.jks`, `*.keystore`.
- `.claude/hooks/git_hooks.py`, `.specify/scripts/bash/common.sh` matched grep for "token"/"secret"; likely benign, not inspected.

---

## Cross-review agreement (strongest signals)
- Destructive Room fallback: Parts 1 and 5.
- Last.fm deep-link token accepted without pending request: Parts 1 and 5.
- Cleartext traffic app-wide for one host: Parts 1 and 5.
- Queue/intent size and 1000-track truncation: Parts 2, 3, 4.
- Repeated JSON re-parse / library-sized work in composition: Parts 1, 3, 4.

## Suggested fix order (outside `player/` and native code)
1. Part 3 blocker (wrong track in big libraries) + regression test.
2. Part 4 blockers 1-4 (duration, LUFS slider callback, artwork decode, comparator).
3. Part 1 #3 (Last.fm CSRF) and cleartext/backup manifest edits.
4. Part 1 #2 (TYER year) with `CurrentMetadataSchemaVersion` bump to 7.
5. Part 1 #4 (`AndroidSyncRuntime` init).
6. Room schema export + migration tests (needs a migration plan per CLAUDE.md).
`player/` / native items (Part 2) → record as requirements in the Media3 spec.
