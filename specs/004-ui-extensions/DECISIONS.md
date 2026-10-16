# Decisions - 004 UI part

Style: Case / Options / Chosen / Revisit if. Written by the planner (2026-10-15) where the owner had not answered; the owner may
overrule any of them. Owner decisions are marked (owner). Codex adds D-19 onward during implementation.

## D-1: Scope split, UI now and decoders later (owner)
Case: 004 held the UI overhaul, the extension runtime, motion artwork, lyrics search/embedding and the M4b analysis decoders.
Options: (a) all in one spec; (b) UI part plus the integrations the owner chose now, the rest later. Chosen: (b), owner 2026-10-15.
In: tokens/theme/glass (with Reduce Transparency), Home, Library, Album, Artist, Playlist, Search, navigation, mini-player,
DEFAULT/IMMERSIVE/LARGE player styles, lyrics UI, accessibility, Hide Volume Bar, and Settings -> Integrations with
(1) Wikipedia artist info, (2) Deezer artist images (both from Rhythm, GPLv3, attribution), (3) animated album covers from Exhale's
`canvas/` module (Apple Music with Tidal fallback), OFF by default, Wi-Fi/cellular choice, no warning text (owner). Out (later,
separate): decoders U-101..U-107, SpotiFLAC extension runtime/registry/manager, Better Lyrics, Lyrically, YouTube Music, lyrics
search changes, writing lyrics into files. LRCLIB already exists in Sistrum: not ported (owner).
Revisit if: the owner re-opens the extension runtime; then a new spec, not an edit of this one.

## D-2: Implementation branch (owner, 2026-10-15)
Case: `feature/ui-overhaul` is stale (e4c6aa5); 002 runs on `feature/media3-migration`. Options: (a) reuse the stale branch;
(b) the migration branch; (c) a fresh `feature/ui-overhaul-004`. Chosen (owner): (b), like 002 and 003; no new branch. Order on
the branch: 002 M2, 005 B4, 004 UI M1-M5, then 002 M3-M6, 003, 004 decoders. Claude records the 004 start SHA in HANDOFF before
T001 runs (SC-012 compares against it). The stale branch is left alone. Revisit if: the owner wants 004 on its own branch.

## D-3: Phase 0 U-019 narrowed to a player UI boundary guard
Case: U-019 defined enrichment adapters (CurrentTrackSnapshot, EntityIdentity, artwork/lyrics repositories) for the extension work.
Options: (a) build all adapters now; (b) only the part the UI needs: an immutable `PlayerUiState`, a static import guard and a
`PlaybackController` API snapshot test. Chosen: (b) as T042/T048. The artist enrichment repository (T028) is the only new boundary and is
read-only over library data. Revisit if: the extension work resumes.

## D-4: Every existing action stays; restyling never changes ViewModels
Case: constitution Principle 3 and the Phase 0 audit (genres, composers, statistics, Last.fm, Mood Radio entry, playlists).
Options: free redesign; presentation-only. Chosen: presentation-only; ViewModels, callbacks and queries stay; each screen task keeps the
existing screen test unmodified and adds cases. Revisit if: a layout cannot hold an action (then an owner question, not a removal).

## D-5: Existing Haze wrapper only, no new backdrop library
Case: Exhale uses Haze plus a separate backdrop/lens library. Options: add the library; adapt the Sistrum wrapper in `LiquidGlass.kt`.
Chosen: wrapper only (Phase 0 P03). Revisit if: a measured spike shows the wrapper cannot reach the Exhale look within SC-011.

## D-6: Reduced Motion follows the system animation scale
Case: Phase 0 wants Reduced Motion as an input; a new setting needs strings, UI and tests. Options: new in-app switch; system
animator scale only. Chosen: system only (`LocalReducedMotion`). Revisit if: users ask for an in-app switch.

## D-7: One small HTTPS fetcher on HttpURLConnection, no Retrofit/OkHttp/Ktor
Case: Rhythm uses Retrofit/OkHttp, Exhale's canvas module Ktor; Sistrum's lyrics clients use `HttpURLConnection` and the build has none
of those libraries. Options: add a client library; port the logic onto a small `HttpFetcher` interface. Chosen: the interface with an
`HttpURLConnection` implementation (fakeable in tests, one place for https-only, timeouts, size caps, redirect rules). Revisit if:
HLS/large downloads need features it lacks (video itself goes through Media3's data source, not this fetcher).

## D-8: Caches on disk, no Room change
Case: Phase 0 P06 proposed a new enrichment DB. Options: Room tables; files in `cacheDir` + DataStore. Chosen: files in `cacheDir`
(bounded, TTL, negative cache) and DataStore for switches; the Room schema is untouched (AGENTS "Never"). Cache is disposable by
design. Revisit if: offline retention of About text becomes a requirement (then a migration plan).

## D-9: Reference ports without credentials; no fallback token (owner accepted 2026-10-15, risk recorded)
Case: Exhale's Apple token code has a hard-coded fallback token that has already expired once; Rhythm/Exhale scrape the Apple web
player's developer token; Tidal and Apple endpoints are undocumented; Deezer and Apple terms apply; Wikipedia text needs CC BY-SA
attribution. Options: port as is; port without the fallback token and fail closed. Chosen: port the logic, drop any hard-coded token;
no token = no motion (static cover), 10 minute back-off. The owner chose to ship motion without warning text; the rights risk is
recorded here and in the release report (T065) and Claude runs `opensource-sanitizer`. Wikipedia always shows source and license.
Revisit if: a service blocks the app or the owner wants terms text in Settings.

## D-10: Wikipedia ON by default, Deezer and animated covers OFF; no new Gradle dependencies (owner, 2026-10-15)
Case: Wikipedia and Deezer send artist names to third parties; owner fixed motion = OFF. Chosen (owner): Wikipedia ON by default,
Deezer artist images OFF, animated covers OFF. With all three switched off the app makes no request from these features (SC-006).
No new dependency: `HttpURLConnection`, org.json/Android JSON, the Media3 modules already present (ExoPlayer, `SimpleCache`). A task
needing one writes a D-entry first. Revisit if: a public release needs a first-run privacy prompt.

## D-11: Wikipedia language follows the app language (owner, 2026-10-15)
Case: Rhythm queries English Wikipedia only. Chosen (owner): the app's current language picks the wiki (`ar.wikipedia.org` when the
app is in Arabic, `en.wikipedia.org` otherwise). When the Arabic wiki has no confident match, English is tried once (Claude's default:
many artists have no Arabic article). Plain-text extract, confident match only (title equals the artist name, not a disambiguation
page); a missing result shows nothing. Cache key includes the language; changing the language shows that language's text (fetched on
the next artist open). Revisit if: Arabic name matching (transliteration) gives wrong artists.

## D-12: LARGE maps to the V8 (APPLE_MUSIC) floating cover, IMMERSIVE to V7
Case: Exhale has no LARGE; `PlayerStyleOption.IMMERSIVE` selects V7 and APPLE_MUSIC V8 (Phase 0 E03/E04). Options: ask first; go
with the mapping. Chosen: proceed with the mapping (owner 2026-10-15: will review the look); visual review is on the owner manual list (T065). Persisted values are
`DEFAULT`, `LARGE`, `IMMERSIVE` strings, unknown = DEFAULT. Revisit if: the owner dislikes the look (only T050 changes).

## D-13: DEFAULT player stays static
Case: motion covers in the player. Options: all styles; LARGE/IMMERSIVE only. Chosen: LARGE/IMMERSIVE only, so DEFAULT is unchanged
as the owner required; the artist header may show motion. Revisit if: the owner wants motion in DEFAULT.

## D-14: No screenshot-library; Compose semantic and pixel checks instead
Case: Phase 0 asked for screenshots/snapshots; the project has no golden-image tool and adding one is a dependency and a flaky-device
risk. Options: add a library; use Compose tests (`captureToImage` for contrast, semantics and bounds assertions, images saved as
artifacts). Chosen: the latter (`UiMatrix`, T003). Revisit if: a visual regression slips through.

## D-15: Milestone order: Foundation, Screens, Integrations, Navigation/Player, Release
Case: owner said integrations come in their own milestone after the screens. The artist screens (M2) leave typed slots that M3 fills;
motion appears in the player styles in M4 (T054). Phase 0 had navigation before extensions; the owner order wins.

## D-16: Tests-first where state or isolation can break
Case: AGENTS rule for high-risk areas. Chosen: tests-first tasks for the fetcher (T021), Deezer (T024), Wikipedia (T026), Apple token
(T031), motion providers (T032), motion player isolation (T036), UI boundary guard (T042), style preference (T045), style parity
(T049) and the characterization tests (T001-T003). HIGH-RISK tasks: T031, T033, T036, T037, T040, T042, T043, T048.

## D-17: Settings placement
Case: the owner wants Integrations as a Settings screen. `IntegrationContent.kt` already hosts Last.fm and Lyrics rows. Chosen:
extend that screen (no new top-level destination); player style and Hide Volume Bar go to Appearance. The other Settings pages are not restyled (D-20) except the glass toggle (D-21).

## D-18: Milestone review set
Claude runs, per milestone: `silent-failure-hunter`, `pr-test-analyzer`, `kotlin-reviewer`, `spec-traceability-auditor`
(+ `comment-analyzer`); M2/M4/M5 add `ui-finish-gate-reviewer`; M3 adds `android-security-reviewer`, `port-fidelity-checker`
and `opensource-sanitizer`; M4 adds `playback-lifecycle-reviewer`; M5 adds `translation-reviewer` and `production-audit`;
`santa-method` on HIGH-RISK diffs. `audio-correctness-reviewer` is not needed (no audio path changes).

## D-19: Default artist photo = cover of the artist's newest song (owner, 2026-10-15)
Case: the library has no artist photos today: `libraryArtistsFrom` (`sync/SyncDatabase.kt`) only reads artwork keys that airmedy's
desktop sync used to send, so after the local-only fork every artist shows the placeholder. Owner: the latest song's cover is the
artist's default photo; with Deezer on, Deezer's photo replaces it and is cached. Chosen: portrait priority = manual image -> Deezer
(only when on; cached on disk, T025) -> artwork of the artist's newest track with artwork (highest year; no year or a tie -> most
recently added; tie -> track id) -> placeholder. Computed from the existing library flows, no Room change, no new file read during
scan. Same rule in the artist list, artist screen and anywhere else an artist image is shown. Revisit if: compilation/featured tracks
give a wrong-looking cover (then prefer tracks where the artist is the album artist).

## D-20: No Settings restyle, no Library Settings page; no Claude stops inside the runs (owner, 2026-10-15)
Case: the plan restyled every Settings page (T019) and added a Library Settings page (T058 gate + T059, which needed 002 M3's
chosen-folder scan). Owner: remove both. Chosen: T019, T058 and T059 removed, FR-010 dropped; Settings only gains the Integrations rows
(T038) and the Appearance rows (T047, T052). T063 removed too: milestone-auto's M5 reviewers (`translation-reviewer`,
`ui-finish-gate-reviewer`, `kotlin-reviewer`, `pr-test-analyzer`, `silent-failure-hunter`, `spec-traceability-auditor`) cover it.
T064 becomes a Codex task (same branch: no rebase; SC-012 against the start SHA). Revisit if: the owner asks for either page later.

## D-21: Exhale's glass toggle replaces every switch (owner, 2026-10-15)
Case: owner (screenshot from Exhale's settings): the Settings pages keep their look, only the toggles get Exhale's liquid-glass switch.
Exhale's `LiquidToggle` draws its lens/blur through Kyant's backdrop library, which D-5 had ruled out. Options: rebuild the look on
Haze (no lens refraction, not the same effect); add the library for the toggle only. Chosen: add `io.github.kyant0:backdrop:2.0.0`
(Exhale's version) used only by `GlassToggle` (T018a); D-5 still applies to every other glass surface. The task confirms the library
license: **Apache-2.0 confirmed 2026-10-16** from the [published 2.0.0 POM](https://repo.maven.apache.org/maven2/io/github/kyant0/backdrop/2.0.0/backdrop-2.0.0.pom) (`The Apache License, Version 2.0`). Fallback without lens on API < 33, Reduce Transparency or reduced
motion. Revisit if: the library costs frame time on long settings lists (SC-011) or breaks on an OEM device.


## D-22: Home in the Apple Music style; Recently added moves to Home (owner, 2026-10-15)
Case: owner sent a screenshot of SpotiFLAC-Mobile (Settings → Extensions → Home feed provider = Apple Music) and a GIF of the
resulting Home. The wish is the LOOK, not Apple's data: the earlier plan (and Claude's mockup) followed the Spotify-like
layout (small recent tiles, paged quick picks). Options: keep the Spotify-like layout; Apple Music style with local data;
fetch Apple's feed (rejected by the owner after clarification: no feed, no network). Chosen: Apple Music style with local
data only: large title collapsing to a small top bar, a "Recently added" hero carousel of wide cards (moved from Library,
T010 drops it there), then shelves of square cards for the existing sections; no Quick picks pages. FR-004 still forbids a
remote feed. Revisit if: the owner wants more shelves (e.g. playlists, genres) or the hero image from square covers looks
poor on device (owner reviews at the M2 phone check).

## D-23: Foundation motion durations
Case: T005 requires motion durations but U-002 specifies no numeric duration scale.
Options: per-screen values; shared short/medium/long durations of 150/300/500 ms.
Chosen: shared 150/300/500 ms defaults in `SistrumTokens.motion`, all zero when the system animator scale is zero (D-6).
Existing animation durations and DEFAULT player geometry stay unchanged; later screen tasks consume these tokens.
Revisit if: the screen tasks or measured accessibility/performance checks require a different shared scale.

## D-24: M2 gate failures are sorted against the pre-004 build before anything is fixed (Claude, 2026-10-16)
Case: T020 (M2 gate) ran 831 device tests for the first time: 314 failures. Per-task gates in M2 were host-only, so the new
M2 device tests had never run. `CrossfadeDurationSliderTest` (maximum case) clicks at y = -24, outside the slider; the test
and the slider are unchanged since `9f0acac`. Codex (medium, high, Astra) stopped because D-4 forbids editing existing tests.
Options: owner question per failure; accept everything; sort each failing method against a build of `9f0acac`.
Chosen: sort. A method that fails the same way at `9f0acac` joins the known baseline (no fix, no test edit in 004; the
003 M4b test checkup fixes such tests). A method that passes at `9f0acac` and fails now is a 004 regression: fix the
production code; existing tests stay unmodified (D-4). A failing test added in 004 may be corrected only when the test
itself is wrong (wrong tag, missing test host such as `LocalActivityResultRegistryOwner`, no scroll to the item), with the
reason in HANDOFF, never by loosening an assertion or expected value. The slider case goes to the baseline if it fails at
`9f0acac`. Task T020-fix3.
Revisit if: a regression needs a layout that cannot hold an existing action (then an owner question, D-4).

## D-25: Fetcher tests-first bridge and observable transport contract (T021)
Case: T021 must compile and fail on assertions with no production fetcher; T022 must pass the same tests unmodified.
Options: production stubs in the tests-only task; reflection at the test boundary, following the existing decoder contracts.
Chosen: reflection only in test helpers, reporting absent classes as assertion failures. Production remains entirely T022.
The D-7 interface is `HttpFetcher.get(String, Map<String, String>, Int): FetchResult` (suspend).
Results observed by these tests are `Success(bytes: ByteArray)`, `HttpError(code: Int)`, `TooLarge`, `InvalidUrl`,
`RedirectLimit`, `Timeout`, and `NetworkError`; cancellation throws the original `CancellationException`.
`UrlConnectionFetcher` has an injectable constructor `(openConnection: (URL) -> HttpURLConnection,
connectTimeoutMs: Int, readTimeoutMs: Int, maxRedirects: Int, log: (String) -> Unit)`; production defaults are T022's
responsibility. Tests inject 1000/2000 ms and two redirects (initial request plus two hops), fake connections only.
Use the existing `Sistrum-Android/${BuildConfig.VERSION_NAME}` User-Agent, ignoring case-insensitive caller overrides.
Accept bodies exactly at the caller's cap, including empty at zero; reject actual over-cap bodies regardless of
Content-Length, reading at most cap plus one sentinel byte. Inspect redirects manually, resolve relative HTTPS targets,
and close streams/disconnect on success, failure and cancellation. Diagnostics must record failures without full URLs,
query tokens or unsanitized exception messages. The scripted test fake exposes `asFetcher<T>()` as an interface proxy
until the production type exists; later tests can use `val fetcher: HttpFetcher = fake.asFetcher()`.
These are test seams and FR-012 boundary choices, with no change to D-7, architecture, or existing networking clients.
Revisit if: a later provider needs response headers or a body larger than its explicit per-call cap.

## D-26: Fetcher defaults and platform network availability (T022)
Case: D-25 leaves production timeout/redirect defaults open; T022 needs a ConnectivityManager-backed policy.
Options: unbounded platform defaults or finite fetcher defaults; network presence alone or validated internet access.
Chosen: 10-second connect and 15-second read timeouts, at most five redirects, with positive timeout and nonnegative
size/redirect inputs. Online requires INTERNET and VALIDATED on the active network; unmetered additionally requires
NOT_METERED. No active network means both false. ACCESS_NETWORK_STATE is required to query these capabilities.
Cancellation disconnects the active connection; body reads check cancellation and read at most cap plus one byte.
Revisit if: provider measurements justify different bounds or a later task explicitly needs captive/offline network access.

## D-25: Album, artist and playlist/Favorites pages in SpotiFLAC's "Mornye" style (owner, 2026-10-16)
Case: owner GIFs of SpotiFLAC Mobile's Mornye style (frames in `research/refs/mornye-*.png`); M2 only recoloured these pages.
SpotiFLAC is Flutter, so nothing is ported: the look is rebuilt in Compose from the references. Only these screens change
(no Mornye accent picker, no glass-clarity setting). Buttons keep Sistrum's actions in Mornye's shape: small round Shuffle,
large white Play (pill; a circle on the artist page), small round "..." with the existing menu (plus edit/reorder on playlists).
Artist sections map to local data: Latest release, Popular (most played), Albums (7+ songs), Singles & EPs (1-6 songs),
Compilations (albums by another album artist, e.g. Various Artists; no schema change). Recents panel T020a runs in the same
mini-milestone M2b. Existing screen tests stay unmodified except an assertion on an element this design removes or moves;
each such change is listed in HANDOFF with the reason (D-24 rule: never loosen a behaviour check).
Revisit if: an existing action has no place on the new pages (owner question, D-4).

## D-27: Recents full list stays inside Home (T020a)
Case: T020a requires a list icon opening the full recent list without changing navigation or ViewModels.
Options: a local dismissible list dialog using the existing recent queue and TrackRow/context actions (recommended);
a new navigation destination and additional routing.
Chosen: the local virtualized list dialog, with the existing recent queue passed unchanged for every track selection.
Cards are 280 × 300 dp; track count/year remain as a compact third overlay line to preserve the existing T009 case unchanged.
Revisit if: the owner requests a dedicated recent-list destination or removal of the metadata line.

## D-28: Mornye page tint and large-font identity layout (T020b)
Case: D-25 requires a dark dominant-artwork tint with white text in both themes, including missing artwork and font scale 2.0.
Options: multiply dominant RGB by a fixed darkening factor with a neutral fallback (recommended); theme-dependent tinting with separate contrast adjustments.
Chosen: reuse DetailHero's dominant-colour helper, multiply RGB by 0.32, and use opaque #242428 for missing/transparent artwork. White text exceeds the required 4.5:1 even for a white cover. Scope page/opaque/glass/text tokens to the scaffold; retain the existing badge accents and GlassPolicy/LiquidGlass implementation. Identity text grows vertically over a minimum-height artwork region, and action symbols retain their physical size so large fonts do not clip them. Pill Play is white translucent glass; Circle Play is white, with identical tinted opaque fallback geometry for glass surfaces.
Revisit if: the T020f owner visual review requests a different tint strength or header proportions; contrast and accessibility tests must remain satisfied.


## D-29: Album metadata from existing local track tags (T020c)
Case: LibraryAlbum has no genre or quality column; the Mornye identity needs both without a schema or ViewModel change.
Options: derive presentation from existing ordered tracks (recommended); extend the album data contract.
Chosen: reuse track-info genre extraction (raw names with named-genre fallback), deduplicate nonblank genre text in track order,
and show the first ordered track's existing localized quality label. Omit absent genre/year fields and quality for an empty
album; retain Unknown for a track with unknown quality. Use existing disc/track order and numbers, sequential numbering
when track tags are missing, and Disc 1 for an untagged disc when grouping a multi-disc album. Footer uses the existing
millisecond-aware total duration, truncated to whole minutes, through the shared English/Arabic plurals.
Revisit if: the owner requests a distinct mixed-quality album label or a different policy for missing disc tags.

## D-30: Artist release sections from the local library (T020d)
Case: D-25 needs release sizes and compilations without a new Room query or album-artist ID column.
Options: derive counts from the existing complete local track flow and compare the projected album artist name (recommended); extend the data schema.
Chosen: count all locally available songs per album, including other artists on compilations, while preserving the artist's canonical playback order. A nonblank album artist differing from the trimmed artist name (case-insensitive) marks a compilation; absent album artist remains an own release. Own albums with 7+ songs go to Albums, 1–6 to Singles & EPs; zero-song own releases have no shelf. Latest release uses highest album year, retaining canonical album order for ties or unknown years. Popular sorts all artist songs by descending play count, retaining canonical order for ties (including zero plays), in pages of five. Missing years are omitted from card metadata. The projected default artist image remains governed by D-19.
Revisit if: the owner requests remote/full-discography song counts, artist-ID matching for album artists, or exclusion of zero-play songs.

## D-31: Playlist detail cover grid with fewer than four covers (T020e)
Case: T020e requests custom cover, else a 2×2 song-cover grid, else tint; the existing projection supplies up to four distinct covers.
Options: repeat the available covers in order to fill four cells (recommended); leave missing cells blank; retain the old single-cover presentation for short collections.
Chosen: the detail page alone prefers customArtworkPath, otherwise repeats the supplied covers in order into four cells. Zero covers retains the existing playlist/Favorites symbol on the scaffold's tinted background. Tint derives from the custom cover or first song cover through the shared dominant-colour helper. Library artwork and the ViewModel projection remain unchanged. Header uses the shared song-count plural; footer uses total duration truncated to whole minutes, including zero, through the existing English/Arabic plurals.
Revisit if: owner visual review requests blank grid cells or a different cover selection; no data or playback contract change is needed.
