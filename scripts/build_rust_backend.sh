#!/usr/bin/env bash
# Stage the Rust backend library and its UniFFI Kotlin bindings for Android arm64.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
RUST_BACKEND_DIR="$PROJECT_DIR/rust_backend"
PLATFORM="${1:-host}"

case "$PLATFORM" in
  host|android) ;;
  *) echo "Usage: bash scripts/build_rust_backend.sh [host|android]" >&2; exit 1 ;;
esac

if [[ "$PLATFORM" == "android" ]]; then
  ANDROID_ABIS_RAW="${MORNYE_RUST_ANDROID_ABIS-arm64-v8a}"
  if [[ "$ANDROID_ABIS_RAW" != "arm64-v8a" ]]; then
    echo "Error: MORNYE_RUST_ANDROID_ABIS must be arm64-v8a (the only supported ABI)." >&2
    exit 1
  fi
fi

cd "$RUST_BACKEND_DIR"
export CARGO_TARGET_DIR="$RUST_BACKEND_DIR/target"
# AES 0.9 detects ARM64 AES at runtime and retains the software fallback;
# do not force target-feature=+aes on Android.

case "$(uname -s)" in
  Linux) HOST_LIBRARY="$CARGO_TARGET_DIR/release/libmornye_mobile.so" ;;
  *) echo "Error: use Linux for this build script." >&2; exit 1 ;;
esac

# Build separately from bindgen so CLI and cargo-metadata features are not
# enabled in the shipped library through Cargo's workspace feature unification.
cargo build --locked --release -p mornye-mobile
# Bindgen discovers each crate's uniffi.toml through Cargo metadata.
cargo run --locked -p mornye-bindgen -- generate \
  --library "$HOST_LIBRARY" \
  --language kotlin \
  --out-dir "$CARGO_TARGET_DIR/bindings/kotlin" \
  --no-format

if [[ "$PLATFORM" == "android" ]]; then
  if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
    echo "Error: set ANDROID_NDK_HOME to Android NDK 30.0.16248370." >&2
    exit 1
  fi
  case "$(uname -s)" in
    Linux) NDK_HOST="linux-x86_64" ;;
  esac
  NDK_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$NDK_HOST/bin"
  NDK_SYSROOT="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$NDK_HOST/sysroot"
  LINKER="$NDK_BIN/aarch64-linux-android24-clang"
  if [[ ! -x "$LINKER" ]]; then
    echo "Error: Android API 24 linker was not found: $LINKER" >&2
    exit 1
  fi

  # QuickJS is compiled C. Configure cc-rs and libclang for the same target and
  # API as Rust; the host SDK cannot supply Android headers or ABI layouts.
  export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$LINKER"
  export CC_aarch64_linux_android="$LINKER"
  export AR_aarch64_linux_android="$NDK_BIN/llvm-ar"
  export BINDGEN_EXTRA_CLANG_ARGS_aarch64_linux_android="--sysroot=\"$NDK_SYSROOT\" --target=aarch64-linux-android24"

  rustup target add aarch64-linux-android
  # Align ELF segments for 16 KB pages and pack relocations (Android API 23+).
  # Keep APS2 rather than RELR, which requires API 28; our minimum is API 24.
  # Fold only identical functions whose addresses are not significant.
  export RUSTFLAGS="${RUSTFLAGS:-} -C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,--pack-dyn-relocs=android -C link-arg=-Wl,--icf=safe"
  cargo build --locked --release -p mornye-mobile --target aarch64-linux-android
  mkdir -p "$CARGO_TARGET_DIR/android/jniLibs/arm64-v8a"
  cp "$CARGO_TARGET_DIR/aarch64-linux-android/release/libmornye_mobile.so" \
    "$CARGO_TARGET_DIR/android/jniLibs/arm64-v8a/"
fi

echo "Rust $PLATFORM artifacts are staged in $CARGO_TARGET_DIR."
