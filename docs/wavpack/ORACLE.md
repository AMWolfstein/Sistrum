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

Owned original CC0-1.0 vectors live in `codecs/src/test/resources/wavpack/generated`.
`corpus-manifest.tsv` records every source hash, signal generator and actual
encoder command (with path placeholders). The deterministic Decimal Q24 sine
table and integer phase/noise operations avoid platform libm variation. Signals
include sines, a sweep, LCG noise and silence; hybrid bitrates 2/3.5/6 bits per
sample with and without correction; float32 including signed zero and finite
extremes; 3/6/8-channel PCM with masks 0x7/0x3f/0x63f; and owned DSF/DFF DSD in
normal and high modes. Fixed 512-sample blocks provide interior seek boundaries.
The generator fails at 5,000,000 bytes; larger corpora must be generated at test
time rather than committed.

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
