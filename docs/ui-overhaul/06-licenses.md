# Source license audit and copying policy

**VERIFIED IN SOURCE:** each repository's actual root LICENSE was read at the full revision below. The license file is the cited symbol/document; file-level headers noted separately. License observations are not legal clearance for provider content/assets. No code/assets were copied into Sistrum in Phase 0.

| Repository / revision | License evidence | PROPOSED use and obligations |
|---|---|---|
| Sistrum `7203f18deadf1ca589c03f1b41572a157a8f4ce3` | `LICENSE` GPLv3 text; metadata source SPDX GPL-3.0-or-later with upstream MIT attribution | Preserve project GPL policy and existing port notices |
| Exhale `166e2cc7c6a3bcfb944999571e1bc5e0e3c1a066` | `LICENSE` GPLv3; inspected Compose files carry GPL-3.0 headers and original-project notice | Visual/interaction reference; GPL-compatible adaptation only with per-file attribution, modification notices and corresponding source; do not copy icons/provider artwork blindly |
| SpotiFLAC-Mobile `5a8952659b247fd95f1914a56db60825657fe26d` | `LICENSE` MIT, Copyright2026 zarzet; `rust_backend/Cargo.toml` workspace MIT | Host/runtime architecture reference; retain MIT notice for substantial adapted material. Does not approve all native/transitive dependencies or content |
| SpotiFLAC-Extension `0290b263802d3418e45583612574b749289bf369` | `LICENSE` Apache-2.0; `NOTICE` SpotiFLAC Extension/Copyright2026 zarzet | Preserve license/NOTICE/attribution and mark modifications if porting licensed source. Verify individual package provenance; downloaded ZIPs lack their own license files |
| Flick `79da4ed76557c8ddf534e898480dde66bcc90334` | `LICENSE` MIT, Flick Player Contributors2026 | Library Settings visual structure only; retain notice if source adapted; no About/bio/audio-conversion port |
| BitChord `fe95bb60aa9cd205cf4ab8bd25a5bec71a595b4f` | `LICENSE` GPLv3 | Implement simple setting independently or GPL-compatible adaptation with attribution/source; no upstream asset bundle |
| Rhythm `6f26f963b82ed592ec28f05824bb7f12b65b82ea` | `LICENSE` GPLv3; inspected Kotlin files SPDX GPL-3.0-or-later | Reference matching/render lifecycle; compatible attributed adaptation allowed by constitution; don't copy credentials/token fallbacks or weak errors |
| WaxFlow fork `b7857aff88820ad37936026421d1641e64611dbe` | Owner-prescribed M4b attribution in NOTES.md; source/oracle audit belongs to moved tasks | Preserve unchanged task text, MIT per-file headers and THIRD-PARTY-NOTICES; audit decoder-only source provenance/oracles in A0/A1–A6, no encoder code |

## Runtime dependency review

**VERIFIED IN SOURCE / official upstream:** pinned Mobile depends on rquickjs0.14.0 (M02). [rquickjs upstream](https://github.com/delskayn/rquickjs) states MIT; [QuickJS official manual](https://www.bellard.org/quickjs/quickjs.html) states MIT. This is not a complete audit of Rust crypto/network/archive/JNI transitive distributions; raw transplantation is BLOCKED by no-native rule anyway.

[Rhino upstream](https://github.com/mozilla/rhino) states MPL2.0 and pure Java. Candidate production version/artifact, Android compatibility, transitive dependencies and [MPL obligations/secondary-license compatibility](https://www.mozilla.org/en-US/MPL/2.0/FAQ/) must be checked for the exact artifact before adding it. AndroidX JavaScriptEngine is a system-engine integration candidate; audit its chosen artifact LICENSE/NOTICE and WebView runtime dependency model before use. Exact candidate versions and all binary size/security properties remain UNKNOWN. No dependency was added or selected in Phase 0.

Haze2 already exists (S05/S21). Exhale's `com.kyant.backdrop` is a distinct proposed dependency, not automatically licensed/approved by Exhale GPL. Pin/version/license/API/performance audit is required before adoption; initial plan uses existing wrapper. Fonts/material icons/protected reference images are not copied.

## GPL distribution plan — PROPOSED

For modified GPL components, retain copyright/license headers, identify modifications and dates, supply GPL text/notices and the complete corresponding source/build/install information required by the applicable distribution method. Include modified source and required scripts, not merely a link to an unmodified upstream repository. Keep source offer/source access aligned with shipped binary revision. Audit every imported file and transitive dependency for incompatible restrictions; Apache2/MIT compatibility does not eliminate their notices. No distribution artifact is produced here.

## Content rights and unresolved blockers

[Extension compliance](extensions/10-compliance-and-licensing.md) records official Apple/Google/Wikimedia sources and Deezer/PDF extraction limitations. Code license does not authorize Apple motion/artwork, YouTube/Deezer portraits, lyrics copyright, tokens or offline redistribution. Wikipedia text needs per-page attribution/license/revision; image licenses are separate. Unknown caching/embedding permission blocks that action. Individual archive contributors, intended distribution channel, authenticity governance and current readable service terms remain UNKNOWN / NEEDS VALIDATION. These block provider shipping, not local UI planning.
