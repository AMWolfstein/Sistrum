# Registry and package audit

**VERIFIED IN SOURCE:** [source map](../01-reference-source-map.md) M04/M05/X01, M SHA `5a8952659b247fd95f1914a56db60825657fe26d`, X SHA `0290b263802d3418e45583612574b749289bf369`. Remote registry is an independently identified artifact, not implicitly pinned by X.

## Observed registry snapshot

Fetched default URL `https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/registry.json` on 2026-10-15; local copy `/tmp/sistrum-refs/registry/registry.json`. SHA-256 `c7f7428d620ec23e83547b24974283e15ed68b35ce9007349a765e22ed2eef73`; JSON `version: 1`, `updated_at: 2026-10-11T00:00:00Z`, 12 extension entries. Timestamp is publisher data, not proof of freshness. Attempt to resolve zarzet main through GitHub API timed out; that repo commit is UNKNOWN. Artifact hashes below are reproducible independent identities. Every downloaded package matched a package at pinned X byte-for-byte, so member source findings also have X provenance.

Root keys: `version`, `updated_at`, `extensions`. Entries: `id`, `name`, `display_name`, `version`, `description`, `download_url`, `sha256`, `category`, `tags`, `downloads`, `updated_at`, `min_app_version`, optional `icon_url`. Eight entries use zarzet raw URLs; Bugs/Melon/Genie/JioSaavn use spotiflacapp raw URLs. Registry minimum-app version and manifest `minAppVersion` are distinct values (e.g. Spotify registry 4.3.0 vs manifest 4.2.3); neither is a Sistrum host API version.

| ID | Version | Downloaded archive SHA-256 | Published comparison |
|---|---|---|---|
| spotify-web | 1.10.2 | `26f4f8de8efae21d4668cac07e57e82965d796c36c9534ee6828c6efba67f81e` | MATCH |
| amazon | 2.3.11 | `39ec454b07b5d881aa844ab6f2aa5eed6cff120bdecf49d10bd2386355de3329` | MATCH |
| apple-music | 1.4.12 | `1c22b8155639be2abe01afcd30b9afbc7e7a78e89d07660ab9964a9ba9ef0dbc` | MATCH |
| soundcloud | 1.0.10 | `88a015a088727e7d8ccf44d347e555e6ff5e845667b2e35c57c109555fbc6652` | MATCH |
| ytmusic-spotiflac | 2.4.1 | `da7e41e45a73e52d2f40507e30372383c8f13872132e36af69f7a69fef01ebbb` | MATCH |
| deezer | 1.3.5 | `582ea3d122d6d6b48023317a745a2497952a05ea960cc08155850ade3d74fb79` | MATCH |
| qobuz-web | 1.2.21 | `d957759c849d9631caa029cf2279635d44bd468af11a2df9cd13088cd721afc2` | MATCH |
| tidal-web | 1.2.7 | `83e3aa93c59a8e16fa3da85d972cb8ba95e4f29992bd756b18f61269714080db` | MATCH |
| bugs-music | 1.6.2 | `72bc4ff38856568223da676374a95b5f79cc8a74569edabc111c82da3e215b1e` | MATCH |
| melon-music | 1.2.4 | `716ba7390e0e0c5ae1db80cd57bb56829c82b769d715d0538de1ec15ee6c4fc1` | MATCH |
| genie-music | 1.0.29 | `d337129bcd37686982ea44d22189bebcd7ceca99fb1b30a141114b32e5fd8f5f` | MATCH |
| jiosaavn | 1.0.6 | `440f63c3bc6e5473d458c84a650fabcb4b9f62c38556a1c2cb2e5fdbed682c9c` | MATCH |

Downloaded all listed packages to `/tmp/sistrum-refs/registry/<id>.sflx`, without executing code. Every archive has root `manifest.json` and `index.js`; some contain icon.png/jpg. Audit reading only selected bounded root members avoided arbitrary extraction. `.sflx` is ZIP; upstream also accepts `.spotiflac-ext` (M04). No package-specific LICENSE/signature/key file was present among these archive members. Repository license alone is not a verified individual contributor/content-rights grant.

## Manifest and entry contract — VERIFIED IN SOURCE

Manifests use `name` as identity (not an assumed `id` field), `displayName`, `version`, description/homepage/author as present, `type` arrays (`metadata_provider`, `lyrics_provider`, `download_provider`), `permissions.network` domain arrays, `storage`/`file` booleans, `minAppVersion`, optional `requiredRuntimeFeatures`, capabilities/searchBehavior/urlHandler/settings. Root entry is `index.js`, not a manifest `main` field. Settings have key/type/default/label/description. Registration is global `registerExtension(object)` with lifecycle/method functions.

Required runtime features include signedSession@1/@3, sessionGrant@1, preparedContext@1, patternedFileTransform@1 and downloadSegments@1 on some packages. Full declared requirements cannot be ignored merely because one desired method looks simple. No signature/authenticity mechanism was found in the sampled registry/package artifacts; SHA matching a value controlled by the same publisher does not authenticate that publisher.

Upstream package inspector bounds 2,048 entries, 256 MiB extracted, 1 MiB manifest; repository download cap 64 MiB, worker JSON input 8 MiB (M02/M04). These are upstream observations, not recommended Sistrum limits or complete security certification.

## PROPOSED parser and validation

Accept only known registry schema version; fail closed on duplicate IDs/JSON keys, missing required fields, invalid versions or invalid 64-hex hashes. Preserve last-valid snapshot on unavailable network/invalid response. Validate HTTPS URLs and each redirect/address through the broker; icons are untrusted too. Canonicalize origin and namespace IDs so custom registries cannot shadow defaults. Strictly compare registry ID/version with manifest.name/version; mismatch requires refusal, never a silent rename.

Sistrum compatibility uses an explicit host API profile and a package-hash allowlist/review record, separate from Sistrum app SemVer. Until upstream provides a host API range, compatibility is unknown unless a POC-tested exact package hash is approved. Unsupported required features/permissions cause Incompatible with reasons, not feature stubs that falsely return success.

Proposed limits: registry 1 MiB/100 entries, archive 16 MiB, expanded 32 MiB/64 entries, manifest 128 KiB, index.js 2 MiB, compression ratio 100:1, bounded actual streamed expansion. Reject absolute/drive/UNC/backslash-traversal paths, `..`, NUL, symlinks/hardlinks/devices, nested archives, duplicate/case-colliding paths and encrypted archives. Only declared static members; no native binaries/dex/JAR/bytecode. Validate before create-new extraction in private staging; never evaluate code during discovery or inspection. Cancellation cleans staging only. See update/recovery design for activation.

## UNKNOWN / NEEDS VALIDATION / BLOCKED

Registry publisher identity, signing authority, upstream future hashes and package contributor licensing remain unresolved. Proposal: explicit audited package digest approval signed by a Sistrum-controlled trust key or bundled approval catalog; upstream unsigned entries remain discoverable but non-executable until approved. Owner must decide trust governance; no key is invented or embedded in Phase 0. Broader registry compatibility is BLOCKED until runtime/security/authenticity gates pass.
