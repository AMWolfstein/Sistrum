# libwavpack oracle and Kotlin port

Port source and encoder/decoder oracle: upstream **libwavpack 5.8.1**, exact tag
commit `4827b9889665b937b6ed71b9c6c0123152cd7a02`, from
https://github.com/dbry/WavPack/tree/5.8.1. The upstream BSD-3-Clause copyright,
conditions and disclaimer are reproduced in `codecs/THIRD-PARTY-NOTICES`.

Run `python3 scripts/wavpack-oracle.py --generate --oracle` from the repository.
The script checks the exact source commit and refuses modified source. It builds
both `wavpack` and `wvunpack` with CMake Release, including DSD support, in
`~/.cache/sistrum-wavpack-oracle`. Production decoding uses Kotlin only.

`wvunpack -q -y --threads=1 --raw -b -i INPUT -o -` is the raw lossy reference;
omitting `-i` uses a sibling `.wvc`. No float normalization or DSD-to-PCM
conversion is requested. Raw SHA-256, length, source SHA-256, return code and
exact error text are committed in `codecs/src/test/resources/wavpack/libwavpack.tsv`.
Raw reference audio stays in the cache and is never committed. Upstream errors
remain separate from successful raw parity expectations.

Owned original CC0-1.0 vectors live outside git in
`$SISTRUM_ORACLE_DIR/owned/wavpack/generated` (default
`~/.cache/sistrum-waxflow-oracle/owned/wavpack/generated`).
`corpus-manifest.tsv` records every source hash, signal generator and actual
encoder command (with path placeholders). The deterministic Decimal Q24 sine
table and integer phase/noise operations avoid platform libm variation. Signals
include sines, a sweep, LCG noise and silence; hybrid bitrates 2/3.5/6 bits per
sample with and without correction; float32 including signed zero and finite
extremes; 3/6/8-channel PCM with masks 0x7/0x3f/0x63f; and owned DSF/DFF DSD in
normal and high modes, mono/stereo/six channels, DSD64/128/256 and duplicated
noise/silence planes. Owned DSF/DFF originals are included for adapter tests.
PCM blocks have 512 frames; DSD blocks have 511 byte frames so tests exercise
DoP carry across odd block boundaries. Both provide interior seek boundaries.
All audio, including encoder inputs, is generated on demand and never committed.
The unified rebuild command is `bash scripts/waxflow-oracle.sh --fetch-generate`.
Normal generation compares against committed hashes; it never replaces expectations.

The external suite remains local-only under `~/.cache/sistrum-waxflow-oracle`,
as specified in `docs/waxflow/ORACLE.md`. Its compressed hashes are already in
that corpus manifest. Tests cover both corpora locally; owned vectors do not
require redistributing external suite audio.

## Feature 1: hybrid lossy validation

`./gradlew :codecs:test :codecs:testOwnedWavPack :androidApp:assembleDevDebug`
passed with zero failures. Both external and owned hybrid integer mono/stereo
streams match raw `wvunpack -i`, including exact seeks. Hybrid multichannel,
float and DSD cases remain assigned to their later feature commits.
`codecs/benchmarks/wavpack-hybrid-lossy.tsv` records 59 successful decoded cases,
all with zero decode-loop allocation bytes; median RTF range 0.001086–0.024910.
All originally successful WaxFlow native PCM hashes remain unchanged.

The oracle uses upstream `-b` (`OPEN_STREAMING | OPEN_NO_CHECKSUM`) so optional
compressed-block checksum verification does not mute the established WaxFlow
PCM output. In particular `bad_checksums.wv` has intact encoded audio and corrupt
block checksum fields: default wvunpack mutes it, while `wvunpack -b --raw`
produces the existing verified native PCM hash
`603994045e14fb060c5dc5a98d57796f0b0794b0712af7032935c4c41f8fb55b`.
This setting is applied consistently, not by replacing expected PCM values.
Audio CRC and extension CRC errors still originate from wvunpack and are recorded.
Block checksum verification remains separately available through
`verifyBlockChecksum`, preserving the existing API. Declared length is advisory
in the Kotlin demuxer; its block-derived total is retained.

## Feature 2: hybrid lossless validation

`./gradlew :codecs:test :codecs:testOwnedWavPack :androidApp:assembleDevDebug
:codecs:benchmark` passed: 985 full codecs tests and 74 owned tests, zero failures,
errors or skips. Raw output and exact seeking pass both with and without
correction, including external correction files held in `wvc_files/` (staged
as siblings only by oracle tooling). Short positional reads and direct-buffer
regions test both caller-owned inputs; mismatched correction indices fail.
All successful oracle raw hashes are unchanged by the documented `-b` setting;
only damaged files differ from default checksum-muting wvunpack output.
`codecs/benchmarks/wavpack-hybrid-lossless.tsv` records 86 decoded cases,
all with zero decode-loop allocation bytes.

## Feature 3: float32 validation

`./gradlew :codecs:test :codecs:testOwnedWavPack :androidApp:assembleDevDebug
:codecs:benchmark -PwavpackBenchmarkFeature=float` passed with zero failures,
errors or skips. Both raw IEEE words and the public borrowed FloatBuffer match
wvunpack, including signed zero, subnormals, infinity and NaN payloads. Raw and
float APIs seek to exact samples in pure-lossless and both hybrid paths.
`codecs/benchmarks/wavpack-float.tsv` records all five float cases, including
float conversion, with zero decode-loop allocation bytes. Earlier integer
benchmark artifacts remain available; native WaxFlow hashes still pass.
Owned vectors total 328,762 bytes.

## Feature 4: multichannel validation

`./gradlew :codecs:test :codecs:testOwnedWavPack :androidApp:assembleDevDebug
:codecs:benchmark -PwavpackBenchmarkFeature=multichannel` passed with zero failures,
errors or skips. All 3/6/8-channel integer, hybrid and float vectors match raw
wvunpack and expose identical masks. Exact seeks include group interiors,
block boundaries and EOF, with and without correction. Direct buffer regions
and 17-byte positional reads exercise both multichannel inputs.
`codecs/benchmarks/wavpack-multichannel.tsv` records all 11 multichannel cases,
with zero decode-loop allocation bytes. All previously matching native hashes
remain unchanged. The self-extracting native file now matches raw wvunpack too.
Owned vectors total 478,838 bytes.

## Feature 5: DSD validation

Raw DSD matches wvunpack byte for byte, with exact native byte-frame seeks.
Owned vectors exercise raw mode 0 (23 blocks), fast mode 1 (113 blocks), and
high mode 3 (26 blocks), including mono, false stereo, six channels and
DSD64/128/256. The borrowed DsdBuffer exposes MSB-first interleaved bytes and
explicit bit positions. WavPackDsd feeds the existing decimator and DoP packer
with independent cursors, exact PCM/DoP seeks and carry across odd native blocks.
Owned original DSF/DFF adapter parity checks run everywhere without the external
suite. Existing DSD specification tests remain the DSP acceptance criteria.

`codecs/benchmarks/wavpack-dsd.tsv` records 39 successful measurements across
13 files: raw, PCM and DoP output. Every decode loop allocates zero bytes.
Median RTF ranges are 0.031034–0.523982 (raw), 0.082452–0.856751 (PCM), and
0.035604–0.426604 (DoP); loading, open and seek are outside the timed loop.
PCM seeking deliberately replays filter history and can take time proportional
to the requested position. Owned assets total 671,266 bytes across 70 files,
including 46 encoded WavPack vectors, 16 correction files and eight originals.

Final acceptance command: `./gradlew :codecs:test :codecs:testOwnedWavPack
:androidApp:assembleDevDebug :codecs:benchmark -PwavpackBenchmarkFeature=dsd`.
The full codecs suite (1,365 tests), owned suite (286 tests) and Android assembly
all pass with zero failures, errors or skips; native WaxFlow
hashes remain unchanged. Corpus hash/size verification and the local-only audio
tracking guard also pass. The refusal inventory is in `REFUSALS.md`: zero valid
modern refusals; eight deprecated files and three damaged files error in the
pinned oracle as well.
