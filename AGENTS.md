# Project instructions


<!-- workflow-bootstrap -->
## Shared workflow instructions

Before modifying this repository, read and follow [workflow/README.md](workflow/README.md)
and its linked policy/skills. This applies to every agent, including small changes.

## Project rules: Liquid Glass

- Package first: build glass with `liquid_glass_easy` components before writing
  custom code. Write custom glass only when the package lacks the capability, and
  say why in the task record.
- One policy: glass goes through `MornyeGlass` (`lib/widgets/mornye_chrome.dart`)
  with the values from `lib/theme/mornye_glass_presets.dart`. Read the selected mode
  from `mornyeGlassModeProvider` and the rendering from `mornyeGlassLevelProvider`.
  Do not add a parallel glass implementation, per-surface blur values or a new slider.
- None never samples the backdrop: no `BackdropFilter`, lens or batch.
- Details and the Performance hub: `lib/theme/README.md`.
