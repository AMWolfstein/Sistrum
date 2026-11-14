# M002 task log

Baseline: `8cb6f4f` on `wip` (see plan.md, Repository evidence).

## M002-T001 Move the Android identity to com.amwolfstein.sistrum

- Map refresh (first action): `code_tools.py update` OK, 1785 sources, 0 partial, 0 unindexed, 36740 symbols.
- Changes: 144 Kotlin/Java files moved from `com/zarz/mornye` to `com/amwolfstein/sistrum` in every source set; `package`/`import` lines, `namespace`, `applicationId`, manifest `package`, provider and activity names, proguard keep rules, JNI names in `native_audio.cpp`/`.map`, every `com.zarz.mornye/*` channel on the Dart and Kotlin sides, intent actions and the `group.com.zarz.mornye` doc id; launcher label `Mornye Local` -> `Sistrum`.
- Docs that name the package or channels were updated with the same rename (android/README.md, CONTRIBUTING.md, docs/player_widgets.md, androidTest assets README), because the T001 validation grep covers the whole repo.
- Checks: `flutter analyze` no issues. `flutter test` (full suite) +4478, ~10 skipped, 0 failures, exit 0.
- Gradle `:app:compileDebugKotlin` unavailable here: Gradle cannot resolve the Kotlin plugin 2.4.10 from the plugin repository, offline or online. Not run.
- Rust: no JNI symbols in `rust_backend`; cargo check not required for this change and not run.
- Validation grep `com\.zarz\.mornye|com/zarz/mornye|Java_com_zarz` outside specs/.specify/history: 0 matches.
