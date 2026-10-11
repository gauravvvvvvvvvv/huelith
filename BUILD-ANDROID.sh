#!/usr/bin/env bash
# Manual offline-app build; only the first-time dependency setup needs network access.
set -euo pipefail
UPSTREAM_SHA=0ba118de92a81f7463ca0e6d79e1323137b1adae
BASE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="${1:-$HOME/HuelithAndroidTest}"
CHECKOUT="$WORK/huelith"
command -v git >/dev/null || { echo 'Missing Git' >&2; exit 1; }
command -v cargo >/dev/null || { echo 'Missing Rust/Cargo' >&2; exit 1; }
command -v python3 >/dev/null || { echo 'Missing Python 3' >&2; exit 1; }
command -v gradle >/dev/null || { echo 'Missing Gradle 8.13; install via Android Studio or Gradle distributions' >&2; exit 1; }
mkdir -p "$WORK"
if [[ ! -d "$CHECKOUT/.git" ]]; then
  mkdir -p "$CHECKOUT"
  git -C "$CHECKOUT" init
  git -C "$CHECKOUT" fetch --depth 1 https://github.com/storytold/lightcraft.git "$UPSTREAM_SHA"
  git -C "$CHECKOUT" checkout --detach FETCH_HEAD
fi
[[ $(git -C "$CHECKOUT" rev-parse HEAD) = "$UPSTREAM_SHA" ]] || { echo 'Unexpected LightCraft source revision' >&2; exit 1; }
if [[ ! -d "$CHECKOUT/crates/mobile-ffi" ]]; then
  python3 "$BASE/install_overlay.py" "$CHECKOUT"
fi
"$CHECKOUT/apps/huelith-kmp/scripts/build-native.sh"
# Preserve original code licenses and copyright notices in the debug APK.
ASSET_DIR="$CHECKOUT/apps/huelith-kmp/composeApp/src/androidMain/assets/licenses"
mkdir -p "$ASSET_DIR"
cp "$CHECKOUT/LICENSE-MIT" "$CHECKOUT/LICENSE-APACHE" "$CHECKOUT/NOTICE" "$ASSET_DIR/"
if [[ -f "$CHECKOUT/assets/ATTRIBUTION.md" ]]; then
  cp "$CHECKOUT/assets/ATTRIBUTION.md" "$ASSET_DIR/ASSETS-ATTRIBUTION.md"
fi
(cd "$CHECKOUT/apps/huelith-kmp" && gradle :composeApp:assembleDebug --no-daemon)
cp "$CHECKOUT/apps/huelith-kmp/composeApp/build/outputs/apk/debug/composeApp-debug.apk" "$WORK/Huelith-offline-arm64-debug.apk"
echo "APK ready at $WORK/Huelith-offline-arm64-debug.apk"
