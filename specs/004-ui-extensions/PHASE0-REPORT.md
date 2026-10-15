# 004 Phase 0 — architecture audit and planning report

Date: 2026-10-15. Planning only. Owner NOTES.md overrides master prompt; no application implementation, build/dependency changes, schema/scanner/playback changes, music-file writes, commit/stage/push or .codex-commit preparation occurred. No subagents or brief files were created. Review passes were sequential. Reference code and registry packages were treated as untrusted and never executed.

Evidence labels used throughout: **VERIFIED IN SOURCE** = inspected text at cited path/symbol/SHA; **PROPOSED** = future design/task; **UNKNOWN / NEEDS VALIDATION** = unmeasured/unresolved; **BLOCKED** = an implementation/shipping gate, not unfinished Phase0 documentation. Common exact citations are in [source map](../../docs/ui-overhaul/01-reference-source-map.md).

## 1. Current Sistrum architecture summary

VERIFIED IN SOURCE: baseline `7203f18deadf1ca589c03f1b41572a157a8f4ce3`, branch `feature/media3-migration`, shared migration worktree `/home/amwolfstein/Documents/GitHub/Sistrum-UI`. Existing dirty application/decoder/spec/tooling/graph changes were present before this audit and preserved. Owner resume reported no other active run; prior dirty files remain unrelated to this documentation. No earlier UI Phase0 docs existed.

MainActivity wires ViewModels/services/preferences into App, destination composables and callback-driven player controls. Navigation has saved state/back handlers, circle collapse and paired mini-player repositioning. Haze2/theme/DataStore and manual artwork already exist. Modules are androidApp/sharedLogic/codecs/metadata/analysis/dsp; Room baseline14. PlaybackController flows/commands form the UI boundary. Media3 default and planned002/003 changes are distinguished from baseline source in [current state](../../docs/ui-overhaul/00-current-state.md), S01–S08/S18–S22. D-31/D-32 are working-copy owner decisions, not falsely cited as baseline committed implementation.

## 2. Existing Find Lyrics providers and locations

VERIFIED IN SOURCE: `androidApp/src/main/kotlin/com/amwolfstein/sistrum/lyrics/AndroidLyricsService.kt::{LrclibLyricsProvider,KugouLyricsProvider,LyricsProvider,AndroidLyricsService,firstLyric}` at baseline, S09–S13. LRCLIB get/search and KuGou search/download are the two online providers. Embedded/sidecar readers precede them. Manual inputs/results/preview live in `ui/components/FindLyricsContent.kt::{FindLyricsSearchState,FindLyricsContent,FindLyricsPreviewContent}`, not an invented ViewModel. MainActivity selection saves `ProviderLyricEntity` through `sync/SyncDatabase.kt::AndroidLibrarySyncStore.saveProviderLyrics`. Cache is not embedding; no writer found. Line sync exists; current parser strips word markers. [Lyrics audit](../../docs/ui-overhaul/extensions/06-lyrics-provider-integration.md).

## 3. Verified extension capabilities

VERIFIED IN SOURCE: Apple getAlbum/getArtist exposes motion/static header fields and fetchLyrics parses TTML including enhanced word timestamps. YTM getArtist exposes artist image/header; Deezer getArtist maps artist picture fields. Availability is not universal, correct matching is required, and no live calls ran. Only Apple is a declared/registered lyrics provider in the 12 packages; Qobuz/Tidal helpers are not compatible provider registration. Required host features make multiple packages incompatible initially. [Capability matrix](../../docs/ui-overhaul/extensions/04-provider-capability-matrix.md), X01–X05 at `0290b263802d3418e45583612574b749289bf369`.

## 4. Registry and package format

VERIFIED IN SOURCE: default registry fetched, schema 1 / 12 entries; SHA256 `c7f7428d620ec23e83547b24974283e15ed68b35ce9007349a765e22ed2eef73`. All 12 archives downloaded and matched published SHA256 values; also byte-identical to pinned X packages. ZIP .sflx packages contain root manifest.json/index.js and optional icons; global registerExtension entry contract. Registry snake_case/min_app_version differs from manifest camelCase/minAppVersion and Sistrum host compatibility. No signature/license file in sampled archives; same-registry hash is not authenticity. zarzet main API lookup timed out; artifact hashes identify snapshot independently. [Full hash inventory/schema](../../docs/ui-overhaul/extensions/01-registry-and-package-format.md).

## 5. Runtime architecture comparison

VERIFIED IN SOURCE: pinned Mobile runtime is Rust/rquickjs0.14.0, bounded dedicated worker with synchronous HTTP/fetch/storage prelude. PROPOSED: compare original runtime adaptation, embedded QuickJS, pure JVM isolated engine, system JavaScriptEngine and constrained Kotlin provider adapters. Rust/QuickJS violates no-native constitution absent owner ADR. System sandbox does not establish sync host compatibility. Prefer no-native POC; choose production only after evidence. C is truthful fallback, not arbitrary-package parity. Size/startup/RSS/Android compatibility UNKNOWN. [Comparison](../../docs/ui-overhaul/extensions/02-runtime-compatibility-audit.md), [POC](../../docs/ui-overhaul/extensions/09-proof-of-concept-plan.md).

## 6. Security and Google Play compatibility

PROPOSED: OS isolation + narrow broker, HTTPS/domain/redirect/actual-address checks, private-IP/rebinding defense, archive limits, independent CPU/heap/deadline/crash control, cancellation and capability/version approval. No file/library/DB/Android APIs for scripts; no automatic code updates. Native/thread interpreter is not automatically a sandbox. Current official Play policy distinguishes interpreted code but still forbids policy violations through it; no approval is inferred. Intended channel, authenticity governance and enforceable runtime isolation remain gates. [Threat model](../../docs/ui-overhaul/extensions/03-security-threat-model.md), [compliance](../../docs/ui-overhaul/extensions/10-compliance-and-licensing.md).

## 7. Artwork and motion strategy

PROPOSED: identity-aware repositories, manual override first, user-editable YTM→Deezer→local artist order, scoped caches and explicit missing/ambiguous results. Motion separate from music: video-only UI player, disabled audio track, volume 0/no focus/session, looping only visible/foreground, static fallback, reduced-motion/power/data/lifecycle policies. HLS all subrequests validated. No PlaybackService/decoder/DSP integration. Apple authorization/offline retention and portrait-provider rights BLOCKED until reviewed. [Design](../../docs/ui-overhaul/extensions/05-artwork-and-motion-design.md).

## 8. Lyrics search and embedding strategy

PROPOSED: retain both built-ins/local readers; add provider priority, typed errors, consolidated dedup/version-aware results, preview/manual selection, optional online search, auto missing OFF, negative caching/retry bounds and offline selected content. Additive word model preserves legacy line/bilingual parsing. Selected cache separate from Embed/Sidecar.

Trusted Kotlin writer in :metadata uses final D-31 URI grants; read grants do not authorize writes. Single-track supported-format gates cover USLT/SYLT, FLAC, Opus, Vorbis and MP4/ALAC; unsupported formats accurately refuse/offer sidecar. Preserve raw unknown metadata/art/audio essence without transcode. Full backup/staged verification/journal/recovery, provider-specific publishing, permission/space/cancel/kill checks and library refresh required. Generic SAF atomicity is UNKNOWN, never promised. Provider rights gate permanent writes. [Search](../../docs/ui-overhaul/extensions/06-lyrics-provider-integration.md), [embedding](../../docs/ui-overhaul/extensions/07-lyrics-embedding-design.md).

## 9. Updated UI implementation plan

PROPOSED: semantic tokens/glass/accessibility → individual Home/library/album/artist/playlist/search screens → navigation/mini/three styles/lyrics → integration/release gates. Exhale primary except SpotiFLAC Home; Flick Library Settings only; BitChord hide-volume default false. DEFAULT unchanged and default-selected; IMMERSIVE source V7, LARGE proposed V8 floating-cover mapping because upstream has no LARGE name. Persist stable strings, no fullscreen-cover toggle. Preserve English+Arabic/RTL/western digits/LTR transport, Light/Dark/System and Reduce Transparency. [Visual specification](../../docs/ui-overhaul/02-visual-specification.md).

## 10. Atomic tasks and dependencies

PROPOSED: 65 U-xxx tasks with every §14 field and future Spec Kit milestone. Dependency-aware UI/runtime/provider/lyrics/storage/Wikipedia/settings/integration roadmap plus independent **analysis decoders** track. Original T033a–g task descriptions copied byte-for-byte into U-101–107; completed002 required, no UI dependency; tests-first U-100 precedes high-risk wiring. No tasks marked implemented. Final UI verification order is U-053 → U-055 preparation → U-054, so the phone check and final report are the milestone’s last task. [Task list](../../docs/ui-overhaul/04-atomic-tasks.md), [roadmap](../../docs/ui-overhaul/03-master-plan.md), [tracker](../../docs/ui-overhaul/08-progress-tracker.md).

## 11. Media3/WaxFlow integration boundaries

PROPOSED read-only CurrentTrackSnapshot/EntityIdentity/PlayerUiState and artwork/lyrics/search/select/write/motion contracts sit outside player internals. Preserve PlaybackController API/flows, queue/listening tracking, normalization/crossfade/DSP/service, decoder order and analysis documents. Final002 URI/metadata contracts re-audited before writing. M4b changes analysis renderer list/table only; assert playback factory unchanged. Fresh future implementation branch from accepted002/003 baseline proposed; no branch created. [Integration contract](../../docs/ui-overhaul/09-migration-integration-contract.md).

## 12. Build/test results

**not run in Phase 0, see NOTES.md**.

No Gradle (including --stop), adb, verify.sh, graph_refresh.py or Graphify update/extract ran. No fonttools repair: NOTES.md says resolved; devQa replaces disabled devDebug. Historical owner assemble success is not this run's test result. Application/test/lint/benchmark/runtime/security/device execution = NOT RUN due explicit planning/concurrency prohibition. Future commands/matrices in [test plan](../../docs/ui-overhaul/05-test-plan.md).

Documentation-only checks: 21 required docs exist;22 total with this report; 65 unique task IDs, all §14 fields/future milestone present, internal links resolve, dependency DAG has no cycles/missing IDs, seven original moved lines exact. These are documentation checks, not an application GATE: PASS.

Commands/read-only operations executed and results:

- cat/sed/rg targeted required instructions, local and reference source, licenses and existing test inventory; successful reads established cited findings. Initial guessed paths AndroidLibrarySyncStore.kt/MetadataReader.kt/AppShell.kt/Go backend were absent; rg located actual symbols and docs use actual paths. Broad outputs were narrowed when truncated.
- git status/branch --show-current/rev-parse/worktree list/diff --name-only and git show HEAD:graphify-out/graph.json: successful read-only inspection; committed graph copied to /tmp.
- graphify --help; query navigation/artwork/lyrics/Room/glass; explain AndroidLyricsService against /tmp committed graph: successful; broad query truncation disclosed and followed by direct reads. No graph writes/refresh.
- Six git clones --filter=blob:none --no-checkout and hooks-disabled detached checkouts **only under /tmp/sistrum-refs**: successful pinned revisions, Rhythm main SHA recorded. These explicit reference-checkout operations preceded resumed sequential-only instruction; no further clones were needed. No repository script/build/JS/dependency install.
- Python standard-library HTTPS fetch/hash/ZIP bounded member reads:12/12 download/hash matches,12/12 equal pinned archive bytes; GitHub spotiflacapp main resolved to pinned X, zarzet main lookup timed out. Registry/package inventory retained in /tmp; no arbitrary extraction/execution.
- Official web sources reviewed for Android storage/isolation/JavaScriptEngine, Play, QuickJS/Rhino, Apple, YouTube, Wikimedia, Deezer, ID3/Vorbis/Opus. Deezer PDF text unreadable; LRCLIB docs text unavailable; GNU FAQ fetch timed out. Limitations recorded; no rights approval invented.
- Python documentation generation/validation under allowed docs/spec paths and /tmp: successful final checks; no source files written. One scratch generator task-token parsing error was fixed before validation; original task text verified exact afterward.

## 13. Licensing and unresolved blockers

VERIFIED IN SOURCE root licenses: Sistrum/Exhale/BitChord/Rhythm GPLv3 (file-specific later-version headers noted), SpotiFLAC-Mobile/Flick MIT, SpotiFLAC-Extension Apache2+NOTICE. QuickJS/rquickjs MIT, Rhino upstream MPL2; exact new dependency/transitive artifact audit still needed. GPL adaptation requires attribution/notices/modification and corresponding-source obligations; protected assets/credentials not copied.

BLOCKED implementation/shipping parts: native runtime without ADR; general execution without isolation/authenticity/channel policy review; Apple scraped-token access/content caching/embedding; undocumented YTM access and Deezer current terms; permanent lyric export without rights; original-file writing before completed002 grants and preservation/recovery tests. UNKNOWN measurements remain explicit. These do not prevent completing this documentation phase. [Licenses](../../docs/ui-overhaul/06-licenses.md), [risk decisions](../../docs/ui-overhaul/07-risks-and-decisions.md).

## 14. Recommended first implementation task

**U-001 — characterize existing UI/local feature contracts [TESTS-FIRST]**, after an accepted integration-base SHA and a separate implementation brief/worktree. Follow with U-019, tokens/glass. Runtime POC is an independent gated track; do not start with production remote execution or real-file embedding. This report does not start Phase1.

## 15. Changed documentation files

New files created by this audit only (owner NOTES/master prompt preserved):

- `docs/ui-overhaul/00-current-state.md`
- `docs/ui-overhaul/01-reference-source-map.md`
- `docs/ui-overhaul/02-visual-specification.md`
- `docs/ui-overhaul/03-master-plan.md`
- `docs/ui-overhaul/04-atomic-tasks.md`
- `docs/ui-overhaul/05-test-plan.md`
- `docs/ui-overhaul/06-licenses.md`
- `docs/ui-overhaul/07-risks-and-decisions.md`
- `docs/ui-overhaul/08-progress-tracker.md`
- `docs/ui-overhaul/09-migration-integration-contract.md`
- `docs/ui-overhaul/extensions/00-extension-architecture.md`
- `docs/ui-overhaul/extensions/01-registry-and-package-format.md`
- `docs/ui-overhaul/extensions/02-runtime-compatibility-audit.md`
- `docs/ui-overhaul/extensions/03-security-threat-model.md`
- `docs/ui-overhaul/extensions/04-provider-capability-matrix.md`
- `docs/ui-overhaul/extensions/05-artwork-and-motion-design.md`
- `docs/ui-overhaul/extensions/06-lyrics-provider-integration.md`
- `docs/ui-overhaul/extensions/07-lyrics-embedding-design.md`
- `docs/ui-overhaul/extensions/08-update-and-rollback.md`
- `docs/ui-overhaul/extensions/09-proof-of-concept-plan.md`
- `docs/ui-overhaul/extensions/10-compliance-and-licensing.md`
- `specs/004-ui-extensions/PHASE0-REPORT.md`

All repository writes stayed under docs/ui-overhaul/** and specs/004-ui-extensions/**; scratch under /tmp/sistrum-refs/ and /tmp. Existing dirty files in §16 are not authored changes from this audit.

## 16. Git status only

Read-only `git status --short --untracked-files=all` snapshot after documentation creation:

```text
 M .claude/agents/pr-test-analyzer.md
 M .claude/skills/verify-task/scripts/verify.sh
 M CLAUDE.md
 M androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/player/KotlinFormatsPlaybackTest.kt
 M androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/ExtractorRandomAccessSource.kt
 M androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinDecodingExtractor.kt
 M androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/media3/Media3PlayerFactory.kt
 M androidApp/src/test/kotlin/com/amwolfstein/sistrum/analysis/OfflineDecoderKotlinFormatsTest.kt
 M androidApp/src/test/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/ExtractorRandomAccessSourceTest.kt
 M androidApp/src/test/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinDecodingExtractorTest.kt
 M codecs/src/main/kotlin/com/amwolfstein/sistrum/codecs/codec/wma/Decode.kt
 M codecs/src/main/kotlin/com/amwolfstein/sistrum/codecs/codec/wmalossless/Decode.kt
 M codecs/src/main/kotlin/com/amwolfstein/sistrum/codecs/codec/wmapro/Decode.kt
 M codecs/src/main/kotlin/com/amwolfstein/sistrum/codecs/codec/wmavoice/Decode.kt
 M codecs/src/main/kotlin/com/amwolfstein/sistrum/codecs/container/asf/Demux.kt
 M codecs/src/main/kotlin/com/amwolfstein/sistrum/codecs/container/asf/Wma.kt
 M codecs/src/main/kotlin/com/amwolfstein/sistrum/codecs/container/asf/WmaLossless.kt
 M codecs/src/main/kotlin/com/amwolfstein/sistrum/codecs/container/asf/WmaPro.kt
 M codecs/src/main/kotlin/com/amwolfstein/sistrum/codecs/container/asf/WmaVoice.kt
 M codecs/src/test/kotlin/com/amwolfstein/sistrum/codecs/WmaVoiceOracleTest.kt
 M graphify-out/.graphify_analysis.json
 M graphify-out/graph.json
 M scripts/test-corpus/README.md
 M specs/002-kotlin-decoders/DECISIONS.md
 M specs/002-kotlin-decoders/HANDOFF.md
 M specs/002-kotlin-decoders/plan.md
 M specs/002-kotlin-decoders/spec.md
 M specs/002-kotlin-decoders/tasks.md
?? codecs/src/test/kotlin/com/amwolfstein/sistrum/codecs/WmaPacketSeekTest.kt
?? docs/ui-overhaul/00-current-state.md
?? docs/ui-overhaul/01-reference-source-map.md
?? docs/ui-overhaul/02-visual-specification.md
?? docs/ui-overhaul/03-master-plan.md
?? docs/ui-overhaul/04-atomic-tasks.md
?? docs/ui-overhaul/05-test-plan.md
?? docs/ui-overhaul/06-licenses.md
?? docs/ui-overhaul/07-risks-and-decisions.md
?? docs/ui-overhaul/08-progress-tracker.md
?? docs/ui-overhaul/09-migration-integration-contract.md
?? docs/ui-overhaul/extensions/00-extension-architecture.md
?? docs/ui-overhaul/extensions/01-registry-and-package-format.md
?? docs/ui-overhaul/extensions/02-runtime-compatibility-audit.md
?? docs/ui-overhaul/extensions/03-security-threat-model.md
?? docs/ui-overhaul/extensions/04-provider-capability-matrix.md
?? docs/ui-overhaul/extensions/05-artwork-and-motion-design.md
?? docs/ui-overhaul/extensions/06-lyrics-provider-integration.md
?? docs/ui-overhaul/extensions/07-lyrics-embedding-design.md
?? docs/ui-overhaul/extensions/08-update-and-rollback.md
?? docs/ui-overhaul/extensions/09-proof-of-concept-plan.md
?? docs/ui-overhaul/extensions/10-compliance-and-licensing.md
?? specs/003-native-removal/DECISIONS.md
?? specs/003-native-removal/checklists/requirements.md
?? specs/003-native-removal/plan.md
?? specs/003-native-removal/quickstart.md
?? specs/003-native-removal/research.md
?? specs/003-native-removal/spec.md
?? specs/003-native-removal/tasks.md
?? specs/004-ui-extensions/NOTES.md
?? specs/004-ui-extensions/OWNER-PROMPT-2026-10-15.md
?? specs/004-ui-extensions/PHASE0-REPORT.md
```

No stage/commit/push, git write in the project, .codex-commit/ or implementation brief created. No automatic implementation follows this report.
