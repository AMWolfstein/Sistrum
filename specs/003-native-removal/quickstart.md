# Quickstart — verify 003

1. `bash scripts/check-no-native.sh` (grep for removed symbols; exit 0) and `bash scripts/check-no-native.sh --apk <apk>` for the
   `devQa` and unsigned `prodRelease` APKs (no `lib/**/*.so` except `scripts/native-allowlist.txt`).
2. `./gradlew :androidApp:testDevQaUnitTest :sharedLogic:testAndroidHostTest :codecs:test :metadata:test :analysis:test :dsp:test`
   and `./gradlew :androidApp:lintDevQa`.
3. `.qa` build only: `FormatInventoryPlaybackTest`, `KotlinFormatsPlaybackTest`, `Media3EngineCoreTest` (see CLAUDE.md for the adb method).
4. Inventory: `specs/003-native-removal/research/format-inventory.md`.
