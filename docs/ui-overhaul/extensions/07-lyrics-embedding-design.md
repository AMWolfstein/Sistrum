# Trusted native Sistrum lyrics tag writer

“Native Sistrum” here means trusted application Kotlin code, not JNI/native binaries. **VERIFIED IN SOURCE:** S16/S20 in [source map](../01-reference-source-map.md) show readers only; `Metadata.read` at S `7203f18deadf1ca589c03f1b41572a157a8f4ce3` explicitly read-only. Its projected Document cannot alone reconstruct every raw frame/chunk. Working-copy owner D-31 grants persisted **read** access to chosen trees; read access does not imply write permission. All writer behavior below is **PROPOSED**.

## Format support contract

| Format | Planned single-track representation | Preservation and release gate |
|---|---|---|
| MP3/ID3v2 | USLT for plain text. SYLT millisecond timing only after reader+writer interoperability tests; optionally LRC text in USLT explicitly labeled convention, not native SYLT | Preserve version, encoding, flags, unrelated/unknown frames, APIC, padding/extended headers/unsynchronization, audio frames and trailers. Refuse unsupported tag variants/encryption rather than rewrite lossily |
| FLAC | Vorbis-comment `LYRICS` or existing compatible lyrics key; line LRC text convention, plain otherwise | Preserve STREAMINFO/MD5, seek table, cuesheet, pictures, unknown blocks and audio frames; adapt block lengths/last flags and metadata padding without audio transcode |
| Ogg Opus | `OpusTags` comments, `LYRICS`/LRC convention | Repage only necessary headers with correct lacing/continuation/sequence/CRC; preserve OpusHead/pre-skip/gain/mapping, audio packets, granule positions and other streams; chained/multiplexed streams refused until tested |
| Ogg Vorbis | Comment header `LYRICS`/LRC convention | Separate Ogg writer tests for setup/header/audio packet preservation; not automatically supported merely because Opus works |
| M4A/ALAC/AAC MP4 | Existing `©lyr`/iTunes freeform lyrics text. LRC text is convention; no blanket native word-sync claim | Preserve cover/unknown atoms, chapters, gapless/edit/sample tables/fragmentation; handle stco/co64 offsets if moov growth shifts data. Initially refuse fragmented/encrypted/unsupported layouts with sidecar fallback |
| WAV/AIFF | Reader may find USLT in ID3 chunks, but writer initially unsupported | Sidecar/cache; future chunks/size/RF64 and ID3 preservation tests required |
| APE/WavPack/Musepack/WMA/ASF/DSF/DFF/Matroska, CUE virtual tracks and other scanned formats | Initially no embedded writer; cache and authorized sidecar when meaningful | Report exact unsupported container/reason. Shared-file CUE tracks need per-track semantics; never embed one track's lyrics across the whole image silently |

[ID3 frame specification](https://id3.org/id3v2.4.0-frames) defines distinct USLT and SYLT structures. [Vorbis comments](https://xiph.org/vorbis/doc/v-comment.html) allow UTF-8 extensible fields; a field called LYRICS is not standardized word synchronization. [RFC7845](https://www.rfc-editor.org/rfc/rfc7845) specifies Opus encapsulation/comments. Existing MP4 reader support (S16) is evidence for reading ©lyr/freeform, not a standardized MP4 timed-lyrics writer. Format support is exposed only after its own preservation gate passes.

## Storage capability and consent

Use final 002 tree-URI mapping and `:metadata` reads; never resolve content URIs into guessed filesystem paths. Plan includes canonical document identity, fingerprint, size, existing lyrics, safe writable target and provider capabilities. Check `persistedUriPermissions` and document flags. Re-request write access for the same D-31 tree/document when needed, taking only flags actually granted; revoked/read-only/moved documents return PermissionRequired/ReadOnly/Stale. Do not request MANAGE_EXTERNAL_STORAGE or restore excluded folders.

For eligible MediaStore media use `MediaStore.createWriteRequest` and its user result; do not apply it to arbitrary SAF document URIs. SAF uses a user-granted write tree/document and supported create/rename/delete operations. Android documents [write requests](https://developer.android.com/training/data-storage/shared/media) and [persisted grants/provider operation flags](https://developer.android.com/training/data-storage/shared/documents-files). Neither gives generic atomic rename or transactional replacement across all providers.

Preview shows candidate/source/timing/rights, existing content comparison, chosen destination, required access and backup space. Cache is separate from Embed and Save .lrc. Never overwrite any existing lyric tag or sidecar without explicit replacement confirmation bound to current fingerprint and exact content. No automatic embedding or batch action. Only trusted storage code receives URI/FD; extension sees no writable capability. Provider persistence rights must be Allowed; Unknown/Denied offers only permitted actions. User-authored/local content can be handled under the user's confirmed rights.

## Recoverable transaction

1. Preflight: acquire per-storage-identity lock; refuse Busy for active playback/analysis source without stopping it; revalidate fingerprint/grant/rights and existing-lyrics confirmation. Check bounded size and free space for original backup + full staged output + margin; unknown free space/provider behavior requires staging capacity check or refusal, not assumption.
2. Prepare: stream original to durable private backup; compute complete backup and audio-essence hashes, unrelated raw metadata/art hash and format inventory. Persist journal `PREPARED` before any destructive operation. No in-place patching of originals.
3. Transform in `:metadata`: bounded streaming structural edits into private staged file. Preserve raw unknown data, no audio decode/re-encode. Cancellation during preparation/transform discards staging, original unchanged; cancellation must propagate.
4. Verify staged file with independent structural fixtures/readers plus app read: requested lyrics exact, all unrelated tags/art/chunks preserved, packet/audio-essence hashes equal and duration/seek/gapless unaffected. App reader alone is not the oracle. Persist `VERIFIED` with hashes.
5. Publish only by a provider-specific tested protocol. For reliable same-directory rename: create temp sibling, copy verified bytes, verify/flush where supported; journal before original→backup rename and temp→target rename, verify each reported URI. Atomic replacement is UNKNOWN for generic SAF and must not be promised. For MediaStore/in-place content replacement use complete durable backup and journal before truncate/copy, report non-atomic/recoverable behavior. If a provider cannot support a demonstrably recoverable path, refuse Embed and offer cache/sidecar/export instead.
6. Cancellation in publish becomes `RecoveryRequired` or finishes verified commit under bounded non-cancellable cleanup; never abandon an original truncated file. On failure restore backup, verify restored hash, keep recovery data if restore fails. Process-death restart scans journal and resumes recovery before new writes. Never delete the only good copy.
7. Reopen published URI and verify actual bytes/lyrics/essence/unrelated metadata. Mark `COMMITTED`; reconcile library URI/fingerprint without losing track IDs/stats/playlists, refresh single-track metadata/lyrics and shared artwork. Same audio essence should retain analysis values after final-002 invalidation contract is checked. A library refresh failure is `WrittenRefreshPending`, not fake failure with a blind second write.
8. Delete staging and redundant backup only after verified commit/reconciliation or a user-visible retention policy. Pending recovery data is never LRU cache. Journal/logs redact paths/content. Provide explicit recovery action and useful reason if permissions are revoked mid-commit.

## Sidecar design

Resolve siblings through granted document tree; `.lrc` for synchronized text, plain text export labeled accurately (.txt if selected), UTF-8 encoding. Canonical audio basename derives from trusted document metadata, not provider title. Prevent name/path traversal and collisions. Existing .lrc/.txt is previewed and replacement explicitly confirmed; write to temp sibling and verify then publish with provider-specific recovery. Unsupported/create-denied tree offers user-selected export or cache, never silent success. Preserve current sidecar lookup priority and make it URI-aware after 002.

## Tests and blockers

Tests-first per format use independent raw fixtures and before/after audio-packet hashes, art hashes, unknown/multiple tags, big-endian/encoding/large metadata and malformed sizes. Fault injection at every journal transition covers kill/cancel/out-of-space/permission revoke/partial write/rename failure/verification mismatch. Device tests only on `.qa` at milestone boundary; sample phone is not universal SAF proof.

**BLOCKED:** real-file writing until D-31 is complete, preservation/recovery tests pass and source rights allow embedding. SYLT and unsupported containers remain unavailable until explicit support tests. **UNKNOWN / NEEDS VALIDATION:** provider atomicity, URI identity after rename, space APIs and final scanner fingerprint behavior. No music files or code were modified in Phase 0.
