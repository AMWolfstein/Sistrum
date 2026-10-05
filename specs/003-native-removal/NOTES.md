# 003-native-removal — notes (recorded 2026-10-05; Spec Kit not run yet)

Owner decisions recorded for the later `/speckit-specify` run. Governing: constitution Principles 8 and 11.

## Gate

Full regression tests on Media3 + the Kotlin decoders, and the owner's explicit approval. 003 merges on its
own, after 001 + 002 are on `main`.

## Step 1 — remove the old native player and our FFmpeg build, together

- Native engine (`player/FfmpegDecoder.kt`, `LegacyNativeEngine` and the engine switch),
  `androidApp/src/main/cpp/`, `androidApp/src/main/jniLibs/`, `scripts/build-ffmpeg-android.sh`, and the
  CMake/NDK wiring that only they use.
- Precondition: **DSD has its Kotlin provider** (002). Until then DSD plays only on the native player.
- Precondition: Media3 + the Kotlin decoders pass full regression.

## Step 2 — remove the NDK entirely

- Precondition: **every format in the full format inventory has a Kotlin decoder**, including the whole WMA
  family (WMA 1/2, Lossless, Pro, Voice).
- **Full format inventory** (written before 003 starts): everything FFmpeg plays today. The scanner admits
  anything MediaStore marks as music and FFmpeg is built with all decoders and demuxers
  (`research/discovery.md` §3), so the inventory comes from FFmpeg's enabled demuxers/decoders crossed with
  what MediaStore classifies as music, not only from the scanner's format labels. For each entry: provider
  (platform / Kotlin port / none).
- Inventory formats with **no WaxFlow source** (e.g. TTA, TAK, and anything else found) are listed for an
  owner decision then (port from another source, or accept unsupported with the skipped-files summary).
- After step 2 the app ships no native code at all (no `.so`, no `externalNativeBuild`), which also removes
  per-ABI builds and 16 KB page-alignment work.

## Not in 003

- The pre-v1.0 review-and-fix phase (`docs/review/2026-10-code-review.md`) is separate and follows all three
  features.
