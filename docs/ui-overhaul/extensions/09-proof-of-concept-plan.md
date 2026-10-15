# Runtime proof-of-concept plan and go/no-go

**VERIFIED IN SOURCE:** runtime/host shape is M02/M03/M05/M06 at M `5a8952659b247fd95f1914a56db60825657fe26d`; reviewed package members/hashes at X `0290b263802d3418e45583612574b749289bf369` ([source map](../01-reference-source-map.md)). **PROPOSED:** the following future experiment; nothing here was executed in Phase 0.

## Small ordered experiments

1. U-020/021 parse pinned registry and archives without evaluating JS. Produce feature/permission/method profile and reject malformed/mutated packages. Establish exact package approval/trust inputs with synthetic signatures; no real trust key is manufactured by the POC.
2. U-022 implement/test broker policy independently with mock public network fixtures, DNS/redirect/private-IP attacks and strict response quotas. No library Context/URI/DB access to candidate engine.
3. U-023 compare a pinned/license-audited pure JVM engine in Android isolated service and AndroidX system sandbox feasibility. Raw QuickJS/Rust is design-only until an owner ADR permits native integration. Use synthetic ES/builtin/registration scripts, recursive exceptions/regex/heap/loop/cancel/death fixtures. Ensure independent watchdog kills hung work without taking music/UI with it.
4. Probe exact sync `http.get`, global sync fetch text/json, storage types/defaults, sleep/cancel/log/hash methods and initialize/cleanup semantics. No Promise-as-response substitution. If system sandbox lacks a safe synchronous host callback, record incompatibility immediately; do not invent a successful shim or run arbitrary WebView code.
5. U-024 only after isolation/trust/legal review permits, evaluate explicitly approved exact Apple and YouTube Music package versions against recorded/synthetic broker responses, never real subscription tokens. Compare registration, method arguments/results, expected network sequence, misses/errors and timing normalization. Deezer required-feature mismatch is an expected Incompatible result; do not silently waive it. Use owned/synthetic TTML and artwork fixtures; no proprietary assets in git.
6. U-025 record decision with exact engine/package hashes and Android/WebView/ABI versions, input/output fixture hashes, security failures, policy/license review, cold/warm startup, p50/p95 latency, APK size delta per ABI, PSS/RSS/heap and idle/battery metrics. 3 runs minimum, no extension scripts from arbitrary latest registry.

## Go/no-go evidence

| Gate | Proposed acceptance |
|---|---|
| No-native | No app-bundled JNI/NDK/Go/Rust dependency added unless owner ADR explicitly changes rule; otherwise reject A/B1 |
| Contract | Every method required by selected exact profile conforms on fixtures, lifecycle deterministic, unsupported requirements refuse before activation |
| Isolation | No files/DB/reflection/direct sockets/Android access; worker crash/kill leaves host/music usable; no secret in results/logs |
| Resource | Hard 20 s call deadline, cancellable host calls, enforced heap/output/body bounds and OS-process recovery; provisional budgets in threat model |
| Performance | Proposed cold usable runtime p95 ≤500 ms, warm invocation overhead ≤50 ms excluding network, worker RSS target ≤96 MiB, APK delta target ≤5 MiB; measure low-end and modern API31+ hardware, revise only with recorded decision |
| Network | Rebinding/private/redirect/HTTPS/credential-leak tests all fail closed; HLS download broker uses same policy |
| Update | Candidate validation/activation crash recovery and previous-version rollback preserve enable/settings intent |
| Compliance | Reviewed intended channel, executable-code policy, runtime dependency license, package license/authenticity and provider access rights for exact scope |

Budgets are proposals, not source facts or completed benchmarks. Prefer one extension worker at a time initially; if the system sandbox shares a process across isolates, do not claim per-provider crash isolation. Report global sandbox death and recover outstanding providers explicitly.

## Outcomes

GO means select a named/pinned backend and narrow host profile through a reviewed architecture decision before manager execution tasks. CONDITIONAL means registry/listing/cache/UI can proceed but activation waits. NO-GO means trusted Kotlin provider-specific adapters only for rights-approved services; state clearly that arbitrary .sflx execution remains unsupported, preserving unresolved owner scope. NEVER bypass isolation/policy or reintroduce native runtime just to make a fixture pass.

## UNKNOWN / NEEDS VALIDATION / BLOCKED

No candidate dependency version/license/API or benchmark has been established beyond upstream rquickjs source. Whole-registry compatibility is BLOCKED; exact safe subset feasibility is UNKNOWN. POC is itself future implementation requiring a new brief; Phase 0 stops at documentation.
