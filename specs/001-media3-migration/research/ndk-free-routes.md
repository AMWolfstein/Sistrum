# NDK-free (pure JVM/ART) routes for WavPack and WMA

Source: external research (Arena.ai), 2026-10-04. Condensed. Verified in chat:
github.com/ColeSpringer/WaxFlow exists, MIT (Copyright 2026 Cole Springer),
latest commit 2026-09-23; codec/ contains aac, adpcm, alac, ape, flac, g711,
mp3, musepack, opus, pcm, vorbis, wavpack, wma, wmalossless, wmapro, wmavoice;
container/ contains adts, aiff, apen, asf, flacn, mka, mp4, mpa, mpc, ogg, riff,
wv. No DSD.

## TL;DR
- WavPack: NDK-free routes exist. Best: WaxFlow codec/wavpack (Go, MIT) or
  symphonia-codec-wavpack (Rust, MPL-2.0). Quick: Peter McQuillan's Java/C#
  4.40-era decoders (BSD; stereo only, no .wvc).
- WMA: the only managed-language implementation is WaxFlow (Go): WMA v1/v2,
  Pro (incl. 5.1/7.1), Lossless (partial), Voice, plus an ASF demuxer. Tested
  against ffmpeg and Microsoft Media Foundation decoders.
- WASM on the JVM works in 2026: Chicory runs on Android; reliable path is
  build-time AOT (WASM -> .class -> dex). The 70 KB Rockbox WMA WASM is a ready
  payload.
- Android platform codecs: WMA effectively gone since ~Android 11 (old Samsung
  only); WavPack never in AOSP. Treat both as absent.

## WavPack
A. WaxFlow codec/wavpack + container/wv (Go, MIT, clean-room port of
   libwavpack, see THIRD-PARTY-NOTICES and docs/adr/0001-clean-room-policy.md):
   lossless v4, 8-32-bit, mono/stereo incl. false stereo; bit-exact vs the
   official WavPack test suite (48 members). Refuses hybrid, float, DSD, >2
   channels. Exact seek via block bisection. Go bench: 562x realtime worst case.
B. symphonia-codec-wavpack (github.com/pierreaubert/symphonia-add-ons, Rust,
   MPL-2.0): joint/false stereo, integer and float, hybrid lossy, embedded
   correction bitstreams, Matroska A_WAVPACK4. DSD raw mode only; no external
   .wvc. Active (2026-09-28).
C. McQuillan decoders (BSD, 4.40 lineage): Java v1.3
   (wavpack.com/files/JavaWavPackDecoder_v1.3.zip), C#
   (github.com/soiaf/C-Sharp-WavPack-Decoder), haXe v1.4. No .wvc, first two
   channels only, no pre-4.0 files.
D. javasound-wavpack (Maven Central, BSD-3, tiny 4.40 decoder): repo 404s.
Validation: official decoder test suite at wavpack.com/downloads.html.

## WMA — WaxFlow (Go, MIT)
- codec/wma (v1/v2; 632-1226x realtime in Go), codec/wmapro (0x0162, mono/
  stereo and 5.1/7.1; refuses the low-bit-rate tool), codec/wmalossless
  (bit-exact on its corpus; refuses arithmetic coding, LPC, splicing,
  transmitted CDLMS; MCLMS and cascade order unverified), codec/wmavoice (CELP),
  container/asf (frame-aligned seek; refuses Audio-Spread span > 1).
- Parameter tables are mechanically extracted from FFmpeg n9.0 (LGPL-2.1+) under
  a SHA-256 pin; decoder logic written from behavioral notes. Worst case the
  tables are LGPL-2.1+, which is GPL-3.0 compatible: keep attribution.
- No pure Java/Kotlin/C#/TS/Rust/Dart/Swift/Haxe/Python WMA decoder exists.

## Running C without the NDK
- Chicory (github.com/dylibso/chicory, Apache-2.0): pure-Java WASM runtime.
  Interpreter runs on ART; runtime compiler needs an experimental DexMaker
  backend; dependable route is build-time AOT through the Gradle plugin, then
  AGP dexes and ART JITs. Reported compatible with API 28+. Moving to "Endive"
  under the Bytecode Alliance (mid-2026). Use larger thread stacks for the
  interpreter.
- Payloads: Rockbox WMA WASM (@audio/wma-decode, GPL-2.0+); libwavpack WASM
  would have to be built.
- Estimated cost: AOT + JIT ~2-10x native; interpreter 10-100x slower. Decoders
  have hundreds-of-x headroom, so likely real-time; measure on a weak device.
- asmble, NestedVM, Cibyl, LLJVM: dormant, not recommended.
- For Go sources specifically: TinyGo -> WASM -> Chicory, or gomobile bind
  (native .so via the NDK toolchain plus Go runtime). To be compared in the
  002 bridge spike.

## Effort estimates (stereo 44.1/48 kHz)
| Route | Effort | Headroom* |
|---|---|---|
| Kotlin port of WaxFlow wavpack | 1-3 weeks | >= 100x |
| Kotlin port of symphonia-codec-wavpack | 2-4 weeks | similar |
| McQuillan Java/C# decoder | 3-7 days | >= 100x |
| Kotlin port of WaxFlow wma | 2-4 weeks | tens-hundreds x |
| WaxFlow wmapro / wmalossless / wmavoice ports | 2-4 / 1-2 / 2-3 weeks | fine |
| Chicory AOT + Rockbox WMA WASM | 2-5 days | 5-30x est. |
| Chicory interpreter | 1-2 days | 1-10x est., measure |
*Estimates except WaxFlow's own Go benchmarks.

## Uncertainties
WaxFlow is single-author and months old; WMA logic clean-room from notes and
tables, quality attested only by its own corpus. symphonia-codec-wavpack src
layout not opened. Chicory Android module names unverified. Speed numbers are
estimates.
