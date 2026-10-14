---
name: production-audit
description: Local-evidence release-readiness audit for the Sistrum Android app before a prodRelease or public build — crashes and ANRs, data safety (Room migrations, analysis/stat data), permissions and storage, playback robustness, battery/background work, R8 and signing, store-policy risks — ending in a ship/block score with the evidence checked. Use before releases or when asked "what would break for users?".
---
<!-- Adapted from ECC (github.com/affaan-m/ECC @ b92f181), MIT License, Copyright (c) 2026 Affaan Mustafa; see docs/ECC-LICENSE.md. Modified for Sistrum. -->

# Production Audit (Android music player)

Use local evidence only; no uploading repo data to external scanners. Do not install anything on the owner's daily
apps; device evidence comes from `.qa` runs and HANDOFF records.

## Evidence first
`git status`, `git log --oneline -20`, `git diff --stat main...HEAD`; latest full gate results (unit, lint,
instrumented on `.qa`), M6 performance report, HANDOFF open issues and the owner's manual-check list.

## Risk lenses

- **Crashes / ANRs** — main-thread I/O or decode, unhandled exceptions in workers/services, `!!` on file/tag data,
  process death during playback or scan, low-memory behaviour.
- **Data safety** — every Room version step has a tested migration; no destructive fallback; analysis documents,
  play history and statistics survive upgrades; schema/version bumps documented.
- **Permissions & storage** — minimum permissions; behaviour when media permission is denied/revoked; SAF grants
  persisted; no `MANAGE_EXTERNAL_STORAGE`; scoped storage on API 31–36.
- **Playback robustness** — unsupported/corrupt files fail with a clear message and skip, not hang; audio focus,
  Bluetooth/headset changes, output switching, gapless and crossfade on long sessions; native fallback engine still
  works until 003.
- **Background & battery** — analysis only when allowed (charging/thermal rules), resumable; no wakelock leaks;
  foreground service rules for the targetSdk.
- **Build & release** — `prodRelease` with R8 builds and runs (keep rules for reflection/serialization/JNI);
  signing done locally per CLAUDE.md; version code bumped; third-party notices complete.
- **UX** — empty/error/permission-denied states explain what happened; RTL and Arabic complete; accessibility
  checklist on the main screens.
- **Privacy** — no personal paths or identifiers in logs; Last.fm credentials stored safely; scrobbling opt-in.

## Score
0–49 blocked · 50–69 risky (internal beta only) · 70–84 launchable with caveats · 85–100 strong.
Cap at 69 if: a Room migration is untested, a crash path is known on a main flow, or secrets/personal data ship.
Cap at 84 if the full gate or the `.qa` phone check is not green, or owner listening checks are still open.

## Output
One-line verdict with score → Blockers → High-value fixes → Evidence checked → Evidence missing → Next action.
