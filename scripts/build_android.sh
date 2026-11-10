#!/usr/bin/env bash
# Build the Android arm64 release APK for Mornye.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
OUTPUT_DIR="$PROJECT_DIR/build/app/outputs/flutter-apk"
FLUTTER_ARGS=()
for argument in "$@"; do
  case "$argument" in
    --target-platform|--target-platform=*|--split-per-abi|--no-split-per-abi)
      echo "Error: the build always targets android-arm64 only." >&2
      exit 1
      ;;
    *) FLUTTER_ARGS+=("$argument") ;;
  esac
done

if [[ -n "${MORNYE_RUST_ANDROID_ABIS-}" && "${MORNYE_RUST_ANDROID_ABIS}" != "arm64-v8a" ]]; then
  echo "Error: MORNYE_RUST_ANDROID_ABIS must be arm64-v8a (the only supported ABI)." >&2
  exit 1
fi
export MORNYE_RUST_ANDROID_ABIS="arm64-v8a"

# Without android/key.properties the release build is signed with the debug key.
# That is only allowed when the caller says so (CI, or a release job that
# re-signs the APK with its own keystore).
if [[ ! -f "$PROJECT_DIR/android/key.properties" && "${MORNYE_ALLOW_DEBUG_SIGNING:-}" != "1" ]]; then
  echo "Error: no android/key.properties, so the APK would be signed with the debug key." >&2
  echo "Add key.properties, or set MORNYE_ALLOW_DEBUG_SIGNING=1 for CI tests or a job that re-signs the APK." >&2
  exit 1
fi

cd "$PROJECT_DIR"
if [[ -x "$PROJECT_DIR/.fvm/flutter_sdk/bin/flutter" ]]; then
  FLUTTER_COMMAND=("$PROJECT_DIR/.fvm/flutter_sdk/bin/flutter")
  DART_COMMAND=("$PROJECT_DIR/.fvm/flutter_sdk/bin/dart")
elif command -v fvm >/dev/null 2>&1; then
  FLUTTER_COMMAND=(fvm flutter)
  DART_COMMAND=(fvm dart)
elif command -v flutter >/dev/null 2>&1; then
  FLUTTER_COMMAND=(flutter)
  DART_COMMAND=(dart)
else
  echo "Error: install the Flutter version pinned in .fvmrc with FVM." >&2
  exit 1
fi
PINNED_FLUTTER_VERSION="$(python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))["flutter"])' "$PROJECT_DIR/.fvmrc")"
FLUTTER_VERSION="$("${FLUTTER_COMMAND[@]}" --version --machine | python3 -c 'import json, sys; print(json.load(sys.stdin)["frameworkVersion"])')"
if [[ "$FLUTTER_VERSION" != "$PINNED_FLUTTER_VERSION" ]]; then
  echo "Error: Flutter $PINNED_FLUTTER_VERSION is required; found $FLUTTER_VERSION." >&2
  exit 1
fi
"${DART_COMMAND[@]}" scripts/prepare_flutter_sdk.dart
BUILD_GIT_COMMIT="$(git rev-parse --short=8 HEAD)"
"${FLUTTER_COMMAND[@]}" build apk \
  --release \
  --obfuscate \
  --split-debug-info "$PROJECT_DIR/build/symbols/android" \
  --target-platform android-arm64 \
  --dart-define="GIT_COMMIT=$BUILD_GIT_COMMIT" \
  ${FLUTTER_ARGS[@]+"${FLUTTER_ARGS[@]}"}

APK="$OUTPUT_DIR/app-release.apk"
if [[ ! -f "$APK" ]]; then
  echo "Error: expected APK was not created: $APK" >&2
  exit 1
fi
python3 scripts/check_backend_apk.py "$APK" --backend rust --abis arm64-v8a

echo "Built the Android arm64 release APK at $APK"
