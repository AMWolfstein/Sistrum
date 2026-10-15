# Extension threat model

**VERIFIED IN SOURCE:** upstream worker/limits (M02), extraction checks (M04), network policies (M07), packages/permissions (X01–X05) in the [source map](../01-reference-source-map.md). These controls are not implemented in Sistrum. All controls below are **PROPOSED**.

## Trust and assets

Adversaries: compromised registry/package publisher/CDN, malicious extension author, hostile DNS/redirect server, malicious output/archive, external app invoking components, or a legitimate provider returning unbounded/corrupt data. Assets: library files/URI grants, selected lyrics/manual artwork, playback stability, database, credentials, private networks, user metadata and battery. Trusted code is Sistrum verifier/broker/repositories/writer; untrusted includes scripts, manifests, remote metadata, URLs and persisted extension KV. A hash from the same compromised registry does not establish authenticity.

## Controls and verification obligations

| Threat | Boundary/control | Required adversarial tests |
|---|---|---|
| Registry/package substitution, rollback attack | HTTPS plus signed approval catalog/pinned reviewed digest; bind origin/id/version/hash/permissions/host profile; manual rollback only to previously trusted version | Same version new hash, forged/missing signature, unknown key, lower version, wrong origin |
| ZIP traversal/bomb/symlink | bounded archive/member count/actual expansion, normalized paths, reject aliases/duplicates/links; create-new private staging | Unix/Windows traversal, Unicode/case collisions, symlinks, corrupt size/CRC, nested/encrypted files, expansion limits |
| Interpreter escape / reflection | OS isolated process (not simply `:process`), no own permissions, no app Context/Java class access, no URIs/FDs/library paths; narrow authenticated broker session | Java reflection/import/prototype attacks, filesystem/DB reads, direct socket/system/Android calls |
| SSRF and DNS rebinding | trusted broker only, HTTPS443 initially, no URL userinfo; canonical IDNA host allowlist with label boundaries; validate each redirect and every resolved/connected IP; no proxy/env overrides | localhost, RFC1918/link-local/ULA/multicast/reserved, IPv4-mapped IPv6, numeric/encoded IPs, rebinding, alternate DNS answers, redirect to private/non-HTTPS |
| Cross-host secret leak | per-provider host/method credential capability; strip auth/cookies on cross-origin redirect; partition cookie jar; never inherit app credentials | signed URL/cookie logs, cross-domain redirects, disabled/uninstalled stale token |
| CPU/heap/output exhaustion | one active extension operation initially; bounded queue/input/result/JSON depth; independent wall watchdog, cancellable host timeouts; retire/terminate worker on overrun | infinite loop, catastrophic regex/BigInt, recursive getter/string conversion, heap churn, large Base64/JSON/bodies, host sleep |
| Crash or deadlock affects music | isolated service death cleanup; broker work off main thread, bounded Binder payload, no callback while holding manager locks | service kill/VM OOM during HTTP, cancellation at registration, reentrant calls, full queue |
| Data poisoning / wrong recording | typed schema and explicit entity kind, provider ID + match confidence, no HTML rendering/untrusted file names | album image posing as artist portrait, live/remix/cover, malicious timestamp, script/HTML text |
| Silent permission escalation | consent binds requested methods/domains/storage scope to exact version; update diff blocks activation pending user approval | new domain, broad wildcard, file=true, new required runtime feature; disabled stays disabled |
| Stale operations / confused deputy | broker-issued opaque session tokens bound to installed hash/capabilities/deadline; authenticated caller/service binding; generation invalidation | disable/uninstall/update while call active, late callbacks, ID spoofing, arbitrary external app bind |
| Secret/log leakage | bounded structured provider+operation+cause log; redact tokens/cookies/signed queries/path/title when sensitive | thrown object custom toString, embedded tokens in response/error; verify redaction without losing provider cause |
| Tag corruption | runtime has no write capability; trusted writer only with explicit per-operation user action and recovery | extension attempts write/FD request; rejected before storage layer |

Proposed operation budgets: JS heap 32 MiB, total worker RSS budget 96 MiB measured, 5 s CPU-oriented watchdog and 20 s overall metadata call, network connect 5 s/read 10 s within overall deadline, 2 MiB text body/512 KiB structured result, JSON depth32/10,000 nodes, queue4. Streaming media has separate bounded download quota and rights gate; it is not returned as JS bytes. These numbers require POC adjustment with evidence, never silent limit relaxation. If the chosen engine cannot enforce heap/time independently, full remote execution stays disabled.

Android documents that [isolated services have no permissions of their own](https://developer.android.com/guide/topics/manifest/service-element). A separate process with the app's UID is not the same control. Binding/caller handling and process termination must be validated on supported devices. An in-process interpreter cannot be described as a security sandbox. A compromised isolated process can abuse any authority the broker mistakenly grants; OS isolation does not replace capability checks.

## Distribution gate

The [current Google Play Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en) distinguishes interpreted runtime code from prohibited downloaded executable binaries, while still holding apps responsible for policy violations through that code. This is not blanket approval of registry execution. No downloaded dex/JAR/.so, self-update bypass, DRM circumvention, stream downloading or unauthorized API access may be added. Intended distribution channel and content authorization must be reviewed for exact runtime/provider scope.

**BLOCKED:** shipping general registry execution until enforceable isolation, authenticity governance, broker tests and distribution review pass. **UNKNOWN / NEEDS VALIDATION:** engine sandbox escape resistance, watchdog guarantees, OEM Binder/memory behavior and Play acceptance. If these cannot be demonstrated, use approved constrained Kotlin adapters and document unsupported packages. Phase 0 ran no attack payloads or extension JavaScript.
