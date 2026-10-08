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


## ALAC

- Go planar callback output becomes a reused interleaved `Buffer`. The frame
  reader and low-byte shift reader are reused rather than constructed per packet.
- Cookie parsing consumes the canonical ALACSpecificConfig supplied by Media3,
  not the enclosing MP4 box. No MP4 demuxer is ported into production.
- `PacketIndex` is extractor-supplied metadata; the `Alac` wrapper reads packets
  through `RandomAccessSource`, finds a seek interval and pre-rolls to the exact
  sample. The source decoder itself has no seeking state or latency.
- Predictor scratch and output are allocated at open rather than the first
  frame; packet scratch is sized from the supplied sample table. Go uint32
  arithmetic is retained with Int wrapping and unsigned shifts/comparisons.
- Container-signaled total samples bounds the last returned block. Index
  timestamps are source samples, not microseconds.
- Tests demux the free MP4 vectors using the pinned Go container into external
  packet dumps; source hashes are verified, and PCM hashes are taken from the
  existing regenerated oracle. Test tooling is not a production MP4 port.


## Musepack

- Go's planar float callbacks become a reused interleaved `FloatBuffer`.
  The synthesis DCT, window, float32 rounding and summation order are retained.
- Encoded packet and bit-reader storage is allocated at open and reused. SV8
  packets drain one frame per `decodeBlock()` call while retaining the reader;
  Go's packet header is internal transport and is replaced by fields here.
- SV7 word realignment, 32-frame offset/state checkpoints, SV8 capped/halving
  block index, noise-generator carry, PNS declaration and sample trimming are
  retained. Container scanning reuses a scanner instead of temporary structs.
- Exact sample seeking and discarded filter pre-roll live in the stream wrapper,
  replacing the Go format pipeline's trim. Go packet-state serialization and
  persisted index serialization are not exposed by this module's API.
- Tag-value, chapter and replay-gain metadata APIs are omitted. Their packet
  framing and leading/trailing tag recognition remain to delimit audio.
- Step-2/3/18 loops use explicit counters: this Kotlin compiler allocates
  progression/range objects for `step` inside these loops. Loop order is unchanged.
- Huffman tries are built once from the same source rows; Int wrapping and
  unsigned shifts implement the source's uint32 noise generator and bit codes.
