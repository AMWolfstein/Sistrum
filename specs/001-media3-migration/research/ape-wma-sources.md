# Open-source APE and WMA/ASF decoders & parsers (any language)

Source: external research (Arena.ai), 2026-10-04. Condensed. Items marked
[unverified] were not checked by the researcher. Verified in chat: golift/ape,
OMBS-IO/ape-decoder, Borewit/music-metadata and DeaDBeeF repos exist; golift/ape
LICENSE carries Go Lift (2026) and Matthew T. Ashland copyrights.

## TL;DR
| Need | Options | License | Route |
|---|---|---|---|
| APE -> PCM | golift/ape (Go), OMBS-IO/ape-decoder (Rust), DeaDBeeF ffap (C), Rockbox libdemac (C) | BSD-3 / MIT+Apache / GPL-2.0+ / GPL-2.0+ | port Go/Rust, or JNI the C |
| ASF parse | TagLib, VLC libasf, GStreamer asfdemux, music-metadata (TS), getID3, mutagen, Rockbox libasf | LGPL/MPL, LGPL, LGPL, MIT, multi, GPL, GPL | write in Kotlin |
| WMA v1/v2 | Rockbox libwma, DeaDBeeF libwma (FFmpeg-derived fixed-point C) | LGPL-2.0+ | JNI (or WASM) |
| WMA Pro | Rockbox libwmapro | LGPL-2.1+ | JNI |
| WMA Voice | Rockbox libwmavoice | LGPL-2.1+ | JNI |
| WMA Lossless | none outside FFmpeg (later: WaxFlow, partial) | — | — |

## APE decoders
- golift/ape (github.com/golift/ape, Go, BSD-3): decode.go, decode_old.go,
  decode_pred.go, decode_stream.go, frame.go, format.go, predict.go, nn.go,
  range.go, range_table.go, float.go, prepare.go, link.go, tag.go, encoders.
  Claims parity with Monkey's Audio 13.26; decodes 3.95-3.99 (3.93/3.94 path
  untested, <3.93 unsupported); 8/16/24/32-bit, float, up to 32 channels.
  Brand new (latest 2026-09-30), no production users.
- OMBS-IO/ape-decoder (Rust, MIT OR Apache-2.0, forbid(unsafe)): >= 3.95, all
  levels, up to 8 channels, sample-level seek, 127 tests, claims byte-identical
  PCM vs mac 12.53. v0.3.2 (2026-03-18). Could be bound via UniFFI.
- DeaDBeeF plugins/ffap/ffap.c (C, GPL-2.0+): 3.95-3.99, <= 2 channels,
  8/16/24-bit, seek table, ARM NEON asm, no mallocs during decode. Lineage:
  libdemac -> FFmpeg apedec -> ffap. Active.
- Rockbox lib/rbcodec/codecs/demac/libdemac/ (C, GPL-2.0+): original APE
  decoder; 3.97+ safe floor; seek via lib/rbcodec/codecs/ape.c.
- Not useful: Python Audio Tools (removed APE), Kodi/XMMS/GStreamer mac
  wrappers (official SDK), Symphonia (no APE; PR #470 open), OxideAV/oxideav-ape
  (unfinished). JMAC only covers <3.93 but license unclear.

## ASF parsers
- TagLib taglib/asf/ (LGPL-2.1 or MPL-1.1; use LGPL): codec id, duration, tags.
- VLC modules/demux/asf/ (LGPL-2.1+): full demux incl. seek; no decode.
- GStreamer gst-plugins-ugly asfdemux (LGPL-2.1+).
- Rockbox lib/rbcodec/codecs/libasf/ (GPL-2.0+, ~600-800 lines) + wma.c header
  parsing: easiest C to port.
- DeaDBeeF plugins/wma/asfheader.c + libasf/ (GPL-2.0+).
- music-metadata lib/asf/ (TypeScript, MIT, ~1-1.5k lines): AsfParser.ts,
  AsfObject.ts, AsfGuid.ts, AsfUtil.ts, AsfTagMapper.ts. Nearly mechanical to
  port.
- getID3 module.audio-video.asf.php (pick GPL-3.0/MPL-2.0); mutagen asf/ and
  monkeysaudio.py (GPL-2.0+, smallest APE header reference).
- lofty (Rust): APE yes, ASF no. Symphonia: no ASF.

## WMA decoders
- Rockbox libwma (wmadeci.c, wmadec.h, wmadata.h, wmafixed.c; LGPL-2.0+):
  WMA v1/v2 only, fixed-point, seek via libasf. @audio/wma-decode (npm, GPL-2.0+)
  compiles it with an ASF demuxer to a ~70 KB WASM (repo URL 404s).
- DeaDBeeF plugins/wma/libwma: same decoder, fewer embedded-isms.
- Rockbox libwmapro (LGPL-2.1+): WMA Pro, fixed-point port of FFmpeg; mono/stereo
  well tested.
- Rockbox libwmavoice (LGPL): WMA Voice, floating point.
- WMA Lossless: no non-FFmpeg decoder (at the time of this report).
- DRM-protected WMA: out of scope (no open implementation; anti-circumvention law).

## Licensing
All listed GPL-2.0-or-later, LGPL, MIT, BSD, Apache-2.0 and MPL-2.0 items are
GPL-3.0 compatible. Monkey's Audio SDK BSD-3 since 10.18 (2023-08). LGPL parts:
keep attribution and replaceability.

## Patents (not legal advice)
Core WMA patents were filed ~1997-2006 and are expired or at end of term by
2026; FFmpeg's reverse-engineered WMA decoders have shipped openly for ~20
years without known enforcement against decoders. Historical caution: VirtualDub
dropped ASF after a Microsoft complaint (~2000); GStreamer keeps asfdemux in
"-ugly". Decoding only; exclude DRM. Get real advice before selling the app.
APE: never patent-asserted.
