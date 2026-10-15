# Verification strategy

**VERIFIED IN SOURCE:** tests named below exist at baseline S `7203f18deadf1ca589c03f1b41572a157a8f4ce3`, per [source map](01-reference-source-map.md) S09–S22 and repository test inventory. **PROPOSED:** new tests and commands. **NOT RUN:** all application builds, host/device tests, lint, benchmarks, screenshot/security execution in Phase0; see NOTES.md. Never turn an unexecuted plan into PASS.

## Preserve existing regression tests

Keep assertions/expected values intact in sharedLogic `lyrics/LyricsMatchingTest`, `player/PlaybackQueueTest`, `player/ListeningTrackerTest`; Android host `lyrics/{FirstLyricTest,KugouLyricsProviderTest,LrclibCandidateTest,LyricsHtmlDecodeTest}`, `ui/navigation/{PlayerLyricsParserTest,LyricsBrowseModeTest,FullScreenPlayerSwipeTest,FullScreenPlayerSeekTest,FullScreenPlayerQueueStatusTest}`, `settings/ThemeModeTest`, `sync/{LyricsTrackDurationTest,AlbumArtworkKeyTest}`, `ui/screens/FavoritesArtworkTest`, `ArabicTranslationCompletenessTest`. Files live under normal test package roots. Device tests include `AppNavigationTest`, `ui/navigation/{MiniPlayerTest,FullScreenPlayerTest,RtlLayoutTest,QueueTransportAvailabilityTest}` and player/media3 engine/DSP/crossfade/registry suites. Existing failures documented in CLAUDE.md are baseline issues to identify, not permission to skip/weaken them.

U-001 first adds characterization against existing callbacks/state/save/back/navigation/DEFAULT before extraction. U-019 adds integration boundaries. Every high-risk Room/file-write/analysis wiring change gets tests first (U-026/U-041/U-100); later tasks may not modify those tests to make implementation pass. Format oracles come from external fixtures/specs/reference decoder, not code under test.

## Required matrices — PROPOSED

| Area | Cases / assertions | Planned owning tasks |
|---|---|---|
| Registry | valid/unknown schema, missing fields, duplicate keys+IDs, invalid SemVer/hash, no network, stale valid cache, malicious URLs/redirects | U-020/U-058 |
| Archive/trust | correct/incorrect hash, tampered bytes, signed approval invalid/unknown/revoked key, identity/version mismatch, traversal/symlink/collision/bomb/CRC/limits | U-021/U-024/U-029 |
| Runtime | unsupported JS/builtins, missing host APIs, sync-vs-Promise behavior, reflection/filesystem/DB/network denial, infinite loop/regex/BigInt, memory/response exhaustion, crash/kill/cancellation/reentrancy | U-022–025 |
| Lifecycle/update | install disabled, enable/disable, permission increases, disabled persistence, settings compatibility, interrupted stage/activation, rollback, late callback after uninstall | U-026–030/U-058 |
| Artist/artwork | correct entity vs homonym/album thumbnail, absent motion, priority change, manual override, refresh, stale/corrupt cache, offline/static fallback | U-031–034 |
| Motion | audio-containing clip remains silent, no focus/session/DSP interaction, visible/background/covered/reduced-motion/power saver/memory release, rapid navigation, decoder errors | U-035/U-052/U-054 |
| Built-in lyrics | preserve LRCLIB+KuGou, local priority, cancellation, HTTP failure, bounded bodies, auto OFF on fresh/upgrade, same missing-track negative cache/backoff | U-036/U-037 |
| Search selection | multiple providers/partial errors, duplicate content/multiple attribution, live/remix/cover/unknown duration, preview/select, track change, offline cached selection, no large-library network burst | U-038/U-039 |
| Timing | plain/line/word, invalid/out-of-order times, repeated timestamps/LRC offsets/bilingual lines, word downgrade explicit, tap seek/browse/RTL text | U-016/U-040 |
| Tag formats | MP3 USLT/SYLT, FLAC, Opus/Ogg, Vorbis, M4A/ALAC/AAC atoms; malformed/unsupported/encrypted/chained files refuse accurately | U-041/U-043–047/U-056 |
| Tag preservation | independent audio packet/essence hashes, all unrelated tags/raw blocks/artwork hashes, gapless/chapters/duration/seeks/large offsets, no transcode | U-041/U-043–047/U-056 |
| Storage fault/recovery | denial/read-only/revoked grant, moved URI, low/unknown space, active playback/analysis Busy, cancel/kill at every journal edge, partial copy/rename failure/restore verification, refresh pending, sidecar collision | U-041/U-042/U-048/U-049/U-054 |
| UI | Light/Dark/System, transparency/motion reduction, EN/AR RTL, western digits/LTR transport, font1/1.3/2, keyboard/insets/landscape, accessibility focus, all nav/mini/style/extension states | U-002–018/U-030/U-053/U-054 |
| Wikipedia | artist/album homonyms, disambiguation, language missing, false match rejected, attribution/license/revision cached, no network, no result | U-050/U-051 |
| Analysis decoders | Go/RFC PCM vectors, pre-skip/gain/mapping/end/odd channels/high rates/corrupt data; retry once logs provider+cause; playback renderer unchanged; loudness tolerances | U-100–107 |

Use synthetic/owned fixtures and network fakes for deterministic tests. Never run registry JS simply to inspect it. Future runtime execution uses approved exact hashes and controlled broker only after security/rights gate. Test HTML/log injection and credential redaction. Real service smoke tests require authorization and are not a substitute for fault tests.

## Commands for future implementation

Per task run the brief's narrowed checks; module edges are defined in `docs/REGRESSION.md`. App baseline/final commands:

```text
./gradlew :androidApp:assembleDevQa :androidApp:testDevQaUnitTest :sharedLogic:testAndroidHostTest --console=plain
./gradlew :androidApp:lintDevQa
./gradlew :metadata:test
./gradlew :codecs:test
```

Run `:metadata:test` plus app sync tests for writer APIs. `:codecs` public/output changes also require `:metadata:test`, app player.decoders and analysis/sync suites per regression ledger. sharedLogic changes require full app host suite. `:dsp` public/numeric changes propagate to codecs/analysis/app. Analysis M4b exact quick/full/benchmark commands stay in preserved task text, with downstream ledger checks. New runtime module commands cannot be assumed to exist; define its test task only when a module is approved.

Autonomous future briefs may require verify.sh/memory gate/Graph workflow; this Phase0 explicitly prohibits all such execution, including Gradle --stop and adb. Build variant is devQa; devDebug is disabled, font issue already resolved in NOTES.md.

## Milestone and device evidence

At each future milestone record command/exit code/test counts, baseline failures, NOT RUN reasons, tool/device/OS/build IDs and artifact/log paths. Host/Compose/screenshot/performance/security evidence comes before release. SK004-M12 executes U-053 then U-055 preparation, then U-054 as its last task including the final report update. SK004-A6 ends with U-107. Only milestone-end phone checks use `com.amwolfstein.sistrum.dev.qa`; never install/instrument/force-stop protected daily app IDs. Use verify.sh's allowed QA flow per that future brief and retain per-class instr/logcat paths in HANDOFF.

M4b U-107 benchmarks Kotlin vs platform per format in same run, 3 medians, ms/audio-second/worker, no run >10 min, records SC-012 and setting-free table choice; no manufactured performance claims. UI perf captures frame timings/memory/background battery on low-end and newer devices, establishes baseline before effects, and compares actual deltas. Real listening/Bluetooth/headset checks are owner manual items, never automatic PASS.

## UNKNOWN / BLOCKED

Current build/test status was not measured because Phase0 forbids builds/device work. NOTES.md's earlier assembleDevQa success is owner context, not our result. Runtime/security/performance and writer atomicity remain UNKNOWN; shipping those features is BLOCKED until corresponding gates pass.
