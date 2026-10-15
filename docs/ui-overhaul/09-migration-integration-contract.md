# Media3 / WaxFlow integration contract

**VERIFIED IN SOURCE:** S01–S04, S15/S18–S22 in the [source map](01-reference-source-map.md), baseline S `7203f18deadf1ca589c03f1b41572a157a8f4ce3`. Working-copy D-31/D-32 and NOTES.md govern future folder grants and moved analysis work. All new interfaces below are **PROPOSED** names, not existing classes.

## Stable consumption boundaries

| Contract | Producer → consumer / payload | Ownership and refusal rules |
|---|---|---|
| Current track | PlaybackController state + library lookup → UI `CurrentTrackSnapshot(trackId,title,artists,albumId,album,durationMs,artworkRevision)` | Read-only mapping outside player; no network source accepted as a music item. Duration remains milliseconds in UI. Provider adapter alone converts to seconds. |
| Artist/album identity | Library IDs and local tags → `EntityIdentity(localId,kind,names,albumArtist,externalIds?)` | PlaybackItem has albumId, no artistId: use library association, never infer by splitting display string alone. Provider IDs are namespaced; ambiguity typed as NeedsSelection. |
| Static artwork | Existing shared resolver/manual override → `ArtworkRepository.observe(entity)` | Manual > selected provider cache > existing local static fallback. Do not change session resolver independently. Downloads never overwrite embedded/file metadata. |
| Lyrics read | Existing provider_lyrics + embedded/sidecar URI adapters → `LyricsRepository.observe(trackId)` | Preserve content/source and existing line/bilingual semantics; additive structured sidecar holds word timing/provenance. Cache misses/errors distinct. |
| Lyrics search | FindLyrics intent → enabled built-ins and approved installed extensions → `SearchOutcome(candidates,providerStatuses)` | Optional network, auto missing OFF, local sources first, cancellation propagated, no writes. Only minimum metadata crosses runtime boundary. |
| Lyrics select | Preview confirmation → selected cache content + provenance transaction/outbox | Track ID/revision checked on completion; late result cannot bind to newly playing track. Selection does not imply file write. |
| Lyrics write | Explicit trusted UI action → `LyricsWriteCoordinator.plan/execute` → `:metadata` structural writer + ContentResolver | No extension URI/FD/path access. Fresh capability, replacement confirmation, recording rights, staged verification and recovery journal required. |
| Motion artwork | Repository validated asset descriptor → `MotionArtworkSession` owned by visible UI | Independent video-only ExoPlayer, audio track disabled, volume zero, no focus/session/service/DSP attachment. Pause/dispose on invisibility/background/reduced motion/power policy. |
| Player UI | Playback state/queue/availability + style preference → immutable `PlayerUiState`, command callbacks | Never expose Engine/Decoder/AudioSink objects; styles do not instantiate audio player or take ownership of queue. |
| Analysis decoder selection | Analysis-only OfflineDecoder renderer list + setting-free AnalysisDecoderChoice → worker | M4b exception only after 002; playback Media3PlayerFactory unchanged and asserted by tests. No UI/provider dependency. |

## Invariants and failures

Keep PlaybackController public API and flows, PlaybackQueue/ListeningTracker unchanged. In this baseline crossfadeSeconds is exposed as Flow<Int>, not falsely described as a StateFlow. Preserve crossfade equal-power behavior, normalization, DSP, statistics, Last.fm, queue restore, Mood Radio and error attribution. Preserve analysis sync_documents shape/readers. No change to DecoderProvider playback priority for UI/motion.

Music stays local. Motion may fetch video through its separate artwork network/cache pipeline, subject to rights; it must never feed network audio to playback service. Disabling motion mid-video releases video resources without a pause/resume command to music. A video with audio, focus callbacks or metadata events must leave music gain/session/queue/position unaffected.

Tag writes cannot run while source is actively decoding/analyzing; trusted boundary reports Busy with retry, no hidden stop/restart of music. Lock by canonical storage identity and consult current playback/analysis ownership through a narrow read-only integration seam agreed with migration owner. Metadata-only writes do not invalidate loudness by modified time alone when audio essence is identical; verify fingerprint/version behavior against final 002 scanner before implementing. Changed URI after a provider rename must reconcile track identity without losing playlists, stats or selected lyrics.

All long requests carry operation ID, track/entity revision, deadline and cancellation. Late completions after disable/uninstall/track change are ignored with typed stale results. Exceptions are logged by provider and cause with token/query/path redaction; CancellationException propagates.

## Expected conflict ledger — PROPOSED

| File/area | Conflict / resolution plan |
|---|---|
| MainActivity/App | Concurrent wiring, player state and permission prompts; add adapters after final migration baseline, preserve callbacks |
| Settings/ScanFilterPreferences/scanner | D-31 removes blacklist and persists read tree grants; re-audit actual tree mapping and permission flags, don't resurrect old UI |
| EmbeddedTagReader/:metadata | 002/003 redirect reads into metadata; add writer against final read contract and preservation fixtures, no FFmpeg writer |
| SyncDatabase | v14 baseline may move; new provider store isolated, explicit migrations only if selected-lyrics contract needs one |
| ArtworkBitmaps/PlaybackService | Static/session artwork parity; add repository outside service then test shared resolver behavior |
| Gradle/catalog/manifest | Migration removes native dependencies; runtime POC cannot reintroduce them without owner ADR; no versions changed in Phase 0 |
| OfflineDecoder/Media3PlayerFactory | M4b owns analysis renderers only; snapshot playback renderer order and assert unchanged |
| strings + Arabic | Keep final D-22 languages and helpers, no upstream localization wholesale copy |
| Graph index | Read-only during audit; later implementation follows its own brief, not this planning pass |

## UNKNOWN / NEEDS VALIDATION / BLOCKED

Actual final URI types, metadata reader ownership, fingerprint invalidation and service/analysis busy seam await completed 002; exact integration branch awaits 03 proposal approval. Native runtime adaptation is BLOCKED by constitution. No API/ADR, playback/schema/scanner or source file was edited to resolve these during Phase 0.
