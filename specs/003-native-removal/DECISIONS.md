# Decisions — 003 native removal

Style: Case / Options / Chosen / Revisit if. Written by the planner (2026-10-15) where the owner had not answered; the owner
may overrule any of them. Codex adds D-13 onward during implementation.

## D-1: Quality tooling first, preconditions second
Case: NOTES say 003 starts after 002; D-26 of 002 puts the quality tooling as 003's first task.
Options: (a) tooling first, then the precondition gate; (b) gate first. Chosen: (a): the tooling changes no production code, so
it can run while the owner still has to approve; the "before" numbers must exist before any removal either way.
Revisit if: the tooling needs a library version that 002's last tasks change.

## D-2: Where step 1 ends and step 2 begins
Case: NOTES list "CMake/NDK wiring that only they use" under step 1 but "no externalNativeBuild" under step 2.
Options: (a) step 1 removes the `externalNativeBuild` blocks together with `cpp/` (the build cannot work without them), step 2 removes
`ndkVersion`, `ndk { abiFilters }`, packaging leftovers, NDK/CMake setup docs and proves zero `.so`; (b) keep CMake until step 2
with an empty source. Chosen: (a). Revisit if: removing `externalNativeBuild` in step 1 breaks a variant that still needs an NDK.

## D-3: Formats nobody plays block step 1, not only step 2
Case: the native engine is the only player for formats with no Kotlin/platform provider (e.g. TTA, TAK if FFmpeg plays them).
Removing it in step 1 therefore already stops them.
Options: (a) the owner decides every "none" row before step 1 (port, or accept unsupported with the skipped-files summary);
(b) only before step 2 as NOTES suggest. Chosen: (a), so no user silently loses a format. The decision is an OWNER DECISION task.
Revisit if: the inventory shows no "none" rows (then nothing to decide).

## D-4: Keep the `PlayerEngine` interface, remove `EngineKind` and `EngineFactory`
Case: with one engine the enum, the factory and the `kind == Media3` branches are dead; the interface is still the test seam
(`FakePlayerEngine`) and the coordinator's boundary. Options: delete the interface too; keep it. Chosen: keep the interface,
remove everything about choosing; the branches keep their Media3 arm. Revisit if: the review phase finds the seam unused by tests.

## D-5: A stored engine choice is ignored, no cleanup code
Case: old installs may hold `Native` in the engine DataStore. Options: ignore; delete the file at startup. Chosen: ignore (the
orphan file is a few bytes; startup code to delete it would be new code in a deletion feature). Revisit if: a store audit objects.

## D-6: Normalization stays as the user saved it
Case: `MainActivity` disables normalization when Native was selected. Chosen: remove that line; no migration of the saved value.
Revisit if: a user with Native selected reports unexpected loudness (the setting is visible and one tap).

## D-7: Scan gate is always the registry gate
Case: `LibraryScanRunner` uses `RegistryScanGate` for Media3 and `AdmitAllGate` for Native. Chosen: always `RegistryScanGate`;
delete `AdmitAllGate` if nothing else uses it. Revisit if: a format is playable by Media3 but absent from the registry.

## D-8: Empty Developer screen goes with the switch
Case: `DeveloperContent.kt` (45 lines) holds the engine row and its note. Chosen: if no other row exists, remove the Developer
screen, its navigation entry and its strings in English and Arabic; otherwise only remove the engine row. Revisit if: the owner
wants the screen kept for future toggles.

## D-9: EQ golden data stays, its C++ generator goes
Case: `eq_golden.json` and `tools/eq-golden/generate.sh` take their reference from `cpp/ffmpeg_player.cpp`. Chosen: keep the JSON (it is
the oracle for the Kotlin equalizer), delete the generator, and write the source commit hash in the test KDoc. Revisit if: the
oracle is needed again (restore the generator from that commit).

## D-10: Third-party `.so` files are reported, not removed
Case: "no native code" may collide with AndroidX libraries that bundle `.so`. Chosen: SC-002 counts our files; every other `.so`
is listed in HANDOFF with its dependency; removing a library is an OWNER DECISION. Revisit if: none found.

## D-11: `audio/ffmpeg` MIME handling stays
Case: MediaStore reports `audio/ffmpeg` for some files (`MediaStoreLibraryScanner`, `AudioFormatTest`). It is incoming data, not our
FFmpeg. Chosen: keep; only comments naming our removed code change. Revisit if: never.

## D-12: Cleanup scope is SAFE findings only
Case: registry rows D-09, D-11, D-12 and refactor-cleaner output. Chosen: SAFE only, each with evidence; CAREFUL/RISKY go to the
review phase list. D-09 rows may be reachable through the 002 tree walk, so CAREFUL until proven.

## D-13: Split the large playback files and their tests after cleanup (owner, 2026-10-15)
Case: owner asked whether playback should be many Kotlin files or one; answer: many, one responsibility each. Today
`PlaybackCoordinator.kt` (~1,450 lines) and `media3/Media3Engine.kt` (~1,360) hold several jobs each. Owner: do it, split the
test files the same way and give the whole test suite a full checkup, and only after FFmpeg/NDK are removed and the cleanup
is done. Chosen: new milestone M4b between M4 and M5 (T025a–T025i): Claude maps the split first, characterization gaps are
pinned, one Codex task per file (move-only, [HIGH-RISK], tests moved verbatim, migration-guard), then the whole-suite checkup
and its fixes. Revisit if: the split map shows a file whose parts share too much state to split without a logic change (then
that file is left whole with the reason recorded).
