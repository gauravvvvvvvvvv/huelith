# Huelith

Offline Android photo editor, built with Kotlin Multiplatform, Compose Multiplatform and a Rust image processing engine.

**Status:** Early, uncompiled Android source prototype. No APK is verified yet. No CI or GitHub Actions configured.

## Development

The independently maintained Android application lives in `apps/huelith-kmp/`, backed by `crates/mobile-ffi/`. The Rust engine code is adapted from [storytold/lightcraft](https://github.com/storytold/lightcraft) at pinned commit `0ba118de92a81f7463ca0e6d79e1323137b1adae` under its upstream MIT OR Apache-2.0 terms.

This repository is not a GitHub fork or pull request. Huelith is unaffiliated with ArtCraft. Original open source notices should be retained; their protected brands and marks are not used. All editing is intended to work without an internet connection.

## Building

On Windows install Android SDK 36, NDK, JDK 17+, Rust 1.90+, cargo-ndk and Gradle 8.13. Build native Rust with `apps/huelith-kmp/scripts/build-native.ps1`, then run `gradle :composeApp:assembleDebug` from `apps/huelith-kmp`.

**Manual builds only. Do not add CI workflows until explicitly requested.**
