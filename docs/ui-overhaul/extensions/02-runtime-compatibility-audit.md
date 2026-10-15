# Runtime compatibility audit

**VERIFIED IN SOURCE:** [source map](../01-reference-source-map.md) M02/M03/M05/M06 and X01–X05; M `5a8952659b247fd95f1914a56db60825657fe26d`, X `0290b263802d3418e45583612574b749289bf369`.

The pinned mobile runtime is Rust with `rquickjs 0.14.0`, not an assumed Flutter-only, Goja or generic WebView engine. It preserves legacy Go semantics in prelude conversion/host functions. `ExtensionRuntime` owns one dedicated worker and serialized calls, bounded queue 8, heap/stack/interrupt limits; a retired interrupted VM is not reused. It also has native host services for files/downloads/session/FFmpeg that Sistrum must not transplant wholesale. A worker thread is not an OS security boundary.

## Observed contract

| Surface | Upstream behavior | Sistrum compatibility decision — PROPOSED |
|---|---|---|
| registerExtension | Saves object to global `extension`; manager invokes initialize/cleanup and provider functions | Implement only a bounded typed registry in a proven isolated backend; inspect callable methods, don't execute while browsing |
| HTTP | `http.get/post/request` and global `fetch` are synchronous dispatch; response statusCode/body/error, fetch text/json helpers are immediate | Browser Promise-based fetch is incompatible. A synchronous broker bridge is a POC hard gate, not solved by renaming APIs |
| Storage/settings | namespaced storage.get/set/remove, credentials and initialize settings | Only broker KV quotas, capability-scoped secrets; no filesystem/Android object reflection |
| Utilities | log, randomUserAgent, sleep, cancellation, hashes/encodings; some packages use signed sessions | Implement exact approved minimum; bounded cancellable sleep; unsupported functions return typed refusal/throw, not successful placeholders |
| Provider methods | getArtist/getAlbum/searchTracks/customSearch; lyrics `fetchLyrics(trackName,artistName,albumName,durationSec)` | Narrow adapter per package/method; validate shape, timestamps, match identity and asset domain |
| Dangerous services | file.download/delete/transform, downloadSegments, native containers/FFmpeg, auth/session protocols | Deny; do not add downloads/streaming/audio conversion or raw library access |
| Versions | minAppVersion and requiredRuntimeFeatures, no general Sistrum host API negotiation | Exact package-hash profile + explicit required-feature checks; never compare Sistrum version directly to upstream minAppVersion |

Static package inspection verified var/let/const, functions/closures, arrays/objects, regex, JSON, Date, Map/Set, string helpers and synchronous host calls. Apple uses TTML string/regex parsing, bounded caches, synchronous HTTP/storage/sleep/cancellation. YouTube Music uses synchronous global fetch and response text/json; Deezer requires signedSession/file-transform features. Syntax presence is not runtime conformance; no JS ran in Phase 0. Full host-property inventory and dynamic/prototype access require POC instrumentation, not regex alone.

## Comparison — PROPOSED assessment, not benchmarks

| Option | Compatibility / Android / maintenance | Security and policy | Size/startup/memory | Decision |
|---|---|---|---|---|
| A: Adapt original Rust/rquickjs runtime | Best source contract match, but extensive Rust/JNI/service graph, compatibility prelude and native services; MIT upstream plus transitive audit | Need isolated process/broker; existing thread is insufficient; remote code policy still applies | All Android APK/ABI size, cold/warm latency and RSS UNKNOWN; upstream heap defaults known only | BLOCKED by Sistrum no-native constitution; requires owner ADR before any integration |
| B1: Embedded QuickJS | Modern JS, host shim still required; Android wrapper/JNI and engine patch upkeep; engine MIT | Heap/stack/interrupt controls do not make an OS sandbox; isolate crash/process boundary and broker necessary | UNKNOWN; do not borrow desktop engine footprint as APK budget | BLOCKED by no-native end state absent ADR; not chosen for convenience |
| B2: Pure JVM engine, e.g. Rhino | Kotlin/Java integration and synchronous broker feasible in principle; modern JS/builtin behavior and reflection denial must be proven; candidate license/version not yet pinned | isolatedProcess service, no Java object exposure/ClassShutter-like escape surface; CPU and heap/crash enforcement must be proven | UNKNOWN; measure packaged delta/RSS/startup on API31+ | Preferred no-native *POC candidate*, not selected production runtime; dependency/license gate first |
| B3: AndroidX JavaScriptEngine | System out-of-process JS, no app-bundled native runtime; device WebView support varies; synchronous host bridge not established | OS sandbox provided, isolates within it are weaker boundaries; feature-check memory/termination; no assumed synchronous network API | UNKNOWN; system process cost included; connection relatively expensive per official docs | Useful comparison POC; currently UNKNOWN package compatibility; cannot promise transparent upstream support |
| C: Trusted Kotlin provider-specific adapters | Small API surface, no downloaded JS; audited methods only, upstream updates need app release | Avoids arbitrary remote execution; network/content rights still apply; lowest execution risk | UNKNOWN until measured, lower infrastructure complexity inferred | Recommended fallback if B cannot safely satisfy compatibility; explicitly not a full .sflx runtime |

[QuickJS documentation](https://www.bellard.org/quickjs/quickjs.html) describes memory/stack/interrupt APIs and MIT licensing. The [Android JavaScriptEngine guide](https://developer.android.com/develop/ui/views/layout/webapps/jsengine) documents optional system support, out-of-process execution and feature-dependent termination/heap controls; it does not establish a drop-in SpotiFLAC host bridge.

## Decision gates

PROPOSED: keep Registry → Verification → Runtime → Adapter boundaries regardless of backend. First authorize U-020–025 POC against synthetic fixtures and exact reviewed packages, with no credentials/library. Choose production only after compatibility, isolation, no-native, license, policy and measured resource gates. If pure JVM/system paths fail, select C for approved narrow providers and mark full extension execution BLOCKED rather than claiming parity. Changing architecture/native rules requires owner decision; this audit does not make it.

UNKNOWN: dependency candidate versions/licenses, JS feature coverage, Binder/host bridge deadlocks, interruption of regex/BigInt/native host calls, bytecode footprint and Android OEM behavior. No runtime integration/test passed here.
