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
