# WavPack refusal inventory

State after features 1–4. Counts are primary `.wv` inputs, excluding
standalone `.wvc` files and duplicate correction/no-correction runs. The inventory
contains 50 external files and 38 owned vectors. Correction-only files are inputs
to the two-source API; they cannot supply audio without the main stream.

| Result / remaining refusal | Files |
|---|---:|
| Decoded, bit-exact native or new raw oracle parity | 72 |
| DSD (feature 5 pending, includes one damaged stream) | 6 |
| Deprecated pre-v4 streams; wvunpack also errors | 8 |
| Audio CRC failures; wvunpack also errors | 2 |

Native successes retain the WaxFlow hashes. See `libwavpack.tsv` for source hashes,
raw oracle hashes, correction hashes and the pinned decoder's exact error text.
