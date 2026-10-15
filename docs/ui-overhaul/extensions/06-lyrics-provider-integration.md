# Extend existing Find Lyrics

## VERIFIED IN SOURCE baseline

Evidence: [source map](../01-reference-source-map.md) S09–S17 at S `7203f18deadf1ca589c03f1b41572a157a8f4ce3`; X02/M06 at pinned revisions. Built-ins are **LRCLIB** and **KuGou**. Local embedded → sidecar → LRCLIB → KuGou order is hard-coded in `AndroidLyricsService`; preferences enable all four by default. `fetch` stops after first result, `search` fans out and returns flattened results. Existing match model uses title/artist/duration/provider score; LRCLIB exact lookup also uses album. Cache is `ProviderLyricEntity` content/source per track. Preview/edit search inputs/manual select exist; no embedding writer, priorities/dedup/negative-result store or word-rendering model exists. MainActivity currently automatically fetches missing playing-track lyrics.

LRCLIB `/api/get` and `/api/search` produce plain or synced text; KuGou HTTP search/HTTPS download produces Base64 LRC. Source suffixes distinguish plain/synced/local. `firstLyric` propagates cancellation, but manual search's `runCatching` can consume it. Preserve/regression-test both providers while fixing only scoped new behavior. Existing sidecar uses path siblings `.lrc` then `.txt`; 002 URI grants mean that access must be adapted, not discarded.

Apple `fetchLyrics(trackName,artistName,albumName,durationSec)` is the only registered extension lyrics provider verified in this registry. It requires Media User Token/subscription, scrapes developer token, requests syllable-lyrics then lyrics, parses TTML and returns line records with enhanced word timestamps/plain text. Word timing presence varies by track. Qobuz/Tidal helpers are not provider registrations. Do not advertise unverified providers.

## PROPOSED search policy and models

Keep built-in interfaces callable through an adapter; evolve additively. Proposed candidate envelope: providerId/packageHash/providerTrackId?, title/artist/album?, durationMs?, recording IDs?, plainText, originalRaw?, timingKind Plain/Line/Word, lines/words where valid, language, source URL/attribution, retrievedAt, match explanation, rights (Display/Cache/Embed/Sidecar separately Allowed/Denied/Unknown). Unknown rights deny persistent export/embedding; user confirmation does not override provider terms. Legacy cache rows remain readable as content+source without claimed rights.

Local selected lyrics and readable embedded/sidecar lyrics first. Network manual search remains available when enabled; automatic missing search defaults OFF in a new versioned preference even for existing installs, an explicit owner-requested policy change. Do not disable LRCLIB/KuGou booleans or delete selections. Enabling auto search separately explains metadata sharing; no library-wide lookup on load/open. Coalesce same-track in-flight queries. Provider priority affects automatic fetch and result ordering; manual search aggregates enabled compatible providers with bounded concurrency2 and cancellation per operation.

Per-provider outcomes: Success/NotFound/AuthRequired/Disabled/Incompatible/RateLimited/NetworkError/Cancelled; show partial results plus provider errors. Retry/backoff and negative cache are specified in architecture doc; missing ≠ transport error. Never repeatedly query the same missing recording on each recomposition. Redact queries/tokens in logs while preserving provider+cause diagnostics.

Match recording IDs first when available, then title/artist/album/known duration and edition qualifiers. Preserve live/remix/instrumental/cover distinctions; avoid normalization that collapses them. Unknown duration removes a signal rather than inventing zero-length agreement. Consolidate duplicate content/timing with multiple source attributions, not title-only dedup; retain differing editions/translations and user choice. Rank with explanations; manual confirmation remains mandatory for selection. Result preview includes source, timing kind and explicit cache/embed availability.

Selection saves existing content/source, then content-hash-bound companion provenance/word data. Offline selected lyrics never depend on extension availability. Display new word timings only through an additive parsed model; current parser's offset/repeated timestamps/bilingual lines/tap seek/browse rules remain covered. Convert upstream milliseconds and duration seconds at adapter boundary, validate monotonic nonnegative bounded times, accept plain/line-only output truthfully. Preserve original raw where permitted for lossless provenance but never execute/render it as HTML.

## PROPOSED integration chain

FindLyricsContent → search coordinator → local adapters + LRCLIB/KuGou + installed enabled compatible lyrics adapters → normalized candidates/statuses → preview → trusted selected-cache save. Separate buttons trigger trusted writer or sidecar coordinator. Provider registration is not permission to write. Automatic fetch never writes audio files or sidecars.

## UNKNOWN / NEEDS VALIDATION / BLOCKED

Live provider output, token authorization, terms for caching/embedding and actual word timing accuracy need fixtures and provider review. Apple execution/embedding is BLOCKED until runtime/auth/rights gates. Existing providers are preserved but historical technical availability does not establish lyric copyright clearance; future durable export actions require a rights decision. Local/user-authored lyrics can test the writer independently.
