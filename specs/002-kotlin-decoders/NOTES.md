# 002-kotlin-decoders — notes (recorded 2026-10-05; Spec Kit not run yet)

Owner decisions recorded for the later `/speckit-specify` run. Governing: constitution Principles 6, 8, 10, 11.
Research: `specs/001-media3-migration/research/` (`pure-kotlin-formats.md`, `ndk-free-routes.md`,
`ape-wma-sources.md`, `go-bridge.md`, `platform-codecs-cph2307.md`).

## Goal

Port WaxFlow's Go decoders to Kotlin, one codec at a time, as Decoder Registry providers (001's mechanism).
No Go and no NDK in the app. Together with 001 this restores format parity on `main`; nothing merges before
001 and 002 are both done.

## Top-level design item: a scan path for formats the device doesn't know (owner, 2026-10-05)

Decoders alone are not enough. On the CPH2307 (Android 15), MediaStore did not index `wavpack_lossless.wv` as audio
(46 of 47 corpus files indexed in 001 T006; the WavPack file is the only one missing from `MediaStore.Audio.Media`).
Sistrum's library scan is MediaStore-based (`sync/MediaStoreLibraryScanner.kt`), so formats the platform doesn't
classify as audio may **never reach our scanner**, with either engine. 002 MUST design a scan path for them (e.g. a
file-system/`MediaStore.Files` pass for the registry's extensions within the user's chosen folders, with the same
tag reader and skip summary as 001 FR-065/066), not just decoders. Check MediaStore behaviour for each new format on
other devices / Android versions before deciding the mechanism (Principle 10: one device is a sample).

## Validation without real samples (owner, 2026-10-05)

The owner supplies no real APE or DSD files.
- **APE**: validated only against the WaxFlow oracle on WaxFlow's own `testdata/`, like WavPack.
- **DSD (DSF/DFF)**: no external samples. The tests generate synthetic DSF/DFF files from known signals (e.g. a
  sigma-delta-modulated sine / silence pattern, written with exact DSF/DFF headers), and **Flick's Rust
  `dsd_engine` is the oracle** for the Kotlin port: bit-exact, or within a tolerance stated in 002's spec before the
  port starts (float decimation filters may differ in the last bits).
- Real-world format problems get fixed from user reports. 001 FR-064a adds an exportable decode-failure log (time,
  file name, format, codec, provider, error) in settings, so a user can send it together with the failing file;
  every 002 provider MUST write its failures there with its provider name and a specific error (refusals by name,
  see below).

## Source and oracle

- Port source: the owner's WaxFlow fork at the pin in `docs/waxflow/ORACLE.md` (WaxFlow is not vendored).
  WaxFlow has no tagged releases: re-pin deliberately (ORACLE.md Procedure 3).
- Attribution: every ported Kotlin file carries the header in ORACLE.md. The first port adds WaxFlow (MIT)
  and its FFmpeg-derived WMA tables (LGPL-2.1-or-later) to Sistrum's third-party notices.
- Format bugs found in WaxFlow itself are fixed in the fork (ORACLE.md Procedure 2, `SISTRUM-PATCHES.md`,
  clean-room rule), then the pin is bumped.
- Oracle: `scripts/waxflow-oracle.sh` builds WaxFlow's CLI on the dev machine/CI and decodes a pinned corpus
  to golden PCM checksums and loudness numbers (small committed fixtures, no audio in git). Corpus: WaxFlow's
  `testdata/`, the official WavPack decoder test suite (wavpack.com/downloads.html), and Sistrum's test
  corpus. **Every Kotlin decoder must be bit-exact against it** (DSD: against Flick's `dsd_engine`, see above).

## Media3 wiring (`pure-kotlin-formats.md`)

- **Pattern A** — decoding Extractor emitting `AUDIO_RAW`: AIFF (001), DSD.
- **Pattern B** — Extractor emitting packets with a custom MIME + `SimpleDecoderAudioRenderer` + Kotlin
  `SimpleDecoder`: WavPack, APE, WMA family, Musepack, ALAC fallback. Registered through a
  `DefaultRenderersFactory` subclass with platform decoders keeping priority (registry order: platform first).
- Decode loops allocation-free; reuse buffers.

## Performance gate (first, before anything else)

Port WavPack (~2.5k lines) and measure on the CPH2307 (with the separate test-build application ID, never the
daily app): real-time factor after JIT warm-up, including **two decoders at once** (crossfade), CPU, battery,
memory. Then check a second device or format mix where possible (Principle 10).
- Pass → every other format follows the same way.
- Fail → stop and show the owner the numbers. Fallback (owner approval only): gomobile bind of WaxFlow
  (`research/go-bridge.md`).

## Order after the gate

1. **APE** (~2.1k lines).
2. **DSD** (DSF/DFF): Kotlin port of Flick's `dsd_engine` (github.com/moss-apps/Flick, MIT,
   `rust/src/audio/dsd_engine/`: DSF/DFF parsers, CIC + FIR decimation). Benchmark the decimation early
   (DSD64 = 2.82 MHz 1-bit; staged decimation). Seek needs filter preroll.
3. **ALAC** — Kotlin fallback from WaxFlow `codec/alac`, with the platform decoder first where a device has
   one. **The CPH2307 has no ALAC decoder** (`research/platform-codecs-cph2307.md`), so ALAC-in-M4A files only
   play on Media3 there after this port: another reason `main` waits for 002. The registry tells ALAC from AAC
   inside M4A.
4. **Musepack** (WaxFlow `codec/musepack` + `container/mpc`, ~2.1k).
5. **ADPCM** (WaxFlow `codec/adpcm`: MS-ADPCM, IMA variants incl. AIFF-C `ima4`) and **G.711** (WaxFlow
   `codec/g711`: A-law/µ-law, incl. AIFF-C `alaw`/`ulaw`), as fallbacks where Media3 + platform don't cover a
   container/codec pair (Media3's WAV reader covers IMA ADPCM; the CPH2307 has AOSP G.711 decoders; MS-ADPCM in
   WAV and compressed AIFF-C have nothing). This also lifts 001's refusal of compressed AIFF-C where WaxFlow
   supports it.
6. **WMA family** (WaxFlow `container/asf` + codecs): WMA 1/2 (~3.5k), WMA Lossless (~1.5k, refusing the modes
   WaxFlow refuses, by name), WMA Pro (~2.8k), and **WMA Voice last** (~7k, CELP, float math).

Every format gets ported; nothing stays on native code. Formats in 003's full inventory with no WaxFlow source
(e.g. TTA) are listed for an owner decision then.

## Refusals (by name, with a clear message, as WaxFlow does)

- WavPack: hybrid (lossy/`.wvc`), float, DSD-in-WavPack, > 2 channels.
- APE: 32-bit, float, > 2 channels, stream versions outside 3950–3990.
- WMA Lossless: arithmetic coding, LPC, splicing, transmitted CDLMS (as WaxFlow). ASF: Audio-Spread span > 1.
- WMA Pro: the low-bit-rate tool.
- Refused files show the normal error, logged with the provider name (001 FR-064).

## Tags for the new formats

Evaluate Anrimian/jaudiotagger-kt (Kotlin, R8-friendly; reads APEv2, ASF, DSF ID3v2, AIFF ID3) against our
`EmbeddedTagReader`, keeping scan speed (one file open per tag read). This also brings APEv2/ASF/DSF gain tags
into normalization (001 treats them as untagged).

## Alternatives, if needed

- golift/ape (Go, BSD-3) as a second APE oracle.
- symphonia-codec-wavpack (Rust, MPL-2.0) for hybrid/float WavPack later.

## Never use

JustDSD (no license), JMAC (license unclear: LGPL vs GPL-2 reports), MediaChest (no license).
