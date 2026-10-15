# Signal-path audit

Reviewed `a18bbcb50e4c37c675d91846d8fd7d2d356b3109^..d2b9669`, including the M2 spec/plan, sink and analysis contracts, and D-34 through D-40.

- Media3 playback: provider extractors/platform renderers and the Kotlin PCM/ALAC renderer routes converge on `FloatChainAudioSink`: encoder trimming → PCM-to-float conversion → normalization/equal-power fade gain → width/EQ/preamp → new per-player voice compressor → float AudioTrack → shared-session mixer/limiter. Both A/B players receive the same volatile Night-mode reader. M2 does not change codec sample packing, channel mapping or resampling. OFF takes the byte-copy bypass; flush discards compressor state and restarts the sample-rate-derived 50 ms blend.
- Automatic transitions: stored, versioned, fingerprint-matched silence edges reach PlaybackItem through the coordinator. Media3 subtracts outgoing tail silence for preparation/trigger timing and restarts incoming playback at its leading edge. Manual skips and same-item repeats use position zero. The incoming processing-clock origin is updated with that restart position.
- Analysis: OfflineDecoder uses the provider extractors/renderers with a separate CaptureAudioSink, trimming/converting to float without playback DSP. Borrowed PCM is copied into the bounded TrackAnalyzer pool; the meter thread reads it for loudness and the new silence accumulator. Edge counts survive checkpoints and become millisecond profile fields only after completed decoding. Analysis does not write into playback buffers. Mood feature integration belongs to later milestones.
- Native: no new compressor or silence policy is inserted; the new engine-interface default retains the existing timing helpers. Native internals and unchanged codec algorithms were not independently revalidated in this M2 review.

## Findings

[HIGH] androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/media3/Media3Engine.kt:1056 — Skipping an incoming lead can make the incoming track end before its fade-in completes, cutting off the still-audible outgoing track. Concrete input: two 10-second, stereo, 48 kHz PCM16 WAVs, A audible throughout and B containing 9 seconds of digital silence followed by 1 second of audio; current profiles, silence skipping ON, 4-second crossfade. The outgoing-only duration calculation at lines 1038–1043 arms both curves for roughly 4 seconds, then this code seeks B to 9000 ms. B has only 1 second left: its fade-in reaches approximately sin(π/8) = 0.383 (−8.34 dB), while A still has approximately cos(π/8) = 0.924 gain. B's EOF is published immediately by Slot's STATE_ENDED listener (line 100); PlaybackCoordinator.tick handles that event by advancing the queue (line 510), and preparation/stop snaps and releases A. Thus valid incoming audio stays attenuated and A is abruptly truncated well before its intended fade endpoint. Before lead skipping, B's original 10-second timeline outlasted the fade. Fix: validate the playable incoming remainder after the proposed seek before arming either curve, and handle a remainder shorter than the fade with an explicit safe fallback or coordinated timing policy. Include a regression that exercises EOF through the real transition/event path, plus an entirely silent incoming track whose lead equals its duration. The current duration clamp only bounds the seek to EOF; it does not ensure any fade frames remain.

[MEDIUM] androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/media3/FloatChainAudioSink.kt:255 — Night mode creates a new FloatBuffer view for every nonempty processed buffer via `asFloatBuffer()`, including after the compressor and planar arrays have warmed up. Concrete input: continuous stereo 48 kHz float PCM with Night mode ON; both active players do this during crossfade. This adds recurring heap allocation on the render path, contrary to checklist item 8, and creates avoidable GC pressure on the two-decoder path. No actual underrun is claimed without device measurement. Fix: use absolute `ByteBuffer.getFloat(base + sampleIndex * 4)` reads while retaining the starting byte position, or another reusable representation, and verify steady-state allocation with Night mode ON.

## Verification and limits

Commands used: read-only `git status`, `git log`, `git diff`, `rg`, `cat`, `sed`, `nl`, and `ls`. Only this report was written. No code/tests were edited; no Gradle, adb, agents, Git mutations, or Graphify operations were run.

The requested range contains T014–T020 commits. T021 is unchecked in tasks.md, and HANDOFF records the new device tests as NOT RUN. Their source was reviewed, but execution, SC-008/SC-009 device results, post-session limiter PCM, and live engine-to-factory preference propagation could not be verified. D-40 already documents the pre-session capture limitation; it is not an additional finding. Compressor timbre and transition feel need a listening check on the owner's deferred list.

VERDICT: NEEDS CHANGES

| Severity | Count |
| --- | ---: |
| CRITICAL | 0 |
| HIGH | 1 |
| MEDIUM | 1 |
| LOW | 0 |
