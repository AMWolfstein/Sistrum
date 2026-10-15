VERDICT: NEEDS CHANGES

# M2 test coverage review

Range: `a18bbcb50e4c37c675d91846d8fd7d2d356b3109^..d2b9669` (HEAD at review). This contains T014–T020; T021 is still unchecked. Read CLAUDE.md, spec FR-019–021 / SC-008–009, tasks, and decisions D-34–40. Reviewed code and tests without executing them. Gradle, adb, instrumentation, and PCM measurements: NOT RUN, as required by review mode. Only this report was written. Existing unrelated working-tree changes were left alone.

## 1. Coverage summary

Paths below are relative to `androidApp/src/`; `main`, `test`, and `androidTest` paths continue under `kotlin/com/amwolfstein/sistrum/` unless specified otherwise.

| Changed production unit | Tests exercising it | Coverage and limits |
| --- | --- | --- |
| `main/.../player/media3/FloatChainAudioSink.kt`: NightModeProcessor configuration, processing, flush/reset | FloatChainNightModeTest; FloatChainNightModeLifecycleTest; NightModeDeviceTest | Strong bypass, range, ramp, multichannel, reversal, reconfiguration, backpressure tests. Device render has H1 below. Limiter assertions on host use a test clamp. |
| `main/.../player/PlaybackPreferences.kt`: nightMode, skip-silence settings and setters | NightModePreferencesTest; SilencePlaybackPreferencesTest | Real DataStore persistence/reopen and independent settings. |
| `main/.../player/media3/Media3Engine.kt`: preference collection, A/B creation and close | No test supplies its new nightMode flow | M1 below: sink-reader tests do not exercise engine subscription/factory wiring. |
| `main/.../analysis/SilenceProfile.kt`: SilenceAccumulator | SilenceProfileTest via real TrackAnalyzer | Empty, fully silent, short, strict threshold, odd channels, fragmented edges/interior spans, cancellation/resume; rates through 192 kHz. |
| `main/.../analysis/TrackAnalyzer.kt`, `AnalysisMeasurer.kt`: profile accumulation, checkpoint v2, result propagation | SilenceProfileTest; existing TrackAnalyzerTest / AnalysisMeasurerTest | Good fake-decoder integration; resume equivalence uses the full measurement as its loudness reference, with explicit independent silence expectations. No real decoder here. |
| `main/.../analysis/LoudnessModels.kt`, `LoudnessStore.kt`: document serialization/version predicate | SilenceProfileTest; PlaybackSilenceProfileTest | Pure document tests cover versions/fingerprint/status/refusal. New Room query composition and transactional profile read have no new integration tests; existing LoudnessStoreTest assumptions conflict (H2). |
| `main/.../player/media3/TransitionController.kt`: prepare window, automatic duration, incoming offset | TransitionControllerSilenceTest | Explicit spec/001 numeric boundaries, missing/zero/off profiles, 401 ms boundary, short/silent tracks, manual reuse, repeat-one, unready fallback. |
| `main/.../player/PlaybackCoordinator.kt`: resolveItem/settings/engine dispatch | PlaybackCoordinatorSilenceTest | Real coordinator with fake ports, real policy in a delegating engine; processed-position/repeat-all/profile error/cancellation coverage. |
| `main/.../player/engine/PlayerEngine.kt`: legacy timing defaults; `PlaybackModels.kt`: item profile | PlaybackCoordinatorSilenceTest; TransitionControllerSilenceTest | Native default and additive profile contract checked. |
| `main/.../player/media3/Media3Engine.kt`: seek-origin application, audible-end clamp, real transitions | SilenceCrossfadeDeviceTest | Real generated WAV and real A/B players; H3 prevents its metric proving actual temporal overlap. |
| `main/.../player/media3/Media3PlayerFactory.kt`, `PlaybackService.kt`: reader/profile/settings wiring | Device tests exercise factory and engine, but omit service | Real factory covered for silence; Night mode bypasses newPlayer. |
| MainActivity, AppDestinationModels, AppDestinationContent, PlaybackSettingsContent, English/Arabic strings | Existing ArabicTranslationCompletenessTest; no new UI test | Translation-key completeness only; engine-dependent switch enablement is untested. |

No deleted/weakened existing tests found in this range. The new tests have no assume/ignore escape hatch. The explicit legacy pipeline, imported VOICE kernel, literal transition table, and generated PCM boundaries are useful independent references. The measurements are pre-session PCM, consistent with D-40; they cannot establish a hardware limiter ceiling.

## 2. Critical gaps and verified findings

### H1

[HIGH] `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/player/NightModeDeviceTest.kt:86` — the nine-second render exceeds the enclosing five-second looper call — `onReducesSettledPlateauRangeByAtLeastThirtyPercent` supplies 432,000 stereo frames to a playing DefaultAudioSink inside `factory.call { ... }`. `Media3PlayerFactory.kt:216,230` defaults that call to 5,000 ms and throws when the latch does not complete. AudioTrack backpressure makes feeding nine seconds take approximately nine seconds minus its queued buffer, rather than CPU-only processing time; a normal subsecond PCM buffer cannot make this finish in five seconds. The test's 30-second internal deadline and 90-second JUnit timeout never override the enclosing timeout. Expected phone result: `IllegalStateException: timed out waiting for the sistrum-player looper` during the first OFF render, before SC-008 assertions. Fix the harness with a suitably bounded explicit call timeout or incremental short looper calls, and retain the consumed-byte checks. Test a backpressured render longer than five seconds.

### H2

[HIGH] `androidApp/src/main/kotlin/com/amwolfstein/sistrum/analysis/LoudnessStore.kt:40` — the new pending-query consumer contract is not covered by database tests, and contradicts existing fixtures — `LoudnessStoreTest.kt:163` writes `Measured(..., histogram)` using its default `silence = null` (`LoudnessModels.kt:36`). `analysisDocument` at `LoudnessStore.kt:260` consequently omits silenceVersion. The new planner branch selects the available same-fingerprint `ok` document because `needsSilenceAnalysis` at lines 301–305 returns true. The next assertion at `androidTest/.../analysis/LoudnessStoreTest.kt:164` requires A to be absent, but the actual list contains A. The same mismatch affects lines 207/209 (B is pending in addition to failed A) and 223 (A and B remain pending after rescan). This is a directly traceable phone-suite failure, not an observed run. Fix by explicitly distinguishing legacy-profile-refresh fixtures from current fully measured fixtures in the database integration tests, with the spec reason recorded; preserve the original no-reanalysis assertion for a current result. Add real Room cases for missing/old/current profile versions, successful refresh, failed one-shot refresh, unavailable audio and fingerprint change. Do not change production to label absent measurements as measured zero just to satisfy the old fixture.

### H3

[HIGH] `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/player/SilenceCrossfadeDeviceTest.kt:169` — fitting and independently aligning A and B removes the timing error SC-009 must detect — Tap rows use only frame counters reset on each flush (`:224,237`); there is no shared output-time anchor. Lines 170–177 independently fit B's start and subtract that start when looking up overlap windows. Delaying B's actual playback by three seconds after the silence-aware seek leaves B's frame-relative curve, firstAudible-minus-inStart, and all independently aligned silence windows unchanged. A still ends at the expected source boundary. Such a regression in `Media3Engine.kt:1056–1061,1112–1126` can therefore produce an audible gap while these SC-009 assertions pass. Existing no-profile 001 tests cannot establish alignment after this new nonzero restart. Add an assertion on a common output clock (AudioTrack timestamps/positions or equivalent established timing instrumentation), and a negative control that delays B by more than 100 ms and must fail. Keep curve fitting for gain-shape checks, but do not use independent fits as proof that the two sources overlap in time.

## 3. Weak tests / additional gaps

[MEDIUM] `androidApp/src/test/kotlin/com/amwolfstein/sistrum/player/media3/FloatChainNightModeTest.kt:125` — the A/B/replacement test constructs three sinks itself with the same test-owned lambda; it does not create a Media3Engine or any factory player. `NightModeDeviceTest.kt:101` likewise injects a constant directly into a sink. Removing the flow collection in `Media3Engine.kt:333–335`, or dropping `nightMode = readNightMode` from incoming creation at line 907, would leave all new Night mode assertions green while saved ON/live toggles fail in the actual app. D-34/D-40 acknowledge this limit, but no remaining named T021 test supplies the new engine flow. Add `Media3NightModeIntegrationTest.savedOnAndLiveToggleReachCurrentIncomingAndReplacement` using the real engine with a controllable/persisted flow and a post-Night-mode capture, and verify collector cancellation on close. Important gap, not evidence that today's wiring is broken.

[MEDIUM] `androidApp/src/test/kotlin/com/amwolfstein/sistrum/analysis/SilenceProfileTest.kt:47` — every resume cut uses 8 kHz, whose frame period is exactly 125 microseconds. The fake decoder reverses the production `frames * 1_000_000 / rate` seek conversion with integer division (`:125`), so this fixture cannot expose a sub-frame truncation mismatch at 44.1 kHz or a rate above 192 kHz. A checkpoint at an arbitrary frame adjacent to the exact one-second edge can resume one frame early when the decoder floors the microsecond seek; this test would miss a changed edge or duplicate loudness sample. Add a real-decoder checkpoint case at an unaligned 44.1 kHz frame and a high-rate case, assert the resumed PCM frame sequence and exact silence durations against uninterrupted reference PCM. Important boundary gap; this review does not claim every decoder currently duplicates that frame.

## 4. Mechanical assumption traces

Each row names an assertion/await site; parameterized invocations share a row when the formula and preconditions are identical. References in this section use basenames from the coverage table. Generated fixtures are constructed in the tests, so there is no external cached file/header to inspect, no fixture-table oracle and no asserted hash. All numerical evaluations below follow the checked-in generators, not an executed test. Conditions in test helpers are included.

### Device: NightModeDeviceTest

| Assertion/await | Number / reference trace | Event/precondition trace and cost |
| --- | --- | --- |
| :61, byte identity | :46 gives 96,000 floats = 48,000 stereo frames = 1 second = 384,000 bytes. Explicit conversion/DSP pipeline :48–60 is the 001 reference. Sink's OFF branch `FloatChainAudioSink:247–250` copies raw bytes. Neutral and colored chains use separate identical configurations. | configure → play → handleBuffer; all supplied by render, no omitted service call. Two one-second renders fit the 60 s outer bound; each individual default factory call still has a 5 s bound. |
| :78, OFF range | :70 gives 864,000 floats = 432,000 frames = 9 s. Each RMS window contains 96,000 samples. OFF RMS is 0.06 / 0.8 / 0.06; `20 log10(0.8/0.06) = 22.4987747 dB`. Float literal rounding is well below 1e-5 dB. Independent amplitude specification D-34. | Actual sink generation/capture required; test invokes it. **H1** prevents reaching this assertion under ordinary AudioTrack buffering. |
| :79, ON reduction | Spec SC-008 requires ≤0.70 × 22.4987747 = 15.7491423 dB. `Compressor.kt:41–59`: quiet is below -23 dB knee, loud over=18.0617997 dB, target=-10.8370798 dB; settled range ≈11.661695 dB. Windows [2,3), [5,6), [8,9) s allow multiple 250 ms release constants. Reference is the spec and VOICE curve, not the adapter's output. | ON reader consumed at queueInput :240; compressor :259–261; output branch :270–273. Test supplies ON directly. Does not traverse service/engine/factory preference path (M1). Two nine-second renders exceed individual 5 s factory calls. |
| :112, backpressure deadline | Local deadline=30,000 ms; approximately 9 s per long render at playback speed; 240 s track requires ≈240 s minus queued buffer and would exceed both this and factory deadline. These are fixed generated fixtures, not long-file seek/performance tests. | `handleBuffer` false causes 2 ms retry until consumed; real AudioTrack is played (:107), volume zero changes amplitude only. Enclosing 5 s call expires first (H1). |
| :115, no remaining input | Pipeline consumes each input before reporting success; sink pending-output retry preserves buffer identity. 514 floats/chunk = 257 stereo frames; 864,000 leaves final 478 floats, still frame-aligned. | Same ByteBuffer retried; capture records position delta, avoiding double-counting partial writes. |
| :118, exact byte count | `input.size*4`: 384,000 bytes for identity; 3,456,000 for plateaus. Compressor has no lookahead/drain output (`Compressor:66`); Night mode maps one output sample per input at :268–278. | Captures only accepted bytes. playToEndOfStream occurs after all chunks; no delayed compressor tail expected. |
| :136, finite levels | quiet>0 and loud>0; square energy finite; log10 of positive finite RMS. ON peak curve and gain envelope finite for fixture. | render completed, range reads in-bounds settled windows. |
| :137, finite samples | Every input is finite; gain/width/EQ/preamp and VOICE operate at ordinary bounded amplitudes. | Checks full capture, not only selected windows. |

### Device: SilenceCrossfadeDeviceTest

Production entry chain: test `prepare(a, ..., 3000, false)` → `Media3Engine:324–395` sets file URI/start and calls play. `preloadNext` at :450–459 stores B and schedules ticks. Tick :1220–1225 calls controller.onPosition; with 10,000 ms duration, 2,000 ms fade and 5,000 ms prebuffer, the starting 7,000 ms remainder already qualifies (also with 2/3 s outgoing tail). PrepareIncoming → engine :904–922 creates/holds B at zero and prepares. Listener :99 and tick :1232 call checkIncomingReady :930–979, verify session and warm up B. The test itself replaces the production coordinator's `maybeStartCrossfade` (`PlaybackCoordinator:1375–1387`) with its policy/begin loop; this required call is present, not omitted. beginCrossfade :717–733 → controller.beginFade → StartFade → engine.startFade :1028. Fade completion tick :1233–1243 releases A. Thus there is no missing-coordinator timeout finding for this harness.

| Assertion/await | Number / reference trace | Event/precondition and cost trace |
| --- | --- | --- |
| :48–49, outgoing end | Generator :249–257: 480,000 frames, PCM16 stereo 48 kHz, blockAlign=4, data=1,920,000 bytes, RIFF size=1,920,036; audible ends 384,000 or 336,000 frames (8,000/7,000 ms). Profile tail=2,000/3,000. Controller :115–118 yields trigger 6,000/5,000 ms for 2 s fade; engine :1042 clamps to `10000-tail-processed`. Expected end=8,000/7,000; tolerance 10 ms. Fitted start plus effective event duration traces source end, subject to buffer-observation error. | Start origin=3,000 ms leaves 3/2 s to nominal trigger and 5/4 s to audible end. Real PCM and event required. Bounded 10 s WAV supports each position. |
| :50–51, incoming first audible | B lead=96,000 frames; StartFade position from controller :103–105,138 is 2,000 ms; engine :1056–1060 sets timeline and processing origin. Gain in is `sin(frame/(duration*rate/1000)*π/2)` (`GainProcessor:115,124–135`), so first 5 ms RMS window exceeds .001. Expected relative first ≈2.5 ms, ≤100 ms. | Uses its own fitted B start, so the relative condition survives arbitrary output delay (H3). |
| :52, silence ≤100 ms | Threshold=.001 = 10^(-60/20), 5 ms windows=240 frames, tolerance=20 windows. Both aligned audible streams peak well above .001 except negligible fade endpoints; expected zero shared-silent windows. | Independent curve alignment has no actual simultaneous-output precondition: H3. |
| :65, legacy end | No profiles/OFF: engine clamp is `min(request,10000-processed)` (:1042). Expected 10,000 ±10 ms from source frame count. | Legacy decision helper loop explicitly begins fade; real transition required. |
| :66, current end | Missing profile or zero edges gives same formula and 10,000 ±10 ms. | Same entry chain as above. |
| :67, normalized start | Measurement :89–90: `(fittedStart+effectiveDuration)-2000`. If each end=10,000, both normalized starts=8,000. This checks source-end consistency again; it is not an independent test of actual 2 s fade length. | D-40 deliberately allows polling overshoot; no finding solely for that choice. Does not establish A/B output alignment (H3). |
| :68, silence=0 | Nonzero tone throughout; at least one fitted gain nonzero in each 5 ms window. Same threshold/count computation as :52. | Subject to H3. |
| :78, OFF silence ≥1800 | A tail [8,10) s; nominal fade [8,10); B unskipped lead [0,2) s. OFF control scans B for event duration in 5 ms steps; should give nearly 2,000 ms, allowing 200 ms polling/clamp loss. | Real fade is explicitly started using 001 helpers. A must supply source-end tail rows (:159–162). |
| :79, OFF incoming lead ≥1900 | B timeline restart=0 when skip=false (controller :138), so first nonzero 5 ms row ≈2,002.5 ms. Independent generated threshold at source frame 96,000. | Wait :144 observes audible B after fade/lead. |
| :117, no engine Error | Errors originate from player listener/handlePlayerError; valid local PCM WAV should produce none. | polled during preload-ready await, after real prepare. |
| :118–119, ready await | 2 live players: factory increments per newPlayer; both ExoPlayers STATE_READY; hasPreloaded additionally requires controller-ready/session checks. | Detailed entry chain above supplies every call. Preparation at 3 s does not wait until end; 15 s bound versus immediate prebuffer qualification + production 10 s prepare deadline has 50% deadline headroom. |
| :125, no Error | Same listener path. | Checked on every start-decision poll. |
| :133, begin succeeds | phase Preparing and incomingReady at controller :129–138; beginCrossfade may await warmup. | Test waits for ready first; supplies public begin call as coordinator does. |
| :123–137, start await | Policy needs 401≤remainder≤2000; cap=min(2000,5000)=2000. At origins and tails above, ≤5 s of source time until trigger, less processing lead. | 15 s bound has ample fixture headroom. A four-minute track starting at 3 s would need ≈235 s to a near-end trigger, so this helper cannot be reused unchanged for long tracks. Fixed 10 s fixture is explicit. |
| :140, no Error | Same listener path. | Checked during completion. |
| :138–142, completion await | `remainingFrames==0`/A ended, then `now>=max(fadeNotBefore,drainDeadline)` at engine :1237–1243; controller onFadeComplete emits ReleaseOutgoing (:141–144). Count becomes 1. | Engine's scheduled tick handles this; no external coordinator required. ≈2 s fade plus ≤1.5 s lead vs 15 s. |
| :144, audible B await | .2/√2 ≈.141421 RMS after fade, >.01. B has 8 s audible material with skip, or begins after 2 s without skip. | incoming restart runnable engine :1076–1130 calls startIncomingPlayback when ready. 15 s vs 10 s restart timeout plus ≤2 s lead; no measured phone runtime asserted. |
| :146, no Error | Same listener path. | Final event drain. |
| :147, single TransitionStarted | Engine :1136 emits once for automatic fade with incoming B and effective duration. `skipRequestDurationMs==null` because public automatic begin used. | Test does not issue manual crossfadeTo; correct event precondition. |
| :148, incoming equals B | Engine :1136 uses incoming.item; same PlaybackItem provided through preload. | No resolver/data-store involved, a coverage limitation rather than mismatch. |
| :149, GaplessAdvanced count=0 | Unready B would yield AppendGapless at controller :131–134; ready path emits StartFade. | Readiness wait and successful begin exclude fallback. |
| :152, outgoing rows ≥100 | Minimum 4 s until audible end from 3 s origin; 4,000/5=800 full rows even before source tail. | latest populated Tap generation survives reset; A not restarted in this test. |
| :153, incoming rows ≥100 | 2 s fade alone=400 rows. | waits completion, then audible B. |
| :159, outgoing tail rows | OFF source tail=2,000 ms →400 windows; assertion floor((2000-100)/5)=380. | A source data remains buffered/processed through end; OFF retains original end clamp. |
| :160, tail peak | Generator gives exact zero from frame384000; gain × zero remains zero <.001. | Captured A tail required by :159. |
| :161–162, OFF end | A remaining source 7,000 ms from origin →1,400 rows; final center=6,997.5; 3000+6997.5+2.5=10,000. | Uses all captured A source PCM, not wall-clock end. |
| :182, no player leak | engine.close → release current/incoming/outgoing (:791 onward); factory live count→0. | Runs finally even on assertion failure. |
| :183, outgoing cleanup | File is private cache, unique generated name; exists then delete must succeed. | No MediaStore indexing or scoped-storage grant needed. |
| :184, incoming cleanup | Same as outgoing. | Both files owned by .qa target. |
| :190, middle rows ≥20 | For 2 s fade, incoming gains .15–.95 span ≈1403 ms; outgoing ≈1403 ms, ≈280 rows vs 20 required. Short polling-clamped fades still ordinarily hundreds of rows. | Need actual audible curve; an all-zero outgoing fade only takes explicit OFF branch. |
| :199, curve error <.015 | Analytic equal-power sin/cos matches GainProcessor :130–132; 5 ms window integrates five 1 kHz cycles, 240 frames. PCM16 amplitude max≈.19998 vs oracle .2; error ≪.015 away from endpoints. | Independent numeric curve reference, but inStart/outStart shifts are free parameters (H3). |
| :217, rate=48000 | WAV header generator :252 sets exactly 48,000; no resampler requested in tested sink chain. | Tee flush supplies format; generated WAV is parsed by real renderer. |
| :218, supported encoding | Generator PCM16; gain processor returns float, so tap typically receives float. Test accepts both float and PCM16 with correct normalization. | Format delivered by pipeline flush. |
| :219, stereo=2 | WAV channels=2, blockAlign=4; production renderer preserves it. | Generator and header are consistent. |
| :232, finite samples | Signed 16-bit input divided by32768, finite gain sin/cos and unity normalization. | Tested on every tapped sample, not only RMS rows. |

Cost beyond playback: writeTone is O(480,000) frames per file, ≈1.92 MB; four minutes would be 11,520,000 frames /46.08 MB per file. Tone/fit analysis is finite generated-data work, not a seek benchmark. PCM WAV seeks use file offsets; no Kotlin whole-file decode-from-zero seek is exercised. Three measure calls for the no-silence test, two for the edge test and one OFF control fit 120/120/90 s outer bounds in ordinary operation; these are estimates, NOT RUN results. No cached/external corpus permission dependency exists.

### Host integration: analysis and coordinator

| Assertion site | Number/reference trace | Event/precondition and cost trace |
| --- | --- | --- |
| SilenceProfileTest:17 | `SilenceAccumulator:40–41`: qualify ≥rate frames, floor(edgeFrames*1000/rate). Five-second generated PCM: zero [0,rate), nonzero [rate,3rate), zero [3rate,5rate) →1000/2000 at 8k,44.1k,192k; only last channel audible still breaks silence. | Real TrackAnalyzer feeds accumulator on meter worker (:134–143); fake decoder emits chunks1237, pipeline subdivides to4093. No extra decode is needed. |
| :18, calls=1 | TrackAnalyzer runs one decode pass when no resume mismatch. | Fresh analyzer/no resume; 5 s PCM processed without real-time pacing. |
| :26 | At .000999 peak strict below .001 →8,000 silent frames=1000 ms; ±.001 are not below threshold →0. Detector uses -60 dB in accumulator :20. | Full decode completes then profile returned (:201–207). Float .001 is slightly above mathematical .001, so this does not prove double-precision equality but matches intended float boundary. |
| :29 | 7,999<8,000 frame minimum →0/0. | completed short input, no coroutine wait. |
| :34 | alternating0/.1 yields at most one silent frame per run →0/0. Bounded capacity bufferFrames/2+1 in accumulator :20. | 31-frame meter chunks; no global-span accumulation. |
| :38 | first/last frame .2 → neither edge silent, regardless of interior length →0/0. | completed stream. |
| :42 | zero frames →no accumulator, TrackAnalyzer completed fallback SilenceProfile(0,0). | fake completed outcome, no chunk required. |
| :43 |120,000/48,000=2.5 s→2500/2500. | fully silent chunks joined by accumulator :33–34. |
| :53 | cancelled finalOutcome prohibits silence publication (:201–207). | Fake decoder returns Cancelled after delivering prefix; this exercises checkpoint drain, not asynchronous decoder cooperation with cancellation. |
| :57 | seek=floor(cut*1e6/8000): cuts8000/28000/40000→1,000,000/3,500,000/5,000,000 us. Source is literal prefix length, not adapter state. | checkpoint emitted on Cancelled after queue drain (:164–167), codec v2 encode/decode, analyzer restores meter+edges. M2 notes unaligned rates absent. |
| :58 | generated zero edges each16,000 frames→2000/2000. | resumed decoder starts at exact fixture frame at8k; no second full decode. |
| :60 | integrated LUFS equals uninterrupted real meter result. | Same input partitions/resume state; an equivalence assertion, not absolute loudness oracle. |
| :61 | histogram encoded value equals uninterrupted meter result. | Same equivalence limitation; external algorithm oracle remains in module tests. |
| :71 |96,000 fully silent frames/48k→2000/2000 even when EOF resume emits no chunk. | restored accumulator initialized before decoder (:89–93). |
| :76 | failed finalOutcome cannot publish partial profile. | Decoder supplies96,000 silent frames then Failed; covers nonempty failure path. |
| :87 | status silent for all-zero PCM; nonzero middle1 s provides enough loudness blocks →ok; AnalyzerTrackMeasurer :78–84 selects by finite meter outputs. | Real analyzer→measurer→document path; fake decoder completed4 s. |
| :88 | silent lead192,000 frames→4000; non-silent lead48,000→1000. | profile serialized by LoudnessStore :260–275. |
| :89 | silent tail192,000→4000; non-silent tail96,000→2000. | same serialization. |
| :90 | version literal silence-1 from Detector.VERSION via SilenceProfile:7. | Explicit external contract version, no self-derived expectation. |
| :91 | document version current, same fingerprint hash, so needsSilenceAnalysis=false (:301–305). | Tests pure predicate, not Room scheduling (H2). |
| :98 | absent/old version same-fingerprint ok→refresh true. | pure document predicate. |
| :101 | current serialized profile→refresh false. | no database write/read in test. |
| :103 | failure preserves prior document keys, adds last_failure/timestamp/attempt version (:242–252). | same-fingerprint status ok supplied explicitly. |
| :104 | attempted current version excludes refresh. | No persisted-query round trip (H2). |
| :105 | failure cannot set measured version. | preserves absent or old measured version as specified D-36. |
| :107 | mismatching fingerprint declines silence-only refresh. | normal pending-query stale-source handling outside this predicate. |
| :108 | missing document declines this supplemental predicate. | ordinary pending query handles initial analysis. |
| :112 | leadFrames11>framesMetered10 rejected by MeterResumeCodec range check. | v2 decode of intentionally invalid encoded state. |
| :114 | version first byte set1, codec VERSION2 rejects. | Not an actual legacy-job restart through AnalyzerTrackMeasurer. |
| PlaybackCoordinatorSilenceTest:43 | prepared A edges0/3000 from loader. | dispatch ActionPlay→resolveItem :1269–1279→engine.prepare; fake resolver has A. |
| :44 | preloaded B edges2000/0. | coordinator preloadNext→resolveItem→engine.preloadNext; fixture initializes restored state. |
| :47 |180000-3000-170999=6001>6000→no fade. | tick Playing + hasPreloaded; fake engine duration180000 (`FakePlayerEngine:238`). |
| :51 |180000-3000-(171650+600)=4750 in401..6000→fade4750. | real coordinator policy dispatch→real TransitionController in wrapper→fake beginCrossfade. |
| :52 | timingCalls≥2 | Each of the two ticks reaches engine policy because B is present and no prior fade at first tick. |
| :60 | skip=false | pre-start onPlaybackSettings retained, applied when engine obtained. |
| :63 | OFF remainder9000>6000→no fade at171000. | public settings then dispatch/tick. |
| :65 | skip=true | existing engine setter receives live update (:548). |
| :67 |180000-3000-171000=6000→fade6000. | same position after toggle, real coordinator tick. |
| :75 | B/preloaded ID=a | repeatAll one-item queue successor is same item. | ActionSetRepeat then ActionPlay; no missing queue call. |
| :78 | same ID treated repeatOne, so remainder9000→none. | coordinator :1385 compares track IDs. |
| :81 |174000→remaining6000, same ID ignores tail→fade6000. | real controller repeat flag true. |
| :88 | failed A profile→null | resolveItem catches/logs ordinary error :1275–1277 and uses resolved item. | dispatch completes with fake engine. |
| :89 | failed B profile→null | same boundary during preload. | no analysis launched. |
| :90 | log mentions silence | coordinator :1276 emits cause-bearing warning. | Does not assert throwable identity; adequate presence check only. |
| :96 | A prepared | resolver supplies A; null profile returns original item. | no wait for analysis. |
| :97 | B preloaded | same for B. | fake immediate loader; does not test slow database read latency. |
| :98 | A silence null | resolveItem :1279 preserves original. | no profile present. |
| :99 | B silence null | same. | same. |
| :110 | cancellation read started | suspend loader invoked via resolveItem. | dispatch.join waits child completion without throwing child's cancellation. |
| :111 | prepareAttempts empty | resolveItem rethrows CancellationException :1273–1274 before engine prepare. | Correct cancellation boundary, not swallowed default. |
| :112 | no ordinary failure log | same cancellation catch runs before generic catch. | Ensures refusal isn't treated as logged ordinary profile failure. |
| :119 | Native171000→9000 remainder>6000→null | PlayerEngine default 001 timing ignores profile. | direct fake/native default call, no Media3 cast. |
| :122 | Native174000→6000 remainder→6000 | duration180000≥2000; half cap90000, configured6000. | direct default API. |

PlaybackSilenceProfileTest is a single-function document-boundary test: lines20–21 return literal2000/3000 for current ok/silent documents; lines25–28 return null for missing/old/failed/stale; line39 rejects missing/negative/non-long/overflow edges; line42 preserves measured zero. Each maps directly to `LoudnessStore:308–315`; no coroutine, event, timeout, I/O or real-table assumption is involved.

### Host integration: Night mode, lifecycle and preferences

| Assertion site | Number/reference trace | Event/precondition and cost trace |
| --- | --- | --- |
| FloatChainNightModeTest:68 | OFF range22.4987747 dB, same plateau formula as device; rates48k/96k,1/2/3ch preserve RMS. | render→configure→feed actual sink with unthrottled test delegate. |
| :69–70 | ON≤15.7491423 dB; settled VOICE prediction≈11.6617 dB, device trace above. | 9 s PCM is CPU-only host work, not wall-clock playback. |
| :88 |2 s×48000×2=192000 samples | sink one-for-one transform/no compressor drain. |
| :91 | sample-by-sample expected is imported VOICE over explicit legacy normalized PCM; tolerance2e-6 after50 ms | GainProcessor -6 dB ramp first, then voice; actual sink order matches pipeline :44–46. Imported algorithm is external to adapter, not a second adapter invocation. |
| :93–94 | limiter config threshold literal-1 dB | LimiterConfigs active config; host clamp does not exercise Android effect. |
| :95 | limitedSamples>0 | initial overload4×normalization ramp×makeup exceeds ceiling; CaptureSink counts before clipping. |
| :97 |192000 samples | host clamp preserves count. |
| :98–100 | ceiling10^(-1/20)≈.89125094 | CaptureSink explicitly clamps to this value; tests placement model only, cannot fail for a disconnected real limiter. D-34 declares this limit. |
| :139 | settled A=.04×(enabled?1.9952623:1), tolerance1e-7 | direct shared lambda read once per sink chunk. M1: actual engine omitted. |
| :140 | B=.02×same factor | same shared lambda; below knee so zero gain reduction. |
| :147 | replacement ON=.07981049 | new sink constructed while flow true; same M1 limit. |
| :150 | replacement OFF=.04 exactly |200 ms feed exceeds50 ms ramp; bypass branch copies exact float. |
| :169 | synthetic0/25/100 ms must fail50 ms assertions | independently constructed curves; negative controls exercise assertion sensitivity, no production prerequisite. |
| :174 (assertRamp helper) | synthetic correct2400-frame ramp accepted | fixture frame0 convention differs from production's first advanced frame by1; allowed2-frame tolerance. |
| :184 | imported VOICE range meets70% | exact compressor invocation on fixture; adapter not involved. |
| :185 | fixed .5 gain retains22.4987747 dB range >70% | tests metric cannot confuse attenuation with compression. |
| :201 |4096×channels samples, raw bits equal explicit001 pipeline | rates44100/48000/96000; colored settings independently initialized in expected chain. Empty trim metadata, no pad/drop expected. |
| :213 |100 ms output=4410/4800/9600 frames×channels | samples one-for-one, input length contract. |
| :218–219 |step≤abs(wet-dry)/N+2e-7, N=2205/2400/4800 | production ramp :263–266 increments1/N per frame; empty input exits :239. Per-channel step and finite assertion. |
| :225–226 |0/10/25/40/50/75/99 ms expected fractions of50 ms | `floor(rate*ms/1000)`; first-frame offset≤1/N within2-frame tolerance. Spec D-34 linear blend, not implementation-generated oracle. |
| :228–229 | exact OFF/ON endpoint raw bits | after100 ms ramp ended; ON .04×10^(6/20); OFF copies dry. |
| :245 | required sink constructor exists | reflection fails assertion rather than selecting bypass; additive seam D-34. |
| :260 | empty handleBuffer=true | no PCM/ramp advancement; FloatChainAudioSink pending-output behavior. |
| :265 | feed accepted=true | CaptureSink consumes all bytes, intentionally no AudioTrack backpressure here. |
| :266 | no remaining input | success must consume the full input ByteBuffer, not just produce output. |
| :276 | render size=input.size | no voice lookahead/delay, one sample out/in. |
| :312 (finite window helper) | positive quiet/loud plateau RMS, finite log | same energy equation as device range. |
| FloatChainNightModeLifecycleTest:28 | flush result=.04×1.9952623 | sink flush calls NightModeProcessor.onFlush :282–288, resets reduction. First100 ms quiet after old overload; no old attack/release residue. |
| :33 |9600×3=28800 output samples | 96k/3ch reconfigure100 ms feed. |
| :34 |same below-knee makeup endpoint | new format flush clears compressor;50 ms blend complete. |
| :43 |raw bits of [-0,0,.125,-1.5] | reset then explicit OFF; exact bypass, no warm kernel. |
| :52 |first handleBuffer=false | delegate takes11 samples from4800-frame output, leaving pending output. |
| :56 |attempts<500 | ceil(4800/11)=437 delegate calls, ample deterministic margin, no wall-time timeout. |
| :58 |4800 total output | retry must drain original pending bytes without processing twice. |
| :59 |last pending ON sample=.04×makeup | setting changed during drain, but ON processing already done once. |
| :62 |next OFF block ends.04 | another4800 frames=100 ms;50 ms blend has ended. |
| :77 |empty feeds leave600 samples |600/48000=12.5 ms,10 empty blocks add0. |
| :80 |first reversal step≤full dry/wet difference/2400+2e-7 | reversal starts mix.25, so its smaller .25/2400 slope fits full bound. |
| :81 |every neighboring step same bound | production uses captured current mix to retarget; no discontinuity. |
| :82 |exact dry bits |2400 frames complete reversed50 ms blend. |
| :92 |feed=true | fake delegate consumes all except explicit backpressure test. |
| NightModePreferencesTest:25 | default false | PlaybackPreferences:74 uses absent key→false; real empty DataStore. |
| :29 |ON read true | setter edit :76–78, flow first waits persisted edit. |
| :30 |stored night_mode true | direct key read, independently named literal key. |
| :31 |settings=(7,7,true,true,true,default skip=true) | set seconds7 updates seconds/last; skip settertrue; night flag independent; crossfadeSettingsOf :116–124. |
| :40 |reopened ON true | first scope cancelled; runCurrent lets store close before new store over same temporary file. |
| :42 |after OFF false | edit persisted false through real store. |
| :43 |other settings unchanged | same7/7/defaults; verifies no coupling. |
| SilencePlaybackPreferencesTest:22 | default skip true | crossfadeSettingsOf :124 absent key→true. |
| :26 |saved skip false | setter :96–98, real DataStore edit. |
| :32 |reopen=(7,7,true,true,false,false) | last two fields manual-skip=false, silence-skip=false; defaults/persisted values traced above. |
| :33 |night ON retained | independent night key persisted before first scope closed. |
| :35 |skip restored true | setter and fresh flow first. |

Host scope lifetime: both preference tests cancel their temporary store scopes in finally and run the deterministic test scheduler before reopening. Their `first()` awaits are satisfied by DataStore initialization/edits which they themselves invoke; no Android service is missing. No arbitrary wall-time sleep is used. Host sink and analysis fixtures do O(frames×channels) CPU work; they make no real-time claim for a four-minute source. TransitionControllerSilenceTest drives a single policy object directly; its expected table is literal spec/001 arithmetic (180000-3000-6000=171000; half cap4000 for8s;401 inclusive/400 excluded), with synchronous command returns and no awaited event or fixture file.

## 5. Suggested follow-up tests

| Risk | Missing test / expected assertion | Priority |
| --- | --- | --- |
| SC-008 cannot reach measurement | NightModeDeviceTest long backpressured render completes under the actual enclosing call bound; retain exact byte counts and70% oracle. | Critical: H1 |
| Real Room consumer contract differs from helper tests | LoudnessStoreTest legacy/current/versioned refresh cases, plus transactional silenceProfile read with active/fingerprint change. Current measured results must leave pending; legacy results must refresh exactly once. | Critical: H2 |
| Independent fitting hides an audible gap | SilenceCrossfadeDeviceTest common-output-clock overlap and delayed-B negative control. A forced >100 ms gap must fail. | Critical: H3 |
| App setting can be disconnected while sink tests pass | Real-engine saved ON/live A/B/replacement preference propagation and close cancellation. | Important: M1 |
| Frame seek conversion at checkpoint boundary | 44.1 kHz nonintegral-microsecond resume, >192 kHz case; exact resumed frame sequence/profile against uninterrupted PCM. | Important: M2 |
| Disabled switches can still write preferences | Playback settings UI: native engine disables Night mode; crossfade OFF disables silence option; saved values survive engine changes and disabling. | Nice-to-have; no demonstrated UI defect |

| Severity | Count |
| --- | ---: |
| CRITICAL | 0 |
| HIGH | 3 |
| MEDIUM | 2 |
| LOW | 0 |

VERDICT: NEEDS CHANGES
