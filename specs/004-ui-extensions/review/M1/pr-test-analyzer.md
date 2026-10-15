VERDICT: PASS

Reviewed `7b5b318e7f5c83e81b2be3117270da193b0e15f2^..HEAD` through `fd58914b`, against CLAUDE.md, spec.md, tasks.md and DECISIONS.md (particularly D-4/5/6/14/23). T008 is not implemented in this range; its phone/full-gate evidence is pending. No Gradle, adb, code/test edits or delegation. This is a static coverage review, not an execution result.

## 1. Coverage summary

Paths below are relative to `androidApp/src/{main,test,androidTest}/kotlin/com/amwolfstein/sistrum/`; `host/` means test, `device/` means androidTest. Production references without a prefix mean main.

| Changed production unit | Tests and assessment |
| --- | --- |
| `ui/theme/SistrumTokens.kt`: token construction, colors, geometry, motion | Host `SistrumTokensTest`: both palettes, literal spacing/corners, palette mappings, opacity, normal/large contrast and independent WCAG boundary fixtures. Host `ReducedMotionTest`: literal normal/reduced durations. |
| `ui/theme/ReducedMotion.kt`: callback flow and Android observer adapter | Host `ReducedMotionTest`: initial zero, positive scales, return to zero, registration order, cancellation unregister. Real ContentResolver observer and Compose provider wiring are not exercised. |
| `ui/theme/SistrumTheme.kt`: provides tokens/reduced motion | Device matrix reads token colors indirectly; no consumer assertion for system scale -> composition locals/motion durations. |
| `ui/components/LiquidGlass.kt`: policy, modifier, surface | Host `LiquidGlassTest`: all eight boolean combinations plus unchanged full-resolution policy. Device `GlassFallbackTest`: attached Haze modifier presence/absence, setting and performance changes in both directions, missing Haze, opaque pixel and text contrast. |
| `App.kt`: transparency composition local | Host `MainViewModelTest` covers setting -> state; device `AppearanceContentTest` covers switch -> callback. Neither checks the new state -> glass provider edge (F2). |
| `FloatingNavigationBar.kt`, `MiniPlayer.kt`: shared surface substitution | Device characterization verifies geometry and interaction; existing `AppNavigationTest`, `MiniPlayerTest`, `RtlLayoutTest` remain unchanged. No App-level blur assertion. |
| `AnchoredPopupMenu.kt`, `StackPageLayout.kt`, `SistrumIconButton.kt`, `PlaylistDetailsContent.kt`: modifier migrations | Shared modifier tests cover underlying policy. Existing component/screen tests provide interaction coverage, but no new per-consumer blur assertions. |
| `LibraryPlaybackActions.kt`: glass/border and label color | Device `GlassFallbackTest.libraryActionLabelsHaveNormalTextContrastOnOpaqueGlass`: reads actual text layout color and sampled opaque background for both action labels. |
| Test infrastructure: `UiMatrix`, frame-stat script | Device `UiMatrixTest`: configuration, artifact, contrast boundaries, offscreen bounds and label overflow negative controls. Script has explicit QA package/foreground/target checks and rejects absent frame counts; no automated script test. |

Additional characterization covers host destination stacks, navigation thresholds/reset, queue availability, mini-player visibility, lyrics parsing and browse truth table; device coverage adds Activity recreation, back priority, full player controls/panels and favorite toggles. Expected values are literals rather than results obtained from the function under test. Palette mapping assertions intentionally use the existing palette as the compatibility contract; contrast assertions provide a separate correctness oracle.

## 2. Critical gaps

No verified CRITICAL/HIGH finding. Two important, MEDIUM coverage gaps follow. Audio boundary cases (rates, channels, corrupt PCM, seek decoding, EOS) are inapplicable: this range changes no audio implementation or audio integration test.

## 3. Weak tests / important gaps

**F1 [MEDIUM] `androidApp/src/test/kotlin/com/amwolfstein/sistrum/ui/theme/SistrumTokensTest.kt:38` and `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/components/GlassFallbackTest.kt:63` — translucent contrast has no real backdrop coverage.** The host test composites the tint only over its matching opaque fill; the device test forces Reduce Transparency true before sampling and asserting contrast. Even matrix cases labeled reduce=false therefore check contrast only after turning transparency off. The Haze state has no registered contrasting source. Scenario: a glass header or mini-player is placed over opposite-luminance artwork/content, making muted text unreadable while all these contrast assertions still pass. `LiquidGlass.kt:77` samples Haze sources and applies tint; it does not place opaqueFill behind the blur branch. The test's matching-fill composite cannot establish SC-002 for that branch. Add `GlassFallbackTest.blurTextContrastOverBrightAndDarkSources`: register actual black/white Haze sources, keep reduce=false for capture, and assert actual consumer text against the rendered background in both palettes. This is an important acceptance-evidence gap, not a claim of a measured rendering failure.

**F2 [MEDIUM] `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/components/GlassFallbackTest.kt:55` — direct provider bypasses the new preference wiring.** All blur-removal assertions inject `LocalReduceTransparency` themselves. Device characterization constructs a store with true, but only asserts navigation/player behavior. Scenario: remove or invert `App.kt:146`'s provider, and a user's Reduce Transparency preference no longer removes navigation/header blur; the new fallback tests and existing setting/state tests still pass. Add `GlassFallbackTest.appPreferenceRemovesAndRestoresConsumerBlur` using App + the fake ThemeModeStore: dispatch SetReduceTransparency and inspect a real navigation/header surface, asserting blur present -> absent -> present. No new test-only policy flag should stand in for attached modifier inspection.

Nice-to-have: add a theme integration assertion for system-scale observation -> `LocalReducedMotion` and zero token durations. Current host tests verify each pure piece but would not detect omission of `SistrumTheme.kt:133`. This is a suggestion rather than a separate finding; existing animations intentionally remain unchanged under D-23.

## 4. Mechanical assumption traces

No verified wrong event/precondition assumption or definite phone timeout. Tables describe static results; none imply device execution. Each assertion/await site in new device tests is traced below; loop rows apply to every listed parameter/label. Synthetic playback values are constructor inputs, not decoded audio fixtures. No local audio-header inspection is applicable.

### Device navigation characterization

`D` = `device/ui/navigation/UiContractCharacterizationTest.kt`. Geometry uses bottom-aligned chrome, LTR context, and settled Compose animations. Literal expectations come from the pre-change shipped geometry (T002), not a new oracle generator.

| Assertion/await | Production formula or event/precondition trace; evaluated fixture |
| --- | --- |
| D:81 Library absent | Test calls accumulator.update(Up,24), sets compact. `NavigationChromeScrollAccumulator.kt:15` reaches abs(-24)>=24; `NavigationChrome.kt:52` permits compact because supplied state is Playing; alpha becomes zero at :69; `FloatingNavigationBar.kt:116` omits full targets. App normally sets this compact input from its scroll handler. Test supplies it directly. |
| D:83 width 48 dp | `CompactNavigationHeight=56` (`FloatingNavigationBar.kt:67`), inner padding=4 per side (:73,:108), compact target fills inner box (:240): 56-8=48 dp. |
| D:83 height 48 dp | Same inner box: 56-2*4=48 dp. |
| D:87 x delta 64*density +/-1 px | `NavigationChrome.kt:78`: 56+8=64 dp offset; title inset is unchanged (`MiniPlayer.kt:311,341,400`); dp -> physical pixels with device density. |
| D:88 y delta 80*density +/-1 px | `NavigationChrome.kt:82`: expanded -(72+8)=-80 dp, compact 0; both bottom aligned, fixed mini height 56 (:105); title y increases 80 dp. |
| D:89 Home left unchanged +/-1 px | BottomStart navigation, fixed 4 dp inset; changing width leaves LTR start unchanged (`NavigationChrome.kt:133`, `FloatingNavigationBar.kt:108`). |
| D:90 Home top increases | Bottom fixed; expanded height72 -> compact56; compact inner top is lower by16 dp. |
| D:91 Home width shrinks | Expanded target width=(W-8)/4 (`FloatingNavigationBar.kt:129,213`), W capped420 (`NavigationChrome.kt:58`); compact48. On the intended phone W>200, (W-8)/4>48. Narrower-than-200 dp window is not this fixture. |
| D:92 Home right < mini left | Compact Home right=start+4+48=52; mini box starts64, with title further inset. |
| D:93 centers equal +/-1 px | Both compact elements centered in the same56 dp vertical band (`NavigationChrome.kt:105,134`, `MiniPlayer.kt:311`). |
| D:99 restored mini bounds | Compact target onClick -> test sets compact=false (:64); reverse same transition restores widths/offsets; Playing item's title is unchanged. |
| D:100 restored Home bounds | Same reverse transition; full target restored by alpha>0; selected Home unchanged. |
| D:101 Library displayed | Full target row returns at alpha1 (`FloatingNavigationBar.kt:116,213`). |
| D:110 Artists empty visible | Navigation target -> `App.kt:546` dispatch SelectDestination; artists tile -> `AppDestinationContent.kt:786` OpenPage(LibraryArtists); MainViewModel.openPage :118 appends page; empty default artists state -> `LibraryArtistsContent.kt:79`. Test mounts App with default empty library. |
| D:114 Activity differs | scenario.recreate replaces ComponentActivity; test reattaches content through onActivity. This is Activity recreation, not process death. |
| D:115 ViewModel identical | ViewModelProvider(activity) in attachApp uses retained Activity ViewModelStore. Production MainActivity also owns the model; test obtains it the same way. |
| D:116 Library selected | Model retained selectedDestination; App collects uiState and navigation passes selected flag (`FloatingNavigationBar.kt:216`). |
| D:117 Artists empty visible | Retained stack selects LibraryArtists via `AppDestinationContent.kt:584`; same empty fixture. |
| D:120 Artists empty after tab roundtrip | Settings click and Library click dispatch SelectDestination; `MainViewModel.kt:113` switches destination without discarding other stack. |
| D:122 stack [Root,LibraryArtists] | openPage appends once (:126); tab switches (:114) preserve stack. Exactly 2 entries from literal action history. |
| D:133 waitForIdle | Test dispatches OpenPage/ OpenAlbumDetails; mounted collectAsState subscribes to stateIn (`MainViewModel.kt:84`); Compose idle settles page transition. No omitted service/coordinator. |
| D:134 AlbumDetails | `MainViewModel.kt:131` sets id and calls openPage; latest page is AlbumDetails. |
| D:137 LibraryAlbums after back | Key back -> App page BackHandler (:265) -> NavigateBack -> MainViewModel :156 drops last entry. |
| D:138 selectedAlbumId null | Same pop -> invalidatePoppedPageState :166 -> clearPoppedPageState :183. |
| D:141 Library selected | Second key back pops LibraryAlbums, destination remains Library. |
| D:142 stack [Root] | Initial [Root,LibraryAlbums,AlbumDetails], two dropLast(1) operations -> [Root], count1. |
| D:153 full player visible | Playing permits mini and full item (`NavigationChrome.kt:29`, `FullScreenPlayer.kt:493`); mini title click -> App :556 sets visible true; expansion animation :138 becomes1 and panel is composed after :155. |
| D:155 full player absent after back | Later enabled BackHandler (`App.kt:652`) sets visible=false; expansion :138 animates to0; :155 returns without panel. |
| D:157 AlbumDetails remains | Full-player handler consumes key without dispatching NavigateBack; underlying model stack unchanged. |
| D:158 missing-album id remains | Same reason; no page pop/clearPoppedPageState call. |
| D:161 next back -> LibraryAlbums | Full player now closed, page handler receives key and drops detail. |
| D:197 seek displayed | `FullScreenPlayer.kt:116,155` admit supplied Playing item and visible expansion; controls receive duration120000 and position0 (:461); `FullScreenPlayerControls.kt:136` supplies seek description. Test asserts presence, not seek output. |
| D:205 callbacks [pause,play,previous,next] | Controls :257-259 forward callbacks. Playing enables play/pause; test first callback replaces state with Paused, next returns Playing. Queue has 1 id/index0; availability helpers enable both transport buttons. Exactly four explicit clicks produce four literal entries. No actual engine required for this UI contract. |
| D:206 favorites [(id,true),(id,false)] | Metadata :176-177 calls onFavoriteToggle(item.trackId,!isFavorite); test updates favorite on each callback. Initial false -> true -> false. |
| D:209 queue header displayed | Queue button (`FullScreenPlayerControls.kt:280`) invokes onPanelSelected(Queue); FullScreenPlayer :474 stores selection; :416 composes queue; `FullScreenPlayerQueuePanel.kt:469` defines header tag. All required callbacks are inside mounted component. |
| D:211 supplied lyric visible | Lyrics button controls :275 -> selectedPanel=Lyrics -> FullScreenPlayer :444 passes literal plain text to lyrics panel. Parser :95 preserves plain line. Position0 does not require timed playback progress. |
| D:212 queue header absent | selectedPanel is one nullable enum (:179); AnimatedContent :406 replaces Queue branch with Lyrics after exit animation. |
| D:231 attachApp waitForIdle | onActivity installs content collecting model.uiState; while-subscribed flow starts on collection. No repository loading needed: App gets default empty library and synthetic PlaybackModel. |
| D:237 pressBack waitForIdle | sendKeyDownUpSync -> enabled App BackHandler -> recomposition/finite animation settles. |

### Device glass fallback

`G` = `device/ui/components/GlassFallbackTest.kt`. All 24 matrix cases (2 themes * 2 transparency inputs * 2 languages * 3 font scales) use the same assertion sites.

| Assertion/await | Production trace and number evaluation/origin |
| --- | --- |
| G:62 blur=!case.reduce | G:55 explicitly provides case input; `LiquidGlass.kt:67` resolves with Haze available and performance=false; :77 adds Haze only for Blur. |
| G:64 blur=false | Test sets reduce=true at :63; resolve returns Opaque; modifier replaced by background (:74). |
| G:67 fill equals sampled pixel | Opaque branch uses token opaqueFill; token maps to palette glassOpaque: Light #FFFFFF, Dark #232326 (`SistrumTheme.kt:49,74`). Sample x4,y=height/2 lies away from text inset24 dp in a240 dp box. Exact color checks token plumbing, independent contrast checks correctness. |
| G:68 main contrast | Same opaque pixel vs token text: Light #0A0A0A on white; Dark white on #232326; WCAG >=4.5. See F1 for excluded translucent backgrounds. |
| G:69 muted contrast | Same opaque pixel vs Light #52525B / Dark #A1A1AA. Threshold4.5 from SC-002; WCAG luminance formula used by ColorUtils. |
| G:73 blur=true | reduce=false explicitly restored; available Haze and default performance flag yield Blur. |
| G:83 initial performance blur=true | Test provides performance=false, Haze available, reduce local defaults false (UiMatrix passes its case reduce as an argument, not a local). Thus true in every case, intentionally independent of case.reduce. |
| G:85 performance blur=false | fallback=true -> Opaque in resolve. |
| G:87 recovery blur=true | fallback=false -> Blur with same Haze. |
| G:96 missing Haze blur=false | liquidGlassBackground(null) -> hazeAvailable=false -> Opaque. |
| G:98 center pixel equals fill | 120 dp empty Box, center has no text/border/scrim; background is exact opaqueFill from tokens. |
| G:114 one text layout per label | Actual LibraryPlaybackActions emits one Text for Play and one for Shuffle (`LibraryPlaybackActions.kt:85`); GetTextLayoutResult callback belongs to that text. Expected count1 is structure contract, not calculated from production output. |
| G:117 label contrast, each label | Actual style color is token text (:85); sampled x4,mid-height is inside26 dp rounded end for >=48 dp action height and outside16 dp text padding. Provider is forced true (:105), so expected background is opaqueFill; threshold4.5. |
| G:122 waitForIdle (every blur assertion above) | State changes provided by test cause recomposition; no async producer/service required. Haze modifier attachment is inspected, not GPU effect completion. |
| G:129 attached Haze presence equality (every call above) | Modifier identity is `dev.chrisbanes.haze.TypedHazeEffectNodeElement`; positive and negative assertions guard against vacuous absence after a library rename. Actual library execution remains NOT RUN. |

### Device matrix harness

`U` = `device/ui/testing/UiMatrixTest.kt`; implementation references are `device/ui/testing/UiMatrix.kt`. Expected configuration comes from the literal case cartesian product (:46-52), not a production oracle.

| Assertion | Formula/event trace and evaluation |
| --- | --- |
| U:39 reduce argument | UiMatrix :79 directly forwards case.reduceTransparency; this does not assert App glass wiring (F2). |
| U:40 fontScale | UiMatrix :77 overrides Density with case scale, exactly1/1.3/2. |
| U:41 locale | Configuration.setLocales at :69 installs en/ar and supplies LocalConfiguration at :75. |
| U:42 direction | UiMatrix :76 provides Rtl iff ar, Ltr otherwise. |
| U:46 no clipped action | UiMatrix :87 compares raw action rectangle with root and visible bounds, then recursively checks TextLayoutResult.hasVisualOverflow. Normal Button with short translated label has no forced narrow size. |
| U:48 image exists and bytes>0 | UiMatrix :114 creates directory, writes screenshot via PNG compress into case-specific file. Test-owned external files dir or additionalTestOutputDir (:56) supplies writable instrumentation output, not music/scoped media storage. No fixture missing/skip path. |
| U:49 black/white passes | WCAG (1+.05)/(0+.05)=21>=4.5; independent WCAG reference. |
| U:50 white/black passes | Same symmetric21>=4.5. |
| U:52 #767676/white passes | WCAG linear-sRGB luminance -> approx4.542>=4.5; ColorUtils in UiMatrix :107. |
| U:53 #777777/white rejected | Same formula -> approx4.478<4.5. |
| U:54 #949494/white large passes | Same formula -> approx3.033>=3.0. |
| U:55 #959595/white large rejected | Same formula -> approx2.995<3.0. |
| U:58 white/white rejected | Ratio1<4.5. |
| U:59 translucent black/white rejected | compositeOver blends .1 black over white to about#E6E6E6; contrast about1.25<4.5. Tests alpha handling rather than treating translucent black as solid black. |
| U:62 translucent background rejected | UiMatrix :106 requires bg.alpha==1; supplied .5 violates precondition, IllegalArgumentException. |
| U:74 clipped action rejected | y=-30 dp places part of Button outside 200 dp root; UiMatrix :94 raw bounds fail root.containsRect. Same vertical displacement in LTR/RTL. |
| U:84 overflowing label rejected | 80 dp button width minus content insets cannot hold literal long single-line label even at font1; TextLayoutResult.hasVisualOverflow -> UiMatrix :99 check fails; larger font scales increase overflow. |

### Host integration/flow traces

Host pure helper truth tables, token data values and parser unit tests do not drive Android events. The multi-class host case and flow callback assertions trace as follows.

| Assertion/await site | Trace/evaluation |
| --- | --- |
| Host UiContractCharacterizationTest: selectedDestination after first advanceUntilIdle | Test collects uiState in backgroundScope, activating MainViewModel :84; dispatches OpenPage albums, OpenAlbumDetails, SettingsLyrics, SelectDestination Library. MainViewModel :106-134 -> Library. |
| Same case: currentPage AlbumDetails | Library stack becomes [Root,LibraryAlbums,AlbumDetails]; selected top is AlbumDetails. |
| Same case: selectedAlbumId album-1 | openAlbumDetails :132 stores literal id. |
| Same case: first advanceUntilIdle | Unconfined main dispatcher and collector supplied by test; combine/stateIn work scheduled on test scheduler; no Android/network prerequisite. |
| Same case: advanceUntilIdle after first back | dispatch NavigateBack -> :156 pops detail and clears id via :183; scheduler drains state publication. |
| Same case: currentPage LibraryAlbums | Three-entry library stack minus last -> two entries, top albums. |
| Same case: selectedAlbumId null | clearPoppedPageState AlbumDetails branch. |
| Same case: Settings [Root,SettingsLyrics] | Cross-destination select and Library back do not pop Settings stack (:106,:156). |
| Same case: advanceUntilIdle after two more backs | Albums popped, then root back is no-op (:161); state publication drains. |
| Same case: destination remains Library | navigateBack never changes selectedDestination. |
| Same case: Library [Root] | Initial length3, two effective pops ->1; third pop at root is no-op. |
| Same case: advanceUntilIdle after select Settings | selectDestination switches selected value only (:114). |
| Same case: currentPage SettingsLyrics | Saved Settings stack remains length2. |
| Same case: advanceUntilIdle after reselect Settings | Same destination triggers stack reset (:107-112). |
| Same case: currentPage Root | Reset explicitly creates listOf(Root), length1. |
| ReducedMotionTest first runCurrent / values==[true] | reducedMotionFlow registers callback then update (:49-53); supplied scale0 -> true. Test subscribes through launch/collect. |
| Same test: loop runCurrent / assertFalse(last), each .5/1/2 | Test invokes registered changed callback after setting scale. Predicate scale==0 is false for all three; distinctUntilChanged may suppress duplicates, which is why last remains false. |
| Same test: final runCurrent / assertTrue(last) | Test sets0 and calls changed; predicate true, distinct transition delivered. |
| ReducedMotionTest registration runCurrent / events==[register,read] | reducedMotionFlow :50 invokes injected observeChanges before update :52 invokes readScale. |
| Same test: cancellation runCurrent / events==[register,read,unregister] | job.cancel closes awaitClose; finally :56 invokes supplied unregister exactly once. |

### Cost trace

| Start/length/bound or wait | Work on actual fixture; long-track comparison |
| --- | --- |
| Geometry fetches/assertions and click waits, D:72-101 | 420 ms geometry tween,160 ms alpha and280 ms entrance (`NavigationChrome.kt:62,71,144`), automatically settled by Compose test clock. Constant small node tree; no track decoding or file-length work. 1 px tolerance accommodates dp rounding, not a runtime deadline. |
| D:133,231,237 waitForIdle and state assertions | MainViewModel StateFlow combinations and bounded stack mutation; App navigation animation. Empty library fixture prevents scanning/list-size dependence. 4-minute track does not change this state work. No new fixed elapsed-time assertion. |
| Full-player open/close and panels D:153-212 | Finite520/400 ms expand/close (`FullScreenPlayer.kt:141`), panel200/120 ms transitions (:409). Compose synchronization waits for animations. Track position0/duration120000 are supplied data; fake path /audio/fake.flac is not decoded for playback. Artwork lookup failure is permissible because no artwork assertion depends on it. A 4-minute duration only changes displayed time/seek scaling. |
| G captures, all blur waits | 120/240 dp surfaces or two buttons; recomposition and render/readback, constant screen-area work. Pixel indices4/mid-height are in range at supported Android densities. No seek/fade/start position or fixed millisecond budget. |
| U clipping fixtures | 200 dp parent/-30 dp offset and80 dp label constraint are layout-only work; screenshot cost bounded by screen dimensions. No audio fixture. |
| Host advanceUntilIdle/runCurrent | Virtual scheduler; finite state updates/callback delivery. MainViewModel collector is in backgroundScope, preventing endless collection from becoming a foreground test job. Cancellation tested explicitly. |
| Script fixed sleeps and30 s shell timeout | `scripts/ui-frame-stats.sh:43` bounds adb subprocesses, not decoding; six400 ms swipes and .3 s pauses, plus1 s settling. UI hierarchy capture/gfxinfo are bounded by visible UI and device responsiveness, not audio length. A4-minute track is already loaded/paused. No phone timing/headroom measurement was performed in this review. |
| Script frame share | :112-119 parses reported total/janky counters and computes100*janky/total; rejects total0. Formula matches SC-011 percentage-point comparisons. Raw baseline logs are referenced in HANDOFF; not remeasured here. |

No new explicit timing assertion has a demonstrated linear-in-audio-length dependency. No expected audio hash/frame-count/fixture-table calculation exists in these tests. No new assumeTrue/skip path was found. T008's future phone result cannot be inferred from compilation or this trace.

## 5. Suggestions

1. Important: add the real bright/dark Haze-source contrast case (F1), retaining opaque and WCAG boundary negative controls.
2. Important: cover the complete fake preference -> MainViewModel -> App -> actual glass modifier path (F2).
3. Nice-to-have: test the Android system-scale observer/theme composition edge, including disposal cleanup, using a controlled adapter rather than changing the owner's global setting.
4. Run the planned T008 suites unchanged and record the per-class device logs; this review did not execute them.

| Severity | Count |
| --- | ---: |
| CRITICAL | 0 |
| HIGH | 0 |
| MEDIUM | 2 |
| LOW | 0 |

VERDICT: PASS
