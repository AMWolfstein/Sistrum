# DSD port sources and independent oracle

The sources and generated audio remain outside Sistrum in
`${SISTRUM_ORACLE_DIR:-$HOME/.cache/sistrum-waxflow-oracle}/dsd/`. No Flutter,
Flick application engine or Rust native library is shipped with the app.

| Source | Pin | License | Download SHA-256 |
|---|---|---|---|
| [Flick](https://github.com/moss-apps/Flick) `rust/src/audio/dsd_engine/dsd/{mod.rs,coefficients.rs,dop.rs}` | `79da4ed76557c8ddf534e898480dde66bcc90334` | MIT, Copyright 2026 Flick Player Contributors | Individual source hashes enforced by `scripts/dsd-oracle.py` |
| [dsf-meta](https://gitlab.com/clone206/dsf) | `0.3.0` | MIT OR Apache-2.0 | `dfc2ace16a9905a870d157d87cbd179d701714fe6315a47e61fa194a3d6f7f1a` |
| [dff-meta](https://github.com/clone206/dff) | `0.2.0` | MIT OR Apache-2.0 | `06301c411497b9d471ba2aa728dc666fd4c5c300673c2f4d937eb6a737824a1b` |
| [dsd-source](https://github.com/clone206/dsd-source), parser interface dependency | `0.2.0` | MIT OR Apache-2.0 | `abefa93db26ebcaae898dedd37f9a3e8378c369b1c251adf54e73e7f1195aa37` |

The versioned parser sources are the immutable crates.io releases, downloaded
from `https://static.crates.io/crates/NAME/NAME-VERSION.crate`; Cargo.lock records
these same checksums and pins transitive packages. The active dependency license
inventory is `docs/waxflow/dsd-dependencies.tsv`; all offer MIT or Apache-2.0.
Optional backends outside the active cargo tree are not linked into the harness.

## Reproduction

```bash
bash scripts/dsd-oracle.sh --fetch-generate
bash scripts/dsd-oracle.sh --verify
bash scripts/dsd-oracle.sh --test
```

The rebuild fetches/checks the exact Flick revision, builds a standalone Rust
harness using only its three DSD processing modules and the pinned parser crates,
and generates the complete corpus. It checks every manifest hash and compares
fresh Rust output against the committed fixture bytes. It never replaces golden
expectations. Rust 1.90.0 (minimal toolchain) and Cargo caches/build outputs live
inside the external working directory; an isolated rustup bootstrap is available
on x86_64 Linux and never changes the user's shell profile. The bootstrap binary
has a pinned SHA-256 and fails if its download changes.

Corpus sources and hashes: `docs/waxflow/dsd-corpus-manifest.tsv`.
Oracle output: `androidApp/src/test/resources/dsd/oracle-fixtures.tsv`.
Each row includes the source hash, refusal or PCM SHA-256, DSD/PCM rates, channels,
physical channel layout, frame count, DoP carrier rate and the first eight DoP
frames as interleaved little-endian 32-bit words. PCM hashes are float32,
interleaved little-endian, with no normalization or integer quantization.

## Synthetic corpus and oracle contract

`generate.py` contains an original first-order sigma-delta modulator driven by
known sine tones and linear frequency sweeps. A Decimal-generated Q24 sine table,
integer phase and error feedback make bytes reproducible without platform libm.
All generated signals and containers are CC0-1.0. The 20 files cover DSD64/128/256,
mono/stereo/5.1, DSF/DFF, native 4096-byte DSF blocks and odd byte tails. Stereo
files include ID3; other files omit it. Mono DFF files include DIIN/DItitle chunks.
An additional DSF uses MSB-first storage and three trailing sub-byte sample bits.
The DST refusal file has a genuine DST compression header and a FRTE declaring
zero frames; it tests header refusal, not DST decompression of an encoded song.

The harness resolves headers with dsf-meta/dff-meta, rearranges native blocked or
interleaved data into planar MSB-first blocks, and passes those blocks to Flick's
unmodified decimation and DoP modules. The processing unit is 4096 bytes per
channel, except the last partial block. PCM target is explicitly **176400 Hz**
for all three rates. Decimation uses Flick's third-order CIC and 512-tap Kaiser
FIR. No delayed tail is flushed: the source provides no drain operation. An
incomplete final FIR decimation group is discarded, as in the source API.
DSF sample_count/8 follows dsf-meta's whole-byte contract; the extra three sample
bits do not become invented full bytes. Physical DSF block padding and following
metadata are never treated as DSD samples.

Layout labels preserve physical order: DSF uses its ChannelType definition
(`C`, `FL,FR`, `FL,FR,C,LFE,BL,BR` in this corpus); DFF uses literal CHNL IDs
(`C`, `SLFT,SRGT`). Channels are not reordered. The oracle harness's reporting
helper extracts DFF CHNL IDs because dff-meta exposes the count but no ID accessor.

## Refusals retained from the pinned parsers

| Reason | Files |
|---|---|
| `dff: CHNL number not found or is unsupported.` | `dsd64-6ch.dff`, `dsd128-6ch.dff`, `dsd256-6ch.dff` |
| `dff: Compression type must be 'DSD '. DST not supported.` | `dst-compressed.dff` |

Dff-meta 0.2.0 and Flick's wrapper both only admit mono/stereo. DSF admits six
channels. These are source limits, retained rather than silently adding DFF
multichannel support. The initial oracle has **20 entries, 16 decoded, four
refused**. Paired DSF/DFF successful files have identical PCM and DoP output.

## Kotlin attribution

Every ported Kotlin file begins with SPDX GPL-3.0-or-later and identifies the
original source path, exact version/commit and MIT or MIT/Apache-2.0 origin.
The original license notices are included in codecs/THIRD-PARTY-NOTICES.
