# UI visual and interaction specification

Evidence: [source map](01-reference-source-map.md), E01–E07, M01, F01, B01 and S02–S08/S17. Reference screenshots were not rendered; all observations below are source-level.

## VERIFIED IN SOURCE reference direction

Exhale supplies library destination tiles/shelves, lazy detail lists, artist headers, local playlist and search, floating mini-player and lyrics backdrop. Its `PlayerStyleOption.IMMERSIVE` selects V7, while APPLE_MUSIC selects V8; Now Playing and Liquid Glass follow separate paths. There is no LARGE identifier (E03/E04). Exhale uses both Haze and a separate backdrop lens/vibrancy library (E05); calling both “Haze” would be inaccurate. Home follows SpotiFLAC-Mobile's recent access and quick-pick/card structures (M01). Flick is Library Settings inspiration only (F01). BitChord provides persistent hide-volume behavior (B01).

## PROPOSED foundation

Keep Sistrum colors/typography and appearance preferences as the authority. Define semantic tokens for page background, glass tint/opaque fill, hero scrim, main/muted text, accent, dividers, disabled states and focus ring, derived for Light/Dark/System. Initial spacing scale 4/8/12/16/24/32 dp, page inset 16 dp on narrow lists and 24 dp on detail hero, 12–28 dp card corners, 48 dp minimum interactive targets. These are Sistrum proposals, not measured Exhale dimensions. E07's 64/72 dp mini-player/nav and 16 dp inset inform review; existing Sistrum compact geometry wins until characterized.

Use one Haze source per surface group; avoid nested full-screen blur, live blurred video and repeated palette extraction. First adapt Sistrum's Haze2 wrapper; do not add Exhale's backdrop dependency without a separate measured/approved spike. Reduce Transparency produces opaque surfaces and readable scrims, without rendering hidden blur underneath. Reduced Motion is a separate preference/system-policy input: no looping backgrounds or large morphs when active. Keep dynamic color optional and text contrast independent of artwork. Use original/simple placeholder assets in screenshots.

## PROPOSED screens

| Surface | Layout and behavior | Retained Sistrum features |
|---|---|---|
| Home | Large local heading; recent listening/local collections; horizontally paged quick picks and recently added cards, inspired by M01 | Local data only; existing track actions, statistics-driven sections; no remote Home feed or downloading implied |
| Library | Destination tiles and collection shelves (E01), with tracks/albums/artists/playlists views | Search, genres, composers, sorts, filters, favorites, empty and choose-folders states |
| Album | Artwork hero, title/artist/year/count/duration, play/shuffle, lazy tracks (E02) | Disc/order, favorite/queue/context actions, manual/local artwork fallback |
| Artist | Identity-aware portrait/header, play/shuffle, local albums and tracks, optional separately attributed About | Manual artist override first; ambiguous match does not silently assign a portrait |
| Playlist | Cover/title/count/actions with local track list and drag affordances (E02) | Rename/delete/import/reorder/favorites semantics; no upstream remote queue model |
| Search | Persistent query, categorized local results, clear/empty/loading/errors (E02) | Sistrum local scope, back and restored query/scroll; keyboard/screen-reader focus |
| Find Lyrics | Existing editable title/artist + duration; provider status, consolidated candidates, preview and explicit selection | Existing providers/local-first; offline selected lyrics; separate Cache / Embed / Sidecar actions |
| Library Settings | Sections for chosen folders, scan/progress/last result, format/metadata scan settings and cache totals (F01) | D-31 required chosen-folder model; no excluded-folder mode, conversion feature or Flick biography |
| Extensions | Default registry details, Available/Installed, version/compatibility/permissions/errors; refresh/install/enable/manual-update/rollback/uninstall | Explicit permission changes; no auto-install or background code update |

All surfaces require loading, empty, missing data, permission-denied and recoverable error representations; content remains usable offline. Provider-enriched content has attribution and status distinct from file tags. Never present a missing match as a fabricated biography/image.

## PROPOSED player styles

| Sistrum persisted value | Planned rendering | Mapping confidence |
|---|---|---|
| `DEFAULT` | Existing `FullScreenPlayer` presentation and gestures | Owner-required unchanged default; characterize before extraction |
| `LARGE` | V8/APPLE_MUSIC-inspired floating large square artwork with bottom transport and artwork-color backdrop | Proposed selection based on E03/E04, not an upstream LARGE name; visual review before implementation |
| `IMMERSIVE` | V7-inspired full-cover art with protective scrim and bottom controls | Upstream V7 mapping verified; Sistrum adaptation proposed |

Persist stable string values in DataStore; missing/unknown values resolve to DEFAULT. Style changes never rebuild audio players or mutate queue. No separate Full-Screen Cover Art toggle. Hide Volume Bar defaults false and applies to all three styles; hardware volume remains available. DEFAULT gains only the explicitly requested shared setting and provider/lyrics content, not an unrequested layout redesign.

Preserve current circle collapse, paired mini-player relocation, destination state restoration and back order. Opening player/sheets disables conflicting navigation drag. One animation geometry source drives nav and mini-player bounds, respecting insets and keyboard. Existing artworkCrossfade is an input; never repurpose audio crossfade timing. Reference spring ideas can be adapted only after baseline gestures and screenshot comparisons.

Lyrics use a legible album-color backdrop, strong active-line emphasis, neighboring-line de-emphasis, tap seek and browse/follow affordances. Reduced Motion removes animated scrolling/blur while retaining active-line contrast. Preserve existing bilingual/offset parser behavior. Word highlighting requires an additive word model/parser with tests; do not claim current enhanced-LRC marker stripping supports it.

## PROPOSED accessibility and localization acceptance

English+Arabic strings and complete Arabic plurals; western digits via existing helpers. Text uses Content direction/start alignment. Transport, seek and volume remain LTR. Apply `towardsEnd()` only to physical drag/graphics x values; do not double-mirror logical offsets. Screen-reader labels include source/error/state, decorative images have no duplicate descriptions. Test 1.0/1.3/2.0 font scales, long Arabic/English names, landscape, cutouts, dark/bright artwork and touch exploration. Preserve focus through expansion and style changes. No clipped essential actions at large font size.

## UNKNOWN / NEEDS VALIDATION and BLOCKED

Pixel parity, low-end frame/battery budgets and backdrop API compatibility need future Compose/device verification. Motion/provider content can be previewed with synthetic assets while real service integrations remain BLOCKED by runtime or rights gates. This document authorizes no implementation or dependency change.
