<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<!-- WaxLabel read port; Copyright (c) 2026 Cole Springer, MIT. -->
# Metadata

Pure-JVM, read-only WaxLabel port. Supports MP3 ID3v1 legacy sources and
ID3v2.2/2.3/2.4, WAV/RF64/BW64 INFO and ID3 chunks, AIFF/AIFF-C text and ID3 chunks, native FLAC and
Ogg Vorbis/Opus/FLAC comments, pictures, synced lyrics and chapters, and MP4/M4A
iTunes ilst/freeform/covr, QuickTime mdta/udta tags and Nero/QuickTime chapters. APEv1/v2 tags on Monkey's Audio, WavPack, Musepack and MP3, plus native Musepack
chapters, are supported. APE tags are legacy-only on MP3. ASF/WMA content descriptions, extended/nested metadata, pictures, markers and
audio properties are supported. Matroska/MKA/WebM supports scoped SimpleTags,
Info.Title, image attachments, default-edition chapters and audio properties.
DSF reads ID3 at its metadata offset; DFF reads DIIN artist/title and top-level ID3
chunks, including tags before audio. DSD properties follow pinned dsf-meta/dff-meta
container behavior. DFF DST and multichannel layouts are rejected as in that reference. WAV bext/iXML/cue and AIFF ancillary chunks are
reported as opaque native summaries, matching the pinned reader. There is no Android/player/scanner code.

```kotlin
FileChannel.open(path).use { channel ->
    val document = Metadata.read(FileChannelSource(channel))
    if (document.error == null) {
        val artists: List<String> = document.tags[TagVocabulary.ARTIST].orEmpty()
        val rate: Int = document.properties.sampleRate
        val covers: List<Picture> = document.pictures // metadata and SHA-256, no payload arrays
    }
}
```

Imports are under `me.misa198.airmedy.metadata`; `FileChannelSource` and
`RandomAccessSource` are under `me.misa198.airmedy.codecs.container`.

`Document` includes ordered canonical tags, native block summaries, audio properties,
pictures, chapters, synced lyrics, warnings, provenance families, legacy-only keys,
and an optional typed error. Chapters include language/IETF language and
hidden/disabled flags; provenance carries album/track/edition/chapter scopes. Collections and nested values are unmodifiable.
Unsupported/malformed data and I/O failures do not throw out of `Metadata.read`.
Callers own the source and must keep its contents immutable during reads.

Picture policy is `ReadOptions(pictures = Pictures.NONE | METADATA_ONLY | FULL)`.
METADATA_ONLY is the default and streams hashes without retaining image payloads.
FULL enables `Picture.bytes()` on demand; load it while the source is still open.
Each call returns a fresh array. NONE skips pictures entirely.

Properties expose codec/profile, sample rate, channels, bits, total samples,
duration in nanoseconds and rounded milliseconds, bitrate, Opus output gain in
signed Q7.8 units, and metadata padding. Empty/unknown fields are
zero or empty strings. MP4 total samples remain zero, matching the pinned public read
API; duration comes from the media header and edit list. QuickTime chapter titles
are metadata samples inside mdat, validated against audio/video sample ranges before
reading. ID3v1 is non-authoritative, as in the pinned upstream reader.

`SISTRUM-PATCHES.md` documents bounded probes, picture loading, limits, and the
precisely tested oracle differences. `docs/waxlabel/ORACLE.md` explains corpus setup,
comparison, and benchmarks. No test media belongs in Git.
