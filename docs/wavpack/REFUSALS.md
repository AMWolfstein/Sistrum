# WavPack refusal inventory

All five features are complete. Counts are primary `.wv` inputs, excluding
standalone `.wvc` files and duplicate correction/no-correction runs. There are
50 local-only external files and 46 owned encoded vectors. Correction files
are optional inputs to the two-source API.

| Result / remaining refusal | Files |
|---|---:|
| Decoded, bit-exact with pinned wvunpack | 85 |
| Deprecated pre-v4 streams; wvunpack also errors | 8 |
| Damaged audio; wvunpack also reports CRC errors | 3 |
| Valid modern files still refused | 0 |

The three damaged files are `dsd_corrupt.wv`, `hybrid_corrupt.wv` and
`lossless_corrupt.wv`. The pinned oracle reports missing data or CRC errors
in 21, 9 and 16 blocks respectively without correction (17 for corrected hybrid).
All eight deprecated inputs receive upstream's instruction to transcode using
version 4.80.0. These failures are retained as negative tests.

There are 116 successful oracle runs including 31 corrected runs, and 13 error
runs including duplicate corrected failures. Native successes retain the
previous WaxFlow hashes. Source/correction SHA-256, raw hashes and exact oracle
errors are recorded in `codecs/src/test/resources/wavpack/libwavpack.tsv`.
