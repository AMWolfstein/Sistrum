# REGRESSION.md — module regression ledger

After a change, run the changed module's acceptance command plus every downstream command its propagation rule
requires (skill `module-regression`). Verdict = exit codes. A module without a command is a gap, never a pass.

Downstream evidence last refreshed: 2026-10-14 with
`grep -o 'project(":[a-zA-Z]*")' */build.gradle.kts`
(androidApp → analysis, codecs, dsp, metadata, sharedLogic; codecs → dsp; metadata → codecs; analysis → dsp).

| Module | Downstream consumers | Acceptance command | Propagation rule |
|---|---|---|---|
| `:dsp` | `:codecs`, `:analysis`, androidApp | `./gradlew :dsp:test` | Public API or numeric output change → run `:codecs:test`, `:analysis:test` and the app tests of the users it touches (`player.media3`, `player.decoders`, `analysis`). Internal-only with green `:dsp:test` → downstream exempt (say why). |
| `:codecs` | `:metadata`, androidApp | `./gradlew :codecs:test` | API/decoded-output change → `:metadata:test` + `:androidApp:testDevQaUnitTest --tests 'com.amwolfstein.sistrum.player.decoders.*'` (and `…analysis.*` once analysis uses codecs, M4b). |
| `:metadata` | androidApp | `./gradlew :metadata:test` | API/tag-output change → `:androidApp:testDevQaUnitTest --tests 'com.amwolfstein.sistrum.sync.*'`. |
| `:analysis` | androidApp | `./gradlew :analysis:test` | Feature/version change → `:androidApp:testDevQaUnitTest --tests 'com.amwolfstein.sistrum.analysis.*'`. |
| `:sharedLogic` | androidApp | `./gradlew :sharedLogic:testAndroidHostTest` | Any change → full `:androidApp:testDevQaUnitTest`. |
| androidApp | — | `./gradlew :androidApp:testDevQaUnitTest` (narrow with `--tests` per task) + `:androidApp:compileDevQaKotlin` | `player.decoders` change (table, providers, registry) → also `--tests 'com.amwolfstein.sistrum.sync.*'` and `'…analysis.*'` (`RegistryScanGate` and `OfflineDecoder` use the registry; missed T006–T009, found in T011). Milestone end: full suite, lint, `assembleDevQa`, `.qa` device classes. |

Every important defect fixed must name its regression test here or in the task's HANDOFF row.
