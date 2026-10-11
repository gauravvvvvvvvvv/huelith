#!/usr/bin/env bash
# Run from the LightCraft checkout, any directory. No CI, no network at app runtime.
set -euo pipefail
ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
cd "$ROOT"
command -v cargo >/dev/null || { echo "Install Rust (rustup) first" >&2; exit 1; }
command -v cargo-ndk >/dev/null || { echo "Install cargo-ndk: cargo install cargo-ndk --locked" >&2; exit 1; }
rustup target add aarch64-linux-android
OUT="$ROOT/apps/huelith-kmp/composeApp/src/androidMain/jniLibs"
cargo ndk -t arm64-v8a -o "$OUT" build --release -p huelith-mobile-ffi
[ -s "$OUT/arm64-v8a/libhuelith_mobile_ffi.so" ] || { echo "Missing native library" >&2; exit 1; }
echo "Native library ready: $OUT/arm64-v8a/libhuelith_mobile_ffi.so"
