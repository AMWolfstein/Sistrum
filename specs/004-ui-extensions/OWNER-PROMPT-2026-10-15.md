# Owner master prompt (2026-10-15), verbatim

Corrections that override parts of this prompt: `NOTES.md` in this folder.

---

CODEX MASTER PROMPT

Sistrum UI Overhaul + SpotiFLAC Extension Runtime + Metadata, Motion Artwork & Lyrics Integration

PHASE 0 — Architecture Audit, Planning & Verification ONLY

You are the lead Android architect, Jetpack Compose specialist, extension-runtime engineer, security reviewer, and technical planner for Sistrum.

Your task is to inspect the existing Sistrum codebase and reference repositories, then produce a complete, technically verified implementation plan.

DO NOT implement application features during this execution.

Do not assume that previously discussed features, APIs, providers, or extension capabilities exist without verifying the actual source code.

---

1. PROJECT CONTEXT

Sistrum is an offline-first Android music player built with Kotlin and Jetpack Compose.

The application already contains:

- Local music library and scanner.
- Local playback engine.
- Database and persistent settings.
- Existing lyrics support.
- Existing Find Lyrics functionality using two services.
- Embedded and/or sidecar lyrics handling.
- Mini-player and full-screen player.
- Existing navigation and animations.
- Existing Haze/blur behavior.
- Light/Dark/System appearance settings.

Some of these capabilities may be incomplete or implemented differently than expected. Verify the source before documenting them as established facts.

A separate agent is working on a Media3/WaxFlow migration.

The UI and extension project must not interfere with that migration.

Existing Git worktree

Use:

"/home/amwolfstein/Documents/GitHub/Sistrum-UI"

Expected branch:

"feature/ui-overhaul"

The migration checkout is separate.

Before making any changes:

1. Inspect Git status, branch, and HEAD.
2. Verify the worktree is isolated.
3. Read existing project instructions.
4. Inspect the current planning documents.
5. Identify changes made since the original Phase 0.
6. Preserve all existing uncommitted work.

Never switch branches, reset, clean, stash, merge, rebase, or push without explicit authorization.

If the expected worktree is unavailable, report the problem rather than modifying another checkout.

---

2. GRAPHIFY-FIRST SOURCE ANALYSIS

Use Graphify if available.

Inspect its actual installed commands and capabilities before invoking it.

Reuse or refresh the existing index where appropriate.

Analyze:

- Compose screens and navigation.
- Artist, Album, Playlist, Home, Library, and Search screens.
- Mini-player and Now Playing.
- Existing playback interfaces.
- Existing lyrics providers.
- Find Lyrics screen and ViewModels.
- Lyrics models and repositories.
- Embedded/sidecar lyrics readers and writers.
- Network clients.
- Metadata and artwork repositories.
- Cache infrastructure.
- Room database and migrations.
- DataStore settings.
- Existing Haze and glass components.
- Gradle modules and dependencies.
- Background workers and permissions.

Use targeted source reads after graph exploration.

Do not infer architecture solely from class names.

Document actual paths, symbols, and call chains.

---

3. REFERENCE REPOSITORIES

Inspect these repositories at pinned revisions when possible.

3.1 Exhale — Primary UI Reference

https://github.com/ozyern/exhale

Previously pinned revision:

"166e2cc7c6a3bcfb944999571e1bc5e0e3c1a066"

Use Exhale as the primary visual and interaction reference for:

- Library.
- Album.
- Artist.
- Playlist.
- Search.
- Now Playing.
- Mini-player.
- Navigation.
- Haze.
- Liquid Glass.
- Animated artwork.
- Screen transitions.
- Lyrics presentation.
- Design tokens and reusable components.

Exception: Home follows SpotiFLAC-Mobile.

Preserve Sistrum's existing navigation behavior where explicitly required, including collapsing navigation and mini-player repositioning.

Do not assume Exhale's player-style identifiers map directly to Sistrum's styles.

Verify the source implementation.

3.2 SpotiFLAC-Mobile — Home Reference and Extension Runtime Research

https://github.com/spotiflacapp/SpotiFLAC-Mobile

Previously pinned revision:

"5a8952659b247fd95f1914a56db60825657fe26d"

Responsibilities:

- Apple Music-inspired Home UI.
- Extension manager architecture.
- Extension package loading.
- JavaScript runtime.
- Host API contracts.
- Provider registration.
- Extension lifecycle.
- Compatibility requirements.

Do not copy unrelated streaming or downloading functionality.

3.3 SpotiFLAC-Extension — Primary Extension Source

https://github.com/spotiflacapp/SpotiFLAC-Extension

Previously pinned revision:

"0290b263802d3418e45583612574b749289bf369"

Also inspect the user-selected extension registry:

https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/registry.json

Treat the registry and its referenced extension packages as independently versioned upstream artifacts.

Inspect actual package manifests and JavaScript sources.

Important: the registry URL is user-selected, not necessarily an official or security-audited distribution channel.

Required investigation:

- Registry schema.
- Extension package format.
- Manifest structure.
- Entry points.
- JavaScript language features.
- Registration APIs.
- Host APIs.
- Network requirements.
- Permission declarations.
- Versioning.
- SHA-256 verification.
- Runtime compatibility.
- Provider output formats.
- Update behavior.
- Extension error handling.

Do not assume all registry entries are compatible with Sistrum.

3.4 Flick — Library Settings Only

https://github.com/moss-apps/Flick

Previously pinned revision:

"79da4ed76557c8ddf534e898480dde66bcc90334"

Use Flick for Library Settings inspiration only.

Do not implement Flick's Artist Biography/About functionality.

3.5 BitChord — Hide Volume Bar

https://github.com/kushagrasinghx/BitChord

Previously pinned revision:

"fe95bb60aa9cd205cf4ab8bd25a5bec71a595b4f"

Implement a persistent Hide Volume Bar setting in a later phase.

Default: false.

3.6 Rhythm — Secondary Technical Reference

Inspect the provided Rhythm source archive if accessible.

Use Rhythm as a reference for:

- Wikipedia metadata retrieval.
- Apple Music Motion Artwork.
- YouTube Music artist images.
- Media3-based silent motion-video rendering.

Do not replace the SpotiFLAC Extension architecture with Rhythm.

Document differences and potential reusable concepts.

---

4. UPDATED FEATURE SCOPE

The complete UI overhaul remains in scope.

The following metadata and extension features are now also required.

4.1 Extension Registry

Sistrum must ship with the following registry URL configured by default:

https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/registry.json

Users should not need to enter this URL manually.

Plan a Settings → Extensions interface supporting:

- Default registry.
- Registry refresh.
- Installed extensions.
- Available extensions.
- Install.
- Enable/disable.
- Update available indicator.
- Manual update.
- Uninstall.
- Rollback.
- Version information.
- Compatibility status.
- Error information.
- Provider permissions.
- Optional custom registries in a later phase.

The default registry must not imply automatic installation or execution of arbitrary remote code.

Default behavior:

- Automatic update checks: enabled, with reasonable caching.
- Automatic code updates: disabled.
- Extension installation: explicit user action.
- Provider execution: only after installation and enablement.

Never claim that a remote update is safe merely because its SHA-256 matches the value published in the same registry.

4.2 Extension Runtime

Research whether the existing SpotiFLAC runtime can be adapted safely.

Compare:

A. Extracting and adapting the original runtime.

B. Embedded QuickJS or another suitable JavaScript engine.

C. A constrained provider-specific adapter without general-purpose remote code execution.

Evaluate:

- JavaScript compatibility.
- Host API compatibility.
- Android integration.
- Binary size.
- Startup performance.
- Memory consumption.
- Security isolation.
- Runtime update compatibility.
- Maintenance burden.
- Google Play policy implications.
- License obligations.

Do not select a runtime based only on implementation convenience.

Create a proof-of-concept plan before committing to full integration.

The architecture must separate:

Extension Registry → Package Verification → Extension Runtime → Provider Adapter → Sistrum Repositories → UI.

4.3 Apple Music Motion Artwork

Required capabilities, subject to actual upstream support:

- Album Motion Artwork.
- Artist Motion Artwork.
- Static fallback artwork.
- Local cache.
- Motion playback only when visible.
- Muted video.
- Looping playback.
- Lifecycle-aware pause/resume.
- Reduced Motion support.
- Battery-conscious behavior.
- No interference with music playback.

Verify actual Apple Music extension methods and supported artwork types.

Do not assume every artist or album has motion assets.

Do not rely on hardcoded or scraped credentials without reviewing reliability, authorization, and terms.

4.4 Artist Photos

Investigate the SpotiFLAC YouTube Music and Deezer extensions.

Required user experience:

- Artist image retrieval.
- Configurable provider priority.
- Automatic fallback.
- Local cache.
- Manual refresh.
- Optional manual image override.
- Offline fallback.
- Correct artist matching.

Preferred initial priority:

1. YouTube Music.
2. Deezer.
3. Existing local artwork.

Users must be able to change this priority.

Verify whether the extensions expose artist-image retrieval directly or require additional host APIs.

Do not confuse album thumbnails with verified artist portraits.

4.5 Wikipedia About

The user previously removed the original Artist About and Album About implementations from Flick and SpotiFLAC Extension.

Do not restore those implementations.

However, Wikipedia metadata retrieval is now requested as an optional capability.

Plan:

- Artist biography.
- Album description only where reliable information is available.
- Source attribution.
- Entity disambiguation.
- Cache.
- Offline fallback.
- Explicit handling of missing results.

Keep Wikipedia About independent from the motion-artwork providers.

Do not fabricate biographies or descriptions.

---

5. EXTEND EXISTING FIND LYRICS

This is a core requirement.

Sistrum already has Find Lyrics using two services.

Do not replace it. Extend it.

First, identify the actual existing providers and their interfaces.

Document:

- Provider names.
- Search API.
- Result models.
- Existing matching logic.
- Existing UI behavior.
- Lyrics synchronization support.
- Current caching.
- Existing write/embed capabilities.
- Existing sidecar handling.

Then inspect SpotiFLAC extensions for additional lyrics-capable providers.

Only list providers that are verified in the actual source or registry.

Potential examples such as LRCLIB, KuGou, Apple Music, or other providers must not be presented as implemented unless verified.

Required user experience

Find Lyrics should support:

- Existing providers.
- Compatible installed extension providers.
- Provider enable/disable.
- Provider priority.
- Search across enabled providers.
- Per-provider error handling.
- Consolidated search results.
- Result deduplication.
- Source attribution.
- Plain lyrics.
- Line-synchronized lyrics.
- Word-synchronized lyrics where genuinely supported.
- Preview before selection.
- Manual selection of the correct result.
- Local caching.
- Offline access to cached lyrics.

Match candidates using available:

- Track title.
- Artist.
- Album.
- Duration.
- Track identifiers, when available.

Avoid selecting incorrect versions such as live, remix, instrumental, or cover recordings.

Preserve all existing embedded and sidecar lyrics behavior.

Search policy

Prefer local lyrics first.

Online search must be optional.

Automatic search for missing lyrics should default to OFF initially.

Do not repeatedly query providers for the same missing track.

Add negative-result caching and sensible retry limits.

Do not download lyrics unnecessarily when opening a large library.

---

6. EMBED LYRICS INTO AUDIO FILES

The user wants to search for missing lyrics and save the selected result inside the actual music file.

This is distinct from merely caching lyrics.

Plan a native Sistrum Lyrics Tag Writer.

Extensions must not receive direct write access to audio files.

Only Sistrum's trusted storage layer may modify files.

Required capabilities:

- Preview lyrics before saving.
- Save to local cache.
- Embed into supported audio files.
- Save as sidecar ".lrc" where appropriate.
- Preserve existing lyrics unless replacement is confirmed.
- Never overwrite without user confirmation.
- Preserve all unrelated metadata and album artwork.
- Preserve audio quality.
- Avoid audio transcoding.
- Update the library after successful writes.
- Report unsupported formats accurately.

Investigate format-specific support:

- MP3 / ID3 USLT.
- MP3 / ID3 SYLT where supported.
- FLAC / Vorbis Comments.
- Opus / OpusTags.
- M4A / ALAC metadata atoms.
- Other formats supported by the current Sistrum library.

Do not assume all formats support the same synchronized lyrics representation.

For synchronized lyrics, evaluate:

- Native standardized tag support.
- Common custom tags.
- LRC text embedding.
- Sidecar ".lrc" fallback.

Android storage requirements:

- Scoped Storage.
- MediaStore write requests.
- Storage Access Framework where needed.
- Atomic or recoverable file replacement.
- Backup/recovery strategy.
- File-size and free-space checks.
- Cancellation handling.
- Verification after writing.

Test that modifying tags does not corrupt audio streams, album art, or unrelated metadata.

Review provider licensing and terms before permitting persistent embedding of retrieved lyrics.

Batch embedding is a later feature and must not be implemented before single-track writing is reliable.

---

7. EXTENSION SECURITY

Treat downloaded extensions as untrusted code.

Design for:

- Isolated execution where technically feasible.
- No direct access to the audio library.
- No direct database access.
- No arbitrary filesystem access.
- No arbitrary Android API access.
- Restricted network access.
- HTTPS enforcement.
- Redirect validation.
- Domain allowlists.
- Protection against localhost/private-network access.
- Request timeouts.
- Response-size limits.
- Memory limits.
- Execution time limits.
- Cancellation.
- Crash isolation.
- Permission declarations.
- Safe package extraction.
- ZIP traversal prevention.
- Archive decompression limits.
- Integrity verification.
- Authenticity verification.
- Version compatibility checks.
- Rollback.
- Audit logging without leaking sensitive data.

A plain in-process JavaScript interpreter must not automatically be described as a security sandbox.

If adequate isolation cannot be implemented, recommend a narrower provider-specific architecture.

Review current Google Play policies concerning remotely downloaded executable/interpreted code.

If the architecture cannot comply with the intended distribution model, document that as a blocker rather than bypassing policy.

---

8. EXTENSION UPDATE STRATEGY

The registry should be bundled as a default configuration, not hardcoded into every provider.

Plan the following lifecycle:

DISCOVERED → INSTALLED → ENABLED → UPDATE_AVAILABLE → UPDATING → VERIFIED → ACTIVE.

Also handle:

- DISABLED.
- INCOMPATIBLE.
- FAILED.
- ROLLED_BACK.
- REMOVED.

The update process must:

1. Fetch the registry.
2. Validate its schema.
3. Verify registry authenticity where supported.
4. Compare installed and available versions.
5. Check host API compatibility.
6. Download into staging storage.
7. Verify package integrity and authenticity.
8. Validate the manifest.
9. Check required permissions.
10. Run compatibility validation.
11. Activate only after successful validation.
12. Preserve the previous working version for rollback.

An extension update must not silently increase permissions.

Automatic checks are allowed; automatic execution of newly downloaded code is not.

Document how upstream breaking changes will be detected.

---

9. UI OVERHAUL REQUIREMENTS

Retain the previously agreed UI direction.

Primary design

Exhale is the main UI reference.

Home

SpotiFLAC-Mobile is the Home design reference.

Player styles

Keep three styles:

- DEFAULT: original Sistrum player, unchanged and selected by default.
- LARGE: Exhale-inspired large player.
- IMMERSIVE: Exhale-inspired immersive player.

Persist the selected style in DataStore.

Verify the actual Exhale implementation for LARGE and IMMERSIVE.

Do not guess source mappings.

Do not create a separate Full-Screen Cover Art toggle.

Navigation

Preserve existing Sistrum behavior:

- Collapsing bottom navigation.
- Circular collapsed state.
- Mini-player repositioning.
- Navigation state restoration.
- Back navigation.

Glass

Use Exhale's Haze and Liquid Glass implementation as a technical reference.

Preserve:

- Reduce Transparency.
- Light/Dark/System themes.
- Performance fallbacks.
- Accessibility.

Lyrics UI

Use Exhale's lyrics presentation as the visual reference.

Use Sistrum's existing lyrics pipeline, extended with new providers.

Do not replace the local lyrics model without a migration and compatibility plan.

Other features

- Flick-inspired Library Settings.
- BitChord-inspired Hide Volume Bar.
- Existing Sistrum functionality remains intact.

Spatial Audio and 8D are deferred until the playback migration is stable.

---

10. MEDIA3/WAXFLOW MIGRATION ISOLATION

Another agent is modifying playback infrastructure.

Do not change:

- Audio decoding.
- Playback engine.
- Audio output.
- Crossfade.
- Normalization.
- Audio DSP.
- Playback queue internals.
- Media3 migration files.

Identify extension/UI integration points through stable interfaces.

Plan how motion-video playback will coexist with music playback.

Avoid accidentally routing motion audio into the music pipeline.

Avoid playback-service lifecycle conflicts.

Create explicit contracts for:

- Current track metadata.
- Artist and album identifiers.
- Artwork loading.
- Lyrics loading.
- Lyrics selection.
- Lyrics writing.
- Motion artwork playback.
- UI player state.

Document expected conflicts when the UI branch is eventually integrated with the migration branch.

---

11. DATA AND CACHE ARCHITECTURE

Plan separate storage for:

- Installed extension metadata.
- Extension package versions.
- Extension settings.
- Provider priority.
- Artwork cache.
- Motion artwork cache.
- Lyrics cache.
- Negative search results.
- Wikipedia descriptions.
- Provider match identifiers.

Do not duplicate existing database entities unnecessarily.

Define:

- Cache keys.
- TTL.
- Maximum size.
- Eviction strategy.
- Offline fallback.
- Schema migration.
- Corruption recovery.
- User-controlled cache clearing.

Distinguish downloaded provider metadata from embedded file metadata.

Preserve existing library records.

---

12. LICENSING AND COMPLIANCE

Audit the actual licenses of:

- Sistrum.
- Exhale.
- SpotiFLAC-Mobile.
- SpotiFLAC-Extension.
- Flick.
- BitChord.
- Rhythm.
- JavaScript runtime dependencies.

Document whether code may be copied, adapted, or used only as an implementation reference.

Check obligations for distributing modified GPL components.

Review the terms and permissions for:

- Apple Music artwork.
- YouTube Music images.
- Deezer images.
- Wikipedia text.
- Lyrics providers.

Do not assume that technical accessibility grants redistribution or permanent embedding rights.

Do not copy protected assets or proprietary credentials.

---

13. IMPLEMENTATION PHASES TO PLAN

Create an updated dependency-aware roadmap.

Suggested phases:

Phase 0 — Discovery and Architecture

- Source inspection.
- Graphify.
- Extension format audit.
- Existing Find Lyrics audit.
- Security assessment.
- License assessment.
- Updated design and implementation plan.

Phase 1 — UI Design Foundation

- Design tokens.
- Shared Compose components.
- Theme integration.
- Haze and Liquid Glass.

Phase 2 — Core Screens

- Home.
- Library.
- Album.
- Artist.
- Playlist.
- Search.

Phase 3 — Navigation and Player UI

- Floating navigation.
- Mini-player.
- DEFAULT/LARGE/IMMERSIVE styles.
- Animations.

Phase 4 — Extension Runtime Proof of Concept

- Registry parsing.
- Package validation.
- Host API compatibility.
- Isolated runtime feasibility.
- Security tests.

Phase 5 — Extension Manager

- Installation.
- Enable/disable.
- Version checks.
- Manual updates.
- Rollback.
- Settings UI.

Phase 6 — Artwork Providers

- Apple Music Album Motion.
- Apple Music Artist Motion.
- YouTube Music artist images.
- Deezer artist images.
- Caching and fallback.

Phase 7 — Find Lyrics Expansion

- Preserve existing two providers.
- Add verified extension providers.
- Multi-provider search.
- Results and previews.
- Priority settings.

Phase 8 — Lyrics Embedding

- Safe tag writing.
- Format support.
- Sidecar fallback.
- File verification.
- Library refresh.

Phase 9 — Wikipedia Metadata

- Artist biography.
- Album information where reliable.
- Attribution.
- Cache.

Phase 10 — Library Settings and Miscellaneous

- Flick-inspired settings.
- Hide Volume Bar.
- Additional preferences.

Phase 11 — Migration Integration

- Merge-compatible API integration.
- Playback compatibility.
- Motion renderer integration.
- Conflict resolution.

Phase 12 — Final Verification

- Unit tests.
- Integration tests.
- Compose UI tests.
- Screenshot tests.
- Performance tests.
- Security tests.
- Offline tests.
- Real-device testing.

These phases are suggestions, not rigid dependencies.

Identify tasks that can proceed independently and tasks blocked by the Media3 migration.

---

14. ATOMIC TASK REQUIREMENTS

Every implementation task must include:

- Unique task ID.
- Title.
- Goal.
- Source references.
- Expected files/modules.
- Dependencies.
- Owner or agent role.
- Risk classification.
- Migration conflict risk.
- Acceptance criteria.
- Required tests.
- Estimated relative complexity.
- Suggested commit boundary.
- Rollback strategy.

Classify tasks as:

- SAFE NOW.
- UI ONLY WITH DEPENDENCIES.
- EXTENSION RUNTIME DEPENDENT.
- POST-MIGRATION.
- BLOCKED BY POLICY OR LICENSING.

Keep tasks small enough for independent review.

Avoid combining unrelated UI and backend modifications in one commit.

---

15. TESTING REQUIREMENTS

Plan tests for:

Extension Registry

- Valid registry.
- Invalid schema.
- Network unavailable.
- Duplicate IDs.
- Invalid versions.
- Missing fields.
- Malicious download URLs.
- Incorrect hashes.
- Tampered packages.

Runtime

- Unsupported JavaScript features.
- Missing Host APIs.
- Network restrictions.
- Infinite loops.
- Excessive memory use.
- Excessive response sizes.
- Crashes.
- Cancellation.
- Update rollback.

Artwork

- Incorrect artist match.
- Missing artwork.
- Motion unavailable.
- Cached artwork.
- Offline fallback.
- Video lifecycle.
- Memory leaks.
- Background battery usage.

Lyrics

- Existing provider regression.
- Multiple providers.
- Duplicate results.
- Incorrect track matches.
- Missing lyrics.
- Plain lyrics.
- Line synchronization.
- Word synchronization.
- Preview and selection.
- Cache behavior.
- Provider failure.
- No internet.

Lyrics Writing

- MP3.
- FLAC.
- Opus.
- M4A.
- Unsupported formats.
- Existing lyrics preservation.
- Existing artwork preservation.
- Audio stream integrity.
- Interrupted writes.
- Storage permission denial.
- Out-of-space conditions.
- Successful library refresh.

UI

- Light/Dark/System.
- Reduce Transparency.
- RTL.
- Font scaling.
- Navigation transitions.
- Mini-player.
- Player styles.
- Extension Manager states.
- Accessibility.
- Low-end device performance.

Do not report tests as passing unless actually executed.

---

16. REQUIRED DOCUMENTATION

Update the existing documents in:

"docs/ui-overhaul/"

Preserve the existing Phase 0 work.

Update:

- "00-current-state.md"
- "01-reference-source-map.md"
- "02-visual-specification.md"
- "03-master-plan.md"
- "04-atomic-tasks.md"
- "05-test-plan.md"
- "06-licenses.md"
- "07-risks-and-decisions.md"
- "08-progress-tracker.md"
- "09-migration-integration-contract.md"

Create a new directory:

"docs/ui-overhaul/extensions/"

Add:

1. "00-extension-architecture.md"
2. "01-registry-and-package-format.md"
3. "02-runtime-compatibility-audit.md"
4. "03-security-threat-model.md"
5. "04-provider-capability-matrix.md"
6. "05-artwork-and-motion-design.md"
7. "06-lyrics-provider-integration.md"
8. "07-lyrics-embedding-design.md"
9. "08-update-and-rollback.md"
10. "09-proof-of-concept-plan.md"
11. "10-compliance-and-licensing.md"

All documentation must distinguish:

- VERIFIED IN SOURCE.
- PROPOSED.
- UNKNOWN / NEEDS VALIDATION.
- BLOCKED.

Cite exact repository paths, symbols, and commit SHAs for verified findings.

Do not invent implementation details.

---

17. REQUIRED AGENT ROLES

Use available specialized agents or equivalent sequential review passes.

Suggested roles:

1. Android Architecture Auditor.
2. Extension Runtime Compatibility Auditor.
3. JavaScript Sandbox Security Auditor.
4. Metadata & Artwork Provider Auditor.
5. Lyrics Pipeline & Tagging Auditor.
6. Compose UI Architect.
7. Media3 Integration Auditor.
8. Licensing & Compliance Reviewer.
9. Test Strategy Auditor.

Do not run redundant full-repository scans for every agent.

Share a common source map and scoped findings.

Keep implementation ownership clear.

---

18. BASELINE BUILD VERIFICATION

The previous Phase 0 encountered a failure in:

"subsetMaterialSymbolsFont"

Investigate the missing Python "fonttools" dependency.

Identify the interpreter invoked by Gradle.

Repair the UI development environment without changing application behavior.

Then attempt:

./gradlew :androidApp:assembleDevDebug :androidApp:testDevDebugUnitTest :sharedLogic:testAndroidHostTest --console=plain

Report:

- Commands executed.
- Environment fixes.
- Build results.
- Test results.
- Remaining failures.

Do not change unrelated application code to bypass a build failure.

---

19. FINAL DELIVERABLE

At the end, provide:

1. Current Sistrum architecture summary.
2. Existing Find Lyrics providers and exact source locations.
3. Verified SpotiFLAC extension capabilities.
4. Registry and package format analysis.
5. Runtime architecture comparison.
6. Security and Google Play compatibility assessment.
7. Artwork and motion implementation strategy.
8. Lyrics search and embedding strategy.
9. Updated UI implementation plan.
10. Atomic tasks with dependencies.
11. Integration boundaries with Media3/WaxFlow.
12. Build/test results.
13. Licensing and unresolved blockers.
14. Recommended first implementation task.
15. List of changed documentation files.
16. Git status and commit hash if a documentation-only commit is permitted.

---

20. ABSOLUTE STOP CONDITION

THIS EXECUTION IS PLANNING AND VERIFICATION ONLY.

Do not:

- Implement UI changes.
- Add runtime dependencies.
- Add extension execution code.
- Modify playback code.
- Modify the scanner.
- Modify the database schema.
- Modify music files.
- Start Phase 1.
- Merge branches.
- Push commits.

Documentation changes and isolated environment repairs are permitted.

Stop after completing the architecture audit and planning documents.

Wait for explicit user approval before implementation.
