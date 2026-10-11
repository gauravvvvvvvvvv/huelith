# Huelith brand and upstream attribution

**Product name:** Huelith (working name, subject to trademark clearance).
**Android app label:** Huelith.
**Android package ID:** `cc.gvrv.huelith`.
**Android workspace module:** `apps/huelith-kmp`.
**Native Android library:** `libhuelith_mobile_ffi.so` (contains integrated Rust engine).

Huelith is independent and is **not affiliated with or endorsed by Storytold / ArtCraft**.
The photo-processing engine is derived from the source at `https://github.com/storytold/lightcraft` (pinned revision documented in the build scripts).
Preserve all upstream license files, copyright notices, NOTICE, and any third-party notices in the independent source repository and distribution.
The upstream code is predominantly MIT OR Apache-2.0; check separate dependencies/assets and model licenses before distributing them.
Do **not** copy LightCraft logos, marks, official graphics, or reuse the upstream product name as this app's title.
Before publishing the **independent GitHub clone**, remove original restricted brand graphics from upstream `docs/brand/` and retain the legally required license notices. They are not covered by the Rust code license.
The Android build script bundles upstream `NOTICE`, `LICENSE-MIT`, `LICENSE-APACHE`, and asset attributions (if present) as APK assets. Verify the terms for any separately shipped models/assets.
Only source identifiers and references necessary to identify the original licensed code retain the upstream name.

A public web search found no clear existing photo-editor product called Huelith on 11 October 2026. This is **not legal trademark clearance**. Search relevant official trademark registries before app store publication.

No fork, issue, or pull request is required. Clone licensed upstream code into a new independent repository and add this overlay there.
