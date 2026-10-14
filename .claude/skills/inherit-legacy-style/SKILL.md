---
name: inherit-legacy-style
description: Extract Sistrum's unwritten code conventions (file anatomy, state/flow naming, where infrastructure lives, error handling) into docs/STYLE-RULES.md with golden files and DONTs, so Codex's code stops drifting from the codebase. Owner resolves real conflicts one question at a time. Use once to create the file, then incrementally after large merges/imports.
---
<!-- Adapted from ECC (github.com/affaan-m/ECC @ b92f181), MIT License, Copyright (c) 2026 Affaan Mustafa; see docs/ECC-LICENSE.md. Modified for Sistrum. -->

# Inherit Legacy Style

## Mode

`docs/STYLE-RULES.md` absent → full scan; present → incremental (diff since the fingerprint commit in its header).
Announce the mode in one line.

## Full scan

1. **Scale**: `git ls-files '*.kt' '*.kts' | wc -l` → sample per dimension (this repo is large: read the infra layer
   fully, sample 2–3 files per area: `player/`, `player/media3/`, `player/decoders/`, `analysis/`, `sync/`, `ui/`,
   `sharedLogic/`, modules).
2. **Four dimensions**: file anatomy (declaration order, companion/const placement, visibility defaults); state & control
   flow (StateFlow naming `_x`/`x`, events, preferences access); infrastructure (where helpers, logging, dispatchers,
   strings/digits/RTL helpers live); error handling (typed refusals, Result vs exceptions, logging tags).
3. **Noise threshold**: minority < 5% and < 10 occurrences → majority wins, minority goes to DONTs. Near-even split
   on a core dimension → ask the owner ONE question (evidence paths for each side; options: follow A / follow B / this
   is evolution / new rule). Never stack questions.
4. **Write `docs/STYLE-RULES.md`**: header (date, commit, scale); **Golden files** (real paths + what each shows);
   **Rules** (checkable); **DONTs**. Keep it short: it is read by Codex every task.
5. **Hook**: reference it from AGENTS.md ("Where things are") so Codex reads it; Claude reads it via CLAUDE.md only
   when reviewing. No hard enforcement hooks.

## Incremental

Read the file, `git diff <fingerprint> HEAD --stat`, compare new code to the rules, ask about real conflicts, append
`### <date> Style evolution` entries; never overwrite earlier rules.

Do not judge the tech stack; do not copy bugs from golden files.
