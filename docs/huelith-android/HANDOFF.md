# Engineering handoff

**Repository plan:** Create an entirely independent GitHub repository (`huelith` recommended), import source with `git clone` from upstream, apply this overlay in the independent checkout. Do not fork or open an upstream PR. GitHub repository creation has not been performed.

**Files:** `apps/huelith-kmp/` is a nested Gradle KMP project targeting Android using Compose Multiplatform. `crates/mobile-ffi/` is a Rust `cdylib` built by cargo-ndk and linked by Android JNI. It reuses the original LightCraft engine, not a reimplementation of color science.

**Privacy:** No `INTERNET` permission and no network service. User originals stored only in Android internal app files. `settings.json` persists independently; export saves a newly encoded file to MediaStore. Verify this in airplane mode.

**Known blockers:** This environment has no network-accessible git clone of upstream and no cargo/rustc/Gradle/Android SDK; cross-compile, installation and device tests are not completed. `QA.md` details these open gates. Do not describe the source as a verified release.

**Next work:** Full KMP editor parity requires mobile crop/mask interaction, custom preset import and persistent history UI, preservation of EXIF/ICC metadata, tiled full-res export (review the unmerged upstream PR #818), low-RAM optimization and actual devices. Avoid enabling cloud, model downloads or analytics. No CI by project preference.
