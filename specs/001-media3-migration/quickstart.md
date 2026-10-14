# Quickstart — validating 001

Prerequisites: the `.qa` test build `com.amwolfstein.sistrum.dev.qa` (ADR-007) — **never** the daily apps
`com.amwolfstein.sistrum.dev` or `me.misa198.airmedy.dev`; the test corpus
in `/sdcard/Music/SistrumTestCorpus` (FR-071); a device on adb with the screen kept awake.

## Host (every task)

```
./gradlew :androidApp:testDevQaUnitTest :sharedLogic:testAndroidHostTest
./gradlew :androidApp:assembleDevQa
.claude/skills/verify-task/scripts/verify.sh --files '<task files>'
```

Expected: characterization tests unchanged and green (SC-001); `git diff main -- '**/PlaybackQueue.kt'
'**/ListeningTracker.kt'` empty (SC-010).

## Device (with the `.qa` build only)

| Scenario | How | Expected |
|---|---|---|
| US1 parity | Native engine selected (Developer → Playback engine; default is Media3 since T062c); play an album, crossfade 6 s, EQ on | As the pre-migration build |
| US3 switch | Switch engine while playing | Track continues; next start uses the new engine; rescan runs |
| US2 core | Media3 selected; queue ops, restore after kill, 20k-track request | Spec US2; no TransactionTooLarge |
| US8 registry | Media3 selected; scan corpus | AIFF plays via kotlin-aiff; DSD/APE/WV/WMA/ALAC (CPH2307) in the skipped summary (SC-016) |
| US5 crossfade | 12 s fade; next/previous/pause/seek during fades; repeat-one | Equal-power, no steps, snaps instant (SC-007) |
| US6 loudness | Corpus tag forms, untagged, album mode | ±1 dB of expected (SC-004) |
| US7 EQ | Sweep tone with extreme EQ/width/preamp vs native engine | Within 0.1 dB at matching rates |
| SC-015 limiter | `adb shell dumpsys media.audio_flinger` during a fade; two tones clipping only when summed | Both tracks on one thread, DP enabled, output limited |
| SC-013 resources | 1 h screen-off, two library mixes, `dumpsys batterystats` / `meminfo` | 0 % / ≤10 % vs native — **not met for 001, deferred to the cleanup/perf phase (owner 2026-10-14)**; the 1 h run stays an owner check |
| US4 system | Lock screen, notification, Bluetooth/headset, focus, unplug | Owner's manual checklist (never marked PASS by Claude) |

Instrumented classes run one at a time with `am instrument -w -r -e class <Class>
com.amwolfstein.sistrum.dev.qa.test/androidx.test.runner.AndroidJUnitRunner`.

## Regression harness (T062)

`com.amwolfstein.sistrum.regression.Regression001MeasurementTest`, one method at a time
(`-e class '<FQCN>#<method>'`): `sc003GaplessSilencePerPair` (SC-003, joins ≤ 10 ms), SC-006 start latency
(≤ 300 ms, median of each mix's file medians, 3 runs, non-debuggable build), `sc014…` (failure attribution),
`cpuSteadyAndTransition` / `cpuPerEngineAndCrossfadeMode` (SC-013 short proxy). CPU and latency numbers need the
release-like build: a temporary local edit of the `qa` build type to `isDebuggable = false; isJniDebuggable = false`,
reverted right after building (same `.dev.qa` ID), and `adb shell dumpsys battery unplug` during the run (the analysis
job runs only while charging), `dumpsys battery reset` after. Results and method: `research/regression-001.md`.
