# Risks and planning decisions

Evidence: [source map](01-reference-source-map.md), [runtime](extensions/02-runtime-compatibility-audit.md), [rights](extensions/10-compliance-and-licensing.md), owner NOTES.md/D-31/D-32. These are **PROPOSED planning decisions**, not changes to shipped architecture or migration ADRs.

| ID | Decision / rationale | Revisit or gate |
|---|---|---|
| P01 | Fresh implementation branch from accepted 002/003 base; preserve stale branch and current worktree | Owner/orchestrator chooses exact stable base after migration |
| P02 | DEFAULT unchanged/selected by default; LARGE proposes V8-inspired floating cover, IMMERSIVE V7; stable DataStore strings | E03 verifies only upstream V7/V8 labels; visual review LARGE mapping; no extra fullscreen toggle |
| P03 | Existing Haze2 wrapper first, semantic effects/opaque fallback | Lens dependency only after separate compatibility/license/performance approval |
| P04 | Optional isolated execution with broker, no native runtime adoption by default | Full runtime decision waits POC; raw Rust/QuickJS BLOCKED under constitution; no ADR silently changed |
| P05 | Default registry bundled once; checks ON/24h, code updates OFF, explicit install+enable | Authenticity approval catalog/key ownership must be decided before activation |
| P06 | New enrichment DB for package/cache provenance; preserve selected lyrics and manual artwork stores | Cross-store content-hash/outbox reconciliation and explicit migrations tested; no duplicated library entities |
| P07 | Local-first lyrics, automatic online search OFF, retain LRCLIB+KuGou manual search and settings | Explicit intended change from current playing-track auto fetch; preserve saved selections |
| P08 | Trusted Kotlin `:metadata` structural writer + URI storage coordinator, no extension write grants | Final D-31 capabilities and per-format preservation/recovery tests first |
| P09 | Provider rights separate Display/Cache/Embed/Sidecar; Unknown denies new persistent export | Current authorized terms/attribution and source rights required; user click is insufficient for third-party rights |
| P10 | UI-owned independent video player, audio track disabled/no focus/session, static fallback | .qa lifecycle/focus/decoder/battery tests; no MusicPlayerFactory change |
| P11 | Independent analysis-decoder track after completed002; unchanged T033a–g under U wrappers | No UI dependency or 003 dependency reversal; choice only after same-run phone numbers |
| P12 | Wikipedia optional and independent, entity-aware attributed text with missing state | Artist bio new; don't restore Flick/extension About; R01 heuristics are inadequate as identity proof |

## Risk register

| Risk | Severity / state | Mitigation / owner |
|---|---|---|
| Shared worktree and dirty migration files | High, VERIFIED IN SOURCE | Docs-only boundaries, preserve all changes; architecture auditor checks final status |
| Final folder URI/read-contract drift | High, UNKNOWN | Re-audit completed002; read grant is not write grant; storage owner |
| New native runtime contradicts constitution | Critical, BLOCKED | No dependency integration absent owner ADR; runtime owner evaluates JVM/system/C |
| Interpreter/broker compromise or resource exhaustion | Critical, UNKNOWN | OS isolation, capability broker, quotas/watchdog, adversarial tests; security owner |
| Registry publisher compromise/unsigned update | Critical, BLOCKED activation | Separate approval trust, exact hashes, no auto code update, rollback/revocation policy |
| Apple token scraping and content persistence | High, BLOCKED shipping | Authorized path and rights review, no copied credentials; compliance owner |
| YouTube/Deezer endpoint/portrait licensing | High, BLOCKED shipping | Approved access and matching/attribution/retention policy |
| Wrong recording/artist or hallucinated About | High, UNKNOWN live | Entity IDs/edition signals, preview/manual resolution, Missing/NeedsSelection |
| File corruption/provider non-atomic replacement | Critical, BLOCKED writes pending tests | Full backup+journal+verify+recovery; refuse incapable providers, never fake atomicity |
| New Room migration destroys user data | High, PROPOSED prevention | Tests-first explicit migration, no destructive fallback, preserve library DB |
| Word timing lost through legacy parser/cache | Medium, VERIFIED IN SOURCE current stripping | Additive timing model bound to content hash; line/word separate support |
| Motion takes music focus or burns background battery | High, UNKNOWN | Independent video-only player, visibility/power policy, QA regression/perf |
| Glass/animation makes RTL/accessibility slow/unreadable | Medium, UNKNOWN | Token/contrast/fallback/font/RTL/performance matrices; no effect-only affordances |
| Existing swallowed cancellation/null errors/unbounded bodies | Medium, VERIFIED IN SOURCE S09/S11/S16 | Scoped U-037/provider adapter error fixes and regression tests; no Phase0 code edits |

## Scope held for later

Batch embedding, custom registries, Spatial Audio and 8D are deferred. Streaming/download features, Flick biography/About and extension About implementations are excluded. Optional Wikipedia is new independent source-attributed functionality. No blocker here was resolved by weakening tests or assuming provider rights. Phase 0 readiness means reviewable planning complete, not that implementation blockers disappeared.
