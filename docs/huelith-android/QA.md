# Mobile port quality gates and release checklist

## Offline enforcement (P0)
- [x] Android manifest contains **no** INTERNET/ACCESS_NETWORK_STATE permission
- [x] No cloud/telemetry/sign-in dependencies in application build files
- [x] The imported file and edit settings are stored in private local files
- [x] Export goes to local MediaStore; no HTTP calls
- [x] Android OS backup disabled
- [ ] Physical Android test in airplane mode: import, edit, reopen, export, and compare file size/dimensions
- [ ] Network isolation smoke test using Android `dumpsys package cc.gvrv.huelith` + packet capture

## Correctness and image quality
- [x] Rust tests written: invalid input, non-destructive edits, settings roundtrip, before/after, undo/redo
- [x] Bound import file size, image pixel count, preview edge, export edge and export pixels
- [x] JNI handle registry prevents use-after-free; errors become Java exceptions
- [x] Async slider updates coalesce; edit settings fsync and atomic rename
- [x] Import failure deletes incomplete private library folder
- [x] Export failure deletes incomplete MediaStore item
- [ ] Rust `cargo test` pass: not runnable here (cargo/rustc unavailable)
- [ ] Rust Android cross-compile / `cargo clippy`: not runnable here
- [ ] Final APK merged permission and package audit via `aapt dump permissions` / `aapt dump badging` (enforced by the Windows builder)
- [ ] Android Gradle build pass: not runnable here (Gradle / SDK unavailable)
- [ ] JPEG and PNG golden-image quality vs desktop reference (PSNR / SSIM / channel error)
- [ ] JPEG Exif/ICC and PNG alpha/ICC / rotations / vertical/horizontal image tests
- [ ] DNG/CR3/ARW/NEF/RAF actual file corpus and corrupted RAW fuzz samples
- [ ] App lifecycle: rotate screen, process kill, force stop, out-of-space, cancellation and import retry
- [ ] Low RAM (2GB/4GB) backgrounding + 24/48 MP RAW memory pressure / tiled exports
- [ ] Accessibility, TalkBack, screen reader, large font and touch targets

## Current scope deliberately not completed
- [ ] Crop handles / masking / healing / cloning / red-eye UI
- [ ] Full-resolution tiled export integration from upstream PR #818
- [x] Built-in LightCraft presets list, application and undo in native Rust
- [ ] Custom preset import/export, XMP interop, smart albums, batch export
- [ ] iOS implementation and UI parity
- [ ] Release signing and Play Store production review

## Manual tester script
1. Disable Wi-Fi and mobile data and enable airplane mode. Launch app.
2. Import locally stored JPEG and DNG, then edit Exposure, Highlights, Vibrance and Clarity.
3. Verify before/after and Undo/Redo, leave the app, kill the process, reopen and check settings.
4. Export JPEG and PNG, find both in `Pictures/Huelith` with Photos or Files.
5. Check the original imported photo's checksum against the picked file; they must match.
6. Import a corrupt file, a zero-byte file, and a >256 MiB file. Confirm explicit errors and no partial library entries.
7. Open a portrait orientation JPEG and check rotation/colour management against desktop.
8. Repeat on a 4GB RAM phone, a tablet and an Android emulator. Log hangs, dropped frames, ANRs and memory peaks.

## Independent branding
- [x] App label, Kotlin packages, JNI symbols and native binary names changed to Huelith
- [x] Build scripts bundle original source licenses and notice (inspection enforced on Windows)
- [ ] Independent GitHub repository created (not yet created)
- [ ] Restricted upstream ArtCraft graphics excluded from the new source repository
- [ ] Official trademark register clearance for Huelith before publication

## Build reproducibility
- Upstream baseline commit: `0ba118de92a81f7463ca0e6d79e1323137b1adae`
- Upstream LightCraft license: MIT OR Apache-2.0 with additional notices; branding excluded
- Rust Android architecture: aarch64-linux-android; Android app minSdk 29
- CI automation: intentionally none
