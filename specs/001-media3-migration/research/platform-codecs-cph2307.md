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
