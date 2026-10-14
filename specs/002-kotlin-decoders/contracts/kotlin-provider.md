# Contract: Kotlin decoder providers (M1)

- Every provider implements `player/decoders/DecoderProvider` and is registered after `PlatformProvider`.
- The decoding extractor emits `MimeTypes.AUDIO_RAW` with the codec's PCM encoding, exact `durationUs` from the
  sample count, a seekable `SeekMap` (sample-exact target; DSD with pre-roll), and encoder delay/padding = 0 with
  exact lengths (gapless joins ≤ 2 frames).
- `sniff` decides by magic bytes only; a refused stream returns its typed refusal through `refusal(header)` and the
  normal error path (FR-005).
- Steady-state decode allocates nothing per block (FR-008); buffers come from the codec's reusable `Buffer`.
- The same providers serve `analysis/OfflineDecoder` (FR-009).
- Output channels: named placement via `Media3Layout.forMask`; downmix via `Matrix.forLayouts` when the device has
  fewer channels; rates above 192 kHz resampled with `:dsp` HQ (research R4/R5).
