VERDICT: NEEDS CHANGES

# M2 test coverage review

Range: `fc03e17e0dc812ed7e64dbe2edff0248fc10f1ca^..fb88f20a` (HEAD at review).
Checklist: pr-test-analyzer. Read CLAUDE.md, spec.md, DECISIONS.md (especially D-4, D-14, D-19, D-20, D-21, D-22), tasks.md and relevant HANDOFF entries.

## 1. Coverage summary

Paths below are relative to `androidApp/src/`. Production abbreviations in the trace appendix resolve under `main/kotlin/com/amwolfstein/sistrum/`; test filenames resolve to the full paths printed in that appendix.

| Changed production unit | Exercising tests | Assessment |
| --- | --- | --- |
| components/HomeContent, recentHomeAlbums, HomeAlbumHero, HomeTrackSection | HomeAlbumsTest, HomeContentTest, HomeContentMatrixTest; moved LibraryContentTest cases | Grouping/order, empty/loading, queue identity, representative click/menu, explicit/quality badges, virtualization, RTL and font matrix. No network is invoked by reviewed implementation. |
| screens/LibraryContent | LibraryContentTest, LibraryContentMatrixTest | Seven callbacks, absence of moved shelf, saved list geometry; old recent-track behavior moved to Home with the D-22 scope change. |
| screens/LibraryAlbumsContent, components/AlbumRow, DiscCard | LibraryAlbumsContentTest; existing DiscCard/grid tests | List/grid, loading/filter, menu callback identities, 10,000 items, restoration, RTL/font 2. |
| screens/LibraryTracksContent, components/TrackRow | LibraryTracksContentTest | Filter/focus, empty/loading, queue availability, order/identity, 10,000 items, restoration, RTL/font 2. |
| screens/LibraryGenresContent, LibraryComposersContent, GenreDetailsContent, ComposerDetailsContent; GenreRow/ComposerRow | GenreComposerContentMatrixTest; existing four screen suites | Loading/empty/filter/content, detail unavailable/empty/content, two-line typography, album navigation and dividers. |
| screens/AlbumDetailsContent, components/DetailHero, ArtworkHeroBackdrop, artworkDominantColor | AlbumDetailsContentTest, AlbumDetailsPresentationTest, AlbumDetailsDiscOrderTest; other detail consumers | Narrow geometry, primary callbacks, manual PNG precedence, disc/track ordering; no decoder work added. |
| sync/SyncDatabase.libraryArtistsFrom and artists flow call site | ArtistDefaultArtworkTest; existing library projection tests | Year/date/id priority, missing artwork, manual override/custom flag, collaborations, shared album cover, track-path fallback. No schema migration. |
| screens/LibraryArtistsContent, components/ArtistRow | ArtistListPresentationTest; existing LibraryArtistsContentTest/ArtistRowTest | State transitions, entity identity, circular clipping, local/supplied portrait priority, disposal/virtualization. |
| screens/ArtistDetailsContent | ArtistDetailsContentTest, ArtistDetailsPresentationTest | Optional slots, manual override/removal, local cover fallback, ordered queue and local album action; narrow/font matrix. |
| screens/LibraryPlaylistsContent, components/PlaylistRow, PlaylistArtwork, screens/CreatePlaylistDialog | PlaylistListPresentationTest; existing LibraryPlaylistsContentTest/FavoritesArtworkTest | Favorites identity, editor validation/clear/save, rename/delete/queue order, local PNG, large list, long names. Picker activity-result path is not newly exercised. |
| screens/PlaylistDetailsContent | PlaylistDetailsPresentationTest; existing PlaylistDetailsContentTest and reorder host tests | Square narrow cover, empty/unavailable, Favorites restrictions, real drag and anchor callback, post-reorder actions. |
| screens/LibrarySearchContent/searchSection | SearchPresentationTest, LibrarySearchContentTest, SearchRestorationTest, LibrarySearchArabicTest | Controlled query/clear/Done, loading/empty, five destinations, three-track height. Restoration observation is defective: H1. |
| App.searchListState and navigation/AppDestinationContent forwarding | SearchRestorationTest exercises a substitute host; existing navigation characterization covers general navigation | Missing regression coverage of this particular new wiring: M1. |
| components/GlassToggle | GlassToggleTest; unchanged AppearanceContentTest, IntegrationContentTest and CrossfadeDurationSliderTest named in HANDOFF | Tap/semantic click/drag/RTL, disabled/cancel, external state, static pixel geometry, policy changes. New suite does not execute actual settings persistence. |
| AppearanceContent, PlaybackSettingsContent, IntegrationContent, TagSeparatorsContent switch substitutions | Shared toggle suite plus existing screen/settings suites | Callback expressions are retained. No new persistence implementation or player change. |
| TrackInfoContent quality-badge visibility | HomeContentTest.shelfKeepsExplicitAndQualityBadges; existing track-info tests | Visibility exposure supports Home's existing badge, with asserted lossless text. |

New tests use literal fixture IDs/spec priorities rather than self-computed expected hashes or PCM. No new audio fixtures, sample rates, end-of-stream, seek operations, platform codec paths or Room migrations are present; those checklist cases do not apply to this range. No added assume/skip was found in the changed tests. UI error states are tested where exposed; most reviewed local screens have no separate error state. The asynchronous work exercised here is thumbnail loading and Compose animation/reordering, not playback.

## 2. Critical gaps

No CRITICAL finding. One HIGH test-assumption defect blocks trusting the pending phone gate.

**[HIGH] H1 — androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/screens/SearchRestorationTest.kt:56 — Scroll observations are stale because SideEffect is not a scroll observer.**

Scenario: type the nonblank query, scroll the real results list to lazy index 8 at line 64, then hide/show search at lines 67/69. The enclosing composition reads query and showingSearch, but firstVisibleItemIndex/firstVisibleItemScrollOffset are read only inside SideEffect (lines 57–58). SideEffect runs after that composition completes; those reads do not subscribe the composition to scroll changes. LibrarySearchContent hands the state to LazyColumn at production LibrarySearchContent.kt:85, so scrolling can update/remeasure that child without rerunning the test's enclosing SideEffect. Consequently line 66 can capture the old (0,0); hiding search forces the enclosing composition to run and copies the actual nonzero scroll pair. The assertion at line 71 then fails even though the supplied list state was preserved correctly. If incidental recomposition refreshes the pair, this instead becomes timing-dependent. Line 74 repeats the defective comparison.

Production trace: App.kt:156 owns the list state; AppDestinationContent.kt:567–571 forwards it; LibrarySearchContent.kt:85 supplies it directly to LazyColumn. None promises a parent recomposition on scroll. This defect is in the test, not evidence of a production scroll-loss bug.

Suggested fix: capture the actual LazyListState reference during composition and read its current index/offset directly inside each runOnIdle. Assert that the pre-hide position is nonzero and that Composer result is visible, then compare the actual state across hide/show and saved-state restoration. Alternatively explicitly collect snapshotFlow, with an await tied to the observed position. Do not change the expected position to make this pass.

## 3. Weak tests / gaps

**[MEDIUM] M1 — androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/screens/SearchRestorationTest.kt:51 — The restoration host bypasses the new production ownership and forwarding.**

Risk → missing test → expected assertion: removing `listState = searchListState` from AppDestinationContent.kt:569, or resetting the App-owned state while search is retained, would lose position after opening a result and returning. This test still passes after H1 is repaired because it supplies its own rememberLazyListState directly and merely toggles a local boolean. Add an App/navigation integration case: enter search, scroll, open a result, return, assert the same query and actual list position; then pop search and reopen to assert the established page-generation reset behavior. Existing general navigation characterization does not exercise the newly added search list state.

Other suggestions, not findings: retain the planned M5 accessibility/landscape sweep and M3 slot wiring tests. Current M2 fixtures deliberately omit enrichment fetchers/video; requesting those implementations here would exceed this milestone.

## 4. Mechanical traces

The following trace definitions are shared by the per-assertion ledger. Each ledger row names the assertion/await and its applicable production trace; repeated invocations and parameterized runs use the same row. Runtime display/pixel/font results are NOT RUN, not inferred PASS.

### N — Number / fixture evaluation

All fixtures here are in-source Kotlin values or test-created 16×16 PNGs in targetContext.cacheDir. No external manifest/header/hash fixture is used, so there is no unavailable binary oracle to substitute. Expected source is the literal test fixture or the named spec decision; pixel truth is Android solid RED/GREEN/BLUE/WHITE/BLACK, independent of production image selection.

| ID | Production formula and fixture evaluation; source of expectation |
| --- | --- |
| N-G | GlassToggle.kt:77,123,135,143–145,200: dragWidth=20*density px; target=64×48 dp; track=64×28 dp; thumb=40×24 dp centered vertically at y=12..36. LTR off x=2..42, on x=22..62; RTL reverses physical side. At x=.15×64=9.6 or .85×64=54.4, y=24, the selected thumb side is WHITE. x=32,y=.3×48=14.4 and .7×48=33.6 lie inside both thumb positions. On-state track at x=.1×64=6.4 is primary BLUE. Disabled WHITE over BLACK = .38×1+.62×0=.38 per channel (tolerance .01). Constants are the intended toggle geometry/D-21; no oracle script. Calls are 1 per completed tap/drag, 2 after two gestures, 0 on disabled/cancel/external state. |
| N-H | HomeContent.kt:201 groups by nonblank albumId, otherwise track id, preserving input order. [t0(a0),t1(a1),t2(a0)] → [[t0,t2],[t1]], count=2 for first hero, representative=t0, full queue remains all 3. HomeContent.kt:213–214,230 uses literal year 2026 and plural count 2 → localized count + ' · 2026'. Literal metadata supplies explicit=true, format=flac → explicit/lossless badges. With 1000 distinct albums t999 is outside initially composed horizontal items. Title index is 0, recent index 1, keep-listening index 2, then most-played/forgotten. Index>0 gives collapsed=true at HomeContent.kt:82,189. For restored initial index 2 it is true. At font 2, shelf card minimum=88×2=176 dp; at fonts 1/1.3 minima=88/114.4 dp (HomeContent.kt:262–270). Row count is explicitly homeTrackSectionRows(1)=1, height=discCardHeight(width), no two-row gap. |
| N-V | LibraryVirtualList.kt:138–165: with filter and leading playback controls, data index k has lazy index k+2. Album list index 5002 → album5000; track list index 5002 → track5000. DiscGrid.kt:35–36 chunks by 2 with key first id: grid index 2502 minus 2 headers=2500 → [album5000,album5001], key album-5000. Artist list has filter only: 501−1=500 → artist-500. Playlist list has neither filter nor leading controls: 500→p500,900→p900. LibraryVirtualList.kt:164/203 renders N−1 dividers: 2 rows→1 divider. Bounds 1..40,1..50,1..30 are test budgets for composed/active visible rows, not exact calculated counts; 320 dp constrained viewport and 48/64/126 dp minimum row sizes fit comfortably below them. |
| N-R | AlbumRow.kt: artwork 48 dp; ArtistRow.kt: artwork 48 dp; PlaylistRow.kt:69 defaults to 110 dp. Artist portrait center from row start=24+48/2=48 dp; playlist center=24+110/2=79 dp, mirrored in RTL. Pixel sampling therefore hits the generated solid artwork, not text. ArtistRow clips CircleShape: center stays RED, corner [1,1] exposes WHITE fixture parent. Captured portrait width=height=48*density (integer rounding). |
| N-T | AlbumRow/ArtistRow/TrackRow/GenreRow/ComposerRow/PlaylistRow explicitly set maxLines=2; long fixture strings at constrained 280/360 dp are intended to fill both lines, especially font 2. Actual lineCount and paragraph height depend on installed font shaping and are NOT RUN; no claimed numerical phone evaluation. Layout height uses wrap/min height, not a fixed text-height cap. The test's height>=multiParagraph.height checks real layout metrics rather than recomputing its own expected height. Open/menu targets use 48 dp; DetailHero.kt:158,181 also provides 48 dp targets. |
| N-D | DetailHero.kt:132 artwork width=min(artworkSize,availableWidth), height=width. PlaylistDetailsContent.kt:142–152 pads a 280 dp fixture by 24+24 → availableWidth=232 dp; expected square <=232 dp (rounding tolerance .5 px). PlaylistArtwork (PlaylistRow.kt:77–85) uses mosaic iff >=4 paths; fixture has exactly 4. PNG source is 16×16 solid RED/GREEN; scaled center remains 1,0,0 / 0,1,0 with >.95/<.05 thresholds. |
| N-A | AlbumDetailsViewModel.kt:63–67 sorts positive disc then track number, missing tags as Int.MAX_VALUE. [d2(2,1),d1b(1,2),d1a(1,1)] → [d1a,d1b,d2]. Host fixture adds untagged → last; albumPlaybackRequestFor at :59 gives indexOf(disc2)=2 and same four IDs. Expected order is literal disc/track truth. |
| N-P | PlaylistDetailsContent.kt:99,177–179,237–238 uses ordered track IDs and N+1 detail dividers when N>0 (0 when N=0). GenreDetailsContent.kt/ComposerDetailsContent.kt: album loop adds top divider once and divider after each item → N+1: 1 album→2,0→0. Reorder from [a,b] to [b,a]: lazy indices subtract hero offset 1 (PlaylistDetailsContent.kt:283); playlistMoveAnchors at PlaylistDetailsViewModel.kt:122–124 gives previous=b,next=null for a; expected one Triple(a,b,null). |
| N-S | LibrarySearchContent.kt:156–189 emits 2 vertical items per nonempty category, five categories → indices 0..9, headers=0/2/4/6/8; composer content=9. ScrollToIndex(8) targets its header and exposes its following card subject to end clamp. With three tracks, chunked(3) produces exactly one column holding tracks 0,1,2. Width=min(320,280−2×24)=232 dp, wrap-content height (no previous 183 dp cap). Destination callbacks are literal t,a,r,p,c. Saved scroll expected pair is observational, not a fixed index because end clamp depends on viewport; H1 explains why the observation is invalid. |
| N-E | CreatePlaylistDialog.kt:103 valid=!showNameInput || trim(name).isNotEmpty(). Empty/'   '→false; '   Evening music  ' or Arabic equivalent→true and trimmed name. No picker result→uri=null. Hidden Favorites name→valid=true,name=''; clear sets uri=null,clearArtwork=true at :139. Rename '  Renamed  '→'Renamed'. PlaylistContextMenu.kt:30 excludes delete/reorder for favorites; queue uses literal [b,a]. Delete callback occurs only after confirmation. |
| N-F | SyncDatabase.kt:1244–1261 picks max(year,createdAt,id) among resolved album-cover/track-path candidates. 2025>2000 even when date older; missing year=0, so 2000>0; equal/missing years compare ISO fixture dates October>January; equal year/date z>a. Unresolved artwork yields no candidate, empty tracks→empty artists, collaboration yields IDs [a,b] with unchanged compound name. :1285–1288 manual staged path wins and custom=true, otherwise false. :1293–1332 resolves same-album shared path old.jpg even when newest row's key is unresolved; explicit track path is used when album path absent. Expected source D-19 and literal paths; tests do not invoke the projection to compute expected paths. |
| N-Q | LibrarySearchViewModel.kt:103–125 requires each normalized token prefix in some field; Arabic diacritics are stripped, matching only track 'match' for 'ألف كلث' and for candidate-constrained 'ليلة'; 'مفقود' matches nothing. SyncDatabase.kt:1542–1549 NFD/strip marks/token+'*'/join AND produces 'الف* AND كلث*'. Literal Arabic/IDs are independent expected strings. |
| N-L | LibraryContent.kt:44–79 fixes seven destination resource IDs; :87 keys by resource ID and :88 dispatches its callback. Expected callback sequence is the seven independently listed test IDs. Row minimum=72 dp at :96. Restored geometry compares before/after the same saved LazyListState; no invented numeric pixel expectation. |

### E — Event/precondition trace and real callers

| ID | Public entry → required conditions → emitter; test/real caller |
| --- | --- |
| E-G | GlassToggle(checked,onCheckedChange,enabled) → toggleable click or completed enabled drag → callback (:104–120) → fixture updates mutableState → recomposition checked semantics. Cancel resets dragging with no callback (:108); disabled omits detector and disables toggleable. External checked mutation has no callback. Policy requires API>=33 and no transparency/motion/performance fallback (:70–74); only then drawBackdrop (:147). Production callers: AppearanceContent:50, IntegrationContent:125–128, PlaybackSettingsContent:92/132/220/225/361/446/489, TagSeparatorsContent:172. Standalone tests supply all state/callback conditions; actual preference persistence remains existing screen/repository tests. |
| E-H | AppDestinationContent.kt:359 supplies Home data/callbacks → HomeContent loaded/empty branches (:101–114), then hero/shelves. Click uses original queue + representative id (:134), long click toggles menu (:135); TrackContextMenu.kt:187–195 emits queue/favorite callbacks and :153–157 forwards sheet request. Fixtures supply AnchoredPopupMenuHost where menus are opened. Scroll changes collapsed derivedState (:82); supplied initial LazyListState is passed at :88. |
| E-L | AppDestinationContent.kt:769 → LibraryContent seven destinations → row clickable (:88–95) → fixture callback. Tests scroll the existing LazyColumn to each row, then click; callback availability is explicitly supplied. Saved list geometry uses rememberLazyListState from the test host, not a simulated callback. |
| E-V | Collection screen → LibraryVirtualList → empty only if loaded; filtered message chosen by filterQuery. Stable filter lazy item preserves focus on empty at LibraryVirtualList.kt:138–149. Row click and more/long-click hold actual entity; collection wrappers forward track/album IDs. Album actions resolve albumDetailsUiStateFor before AlbumContextMenu (:53–61); literal fixture provides both tracks and album. Production screens are called by AppDestinationContent; tests call the screen directly and supply callbacks/menu host. |
| E-R | ArtistRow portrait!=null → invoke supplied slot; otherwise rememberArtworkThumbnail → asynchronous bitmap → Image, else Person. LibraryArtistsContent.kt:113 forwards the artist-specific slot, :114 forwards actual artist click. Test mutation supplied=false restores local loading path; path=null clears keyed bitmap. Local PNG exists in target cache. DisposableEffect in supplied portrait counts only active lazy items and removes IDs on disposal. |
| E-D | AppDestinationContent detail route → nonnull entity → ArtworkHeroBackdrop + DetailHero → showArtwork branch → supplied artworkContent, loaded bitmap, or fallback at DetailHero.kt:130–137. Primary clicks at :154–170 directly invoke supplied callbacks; no player/coordinator call is needed to count callbacks. Tests omit entities only for unavailable-state cases. ArtistDetailsContent.kt:83–84 invokes background, :118 suppresses portrait if custom, :130–133 invokes about; artist-image menu removal only offered with hasCustomArtwork (:95). Tests supply real PNG, mutable custom flag and explicit add/remove callbacks. |
| E-P | PlaylistDetailsContent nonnull playlist → hero/menu → PlaylistContextMenu onReorder (:168) sets isReordering. Track handles emitted only in that branch (:180–195). Long-press draggable modifier (:276) starts tracking index (:196–199); movement updates ordered IDs (:106–109); stop emits anchors iff index changed (:200–205). Test supplies root touch down/move/up and callback; no omitted ViewModel/coordinator starts the gesture. Done switches to ordinary TrackContextMenu/TrackRow (:157,215–233). Favorites menu gates are PlaylistContextMenu.kt:30–58 and track removal is disabled at PlaylistDetailsContent.kt:220. |
| E-E | LibraryPlaylistsContent row long-click → editing/deletingPlaylist state (:70–71), host menu → EditPlaylistBottomSheet → local name/clear state → valid save (:103–113) → onPlaylistUpdate (:88). Delete confirmation calls provided callback, not the first menu action. Direct editor tests provide onSave/onCreate; chooser is never launched. Fixtures include AnchoredPopupMenuHost for context menus. |
| E-S | AppDestinationContent.kt:567–584 → controlled uiState.query and callback → SistrumTextField BasicTextField(value,onValueChange) (:72–74), clear (:114), Done clearFocus (:91–93). Test feeds callback back into state. Search content branches blank/loading/no-results/results at LibrarySearchContent.kt:86–97; category click emitters :105/129/135/144/148. Tests populate each category and scroll to its header before click. No network, search debounce, DAO or player is necessary for these presentation callbacks. |
| E-B | SearchRestorationTest uses its own rememberSaveable query and rememberLazyListState (:50–51), BackHandler (:52), visibility boolean (:53), then actual screen. StateRestorationTester restores those saved values. Back via instrumentation is handled only after Done clears text focus. Scroll copy has missing observation precondition: H1. Real production ownership differs (App.kt:156, AppDestinationContent.kt:569): M1. |
| E-F | AndroidLibrarySyncStore.artists combines active rows, artwork assets and staged artwork (SyncDatabase.kt:720–730), then libraryArtistsFrom. Host tests directly invoke projection with all three inputs; no flow subscription or mutable staging roundtrip is exercised. This is sufficient for the changed selection algorithm, not proof of DAO delivery. |
| E-A | albumDetailsUiStateFor → sort/filter → albumPlaybackRequestFor → indexOf selected id. Host/device fixtures provide matching album IDs and all track metadata; no playback engine is started or awaited. |
| E-Q | searchTracks → searchLibrary normalized prefix matching; searchFtsMatch separately transforms query. Host fixture supplies two local tracks and candidate set explicitly; it is not a real Room FTS integration test. |

### C — Cost / bounds trace

No asserted media duration, frame count, seek position, hash or four-minute PCM operation exists in these tests. A four-minute audio file would not be opened by these fixtures: audioPath is null or deliberately unavailable. The same screen can load embedded artwork in production when a cached cover is missing; this review does not claim the PNG timeout measures that path.

| Await/time/position | Work on actual fixture; long-file comparison; result |
| --- | --- |
| AlbumDetailsPresentationTest.kt:91 waitUntil(5000) | 16×16 local PNG → bounded thumbnail decode on Dispatchers.IO (TrackRow.kt:74–82), then image branch (DetailHero.kt:136). decodeArtworkBitmaps prioritizes PNG; nonexistent audio path is unused on success. No full-track scan on this fixture. 5s is a scheduling allowance; no measured headroom claim. |
| ArtistDetailsContentTest.kt:183 waitUntil(5000) | Same local 16×16 RED PNG → 480px target and image branch. No audio path. Constant image cost; NOT RUN timing. |
| ArtistDetailsContentTest.kt:220 waitUntil(5000) | Same with solid GREEN PNG. No audio path; NOT RUN timing. |
| ArtistListPresentationTest.kt:147 waitUntil(5000) | New 16×16 PNG → 120px target; poll row-center pixel. Constant image cost, no track IO. |
| ArtistListPresentationTest.kt:152 waitUntil(5000) | Restoring null slot requests same path/cache key; cache may satisfy instantly, otherwise same tiny decode. No track IO. |
| PlaylistListPresentationTest.kt:215 waitUntil(5000) | 16×16 local PNG at 128px target, row center=79dp (N-R). No audio path; constant image cost. |
| PlaylistDetailsPresentationTest.kt:165 advanceEventTime(600) | Synthetic touch holds longer than standard long press, to enable longPressDraggableHandle (:276). This advances input event time, not a 600ms CPU budget or sleep. Device setting with a longer long-press threshold is not exercised. |
| PlaylistDetailsPresentationTest.kt:166 moveTo delayMillis=500 | One drag from first handle center to second on two-item fixture; reorder callback operates on two IDs. No audio decode or fixed CPU deadline. |
| PlaylistDetailsPresentationTest.kt:167 advanceEventTime(300), :170 waitForIdle | Release/settle UI animation then anchor assertion. No explicit performance bound, no omitted start call. On a long playlist list updates/callback index scans are O(N), but no millisecond limit is asserted. |
| GlassToggleTest gestures/capture/idle | Finite springs (GlassToggle.kt:85–95) with Compose auto clock; static fallback uses snapTo. 64dp swipe spans >20dp travel; cancel does not complete. No file-length-dependent work. |
| HomeContentTest large library | Group 1000 distinct albums once O(N), lazy horizontal composition; swipe changes vertical index. Font matrix targets index2 then first shelf. No timing limit. |
| LibraryAlbumsContentTest indices 5002/2502 | O(N) mapping/chunking of 10000 in-memory items, lazy viewport measurement, no artwork/audio paths. Formula N-V matches actual target5000 in both modes. |
| LibraryTracksContentTest index5002 | Lazy viewport around item5000 of10000, no media IO. No time budget. |
| ArtistListPresentationTest index501 | Lazy viewport around artist500 of1000; supplied boxes only. No files. |
| PlaylistListPresentationTest initial500/target900 | Lazy viewport, no file paths in1000-item list. initial500 and target900 are both inside list. No time budget. |
| SearchPresentationTest indices0/2/4/6/8 | Five two-item categories, one result each. Headers have following content; end scroll may clamp. No target exceeds last9. |
| SearchRestorationTest index8 | Same ten-item structure; header8/content9. No audio work. Position itself is valid; observer is not (H1). |
| All performScrollTo/StateRestorationTester cases | Compose semantics scroll/restore local list geometry. There is no decode-from-start or file-duration budget. Actual font-dependent clipping, spring settle times and bounds equality require device execution; NOT RUN. |

The PNGs use targetContext.cacheDir and absolute paths, so they do not depend on MediaStore indexing or scoped-storage grants. No tests silently substitute a fallback for an absent binary fixture. All phone execution remains pending T020.

## 5. Per-assertion / await ledger

Each row is a source assertion or await call (chained calls on one source line are split). Trace references above specify the formula, fixture evaluation, emitter, preconditions, caller and cost. In-source multiline assertions are identified by their call-start line. Helper assertions are listed once and apply to every invocation; loop assertions apply to each iteration. Existing assertions in changed files are included for completeness, even when the range left them untouched.


### GlassToggleTest

Source: `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/components/GlassToggleTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 63: `assertIsOff()` | N-G; E-G; C toggle gestures; tapTogglesAndAnnouncesSwitchState |
| 65: `assertIsOn()` | N-G; E-G; C toggle gestures; tapTogglesAndAnnouncesSwitchState |
| 66: `assertEquals(1, calls) }` | N-G; E-G; C toggle gestures; tapTogglesAndAnnouncesSwitchState |
| 67: `assertIsOff()` | N-G; E-G; C toggle gestures; tapTogglesAndAnnouncesSwitchState |
| 68: `assertEquals(2, calls) }` | N-G; E-G; C toggle gestures; tapTogglesAndAnnouncesSwitchState |
| 73: `assertIsOn()` | N-G; E-G; C toggle gestures; dragTogglesInBothDirections |
| 74: `assertIsOff()` | N-G; E-G; C toggle gestures; dragTogglesInBothDirections |
| 75: `assertEquals(2, calls) }` | N-G; E-G; C toggle gestures; dragTogglesInBothDirections |
| 80: `assertIsOn()` | N-G; E-G; C toggle gestures; rtlDragUsesMirroredDirection |
| 81: `assertIsOff()` | N-G; E-G; C toggle gestures; rtlDragUsesMirroredDirection |
| 82: `assertEquals(2, calls) }` | N-G; E-G; C toggle gestures; rtlDragUsesMirroredDirection |
| 87: `assertIsNotEnabled().performTouchInput { click(); swipeRight() }.` | N-G; E-G; C toggle gestures; disabledIgnoresTapAndDrag |
| 87: `assertIsOff()` | N-G; E-G; C toggle gestures; disabledIgnoresTapAndDrag |
| 88: `assertEquals(0, calls) }` | N-G; E-G; C toggle gestures; disabledIgnoresTapAndDrag |
| 97: `assertIsOff()` | N-G; E-G; C toggle gestures; cancelledDragDoesNotToggle |
| 98: `assertEquals(0, calls) }` | N-G; E-G; C toggle gestures; cancelledDragDoesNotToggle |
| 103: `assertThumb(onRight = false)` | N-G; E-G; C toggle gestures; reduceTransparencyRendersStaticWhiteThumbAndPrimaryTrack |
| 104: `assertIsOn()` | N-G; E-G; C toggle gestures; reduceTransparencyRendersStaticWhiteThumbAndPrimaryTrack |
| 105: `assertThumb(onRight = true)` | N-G; E-G; C toggle gestures; reduceTransparencyRendersStaticWhiteThumbAndPrimaryTrack |
| 107: `assertEquals(Color.Blue, pixels[(pixels.width * 0.1f).toInt(), pixels.height / 2])` | N-G; E-G; C toggle gestures; reduceTransparencyRendersStaticWhiteThumbAndPrimaryTrack |
| 108: `assertNoBackdrop()` | N-G; E-G; C toggle gestures; reduceTransparencyRendersStaticWhiteThumbAndPrimaryTrack |
| 113: `assertThumb(onRight = true)` | N-G; E-G; C toggle gestures; rtlThumbSideIsMirrored |
| 114: `assertIsOn()` | N-G; E-G; C toggle gestures; rtlThumbSideIsMirrored |
| 115: `assertThumb(onRight = false)` | N-G; E-G; C toggle gestures; rtlThumbSideIsMirrored |
| 120: `assertThumb(onRight = false)` | N-G; E-G; C toggle gestures; reducedMotionUsesStaticFallbackInDarkTheme |
| 122: `assertThumb(onRight = true)` | N-G; E-G; C toggle gestures; reducedMotionUsesStaticFallbackInDarkTheme |
| 123: `assertNoBackdrop()` | N-G; E-G; C toggle gestures; reducedMotionUsesStaticFallbackInDarkTheme |
| 128: `assertWidthIsEqualTo(androidx.compose.ui.unit.Dp(64f))` | N-G; E-G; C toggle gestures; fontScaleTwoKeepsFullSizeThumbAndTouchTarget |
| 129: `assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(48f))` | N-G; E-G; C toggle gestures; fontScaleTwoKeepsFullSizeThumbAndTouchTarget |
| 130: `assertThumb(onRight = false)` | N-G; E-G; C toggle gestures; fontScaleTwoKeepsFullSizeThumbAndTouchTarget |
| 132: `assertThumb(onRight = true)` | N-G; E-G; C toggle gestures; fontScaleTwoKeepsFullSizeThumbAndTouchTarget |
| 138: `assertIsOn()` | N-G; E-G; C toggle gestures; externalStateChangesMoveThumbWithoutCallback |
| 139: `assertThumb(onRight = true)` | N-G; E-G; C toggle gestures; externalStateChangesMoveThumbWithoutCallback |
| 140: `assertEquals(0, calls) }` | N-G; E-G; C toggle gestures; externalStateChangesMoveThumbWithoutCallback |
| 145: `assertBackdrop(android.os.Build.VERSION.SDK_INT >= 33)` | N-G; E-G; C toggle gestures; transparencyChangeRemovesLensAndRestoresOnlyOnSupportedApis |
| 147: `assertNoBackdrop()` | N-G; E-G; C toggle gestures; transparencyChangeRemovesLensAndRestoresOnlyOnSupportedApis |
| 148: `assertThumb(onRight = false)` | N-G; E-G; C toggle gestures; transparencyChangeRemovesLensAndRestoresOnlyOnSupportedApis |
| 150: `assertBackdrop(android.os.Build.VERSION.SDK_INT >= 33)` | N-G; E-G; C toggle gestures; transparencyChangeRemovesLensAndRestoresOnlyOnSupportedApis |
| 157: `assertEquals(0.38f, thumb.red, 0.01f)` | N-G; E-G; C toggle gestures; disabledThumbHasSpecifiedAlpha |
| 158: `assertEquals(0.38f, thumb.green, 0.01f)` | N-G; E-G; C toggle gestures; disabledThumbHasSpecifiedAlpha |
| 159: `assertEquals(0.38f, thumb.blue, 0.01f)` | N-G; E-G; C toggle gestures; disabledThumbHasSpecifiedAlpha |
| 167: `assertEquals(Color.White, pixels[(pixels.width * whiteSide).toInt(), pixels.height / 2])` | N-G; E-G; C toggle gestures; assertThumb |
| 168: `assertTrue(Color.White != pixels[(pixels.width * trackSide).toInt(), pixels.height / 2])` | N-G; E-G; C toggle gestures; assertThumb |
| 170: `assertEquals(Color.White, pixels[pixels.width / 2, (pixels.height * 0.3f).toInt()])` | N-G; E-G; C toggle gestures; assertThumb |
| 171: `assertEquals(Color.White, pixels[pixels.width / 2, (pixels.height * 0.7f).toInt()])` | N-G; E-G; C toggle gestures; assertThumb |
| 179: `assertEquals(modifiers.map { it.modifier.javaClass.name }.toString(), expected,` | N-G; E-G; C toggle gestures; assertBackdrop |

### HomeContentTest

Source: `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/components/HomeContentTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 56: `assertIsDisplayed()` | N-H; E-H; C Home; emptyLibraryShowsEmptyStateAfterLoading |
| 57: `assertDoesNotExist()` | N-H; E-H; C Home; emptyLibraryShowsEmptyStateAfterLoading |
| 63: `assertDoesNotExist()` | N-H; E-H; C Home; loadingDoesNotClaimLibraryIsEmpty |
| 73: `assertIsDisplayed().performClick()` | N-H; E-H; C Home; recentHeroPlaysItsRepresentativeFromTheUnchangedRecentQueue |
| 74: `assertEquals(tracks, queue)` | N-H; E-H; C Home; recentHeroPlaysItsRepresentativeFromTheUnchangedRecentQueue |
| 75: `assertEquals("t0", selected)` | N-H; E-H; C Home; recentHeroPlaysItsRepresentativeFromTheUnchangedRecentQueue |
| 78: `assertIsDisplayed()` | N-H; E-H; C Home; recentHeroPlaysItsRepresentativeFromTheUnchangedRecentQueue |
| 79: `assertDoesNotExist()` | N-H; E-H; C Home; recentHeroPlaysItsRepresentativeFromTheUnchangedRecentQueue |
| 84: `assertIsDisplayed()` | N-H; E-H; C Home; largeLibraryIsVirtualizedAndTitleCollapsesOnScroll |
| 85: `assertDoesNotExist()` | N-H; E-H; C Home; largeLibraryIsVirtualizedAndTitleCollapsesOnScroll |
| 87: `assertIsDisplayed()` | N-H; E-H; C Home; largeLibraryIsVirtualizedAndTitleCollapsesOnScroll |
| 99: `assertEquals("t0", selected)` | N-H; E-H; C Home; shelfClickAndLongPressKeepTrackActions |
| 100: `assertEquals(tracks, queue)` | N-H; E-H; C Home; shelfClickAndLongPressKeepTrackActions |
| 104: `assertEquals(tracks[0], next)` | N-H; E-H; C Home; shelfClickAndLongPressKeepTrackActions |
| 110: `assertIsDisplayed()` | N-H; E-H; C Home; shelfKeepsExplicitAndQualityBadges |
| 111: `assertIsDisplayed()` | N-H; E-H; C Home; shelfKeepsExplicitAndQualityBadges |
| 118: `assertIsDisplayed()` | N-H; E-H; C Home; suppliedScrollStateIsUsedWhenHomeReturns |
| 119: `assertDoesNotExist()` | N-H; E-H; C Home; suppliedScrollStateIsUsedWhenHomeReturns |
| 129: `assertEquals(tracks[0], next)` | N-H; E-H; C Home; heroLongPressKeepsTrackActions |
| 145: `assertIsDisplayed()` | N-H; E-H; C Home; heroRemainsClickableAtEveryFontAndCarouselsMirror |
| 147: `assertTrue(clicked)` | N-H; E-H; C Home; heroRemainsClickableAtEveryFontAndCarouselsMirror |
| 150: `assertTrue(b.left < a.left) else` | N-H; E-H; C Home; heroRemainsClickableAtEveryFontAndCarouselsMirror |
| 150: `assertTrue(b.left > a.left)` | N-H; E-H; C Home; heroRemainsClickableAtEveryFontAndCarouselsMirror |
| 151: `assertNoClippedActions(hasTestTag("home-hero-t0"))` | N-H; E-H; C Home; heroRemainsClickableAtEveryFontAndCarouselsMirror |
| 153: `performScrollToIndex(2)` | N-H; E-H; C Home; heroRemainsClickableAtEveryFontAndCarouselsMirror |
| 154: `assertIsDisplayed()` | N-H; E-H; C Home; heroRemainsClickableAtEveryFontAndCarouselsMirror |
| 158: `assertTrue(nextBounds.left < firstBounds.left)` | N-H; E-H; C Home; heroRemainsClickableAtEveryFontAndCarouselsMirror |
| 159: `assertTrue(nextBounds.left > firstBounds.left)` | N-H; E-H; C Home; heroRemainsClickableAtEveryFontAndCarouselsMirror |
| 162: `assertTrue(clicked)` | N-H; E-H; C Home; heroRemainsClickableAtEveryFontAndCarouselsMirror |
| 163: `assertNoClippedActions(hasTestTag("home-track-t0") and` | N-H; E-H; C Home; heroRemainsClickableAtEveryFontAndCarouselsMirror |

### AlbumDetailsPresentationTest

Source: `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/screens/AlbumDetailsPresentationTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 62: `assertExists()` | N-D/N-A/N-T; E-D/E-A; C local PNG; longTitleMissingArtKeepsPrimaryActionsVisibleAtNarrowWidth |
| 63: `assertDoesNotExist()` | N-D/N-A/N-T; E-D/E-A; C local PNG; longTitleMissingArtKeepsPrimaryActionsVisibleAtNarrowWidth |
| 66: `assertIsDisplayed()` | N-D/N-A/N-T; E-D/E-A; C local PNG; longTitleMissingArtKeepsPrimaryActionsVisibleAtNarrowWidth |
| 67: `assertNoClippedActions(play)` | N-D/N-A/N-T; E-D/E-A; C local PNG; longTitleMissingArtKeepsPrimaryActionsVisibleAtNarrowWidth |
| 68: `assertNoClippedActions(shuffle)` | N-D/N-A/N-T; E-D/E-A; C local PNG; longTitleMissingArtKeepsPrimaryActionsVisibleAtNarrowWidth |
| 71: `assertEquals(1, plays)` | N-D/N-A/N-T; E-D/E-A; C local PNG; longTitleMissingArtKeepsPrimaryActionsVisibleAtNarrowWidth |
| 72: `assertEquals(1, shuffles)` | N-D/N-A/N-T; E-D/E-A; C local PNG; longTitleMissingArtKeepsPrimaryActionsVisibleAtNarrowWidth |
| 83: `assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }` | N-D/N-A/N-T; E-D/E-A; C local PNG; localArtworkWinsOverUnavailableEmbeddedArtwork |
| 91: `waitUntil(5_000) { rule.onAllNodes(hasTestTag("detail-hero-artwork-image")).fetchSemanticsNodes().size == 1 }` | N-D/N-A/N-T; E-D/E-A; C local PNG; localArtworkWinsOverUnavailableEmbeddedArtwork |
| 92: `assertIsDisplayed().captureToImage()` | N-D/N-A/N-T; E-D/E-A; C local PNG; localArtworkWinsOverUnavailableEmbeddedArtwork |
| 94: `assertTrue(pixel.red > 0.95f && pixel.green < 0.05f && pixel.blue < 0.05f)` | N-D/N-A/N-T; E-D/E-A; C local PNG; localArtworkWinsOverUnavailableEmbeddedArtwork |
| 114: `assertEquals(listOf("d1a", "d1b", "d2"), state.tracks.map { it.id })` | N-D/N-A/N-T; E-D/E-A; C local PNG; multipleDiscsKeepTheirOrderAndTrackClickIdentity |
| 115: `assertIsDisplayed().performClick()` | N-D/N-A/N-T; E-D/E-A; C local PNG; multipleDiscsKeepTheirOrderAndTrackClickIdentity |
| 116: `assertEquals("d2", selected)` | N-D/N-A/N-T; E-D/E-A; C local PNG; multipleDiscsKeepTheirOrderAndTrackClickIdentity |

### ArtistDetailsContentTest

Source: `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/screens/ArtistDetailsContentTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 55: `assertExists()` | N-D/N-E; E-D/E-V; C local PNG; displaysArtistSummaryAndOpensSelectedAlbum |
| 56: `assertExists()` | N-D/N-E; E-D/E-V; C local PNG; displaysArtistSummaryAndOpensSelectedAlbum |
| 58: `assertCountEquals(3)` | N-D/N-E; E-D/E-V; C local PNG; displaysArtistSummaryAndOpensSelectedAlbum |
| 59: `assertEquals("album", selectedAlbumId)` | N-D/N-E; E-D/E-V; C local PNG; displaysArtistSummaryAndOpensSelectedAlbum |
| 70: `assertExists()` | N-D/N-E; E-D/E-V; C local PNG; displaysUnavailableStateWhenArtistIsMissing |
| 87: `assertCountEquals(2)` | N-D/N-E; E-D/E-V; C local PNG; doesNotDisplayAnAlbumDividerForOneAlbum |
| 118: `assertEquals(listOf("first", "second"), nextIds)` | N-D/N-E; E-D/E-V; C local PNG; artistMoreMenuUsesOrderedTracksForPlayNextAndPlaylist |
| 122: `assertEquals(listOf("first", "second"), playlistIds)` | N-D/N-E; E-D/E-V; C local PNG; artistMoreMenuUsesOrderedTracksForPlayNextAndPlaylist |
| 123: `assertEquals(true, addOnly)` | N-D/N-E; E-D/E-V; C local PNG; artistMoreMenuUsesOrderedTracksForPlayNextAndPlaylist |
| 150: `assertIsDisplayed()` | N-D/N-E; E-D/E-V; C local PNG; optionalSlotsKeepLocalAlbumAndOrderedQueueActions |
| 151: `assertIsDisplayed()` | N-D/N-E; E-D/E-V; C local PNG; optionalSlotsKeepLocalAlbumAndOrderedQueueActions |
| 154: `assertEquals(listOf("b", "a"), queued)` | N-D/N-E; E-D/E-V; C local PNG; optionalSlotsKeepLocalAlbumAndOrderedQueueActions |
| 155: `assertIsDisplayed()` | N-D/N-E; E-D/E-V; C local PNG; optionalSlotsKeepLocalAlbumAndOrderedQueueActions |
| 157: `assertEquals("local-album", selected)` | N-D/N-E; E-D/E-V; C local PNG; optionalSlotsKeepLocalAlbumAndOrderedQueueActions |
| 166: `assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }` | N-D/N-E; E-D/E-V; C local PNG; manualImageWinsAndRemovingItRestoresPortraitSlot |
| 182: `assertDoesNotExist()` | N-D/N-E; E-D/E-V; C local PNG; manualImageWinsAndRemovingItRestoresPortraitSlot |
| 183: `waitUntil(5_000) {` | N-D/N-E; E-D/E-V; C local PNG; manualImageWinsAndRemovingItRestoresPortraitSlot |
| 188: `assertTrue(pixel.red > 0.95f && pixel.green < 0.05f && pixel.blue < 0.05f)` | N-D/N-E; E-D/E-V; C local PNG; manualImageWinsAndRemovingItRestoresPortraitSlot |
| 191: `assertEquals(1, additions)` | N-D/N-E; E-D/E-V; C local PNG; manualImageWinsAndRemovingItRestoresPortraitSlot |
| 194: `assertEquals(1, removals)` | N-D/N-E; E-D/E-V; C local PNG; manualImageWinsAndRemovingItRestoresPortraitSlot |
| 195: `assertIsDisplayed()` | N-D/N-E; E-D/E-V; C local PNG; manualImageWinsAndRemovingItRestoresPortraitSlot |
| 196: `assertDoesNotExist()` | N-D/N-E; E-D/E-V; C local PNG; manualImageWinsAndRemovingItRestoresPortraitSlot |
| 198: `assertDoesNotExist()` | N-D/N-E; E-D/E-V; C local PNG; manualImageWinsAndRemovingItRestoresPortraitSlot |
| 210: `assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }` | N-D/N-E; E-D/E-V; C local PNG; nullPortraitUsesProjectedNewestTrackCover |
| 220: `waitUntil(5_000) {` | N-D/N-E; E-D/E-V; C local PNG; nullPortraitUsesProjectedNewestTrackCover |
| 225: `assertTrue(pixel.green > 0.95f && pixel.red < 0.05f && pixel.blue < 0.05f)` | N-D/N-E; E-D/E-V; C local PNG; nullPortraitUsesProjectedNewestTrackCover |

### ArtistDetailsPresentationTest

Source: `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/screens/ArtistDetailsPresentationTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 58: `assertExists()` | N-D/N-T; E-D; C scroll; longTitleMissingArtKeepsPrimaryActionsVisibleAtNarrowWidth |
| 59: `assertDoesNotExist()` | N-D/N-T; E-D; C scroll; longTitleMissingArtKeepsPrimaryActionsVisibleAtNarrowWidth |
| 62: `assertIsDisplayed()` | N-D/N-T; E-D; C scroll; longTitleMissingArtKeepsPrimaryActionsVisibleAtNarrowWidth |
| 63: `assertNoClippedActions(play)` | N-D/N-T; E-D; C scroll; longTitleMissingArtKeepsPrimaryActionsVisibleAtNarrowWidth |
| 64: `assertNoClippedActions(shuffle)` | N-D/N-T; E-D; C scroll; longTitleMissingArtKeepsPrimaryActionsVisibleAtNarrowWidth |
| 67: `assertEquals(1, plays)` | N-D/N-T; E-D; C scroll; longTitleMissingArtKeepsPrimaryActionsVisibleAtNarrowWidth |
| 68: `assertEquals(1, shuffles)` | N-D/N-T; E-D; C scroll; longTitleMissingArtKeepsPrimaryActionsVisibleAtNarrowWidth |

### ArtistListPresentationTest

Source: `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/screens/ArtistListPresentationTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 68: `assertDoesNotExist()` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; loadingEmptyFilteredAndContentStatesKeepArtistIdentity |
| 69: `assertDoesNotExist()` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; loadingEmptyFilteredAndContentStatesKeepArtistIdentity |
| 71: `assertIsDisplayed()` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; loadingEmptyFilteredAndContentStatesKeepArtistIdentity |
| 73: `assertIsDisplayed()` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; loadingEmptyFilteredAndContentStatesKeepArtistIdentity |
| 74: `assertDoesNotExist()` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; loadingEmptyFilteredAndContentStatesKeepArtistIdentity |
| 80: `assertDoesNotExist()` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; loadingEmptyFilteredAndContentStatesKeepArtistIdentity |
| 81: `assertCountEquals(1)` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; loadingEmptyFilteredAndContentStatesKeepArtistIdentity |
| 83: `assertEquals("first", selected)` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; loadingEmptyFilteredAndContentStatesKeepArtistIdentity |
| 84: `assertNoClippedActions(hasContentDescription(label(R.string.artist_row_open)))` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; loadingEmptyFilteredAndContentStatesKeepArtistIdentity |
| 101: `assertIsDisplayed().fetchSemanticsNode()` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; suppliedPortraitIsRoundAndLongNameKeepsOpenActionVisible |
| 103: `assertEquals(2, layouts.single().layoutInput.maxLines)` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; suppliedPortraitIsRoundAndLongNameKeepsOpenActionVisible |
| 104: `assertEquals(2, layouts.single().lineCount)` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; suppliedPortraitIsRoundAndLongNameKeepsOpenActionVisible |
| 105: `assertNoClippedActions(hasContentDescription(label(R.string.artist_row_open)))` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; suppliedPortraitIsRoundAndLongNameKeepsOpenActionVisible |
| 108: `assertEquals(2, clicks)` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; suppliedPortraitIsRoundAndLongNameKeepsOpenActionVisible |
| 110: `assertEquals(image.width, image.height)` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; suppliedPortraitIsRoundAndLongNameKeepsOpenActionVisible |
| 112: `assertTrue(pixels[image.width / 2, image.height / 2].red > 0.95f)` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; suppliedPortraitIsRoundAndLongNameKeepsOpenActionVisible |
| 113: `assertTrue(pixels[image.width / 2, image.height / 2].green < 0.05f)` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; suppliedPortraitIsRoundAndLongNameKeepsOpenActionVisible |
| 115: `assertTrue(pixels[1, 1].green > 0.95f)` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; suppliedPortraitIsRoundAndLongNameKeepsOpenActionVisible |
| 126: `assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; portraitOverridesLocalArtworkAndEmptySlotRetainsOfflineFallbacks |
| 147: `waitUntil(5_000) { centerIsRed() }` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; centerIsRed |
| 150: `assertTrue(suppliedPixel.blue > 0.95f && suppliedPixel.red < 0.05f)` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; centerIsRed |
| 152: `waitUntil(5_000) { centerIsRed() }` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; centerIsRed |
| 154: `assertTrue(!centerIsRed())` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; centerIsRed |
| 155: `assertIsDisplayed()` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; centerIsRed |
| 179: `assertIsDisplayed()` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; listForwardsEachArtistToPortraitWithoutComposingTheWholeLibrary |
| 180: `assertTrue(active.size in 1..50) }` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; listForwardsEachArtistToPortraitWithoutComposingTheWholeLibrary |
| 182: `performScrollToIndex(501)` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; listForwardsEachArtistToPortraitWithoutComposingTheWholeLibrary |
| 183: `assertIsDisplayed()` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; listForwardsEachArtistToPortraitWithoutComposingTheWholeLibrary |
| 185: `assertEquals("artist-500", selected)` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; listForwardsEachArtistToPortraitWithoutComposingTheWholeLibrary |
| 186: `assertTrue(active.size in 1..50) }` | N-V/N-R/N-T; E-V/E-R; C artist list/local PNG; listForwardsEachArtistToPortraitWithoutComposingTheWholeLibrary |

### GenreComposerContentMatrixTest

Source: `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/screens/GenreComposerContentMatrixTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 61: `assertDoesNotExist()` | N-V/N-P/N-T; E-V/E-D; C scroll; genreListLoadingEmptyFilteredAndContentStates |
| 62: `assertDoesNotExist()` | N-V/N-P/N-T; E-V/E-D; C scroll; genreListLoadingEmptyFilteredAndContentStates |
| 64: `assertIsDisplayed()` | N-V/N-P/N-T; E-V/E-D; C scroll; genreListLoadingEmptyFilteredAndContentStates |
| 66: `assertIsDisplayed()` | N-V/N-P/N-T; E-V/E-D; C scroll; genreListLoadingEmptyFilteredAndContentStates |
| 67: `assertDoesNotExist()` | N-V/N-P/N-T; E-V/E-D; C scroll; genreListLoadingEmptyFilteredAndContentStates |
| 73: `assertDoesNotExist()` | N-V/N-P/N-T; E-V/E-D; C scroll; genreListLoadingEmptyFilteredAndContentStates |
| 74: `assertCountEquals(1)` | N-V/N-P/N-T; E-V/E-D; C scroll; genreListLoadingEmptyFilteredAndContentStates |
| 76: `assertEquals("first", selected)` | N-V/N-P/N-T; E-V/E-D; C scroll; genreListLoadingEmptyFilteredAndContentStates |
| 77: `assertNoClippedActions(hasContentDescription(label(R.string.genre_row_open)))` | N-V/N-P/N-T; E-V/E-D; C scroll; genreListLoadingEmptyFilteredAndContentStates |
| 95: `assertIsDisplayed().fetchSemanticsNode()` | N-V/N-P/N-T; E-V/E-D; C scroll; genreLongNameWrapsAndKeepsOpenActionVisible |
| 97: `assertEquals(2, layouts.single().layoutInput.maxLines)` | N-V/N-P/N-T; E-V/E-D; C scroll; genreLongNameWrapsAndKeepsOpenActionVisible |
| 98: `assertEquals(2, layouts.single().lineCount)` | N-V/N-P/N-T; E-V/E-D; C scroll; genreLongNameWrapsAndKeepsOpenActionVisible |
| 99: `assertTrue(layouts.single().size.height >= layouts.single().multiParagraph.height)` | N-V/N-P/N-T; E-V/E-D; C scroll; genreLongNameWrapsAndKeepsOpenActionVisible |
| 100: `assertNoClippedActions(hasContentDescription(label(R.string.genre_row_open)))` | N-V/N-P/N-T; E-V/E-D; C scroll; genreLongNameWrapsAndKeepsOpenActionVisible |
| 102: `assertEquals("long", selected)` | N-V/N-P/N-T; E-V/E-D; C scroll; genreLongNameWrapsAndKeepsOpenActionVisible |
| 115: `assertIsDisplayed()` | N-V/N-P/N-T; E-V/E-D; C scroll; genreDetailsMissingEmptyAndAlbumStates |
| 117: `assertDoesNotExist()` | N-V/N-P/N-T; E-V/E-D; C scroll; genreDetailsMissingEmptyAndAlbumStates |
| 118: `assertIsDisplayed()` | N-V/N-P/N-T; E-V/E-D; C scroll; genreDetailsMissingEmptyAndAlbumStates |
| 119: `assertCountEquals(0)` | N-V/N-P/N-T; E-V/E-D; C scroll; genreDetailsMissingEmptyAndAlbumStates |
| 123: `assertIsDisplayed().performClick()` | N-V/N-P/N-T; E-V/E-D; C scroll; genreDetailsMissingEmptyAndAlbumStates |
| 124: `assertEquals("album", selected)` | N-V/N-P/N-T; E-V/E-D; C scroll; genreDetailsMissingEmptyAndAlbumStates |
| 125: `assertCountEquals(2)` | N-V/N-P/N-T; E-V/E-D; C scroll; genreDetailsMissingEmptyAndAlbumStates |
| 126: `assertNoClippedActions(hasContentDescription(label(R.string.album_row_open)))` | N-V/N-P/N-T; E-V/E-D; C scroll; genreDetailsMissingEmptyAndAlbumStates |
| 138: `assertDoesNotExist()` | N-V/N-P/N-T; E-V/E-D; C scroll; composerListLoadingEmptyFilteredAndContentStates |
| 139: `assertDoesNotExist()` | N-V/N-P/N-T; E-V/E-D; C scroll; composerListLoadingEmptyFilteredAndContentStates |
| 141: `assertIsDisplayed()` | N-V/N-P/N-T; E-V/E-D; C scroll; composerListLoadingEmptyFilteredAndContentStates |
| 143: `assertIsDisplayed()` | N-V/N-P/N-T; E-V/E-D; C scroll; composerListLoadingEmptyFilteredAndContentStates |
| 144: `assertDoesNotExist()` | N-V/N-P/N-T; E-V/E-D; C scroll; composerListLoadingEmptyFilteredAndContentStates |
| 150: `assertDoesNotExist()` | N-V/N-P/N-T; E-V/E-D; C scroll; composerListLoadingEmptyFilteredAndContentStates |
| 151: `assertCountEquals(1)` | N-V/N-P/N-T; E-V/E-D; C scroll; composerListLoadingEmptyFilteredAndContentStates |
| 153: `assertEquals("first", selected)` | N-V/N-P/N-T; E-V/E-D; C scroll; composerListLoadingEmptyFilteredAndContentStates |
| 154: `assertNoClippedActions(hasContentDescription(label(R.string.composer_row_open)))` | N-V/N-P/N-T; E-V/E-D; C scroll; composerListLoadingEmptyFilteredAndContentStates |
| 172: `assertIsDisplayed().fetchSemanticsNode()` | N-V/N-P/N-T; E-V/E-D; C scroll; composerLongNameWrapsAndKeepsOpenActionVisible |
| 174: `assertEquals(2, layouts.single().layoutInput.maxLines)` | N-V/N-P/N-T; E-V/E-D; C scroll; composerLongNameWrapsAndKeepsOpenActionVisible |
| 175: `assertEquals(2, layouts.single().lineCount)` | N-V/N-P/N-T; E-V/E-D; C scroll; composerLongNameWrapsAndKeepsOpenActionVisible |
| 176: `assertTrue(layouts.single().size.height >= layouts.single().multiParagraph.height)` | N-V/N-P/N-T; E-V/E-D; C scroll; composerLongNameWrapsAndKeepsOpenActionVisible |
| 177: `assertNoClippedActions(hasContentDescription(label(R.string.composer_row_open)))` | N-V/N-P/N-T; E-V/E-D; C scroll; composerLongNameWrapsAndKeepsOpenActionVisible |
| 179: `assertEquals("long", selected)` | N-V/N-P/N-T; E-V/E-D; C scroll; composerLongNameWrapsAndKeepsOpenActionVisible |
| 192: `assertIsDisplayed()` | N-V/N-P/N-T; E-V/E-D; C scroll; composerDetailsMissingEmptyAndAlbumStates |
| 194: `assertDoesNotExist()` | N-V/N-P/N-T; E-V/E-D; C scroll; composerDetailsMissingEmptyAndAlbumStates |
| 195: `assertIsDisplayed()` | N-V/N-P/N-T; E-V/E-D; C scroll; composerDetailsMissingEmptyAndAlbumStates |
| 196: `assertCountEquals(0)` | N-V/N-P/N-T; E-V/E-D; C scroll; composerDetailsMissingEmptyAndAlbumStates |
| 200: `assertIsDisplayed().performClick()` | N-V/N-P/N-T; E-V/E-D; C scroll; composerDetailsMissingEmptyAndAlbumStates |
| 201: `assertEquals("album", selected)` | N-V/N-P/N-T; E-V/E-D; C scroll; composerDetailsMissingEmptyAndAlbumStates |
| 202: `assertCountEquals(2)` | N-V/N-P/N-T; E-V/E-D; C scroll; composerDetailsMissingEmptyAndAlbumStates |
| 203: `assertNoClippedActions(hasContentDescription(label(R.string.album_row_open)))` | N-V/N-P/N-T; E-V/E-D; C scroll; composerDetailsMissingEmptyAndAlbumStates |

### LibraryAlbumsContentTest

Source: `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/screens/LibraryAlbumsContentTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 55: `assertExists()` | N-V/N-T/N-A; E-V; C album list/grid; displaysEmptyStateWhenNoAlbums |
| 72: `assertExists()` | N-V/N-T/N-A; E-V; C album list/grid; displaysAlbumsInVirtualizedRowsAndUsesUnknownArtistFallback |
| 73: `assertExists()` | N-V/N-T/N-A; E-V; C album list/grid; displaysAlbumsInVirtualizedRowsAndUsesUnknownArtistFallback |
| 74: `assertExists()` | N-V/N-T/N-A; E-V; C album list/grid; displaysAlbumsInVirtualizedRowsAndUsesUnknownArtistFallback |
| 75: `assertCountEquals(1)` | N-V/N-T/N-A; E-V; C album list/grid; displaysAlbumsInVirtualizedRowsAndUsesUnknownArtistFallback |
| 91: `assertEquals(false, playMode)` | N-V/N-T/N-A; E-V; C album list/grid; playbackActionsAppearForAlbumsAndDispatchTheirMode |
| 93: `assertEquals(true, playMode)` | N-V/N-T/N-A; E-V; C album list/grid; playbackActionsAppearForAlbumsAndDispatchTheirMode |
| 115: `assertExists()` | N-V/N-T/N-A; E-V; C album list/grid; holdingAnAlbumOpensItsContextMenu |
| 116: `assertExists()` | N-V/N-T/N-A; E-V; C album list/grid; holdingAnAlbumOpensItsContextMenu |
| 118: `assertEquals(` | N-V/N-T/N-A; E-V; C album list/grid; holdingAnAlbumOpensItsContextMenu |
| 140: `assertExists()` | N-V/N-T/N-A; E-V; C album list/grid; displaysAlbumsInTwoColumnGridWithoutListDividers |
| 141: `assertExists()` | N-V/N-T/N-A; E-V; C album list/grid; displaysAlbumsInTwoColumnGridWithoutListDividers |
| 142: `assertExists()` | N-V/N-T/N-A; E-V; C album list/grid; displaysAlbumsInTwoColumnGridWithoutListDividers |
| 143: `assertCountEquals(0)` | N-V/N-T/N-A; E-V; C album list/grid; displaysAlbumsInTwoColumnGridWithoutListDividers |
| 152: `assertDoesNotExist()` | N-V/N-T/N-A; E-V; C album list/grid; loadingAndFilteredEmptyStatesStayDistinct |
| 154: `assertIsDisplayed()` | N-V/N-T/N-A; E-V; C album list/grid; loadingAndFilteredEmptyStatesStayDistinct |
| 155: `assertDoesNotExist()` | N-V/N-T/N-A; E-V; C album list/grid; loadingAndFilteredEmptyStatesStayDistinct |
| 191: `assertEquals(` | N-V/N-T/N-A; E-V; C album list/grid; reorderedGridKeepsAlbumSelectionAndCollectionActions |
| 217: `assertTrue("Composed album cards/rows: $count", count in 1..40)` | N-V/N-T/N-A; E-V; C album list/grid; assertBounded |
| 219: `assertBounded()` | N-V/N-T/N-A; E-V; C album list/grid; assertBounded |
| 220: `performScrollToIndex(5_002)` | N-V/N-T/N-A; E-V; C album list/grid; assertBounded |
| 221: `assertIsDisplayed()` | N-V/N-T/N-A; E-V; C album list/grid; assertBounded |
| 222: `assertEquals("album-5000", listState.layoutInfo.visibleItemsInfo.first().key) }` | N-V/N-T/N-A; E-V; C album list/grid; assertBounded |
| 223: `assertDoesNotExist()` | N-V/N-T/N-A; E-V; C album list/grid; assertBounded |
| 224: `assertBounded()` | N-V/N-T/N-A; E-V; C album list/grid; assertBounded |
| 226: `performScrollToIndex(2_502)` | N-V/N-T/N-A; E-V; C album list/grid; assertBounded |
| 227: `assertIsDisplayed()` | N-V/N-T/N-A; E-V; C album list/grid; assertBounded |
| 228: `assertEquals("album-5000", listState.layoutInfo.visibleItemsInfo.first().key) }` | N-V/N-T/N-A; E-V; C album list/grid; assertBounded |
| 229: `assertDoesNotExist()` | N-V/N-T/N-A; E-V; C album list/grid; assertBounded |
| 230: `assertBounded()` | N-V/N-T/N-A; E-V; C album list/grid; assertBounded |
| 233: `assertIsDisplayed()` | N-V/N-T/N-A; E-V; C album list/grid; assertBounded |
| 234: `assertEquals(before, composeTestRule.onNodeWithText("Large album 5000").fetchSemanticsNode().boundsInRoot)` | N-V/N-T/N-A; E-V; C album list/grid; assertBounded |
| 235: `assertBounded()` | N-V/N-T/N-A; E-V; C album list/grid; assertBounded |
| 259: `assertEquals(2, layouts.single().lineCount)` | N-V/N-T/N-A; E-V; C album list/grid; longArabicTitleUsesTwoLinesAndKeepsTheAlbumActionVisibleAtLargeFont |
| 260: `assertIsDisplayed()` | N-V/N-T/N-A; E-V; C album list/grid; longArabicTitleUsesTwoLinesAndKeepsTheAlbumActionVisibleAtLargeFont |

### LibraryContentTest

Source: `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/screens/LibraryContentTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 71: `assertIsDisplayed().performClick()` | N-L; E-L; C scroll/restore; libraryContentDisplaysActionListItems |
| 72: `assertTrue(searchClicked)` | N-L; E-L; C scroll/restore; libraryContentDisplaysActionListItems |
| 74: `assertIsDisplayed().performClick()` | N-L; E-L; C scroll/restore; libraryContentDisplaysActionListItems |
| 75: `assertTrue(artistsClicked)` | N-L; E-L; C scroll/restore; libraryContentDisplaysActionListItems |
| 77: `assertIsDisplayed().performClick()` | N-L; E-L; C scroll/restore; libraryContentDisplaysActionListItems |
| 78: `assertTrue(albumsClicked)` | N-L; E-L; C scroll/restore; libraryContentDisplaysActionListItems |
| 80: `assertIsDisplayed().performClick()` | N-L; E-L; C scroll/restore; libraryContentDisplaysActionListItems |
| 81: `assertTrue(tracksClicked)` | N-L; E-L; C scroll/restore; libraryContentDisplaysActionListItems |
| 83: `assertIsDisplayed().performClick()` | N-L; E-L; C scroll/restore; libraryContentDisplaysActionListItems |
| 84: `assertTrue(genresClicked)` | N-L; E-L; C scroll/restore; libraryContentDisplaysActionListItems |
| 86: `assertIsDisplayed().performClick()` | N-L; E-L; C scroll/restore; libraryContentDisplaysActionListItems |
| 87: `assertTrue(composersClicked)` | N-L; E-L; C scroll/restore; libraryContentDisplaysActionListItems |
| 89: `assertIsDisplayed().performClick()` | N-L; E-L; C scroll/restore; libraryContentDisplaysActionListItems |
| 90: `assertTrue(playlistsClicked)` | N-L; E-L; C scroll/restore; libraryContentDisplaysActionListItems |
| 113: `assertIsDisplayed()` | N-H; E-H; C Home; libraryContentRendersRecentTracksGridAndTriggersTrackClick |
| 114: `assertIsDisplayed()` | N-H; E-H; C Home; libraryContentRendersRecentTracksGridAndTriggersTrackClick |
| 115: `performScrollToIndex(1)` | N-H; E-H; C Home; libraryContentRendersRecentTracksGridAndTriggersTrackClick |
| 116: `assertIsDisplayed().performClick()` | N-H; E-H; C Home; libraryContentRendersRecentTracksGridAndTriggersTrackClick |
| 118: `assertEquals("r2", clickedTrackId)` | N-H; E-H; C Home; libraryContentRendersRecentTracksGridAndTriggersTrackClick |
| 134: `assertIsDisplayed()` | N-H; E-H; C Home; holdingRecentlyAddedTrackOpensContextMenu |
| 153: `assertEquals(TrackContextBottomSheetRequest.Info(track), request)` | N-H; E-H; C Home; trackInfoSheetOpensFromRecentlyAddedGrid |
| 160: `assertIsDisplayed()` | N-H; E-H; C Home; emptyLibraryKeepsDestinationsWithoutRecentlyAdded |
| 161: `assertDoesNotExist()` | N-H; E-H; C Home; emptyLibraryKeepsDestinationsWithoutRecentlyAdded |
| 174: `assertIsDisplayed()` | N-L; E-L; C scroll/restore; libraryScrollRestoresAfterRecreation |
| 177: `assertIsDisplayed()` | N-L; E-L; C scroll/restore; libraryScrollRestoresAfterRecreation |
| 178: `assertEquals(before, composeTestRule.onNodeWithText(playlists).fetchSemanticsNode().boundsInRoot)` | N-L; E-L; C scroll/restore; libraryScrollRestoresAfterRecreation |
| 232: `assertIsDisplayed().performClick()` | N-L; E-L; C scroll/restore; everyDestinationRemainsReachableWithoutClipping |
| 233: `assertNoClippedActions(hasClickAction() and hasAnyDescendant(hasText(label)))` | N-L; E-L; C scroll/restore; everyDestinationRemainsReachableWithoutClipping |
| 235: `assertEquals(destinations, selected)` | N-L; E-L; C scroll/restore; everyDestinationRemainsReachableWithoutClipping |

### LibraryTracksContentTest

Source: `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/screens/LibraryTracksContentTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 63: `assertExists()` | N-V/N-T; E-V; C track list; displaysEmptyStateWhenNoTracks |
| 78: `assertDoesNotExist()` | N-V/N-T; E-V; C track list; doesNotDisplayEmptyStateBeforeInitialLoad |
| 102: `assertExists()` | N-V/N-T; E-V; C track list; displaysTracksList |
| 103: `assertExists()` | N-V/N-T; E-V; C track list; displaysTracksList |
| 104: `assertCountEquals(1)` | N-V/N-T; E-V; C track list; displaysTracksList |
| 124: `assertExists().performTextInput(" a")` | N-V/N-T; E-V; C track list; keepsTheFilterVisibleAndReportsChangesWhenAQueryIsActive |
| 125: `assertEquals("song a", changedQuery)` | N-V/N-T; E-V; C track list; keepsTheFilterVisibleAndReportsChangesWhenAQueryIsActive |
| 140: `assertExists()` | N-V/N-T; E-V; C track list; showsTheMatchingEmptyStateForAnActiveFilter |
| 169: `assertIsFocused()` | N-V/N-T; E-V; C track list; keepsFilterFocusWhenItsQueryLeavesNoMatches |
| 172: `assertIsFocused()` | N-V/N-T; E-V; C track list; keepsFilterFocusWhenItsQueryLeavesNoMatches |
| 173: `assertExists()` | N-V/N-T; E-V; C track list; keepsFilterFocusWhenItsQueryLeavesNoMatches |
| 191: `assertEquals(false, playMode)` | N-V/N-T; E-V; C track list; playbackActionsAppearForTracksAndDispatchTheirMode |
| 193: `assertEquals(true, playMode)` | N-V/N-T; E-V; C track list; playbackActionsAppearForTracksAndDispatchTheirMode |
| 212: `assertEquals("selected", selectedId)` | N-V/N-T; E-V; C track list; clickingTrackEmitsTheSelectedTrack |
| 231: `assertExists()` | N-V/N-T; E-V; C track list; moreOptionsOpensTrackContextMenu |
| 232: `assertExists()` | N-V/N-T; E-V; C track list; moreOptionsOpensTrackContextMenu |
| 233: `assertExists()` | N-V/N-T; E-V; C track list; moreOptionsOpensTrackContextMenu |
| 234: `assertExists()` | N-V/N-T; E-V; C track list; moreOptionsOpensTrackContextMenu |
| 254: `assertExists()` | N-V/N-T; E-V; C track list; holdingTrackOpensTrackContextMenu |
| 274: `assertCountEquals(0)` | N-V/N-T; E-V; C track list; contextMenuHidesMissingAlbumAndArtistNavigation |
| 275: `assertCountEquals(0)` | N-V/N-T; E-V; C track list; contextMenuHidesMissingAlbumAndArtistNavigation |
| 276: `assertCountEquals(0)` | N-V/N-T; E-V; C track list; contextMenuHidesMissingAlbumAndArtistNavigation |
| 297: `assertExists()` | N-V/N-T; E-V; C track list; trackRemainsVisibleWhileContextBottomSheetIsOpen |
| 298: `assertExists()` | N-V/N-T; E-V; C track list; trackRemainsVisibleWhileContextBottomSheetIsOpen |
| 321: `assertCountEquals(0)` | N-V/N-T; E-V; C track list; currentTrackHidesQueueActions |
| 322: `assertCountEquals(0)` | N-V/N-T; E-V; C track list; currentTrackHidesQueueActions |
| 346: `assertExists()` | N-V/N-T; E-V; C track list; alreadyQueuedTrackHidesAddToQueueAction |
| 347: `assertCountEquals(0)` | N-V/N-T; E-V; C track list; alreadyQueuedTrackHidesAddToQueueAction |
| 373: `assertTrue(zuluBounds.top < alphaBounds.top)` | N-V/N-T; E-V; C track list; reorderedTracksKeepTheirIdsAndDispatchQueueAndFavoriteActions |
| 379: `assertEquals(listOf("select" to "zulu", "next" to "zulu", "queue" to "zulu", "favorite:true" to "zulu"), actions)` | N-V/N-T; E-V; C track list; reorderedTracksKeepTheirIdsAndDispatchQueueAndFavoriteActions |
| 399: `assertTrue("Composed track rows: $count", count in 1..40)` | N-V/N-T; E-V; C track list; assertBounded |
| 401: `assertBounded()` | N-V/N-T; E-V; C track list; assertBounded |
| 403: `performScrollToIndex(5_002)` | N-V/N-T; E-V; C track list; assertBounded |
| 404: `assertIsDisplayed()` | N-V/N-T; E-V; C track list; assertBounded |
| 405: `assertEquals("track-5000", listState.layoutInfo.visibleItemsInfo.first().key) }` | N-V/N-T; E-V; C track list; assertBounded |
| 406: `assertDoesNotExist()` | N-V/N-T; E-V; C track list; assertBounded |
| 407: `assertBounded()` | N-V/N-T; E-V; C track list; assertBounded |
| 410: `assertIsDisplayed()` | N-V/N-T; E-V; C track list; assertBounded |
| 411: `assertEquals(before, composeTestRule.onNodeWithText("Large song 5000").fetchSemanticsNode().boundsInRoot)` | N-V/N-T; E-V; C track list; assertBounded |
| 412: `assertBounded()` | N-V/N-T; E-V; C track list; assertBounded |
| 437: `assertEquals(2, layouts.single().lineCount)` | N-V/N-T; E-V; C track list; longArabicTitleUsesTwoLinesAndKeepsTheTrackActionVisibleAtLargeFont |
| 438: `assertIsDisplayed()` | N-V/N-T; E-V; C track list; longArabicTitleUsesTwoLinesAndKeepsTheTrackActionVisibleAtLargeFont |

### PlaylistDetailsPresentationTest

Source: `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/screens/PlaylistDetailsPresentationTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 72: `assertExists()` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; narrowLongTitleKeepsSquareMosaicAndPrimaryActions |
| 74: `assertEquals(cover.width, cover.height, 0.5f)` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; narrowLongTitleKeepsSquareMosaicAndPrimaryActions |
| 75: `assertTrue(cover.width <= 232.dp.toPx() + 0.5f) }` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; narrowLongTitleKeepsSquareMosaicAndPrimaryActions |
| 78: `assertIsDisplayed()` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; narrowLongTitleKeepsSquareMosaicAndPrimaryActions |
| 79: `assertNoClippedActions(play)` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; narrowLongTitleKeepsSquareMosaicAndPrimaryActions |
| 80: `assertNoClippedActions(shuffle)` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; narrowLongTitleKeepsSquareMosaicAndPrimaryActions |
| 83: `assertEquals(1, plays)` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; narrowLongTitleKeepsSquareMosaicAndPrimaryActions |
| 84: `assertEquals(1, shuffles)` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; narrowLongTitleKeepsSquareMosaicAndPrimaryActions |
| 95: `assertIsDisplayed()` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; unavailableThenEmptyPlaylistKeepsItsIdentityAndMenu |
| 96: `assertDoesNotExist()` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; unavailableThenEmptyPlaylistKeepsItsIdentityAndMenu |
| 100: `assertDoesNotExist()` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; unavailableThenEmptyPlaylistKeepsItsIdentityAndMenu |
| 101: `assertExists()` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; unavailableThenEmptyPlaylistKeepsItsIdentityAndMenu |
| 102: `assertCountEquals(0)` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; unavailableThenEmptyPlaylistKeepsItsIdentityAndMenu |
| 104: `assertIsDisplayed()` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; unavailableThenEmptyPlaylistKeepsItsIdentityAndMenu |
| 106: `assertCountEquals(0)` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; unavailableThenEmptyPlaylistKeepsItsIdentityAndMenu |
| 108: `assertIsDisplayed()` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; unavailableThenEmptyPlaylistKeepsItsIdentityAndMenu |
| 123: `assertExists()` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; favoritesKeepLocalizedIdentityAndProtectedActions |
| 125: `assertDoesNotExist()` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; favoritesKeepLocalizedIdentityAndProtectedActions |
| 126: `assertDoesNotExist()` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; favoritesKeepLocalizedIdentityAndProtectedActions |
| 132: `assertDoesNotExist()` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; favoritesKeepLocalizedIdentityAndProtectedActions |
| 134: `assertEquals(track.id to false, favorite)` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; favoritesKeepLocalizedIdentityAndProtectedActions |
| 156: `performScrollToIndex(1)` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; dragReorderReportsDropAnchorsAndDoneRestoresTrackActions |
| 158: `assertCountEquals(2)` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; dragReorderReportsDropAnchorsAndDoneRestoresTrackActions |
| 159: `assertHeightIsAtLeast(48.dp).` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; dragReorderReportsDropAnchorsAndDoneRestoresTrackActions |
| 159: `assertWidthIsAtLeast(48.dp)` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; dragReorderReportsDropAnchorsAndDoneRestoresTrackActions |
| 160: `assertNoClippedActions(handle)` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; dragReorderReportsDropAnchorsAndDoneRestoresTrackActions |
| 165: `advanceEventTime(600)` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; dragReorderReportsDropAnchorsAndDoneRestoresTrackActions |
| 166: `moveTo(to, delayMillis = 500)` | C drag movement; E-P; N-P |
| 167: `advanceEventTime(300)` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; dragReorderReportsDropAnchorsAndDoneRestoresTrackActions |
| 170: `waitForIdle()` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; dragReorderReportsDropAnchorsAndDoneRestoresTrackActions |
| 171: `assertEquals(listOf(Triple("a", "b", null)), moves)` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; dragReorderReportsDropAnchorsAndDoneRestoresTrackActions |
| 174: `assertEquals("a", selected)` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; dragReorderReportsDropAnchorsAndDoneRestoresTrackActions |
| 175: `assertCountEquals(0)` | N-D/N-P/N-E; E-P/E-E; C drag/scroll; dragReorderReportsDropAnchorsAndDoneRestoresTrackActions |

### PlaylistListPresentationTest

Source: `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/screens/PlaylistListPresentationTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 74: `assertDoesNotExist()` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; loadingEmptyAndContentKeepFavoritesAndClickIdentity |
| 76: `assertIsDisplayed()` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; loadingEmptyAndContentKeepFavoritesAndClickIdentity |
| 80: `assertDoesNotExist()` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; loadingEmptyAndContentKeepFavoritesAndClickIdentity |
| 82: `assertEquals("favorites", selected)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; loadingEmptyAndContentKeepFavoritesAndClickIdentity |
| 84: `assertEquals("p", selected)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; loadingEmptyAndContentKeepFavoritesAndClickIdentity |
| 85: `assertCountEquals(1)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; loadingEmptyAndContentKeepFavoritesAndClickIdentity |
| 86: `assertNoClippedActions(hasContentDescription(label(R.string.playlist_row_open)))` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; loadingEmptyAndContentKeepFavoritesAndClickIdentity |
| 103: `assertEquals(2, layouts.single().layoutInput.maxLines)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; narrowLongNameUsesTwoLinesAndKeepsOpenTarget |
| 104: `assertEquals(2, layouts.single().lineCount)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; narrowLongNameUsesTwoLinesAndKeepsOpenTarget |
| 105: `assertNoClippedActions(hasContentDescription(label(R.string.playlist_row_open)))` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; narrowLongNameUsesTwoLinesAndKeepsOpenTarget |
| 108: `assertEquals(2, clicks)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; narrowLongNameUsesTwoLinesAndKeepsOpenTarget |
| 119: `assertNull(uri)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; createRejectsWhitespaceAndReturnsTrimmedLocalNameWithoutCover |
| 122: `assertIsNotEnabled()` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; createRejectsWhitespaceAndReturnsTrimmedLocalNameWithoutCover |
| 124: `assertIsNotEnabled()` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; createRejectsWhitespaceAndReturnsTrimmedLocalNameWithoutCover |
| 127: `assertIsEnabled()` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; createRejectsWhitespaceAndReturnsTrimmedLocalNameWithoutCover |
| 128: `assertIsDisplayed()` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; createRejectsWhitespaceAndReturnsTrimmedLocalNameWithoutCover |
| 129: `assertWidthIsAtLeast(48.dp).` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; createRejectsWhitespaceAndReturnsTrimmedLocalNameWithoutCover |
| 129: `assertHeightIsAtLeast(48.dp)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; createRejectsWhitespaceAndReturnsTrimmedLocalNameWithoutCover |
| 131: `assertEquals(name, created)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; createRejectsWhitespaceAndReturnsTrimmedLocalNameWithoutCover |
| 141: `assertEquals("", name)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; favoritesEditorKeepsNameHiddenAndClearsOnlyTheCover |
| 142: `assertNull(uri)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; favoritesEditorKeepsNameHiddenAndClearsOnlyTheCover |
| 143: `assertTrue(clear)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; favoritesEditorKeepsNameHiddenAndClearsOnlyTheCover |
| 147: `assertCountEquals(0)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; favoritesEditorKeepsNameHiddenAndClearsOnlyTheCover |
| 148: `assertIsEnabled()` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; favoritesEditorKeepsNameHiddenAndClearsOnlyTheCover |
| 150: `assertDoesNotExist()` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; favoritesEditorKeepsNameHiddenAndClearsOnlyTheCover |
| 151: `assertIsDisplayed()` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; favoritesEditorKeepsNameHiddenAndClearsOnlyTheCover |
| 152: `assertWidthIsAtLeast(48.dp).` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; favoritesEditorKeepsNameHiddenAndClearsOnlyTheCover |
| 152: `assertHeightIsAtLeast(48.dp)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; favoritesEditorKeepsNameHiddenAndClearsOnlyTheCover |
| 154: `assertTrue(saved)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; favoritesEditorKeepsNameHiddenAndClearsOnlyTheCover |
| 169: `assertEquals("p", id)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; contextActionsKeepQueueOrderRenameAndConfirmedDelete |
| 170: `assertNull(uri)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; contextActionsKeepQueueOrderRenameAndConfirmedDelete |
| 171: `assertEquals(false, clear)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; contextActionsKeepQueueOrderRenameAndConfirmedDelete |
| 180: `assertEquals(listOf("b", "a"), next)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; openMenu |
| 183: `assertEquals(listOf("b", "a"), queue)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; openMenu |
| 188: `assertEquals("Renamed", updated)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; openMenu |
| 191: `assertNull(deleted)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; openMenu |
| 193: `assertEquals("p", deleted)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; openMenu |
| 203: `assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; manualCoverRendersAndLargeListKeepsScrollStateAndBoundedRows |
| 215: `waitUntil(5_000) {` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; manualCoverRendersAndLargeListKeepsScrollStateAndBoundedRows |
| 222: `assertIsDisplayed()` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; manualCoverRendersAndLargeListKeepsScrollStateAndBoundedRows |
| 223: `assertEquals(500, listState.firstVisibleItemIndex) }` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; manualCoverRendersAndLargeListKeepsScrollStateAndBoundedRows |
| 224: `assertTrue(rule.onAllNodes(hasContentDescription(label(R.string.playlist_row_open))).fetchSemanticsNodes().size in 1..30)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; manualCoverRendersAndLargeListKeepsScrollStateAndBoundedRows |
| 225: `performScrollToIndex(900)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; manualCoverRendersAndLargeListKeepsScrollStateAndBoundedRows |
| 227: `assertEquals("p900", selected)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; manualCoverRendersAndLargeListKeepsScrollStateAndBoundedRows |
| 228: `assertTrue(rule.onAllNodes(hasContentDescription(label(R.string.playlist_row_open))).fetchSemanticsNodes().size in 1..30)` | N-V/N-R/N-T/N-E; E-E/E-V; C playlist list/local PNG; manualCoverRendersAndLargeListKeepsScrollStateAndBoundedRows |

### SearchPresentationTest

Source: `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/screens/SearchPresentationTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 60: `assertTextEquals("restored")` | N-S; E-S; C search; controlledQueryClearLoadingAndEmptyStatesKeepFocusUntilDone |
| 61: `assertIsFocused()` | N-S; E-S; C search; controlledQueryClearLoadingAndEmptyStatesKeepFocusUntilDone |
| 63: `assertEquals("الموسيقى", state.value.query) }` | N-S; E-S; C search; controlledQueryClearLoadingAndEmptyStatesKeepFocusUntilDone |
| 65: `assertTextEquals("")` | N-S; E-S; C search; controlledQueryClearLoadingAndEmptyStatesKeepFocusUntilDone |
| 66: `assertIsDisplayed()` | N-S; E-S; C search; controlledQueryClearLoadingAndEmptyStatesKeepFocusUntilDone |
| 68: `assertTextEquals("pending")` | N-S; E-S; C search; controlledQueryClearLoadingAndEmptyStatesKeepFocusUntilDone |
| 69: `assertIsDisplayed()` | N-S; E-S; C search; controlledQueryClearLoadingAndEmptyStatesKeepFocusUntilDone |
| 70: `assertDoesNotExist()` | N-S; E-S; C search; controlledQueryClearLoadingAndEmptyStatesKeepFocusUntilDone |
| 72: `assertDoesNotExist()` | N-S; E-S; C search; controlledQueryClearLoadingAndEmptyStatesKeepFocusUntilDone |
| 73: `assertIsDisplayed()` | N-S; E-S; C search; controlledQueryClearLoadingAndEmptyStatesKeepFocusUntilDone |
| 75: `assertIsNotFocused()` | N-S; E-S; C search; controlledQueryClearLoadingAndEmptyStatesKeepFocusUntilDone |
| 92: `performScrollToIndex(index * 2)` | N-S; E-S; C search; categorizedResultsKeepEveryDestinationCallback |
| 93: `assertIsDisplayed().performClick()` | N-S; E-S; C search; categorizedResultsKeepEveryDestinationCallback |
| 95: `assertEquals(listOf("t", "a", "r", "p", "c"), clicked)` | N-S; E-S; C search; categorizedResultsKeepEveryDestinationCallback |
| 109: `assertIsDisplayed().performClick()` | N-S; E-S; C search; narrowTrackColumnsGrowForLongEnglishAndArabicNames |
| 110: `assertEquals("2", clicked)` | N-S; E-S; C search; narrowTrackColumnsGrowForLongEnglishAndArabicNames |
| 111: `assertNoClippedActions(hasContentDescription(label(R.string.track_row_more_options)))` | N-S; E-S; C search; narrowTrackColumnsGrowForLongEnglishAndArabicNames |

### SearchRestorationTest

Source: `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/screens/SearchRestorationTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 64: `performScrollToIndex(8)` | N-S; E-S/E-B; C search; H1; queryAndResultsScrollRestoreAndBackRemainsWithTheHost |
| 65: `assertIsDisplayed()` | N-S; E-S/E-B; C search; H1; queryAndResultsScrollRestoreAndBackRemainsWithTheHost |
| 68: `assertDoesNotExist()` | N-S; E-S/E-B; C search; H1; queryAndResultsScrollRestoreAndBackRemainsWithTheHost |
| 70: `assertTextEquals("local query")` | N-S; E-S/E-B; C search; H1; queryAndResultsScrollRestoreAndBackRemainsWithTheHost |
| 71: `assertEquals(before, index to offset) }` | N-S; E-S/E-B; C search; H1 — stale before pair; expected false failure; queryAndResultsScrollRestoreAndBackRemainsWithTheHost |
| 73: `assertTextEquals("local query")` | N-S; E-S/E-B; C search; H1; queryAndResultsScrollRestoreAndBackRemainsWithTheHost |
| 74: `assertEquals(before, index to offset) }` | N-S; E-S/E-B; C search; H1 — stale before pair; expected false failure; queryAndResultsScrollRestoreAndBackRemainsWithTheHost |
| 75: `assertIsDisplayed()` | N-S; E-S/E-B; C search; H1; queryAndResultsScrollRestoreAndBackRemainsWithTheHost |
| 77: `assertEquals(1, backCount) }` | N-S; E-S/E-B; C search; H1; queryAndResultsScrollRestoreAndBackRemainsWithTheHost |

### LibrarySearchArabicTest

Source: `androidApp/src/test/kotlin/com/amwolfstein/sistrum/LibrarySearchArabicTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 15: `assertEquals(listOf("match"), searchTracks(tracks, "ألف كلث").map { it.id })` | N-Q; E-Q; C synchronous in-memory; arabicTitleAndArtistPrefixesMatchLocalTracksWithDiacritics |
| 16: `assertEquals(listOf("match"), searchTracks(tracks, "ليلة", setOf("match")).map { it.id })` | N-Q; E-Q; C synchronous in-memory; arabicTitleAndArtistPrefixesMatchLocalTracksWithDiacritics |
| 17: `assertEquals("الف* AND كلث*", searchFtsMatch("أَلْف كلث"))` | N-Q; E-Q; C synchronous in-memory; arabicTitleAndArtistPrefixesMatchLocalTracksWithDiacritics |
| 18: `assertEquals(emptyList<String>(), searchTracks(tracks, "مفقود").map { it.id })` | N-Q; E-Q; C synchronous in-memory; arabicTitleAndArtistPrefixesMatchLocalTracksWithDiacritics |

### ArtistDefaultArtworkTest

Source: `androidApp/src/test/kotlin/com/amwolfstein/sistrum/sync/ArtistDefaultArtworkTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 18: `assertEquals("artwork/new.jpg", artist.artworkPath)` | N-F; E-F; C synchronous in-memory; newestYearWinsRegardlessOfInputOrderAndDateAdded |
| 19: `assertFalse(artist.hasCustomArtwork)` | N-F; E-F; C synchronous in-memory; newestYearWinsRegardlessOfInputOrderAndDateAdded |
| 25: `assertEquals("artwork/new.jpg", libraryArtistsFrom(listOf(` | N-F; E-F; C synchronous in-memory; missingYearsUseMostRecentlyAddedTrack |
| 33: `assertEquals("artwork/old.jpg", libraryArtistsFrom(listOf(` | N-F; E-F; C synchronous in-memory; knownYearRanksAboveMissingYear |
| 41: `assertEquals("artwork/new.jpg", libraryArtistsFrom(listOf(` | N-F; E-F; C synchronous in-memory; equalYearsUseMostRecentlyAddedTrack |
| 52: `assertEquals("artwork/new.jpg", libraryArtistsFrom(tracks, paths).single().artworkPath)` | N-F; E-F; C synchronous in-memory; equalYearAndDateUseHighestTrackIdRegardlessOfInputOrder |
| 58: `assertEquals("artwork/old.jpg", libraryArtistsFrom(listOf(` | N-F; E-F; C synchronous in-memory; tracksWithoutResolvedArtworkAreSkipped |
| 65: `assertNull(libraryArtistsFrom(listOf(row("missing")), paths).single().artworkPath)` | N-F; E-F; C synchronous in-memory; noArtworkAnywhereUsesPlaceholder |
| 66: `assertEquals(emptyList<LibraryArtist>(), libraryArtistsFrom(emptyList(), paths))` | N-F; E-F; C synchronous in-memory; noArtworkAnywhereUsesPlaceholder |
| 74: `assertEquals("artist-artwork/manual.jpg", artist.artworkPath)` | N-F; E-F; C synchronous in-memory; manualImageWinsOverManifestAndNewestCoverAndKeepsCustomFlag |
| 75: `assertTrue(artist.hasCustomArtwork)` | N-F; E-F; C synchronous in-memory; manualImageWinsOverManifestAndNewestCoverAndKeepsCustomFlag |
| 77: `assertEquals("artwork/new.jpg", removed.artworkPath)` | N-F; E-F; C synchronous in-memory; manualImageWinsOverManifestAndNewestCoverAndKeepsCustomFlag |
| 78: `assertFalse(removed.hasCustomArtwork)` | N-F; E-F; C synchronous in-memory; manualImageWinsOverManifestAndNewestCoverAndKeepsCustomFlag |
| 87: `assertEquals(listOf("a", "b"), artists.map { it.id })` | N-F; E-F; C synchronous in-memory; collaborationCountsUnderEachEntityWithoutSplittingNames |
| 88: `assertEquals(listOf("Artist A", "B, C & D"), artists.map { it.name })` | N-F; E-F; C synchronous in-memory; collaborationCountsUnderEachEntityWithoutSplittingNames |
| 89: `assertEquals(listOf("artwork/new.jpg", "artwork/new.jpg"), artists.map { it.artworkPath })` | N-F; E-F; C synchronous in-memory; collaborationCountsUnderEachEntityWithoutSplittingNames |
| 97: `assertEquals("artwork/old.jpg", libraryArtistsFrom(listOf(` | N-F; E-F; C synchronous in-memory; usesArtworkResolvedByAlbumFlowEvenWhenNewestRowHasNoKey |
| 104: `assertEquals("artwork/track.jpg", libraryArtistsFrom(listOf(` | N-F; E-F; C synchronous in-memory; usesExistingTrackArtworkPathWhenAlbumArtworkIsUnavailable |

### HomeAlbumsTest

Source: `androidApp/src/test/kotlin/com/amwolfstein/sistrum/ui/components/HomeAlbumsTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 16: `assertEquals(listOf(listOf("new", "older"), listOf("other")),` | N-H; E-H; C synchronous in-memory/static source scan; groupsAlbumsInRecentQueueOrderWithoutReorderingTracks |
| 18: `assertEquals(listOf("new", "other", "older"), tracks.map { it.id })` | N-H; E-H; C synchronous in-memory/static source scan; groupsAlbumsInRecentQueueOrderWithoutReorderingTracks |
| 22: `assertEquals(emptyList<List<LibraryTrack>>(), recentHomeAlbums(emptyList()))` | N-H; E-H; C synchronous in-memory/static source scan; emptyAndUnknownAlbumsDoNotMergeUnrelatedTracks |
| 24: `assertEquals(listOf(listOf(tracks[0]), listOf(tracks[1])), recentHomeAlbums(tracks))` | N-H; E-H; C synchronous in-memory/static source scan; emptyAndUnknownAlbumsDoNotMergeUnrelatedTracks |
| 30: `assertFalse("Home must remain local: $forbidden", source.contains(forbidden, ignoreCase = true))` | Source-read denylist check; HomeContent.kt:208 uses local thumbnail helper; indirect calls are not proven network-free by this assertion.; homeReferencesOnlyLocalArtworkAndNoNetworkClasses |

### AlbumDetailsDiscOrderTest

Source: `androidApp/src/test/kotlin/com/amwolfstein/sistrum/ui/screens/AlbumDetailsDiscOrderTest.kt`.

| Line / assertion or await | Mechanical trace |
| --- | --- |
| 20: `assertEquals(listOf("disc1a", "disc1b", "disc2", "untagged"), details.tracks.map { it.id })` | N-A; E-A; C synchronous in-memory; discThenTrackOrderSurvivesRepeatedTrackNumbersAndMissingTags |
| 22: `assertEquals(listOf("disc1a", "disc1b", "disc2", "untagged"), request.trackIds)` | N-A; E-A; C synchronous in-memory; discThenTrackOrderSurvivesRepeatedTrackNumbersAndMissingTags |
| 23: `assertEquals(2, request.startIndex)` | N-A; E-A; C synchronous in-memory; discThenTrackOrderSurvivesRepeatedTrackNumbersAndMissingTags |

## 6. Verification and suggestions

Fix H1 before running T020. Add the real navigation roundtrip described in M1 without replacing the direct presentation test. Run the new matrix classes explicitly as well as the older screen classes; HANDOFF lists those additional classes, while the task's short phone-class list alone does not enumerate all of them.

Review commands: git log/range diff/stat/name-only, git diff --check (source paths: clean), git grep/rg callers and assertions, numbered source/test reads, CLAUDE/spec/decisions/tasks/HANDOFF reads. Two guessed source filenames were absent; located and read the actual LibraryVirtualList.kt and ArtworkBitmaps.kt instead. No Gradle, adb, network requests, staging, commits or code/test edits. Full host/device execution and measured timing: NOT RUN (review mode forbids them). Only this report was written; the pre-existing silent-failure-hunter report was not changed.

| Severity | Count |
| --- | ---: |
| CRITICAL | 0 |
| HIGH | 1 |
| MEDIUM | 1 |
| LOW | 0 |

VERDICT: NEEDS CHANGES

