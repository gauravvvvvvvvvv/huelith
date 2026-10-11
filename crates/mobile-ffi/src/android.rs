//! The only JNI boundary. No network operations. Each open session is reference-counted
//! behind a process-wide registry, so Android handles cannot become dangling pointers.
//! JNI necessarily crosses an unsafe foreign-code boundary; no raw-pointer dereferencing here.

use std::collections::HashMap;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::path::Path;
use std::sync::{Arc, Mutex, OnceLock, atomic::{AtomicI64, Ordering}};
use jni::{JNIEnv, objects::{JClass, JString}, sys::{jbyteArray, jdouble, jint, jlong, jstring}};
use super::Editor;

type Session = Arc<Mutex<Editor>>;
static SESSIONS: OnceLock<Mutex<HashMap<i64, Session>>> = OnceLock::new();
static NEXT: AtomicI64 = AtomicI64::new(1);
fn sessions() -> &'static Mutex<HashMap<i64, Session>> { SESSIONS.get_or_init(|| Mutex::new(HashMap::new())) }
fn error(env: &mut JNIEnv<'_>, msg: &str) {
    let _ = env.throw_new("java/lang/IllegalStateException", msg);
}
// JNI raw object pointers have no stable `Default` impl across our Rust MSRV.
// Return an explicitly typed null or zero on failure after throwing the Java exception.
trait JniFallback { fn null_result() -> Self; }
impl<T> JniFallback for *mut T { fn null_result() -> Self { std::ptr::null_mut() } }
impl JniFallback for i64 { fn null_result() -> Self { 0 } }
impl JniFallback for u8 { fn null_result() -> Self { 0 } }
impl JniFallback for () { fn null_result() -> Self {} }
fn guarded<T: JniFallback>(env: &mut JNIEnv<'_>, f: impl FnOnce(&mut JNIEnv<'_>) -> Result<T, String>) -> T {
    match catch_unwind(AssertUnwindSafe(|| f(env))) {
        Ok(Ok(v)) => v,
        Ok(Err(e)) => { error(env, &e); T::null_result() },
        Err(_) => { error(env, "Native image processing failed safely"); T::null_result() },
    }
}
fn text(env: &mut JNIEnv<'_>, v: JString<'_>) -> Result<String, String> {
    env.get_string(&v).map(|s| s.into()).map_err(|e| e.to_string())
}
fn session(handle: jlong) -> Result<Session, String> {
    if handle <= 0 { return Err("Invalid photo editor session".into()); }
    sessions().lock().unwrap_or_else(|e| e.into_inner()).get(&handle).cloned().ok_or("Editor session has closed".into())
}
fn with_editor<T>(handle: jlong, f: impl FnOnce(&mut Editor) -> Result<T, String>) -> Result<T, String> {
    let item = session(handle)?;
    let mut ed = item.lock().unwrap_or_else(|e| e.into_inner());
    f(&mut ed)
}
fn make_string(env: &mut JNIEnv<'_>, value: String) -> Result<jstring, String> {
    env.new_string(value).map(|s| s.into_raw()).map_err(|e| e.to_string())
}
fn make_bytes(env: &mut JNIEnv<'_>, data: Vec<u8>) -> Result<jbyteArray, String> {
    env.byte_array_from_slice(&data).map(|a| a.into_raw()).map_err(|e| e.to_string())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cc_gvrv_huelith_RustEditor_nativeOpen(mut env: JNIEnv, _class: JClass,
                                                                       path: JString, settings: JString) -> jlong {
    guarded(&mut env, |env| {
        let path = text(env, path)?;
        let settings = text(env, settings)?;
        let editor = Editor::open_file(Path::new(&path), &settings)?;
        let id = NEXT.fetch_add(1, Ordering::Relaxed);
        sessions().lock().unwrap_or_else(|e| e.into_inner()).insert(id, Arc::new(Mutex::new(editor)));
        Ok(id)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cc_gvrv_huelith_RustEditor_nativeClose(mut env: JNIEnv, _class: JClass, handle: jlong) {
    guarded(&mut env, |_env| {
        sessions().lock().unwrap_or_else(|e| e.into_inner()).remove(&handle);
        Ok(())
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cc_gvrv_huelith_RustEditor_nativeControls(mut env: JNIEnv, _class: JClass,
                                                                           handle: jlong) -> jstring {
    guarded(&mut env, |env| {
        let json = with_editor(handle, |ed| Ok(ed.controls_json()))?;
        make_string(env, json)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cc_gvrv_huelith_RustEditor_nativePresets(mut env: JNIEnv, _class: JClass,
                                                                          handle: jlong) -> jstring {
    guarded(&mut env, |env| make_string(env, with_editor(handle, |ed| Ok(ed.presets_json()))?))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cc_gvrv_huelith_RustEditor_nativeApplyPreset(mut env: JNIEnv, _class: JClass,
                                                                      handle: jlong, id: JString) {
    guarded(&mut env, |env| {
        let id = text(env, id)?;
        with_editor(handle, |ed| ed.apply_preset(&id))
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cc_gvrv_huelith_RustEditor_nativeSettings(mut env: JNIEnv, _class: JClass,
                                                                           handle: jlong) -> jstring {
    guarded(&mut env, |env| make_string(env, with_editor(handle, |ed| ed.settings_json())?))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cc_gvrv_huelith_RustEditor_nativeSetControl(mut env: JNIEnv, _class: JClass,
                                                                  handle: jlong, id: JString, value: jdouble) {
    guarded(&mut env, |env| {
        let id = text(env, id)?;
        with_editor(handle, |ed| ed.set_control(&id, value))
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cc_gvrv_huelith_RustEditor_nativeCommit(mut env: JNIEnv, _class: JClass, handle: jlong) {
    guarded(&mut env, |_env| with_editor(handle, |ed| { ed.commit(); Ok(()) }));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cc_gvrv_huelith_RustEditor_nativeUndo(mut env: JNIEnv, _class: JClass, handle: jlong) -> jni::sys::jboolean {
    guarded(&mut env, |_env| with_editor(handle, |ed| Ok(if ed.undo() { 1 } else { 0 })))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cc_gvrv_huelith_RustEditor_nativeRedo(mut env: JNIEnv, _class: JClass, handle: jlong) -> jni::sys::jboolean {
    guarded(&mut env, |_env| with_editor(handle, |ed| Ok(if ed.redo() { 1 } else { 0 })))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cc_gvrv_huelith_RustEditor_nativeReset(mut env: JNIEnv, _class: JClass,
                                                                      handle: jlong) {
    guarded(&mut env, |_env| with_editor(handle, |ed| { ed.reset(); Ok(()) }));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cc_gvrv_huelith_RustEditor_nativePreview(mut env: JNIEnv, _class: JClass,
                                                                       handle: jlong, edge: jint, before: jni::sys::jboolean) -> jbyteArray {
    guarded(&mut env, |env| {
        let bytes = with_editor(handle, |ed| ed.preview(edge.max(64) as usize, before != 0))?;
        make_bytes(env, bytes)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cc_gvrv_huelith_RustEditor_nativeExport(mut env: JNIEnv, _class: JClass,
                                                                       handle: jlong, original: JString,
                                                                       output: JString, edge: jint,
                                                                       png: jni::sys::jboolean) {
    guarded(&mut env, |env| {
        let original = text(env, original)?;
        let output = text(env, output)?;
        with_editor(handle, |ed| ed.export_file(Path::new(&original), Path::new(&output), edge.max(128) as usize, png != 0))
    });
}
