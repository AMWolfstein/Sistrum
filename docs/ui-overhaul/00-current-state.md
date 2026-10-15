# Current architecture audit

Evidence classification and complete revision/path/symbol citations: [source map](01-reference-source-map.md). Audit baseline S = `7203f18deadf1ca589c03f1b41572a157a8f4ce3`.

## VERIFIED IN SOURCE

The actual worktree is `/home/amwolfstein/Documents/GitHub/Sistrum-UI`, branch `feature/media3-migration`. It is shared with 002, not isolated from it. At first inspection numerous application/decoder/spec/graph/tooling files were already dirty; they were preserved. The resume instruction says no other process is now running, which does not make those changes ours. `feature/ui-overhaul` is stale per NOTES.md. No earlier `docs/ui-overhaul/` existed. This audit creates documentation from scratch.

`MainActivity.onCreate → App → AppDestinationContent → screen composables` combines lifecycle-collected ViewModels and callback-based commands (S01–S08). Library/detail/search screens already implement local playback, favorites, queue and context actions. Home has local card sections. Preserve genres, composers, statistics, playlists, Last.fm and Mood Radio entry behavior while changing presentation. Navigation owns back handling and saved state; compact chrome moves the mini-player alongside the circular nav (S02–S04), rather than merely hiding a toolbar.

The UI consumes `PlaybackController.state/queue/artworkCrossfade/moodRadioActive/crossfadeSeconds` and commands (S19). Media3 is the default since 001 according to owner working-copy CLAUDE.md; Native remains pending 003. Decoder, DSP, normalization, queue internals and session service are migration-owned. Do not infer that stale constitution descriptions of old default/path-only playback supersede D-31 or completed 001 decisions.

Theme preferences persist Light/Dark/System and Reduce Transparency. Haze2 glass has a null-state opaque fallback and quality sampling (S05). English and Arabic only; western digits, Content text direction, LTR transport/seek/volume and RTL-safe physical deltas are project requirements (working-copy CLAUDE.md Localization).

`AndroidLyricsService` uses local embedded and sidecar readers plus LRCLIB and KuGou (S09–S13). Manual search is parallel; fetch is sequential. `FindLyricsSearchState` is composable state, not a dedicated ViewModel. Preview/select exists (S14). `MainActivity` saves selection into `provider_lyrics`, one content/source row per track, Room v14, with a content-only observer (S15). This is persistent cache/selection, not file embedding. Existing parsing supports plain/line LRC and strips word markers (S17). EmbeddedTagReader supports multiple tag containers but not SYLT and has no writer (S16). Metadata.read in `:metadata` is read-only (S20).

Static artwork is shared between UI and media-session metadata through `decodeArtworkBitmaps`, file then embedded fallback. Artist manual overrides already have staging/storage; reuse them (S18). No generic motion/online artwork repository or registry/runtime infrastructure is established by these source reads.

Modules, dependencies, workers and permissions are S21–S22. Network lyrics clients use HttpURLConnection, not an assumed shared OkHttp/Retrofit client. A loudness WorkManager worker exists; extension refresh scheduling is new. INTERNET and cleartext are allowed globally; a new broker must enforce HTTPS itself.

## PROPOSED changes and impact

Add presentation contracts outside player internals, optional repository enrichment, bounded provider caches and a trusted URI-based writer. Preserve selected lyrics in the existing store; companion provenance/candidate/negative records must not duplicate the library graph. See [contracts](09-migration-integration-contract.md) and [storage architecture](extensions/00-extension-architecture.md).

Automatic missing-lyrics network requests currently occur for the playing track with default-enabled providers (S01/S13). Change that explicitly to local-first and automatic online search OFF, while preserving manual search and both built-ins. Existing saved selections remain readable. This is an intended spec-004 behavior change, not a claim that the current default is already OFF.

## UNKNOWN / NEEDS VALIDATION

D-31 folder-grant implementation and scanner `:metadata` adoption are in flight in 002; 003 will remove native paths. Re-audit them at implementation baseline. Current paths in EmbeddedTagReader/sidecar access need URI-aware integration after 002, not speculative fixes during Phase 0. No build, test, screenshot, runtime or service-live compatibility results were measured here.

## BLOCKED parts and recorded defects

Full native QuickJS/Rust adaptation conflicts with the no-native constitution. Apple credential scraping, service rights, and package authenticity need gates. Existing manual-search `runCatching` can consume cancellation; sidecar read errors become null; global cleartext and unbounded lyrics bodies need scoped follow-up tasks. Room's destructive fallback is an existing risk; no new destructive migration is authorized. No application defect was edited during this audit.
