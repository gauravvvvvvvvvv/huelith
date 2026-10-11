//! Offline-only Android bridge to LightCraft's image processing pipeline.
//! All unsafe/JNI code stays in `android` and is never part of the imaging crates.
//! The original file is opened read-only. No socket, API, model download or telemetry exists here.
#![deny(clippy::unwrap_used, clippy::expect_used, clippy::panic, clippy::unimplemented, clippy::todo, clippy::unreachable)]

use std::sync::Arc;
use lightcraft_develop::{DevelopSettings, controls};
use lightcraft_pipeline::{Quality, RenderRequest, SourceInfo, StageCache};
use lightcraft_raster::{Rgb32f, Rgba8};

const PREVIEW_SOURCE_EDGE: usize = 2560;
const MAX_FILE_BYTES: u64 = 256 * 1024 * 1024;
const MAX_SOURCE_PIXELS: u64 = 48_000_000;
const MAX_EXPORT_EDGE: usize = 4096;
const MAX_EXPORT_PIXELS: usize = 24_000_000;

/// One open editor. All adjustments are non-destructive, expressed as serializable settings.
pub struct Editor {
    source: Arc<Rgb32f>,
    info: SourceInfo,
    settings: DevelopSettings,
    stages: StageCache,
    filename: String,
    undo: Vec<DevelopSettings>,
    redo: Vec<DevelopSettings>,
    drag_start: Option<DevelopSettings>,
}

impl Editor {
    pub fn open_file(path: &std::path::Path, saved_settings: &str) -> Result<Self, String> {
        let size = std::fs::metadata(path).map_err(|e| format!("Cannot open photo: {e}"))?.len();
        if size == 0 || size > MAX_FILE_BYTES {
            return Err("Source file must be between 1 byte and 256 MiB".into());
        }
        let bytes = std::fs::read(path).map_err(|e| e.to_string())?;
        Self::open_bytes(&bytes, saved_settings, path.file_name().and_then(|p| p.to_str()).unwrap_or("photo"))
    }

    fn open_bytes(bytes: &[u8], saved_settings: &str, filename: &str) -> Result<Self, String> {
        if bytes.is_empty() || bytes.len() as u64 > MAX_FILE_BYTES {
            return Err("Invalid or oversized image".into());
        }
        let probe = lightcraft_engine::files::probe_bytes(filename, bytes)?;
        if u64::from(probe.width) * u64::from(probe.height) > MAX_SOURCE_PIXELS {
            return Err("Source exceeds mobile 48 megapixel import limit".into());
        }
        let (source, info) = lightcraft_engine::files::load_bytes(bytes, PREVIEW_SOURCE_EDGE)?;
        if source.width == 0 || source.height == 0 {
            return Err("Image has no pixels".into());
        }
        let settings = if saved_settings.is_empty() {
            if info.raw { DevelopSettings::for_raw(info.as_shot_temp, info.as_shot_tint) }
            else { DevelopSettings::default() }
        } else {
            serde_json::from_str(saved_settings).map_err(|e| format!("Corrupt edit settings: {e}"))?
        };
        Ok(Self { source: Arc::new(source), info, settings, stages: StageCache::default(), filename: filename.into(), undo: Vec::new(), redo: Vec::new(), drag_start: None })
    }

    pub fn controls_json(&self) -> String {
        let specs: Vec<_> = controls::CONTROLS.iter().filter_map(|s| {
            let value = controls::get(&self.settings, s.id)?;
            Some(serde_json::json!({
                "id": s.id, "label": s.label, "section": s.section.label(),
                "min": s.min, "max": s.max, "default": s.default,
                "step": s.step, "decimals": s.decimals, "value": value
            }))
        }).collect();
        serde_json::to_string(&specs).unwrap_or_default()
    }

    pub fn set_control(&mut self, id: &str, value: f64) -> Result<(), String> {
        if !value.is_finite() { return Err("Adjustment must be a finite number".into()); }
        if controls::find(id).is_none() { return Err(format!("Unknown control: {id}")); }
        if self.drag_start.is_none() { self.drag_start = Some(self.settings.clone()); }
        if !controls::set(&mut self.settings, id, value) { return Err(format!("Unknown control: {id}")); }
        Ok(())
    }

    pub fn presets_json(&self) -> String {
        let data: Vec<_> = lightcraft_engine::presets::builtin().into_iter().map(|p| {
            serde_json::json!({"id": p.id, "name": p.name, "group": p.group})
        }).collect();
        serde_json::to_string(&data).unwrap_or_default()
    }

    pub fn apply_preset(&mut self, id: &str) -> Result<(), String> {
        let preset = lightcraft_engine::presets::builtin().into_iter().find(|p| p.id == id)
            .ok_or_else(|| format!("Unknown preset: {id}"))?;
        self.commit();
        let old = self.settings.clone();
        self.settings = self.settings.merged(&preset.settings).map_err(|e| e.to_string())?;
        self.drag_start = Some(old);
        self.commit();
        self.stages.clear();
        Ok(())
    }

    pub fn settings_json(&self) -> Result<String, String> {
        serde_json::to_string(&self.settings).map_err(|e| e.to_string())
    }

    pub fn commit(&mut self) {
        if let Some(before) = self.drag_start.take() {
            if before != self.settings {
                if self.undo.len() >= 100 { self.undo.remove(0); }
                self.undo.push(before);
                self.redo.clear();
            }
        }
    }

    pub fn undo(&mut self) -> bool {
        self.commit();
        if let Some(before) = self.undo.pop() {
            self.redo.push(std::mem::replace(&mut self.settings, before));
            self.stages.clear();
            true
        } else { false }
    }

    pub fn redo(&mut self) -> bool {
        self.commit();
        if let Some(next) = self.redo.pop() {
            self.undo.push(std::mem::replace(&mut self.settings, next));
            self.stages.clear();
            true
        } else { false }
    }

    pub fn reset(&mut self) {
        self.commit();
        let previous = self.settings.clone();
        self.settings = if self.info.raw {
            DevelopSettings::for_raw(self.info.as_shot_temp, self.info.as_shot_tint)
        } else { DevelopSettings::default() };
        if previous != self.settings {
            self.undo.push(previous);
            self.redo.clear();
        }
        self.stages.clear();
    }

    pub fn preview(&self, max_edge: usize, before: bool) -> Result<Vec<u8>, String> {
        let edge = max_edge.clamp(64, 2048);
        let settings = if before {
            if self.info.raw { DevelopSettings::for_raw(self.info.as_shot_temp, self.info.as_shot_tint) }
            else { DevelopSettings::default() }
        } else { self.settings.clone() };
        let mut req = RenderRequest::fit(edge, edge);
        req.quality = Quality::Full;
        let result = lightcraft_pipeline::render_cached(&self.source, &self.info, &settings, &req, &self.stages);
        png_bytes(&result.image)
    }

    pub fn filename(&self) -> &str { &self.filename }

    /// Renders a fresh decode from the local original. Never writes to the original.
    /// 24 MP and 4096px edge caps prevent unbounded allocations on mobile devices.
    pub fn export_file(&self, source_path: &std::path::Path, output: &std::path::Path,
                       edge: usize, png: bool) -> Result<(), String> {
        let size = std::fs::metadata(source_path).map_err(|e| e.to_string())?.len();
        if size == 0 || size > MAX_FILE_BYTES { return Err("Source file is empty or too large".into()); }
        let edge = edge.clamp(128, MAX_EXPORT_EDGE);
        let bytes = std::fs::read(source_path).map_err(|e| e.to_string())?;
        let (mut src, info) = lightcraft_engine::files::load_bytes(&bytes, edge)?;
        if src.width.saturating_mul(src.height) > MAX_EXPORT_PIXELS {
            let scale = (MAX_EXPORT_PIXELS as f64 / (src.width * src.height) as f64).sqrt();
            let w = (src.width as f64 * scale).floor().max(1.0) as usize;
            let h = (src.height as f64 * scale).floor().max(1.0) as usize;
            src = lightcraft_raster::resample::fit(&src, w, h, lightcraft_raster::resample::Filter::Box);
        }
        let req = RenderRequest::fit(edge, edge);
        let rendered = lightcraft_pipeline::render(&src, &info, &self.settings, &req);
        let image = lightcraft_codecs::EncodeImage::rgba8(&rendered.image);
        let meta = lightcraft_codecs::EncodeMeta::default();
        let payload = if png {
            lightcraft_codecs::encode_png(&image, &meta)
        } else {
            lightcraft_codecs::encode_jpeg(&image, 94, lightcraft_codecs::ChromaSubsampling::S444, &meta)
        }.map_err(|e| e.to_string())?;
        use std::io::Write;
        let mut f = std::fs::OpenOptions::new().write(true).create_new(true).open(output).map_err(|e| e.to_string())?;
        if let Err(e) = f.write_all(&payload).and_then(|_| f.sync_all()) {
            drop(f);
            let _ = std::fs::remove_file(output);
            return Err(format!("Export failed: {e}"));
        }
        Ok(())
    }
}

fn png_bytes(image: &Rgba8) -> Result<Vec<u8>, String> {
    lightcraft_codecs::encode_png(&lightcraft_codecs::EncodeImage::rgba8(image), &lightcraft_codecs::EncodeMeta::default())
        .map_err(|e| e.to_string())
}

#[cfg(target_os = "android")]
// JNI exported symbol attributes are necessarily unsafe under Rust 2024.
// This exception is isolated here; the editing engine itself remains safe Rust.
#[allow(unsafe_code)]
mod android;

#[cfg(test)]
mod tests {
    use super::*;
    use lightcraft_raster::Rgba8;
    fn sample() -> Vec<u8> {
        let pixels = Rgba8::from_fn(24, 16, |x, y| [((x * 7) % 255) as u8, ((y * 11) % 255) as u8, 75, 255]);
        png_bytes(&pixels).expect("test PNG")
    }

    #[test]
    fn invalid_input_does_not_open() {
        assert!(Editor::open_bytes(b"invalid", "", "broken.jpg").is_err());
        assert!(Editor::open_bytes(b"", "", "empty.png").is_err());
    }

    #[test]
    fn editing_does_not_mutate_original_and_is_serializable() {
        let original = sample();
        let copy = original.clone();
        let mut ed = Editor::open_bytes(&original, "", "sample.png").expect("open");
        assert!(ed.set_control("light.exposure", 1.25).is_ok());
        assert!(ed.set_control("light.exposure", f64::NAN).is_err());
        assert!(ed.set_control("invalid.control", 1.0).is_err());
        let settings = ed.settings_json().expect("json");
        let other = Editor::open_bytes(&original, &settings, "sample.png").expect("reopen");
        assert_eq!(settings, other.settings_json().expect("json"));
        assert_eq!(original, copy);
    }

    #[test]
    fn preview_changes_and_before_stays_original() {
        let mut ed = Editor::open_bytes(&sample(), "", "sample.png").expect("open");
        let before = ed.preview(256, true).expect("render");
        let initial = ed.preview(256, false).expect("render");
        assert_eq!(before, initial);
        ed.set_control("light.exposure", 2.0).expect("adjust");
        assert_ne!(ed.preview(256, false).expect("render"), before);
        assert_eq!(ed.preview(256, true).expect("render"), before);
        ed.commit();
        assert!(ed.undo());
        assert_eq!(ed.preview(256, false).expect("render"), before);
        assert!(ed.redo());
        ed.reset();
        assert_eq!(ed.preview(256, false).expect("render"), before);
        let presets: serde_json::Value = serde_json::from_str(&ed.presets_json()).expect("json");
        assert!(presets.as_array().is_some_and(|a| a.len() >= 10));
        assert!(ed.apply_preset("lc.vivid-pop").is_ok());
        assert!(ed.apply_preset("missing").is_err());
    }
}
