# Huelith

**Offline Android photo editor using Kotlin Multiplatform + Compose and a native Rust editing engine.**

This is an **independent source repository**, not a GitHub fork and not an upstream pull request. There are **no GitHub Actions or other CI configurations** at this stage.

## Status

The Android source and JNI bridge have been written, but **no compiled APK or real-device result has been verified**. Expect that native Android/Rust compilation may expose errors requiring fixes before device QA. Do not distribute the app as production-ready.

## Android source

- `apps/huelith-kmp/`: Compose Multiplatform interface, Android photo picker/storage, image editor and export.
- `crates/mobile-ffi/`: native Rust JNI bridge using existing image processing crates.
- `docs/huelith-android/`: QA checklist, release status and tester instructions.
- `tests/quality_check.py`: source-level quality checks, not device-level tests.

Features implemented in source: local import, preview, non-destructive adjustments, offline built-in presets, undo/redo, before/after, locally persisted edits, JPEG and PNG export. Android build is ARM64, Android 10+; exports are limited to 4096 px long edge at present.

## Manual build, no CI

1. Install Android SDK Platform 36, Android NDK, Java 17+, Rust 1.90+, `cargo-ndk`, and Gradle 8.13.
2. Install Rust Android target: `rustup target add aarch64-linux-android`.
3. Build the native engine (from repository root):

   ```powershell
   .\apps\huelith-kmp\scripts\build-native.ps1
   ```

4. Build the Android debug APK:

   ```powershell
   cd apps\huelith-kmp
   gradle :composeApp:assembleDebug
   ```

The resulting APK should be under `apps/huelith-kmp/composeApp/build/outputs/apk/debug/` when successful. This app has **no internet permission**, and no account/cloud/runtime download dependency is intended. First verify the merged Android manifest and test in airplane mode. Build-time dependency downloads require an internet connection.

## License and provenance

Core code derives from [storytold/lightcraft](https://github.com/storytold/lightcraft) at commit `0ba118de92a81f7463ca0e6d79e1323137b1adae` and is governed by the retained `LICENSE-MIT`, `LICENSE-APACHE`, `NOTICE`, and individual third-party file notices. `UPSTREAM-README.md` is preserved for source context.

The ArtCraft brand marks are **not** covered by the open-source code license and have been excluded. Huelith is independent, not endorsed by, affiliated with, or sponsored by the upstream maintainers. Retained upstream Rust crate names identify dependency provenance, not the product brand. Huelith itself has not undergone trademark clearance.
