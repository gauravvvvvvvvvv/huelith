# Android APK compilation attempt and blockers

**11 October 2026**: Started with the previously authored Huelith KMP overlay at the pinned upstream commit `0ba118de92a81f7463ca0e6d79e1323137b1adae`.

## Build capability checks, actually run

- `which cargo rustc gradle adb sdkmanager`: these tools were **not installed**.
- `java -version`: OpenJDK 21 available.
- `kotlinc`: Kotlin 1.9.0 installed. The pure shared Kotlin model compiled and 6/6 smoke assertions passed.
- `curl -I` GitHub, Gradle distributions, Google Android SDK and Rust toolchain: all returned `Could not resolve host`. Internet installation is not possible from this runtime.
- No `android.jar`, Android NDK, Android SDK or Rust standard library toolchains were found.

**Result: APK compilation cannot start here. An APK has not been produced, signed or tested on a device.** No false binary or fake build logs have been supplied.

## Code/QA performed in this environment

- 32/32 static source checks passed, including offline manifest, no network client, deterministic pinned upstream source, complete JNI entrypoint correspondence, mandatory native .so gate, and final built APK permission guard.
- 3/3 installer integration tests passed.
- 6/6 Kotlin JVM `Model.kt` smoke assertions passed.
- The PowerShell builder now executes cargo commands from the checkout (previously a blocking error).

## Exact next build action (Windows)

Install Android Studio with SDK 36 / Build Tools / NDK, Rust 1.90+, Git, Python 3 and Java 17+. Extract this ZIP and run from PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\BUILD-ANDROID.ps1
```

The builder downloads build dependencies once, compiles the Android ARM64 Rust engine, generates a debug APK, and rejects any APK that requests Android permissions or lacks native engine / DEX. The installed app runs offline. It writes `Huelith-offline-arm64-debug.apk` inside `%USERPROFILE%\HuelithAndroidTest` on success.

Share the FIRST compile error or the compiled APK/device feedback. Native Android device behavior is not yet certified.
