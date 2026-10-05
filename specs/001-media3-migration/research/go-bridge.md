# Bridging WaxFlow (pure-Go decoders) into Sistrum's Media3 engine

Source: external research (Arena.ai), 2026-10-04. Condensed. The researcher
ran NO benchmarks; all performance figures are cited or estimated. Items
marked [unverified] were not confirmed. Note: the original report recommends
Jellyfin's decoder as a fallback; that is OUTDATED for Sistrum (Jellyfin was
removed from the plan).

## Verdict
| | (a) gomobile | (b) Go -> WASM in Chicory | (c) Hand-port to Kotlin |
|---|---|---|---|
| Prior art, CPU-heavy on Android | yes, production (Tailscale, WireGuard-style Go, audio DSP apps) | none for audio | none for Go->Kotlin audio |
| Real-time stereo decode | safe, est. 40-250x realtime | interpreter fails by orders of magnitude; AOT unproven | safe class (JVM JIT decoders) |
| APK cost | +~10-30 MB per ABI | >= 13.7 MB wasm + runtime + unknown dex | ~0 |
| Main risk | "experimental" label; one Go AAR per app; 16 KB pages; Go crashes have no Java stack | experimental Android compiler; >200 MB heap to compile a small module on device | ~22.5k LOC to port; WaxFlow has no tagged releases |

Recommendation: (a) gomobile for 002. (b) not for playback; optional
loudness-only experiment. (c) later, codec by codec, after WaxFlow tags
releases.

## (a) gomobile — evidence
- Production users: tailscale-android (Makefile runs gomobile bind with
  `-ldflags "-linkmode=external -extldflags=-Wl,-z,max-page-size=16384"`,
  produces libtailscale.aar / libgojni.so), status-go (mobile/), go-ethereum
  mobile, AndroidLibXrayLite (50.4 MB AAR all ABIs, "16kb-support" releases),
  Cwtch (16 KB write-up), RBA Consulting (audio streams + FFT DSP apps; "only
  primitives and byte slices", protobuf framing). WireGuard Android uses
  hand-rolled cgo+JNI instead of gomobile; userspace wireguard-go sustains
  ~15 MB/s through the bridge on phones.
- Audio Go on Android (output side): ebitengine/oto (Oboe/AAudio),
  gominiaudio (keeps the realtime callback out of the Go runtime).
- Supported types (gobind): signed ints, floats, string, bool, []byte (by
  reference, mutable), funcs, interfaces, structs of those. No arbitrary
  slices/maps/unsigned. Panics crossing the boundary kill the process.
- Buffers cross by JNI copy (GetByteArrayElements / NewByteArray +
  SetByteArrayRegion). Keep calls coarse: per packet in, per PCM block out.
  Stereo 16-bit 48 kHz is ~0.19 MB/s, far below the ~15 MB/s precedent.
- Only ONE gomobile AAR per app (libgojni.so and go.Seq collide: golang/go
  #35744, #12245, #56567). Bind all packages into one AAR.
- 16 KB pages (Play requirement for Android 15+ targets since 2025-11-01):
  build with NDK r28+ and -androidapi 21+ (golang/go #81358), and/or the
  Tailscale linker flag. Verify with llvm-objdump and the 16 KB emulator.
- R8: gomobile emits keep rules (-keep class go.** etc.) into the AAR.
- Runtime: Go threads coexist with ART in shipping apps; Go panics give no
  Java stack (golang/go #69101): plan native crash reporting. Use
  debug.SetMemoryLimit / SetGCPercent and buffer pooling. Startup cost
  unmeasured [unverified].
- Maintenance: golang/mobile still labelled experimental, no tagged releases,
  but active commits in Aug-Sep 2026 and maintainer triage.
- Size data: libgojni.so 27.4 MB arm64 for a sync facade (kdb PR#95); 9-12 MB
  arm32 (hermes-vox). WaxFlow-subset AAR unmeasured: est. 10-25 MB per ABI
  before stripping.

## (b) WASM + Chicory — evidence
- Chicory (Apache-2.0, 1.7.5 on 2026-03-24) runs its tests on Android
  (android-tests/, API 33+). mcp.run runs Wasm servlets on Android via it.
- Android compiler port: all spec tests pass, but compiling a small module on
  device used >200 MB heap; chicory-compiler-android is experimental.
- Only public standard-Go-in-Chicory case: cel-go via GOOS=wasip1 in Quarkus
  (server-side), 13.7 MB wasm module; full stdlib and reflection work.
- Performance: Chicory interpreter on Android ~3-4 orders of magnitude slower
  for CPU-heavy work (chicory #901). Go-wasm reaches ~20% of native even in
  native-code Wasm runtimes (golang/go #65440). No audio-decode-in-Chicory data
  anywhere.
- TinyGo is a subset (partial reflect, scheduler limits); WaxFlow targets
  standard Go 1.26, so TinyGo would mean another port.

## (c) Kotlin port — evidence
- Nobody has ported a Go audio codec to Kotlin/Java. Non-audio precedent:
  projectnessie/cel-java (independent port of cel-go, tracks the spec).
- JVM audio decoders (JOrbis, jflac, JMAC) were real-time with a JIT, but
  their maintenance decayed.
- Size of WaxFlow non-test code Sistrum needs: wavpack ~2.5k, ape ~2.1k,
  wma ~3.5k, wmalossless ~1.5k, wmapro ~2.8k, wmavoice ~7.0k, musepack ~2.1k,
  dsp/loudness ~0.7k, audio ~0.3k lines: ~22.5k total.
- WaxFlow helps a port: differential-vs-ffmpeg quality gates, SHA-pinned
  corpora, clean-room policy (MAINTENANCE.md). But no tagged releases yet.

## Media3 specifically
Nobody has bridged a Go codec into ExoPlayer/Media3. The slot is Media3's
decoder-extension pattern (SimpleDecoderAudioRenderer + JNI decoder, as in
decoder_flac / decoder_ffmpeg) plus per-container Extractors. WaxFlow's
packet-in / PCM-out API fits SimpleDecoder.

## WaxFlow scope facts (docs/quality-gates.md)
- No DSD decoder. WavPack refuses hybrid, float, DSD, >2 channels. APE refuses
  32-bit, float, >2 channels and stream versions outside 3950-3990.
- CI gates (desktop-class core): WavPack >= 200x realtime, WMA 1/2 >= 500x
  (1226x measured), APE >= 100x (251x), Musepack >= 300x, FLAC >= 300x,
  MP3 >= 150x, Opus >= 150x, Vorbis >= 80x, ALAC >= 100x. Loudness (BS.1770-4)
  within 0.15 LU of ffmpeg ebur128 and passes EBU Tech 3342.
- Estimated phone derate 2-5x per core: still far above 1x, including two
  decoders at once for crossfade.
