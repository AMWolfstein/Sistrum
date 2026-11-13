---
name: code-navigation
description: "Navigate this repository with CodeGraph, native/resource/Bash adapters and optional Serena definitions/references, preserving coverage and confidence limits without workflow gates."
---

# Code navigation

Use when tracing code, callers, native boundaries or resources. Start with the
known file/symbol and widen only as needed. Run from the repository root:

```sh
python3 workflow/codegraph/code_tools.py update
python3 workflow/codegraph/code_tools.py query --file lib/theme/mornye_theme.dart
python3 workflow/codegraph/code_tools.py query --symbol MornyeTheme --limit 30
```

Refresh the ignored `.work/codegraph/map.json` if missing or after edits. It is a
saved snapshot, with no fingerprint/freshness gate. Reuse it during unchanged-file
inspection. A limited `update --paths FILE ...` replaces the saved scope; check
`scope.selected_only` and the recorded input list before claiming full coverage.
Read query totals and raise `--limit` when results are truncated.

CodeGraph supplies static symbols/relationships; resource and Bash adapters add
structured links and parse-only shell evidence. Native bindings/platform-channel
records are candidates, not verified runtime calls. Relationships marked inferred
remain inferred. Open the referenced source and build/registration code before
making cross-language conclusions. Generated/dynamic paths and unsupported file
formats remain coverage gaps. Missing references do not prove dead code.

For optional semantic verification of the indexed snapshot:

```sh
python3 workflow/codegraph/code_tools.py serena symbols --file lib/theme/mornye_theme.dart
python3 workflow/codegraph/code_tools.py serena definition --file lib/theme/mornye_theme.dart --symbol MornyeTheme
python3 workflow/codegraph/code_tools.py serena references --file lib/theme/mornye_theme.dart --symbol MornyeTheme
```

Configure `SERENA_PYTHON` and the isolated language-server settings as documented
in [the README](../../../workflow/codegraph/README.md). Do not update the mirror while
a Serena query is running. Results reference a private snapshot; map those paths
back to the checkout. Missing SDKs, incomplete snapshot dependencies, timeouts and
unsupported language servers must be reported explicitly. Only Dart semantic
smoke checks were validated in this integration; do not presume other languages
have been verified. Fall back to read-only source inspection when unavailable.

Use [audit](../audit/SKILL.md) for the diagnostic scans. None of these navigation
commands requires a plan, task, reviewer, source change, commit or push.
