# Quickstart - verify 004 (UI part)

Build and tests (CLAUDE.md): only `devQa` for tests. Memory check first (`./gradlew --stop`, `pkill -f '[K]otlinCompileDaemon'`,
`MemAvailable` >= 2 GB).

- Host: `./gradlew :androidApp:testDevQaUnitTest :sharedLogic:testAndroidHostTest`; lint: `./gradlew :androidApp:lintDevQa`;
  build: `./gradlew :androidApp:assembleDevQa :androidApp:assembleDevQaAndroidTest`.
- Device (`.qa` only, `com.amwolfstein.sistrum.dev.qa`, never the daily apps): check both APK ids with `aapt2 dump packagename`,
  `adb install -r` (`-t` for the test APK), `adb shell svc power stayon usb`, then one class at a time:
  `adb shell am instrument -w -r -e class <Class> com.amwolfstein.sistrum.dev.qa.test/androidx.test.runner.AndroidJUnitRunner`.
- Frame stats: `bash scripts/ui-frame-stats.sh` (refuses other package ids).

| Milestone | Validate |
|-----------|----------|
| M1 Foundation (T008) | host suites; `UiContractCharacterizationTest`, `AppNavigationTest`, `MiniPlayerTest`, `FullScreenPlayerTest`, `RtlLayoutTest`, `GlassFallbackTest`, `CardTest`; SC-001, SC-002; frame baseline from T003 |
| M2 Screens (T020) | per-screen classes (`Home`, `Library*`, `AlbumDetails`, `ArtistDetails`, `Playlist*`, `LibrarySearch`, `Appearance`) + characterization; SC-003; `ui-finish-gate-reviewer` |
| M3 Integrations (T041) | provider suites (host, fake HTTP); `MotionArtworkIsolationTest` (needs `scripts/make-motion-fixture.sh` output in `~/.cache/sistrum/`), `IntegrationContentTest`, `ArtistAboutTest`; switches off = 0 requests; SC-006/007/008 |
| M4 Nav/player (T055) | `PlayerStyleParityTest`, `MiniPlayerTest`, `FullScreenPlayerTest`, `QueueTransportAvailabilityTest`, `RtlLayoutTest`, characterization; `PlaybackControllerApiGuardTest`; `git diff <base> -- androidApp/src/main/kotlin/com/amwolfstein/sistrum/player` empty; SC-001/004/005/012 |
| M5 Release (T066) | all modules' tests, lint, `assembleDevQa`, unsigned `assembleProdRelease`, `LyricsPanelTest`, `AccessibilityAuditTest`, all earlier classes; SC-009..013; `release-report.md` |

Owner manual checks (never marked PASS by Claude): real listening, Bluetooth/headset, OEM devices, LARGE/IMMERSIVE visual
review, animated-cover battery feel, Arabic reading pass.
