# Ponytail — shared instructions for every agent

Official [DietrichGebert/ponytail](https://github.com/DietrichGebert/ponytail),
package version **5.3.3**, pinned source commit
`96608e078b2d5ea607c5c3b8b5a54d1c1fba6846`. The two installed skills are unmodified
upstream copies; [MIT license](LICENSE) and [source metadata](upstream.json) are
retained. This uses the project's documented skills/AGENTS instruction-only install.

## One portable rule

Root [AGENTS.md](../../AGENTS.md) requires
[Ponytail full mode](../../.agents/skills/ponytail/SKILL.md) before **every** edit:
application code, tests, scripts, configuration, docs, small direct edits and
Spec Kit planning/implementation. The owner need not invoke it for each request.
The same files apply to any agent and any checkout, including a laptop. No
agent-specific instruction file, plugin, slash command or global configuration
is required. Agents without skill discovery open the linked SKILL.md files directly.
Current owner instructions override defaults; responses use the owner's language.

Understand callers, fixtures, config and user-visible consequences before editing.
Prefer existing components, stdlib/platform features and installed dependencies.
Deliver the smallest complete solution, preserving validation, error handling,
security, accessibility and performance-sensitive audio/hardware behavior. Keep
meaningful tests for non-trivial logic; trivial docs/config edits do not require
invented tests. Smaller code alone is not proof of better code or deadness.

Before completion, use the
[Ponytail review checklist](../../.agents/skills/ponytail-review/SKILL.md) on the diff
and connected code. Incorporate it into the existing planned task review, not a
new review round or separate commit. Small modifications use direct TOR records with the same independent review and
three-result/one-commit rules; a self-review does not replace independent PASS.
They skip spec/plan generation and have no separate final task.
All Spec Kit map-first, three-round and one-commit rules remain in force.

## Scope and limits

This is shared repository policy, **not a per-write executable hook**. It governs
agents following the root instructions; it cannot intercept manual editor writes
or force an agent that ignores repository instructions to obey. When a host does
not automatically read AGENTS.md, supply that file as its project instructions.
No host-specific configuration, lifecycle hooks or session collection was added.

Only the core and review skills were needed. The existing combined code/test/gap
`audit` remains the audit entrypoint; Ponytail's separate audit/debt/gain/help skills
were not installed. No runtime/application dependencies or installer scripts were
added. Update by comparing pinned upstream and preserving the shared policy,
never by replacing the repository's whole AGENTS.md.

## Verification

Upstream file identity, instruction links, portable entrypoints, Spec Kit/reviewer
integration and application-source boundaries were checked. Subsequent modification-specific checks/reviews are recorded in the relevant
feature or standalone TOR record. No application behavior
changed; no APK, feature test suite or unnecessary new tests were run.
