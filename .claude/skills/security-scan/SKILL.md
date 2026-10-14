---
name: security-scan
description: Scan the project's Claude Code / Codex configuration (.claude/ hooks, skills, agents, settings, .mcp.json, CLAUDE.md, AGENTS.md) for injection risks, over-broad permissions and risky hooks using AgentShield in read-only mode. Use after changing hooks, settings, agents or MCP config, and periodically.
---
<!-- Adapted from ECC (github.com/affaan-m/ECC @ b92f181), MIT License, Copyright (c) 2026 Affaan Mustafa; see docs/ECC-LICENSE.md. Modified for Sistrum. -->

# Security Scan (AgentShield, read-only)

AgentShield is an npm tool from the ECC author. Run it **scan-only** at a pinned version; never `--fix` (it rewrites
config) and never the `--opus` mode (it needs an API key and sends config to a model).

```bash
npx --yes ecc-agentshield@<pinned version> scan --path .claude --format markdown
```

Pin the version the first time it is run (record it in HANDOFF) and keep it; review its changelog before upgrading.

## Reading results
- **Critical** (fix now): secrets in config, `Bash(*)` allow-all, hook command injection via interpolated input,
  shell-running MCP servers without pinning.
- **High**: auto-run instructions in CLAUDE.md/AGENTS.md, agents with write tools they don't need.
- **Medium**: silent error suppression in hooks, unpinned `npx -y` servers.

Our own hooks (`.claude/hooks/git_hooks.py`, `graph_refresh.py`) and `.mcp.json` (context7, pinned) are expected
findings to judge, not to auto-change. Each real finding → a small orchestrator fix (config is Claude's domain), or a
recorded wont-fix with the reason.
