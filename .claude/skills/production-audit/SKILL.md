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

## Release checklist (release-blocking; from agency-agents mobile-release-engineer, adapted)
A shipped APK cannot be pulled back, only replaced by a fix, so each item is checked, not assumed.
- [ ] `versionCode` bumped and higher than every published build (never reused); `versionName` matches the notes.
- [ ] Signed with the intended key, verified with `apksigner verify --print-certs` (CLAUDE.md signing procedure);
      the keystore is backed up somewhere other than this laptop — losing it means no updates ever.
- [ ] The tested artifact is the release build itself (`prodRelease`, R8 on), not devQa/debug; smoke-tested on a device
      under the release application ID (not the owner's daily apps).
- [ ] R8 `mapping.txt` for this exact build archived with the release tag, so stack traces can be read later.
- [ ] Release notes and store/listing text current and localized (English + Arabic); screenshots current.
- [ ] Privacy declarations current (permissions used and why; Play "Data safety" form if published there).
- [ ] minSdk/targetSdk and supported ABIs as intended; third-party notices complete.
- [ ] Rollback = forward-fix plan written: which build replaces this one, who watches the first days.
- [ ] Tag the release commit; archive APK + mapping + notes together.

Staged rollout (if distributed through a store with staging): internal → closed testing → production
1% → 5% → 20% → 50% → 100%; pause and fix forward on a crash-free drop, an ANR spike, or a P0 regression report.

## Score
0–49 blocked · 50–69 risky (internal beta only) · 70–84 launchable with caveats · 85–100 strong.
Cap at 69 if: a Room migration is untested, a crash path is known on a main flow, or secrets/personal data ship.
Cap at 84 if the full gate or the `.qa` phone check is not green, or owner listening checks are still open.

## Output
One-line verdict with score → Blockers → High-value fixes → Evidence checked → Evidence missing → Next action.
