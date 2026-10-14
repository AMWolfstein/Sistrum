---
name: kotlin-coroutines-flows
description: Kotlin coroutine and Flow reference for Sistrum — structured concurrency, cancellation, dispatchers, StateFlow/SharedFlow, and testing with kotlinx-coroutines-test. Use when writing or reviewing coroutine/Flow code (analysis workers, scanner, player state, ViewModels) or debugging cancellation and races.
---
<!-- Adapted from ECC (github.com/affaan-m/ECC @ b92f181), MIT License, Copyright (c) 2026 Affaan Mustafa; see docs/ECC-LICENSE.md. Modified for Sistrum. -->

# Kotlin Coroutines & Flows

## Structured concurrency
- Never `GlobalScope`. Scope work to its owner: `viewModelScope`, a service/engine scope cancelled on release,
  `coroutineScope {}` for children, `LaunchedEffect(key)` in Compose.
- Parallel parts: `coroutineScope { val a = async { … }; val b = async { … }; combine(a.await(), b.await()) }`.
- Independent children whose failure must not cancel siblings (e.g. analysing several files): `supervisorScope`.

## Cancellation
- Long loops (decode, analysis, scan) call `ensureActive()` (or `yield()`) each iteration/block.
- Never swallow `CancellationException`: `catch (e: Exception)` and `runCatching {}` catch it too — rethrow it
  (`catch (e: CancellationException) { throw e }` first) or check `isActive`.
- Cleanup in `finally` (close decoders/streams, release codecs); use `withContext(NonCancellable)` only for short cleanup.

## Dispatchers
- Decoding/analysis/DSP: `Dispatchers.Default` (or the bounded pool `AnalysisConcurrency` provides); file/DB I/O:
  `Dispatchers.IO`; never block `Main` or the playback thread. ExoPlayer/Media3 objects stay on their own looper.

## Flows
- UI state: one immutable data class in a `MutableStateFlow`, changed with `update { it.copy(…) }`; expose
  `asStateFlow()`. Never put mutable collections inside.
- Derived state: `combine(...).stateIn(scope, SharingStarted.WhileSubscribed(5_000), initial)`.
- One-off events: `MutableSharedFlow` (or a `Channel`) collected in `LaunchedEffect`.
- Collect in UI with `collectAsStateWithLifecycle()`; `flowOn` changes upstream only.
- `catch {}` on a Flow must log/convert the error, not hide it (see `silent-failure-hunter`).

## Testing
- `runTest` + `StandardTestDispatcher`; `advanceUntilIdle()`; inject dispatchers instead of hardcoding them.
- Fakes over mocks: a fake repository backed by `MutableStateFlow`.
- Test cancellation explicitly: cancel the job, assert resources released and no further emissions.

## Anti-patterns
`GlobalScope`; collecting in `init {}` without a scope; mutable lists in StateFlow; catching `CancellationException`;
creating Flows inside composables without `remember`; `runBlocking` in app code.
