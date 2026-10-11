# Huelith Offline for Android: tester build

This is the **Android v0.1.3 source and build package**, not an already compiled APK. No fork, original-repo PR, or GitHub account mutation has occurred.
The backend is the open-source upstream Rust engine and the frontend is Kotlin Multiplatform / Compose.
The app does not need an internet connection, account, cloud API, or a model download to import, edit, preview, and export photos.

## Get one installable APK (Windows)

1. Install **Android Studio** with Android SDK Platform 36 and an **NDK**, **Git**, **Python 3**, **Rust / rustup (1.90+)** and **Java 17+**. The package downloads Gradle and cargo-ndk if missing; **internet is needed to download build dependencies**, not while running the app.
2. Extract the whole ZIP anywhere on your PC (not inside Program Files).
3. Open a PowerShell terminal in the extracted folder and execute:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\BUILD-ANDROID.ps1
   ```

4. On success the APK is written beside this file as `Huelith-offline-arm64-debug.apk` in `%USERPROFILE%\HuelithAndroidTest`.
5. With USB debugging enabled and `adb` available, install with:

   ```powershell
   adb install -r "$env:USERPROFILE\HuelithAndroidTest\Huelith-offline-arm64-debug.apk"
   ```

**Supported build target:** physical Android phones/tablets using ARM64, Android 10+. x86 emulators are not supported by the current native library package.

To use a separate build directory, pass `-WorkDir C:\path\to\build-folder` to `BUILD-ANDROID.ps1`. The script clones and pins the upstream source automatically, and never alters your existing repositories.

## First device test

Enable airplane mode. Launch Huelith, import a local JPEG, open its photo card, change Exposure and Contrast, close/reopen, and export to `Pictures/Huelith`. Compare the original and exported photo in Files. Test PNG too. Then try one DNG file.

If it crashes, copy the `adb logcat` output for `AndroidRuntime` or `lightcraft` and send it along with the phone model, Android version, what you tapped, and a screenshot. The app has no crash reporter, so nothing is sent automatically.

## Honest status

- The KMP app + JNI Rust adapter, UI, import/export, presets, settings, undo and quality test cases are authored.
- A **static source audit** passes. Native Rust tests, Android assembly, installation, real-device behavior and image quality are **not verified** in the current ChatGPT runtime, which lacks Android SDK, Gradle and Rust, and cannot download them.
- The app is an **offline MVP** rather than a feature-complete Lightroom replacement. Photo masks/crop handles and HEIC support are not in the MVP. Export is size-limited to 4096px long edge.
- No signing keys for a production release are included; this produces a developer debug APK.

See `QA.md` for the complete test matrix and `README.md` for implementation details and licensing.
