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
than 100,000 values. APIC MIME and description fields have separate 64 KiB ceilings.
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

## Implementation boundary

Step 1 includes canonical vocabulary/aliases, ID3v1 legacy provenance,
ID3v2.2/2.3/2.4 text/flags/unsynchronization/extended headers/footer, APIC/PIC,
CHAP/CTOC, SYLT, MP3 properties, and embedded ID3 in ordinary WAV/AIFF/AIFF-C.
Minimal INFO/AIFF text fallback and PCM framing preserve the pinned embedded-ID3
projection; full IFF recovery, RF64/BW64, bext, and compressed property details
remain step 5. APEv2 on MP3 remains step 4. The dispatcher is limited to the step-1 signatures; the remaining container
readers and detection after stray leading ID3 are not ported yet. No application
feature was removed.
