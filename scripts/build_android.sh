#!/usr/bin/env bash
# Build the supported Android release APKs for Sistrum.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
OUTPUT_DIR="$PROJECT_DIR/build/app/outputs/flutter-apk"
FLUTTER_ARGS=()
for argument in "$@"; do
  case "$argument" in
    --target-platform|--target-platform=*|--split-per-abi|--no-split-per-abi)
      echo "Error: this script always builds arm64-v8a (android-arm64); do not pass $argument." >&2
      exit 1
      ;;
    *) FLUTTER_ARGS+=("$argument") ;;
  esac
done

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
  --split-per-abi \
  --target-platform android-arm64 \
  --dart-define="GIT_COMMIT=$BUILD_GIT_COMMIT" \
  ${FLUTTER_ARGS[@]+"${FLUTTER_ARGS[@]}"}

for apk in app-arm64-v8a-release.apk app-release.apk; do
  if [[ ! -f "$OUTPUT_DIR/$apk" ]]; then
    echo "Error: expected APK was not created: $OUTPUT_DIR/$apk" >&2
    exit 1
  fi
  python3 scripts/check_backend_apk.py "$OUTPUT_DIR/$apk" --backend rust --abis arm64-v8a
done

echo "Built Android release APKs in $OUTPUT_DIR"
