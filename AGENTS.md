# Sistrum — instructions for the implementer (OpenCode)

You are the implementer. You receive one task brief at a time and implement exactly that task.
The orchestrator (Claude Code) decides scope and architecture, reviews your diff and commits.
You never delegate: do not start other agents, `opencode` or `relay.mjs`.

## Always
- Follow the brief; its MUST / MUST NOT / TESTS / ACCEPTANCE sections override anything here.
- Run the test command the brief gives you, fix failures, and report the results.
- Report: files changed, commands run with results, anything left undone.

## Never
- Commit, stage, stash, switch branches or push.
- Touch files outside the brief without saying why in your report.
- Run or edit Graphify, or anything in `graphify-out/` or `.claude/`.
- Edit `local.properties`, `androidApp/src/main/jniLibs/` or the generated font
  `androidApp/src/main/res/font/material_symbols_rounded.ttf`.
- Change the Room schema or version (`sync/SyncDatabase.kt`) unless the brief specifies it.
- Remove or disable a feature; change `player/` or `androidApp/src/main/cpp/` beyond the brief.
- Delete, skip (`@Ignore`) or weaken tests, or change expected values to make them pass.

## Where things are
- App: Kotlin + Jetpack Compose in `androidApp/src/main/kotlin/me/misa198/airmedy/`;
  shared module `sharedLogic/`. Unit tests in `androidApp/src/test/`, device tests in `androidApp/src/androidTest/`.
- Build: `./gradlew :androidApp:assembleDevDebug`
- Unit tests: `./gradlew :androidApp:testDevDebugUnitTest :sharedLogic:testAndroidHostTest`
- Playback migration rules: `.specify/memory/constitution.md`
- Strings: `androidApp/src/main/res/values/strings.xml` is the source; every new key also needs
  `values-ar/strings.xml` (`ArabicTranslationCompletenessTest`). Counts use `<plurals>`.
- Numbers and RTL helpers: `ui/components/LatinDigits.kt`, `ui/components/LayoutDirectionSupport.kt`,
  `MirroredInRtlSymbols` in `ui/components/MaterialSymbols.kt`.
