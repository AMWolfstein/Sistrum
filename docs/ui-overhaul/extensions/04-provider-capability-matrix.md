# Provider capability matrix

**VERIFIED IN SOURCE:** all 12 package hashes/member paths and X revision `0290b263802d3418e45583612574b749289bf369` are linked through [format audit](01-registry-and-package-format.md) and [source map](../01-reference-source-map.md) X01–X05. A “yes” below is method/output presence, not a successful online request, rights clearance or Sistrum compatibility.

| Package | Observed registration/type | Requested relevant capability | Compatibility disposition — PROPOSED |
|---|---|---|---|
| apple-music 1.4.12 | metadata_provider + lyrics_provider; getAlbum/getArtist/fetchLyrics | Album/artist header_video/header_image; artist artwork; TTML line/word output | Candidate exact profile; token scraping/rights gate; storage/http/utils needed |
| ytmusic-spotiflac 2.4.1 | metadata_provider + download_provider; getArtist | `image_url`, `header_image` from artist browse headers | Candidate artist-only profile if whole-package requirements can be safely validated; synchronous fetch shim; block download calls |
| deezer 1.3.5 | metadata_provider + download_provider; getArtist | artist picture_xl/big/medium → image_url | Full package incompatible initially: required signedSession@1/sessionGrant@1/patternedFileTransform@1/preparedContext@1; narrow app adapter is distinct proposal |
| spotify-web 1.10.2 | metadata_provider; getArtist/getAlbum/search and enrichment | General metadata/artwork, no registered fetchLyrics | Out of initial scope; text helper called normalizeLyricsText is not a lyrics provider |
| amazon 2.3.11 | metadata_provider + download_provider | General metadata, no registered fetchLyrics | Unsupported signedSession/sessionGrant/preparedContext; downloads/native conversion excluded |
| soundcloud 1.0.10 | metadata_provider + download_provider; skipLyrics=true | No registered lyrics API | Out of scope; required preparedContext/downloadSegments unsupported |
| qobuz-web 1.2.21 | metadata_provider + download_provider | `tryFetchLyricsLRC` helper, not lyrics_provider registration | Not a Find Lyrics provider; signedSession@3/sessionGrant/preparedContext unsupported |
| tidal-web 1.2.7 | metadata_provider + download_provider | `tryFetchLyricsLRC` helper, not lyrics_provider registration | Not a Find Lyrics provider; signedSession@3/sessionGrant/downloadSegments unsupported |
| bugs-music 1.6.2 | metadata_provider; charts/home/getArtist | General metadata; no registered lyrics or verified motion method | Available listing can say unsupported/out of requested scope |
| melon-music 1.2.4 | metadata_provider; charts/home/getArtist | General metadata; no registered lyrics API | Same |
| genie-music 1.0.29 | metadata_provider; charts/home/getArtist | General metadata; no registered lyrics API | Same |
| jiosaavn 1.0.6 | metadata_provider + download_provider | General metadata; no registered lyrics API | Required preparedContext unsupported; downloads excluded |

Each row comes from that package's `manifest.json` and `index.js::registerExtension` object at X (archive names in format inventory; amazon archive is `extensions/amzn.sflx`). Apple `sources/apple-music/index.js` equals its package source. Only Apple declares and registers an extension lyrics provider in this snapshot. LRCLIB and KuGou are Sistrum built-ins (S10/S11), not entries in this registry. Wikipedia is a separate proposed repository, not a verified registry capability.

## Matching and output cautions

Apple `getAlbum` requests editorialVideo and returns formatted album fields with header_video; `getArtist` requests editorialVideo/editorialArtwork and uses artist-specific artwork helpers (X02). Motion can be absent and HLS rather than a local loop file. Artist keys can be square/wide; album prefers tall. YouTube Music `getArtist` follows artist headers and uses artist browse identity (X03); do not label fallback song/album art elsewhere as a verified portrait. Deezer artist endpoint/picture fields are distinct from album art (X04).

PROPOSED: requested artist priority YouTube Music → Deezer → existing local artwork, editable per user, with manual override taking precedence. An unsupported/disabled provider yields a visible reason then fallback, not a false success. Match on stable identifiers when available and verified artist/album kind; ambiguous names require manual selection. No provider gets library enumeration.

UNKNOWN / NEEDS VALIDATION: live availability, region/subscription/API changes, all packages' dynamic host calls and precise rights. BLOCKED: real credentials, persistent content or package execution before compatibility/security/rights decisions. A registry category/tag or helper function alone is insufficient evidence for a usable capability.
