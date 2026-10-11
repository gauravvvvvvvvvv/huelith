# Local Android build (no CI)

The Kotlin Multiplatform app and JNI integration are under `apps/huelith-kmp` and `crates/mobile-ffi`.

1. Install Java 17+, Android SDK API 36 and Android NDK through Android Studio.
2. Install Rust 1.90+, the `aarch64-linux-android` target, `cargo-ndk`, and Gradle 8.13.
3. In the repository root run `./apps/huelith-kmp/scripts/build-native.ps1` on Windows.
4. In `apps/huelith-kmp` run `gradle :composeApp:assembleDebug`.
5. Review the merged APK manifest; no internet permission should be present. Run device QA in airplane mode.

There is no CI, workflow, cloud runtime or remote-editing service.
