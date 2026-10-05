# ADR-002 — Decoder Registry

Status: Accepted (owner, 2026-10-05) · Date: 2026-10-05 · Spec: FR-060…068, SC-002, SC-014, SC-016 ·
Research: D8, `research/platform-codecs-cph2307.md`, `research/pure-kotlin-formats.md`

## Context

No FFmpeg in Media3. Each format/codec needs an ordered list of providers (platform first, then Kotlin), the scan
must admit only playable files on the new engine, and 002 adds providers without scanner changes. Platform codec
sets differ per device (the CPH2307 has no ALAC decoder).

## Decision

- `player/decoders/DecoderRegistry.kt`: one declarative table `FormatKey(format, codec) → List<ProviderId>`,
  ordered. `format`/`codec` are the scanner's existing labels (`audioFormatOf`, `realCodec`).
- Provider interface (contract: `contracts/decoder-registry.md`): `id`, `isAvailable()` (device probe),
  `extractors()` (Media3 `Extractor` factories it adds), `renderers()` (audio renderers it adds; empty in 001),
  `refusal(file)` (by-name refusal reason or null).
- 001 providers:
  - `platform`: available when a Media3 default extractor reads the container and `MediaCodecList` has a
    decoder for the codec's MIME (probed once per process).
  - `kotlin-aiff`: port of Choir's `AiffExtractor` (GPL-3.0-or-later, pin `f2e96fd`), Pattern A (`AUDIO_RAW`).
    AIFF / AIFF-C `NONE`, `twos`, `sowt`, 8/16/24/32-bit integer PCM; refuses float and compressed AIFF-C by
    name. Attribution header + third-party notices entry.
- `Media3Engine` builds its `ExtractorsFactory` (Media3 defaults + registry extractors) and
  `RenderersFactory` (platform first; registry renderers later, 002) from the registry.
- Scan: while the new engine is selected, `MediaStoreLibraryScanner` asks `registry.resolve(format, codec)`
  before tag reading; no provider → file skipped and counted by `(format, reason)`. With the current engine the
  scan is unchanged. Changing the engine switch starts a rescan. The `SkippedFilesSummary` lives in DataStore
  (no Room change, FR-074) and is shown after a scan and in settings.
- Decode failure → `EngineEvent.Error(provider, format, cause)` → normal error + skip; logged with provider name.

## Consequences

- A format appears by adding a table row + a provider; the scanner never changes (FR-066).
- ALAC-in-M4A is skipped on devices without an ALAC decoder until 002's Kotlin ALAC provider.
- Containers whose inner codec the scan can't tell (e.g. WAV with MS-ADPCM) reach the decode-failure path.

## Alternatives

- MIME-only keys (M4A hides ALAC/AAC). One provider per format (no fallback order). Admitting everything and
  failing at playback (rejected by the owner: unsupported files must not appear).
