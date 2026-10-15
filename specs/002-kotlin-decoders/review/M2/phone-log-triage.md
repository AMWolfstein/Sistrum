# M2 phone log triage

VERDICT: NEEDS CHANGES

Reviewed range: `a18bbcb^..HEAD` (HEAD `b9200a8`). Evidence: all six `instr-*.log` and six `logcat-*.log` files in `/tmp/verify-task.fezU3l`. Log references below are relative to that directory; line numbers are physical file lines. Findings are grouped by cause, not counted once per stack frame or warning occurrence.

| Category | Observed count | Assessment |
| --- | ---: | --- |
| QA crashes / ANRs / native crashes / unexpected process deaths | 0 | Six normal instrumentation-finished kills; no crash signature |
| Daily apps started, killed, or instrumented by this run | 0 evidenced | Background package/artifact references are not execution evidence |
| HAL underrun warnings | 57 | One HIGH finding; lifecycle correlation limits attribution |
| AudioTrack create/write failures, buffer timeouts, disabled-track restarts | 0 | No matching failure in QA client logs |
| ExoPlayer playback-error events | 6 | All verified deliberate invalid-input tests |
| App silence-seek fallbacks | 2 | Both verified deliberate unsafe-lead cases |
| AudioTrack retrograde timestamp corrections | 17 | LOW; corrected by platform |
| AudioFlinger slow-write diagnostics | 15 | One MEDIUM finding; 63–71 ms versus 40 ms buffer |
| QA skipped-frame, StrictMode, long-GC, low-memory-kill warnings | 0 | No matching scoped event |
| Instrumentation classes / tests | 6 / 59 | 59 passed; 0 failed, ignored, assumption-skipped, or zero-test classes evidenced |

QA PID mapping from ActivityManager `Start proc` lines:

| Class | PID | Start-proc evidence | Result evidence |
| --- | ---: | --- | --- |
| NightModeDeviceTest | 14322 | logcat-NightModeDeviceTest.log:159 | instr-NightModeDeviceTest.log:10 — 2 tests |
| SilenceCrossfadeDeviceTest | 14406 | logcat-SilenceCrossfadeDeviceTest.log:52 | instr-SilenceCrossfadeDeviceTest.log:16 — 6 tests |
| Media3CrossfadeTest | 14631 | logcat-Media3CrossfadeTest.log:46 | instr-Media3CrossfadeTest.log:38 — 16 tests |
| Media3EngineCoreTest | 15045 | logcat-Media3EngineCoreTest.log:47 | instr-Media3EngineCoreTest.log:6 — 12 tests |
| Media3LimiterTest | 15204 | logcat-Media3LimiterTest.log:47 | instr-Media3LimiterTest.log:8 — 2 tests |
| LoudnessStoreTest | 15278 | logcat-LoudnessStoreTest.log:47 | instr-LoudnessStoreTest.log:6 — 21 tests |

Instrumentation runs in the target QA process with `com.amwolfstein.sistrum.dev.qa.test`; the captured starts do not show a separate test-package process. Each logcat `TestRunner: run finished` agrees with its instrumentation count (respectively lines 2441, 10496, 23169, 4153, 866, and 244). Crossfade test status reports include PASS measurements after the fixture assumptions, so their passing totals do not conceal an assumption skip.

## Findings

1. [HIGH] logcat-NightModeDeviceTest.log:1683 — HAL reports `out_write: underrun(215) frames_by_time(1488) > out->last_fifo_frames_remaining(0)` during passing `onReducesSettledPlateauRangeByAtLeastThirtyPercent`. This is a device audio-output warning that the passing pre-session PCM measurement does not rule out. Immediately preceding evidence associates this output startup with QA PID 14322: `start_output_stream: exit` at line 1672, AudioFlinger write taking 66 ms at line 1673, and `trackPid 14322` at line 1682. Likely cause is the vendor deep-buffer output startup/scheduling path; no app stack establishes an implementation culprit. Suggested follow-up: distinguish startup/drain warnings from starvation while QA audio is active using track/output timing, and fix any confirmed active-output starvation before claiming a clean audio phone check. An audible dropout is **not established** by this dump, and these muted/pre-session tests are not a listening check.

   There are 57 HAL warnings across the captured run (Night 1, Silence 26, Crossfade 26, Core 3, Limiter 1, Loudness 0). They are the same grouped concern, not 57 proven audible dropouts. Some demonstrably occur after pause or completion: Core line 3450 pauses the track before warnings at 3509/3511/3530; Silence line 10496 finishes the class before warning 10589. Those occurrences alone would not establish a HIGH playback defect. The complete correlation inventory is below so lifecycle noise can be separated without losing evidence.

   | Log suffix | Test method (or lifecycle boundary) | Warning lines |
   | --- | --- | --- |
   | NightModeDeviceTest | onReducesSettledPlateauRangeByAtLeastThirtyPercent | 1683 |
   | SilenceCrossfadeDeviceTest | twoAndThreeSecondTailsOverlapAudibleAudioAndSkipIncomingLead | 1106, 1115, 1424, 1436, 2034, 2048 |
   | SilenceCrossfadeDeviceTest | retainedLeadCannotStartMeasuredTailBeforeCurrentProcessedClock | 2472, 2482 |
   | SilenceCrossfadeDeviceTest | shortAndEntirelySilentIncomingTracksCannotEndDuringTheOutgoingFade | 3222, 3227, 4360, 4379 |
   | SilenceCrossfadeDeviceTest | delayedIncomingPlaybackFailsTheCommonClockOverlapOracle | 5586, 5601, 5828, 5830, 5832, 5834 |
   | SilenceCrossfadeDeviceTest | noProfileAndNoSilenceKeep001PcmTimingWithinTenMs | 7109, 7111, 7112, 8334, 8336, 9418, 9420 |
   | SilenceCrossfadeDeviceTest | after class completion | 10589 |
   | Media3CrossfadeTest | incomingExistsOnlyInWindowAndOutgoingIsReleasedAfterFade | 4314, 4323 |
   | Media3CrossfadeTest | skipToUnpreparableTargetReturnsFalseFast | 5551, 5575 |
   | Media3CrossfadeTest | gainStageTeeMatchesEqualPowerCurvesAndEnvelope | 9840, 9855 |
   | Media3CrossfadeTest | endOfTrackFadeDecidedOnProcessedTimelineEndsWithTrackA | 10845, 10848, 10891 |
   | Media3CrossfadeTest | firstSkipWithoutHistoryWarmsUpAndStartsWithinFiftyMs | 11766, 11789 |
   | Media3CrossfadeTest | audibleCurveStartsAreWithinFiftyMs | 12774, 12801 |
   | Media3CrossfadeTest | twoFadesEmitExactlyOneTransitionEachAndNoGaplessAdvance | 13769, 13778, 15088, 15225, 15227 |
   | Media3CrossfadeTest | skipFadeCurvesStartTogetherAndSnapLeavesNoOldGain | 16132, 16196, 17142, 17153 |
   | Media3CrossfadeTest | focusDuckReachesBothPlayersAndSeedsNewIncoming | 18728, 18751 |
   | Media3CrossfadeTest | pauseAndSkipFlushEveryOldGainFrameDuringAndAfterFade | 23014, 23036 |
   | Media3EngineCoreTest | positionAfterPauseIsStableAndSeekBackwardReturnsLowerPosition | 3509, 3511, 3530 |
   | Media3LimiterTest | twoEnginesGetDifferentSessionsEachPlayerOnOwnSession | 578 |

2. [MEDIUM] logcat-NightModeDeviceTest.log:1673 — AudioFlinger `write() process 66 ms, mBufferMs 40` during `onReducesSettledPlateauRangeByAtLeastThirtyPercent`; likely vendor output-start latency (stream exits startup immediately before it). Fifteen such diagnostics occur: Night lines 584/1673/2142, Silence 3971/5200/6910/8137/9217/10341, Crossfade 557/1341/7577, Core 1023/3415, Limiter 574. Values range from 63 to 71 ms. This is system-output latency evidence, not a measured main-thread stall or proof that Kotlin DSP exceeded its deadline. Investigate with the underrun correlation above; no app stack identifies a source fix.

3. [LOW] logcat-Media3CrossfadeTest.log:5791 — expected missing-file source error during `skipToUnpreparableTargetReturnsFalseFast`; cause names `t060c_missing.wav` at line 5799. Test source `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/player/media3/Media3CrossfadeTest.kt:484` deliberately removes that target and asserts fast refusal, no transition, and continued outgoing playback. This is exercised error handling, not an unexplained swallowed playback failure. Filter only with this test/fixture context.

4. [LOW] logcat-Media3CrossfadeTest.log:7100 — two expected source-error events (second at 7241) during `failedIncomingFallsBackAndErrorDuringFadeSnapsWithAttribution`; cause is deliberately absent `t059_missing.aifc`. Source `Media3CrossfadeTest.kt:352` verifies the failed incoming preload, normal-source fallback and attributed error, then explicitly injects outgoing/incoming failures. Its PASS status appears at logcat line 8561 and instr line 14. No unexpected decoder fallback is established.

5. [LOW] logcat-Media3EngineCoreTest.log:388 — deliberate malformed-FLAC loader exception during `malformedFilePrepareThrowsWithoutLeakingPlayers`, followed by playback errors at 401 and 429 (garbage FLAC and empty MP3). Stack includes `ProviderExtractorsFactory.kt:12` and `:78`, wrapping Media3's `FlacMetadataReader.readMetadataBlock`; the test explicitly writes malformed input and requires prepare to throw without leaking players (`Media3EngineCoreTest.kt:227`). A third playback error at logcat line 2324 is the deliberate missing WAV in `missingFilePrepareThrowsWithoutLeakingPlayers` (`Media3EngineCoreTest.kt:310`). These account for all three Core playback errors; they are expected negative-test results, not crashes.

6. [LOW] logcat-SilenceCrossfadeDeviceTest.log:3114 — app logs `Silence seek fallback provider=Media3 track=b leadMs=9000 durationMs=10000 fadeMs=3997`, restarting from zero; second occurrence at 4252 uses lead 10000/fade 4000. Both occur during passing `shortAndEntirelySilentIncomingTracksCannotEndDuringTheOutgoingFade`. Source `SilenceCrossfadeDeviceTest.kt:130` explicitly supplies these unsafe profiles and asserts restart below 1000 ms plus complete outgoing fade. Production boundary is `androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/media3/Media3Engine.kt:1068`. Cause and provider are logged; this is intentional boundary behavior.

7. [LOW] logcat-Media3CrossfadeTest.log:17172 — AudioTrack corrects a retrograde timestamp by approximately 131 ms during `skipFadeCurvesStartTogetherAndSnapLeavesNoOldGain`. There are 17 corrections: Crossfade lines 6195/8932/11478/14728/15230/17172/17896/23061, Core 1578, Silence 2064/3253/4397/6912/7554/8139/9218/10343. They span skip/pause, ordinary fades and silence timing tests; the platform reports correction, not failure. Likely output-clock behavior around lifecycle changes; no app stack or failed timing assertion establishes a separate defect. Retain when examining clock accuracy, rather than treating corrected timestamps as crashes.

8. [LOW] logcat-Media3CrossfadeTest.log:7699 — platform FLAC codec does not support loudness annotation during `failedIncomingFallsBackAndErrorDuringFadeSnapsWithAttribution`. Seven annotation warnings total accompany codec configuration/capability messages across Crossfade and Core; examples include Crossfade 7658 (`config failed => CORRUPTED`, debug-level configuration), 7659 (unsupported query parameter), 7661 (max input size advice), and Core 2692/2728. These are codec negotiation diagnostics, with subsequent successful test completion, not an observed fatal codec error or app Night Mode refusal. Core 1322 separately warns of an intentionally truncated WAV in `truncatedWavEndsOrErrorsWithoutCrashing`; Core 2525 ignores an unknown WAV chunk. Startup capability scanning also reports unsupported unrelated MIME types. Filter by tag and context, not the word `unsupported` alone.

9. [LOW] logcat-LoudnessStoreTest.log:54 — vendor startup reflection failure (`VMRuntime.SupressionGC`), with related vendor `callGcSupression` null invocation at 66, occurs before tests begin; repeated startup noise appears in all six QA processes. Runtime-flag, instrumentation ABI-description, property-access, scheduler-file permission messages and repeated `AudioTrackExtImpl: get fade type, but return null` (e.g. Crossfade 301 during `readyIncomingSurvivesLongPauseAndStartsWhenFadeBegunAtTheEnd`) have no app stack or associated failed create/write. These are platform/vendor diagnostics worth filtering separately from app exceptions. Stereo speaker-layout defaults and audio policy effect-route diagnostics are likewise platform fallbacks, not evidence of a failed requested sample format.

10. [LOW] logcat-SilenceCrossfadeDeviceTest.log:910 — background `artd` inspects daily Airmedy package artifacts while `twoAndThreeSecondTailsOverlapAudibleAudioAndSkipIncomingLead` runs. Lines 911/913/914 continue artifact inspection; 798/799 are battery statistics for Airmedy QA packages. These lines do not say the runner launched, killed, installed, or instrumented a daily app. No corresponding daily-app ActivityManager action appears in the captured files. Filter this out-of-scope background noise; do not misclassify it as a device-rule violation.

## Limits and verification

Only the report was written. Read-only commands used: `ls`, `git status --short`, `git log --oneline a18bbcb^..HEAD`, `git diff --stat a18bbcb^..HEAD`, `rg`, `awk`, and `sed -n` over supplied logs and relevant test/production source. No Gradle, adb, device access, code/test edits, staging, or commits.

Six ActivityManager kills explicitly say `due to finished inst` (Night 2452, Silence 10499, Crossfade 23178, Core 4197, Limiter 875, Loudness 249). They follow successful completion and are not unexpected deaths. The only captured GC-pause line belongs to `m.android.phone` PID 4777, outside QA scope. No unexpected focus-loss callback was found; `focusDuckReachesBothPlayersAndSeedsNewIncoming` deliberately changes focus gain and reports PASS.

The supplied directory contains no instrumentation output for `OfflineDecoderReleaseDeviceTest`, no standalone runner transcript, and no audio recording. Its execution, the exact APK revision, and audible/Bluetooth/headset behavior cannot be verified from these files. Saved instrumentation reports Night Mode range reduction of 48.16% (instr-NightModeDeviceTest.log:2), but explicitly identifies the pre-session tap; that measurement does not resolve downstream HAL warnings.

| Severity | Grouped findings |
| --- | ---: |
| CRITICAL | 0 |
| HIGH | 1 |
| MEDIUM | 1 |
| LOW | 8 |
| CRITICAL + HIGH | 1 |

VERDICT: NEEDS CHANGES
