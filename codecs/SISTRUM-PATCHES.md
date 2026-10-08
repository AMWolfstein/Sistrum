# Kotlin port adaptations

Source pin: `b7857aff88820ad37936026421d1641e64611dbe`. Fork fixes themselves
remain recorded in the fork's root `SISTRUM-PATCHES.md`; this module-local file
records JVM port deviations within the authorized Sistrum file scope.

## APE

- Go callbacks delivering planar 4096-frame buffers become borrowed interleaved
  chunks from `decodeBlock()`. A checked frame is retained while chunks drain.
- Frame, encoded-packet and CRC scratch are preallocated at open, rather than
  grown lazily on the first decode, to make the decode loop allocation-free.
- Go uint32 range registers use masked Long values; predictor wrapping remains
  Int and the interim path uses Long before truncating, in the source's order.
- The reference CRC update uses one reusable JVM `CRC32`; sample packing and
  special-code handling retain the source's behavior.
- The Go demuxer lands at frame granularity; the stream wrapper pre-rolls to the
  exact sample requested, as the WavPack API does.
- Tag-value extraction, encoders, muxers and pipeline registry are outside this
  decoder API. Trailer/leading-ID3 recognition remains to delimit audio.
- The existing bounded source window now accepts an error factory so APE source
  failures retain APE's message. Trailer recognition takes the caller's floor
  (APE: 1; WavPack: 32). Existing WavPack defaults and behavior are retained.
- The seek table is read once at open into bounded metadata scratch, and each
  encoded frame is read positionally into the reused packet buffer. Sources
  are never loaded wholesale for decoding.
