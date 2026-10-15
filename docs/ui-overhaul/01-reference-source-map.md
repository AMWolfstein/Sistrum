# Phase 0 source map — 2026-10-15

## Evidence convention

**VERIFIED IN SOURCE** means inspected text, not successful execution or authorized service use. Evidence IDs below expand to repository, exact path, symbol and revision. Other documents link here and use these IDs. **PROPOSED** means future design, including all numeric budgets. **UNKNOWN / NEEDS VALIDATION** means unmeasured, unavailable or unresolved. **BLOCKED** means a stated gate prevents shipping that part; it does not prevent completing Phase 0.

The audit used sequential architecture, runtime, security, providers, lyrics/tagging, Compose, migration, licensing and test review passes. No agents were started. Reference code, archives and manifests were read as untrusted data; no reference build, script, dependency install or JavaScript ran.

## Revision ledger — VERIFIED IN SOURCE

| Alias | Repository | Inspected revision |
|---|---|---|
| S | Sistrum, this worktree | `7203f18deadf1ca589c03f1b41572a157a8f4ce3` |
| E | https://github.com/ozyern/exhale | `166e2cc7c6a3bcfb944999571e1bc5e0e3c1a066` |
| M | https://github.com/spotiflacapp/SpotiFLAC-Mobile | `5a8952659b247fd95f1914a56db60825657fe26d` |
| X | https://github.com/spotiflacapp/SpotiFLAC-Extension | `0290b263802d3418e45583612574b749289bf369` |
| F | https://github.com/moss-apps/Flick | `79da4ed76557c8ddf534e898480dde66bcc90334` |
| B | https://github.com/kushagrasinghx/BitChord | `fe95bb60aa9cd205cf4ab8bd25a5bec71a595b4f` |
| R | https://github.com/cromaguy/Rhythm, main at audit | `6f26f963b82ed592ec28f05824bb7f12b65b82ea` |

All six checkouts reside under `/tmp/sistrum-refs/<name>` at detached revisions. Initial clones used `--filter=blob:none --no-checkout`, followed by checkout of the requested reference revision with hooks disabled. These completed before the resume request to keep operations sequential; no further clones are needed. Do not run their code.

S citations refer to unchanged committed source unless explicitly marked working-copy evidence. D-31/D-32 and CLAUDE.md are owner-provided working-copy decisions, **not** claimed to exist at S. HEAD is a baseline, not a documentation commit. No earlier Phase 0 documents existed; NOTES.md corrects that premise.

## Sistrum map — VERIFIED IN SOURCE

Paths below use `A = androidApp/src/main/kotlin/com/amwolfstein/sistrum/` and `K = sharedLogic/src/commonMain/kotlin/com/amwolfstein/sistrum/`. Each S row is at the full S SHA above.

| ID | Exact path and symbol | Inspected finding / call chain |
|---|---|---|
| S01 | `A/MainActivity.kt`, `MainActivity.onCreate` | Instantiates `AndroidLyricsService`, collects `LyricsPreferences.settings` and provider lyrics; missing current-track lyrics trigger `fetch`; search and selection callbacks enter `App`. |
| S02 | `A/App.kt`, `App`; `A/AppDestination.kt`, `AppDestination`; `A/MainViewModel.kt`, `MainViewModel` | `App` owns full-player visibility with `rememberSaveable`, destination and full-player `BackHandler`s, and invokes `NavigationChrome`; activity saves app navigation state. |
| S03 | `A/ui/navigation/NavigationChrome.kt`, `NavigationChrome`, `PlaybackState.showsMiniPlayer` | Compact mode only with a visible mini-player; width/height and mini-player x/y animate together, 420 ms. Circle occupies the compact nav slot; mini-player moves beside it. |
| S04 | `A/ui/navigation/FloatingNavigationBar.kt`, `FloatingNavigationBar`; `A/ui/navigation/MiniPlayer.kt`, `MiniPlayer`; `A/ui/navigation/FullScreenPlayer.kt`, `FullScreenPlayer` | Existing callback-driven presentation. Preserve transport availability, gestures, queue, lyrics, dismiss and seek. |
| S05 | `A/ui/components/LiquidGlass.kt`, `liquidGlassBackground`, `LiquidGlassPerformanceMode`; `A/settings/ThemePreferences.kt`, `ThemePreferences`, `ThemeMode`; `A/ui/theme/SistrumTheme.kt`, `SistrumColors` | Haze 2 blur, quality sampling, 16 dp default radius; null Haze state uses opaque glass. DataStore `appearance` stores theme and Reduce Transparency. |
| S06 | `A/ui/components/HomeContent.kt`, `HomeContent`, `HomeTrackSection` | Existing local sections and horizontal track-card grid; callbacks remain local playback/library actions. |
| S07 | `A/ui/screens/LibraryContent.kt`, `LibraryContent`; `LibraryAlbumsContent.kt`, `LibraryArtistsContent.kt`, `LibraryTracksContent.kt`, `LibraryPlaylistsContent.kt` in same directory | Library destination list includes search, artists, albums, tracks, genres, composers and playlists; recent local tracks remain visible. |
| S08 | `A/ui/screens/AlbumDetailsContent.kt`, `AlbumDetailsContent`; `ArtistDetailsContent.kt`, `ArtistDetailsContent`; `PlaylistDetailsContent.kt`, `PlaylistDetailsContent`; `LibrarySearchContent.kt`, `LibrarySearchContent` | Detail hero plus lazy track rows and context actions; screen callbacks feed existing ViewModels and playback commands. Album uses stable track IDs and local artwork fallback. |
| S09 | `A/lyrics/AndroidLyricsService.kt`, `LyricsProvider`, `AndroidLyricsService.fetch/search`, `firstLyric`, `LyricsTrack`, `LyricsSearchResult` | Embedded → sidecar → LRCLIB → KuGou fetch priority; manual search fans out enabled providers and flattens results. No dedicated Find Lyrics ViewModel: state lives in `FindLyricsSearchState`. |
| S10 | same file, `LrclibLyricsProvider.exact/fetch/search`, `LrclibCandidate` | `/api/get` then `/api/search`, title/artist/album/duration; synchronized text preferred over plain. |
| S11 | same file, `KugouLyricsProvider.fetch/search`, `kugouSearch`, `kugouDownload`, `request` | HTTP search at `krcs.kugou.com`; HTTPS download at `lyrics.kugou.com`; Base64 LRC; up to three automatic matching attempts, up to five manual downloads. HTTP helper has 10 s connect/read timeouts and disconnect-on-cancel, but no body-size limit. |
| S12 | `K/lyrics/LyricsMatching.kt`, `lyricsCandidateScore`, `bestLyricsCandidate`, `normalizeLyricsText` | Title similarity floor .7, title/artist weights .5/.3; known duration outside ±5 seconds rejected, duration weight .2; provider score breaks ties. No strong recording-version discriminator. |
| S13 | `A/lyrics/LyricsPreferences.kt`, `LyricsSettings`, `LyricsPreferences` | Four booleans default true; no priority list, automatic-search preference or negative cache. |
| S14 | `A/ui/components/FindLyricsContent.kt`, `FindLyricsSearchState`, `FindLyricsContent`, `FindLyricsPreviewContent` | Editable title/artist, read-only duration, explicit search, provider-labeled rows, full-text preview and select. Selection calls the activity's database save. |
| S15 | `A/sync/SyncDatabase.kt`, `ProviderLyricEntity`, `SyncDao.observeProviderLyrics`, `AndroidLibrarySyncStore.providerLyrics/saveProviderLyrics/lyricsTrack`, `LibraryTrack.toLyricsTrack`, `SyncDatabase` | `provider_lyrics(trackId,content,source)` is one selected result per track, Room version 14, migration 11→12 created it. No TTL or candidates. `toLyricsTrack` converts duration milliseconds to seconds. Read flow projects content only. Existing destructive fallback is a risk, not permission to use one in new migrations. |
| S16 | `A/sync/EmbeddedTagReader.kt`, `embeddedLyricsText`, `mp4Lyrics`, `id3Uslt`, `oggVorbisLyrics` | Reads FLAC/Ogg comment lyrics, MP4 ©lyr/freeform, ID3 USLT/ULT, RIFF/FORM ID3. SYLT explicitly TODO. Uses paths/RandomAccessFile at this baseline. No audio-tag writer found. |
| S17 | `A/ui/navigation/FullScreenPlayerLyricsPanel.kt`, `PlayerLyricLine`, `parsePlayerLyrics`, `SyncedLyricsList` | Plain and line LRC with offsets/bilingual text, tap seeking and browse/follow. Enhanced-LRC word markers are stripped; current UI does not render word timing. |
| S18 | `A/sync/ArtworkBitmaps.kt`, `decodeArtworkBitmaps`; `A/sync/PlaylistArtworkStorage.kt`, `stageArtistArtwork`, `stagePlaylistArtwork`; `A/sync/SyncDatabase.kt`, `ArtistArtworkStagingEntity` | Shared static artwork file → embedded picture resolver used by UI/session; manual artist image staging already exists, JPEG ≤2048 sizing, hash paths in private files. Reuse manual override, not duplicate it. |
| S19 | `A/player/PlaybackController.kt`, `PlaybackController`; `A/player/PlaybackModels.kt`, `PlaybackState`, `PlaybackItem`, `ArtworkCrossfadeTransition` | State/queue/artworkCrossfade/moodRadioActive flows and crossfadeSeconds Flow; play/pause/resume/stop/next/previous/seek/queue commands are the UI seam. `PlaybackItem` has albumId but no dedicated artist ID field; resolve through library. |
| S20 | `metadata/src/main/kotlin/com/amwolfstein/sistrum/metadata/Metadata.kt`, `Metadata.read`; `Document.kt`, `Document`; `Id3.kt`, `Id3`; `Mp4Tags.kt`, `Mp4Tags` | Bounded read-only metadata port. Projected Document alone is not a lossless rewrite representation. Future writer belongs here but must preserve raw unknown blocks. |
| S21 | `settings.gradle.kts`, module includes; `gradle/libs.versions.toml`, versions/libraries; `androidApp/src/main/AndroidManifest.xml`, application/services | Modules androidApp/sharedLogic/codecs/metadata/analysis/dsp; Media3 1.11.1, Haze 2.0.0, Room 2.8.5, DataStore 1.2.1, min API31. Internet, media read and foreground permissions; global cleartext enabled. No isolated extension service at baseline. |
| S22 | `A/analysis/LoudnessAnalysisWorker.kt`, `LoudnessAnalysisWorker`; `A/sync/MediaStoreLibraryScanner.kt`, `MediaStoreLibraryScanner`; `A/sync/ScanFilterPreferences.kt`, `ScanFilterPreferences`; `A/ui/screens/ScanFilterContent.kt`, `ScanFilterContent` | Worker/scanner/storage boundaries being changed by 002. Existing folder UI drops tree capability at baseline; D-31 replaces that design. Do not port baseline path-only behavior into 004. |

Source paths are case-sensitive; `AndroidLibrarySyncStore` is in `sync/SyncDatabase.kt`, not a separate file. No invented LyricsRepository or FindLyricsViewModel is treated as existing architecture.

## Reference map — VERIFIED IN SOURCE

`EA = app/src/main/kotlin/com/ozyern/exhale/`; `MX = rust_backend/crates/extensions/src/`; `RA = app/src/main/java/chromahub/rhythm/app/`. Revision for every row is its repository alias in the ledger.

| ID | Repository, exact path + symbol | Finding |
|---|---|---|
| E01 | E `EA/ui/screens/library/LibraryScreen.kt::LibraryScreen`, `LibraryShelves.kt::LibraryDestinationGrid/LibraryCollectionsRow/LibraryCardRow` | Section headers, destination tiles and horizontal collection shelves. |
| E02 | E `EA/ui/screens/AlbumScreen.kt::AlbumScreen`, `ui/screens/artist/ArtistScreen.kt::ArtistScreen`, `ui/screens/playlist/LocalPlaylistScreen.kt::LocalPlaylistScreen`, `ui/screens/search/LocalSearchScreen.kt::LocalSearchScreen` | Hero detail pages, artist shelves, reorderable local playlist and lazy local search. Use visual structure, not network/download behavior. |
| E03 | E `EA/ui/screens/settings/PlayerStyleScreen.kt::PlayerStyleOption/currentPlayerStyle`, `constants/PreferenceKeys.kt::PlayerDesignStyle`; `EA/ui/player/Player.kt::BottomSheetPlayer` | IMMERSIVE explicitly V7; APPLE_MUSIC V8. No LARGE label. Now Playing/Liquid Glass are separate paths with null design. |
| E04 | E `EA/ui/player/Player.kt::BottomSheetPlayer` V7/V8 branches, `Player.kt::V7PlayerBackdrop`, `PlayerComponents.kt::V8PlayerBackdrop`; `LiquidGlassPlayer.kt::LiquidGlassPlayerScreen`; `LiquidGlassBackdrop.kt::LiquidGlassArtworkBackdrop` | V7 full-cover backdrop and bottom controls; V8 floating-cover treatment. Distinguish these from Liquid Glass player and shader backdrop. |
| E05 | E `EA/ui/component/Glass.kt::GlassSurface/clearGlass/dockGlass/miniPlayerGlass`, `ui/theme/GlassTheme.kt::GlassTokens/glassTokensOf/MotionTokens` | Haze fallback and `com.kyant.backdrop` lens/vibrancy effects are different mechanisms. Tokens do not prove compatible dependency APIs with Sistrum Haze2. |
| E06 | E `EA/ui/player/MiniPlayer.kt::MiniPlayer`, `ui/player/LyricsScreen.kt::LyricsScreen/AppleLyricsBackground`, `ui/player/GlassLyrics.kt::GlassLyricsView` | Mini-player, album-color lyrics background and lyrics presentation references. Do not copy automatic fetch or replace Sistrum pipeline. |
| E07 | E `EA/constants/Dimensions.kt`, `MiniPlayerHeight`, `FloatingToolbarHeight`, `MiniPlayerPillHorizontalInset`, sheet animation specs | 64 dp mini-player, 72 dp toolbar, 16 dp inset; reference geometry only, Sistrum collapse behavior wins. |
| M01 | M `lib/screens/home_tab.dart::HomeTab`, `home_tab_recent.dart::_buildRecentAccess/_buildRecentAccessHeader`, `home_tab_widgets.dart::_QuickPicksPageView` | Home sections/recent access/cards; Flutter implementation must be re-expressed in Compose with local Sistrum data. |
| M02 | M `MX/runtime.rs::ExtensionRuntime/RuntimeLimits/ExtensionServices`, `rust_backend/Cargo.toml::rquickjs` | Rust QuickJS (`rquickjs = 0.14.0`), dedicated worker, bounded queue; defaults 64 MiB heap, 512 KiB stack, 30 s; interrupt callback. Thread/VM isolation is not OS isolation. |
| M03 | M `MX/prelude.js::registerExtension/http/fetch/storage/utils`, `network_host.rs::register`, `host.rs::register` | Global registration; synchronous HTTP/fetch response objects, namespaced storage, utilities and legacy compatibility semantics. Browser async fetch is not drop-in. |
| M04 | M `MX/package.rs::ExtensionPackage.open/extract/clean_path`; `repository/model.rs::Registry/RepoExtension/Registry.parse`; `repository/package.rs::write_verified_package` | ZIP `.sflx`/`.spotiflac-ext`, root manifest/index; archive checks; SHA256 downloads and registry model. |
| M05 | M `MX/manifest.rs::ExtensionManifest/ExtensionPermissions/is_domain_allowed`, `environment.rs::ExtensionEnvironment/validate_gates`, `manager.rs::ExtensionManager.install/set_enabled/initialize/cleanup` | Manifest, runtime feature gates, lifecycle and manager services. |
| M06 | M `MX/lyrics.rs::InstalledLyricsFetcher.fetch/extensions`, `provider.js::lyrics` (export key `fetchLyrics`) adapter | `lyrics_provider` registration routes `fetchLyrics(track,artist,album,duration)` to installed enabled providers; converts output through native lyrics types. |
| M07 | M `rust_backend/crates/network/src/policy.rs::NetworkPermissions.validate/private_literal_or_local/is_private_ip` | Validates network permissions and private addresses including mapped IPs; useful test/design reference, not proof Sistrum has these defenses. |
| X01 | X `registry.json`; all `extensions/*.sflx`, `manifest.json` and `index.js` members | All 12 downloaded registry packages are byte-identical to packages at X. Artifact hashes in extension format document independently identify remote bytes. |
| X02 | X `sources/apple-music/index.js::getAlbum/getArtist/motionArtworkVideoURL/artistHeaderArtwork/fetchToken/fetchLyrics/parseTTML/ttmlToLyricsResult`; manifest | Album/artist header motion, portrait fields, TTML enhanced LRC; web token scraping and subscription user token requirement. Source index is byte-identical to downloaded package member. |
| X03 | X `extensions/ytmusic-spotiflac.sflx!index.js::getArtist`, `!manifest.json` | Artist browse headers produce `image_url` and `header_image`; synchronous fetch host surface. Album fallbacks elsewhere must not be labeled portrait. |
| X04 | X `extensions/deezer.sflx!index.js::getArtist/coverFromArtist`, `!manifest.json` | Artist object picture fields produce image_url; manifest also requires signed-session/file runtime features beyond Sistrum scope. |
| X05 | X `extensions/qobuz-web.sflx!index.js::tryFetchLyricsLRC`, `extensions/tidal-web.sflx!index.js::tryFetchLyricsLRC` | Helpers exist but not registered lyrics-provider APIs; do not list as compatible Find Lyrics providers. |
| F01 | F `lib/features/settings/screens/library_settings_screen.dart::LibrarySettingsScreen/_LibrarySettingsScreenState` | Folder cards, scan state/progress, library and cache sections. Do not import all-files access, audio conversion or biography functionality. |
| B01 | B `app/src/main/java/com/music/bitchord/data/settings/AppSettings.kt::hideVolumeBar` and setter; `sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/NowPlayingScreen.kt::NowPlayingScreen` | Persistent hide-volume boolean default false, conditional slider. Sistrum uses its own DataStore. |
| R01 | R `RA/infrastructure/network/WikipediaProvider.kt::WikipediaProvider.getAlbumDescription/fetchPageSummary` | English summary endpoint, album/artist query heuristics; no artist biography API here; exception-to-null and weak disambiguation must not be copied. |
| R02 | R `RA/infrastructure/network/AppleMusicCanvasProvider.kt::getBySongArtist/getByAlbumArtist/getOrFetchToken` | Motion matching/cache concepts, dynamic token scraping/fallback; no credential copying. |
| R03 | R `RA/infrastructure/network/YTMusicApiService.kt::YTMusicSearchResponse.extractArtistImageUrl` | Looks for artist UC browse IDs before image extraction; matching concept, not approved API access. |
| R04 | R `RA/shared/presentation/components/player/CanvasArtworkPlayer.kt::CanvasArtworkPlayer` | Separate ExoPlayer, muted/repeat-one, audio-focus handling false, bounded buffer, disposal; strengthen to audio-track-disabled and visibility gating. |

## Graphify and limitations

**VERIFIED IN SOURCE:** installed `graphify --help` exposes query/explain/path/affected as well as mutating commands. Only help, query and explain ran. The committed graph was copied with `git show HEAD:graphify-out/graph.json` into `/tmp/sistrum-refs/sistrum-committed-graph.json` (11,285 nodes). Queries covered navigation, artwork, LyricsProvider, SyncDatabase and LiquidGlass; `explain AndroidLyricsService` connected activity → service → providers/store. Broad queries truncated, so direct source reads established findings. No index refresh/extract/update ran. Graph freshness beyond its committed baseline is UNKNOWN; graph names are leads, not proof.

Working-copy evidence: `specs/002-kotlin-decoders/DECISIONS.md::D-31/D-32`, `CLAUDE.md::Playback/Library scan/Localization`, and `specs/004-ui-extensions/NOTES.md` are separately identified owner inputs. Planned 002 folder grants and 003 native removal are dependencies, not baseline implemented facts.
