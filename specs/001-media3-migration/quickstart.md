# Quickstart — validating 001

Prerequisites: the `.qa` test build (ADR-007) — **never** the daily app `me.misa198.airmedy.dev`; the test corpus
in `/sdcard/Music/SistrumTestCorpus` (FR-071); a device on adb with the screen kept awake.

## Host (every task)

```
./gradlew :androidApp:testDevDebugUnitTest :sharedLogic:testAndroidHostTest
./gradlew :androidApp:assembleDevDebug
.claude/skills/verify-task/scripts/verify.sh --files '<task files>'
```

Expected: characterization tests unchanged and green (SC-001); `git diff main -- '**/PlaybackQueue.kt'
'**/ListeningTracker.kt'` empty (SC-010).

## Device (with the `.qa` build only)

| Scenario | How | Expected |
|---|---|---|
| US1 parity | Native engine selected; play an album, crossfade 6 s, EQ on | As the pre-migration build |
| US3 switch | Switch engine while playing | Track continues; next start uses the new engine; rescan runs |
| US2 core | Media3 selected; queue ops, restore after kill, 20k-track request | Spec US2; no TransactionTooLarge |
| US8 registry | Media3 selected; scan corpus | AIFF plays via kotlin-aiff; DSD/APE/WV/WMA/ALAC (CPH2307) in the skipped summary (SC-016) |
| US5 crossfade | 12 s fade; next/previous/pause/seek during fades; repeat-one | Equal-power, no steps, snaps instant (SC-007) |
| US6 loudness | Corpus tag forms, untagged, album mode | ±1 dB of expected (SC-004) |
| US7 EQ | Sweep tone with extreme EQ/width/preamp vs native engine | Within 0.1 dB at matching rates |
| SC-015 limiter | `adb shell dumpsys media.audio_flinger` during a fade; two tones clipping only when summed | Both tracks on one thread, DP enabled, output limited |
| SC-013 resources | 1 h screen-off, two library mixes, `dumpsys batterystats` / `meminfo` | 0 % / ≤10 % vs native |
| US4 system | Lock screen, notification, Bluetooth/headset, focus, unplug | Owner's manual checklist (never marked PASS by Claude) |

Instrumented classes run one at a time with `am instrument -w -r -e class <Class>
me.misa198.airmedy.dev.qa.test/androidx.test.runner.AndroidJUnitRunner`.
