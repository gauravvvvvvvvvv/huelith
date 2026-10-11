# Changelog

## Android v0.1.3: Huelith independent branding (11 October 2026)

- Renamed the independent app to **Huelith** across Compose UI, application ID, Kotlin source package, JNI symbols, Rust bridge crate, native binary, installer targets, and APK output names.
- Added `BRANDING.md` for source attribution and restricted upstream brand assets.
- Added APK licensing-asset packaging and audit for upstream `NOTICE` and source licenses.
- Added branding consistency checks without removing the offline QA checks.
- Product ID `cc.gvrv.huelith`, recommended independent repo `gauravvvvvvvvvv/huelith` (not created).
- This is **source only**, not a compiled or device-verified APK.

## Android v0.1.3: offline tester source verification (11 October 2026)

- Fixed the Windows `BUILD-ANDROID.ps1` builder invoking `cargo test`, `cargo fmt`, and native compilation from outside its required Rust checkout (a blocking build error).
- Added inspection of the **final compiled APK** through the Android SDK `aapt` tool: all merged permissions must be absent and the package id must match `cc.gvrv.huelith`.
- Fixed Compose slider number display rounding and added regression coverage (six Kotlin JVM model smoke assertions passed here).
- Contained file-system and native errors for reset, undo/redo, preset application, comparison, and editor closing, so ordinary failures surface to the user instead of terminating a UI coroutine.
- Export now checks `MediaStore` publication succeeded, and cleans up partial gallery items when it fails.
- Updated documentation to explicitly describe a separate **independent clone**, not a GitHub fork or upstream pull request.

## Validation carried out here

- 43/43 static source, offline-policy and build-script checks passed.
- 3/3 Python installer integration tests passed.
- 6/6 Kotlin JVM shared model assertions passed using the locally installed Kotlin compiler (1.9.0, not the target Android Kotlin Gradle compiler).
- **Not built/tested:** native Rust cargo tests, Rust Android cross compilation, Android Gradle assembly, APK permission audit, Android device runtime and photo quality comparison. Rust, Android SDK/NDK, Gradle and working downloads are unavailable in this runtime.

## Goal

One installable **ARM64 Android 10+ debug APK** from `BUILD-ANDROID.ps1`, running photo import, edits, gallery JPEG/PNG export fully offline. Only runtime/device QA can confirm that goal.
