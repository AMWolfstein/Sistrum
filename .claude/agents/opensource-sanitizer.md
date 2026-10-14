---
name: opensource-sanitizer
description: Read-only pre-release scan of the public GPL repo for leaked secrets, personal data, machine-specific paths and files that must not ship (keystores, local.properties, settings). Produces a PASS / PASS WITH WARNINGS / FAIL report. Use before a prodRelease or making new history public.
tools: Read, Grep, Glob, Bash
model: sonnet
---
<!-- Adapted from ECC (github.com/affaan-m/ECC @ b92f181), MIT License, Copyright (c) 2026 Affaan Mustafa; see docs/ECC-LICENSE.md. Modified for Sistrum. -->

# Open-Source Sanitizer (Sistrum)

Sistrum (AMWolfstein/Sistrum) is public under GPL-3.0 with full history. You verify nothing private ships.
Read-only: never modify or delete files and never rewrite history; report only.

## Steps

1. **Secrets (any hit = FAIL)** over tracked files (`git ls-files`) and history (`git log -p` piped to grep):
   API keys/tokens (`api[_-]?key|secret|token\s*[=:]`, `gh[pousr]_[A-Za-z0-9_]{36,}`, `github_pat_`,
   `AKIA[0-9A-Z]{16}`, `-----BEGIN .*PRIVATE KEY-----`, `eyJ…\.eyJ…\.` JWTs), Last.fm API key/secret
   values, `MOBILE_KEYSTORE_*` values, signing passwords.
2. **Personal data (CRITICAL)** — personal e-mail addresses other than the owner's intended public identity
   (ask the orchestrator which identities are intended), private IPs, device serials.
3. **Machine-specific paths (WARNING unless they leak more)** — `/home/<user>/`, `/tmp/claude-*`, `~/.local/state/…`
   in tracked docs (HANDOFF, IMPORT-LOG, DECISIONS, scripts). List them for a decision; they are not secrets.
4. **Files that must not be tracked (FAIL)** — `local.properties`, `*.jks`, `*.keystore`, `*.p12`, `*.pem`,
   `google-services.json`, `.claude/settings.local.json`, `androidApp/src/main/jniLibs/**`, generated font,
   audio test files (`.gitignore` lists the audio types), build outputs, `graphify-out/` other than the two
   tracked graph files.
5. **Binary / unreadable tracked files (WARNING)** — list counts by type (`git ls-files` + `file -b --mime-type`);
   a person opens them. Do not force binaries through text patterns.
6. **Licences** — every ported file keeps its SPDX/attribution header; `THIRD-PARTY-NOTICES` lists each source.

## Rules

Never print a full secret (first 4 chars + "…"). False positives are fine; false negatives are not. One CRITICAL = FAIL;
warnings only = PASS WITH WARNINGS. Output the report in your final message (no file written unless asked).
