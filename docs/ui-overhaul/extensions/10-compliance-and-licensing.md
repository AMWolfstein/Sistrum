# Extension compliance and content rights

Audit date 2026-10-15. Repository license evidence/revisions are in [06-licenses](../06-licenses.md) and [source map](../01-reference-source-map.md). **VERIFIED IN SOURCE** labels code/license text or official published policy reviewed; **PROPOSED** labels release controls. Legal clearance and service-live behavior are not asserted.

## Runtime distribution

The [Google Play policy](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en) permits an interpreted-code distinction while requiring runtime-loaded code to respect all policies. It does not pre-approve this registry, its download functions or unauthorized service access. Proposed release scope excludes downloaded native/dex/JAR code, app self-updates, stream downloading, DRM bypass and arbitrary Android access. Isolation, authenticity, capability constraints and end-to-end policy review remain release gates. Intended distribution channel is UNKNOWN; confirm it before production runtime selection. Side-loading does not remove copyright/service terms.

## Content decisions

| Source | Technical evidence | Rights finding / release requirement |
|---|---|---|
| Apple Music | X02 token scraping, user subscription token for lyrics, motion HLS metadata | [Apple agreement §3.3.9 MusicKit](https://developer.apple.com/support/terms/apple-developer-program-license-agreement/) restricts MusicKit content downloading/modification/use. Scraping another client's developer token is not approved authorization. Local-player enrichment, motion offline cache and lyric embedding permission UNKNOWN; BLOCKED until authorized access and specific permitted uses documented |
| YouTube Music | X03 artist browse header retrieval; R03 matching concept | [YouTube developer policies](https://developers.google.com/youtube/terms/developer-policies) impose source/branding and API data obligations. Undocumented Music endpoints are not shown authorized by those API terms. Persistent portrait caching/redistribution and endpoint access UNKNOWN; BLOCKED pending approved access/retention policy |
| Deezer | X04 getArtist picture fields; signed-session/file requirements | [Developer terms PDF](https://cdn-content.dzcdn.net/pdf/CGU-developers.pdf) retrieved but text extraction was garbled; do not claim full review/approval. API access, caching, image display and distribution terms need current readable confirmation; BLOCKED for shipping until reviewed |
| Wikipedia | R01 summary endpoint heuristics, artist biography extension proposed | [Wikimedia terms](https://foundation.wikimedia.org/wiki/Terms_of_Use) describe reusable text licensing, commonly CC BY-SA4.0. Store page URL/title/revision/language/license and attribution; mark modifications and comply with applicable license. Image licenses are separate and not imported by this feature |
| LRCLIB | S10 exact/search APIs; [docs](https://lrclib.net/docs) accessible but web text not extractable | API accessibility and client/library code license do not establish ownership of contributed lyrics. Display/cache/embed/sidecar rights UNKNOWN; embedding disabled unless separately allowed |
| KuGou | S11 search/download code | Current official API authorization/content license not verified. Preserve existing behavior in regression scope; new permanent embedding/export gate remains BLOCKED until reviewed |
| Local/user-authored lyrics | Existing embedded/sidecar content and user-entered text | Trusted single-track writer can be tested with owned/public-domain/synthetic lyrics; explicit ownership/authorization action is a proposal, not blanket clearance of third-party text |

Provider rights should be capability-specific, versioned policy records: display, temporary cache, durable selected cache, sidecar and embedding are different uses. Unknown is not Allowed. Keep a source URL and attribution in preview and cache; do not store secrets there. Content licensing controls do not delete existing selected lyrics automatically. New restricted providers remain unavailable until cleared; local/built-in regression work continues.

## Package authenticity and notices

Registry/hash agreement checks tampering relative to supplied bytes, not publisher authenticity. Packages contain manifest/index/icons but no verified signature or individual license file. X has Apache-2.0/NOTICE; contributor/package-level provenance still needs confirmation before redistribution/adaptation. Ship only separately reviewed/approved package digests, or use explicitly licensed trusted adapters. Never copy protected provider icons/artwork, proprietary credentials or subscription token extraction instructions into product onboarding.

## UNKNOWN / NEEDS VALIDATION / BLOCKED

No legal approval, Google Play acceptance or provider permissions were obtained in Phase 0. General package activation and restricted persistent content features remain BLOCKED by the gates above. Code-reference use and independent local UI/storage planning can proceed without falsely claiming these blockers resolved.
