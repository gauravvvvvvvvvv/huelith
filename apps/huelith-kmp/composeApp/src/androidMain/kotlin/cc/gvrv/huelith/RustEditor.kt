package cc.gvrv.huelith

/** JNI facade for the vendored upstream Rust engine. No JNI calls on the UI thread. */
internal object RustEditor {
    init { System.loadLibrary("huelith_mobile_ffi") }

    external fun nativeOpen(path: String, settings: String): Long
    external fun nativeClose(handle: Long)
    external fun nativeControls(handle: Long): String
    external fun nativeSettings(handle: Long): String
    external fun nativePresets(handle: Long): String
    external fun nativeApplyPreset(handle: Long, id: String)
    external fun nativeSetControl(handle: Long, id: String, value: Double)
    external fun nativeCommit(handle: Long)
    external fun nativeUndo(handle: Long): Boolean
    external fun nativeRedo(handle: Long): Boolean
    external fun nativeReset(handle: Long)
    external fun nativePreview(handle: Long, edge: Int, before: Boolean): ByteArray
    external fun nativeExport(handle: Long, original: String, output: String, edge: Int, png: Boolean)
}
