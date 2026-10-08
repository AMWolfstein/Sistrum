# Pure Kotlin codecs

Kotlin/JVM only, JDK 21, no Android, Media3, native decoder or application
integration. Package paths mirror WaxFlow under `me.misa198.airmedy.codecs`:
`codec/wavpack`, `container/wv`, `bitstream`, and `audio`. Future codecs can sit
beside WavPack without changing this module's platform requirements.

Port source: https://github.com/AMWolfstein/WaxFlow at
`b7857aff88820ad37936026421d1641e64611dbe`. The clean external clone used here is
`/tmp/sistrum-waxflow-oracle/oracle-work/src`. See `THIRD-PARTY-NOTICES` for the
WaxFlow MIT license and underlying WavPack 5.9.0 BSD-3-Clause attribution.

## API

```kotlin
import java.nio.ByteBuffer
import me.misa198.airmedy.codecs.container.wv.Wv

val stream = Wv.open(byteBuffer) // uses its remaining region; also accepts RandomAccessSource
val info = stream.info // sampleRate, channels, bits, validBits, totalSamples, samplesExact
while (true) {
    val pcm = stream.decodeBlock() ?: break
    // Consume pcm.samples[0 until pcm.frames * pcm.channels], interleaved Int PCM.
    // Samples are right-justified at pcm.bits; position is the first frame number.
    // The Buffer and its IntArray are borrowed and reused by the next decode.
}
stream.seekSample(12345) // next block starts at the exact requested sample
```

Decodes native version-4 WavPack lossless integer mono/stereo, including
false stereo, joint stereo, extended integers and custom sample rates. Sample
CRC and extension CRC are checked in the same order as WaxFlow. Encoded-byte
checksums are available separately through `verifyBlockChecksum`, returning
null when absent; WaxFlow intentionally does not check them during decode.

The port preserves entropy medians and unsigned carry arithmetic modulo 2^32,
the two weight-application forms, distinct mono/stereo term-18 expressions,
metadata order, refusal order, bounded boundary confirmation/resynchronization,
trailer recognition, tail length recovery and sample-index bisection. Strict
mode escalates damage warnings but not informational notes, as in WaxFlow.

Adaptations required by the JVM API: reusable headers/cursors replace Go structs,
and interleaved PCM replaces the planar pipeline buffer. No encoder, muxer, tag
editor, pipeline registry, pool or DSP is included. Trailing APEv2/ID3 recognition
needed to delimit audio is included.

`RandomAccessSource` exposes `length: Long` and `read(position: Long, buffer: ByteBuffer): Int`.
Production opens only seekable sources or ByteBuffers. `FileChannelSource` uses
positional reads without moving the caller-owned channel. ByteBuffer input uses
its remaining region and must remain immutable. File offsets use Long; bounded
128 KiB read-ahead windows and a reused encoded-block buffer replace whole-file
loading. The InputStream adapter lives only in tests and spools to a temporary
file. The caller owns production source lifetime.
The existing maximum block-sample cap determines scratch capacity at open
(786432 bytes mono / 1572864 bytes stereo). Normal block decode allocates no
headers, metadata slices, bit readers or PCM buffers. Warnings/errors may allocate.

## Tests and benchmark

Fixtures are read directly from
`androidApp/src/test/resources/waxflow/oracle-corpus-fixtures.tsv`, never copied.
Tests validate the pinned commit and each source hash before running. Missing
fixtures/corpus fail the run; tests never skip because data is unavailable.
Configure the external corpus with `WAXFLOW_CORPUS` or `-PwaxflowCorpus`:

```bash
./gradlew :codecs:test -PwaxflowCorpus=/tmp/sistrum-waxflow-oracle/corpus
./gradlew :codecs:benchmark -PwaxflowCorpus=/tmp/sistrum-waxflow-oracle/corpus
./gradlew :androidApp:assembleDevDebug
```

All 50 `.wv` entries and 17 `.wvc` entries are covered. Tests require exact
packed little-endian PCM SHA-256, frame counts, stream info and refusal messages.
They also check borrowed buffers, InputStream/direct-region input, exact seeking
(including EOF and backwards seeks), named unsupported shapes, unknown stream
length, strict warnings and separate block checksum verification. The legacy
`vers-397-hybrid.wvc` has no recognized magic: its oracle refusal is at the
format-registry level, tested by sniffing and asserting the native demuxer
also refuses it. There is no format registry in this module.

The benchmark writes `build/reports/wavpack-benchmark.tsv`. It records every
`.wv` fixture, marking refusals and oracle mismatches explicitly. Successful
files have five full-decode warmups and five measured decodes; results are the
median. RTF is decode seconds / audio seconds, so values below 1 are faster
than realtime. Input loading, opening, seeking, hashing and PCM byte packing
are outside the timer. Thread-allocated bytes are measured for each full decode.
This is a laptop proxy, not the later phone performance gate.

## Validation (2026-10-08)

The fork RIFF recognition fix and regenerated oracle now classify all eight
legacy streams as unsupported versions. Kotlin recognizes the same legacy
headers and uses the same unsupported-version message in codec and container.
Native Go's container still filters raw out-of-range version headers through
its sync predicate; Kotlin's unified error is the explicitly requested API change.

A synthetic `ID_WVX_NEW_BITSTREAM` maximum-width vector covers full-width,
partial-width and no-extension reads, including negative samples. Its reference
PCM and hash were independently decoded by the pinned Go CLI; regenerate it
with `python3 codecs/tools/generate-max-width.py /outside/repo/bin/waxflow`. The corpus fixtures are not copied.

Before-change benchmark: AMD Ryzen 5 3500U, OpenJDK 21.0.12.1, 20 supported files,
RTF 0.003626–0.015825 (median 0.010530), median allocation 0 bytes per file.
After-change RTF is 0.003555–0.015666 (median 0.010392), with zero median
decode-loop allocation for each supported file. The paired median after/before
ratio is 0.9955; this rough run does not establish a meaningful speed difference.
Saved before/after reports and per-file comparisons are in `benchmarks/`.

Kotlin: 211 passed, zero failures or skips; shared logic: 39 passed. Android
`assembleDevDebug` succeeds. The broader Android unit suite has 899 passes,
seven gain-ramp failures and one existing skip. Full Go root tests have 5663
leaf passes, two baseline IMA differential failures and 273 existing skips.
Relevant RIFF/WavPack format tests (511 leaf cases), suite conformance (56),
and the separate oracle module (130) all pass. See `VALIDATION.md` for commands
and scope. No failing test was removed, disabled or weakened.

### The 30 refused `.wv` files, grouped by actual first refusal

Paths below are relative to `wavpack-test-suite-2.0/test_suite/`.

**Hybrid (17)** — hybrid refusal precedes multichannel/float checks:

- `corruption/hybrid_corrupt.wv`
- `hybrid_bitrates/1024kbps.wv`
- `hybrid_bitrates/128kbps.wv`
- `hybrid_bitrates/160kbps.wv`
- `hybrid_bitrates/24kbps.wv`
- `hybrid_bitrates/256kbps.wv`
- `hybrid_bitrates/320kbps.wv`
- `hybrid_bitrates/32kbps.wv`
- `hybrid_bitrates/384kbps.wv`
- `hybrid_bitrates/48kbps.wv`
- `hybrid_bitrates/512kbps.wv`
- `hybrid_bitrates/64kbps.wv`
- `legacy/vers-40-hybrid.wv`
- `legacy/vers-480-hybrid.wv`
- `num_channels/multichannel-6.wv`
- `special_cases/cue_sheet.wv`
- `special_cases/hybrid_test.wv`

**Float (1)**: `bit_depths/32bit_float.wv`.

**DSD (2)**: `bit_depths/1bit_dsd.wv`, `corruption/dsd_corrupt.wv`.

**More than two channels (0 as the first refusal)**: the six-channel file above
is refused as hybrid first. Standalone multichannel refusal is covered with
source-derived flag and channel-metadata tests.

**Unsupported stream version (8)**:

- `legacy/vers-10.wv`
- `legacy/vers-20.wv`
- `legacy/vers-20-lossy.wv`
- `legacy/vers-30.wv`
- `legacy/vers-30-fast.wv`
- `legacy/vers-30-lossy.wv`
- `legacy/vers-397.wv`
- `legacy/vers-397-hybrid.wv`

**Corrupt sample CRC (1)**: `corruption/lossless_corrupt.wv` —
`block at sample 22050 fails its CRC`.

**Unrecognized native framing (1)**: `legacy/vers-480-sfx.wv` —
`not a WavPack file` (self-extracting executable).

### Remaining coverage limits

Inspection of the successful native files found 1388 audio blocks, including
16 false-stereo blocks and all supported decorrelation terms (-3..-1, 1..8,
17, 18). These paths are covered by the bit-exact corpus tests. Dedicated
unsigned-overflow stress vectors beyond the corpus were not added.
Hardware/phone testing is deferred.


## APE — Media3 gap and port decision

The app pins Media3 **1.11.1** (`gradle/libs.versions.toml`). Before porting APE,
`jar tf` and `javap -c -p DefaultExtractorsFactory` on the resolved
`media3-extractor-1.11.1.aar` confirmed there is no APE extractor. The
[official progressive format list](https://developer.android.com/media/media3/exoplayer/supported-formats)
also has no APE container. Media3 has no bundled pure JVM APE decoder; optional
FFmpeg support is native and does not supply the missing APE extractor.
**Decision: port `container/apen` and `codec/ape`.** No Media3 or app integration
is added by this module task.

`Ape.open(RandomAccessSource)` and `Ape.open(ByteBuffer)` expose parsed stream
info, borrowed 4096-frame interleaved integer PCM chunks, and exact sample seeking.
Every frame is decoded and CRC-checked before exposing any of its chunks.
This includes the source's interim 24-bit retry, word-reversed frame reads,
both range models, neural filter cascades and predictor arithmetic. Supported
versions are 3950–3990, mono/stereo integer 8/16/24-bit, all five levels.
Fixed scratch buffers are allocated at open. Sources and file handles remain
caller-owned; seeking does not reload the whole source.

[Monkey's Audio SDK 13.26 license](https://www.monkeysaudio.com/license.html)
is BSD-3-Clause. The exact SDK archive license and WaxFlow's MIT license are in
`THIRD-PARTY-NOTICES`. JVM/API adaptations are recorded in `SISTRUM-PATCHES.md`.

The public oracle contains ten decoded APE files: the original two plus eight
free WaxFlow test-suite vectors from the pinned commit. Audio remains outside
this repo. All five levels, 8/16/24-bit, trailers and multi-frame seeks are
covered; expected PCM comes only from the regenerated Go oracle.

| APE corpus refusal reason | Count |
|---|---:|
| None (all ten decoded) | 0 |

Named version, float, width, channel and compression-level refusals are tested
separately with source-derived header mutations; strict descriptor warnings and
negative seeks also have contract tests. These are not fabricated oracle rows.

Run `./gradlew :codecs:benchmarkApe`; the per-file report is
`build/reports/ape-benchmark.tsv`.

APE validation: **243 full-module tests passed, zero failures/skips**, including
32 APE cases. `assembleDevDebug` passes. Benchmark across ten files:
**RTF 0.006376–0.090657**, median 0.034397; every file
measured **0 median allocated bytes per full decode**. Saved report: `benchmarks/ape.tsv`.


## ALAC — Media3 gap and port decision

Media3 **1.11.1** already provides `Mp4Extractor`. Inspection of the resolved
extractor's `BoxParser` bytecode confirms `audio/alac`, parsing the nested `alac`
box, skipping its 12-byte box/full-box header, and putting the remaining
ALACSpecificConfig into `Format.initializationData`. The module accepts that
canonical 24-byte cookie, including longer cookies with trailing layout data.
[Media3's official format documentation](https://developer.android.com/media/media3/exoplayer/supported-formats)
lists ALAC under the optional **native FFmpeg extension**; core Media3 has no
bundled ALAC decoder. **Decision: port only `codec/alac`; no MP4 container port.**
The [Apple reference](https://github.com/macosforge/alac) is Apache-2.0.

`Decoder(Config(cookie)).decode(packet)` returns reused interleaved Int PCM.
`Alac.open(cookie, RandomAccessSource, PacketIndex)` supplies bounded positional
packet reads and exact sample seeking. A ByteBuffer overload is available.
The existing extractor supplies packet locations, sample timestamps and total
samples; this module does not parse MP4 or depend on Android/Media3. Index arrays
and sources remain caller-owned and immutable. Each packet is independent;
seeking finds its sample interval and discards the prefix after decoding.
The source Golomb coder, adaptive FIR cascade, matrix, escape, shift-off and
refusal order are retained. Scratch and packet buffers are reused.

Six files in the public oracle cover progressive and fragmented input, mono and
stereo, partial frames, and 16/24/32-bit samples. Twenty-bit decoding is ported
but is not exercised by these corpus files. Test-only packet extraction uses
pinned WaxFlow's existing MP4 demuxer, outside the repository:

```bash
SISTRUM_WAXFLOW_DIR=/tmp/sistrum-waxflow-oracle/oracle-work \
  bash scripts/waxflow-alac-packets.sh /tmp/sistrum-waxflow-oracle/corpus
./gradlew :codecs:test :codecs:benchmarkAlac
```

Set `WAXFLOW_ALAC_PACKETS` or `-PwaxflowAlacPackets` for a different external
packet directory. Missing packets fail tests. The packet dump embeds the original
source hash; PCM expectations still come solely from the committed oracle.
All MP4 demuxing is test tooling, not shipped Kotlin container code.

| ALAC corpus refusal reason | Count |
|---|---:|
| None (all six decoded) | 0 |

Cookie channel-count/frame/width and unsupported-element reasons have separate
source-derived contract tests. File-backed input, partial reads and source offsets
beyond 2 GiB are checked against the same PCM oracle. Deviations are recorded in
`SISTRUM-PATCHES.md`.

ALAC validation: **263 full-module tests passed, zero failures/skips**, including
20 ALAC cases. `assembleDevDebug` passes. Across six files, **RTF
0.013827–0.034957**, median 0.026867; every file measures
**0 median allocated bytes per full decode**. Report: `benchmarks/alac.tsv`.


## Musepack decision and validation (2026-10-08)

Media3 **1.11.1** has no Musepack extractor or bundled Musepack decoder. The
resolved `media3-extractor-1.11.1.aar` class list and the bytecode of
`DefaultExtractorsFactory` contain neither an MPC extractor nor MP+/MPCK
recognition. Google's [supported-formats documentation](https://developer.android.com/media/media3/exoplayer/supported-formats)
also lists no Musepack progressive container. Decision: port both
`codec/musepack` and `container/mpc`, covering SV7 and SV8. No Media3 dependency
is introduced. The reference libmpcdec license in the pinned r475 archive is
BSD-3-Clause; its archive SHA matches WaxFlow's vector manifest. Encoder and
chapter-editor source from that archive was not consulted.

`container.mpc.Musepack.open(RandomAccessSource)` (also ByteBuffer) exposes
`info`, reused interleaved `FloatBuffer` blocks and `seekSample(Long)`. Samples
are IEEE float32, matching the oracle WAV data. SV7 little-endian words are
realigned into bounded packets; SV8 blocks use their header frame count.
Synthesis delay, beginning silence, decay frames and exact trailing counts
follow the source. SV7 seeks restore scalefactors/noise state from 32-frame
checkpoints; SV8 seeks restore noise state, with scanning omitted when the
encoder declares PNS off. Both pre-roll the filter before the target.

All **32 Musepack files** decode bit-exactly, with exact backward/forward seeks,
reused PCM buffers, partial source reads and direct ByteBuffer regions. Corpus
refusals: **0**. Contract tests retain the named SV4/5/6 and >2-channel refusals.
No tag/chapter editing or metadata API is included; framing still skips those
packets and peels trailers to delimit audio.

Final full module gate: **361 tests, 0 failures, 0 skipped**;
`:androidApp:assembleDevDebug` passed. `:codecs:benchmarkMusepack` results are
saved in `benchmarks/musepack.tsv`: RTF **0.002839–0.022983**, median **0.006913**;
median decode-loop allocation is **0 bytes on every file** (same laptop/JVM and
five warmups/five measured decodes as the other codecs). JVM allocation tracing
identified allocating Kotlin stepped ranges; explicit loops preserve Go's
iteration order and remove those range objects.

## ADPCM / G.711 decision and validation (2026-10-08)

The resolved Media3 **1.11.1** `WavExtractor` selects its own
`ImaAdPcmOutputWriter` for WAV format 17. Formats 6/7 select a passthrough
writer with `audio/g711-alaw` / `audio/g711-mlaw`; `BoxParser` also recognizes
MP4 `alaw` and `ulaw`. These variants already have a Media3/platform path.
There is no MS ADPCM output writer, no `ima4` sample-entry branch in
`BoxParser`, and no AIFF extractor in `DefaultExtractorsFactory`.
Decision: port **MS ADPCM and QuickTime IMA4**, including their missing WAV,
AIFF-C and IMA4 MOV container branches. Do not port WAV IMA ADPCM.

Android's published software decoder capabilities limit G.711 to
[at most six channels in this AOSP listing](https://android.googlesource.com/platform/frameworks/av/+/3a7fe554a2958dd7293c90b9090ba4972b17ab4b/media/libstagefright/data/media_codecs_sw.xml);
[the current Codec2 listing](https://android.googlesource.com/platform/frameworks/av/+/master/media/libstagefright/data/media_codecs_google_c2_audio.xml)
limits both laws to one channel. WaxFlow supports eight. Therefore the missing
**7/8-channel G.711 decoder fallback** is included. `codec.g711.G711.open`
takes law/rate/channels and an extractor-supplied source region, rather than
duplicating Media3's WAV/MP4 extractors. The same scalar kernel naturally works
for fewer channels; app routing should prefer the existing decoder wherever its
reported capabilities suffice. This is a capability inference, not a device test.

All entry points use `RandomAccessSource` or ByteBuffer. ADPCM reads bounded
compressed blocks; G.711 reads bounded 4096-frame regions. Both reuse their
interleaved 16-bit PCM buffers. Seeking resets/pre-rolls QuickTime predictor
carry, lands at MS blocks, or addresses G.711 samples directly. MP4 indexing
reads metadata at open; it never loads all compressed audio. The IMA4 MOV port
is deliberately scoped to that sample entry, not a second ALAC extractor.

Six ADPCM and six G.711 files (two original mono, four derived multichannel) decode bit-exactly with exact seeks,
reused buffers, file-backed input and direct-buffer regions. All twelve decode;
**corpus refusals: 0 for both families**. MS >2-channel and invalid block
geometry refusals are checked separately against source messages.

### Investigation of the two existing Go IMA differential failures

At unchanged pin `b7857af`, `go test ./tests -run
'TestFixturesDecodeDifferential/sine-ima' -count=1 -v` fails on
`sine-ima.wav` and `sine-ima-stereo.wav` at interleaved sample indices 1 and 2;
both QuickTime IMA4 cases pass. The source explicitly targets FFmpeg **8.0.1**
for WAV IMA's multiply-style step. This laptop has **n9.0.2**. A black-box
comparison of that binary's output with both arithmetic forms finds zero
mismatches with shift/add, versus 7,509 mono and 21,564 stereo mismatches with
multiply. No FFmpeg source was consulted. This establishes a reference-version
mismatch; it does not justify changing pinned WaxFlow PCM behavior. No fork fix
or pin bump was made. The focused Go ADPCM/G.711 unit suites pass (60 test
records including subtests); these two differential failures remain documented.

Final gate: **399 codecs tests, 0 failures, 0 skipped**, including 20 ADPCM
and 18 G.711 cases. `:androidApp:assembleDevDebug` passes. Benchmarks run
sequentially after the test gate on the same laptop/JVM, with five warmups and
five measured full decodes per file:

| Family | Files decoded | Refusal reason / count | RTF range | Median RTF | Median loop allocation per file |
|---|---:|---|---|---:|---:|
| MS ADPCM / QuickTime IMA4 | 6 | None / 0 | 0.000345–0.005070 | 0.004233 | 0 bytes |
| G.711 A-law / mu-law | 6 | None / 0 | 0.000141–0.001076 | 0.0009295 | 0 bytes |

Reports: `benchmarks/adpcm.tsv`, `benchmarks/g711.tsv`. Timings exclude open,
file loading, seek setup and checksum packing; bounded memory reads and the
entire decode walk are included. These are JVM proxies; the phone gate remains
future work. No application playback integration or WMA-family port is included.

## Expanded lossless corpus (2026-10-09)

`scripts/waxflow-expand-lossless-corpus.py` generates original deterministic
triangle/noise signals outside the repository. The generated audio is dedicated
to **CC0-1.0**; no third-party recording is used. Twenty modern APE files cover
the full cross-product of levels **1000/2000/3000/4000/5000** (fast, normal,
high, extra high, insane), **16/24-bit**, and **mono/stereo**. They are encoded
with the official [Monkey's Audio 13.26 SDK](https://monkeysaudio.com/files/MAC_1326_SDK.zip)
(BSD-3-Clause), archive SHA-256
`3fdb516db15cc754eb2db1d255e405a8142fbb115eccdf51b0fa07b84305b6ac`.
Build its CMake console target outside Sistrum and pass its executable to:

```bash
python3 scripts/waxflow-expand-lossless-corpus.py /tmp/sistrum-waxflow-oracle/corpus /tmp/sistrum-waxflow-oracle/encoders/mac-1326/build/mac
```

A twenty-first APE vector is a genuine **3.97** mono/16-bit silence stream,
constructed directly from pinned WaxFlow's old-header and special-silence frame
model, including its CRC and range-coder terminator. The generator independently
decodes it with the official 13.26 decoder and checks the original zero PCM.
It tests legacy framing and silence; it does **not** exercise the old non-silent
entropy/predictor path. No historical SDK code is used or included.

Five ALAC vectors use the same original signals at **24 bits**, with
**1/2/4/6/8 channels**, encoded by the installed FFmpeg **n9.0.2** binary as a
black-box tool. Explicit layouts (mono, stereo, 4.0, 5.1, 7.1(wide)) prevent the
encoder's automatic selection from reducing the eight-channel input to seven.
No FFmpeg implementation source is consulted. WaxFlow deliberately refuses
multichannel ALAC because its WAV channel remap is unimplemented; Kotlin must
retain those exact refusals. These are refusal vectors, not evidence that the
port plays multichannel ALAC.

All compressed audio stays in external `waxflow-ape-tests/generated/` and
`waxflow-alac-tests/generated/`. Run the existing oracle and packet-extraction
scripts afterward; only Go produces expected PCM hashes. Packet extraction
accepts an unavailable dump only when the Go demux error exactly matches the
committed oracle refusal. Tests independently read refused files' actual cookies
and compare the Kotlin refusal text with that oracle.

Expanded gate: **470 tests passed, 0 failures/skips**; `assembleDevDebug` passed.
No production decoder changes were needed.

| Codec | Successful files | Refusal reason | Refused files | RTF range | Median RTF | Median decode-loop allocation |
|---|---:|---|---:|---|---:|---:|
| APE | 31 | None | 0 | 0.000547–0.296532 | 0.020283 | 0 bytes per file |
| ALAC | 8 | Only mono/stereo supported: channel counts 4, 6, 8 | 3 | 0.013656–0.041438 | 0.027937 | 0 bytes per decoded file |

The expanded per-file measurements replace `benchmarks/ape.tsv` and
`benchmarks/alac.tsv`; the earlier measurements remain in git history.
