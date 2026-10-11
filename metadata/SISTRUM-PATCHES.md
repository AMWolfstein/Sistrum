<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<!-- WaxLabel read port; Copyright (c) 2026 Cole Springer, MIT. -->
# Sistrum metadata patches

Base: AMWolfstein/WaxLabel `cfbb45d13135a76f5df6781fdeec75f5cfe63bf9` (MIT).
This module is read-only and pure JVM. It does not depend on Android, call decoders,
write metadata, or connect to the player or scanner. `:codecs` supplies only
`RandomAccessSource`. Native blocks expose the same
ordered descriptions as `dump --native`; they are not an editable native document.

## PATCH-1 — picture loading policy and streamed hashes

Upstream eagerly clones pictures on parse and on public access. Sistrum defaults to
`ReadOptions(pictures = Pictures.METADATA_ONLY)`: parse type/description, sniff
MIME/dimensions, and stream SHA-256 through an 8 KiB scratch buffer. No complete
picture payload is allocated or retained. Hashes require reading the embedded
picture region, so METADATA_ONLY reduces memory rather than eliminating picture
I/O. `NONE` skips picture projection, sniffing, hashes, and invalid-picture warnings;
ordinary untransformed APIC payloads are not read at all. Tag-level unsynchronization
must still index its metadata region to find subsequent frames.

`FULL` enables `Picture.bytes()` as a bounded on-demand read, returning a fresh array
on each call. The caller must keep the immutable source open until the payload is
loaded. Other document fields are detached; no source lifetime is required to read
them. A sparse unsynchronization source stores checkpoints every 8192 decoded bytes
and one 8 KiB physical window rather than a whole-tag or picture buffer.

Tests: `defaultPicturePolicyStreamsInsteadOfMaterializing`,
`noneSkipsThePicturePayloadAndItsHash`, `fullReadsAreLazyRepeatableAndDetached`,
and every oracle record with art in both METADATA_ONLY and FULL. Large normal,
whole-tag-unsynchronized, and frame-unsynchronized covers are owned external inputs.

## PATCH-2 — fixed MP3 property probes instead of scanning audio

Upstream reads/scans the first 64 KiB of the MPEG region and fingerprints audio
ranges. Sistrum reads no compressed frame body or PCM sample payload and does not
fingerprint audio. It probes format markers, the first MPEG header and the agreeing
next header at its calculated offset, and the conventional Xing/Info/VBRI property
header positions. An unreadable leading tag permits one additional header probe
immediately after the known 10-byte ID3 header. Trailer detection probes `TAG`
before requesting its fixed 128-byte metadata block. Arbitrary leading junk cannot
be searched for a later MPEG header; properties are unknown with `no-audio` instead.

The pinned reader falsely identifies UTF-16 bytes in these truncated tags as MP1:

- `owned/truncated-mutagen-v23-469.mp3`
- `owned/truncated-mutagen-v23-937.mp3`
- `owned/truncated-mutagen-v24-692.mp3`

For these three inputs Kotlin reports `codec = "MPEG audio"`, no codec profile,
rate/channels/samples/duration/bitrate, native audio note `MPEG audio`, and one
additional `no-audio` warning. The malformed-leading-tag warning and every other
JSON field remain identical. `OracleParityTest.assertBoundedProbeDifference`
asserts this exact delta and the upstream MP1 premise; these are documented
oracle differences, not exact matches. The unmodified CLI JSON is retained.

Tests: the three differential assertions plus
`mp3OnlyReadsTagRegionsAndFixedPropertyHeaders` and `wavAndAiffSkipSamplePayload`.
This is the deliberate bounded-read tradeoff, not a dropped playback feature.

## PATCH-3 — JVM resource limits and typed errors

The default single tag/allocation ceiling is 64 MiB (upstream general allocation
ceiling: 256 MiB), with a 128 MiB read budget per input view and 100,000 frame/chunk
entries (same default breadth as upstream). A text frame cannot amplify into more
than 100,000 values. APIC MIME and description fields have separate 64 KiB ceilings. Vorbis comment
names are scanned with a 64 KiB header ceiling; comment packets, native FLAC
blocks and decoded picture blocks share the single tag ceiling. Ogg page
descriptors have a separate 64 MiB budget, as upstream. Sparse packet/base64
region inventories are bounded by `maxElements`.
These guards return `SIZE_TOO_LARGE` instead of allocating an unbounded JVM object
graph. The limits can be configured with `ReadOptions`; the text/header amplification
ceilings are fixed. Sparse transformed views have their own bounded read budgets;
these are not a global physical-I/O quota. FULL calls receive fresh budgets so they
remain repeatable and independent across callers.

Source I/O failures, stalled/invalid read contracts, and invalid data return a
`Document.error` (`IO`, `INVALID_DATA`, `UNSUPPORTED_FORMAT`, `SIZE_TOO_LARGE`)
rather than escaping `Metadata.read`. Fatal VM errors are not suppressed. Error
classes match CLI exits for malformed corpus records; messages do not contain a
CLI filename because the API accepts a source rather than a path.

Tests: `explicitByteAndElementLimitsAreEnforced`,
`oversizedTagsAreRejectedBeforePayloadAllocation`,
`amplifiedTextValuesAndPictureHeadersAreBounded`,
`sourceFailuresReturnTypedErrors`, `corruptSourceContractDoesNotEscape`,
`invalidOptionsAndLengthsDoNotEscape`, and `malformedAndTruncatedInputsNeverThrow`.

## PATCH-4 — API fields absent from CLI JSON

The CLI does not serialize total samples or picture hashes. Sistrum exposes both.
The oracle script builds a supplementary Go program against the same pinned public
read API to assert these two fields, while `waxlabel dump --native --json` remains
the reference for all fields it supplies. No supplement changes the CLI output.

Tests: all `OracleParityTest` records; PATCH-2 explicitly asserts zero total samples
where its false MPEG detection differs. Document collections, nested values, and
vocabulary sets are unmodifiable. `documentsAndNestedCollectionsCannotBeMutated`
asserts the immutable API.

## PATCH-5 — FLAC frame scanning omitted

Upstream scans encoded FLAC frame headers/tails to diagnose truncated essence and
separate trailing junk. Sistrum reads STREAMINFO and metadata blocks, probes a
possible ID3v1 trailer, and treats the remaining file extent as the encoded stream.
It does not scan frames or fingerprint essence. STREAMINFO remains authoritative
for samples, duration, channels, rate and depth; bitrate uses that structural extent.
Ogg scans page headers/lacing for granules and audio byte counts, skipping all audio
packet bodies, including a body shared with the last metadata packet.

These three owned inputs intentionally differ from the unmodified CLI JSON:

- `owned/vorbis-tail-junk.flac`: upstream separates 4096 bytes of junk. Kotlin has
  no `trailing-bytes` warning/native tail block, and reports 169520 rather than
  5680 bits/s because its structural extent includes those bytes.
- `owned/vorbis-truncated-audio.flac` and `owned/vorbis-truncated-8427.flac`: Kotlin
  omits only the upstream `truncated-audio` warning. All other fields, including
  hashes and STREAMINFO samples, match exactly.

`VorbisOracleParityTest.expected` asserts the precise upstream premise and allowed
delta for each; every unaffected field is compared. Guarded-source tests
`flacReadsNoEncodedFramesEvenForTruncatedOrJunkAudio` and
`oggReadsPageHeadersAndTagPacketsWithoutAudioBodies` reject payload reads.

PATCH-1 also applies to native FLAC and base64 comment pictures. Packet source views
retain source offsets; base64 views validate and decode in bounded chunks, ignoring
CR/LF as upstream does. METADATA_ONLY retains no complete image or encoded-image
string. NONE skips those payloads and invalid-picture warnings; picture-comment
native entries in Ogg are absent when no pictures are projected. FULL clones the
packet/base64 view budgets per payload load. Tests:
`metadataOnlyStreamsNativeAndBase64ArtInBoundedBuffers`,
`noneSkipsNativeAndBase64PicturePayloads`,
`fullBase64ReadsHaveFreshBudgetsAndDetachedBytes`, plus all picture oracle cases.

The native API additionally exposes Ogg's `unit = "pages"` descriptor; the pinned
CLI JSON omits units, so its JSON projection does likewise. Error messages remain
source-relative; malformed corpus error classes are compared with CLI exits.

## PATCH-6 — validate QuickTime chapter samples before reading titles

Upstream trusts the chapter track's `stco`/`co64`, `stsc` and `stsz` geometry and
reads text at the resulting file offsets. A corrupt table can alias encoded audio
or file-header bytes. Sistrum resolves only a text/tx3g sample entry with a text,
subtitle or subtext handler. Each title sample must fit wholly inside one mdat and
must not overlap any other track's declared media samples. Missing/invalid media
geometry prevents title reads. Only the length prefix and bounded UTF-8 title are
read; style trailers and audio bodies are skipped. Structural sample tables are
metadata; neither audio decoding nor essence fingerprinting is performed.

Two owned inputs intentionally differ from untouched pinned CLI JSON:

- `owned/mp4-chapter-audio-alias.m4a`: the first chapter's upstream title `LEAK`
  comes from generated bytes in an audio sample. Kotlin leaves that title empty.
- `owned/mp4-stco-structural.m4a`: the second chapter's upstream title contains
  ftyp/free header bytes (`somiso2` followed by zero bytes and a free header).
  Kotlin leaves that title empty.

Every other field, including chapter times, source-conflict warnings and native
chapter descriptors, remains identical. `Mp4OracleParityTest.expected` asserts
these exact upstream premises and only removes the specified titles from its local
comparison copy. `chapterOffsetsCannotAliasAudioOrStructuralBytes` uses a source
that rejects mdat reads; `onlyChapterTitlesAreReadInsideMdat` permits only the two
known title regions in the legitimate generated chapter file. `audioPayloadIsNeverRead`
rejects every mdat body read for normal AAC, ALAC, FLAC, PCM/float and fragmented files.

PATCH-1 applies to covr (including indexed mdta covr): metadata/hash reads stream,
NONE reads only box headers and skips images, FULL uses fresh bounded source views.
Tests `scanStreamsLargeCoversAndNoneSkipsAllImageBytes` and
`fullLoadsAreLazyAndHaveIndependentReadBudgets` cover large art and repeated loads.
PATCH-3 also bounds MP4 atoms, table entries, contributions, data regions, freeform
labels (64 KiB), nesting (64 levels) and read bytes. Existing upstream keys/language
lists retain their 4096-entry caps and chapter times retain the 65536-sample cap.

MP4 exposes zero total samples as the pinned public API does; it does not infer a
sample count from duration. Its two known high-resolution PCM rate errors remain
unchanged: parity targets the pinned reader, not ffprobe. No new deviation is
claimed for those upstream errors. Classic QuickTime udta and indexed mdta tag
stores are included because they are part of the same pinned MP4 READ path.

## Implementation boundary

Step 1 includes canonical vocabulary/aliases, ID3v1 legacy provenance,
ID3v2.2/2.3/2.4 text/flags/unsynchronization/extended headers/footer, APIC/PIC,
CHAP/CTOC, SYLT, MP3 properties, and embedded ID3 in ordinary WAV/AIFF/AIFF-C.
Minimal INFO/AIFF text fallback and PCM framing preserve the pinned embedded-ID3
projection; full IFF recovery, RF64/BW64, bext, and compressed property details
remain step 5. APEv2 on MP3 remains step 4. Step 2 adds native FLAC, Ogg Vorbis/Opus/FLAC,
Vorbis-comment aliases/number pairs/provenance, native and base64 PICTURE, LRC
synced lyrics and CHAPTER comments. Leading ID3 before FLAC is detected and kept
non-authoritative. Ogg gain, pre-skip, chained streams, continued packets, shared
header/audio pages, comment padding and trailing bytes follow the pinned read
projection. Step 3 adds MP4/M4A ilst/freeform/covr, native summaries, audio properties and
Nero/QuickTime chapter tracks, plus classic udta and indexed mdta tags. Remaining
readers (steps 4–8) are not ported yet. No application feature was removed.
