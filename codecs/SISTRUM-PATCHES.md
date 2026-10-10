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

## ADPCM / G.711

- Only missing MS and QuickTime IMA layouts are ported. Media3 owns WAV IMA.
  Shift/add QuickTime step arithmetic, carry/reset thresholds, MS coefficient
  products, truncation, bias and delta clamps retain the source behavior.
- Planar callbacks become reused interleaved IntArray blocks. Encoded storage,
  predictors and output are allocated at open; bounded source reads replace Go
  packet slices. Sample-exact seeking/trim belongs to `BlockStream`.
- The RIFF branch is scoped to MS ADPCM and AIFF-C to `ima4`; other codecs are
  left to existing extractors. Chunk boundaries, fact trimming, RF64 ds64,
  sample-entry fields and source refusal messages remain.
- The MP4 branch is scoped to `ima4`. Progressive chunk tables and fragmented
  addresses are indexed at open instead of a streaming Go packet queue; encoded
  audio remains in RandomAccessSource. General MP4 codec routing, metadata/tag
  APIs and persisted index serialization are omitted. Source edit-list trimming
  is represented by a front delay and emitted length. Undeclared fragment length
  retains the complete last coded block, matching Go's 44,160-frame fixture.
- G.711 is decoder-only: law/rate/channels and byte-region metadata come from
  the existing extractor. The original expansion tables are built once at open.
  This supplies the 7/8-channel fallback beyond published Android capabilities;
  it does not add a second WAV/MP4 extractor. File seeking replaces packet/flush
  callbacks since G.711 has no predictor state.
- Derived multichannel test vectors replicate pinned MIT compressed mono bytes;
  scripts/waxflow-g711-corpus.py reproduces them outside the repository. All PCM
  expectations are produced by Go, never by the Kotlin implementation.

## Expanded lossless corpus tooling

- The ALAC packet helper now recognizes a demux refusal only when its exact Go
  error matches the committed fixture. No packet dump exists for such a stream;
  tests extract its real cookie and assert the Kotlin parser's exact refusal.
- Original generated signals and a directly constructed legacy silence stream
  expand coverage without changing production decoder arithmetic or expectations.
  The legacy vector exercises old framing, not the non-silent old entropy path.

## ASF / WMA v1 and v2

- Ported the pinned Go ASF header/stream selection, packet fields, compressed
  subpayloads and fragment assembly, and the WMA reservoir, exponent, run-level,
  noise substitution, mid/side, IMDCT, overlap and drain logic. Refusal messages
  retain the source's duplicated `wma: wma:` prefix where ASF annotates a codec
  error. No arithmetic correction or lossy tolerance is applied.
- Go packet slices/callback frames become borrowed fixed-capacity buffers and
  RandomAccessSource reads. The maximum packet/media-object caps are preserved;
  construction preallocates assembly, lookahead and output storage. PCM is
  reused interleaved float32. Caller owns the source; ByteBuffer regions work.
- Exact sample seeking replays from the beginning instead of using ASF's native
  index approximation. This preserves noise-generator and overlap history and
  permits arbitrary backward seeks at linear time cost. `Decoder.reset(true)`
  distinguishes this full restart from Go's state-losing midstream reset.
- ASF declared duration remains advisory and never truncates decoded PCM. Tags,
  chapters, general metadata APIs and persisted index APIs are not included in
  the audio stream API. These objects are skipped using their object framing.
- The shared FFT port includes only radix 4/2, the factors WMA's power-of-two
  transforms use. Go's arithmetic order and float32/float64 boundaries remain.
  Huffman tries and transform plans are initialized outside the decode loop.
- Parameter arrays are copied from WaxFlow's LGPL-2.1-or-later FFmpeg-derived
  data artifacts; THIRD-PARTY-NOTICES includes their provenance and LGPL text.
  No FFmpeg implementation source was used, and no fork fix was needed.

## WMA Lossless

- Ported integer Golomb, CDLMS/MCLMS, AC filtering, lifting, padding, raw PCM,
  tiling, skip and carry logic from `codec/wmalossless` at the same pin. Filter
  sums and updates remain wrapping 32-bit integers, including unsigned Golomb
  averages. No FFmpeg code or parameter tables are involved in this codec.
- Go's callback frame loop becomes `acceptPacket` / `nextFrame` / `finish`, with
  two bounded 1 MiB compressed carries and one reused interleaved PCM frame.
  This avoids accumulating a whole packet's potentially 1024 output frames.
  Packet sequence, restart, incomplete-carry recovery and latched error rules
  remain; ASF source ownership and replay-based exact seeking match the v1/v2 API.
- Tiling counts, filter orders, planar reconstruction and maximum-sized filter
  histories are allocated at open, replacing Go's per-frame slices/grow calls.
  Only active spans are cleared at seekable filter definitions. No signal-path
  optimizations or arithmetic changes are made.

## WMA Pro

- Ported the pinned `codec/wmapro` configuration, bit reader, band resampling,
  canonical Huffman books, vectors/run-level coefficients, scale factors,
  channel groups/matrices, IMDCT, overlap and packet continuation state.
  Float arithmetic and Go's long-frame retry/rollback behavior are preserved.
- The frame callback API uses a bounded reused interleaved output buffer in the
  ASF wrapper. Its capacity is the source's 256-frames-per-packet cap plus one
  carried frame; encoded input still streams through RandomAccessSource. Exact
  seeking replays from the beginning. ASF declared duration remains advisory.
- Tiling/group scratch arrays, vector magnitudes, compressed carry and long-frame
  rollback storage are preallocated at open. Go's diagnostic path counters are
  omitted; signal state and buffer rollback are retained. Transform plans are
  per decoder instead of a global mutex cache. No signal arithmetic is optimized.
- Four table files preserve LGPL-2.1-or-later FFmpeg data provenance through
  WaxFlow's pinned extraction; THIRD-PARTY-NOTICES records them. No FFmpeg decoder
  implementation was read and no fork bug/fix was required.

## WMA Voice

- Ported the pinned `codec/wmavoice` configuration/geometry, variable-bit-mode
  tree, independent/residual LSPs, LPC synthesis, pitch interpolation, pulse
  windows, noise, gains and postfilter. Source arithmetic, sample-count trims,
  spillover, drain and error recovery are retained. PCM is mono float32.
- The source's callback loop becomes `acceptPacket` / `nextFrame` / `finish`.
  This permits escaped superframe counts without accumulating unbounded PCM.
  A borrowed packet remains live until consumed; carry/join storage is allocated
  for one/two block-aligned packets at open. Output is one reused 480-frame buffer.
- Go stack scratch (LSP indices, window masks and DCT/DST vectors) is held on the
  decoder. Tuple gain returns become two fields. Diagnostic path counters are
  omitted. Transform tables/plans are per decoder rather than global once values.
  Constant-folded Go expressions retain their final rounded double values.
- The existing ASF demuxer supplies RandomAccessSource/ByteBuffer streaming.
  Sample seeking replays from the beginning, preserving predictive/noise/filter
  state exactly; declared ASF length remains advisory. Voice 10 and mixed WMA
  Pro speech/music payloads remain named refusals, as in the pinned source.
- Five parameter-table files retain LGPL-2.1-or-later provenance through
  WaxFlow's extraction. THIRD-PARTY-NOTICES records the data-only origins.
  No FFmpeg decoder source was read. All successful corpus PCM hashes match Go
  bit-for-bit, without a lossy tolerance or fork fix.

## AIFF container completion

- Extend the earlier IMA4-only AIFF-C entry point with pinned WaxFlow's fixed-unit
  AIFF/AIFF-C COMM and SSND parsing for PCM, float and G.711. Retain the existing
  Ima4 API as a facade over the shared parser. MP3's frame-walking branch is
  outside the requested subset and is not ported.
- Reuse the ADPCM BlockStream and G.711 decoder. Port the needed signed/unsigned
  integer and float unpacking from codec/pcm; channel permutation and encoder
  APIs are not needed here. Scratch and output are allocated at construction.
- Return interleaved integer or float buffers through separate typed decode
  methods, selected by stream info. Preserve FL64-to-float32 narrowing and valid
  integer depth. Sample seeks restore IMA4 history by replay and seek directly
  for independent PCM/G.711 frames. No WaxFlow arithmetic fix is applied.
- Tests serialize non-byte-aligned integers using the Go oracle's WAV packing:
  valid bits are left-justified in whole-byte output words before hashing.
  This changes neither the expected hash nor the decoder's right-justified PCM.

## DSD: Flick, dsf-meta and dff-meta

- Separate source pins and reproducible Rust oracle are documented in
  `docs/dsd/ORACLE.md`; this port does not derive DSD behavior from WaxFlow.
- The test-only `FlickExactDecimationPipeline` preserves third-order integer/fractional CIC
  arithmetic, wrapping Long accumulators, 512-tap FIR mirrored history and summation
  order, runtime Kaiser coefficient generation and float32 output. The source
  allocates CIC/channel/coefficient vectors in each call; JVM scratch and
  interleaved output are allocated once at open. Logging is omitted.
- DSF/DFF native data is read positionally through RandomAccessSource and
  rearranged into planar, MSB-first 4096-byte units, matching the harness.
  Physical DSF padding and following ID3/DIIN chunks are excluded from audio.
  Metadata editing and ID3 tag values are outside the codec API; only source
  stream information and physical channel labels are exposed.
- Native parser chunk structures become audio header fields. Bounds and
  arithmetic are checked before positional reads; malformed/truncated bounds
  have explicit JVM I/O errors. DFF chunks are advanced with even-byte padding;
  dff-meta's recognized property-chunk reader appears to omit this advance for
  odd payloads. The corpus's recognized properties have even sizes, so no
  claimed parity result relies on changing that suspected source behavior.
- PCM seeking replays all preceding byte/FIR state and retains the requested
  remainder in the borrowed block. DoP seeking computes the byte offset and
  alternating marker from the absolute carrier sample. The two cursors are
  independent. Neither mode is wired into playback.
- The container API admits the required DSD64/128/256 rates and documented
  Flick PCM targets. DSD512 is outside the corpus and this container API:
  Flick's three-byte DoP words drop one byte at every 4096-byte input boundary,
  so a faithful exact-seek contract needs a separate covered decision before
  exposing that rate. The low-level DopPacker retains the source's 32-bit mode.
- dff-meta's mono/stereo limit and DST refusal are retained verbatim. DSF's
  whole-byte sample_count/8 rule and lack of a delayed-tail drain are retained.
  Production retains partial decimation groups across calls, dropping only an
  incomplete group at EOF. The test-only Flick path keeps the original behavior.
- The production converter replaces CIC with a raw-byte lookup FIR, then sparse
  half-band and symmetric output-decimated FIRs. Tables are constructed once per
  rate and shared; channel state is retained, with no per-call allocation. DSD
  +/-1 maps to PCM +/-1, preserving Flick's DC level. Defaults are 88.2 kHz for
  DSD64 and 176.4 kHz for DSD128/256; explicit targets are unchanged. It is
  validated against generated signals rather than Flick's old PCM hashes.
  The user prioritized flat 20 kHz response over the incompatible -90 dB Flick
  waveform difference. See `docs/dsd/BYTE-TABLE-PERFORMANCE.md`; after three
  attempts the laptop measurement (0.136–0.141 RTF, DSD256 stereo) was accepted;
  the 0.10 target is dropped and the spec 002 phone gate remains pending.

### Pinned Flick filter does not meet the flat-audio specification

The exact port matches the pinned Rust PCM hashes and its own analytic
CIC/FIR response. However, Flick fixes its Kaiser FIR at 512 taps / beta 10 /
18 kHz cutoff while FIR input rate rises from 705600 Hz to 2822400 Hz. This
widens the transition in Hz. Measured 10 kHz gains for DSD64/128/256 are
-0.008059/-0.005267/-0.651034 dB; DSD256 fails the new ±0.1 dB passband
specification even at 10 kHz. Measured 19.5 kHz gains are -17.119633/-10.566493/
-8.071546 dB, explaining the earlier -4.40/-6.43/-8.00 dB two-tone difference.
A scalar gain cannot undo frequency-dependent attenuation.

Using 1 kHz + 10 kHz tones, a full 0–15 kHz spectral comparison after measured
fractional delay, gain and polarity alignment gives -66.795582/-70.333549/
-28.544328 dB. Fitted delays applied to Flick are 290.887187/472.824546/
563.793226 microseconds; fitted gains are 1.000470263/1.000301965/1.036051842;
polarity is +1 for all rates. Production matches the analytic flat generated
signal below -134/-144/-150 dB. Flick matches an independent analytic prediction
of its own CIC/FIR response below -111/-132/-142 dB without alignment fitting.
This is a source filter-design limitation relative to the new audio contract,
not a byte-order, normalization or delay bug in either port. The user chose
flat audio over preserving this roll-off. Test code retains the 0–15 kHz
comparison and asserts each decoder's corresponding analytic response; no
original hash expectation changed. See `docs/dsd/BYTE-TABLE-PERFORMANCE.md` and
`codecs/benchmarks/dsd-byte-table-comparison.tsv` for measurement details.

## libwavpack extension (5.8.1)

- Source: upstream release tag 5.8.1, commit
  `4827b9889665b937b6ed71b9c6c0123152cd7a02` (BSD-3-Clause).
- Hybrid entropy follows `read_words.c` and `entropy_utils.c`, retaining integer
  wrapping, unsigned shifts, median adaptation, bitrate balance, slow-level log
  lookup and zero runs. The existing pure-lossless fast path is unchanged.
- Upstream pointer/bitstream fields become reused Kotlin block state and the
  existing bounded reader; word output is decoded into preallocated storage.
- Hybrid lossy integer fixup follows `unpack.c`, including 32-bit redundant low
  bits and clipping before shifting. No correction stream is consulted in this
  first feature. CRC still checks the lossy block's own expected checksum.
