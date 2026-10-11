<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<!-- WaxLabel read port; Copyright (c) 2026 Cole Springer, MIT. -->
# Metadata

Pure-JVM, read-only WaxLabel port. Supports MP3 ID3v1 legacy sources and
ID3v2.2/2.3/2.4, ID3 chunks in ordinary WAV/AIFF/AIFF-C, native FLAC and
Ogg Vorbis/Opus/FLAC comments, pictures, synced lyrics and chapters, and MP4/M4A
iTunes ilst/freeform/covr, QuickTime mdta/udta tags and Nero/QuickTime chapters. APEv1/v2 tags on Monkey's Audio, WavPack, Musepack and MP3, plus native Musepack
chapters, are supported. APE tags are legacy-only on MP3. Other containers
remain reserved for the subsequent ordered steps. There is no Android/player/scanner code.

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

Imports are under `com.amwolfstein.sistrum.metadata`; `FileChannelSource` and
`RandomAccessSource` are under `com.amwolfstein.sistrum.codecs.container`.

`Document` includes ordered canonical tags, native block summaries, audio properties,
pictures, chapters, synced lyrics, warnings, provenance families, legacy-only keys,
and an optional typed error. Collections and nested values are unmodifiable.
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
