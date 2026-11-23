#!/usr/bin/env bash
# Stage the arm64-v8a Rust library and Kotlin UniFFI bindings for the Android app.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
RUST_BACKEND_DIR="$PROJECT_DIR/rust_backend"
PLATFORM="${1:-}"

if [[ "$PLATFORM" != "android" ]]; then
  echo "Error: only the Android arm64-v8a backend is supported. Usage: bash scripts/build_rust_backend.sh android" >&2
  exit 1
fi

cd "$RUST_BACKEND_DIR"
export CARGO_TARGET_DIR="$RUST_BACKEND_DIR/target"
# AES 0.9 detects ARM64 AES at runtime and retains the software fallback;
# do not force target-feature=+aes on Android.

case "$(uname -s)" in
  Darwin) HOST_LIBRARY="$CARGO_TARGET_DIR/release/libsistrum_mobile.dylib" ;;
  Linux) HOST_LIBRARY="$CARGO_TARGET_DIR/release/libsistrum_mobile.so" ;;
  *) echo "Error: use macOS or Linux for this build script." >&2; exit 1 ;;
esac

# Build separately from bindgen so CLI and cargo-metadata features are not
# enabled in the shipped library through Cargo's workspace feature unification.
cargo build --locked --release -p sistrum-mobile
# Bindgen discovers each crate's uniffi.toml through Cargo metadata.
cargo run --locked -p sistrum-bindgen -- generate \
  --library "$HOST_LIBRARY" \
  --language kotlin \
  --out-dir "$CARGO_TARGET_DIR/bindings/kotlin" \
  --no-format

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  echo "Error: set ANDROID_NDK_HOME to Android NDK 30.0.16248370." >&2
  exit 1
fi
case "$(uname -s)" in
  Darwin) NDK_HOST="darwin-x86_64" ;;
  Linux) NDK_HOST="linux-x86_64" ;;
esac
NDK_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$NDK_HOST/bin"
NDK_SYSROOT="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$NDK_HOST/sysroot"
ANDROID_LINKER="$NDK_BIN/aarch64-linux-android24-clang"
# QuickJS is compiled C. Configure cc-rs and libclang for the same
# target/API as Rust; the host SDK cannot supply Android headers or
# ABI layouts.
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_LINKER"
export CC_aarch64_linux_android="$ANDROID_LINKER"
export AR_aarch64_linux_android="$NDK_BIN/llvm-ar"
export BINDGEN_EXTRA_CLANG_ARGS_aarch64_linux_android="--sysroot=\"$NDK_SYSROOT\" --target=aarch64-linux-android24"

if [[ ! -x "$ANDROID_LINKER" ]]; then
  echo "Error: Android API 24 linker was not found: $ANDROID_LINKER" >&2
  exit 1
fi

rustup target add aarch64-linux-android
# Align ELF segments for 16 KB pages and pack relocations (Android API 23+).
# Keep APS2 rather than RELR, which requires API 28; our minimum is API 24.
# Fold only identical functions whose addresses are not significant.
export RUSTFLAGS="${RUSTFLAGS:-} -C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,--pack-dyn-relocs=android -C link-arg=-Wl,--icf=safe"
cargo build --locked --release -p sistrum-mobile --target aarch64-linux-android
# Remove stale ABIs: Gradle packages everything under this directory.
[[ -n "$CARGO_TARGET_DIR" ]] || { echo "Error: CARGO_TARGET_DIR is empty." >&2; exit 1; }
rm -rf "$CARGO_TARGET_DIR/android/jniLibs"
mkdir -p "$CARGO_TARGET_DIR/android/jniLibs/arm64-v8a"
cp "$CARGO_TARGET_DIR/aarch64-linux-android/release/libsistrum_mobile.so" \
  "$CARGO_TARGET_DIR/android/jniLibs/arm64-v8a/"

echo "Rust android artifacts are staged in $CARGO_TARGET_DIR."
