# Analysis port changes

- Source pins and algorithm definitions are in `docs/analysis/ORACLE.md`.
- Independent pure-JVM PCM API; no decoder, Android, Room, scanner or player dependency.
- Desktop oracle has a standalone cgo transport around the unmodified Airmedy
  header; it is external and never shipped.
- The research's BeatRoot suggestion is superseded by the owner's exact aubio requirement.
- Spec 001 M6 separately ports a BS.1770 loudness meter into androidApp from
  WaxFlow. This module's FFmpeg-derived loudness/LRA/true-peak implementation
  overlaps. Unification can happen later after measuring definition, channel
  layout, EOF, checkpoint and tolerance parity; neither module depends on the other.
- These ports read LGPL/GPL sources and are not clean-room contributions to WaxFlow.
