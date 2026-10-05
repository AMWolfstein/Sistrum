# Missing formats on Media3 — first search (AIFF, APE, WavPack, DSD, WMA)

Source: external research (Arena.ai), 2026-10-01. Superseded in parts by
research-ape-wma-sources.md and research-ndk-free-routes.md. Verify claims.

Key distinction: Jellyfin's Media3 FFmpeg decoder is a decoder, not a container
parser. These files need an Extractor (tracks, packets, duration, seek).

## Matrix (as found then)
| Format | Route | Java/Kotlin only? | Native? | Verdict |
|---|---|---|---|---|
| AIFF / PCM AIFC | Choir AiffExtractor | yes | no | adopt now |
| APE | Monkey's Audio SDK (BSD-3 since v10.18) + JNI | no | yes | viable, build it |
| WavPack | libwavpack (BSD-3) + JNI | no | yes | best native dependency |
| DSF/DFF | none reusable found | — | — | gap (later solved: Flick) |
| ASF/WMA | none without FFmpeg | — | — | gap (later solved: WaxFlow) |

## AIFF — Choir
- github.com/AurielSolaris/Choir, AiffExtractor.kt, GPL-3.0-or-later, active
  (2026-09-20). Local-first Android player ported from AOSP Music to Kotlin,
  Compose and Media3 (verified).
- Parses FORM/COMM/SSND; AIFF PCM and AIFC NONE/sowt/twos; big-endian to
  little-endian; emits audio/raw; duration from sample count; constant-rate
  seek. Rejects compressed AIFC on purpose.
- Test: 8/16/24/32-bit, mono/stereo/multichannel, sowt, non-zero SSND offset,
  seeks at start/middle/end.
- Rejected: RalleYTN/SimpleAudio (MIT, javax.sound, not Android).

## APE
- Choir ApeExtractor.kt: parser + seek table, but needs Choir's modified FFmpeg
  decoder (FfmpegLibrary/FfmpegAudioDecoder patches, bits_per_coded_sample JNI).
  Reference only.
- Official Monkey's Audio SDK: BSD-3 since v10.18 (current 13.26), C/C++, no
  Android binding.
- JMAC (tulskiy/jmac): GPL-2.0 text in repo, old format support, Java Sound
  pieces; not recommended (license unclear vs GPL-3.0).

## WavPack
- dbry/WavPack: BSD-3, active (2026-09-29), supports Android builds; modern
  streams, multichannel, seeking, .wvc, hi-res, DSD-in-WavPack. Tiny Decoder is
  constrained.
- Choir WavPackExtractor.kt: block parsing, duration, seek map; depends on
  Choir's FFmpeg. Reference only.
- Old Java decoders (4.40-era, Musique bundle): 2 channels, no .wvc, no DSD.

## DSD (DSF/DFF)
- No GPL-compatible Android solution found then.
- drogatkin/JustDSD: technically useful but NO license: do not copy or ship.
- xbmc/audiodecoder.ssf is Sega Saturn SSF, not DSD.
- jasyu666/yuHIFI: PolyForm Noncommercial, not GPL-compatible.
- DSD seeking needs filter preroll; treat seek quality as a requirement.

## ASF/WMA
- Choir AsfExtractor.kt: real ASF parser, but WMA decode needs Choir's patched
  FFmpeg (block_align/bitrate JNI, custom MIME mappings, wmav1/v2/pro/lossless/
  voice enabled).
- No pure Java/Kotlin WMA decoder found then.
- BASS: proprietary, not GPL-compatible.

## How big Android players do it
VLC, Kodi, mpv-android, libmpvKt: large native engines (FFmpeg-based). Proof of
feasibility, not a small Media3 dependency.
