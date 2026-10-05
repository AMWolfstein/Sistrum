# Supporting the deferred formats in pure Kotlin (no NDK, no Go, no WASM)

Source: external research (Arena.ai), 2026-10-05. Condensed. Corrections from
other research in this folder are noted inline; verify before relying on any
claim.

## Short answer
Yes for AIFF, WavPack, APE and DSD; WMA is the holdout (no pure-Java/Kotlin WMA
decoder exists anywhere).

## What no-NDK buys / costs
- Gains: no 16 KB alignment work, no per-ABI .so, APK delta of hundreds of KB,
  R8 optimizes decoders, Java stack traces in crashes.
- Costs: we own the codecs forever (old pure-Java decoders decayed:
  TarsosTranscoder README on JOrbis/jflac). Plan vendoring + a conformance
  harness.

## Per format
| Format | Verdict | Path |
|---|---|---|
| AIFF / AIFF-C | trivial, PCM in IFF | one Extractor modeled on Media3 WavExtractor (Media3 has no AIFF at all); refuse compressed AIFF-C by name (sowt = little-endian PCM is fine). Sistrum uses Choir's AiffExtractor |
| WavPack | exists | port WaxFlow codec/wavpack (~2.5k lines, MIT, bit-exact on the official suite) — chosen; alternative: javasound-wavpack / McQuillan 4.40 Java (BSD-3, repo gone, "tiny" decoder limits) |
| APE | exists | port WaxFlow codec/ape (~2.1k lines, MIT, clean-room) — chosen. JMAC: the report says LGPL, other research reports GPL-2 text: license unclear, don't use. Note: the report calls the Monkey's Audio SDK license non-free (old Ubuntu bug), but other research says it is BSD-3 since v10.18 (2023) |
| DSD (DSF/DFF) | exists | JustDSD (Java, DSF/DFF/SACD/DST, powers the Android app "DSD Boss") has NO license, so nothing may be copied from it; Sistrum ports Flick's dsd_engine (MIT) instead. Benchmark decimation early: DSD64 is 2.82 MHz 1-bit, naive FIR is ~50-200 MMAC/s per channel; use staged decimation |
| WMA 1/2, Lossless, Pro, Voice | none in Java | port WaxFlow (~3.5k WMA 1/2, 1.5k Lossless, 2.8k Pro, 7k Voice) or leave unsupported. Sistrum: WMA 1/2 later, the rest unsupported until decided |
| Tags | solved | jaudiotagger (Java) and Anrimian/jaudiotagger-kt (Kotlin multiplatform, no reflection, R8-friendly, Os.pread for Android FDs): AIFF ID3, WMA ASF descriptors, APEv2 for APE/WavPack, DSF ID3v2 (DFF read-only) |

## Media3 integration (no native)
Verified in Media3 source: MediaCodecAudioRenderer passes PCM straight to the
sink; WavExtractor emits MimeTypes.AUDIO_RAW.
- Pattern A, decoding Extractor: parse + decode in the Extractor, emit AUDIO_RAW;
  stock pipeline plays it; seeking via SeekMap at block/frame granularity; decode
  runs on the loader thread. Best for AIFF and DSD. No public precedent for a
  compressed-codec decoding extractor.
- Pattern B, decoder extension in Kotlin: Extractor emits packets with a custom
  MIME (e.g. audio/x-wavpack); Kotlin SimpleDecoder<DecoderInputBuffer,
  DecoderAudioBuffer, DecoderException>; SimpleDecoderAudioRenderer subclass;
  register through a DefaultRenderersFactory subclass (buildAudioRenderers,
  EXTENSION_RENDERER_MODE_ON/PREFER), same as decoder_flac / decoder_ffmpeg minus
  JNI. Best for WavPack, APE, WMA. No public pure-JVM Media3 decoder extension
  exists yet; keep decode loops allocation-free.

## Loudness without native code
- mp3care PR#16 (github.com/andresdelcampo/mp3care/pull/16): EBU R128 /
  BS.1770-4 in an Android project (K-weighting per sample rate, 400 ms blocks,
  75% overlap, gating, LRA, true peak), verified against ffmpeg ebur128 on 38
  files (within 0.1 LU for files >= 12 s).
- Or port WaxFlow dsp/loudness (~676 lines, MIT, EBU Tech 3342 + ffmpeg
  differential). Reference C: jiixyj/libebur128 (MIT).

## Performance
Pure-Java decoding of these formats is proven real-time (JMAC, JOrbis with JIT,
musique, DSD Boss on Android). From WaxFlow's Go gates (WavPack >= 200x, APE >=
100x, WMA >= 500x realtime) and an estimated ART-vs-native factor of 2-4x after
JIT warm-up [unverified], even 10x pessimism leaves >= 10x realtime, including
two decoders for crossfade. DSD is the one perf-sensitive format.

## Recommended order (researcher)
1. AiffExtractor. 2. WavPack (Pattern B) with a golden-PCM harness first.
3. APE. 4. DSF then DFF with a Kotlin decimator. 5. Kotlin R128 analysis.
6. WMA last.
Conformance harness before step 2: golden PCM from the WaxFlow CLI or ffmpeg on
a pinned corpus; every Kotlin decoder bit-exact.

## Precedents to study
tulskiy/musique (LGPL-3.0, dead since 2016): pure-Java player of APE, WavPack and
AIFF. drogatkin/JustDSD and mediaChest: no license, don't copy.
