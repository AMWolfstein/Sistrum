# Quickstart — validating 002

Prerequisites: oracle corpora built (`bash scripts/waxflow-oracle.sh --fetch-generate`; IMPORT-LOG D-notes), phone
with the `.qa` build only (CLAUDE.md "Build and test").

1. Host: `./gradlew :codecs:test :metadata:test :analysis:test :dsp:test :androidApp:testDevQaUnitTest :sharedLogic:testAndroidHostTest`.
2. Build: `./gradlew :androidApp:assembleDevQa :androidApp:assembleDevQaAndroidTest`; check both APK IDs are
   `com.amwolfstein.sistrum.dev.qa(.test)`.
3. Per milestone, the phone-check task lists its instrumented classes; run them one at a time with
   `verify.sh --instrumented` (it refuses other IDs).
4. Expected: SC-001…SC-011 in spec.md; measurements recorded in HANDOFF; owner listening checks listed, never PASS.
