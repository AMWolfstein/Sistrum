# ADR-007 — Separate test-build application ID (first task)

Status: Accepted (owner, 2026-10-05) · Date: 2026-10-05 · Spec: US9, FR-070, SC-009 · Research: D11

## Context

The owner's daily app is `me.misa198.airmedy.dev` (an R8 release build); its instrumentation package is
`me.misa198.airmedy.dev.test`. CLAUDE.md (2026-10-05): never instrument, install over, or force-stop the daily app;
all device tests use a separate test-build ID; if it doesn't exist, stop. Every device spike (S1–S3, S5) and
every instrumented test is blocked on it.

## Decision

- A test build with application ID suffix `.qa` (debug-signed, debuggable, no minify), its own label (e.g.
  "Sistrum QA") and launcher-icon tint, and its own data dir by construction. Result: app
  `me.misa198.airmedy.dev.qa`, instrumentation `me.misa198.airmedy.dev.qa.test`.
- Implemented without touching `signingConfigs` (CLAUDE.md), e.g. as a `qa` build type initialised from `debug`
  with `applicationIdSuffix = ".qa"`, or a `qa` flavor in a second dimension; the task picks the smaller Gradle diff
  and keeps `assembleDevDebug` unchanged.
- CLAUDE.md adb commands and `verify.sh --instrumented` switch to the `.qa` APKs (the guard already refuses the
  daily IDs).
- It is the first implementation task; nothing device-side runs before it.

## Consequences

- QA installs next to the daily app; uninstalling QA never touches the daily app (SC-009).
- MediaStore-backed library: both apps see the same files; the corpus folder is blocklisted only in the daily app.
