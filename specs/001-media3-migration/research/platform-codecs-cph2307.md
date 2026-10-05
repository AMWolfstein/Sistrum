# Platform audio decoders on the CPH2307 (2026-10-05)

Own measurement, read-only. Device: OPPO CPH2307 (product CPH2307EEA), Android 15 (API 35), Qualcomm
(lahaina/shima/yupik codec configs).

## Method

The planned instrumented probe (`androidTest/.../diagnostics/PlatformAudioDecodersTest.kt`, lists
`MediaCodecList(ALL_CODECS)` audio decoders) could not run: the installed `me.misa198.airmedy.dev` is the
owner's R8-minified daily build, so the test APK's Kotlin classes fail to load against it
(`ClassNotFoundException: kotlin.jvm.internal.Intrinsics`). Running it needs a debug app build installed,
which would replace the daily app (wait for the `.qa` test-build suffix, FR-070). Instead the codec XML files
that `MediaCodecList` is built from were read over adb (`/apex/com.android.media.swcodec/etc/`,
`/vendor/etc/media_codecs*.xml`, `/odm/etc/media_codecs*.xml`).

## Audio decoders found

| MIME | Decoders |
|---|---|
| audio/mpeg | c2.android.mp3, OMX.google.mp3 |
| audio/mp4a-latm (AAC) | c2.android.aac, c2.android.inproc.aac, OMX.google.aac |
| audio/flac | c2.android.flac, OMX.google.flac |
| audio/opus | c2.android.opus, c2.android.inproc.opus, OMX.google.opus |
| audio/vorbis | c2.android.vorbis, OMX.google.vorbis |
| audio/raw | c2.android.raw, OMX.google.raw |
| audio/g711-alaw, audio/g711-mlaw | c2.android.g711.*, OMX.google.g711.* |
| audio/3gpp, audio/amr-wb, audio/gsm, audio/iamf | AOSP software decoders |
| audio/ac3, audio/eac3, audio/eac3-joc, audio/ac4 | OMX.dolby.* (vendor, device-specific) |
| audio/ffmpeg | OMX.ffmpeg.atrial (OPPO vendor-private; not addressable from Media3 by a standard MIME) |

**No `audio/alac` decoder** (0 matches in every codec list). No WMA, APE, WavPack, Musepack, DSD decoders.

## Consequences

- ALAC (in M4A) has no platform provider on this device. FFmpeg plays it today. It needs a Kotlin provider:
  WaxFlow has `codec/alac`. Until then, ALAC files are unsupported on the new engine on such devices, and the
  scan must tell ALAC from AAC inside M4A (codec, not only container) to skip them correctly.
- AC-3/E-AC-3 work here only through Dolby vendor decoders; other devices may lack them (Principle 10).
- The registry must probe the platform at runtime, never assume a codec list from one device.

## Instrumented probe result (2026-10-05, `.qa` build, T001)

`PlatformAudioDecodersTest` run on the CPH2307 through `me.misa198.airmedy.dev.qa.test` (OK, 1 test). It confirms the
codec-XML desk result: **no ALAC decoder**; FLAC, Opus, Vorbis, MP3, AAC, raw PCM and G.711 present (c2.android.*).
Also listed: Dolby AC-3/E-AC-3/AC-4 hardware decoders and a vendor `audio/ffmpeg` (`OMX.ffmpeg.atrial.decoder`). The
registry keys on codec MIME types, so a vendor FFmpeg component is never selected for a format (constitution: no
FFmpeg inside Media3); it is recorded here only as a device fact.

```
me.misa198.airmedy.diagnostics.PlatformAudioDecodersTest:DECODERS
audio/3gpp	OMX.google.amrnb.decoder	alias
audio/3gpp	c2.android.amrnb.decoder	software
audio/ac3	OMX.dolby.ac3.decoder	hardware
audio/ac4	OMX.dolby.ac4.decoder	hardware
audio/amr-wb	OMX.google.amrwb.decoder	alias
audio/amr-wb	c2.android.amrwb.decoder	software
audio/eac3	OMX.dolby.eac3.decoder	hardware
audio/eac3-joc	OMX.dolby.eac3-joc.decoder	hardware
audio/ffmpeg	OMX.ffmpeg.atrial.decoder	hardware
audio/flac	OMX.google.flac.decoder	alias
audio/flac	c2.android.flac.decoder	software
audio/g711-alaw	OMX.google.g711.alaw.decoder	alias
audio/g711-alaw	c2.android.g711.alaw.decoder	software
audio/g711-mlaw	OMX.google.g711.mlaw.decoder	alias
audio/g711-mlaw	c2.android.g711.mlaw.decoder	software
audio/gsm	OMX.google.gsm.decoder	alias
audio/gsm	c2.android.gsm.decoder	software
audio/mp4a-latm	OMX.google.aac.decoder	alias
audio/mp4a-latm	c2.android.aac.decoder	software
audio/mpeg	OMX.google.mp3.decoder	alias
audio/mpeg	c2.android.mp3.decoder	software
audio/opus	OMX.google.opus.decoder	alias
audio/opus	c2.android.opus.decoder	software
audio/raw	OMX.google.raw.decoder	other
audio/raw	c2.android.raw.decoder	software
audio/vorbis	OMX.google.vorbis.decoder	alias
audio/vorbis	c2.android.vorbis.decoder	software
PROBE
audio/alac	no
audio/flac	YES
audio/opus	YES
audio/vorbis	YES
audio/mpeg	YES
audio/mp4a-latm	YES
audio/raw	YES
audio/g711-alaw	YES
audio/g711-mlaw	YES
audio/ac3	YES
audio/eac3	YES
audio/x-ms-wma	no
audio/x-ape	no
audio/x-wavpack	no
audio/x-musepack	no
.

Time: 0.063

OK (1 test)
```
