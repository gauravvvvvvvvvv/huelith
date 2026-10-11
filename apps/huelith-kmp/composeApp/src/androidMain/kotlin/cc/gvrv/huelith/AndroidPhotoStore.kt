package cc.gvrv.huelith

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Entirely local, private photo vault. Imported originals are immutable by design. */
class AndroidPhotoStore(private val context: Context) : PhotoStore {
    private val root = File(context.filesDir, "library").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private var handle = 0L
    private var editJob: Job? = null
    private val pending = ConcurrentHashMap<String, Double>()
    private val _photos = MutableStateFlow<List<LibraryPhoto>>(emptyList())
    private val _open = MutableStateFlow<OpenPhoto?>(null)
    private val _busy = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)

    override val photos: StateFlow<List<LibraryPhoto>> = _photos
    override val openPhoto: StateFlow<OpenPhoto?> = _open
    override val busy: StateFlow<Boolean> = _busy
    override val message: StateFlow<String?> = _message

    init {
        scope.launch { refresh() }
    }

    private fun folder(id: String) = File(root, id)
    private fun original(id: String): File = folder(id).listFiles()?.firstOrNull { it.name.startsWith("original.") }
        ?: error("Original photo missing")
    private fun settingsFile(id: String) = File(folder(id), "settings.json")

    private fun inspectControls(handle: Long): List<EditControl> {
        val values = JSONArray(RustEditor.nativeControls(handle))
        return (0 until values.length()).map { i ->
            val x = values.getJSONObject(i)
            EditControl(x.getString("id"), x.getString("label"), x.getString("section"),
                x.getDouble("min"), x.getDouble("max"), x.getDouble("default"),
                x.getDouble("step"), x.getInt("decimals"), x.getDouble("value"))
        }
    }

    private fun inspectPresets(handle: Long): List<PhotoPreset> {
        val values = JSONArray(RustEditor.nativePresets(handle))
        return (0 until values.length()).map { index ->
            val json = values.getJSONObject(index)
            PhotoPreset(json.getString("id"), json.getString("name"), json.getString("group"))
        }
    }

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        val result = root.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            runCatching {
                val meta = JSONObject(File(dir, "meta.json").readText())
                LibraryPhoto(dir.name, meta.getString("title"), File(dir, "thumb.png").readBytes())
            }.getOrNull()
        }.orEmpty()
        _photos.value = result.sortedBy { it.title.lowercase() }
    }

    /** Android's picker opens the URI; no broad gallery permission, cloud or network API needed. */
    suspend fun importPhoto(uri: Uri) {
        withContext(Dispatchers.IO) {
            _busy.value = true
            val dir = folder(UUID.randomUUID().toString())
            try {
                dir.mkdirs()
                val label = displayName(uri).take(140).ifBlank { "Untitled photo" }
                val extension = label.substringAfterLast('.', "jpg").lowercase()
                    .takeIf { it.matches(Regex("[a-z0-9]{2,5}")) } ?: "jpg"
                val part = File(dir, "original.$extension")
                val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read selected photo")
                input.use { stream ->
                    FileOutputStream(part).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = stream.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= 256L * 1024 * 1024) { "File exceeds 256 MiB safety limit" }
                            out.write(buffer, 0, n)
                        }
                        out.fd.sync()
                    }
                }
                var temporary = 0L
                try {
                    temporary = RustEditor.nativeOpen(part.absolutePath, "")
                    val thumbnail = RustEditor.nativePreview(temporary, 380, false)
                    File(dir, "thumb.png").writeBytes(thumbnail)
                    writeAtomically(settingsFile(dir.name), RustEditor.nativeSettings(temporary))
                    writeAtomically(File(dir, "meta.json"), JSONObject().put("title", label).toString())
                } finally {
                    if (temporary != 0L) RustEditor.nativeClose(temporary)
                }
                refresh()
                selectPhoto(dir.name)
            } catch (t: Exception) {
                dir.deleteRecursively() // never expose a half-imported entry
                _message.value = "Import failed: ${t.message ?: "unreadable photo"}"
            } finally {
                _busy.value = false
            }
        }
    }

    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.replace('/', '_')?.replace('\\', '_') ?: "photo.jpg"

    override suspend fun selectPhoto(id: String) = withContext(Dispatchers.IO) {
        _busy.value = true
        try {
            gate.withLock {
                applyPendingLocked()
                if (handle != 0L) RustEditor.nativeCommit(handle)
                val photo = _photos.value.firstOrNull { it.id == id } ?: error("Photo not found")
                val saved = settingsFile(id).takeIf(File::exists)?.readText().orEmpty()
                // Don't destroy the working editor until the next photo has decoded and
                // rendered successfully. An unsupported RAW should not lose active edits.
                val candidate = RustEditor.nativeOpen(original(id).absolutePath, saved)
                try {
                    val screen = OpenPhoto(photo,
                        RustEditor.nativePreview(candidate, 1500, false),
                        inspectControls(candidate), inspectPresets(candidate))
                    val old = handle
                    handle = candidate
                    _open.value = screen
                    if (old != 0L) RustEditor.nativeClose(old)
                } catch (failure: Exception) {
                    RustEditor.nativeClose(candidate)
                    throw failure
                }
            }
        } catch (failure: Exception) {
            _message.value = "Cannot open image: ${failure.message}"
        } finally {
            _busy.value = false
        }
    }

    override fun updateControl(id: String, value: Double) {
        val open = _open.value ?: return
        if (!value.isFinite()) return
        val control = open.controls.find { it.id == id } ?: return
        val bounded = value.coerceIn(control.min, control.max)
        _open.value = open.copy(controls = open.controls.map { if (it.id == id) it.copy(value = bounded) else it })
        pending[id] = bounded
        editJob?.cancel()
        editJob = scope.launch {
            delay(85) // coalesce touch-slider events, then render once on a worker
            runCatching { gate.withLock { applyPendingLocked() } }
                .onFailure { _message.value = "Edit failed: ${it.message}" }
        }
    }

    override fun finishAdjustment() {
        editJob?.cancel()
        editJob = scope.launch {
            runCatching { gate.withLock { applyPendingLocked(); if (handle != 0L) RustEditor.nativeCommit(handle) } }
                .onFailure { _message.value = "Edit failed: ${it.message}" }
        }
    }

    private fun applyPendingLocked() {
        if (pending.isEmpty()) return
        val updates = pending.toMap()
        val current = _open.value ?: return
        if (handle == 0L) return
        // Never dequeue updates before their settings are durable. A full disk or an
        // unexpected JNI error must not silently discard the user's slider changes.
        for ((key, value) in updates) RustEditor.nativeSetControl(handle, key, value)
        writeAtomically(settingsFile(current.photo.id), RustEditor.nativeSettings(handle))
        for ((key, value) in updates) pending.remove(key, value)
        val png = RustEditor.nativePreview(handle, 1500, false)
        if (_open.value?.photo?.id == current.photo.id) {
            val controls = inspectControls(handle).map { ctl ->
                pending[ctl.id]?.let { ctl.copy(value = it) } ?: ctl
            }
            _open.value = _open.value?.copy(preview = png, controls = controls)
        }
    }

    override suspend fun reset() = withContext(Dispatchers.IO) {
        try {
            editJob?.cancel()
            gate.withLock {
                pending.clear()
                val previous = _open.value ?: return@withLock
                if (handle == 0L) return@withLock
                RustEditor.nativeReset(handle)
                writeAtomically(settingsFile(previous.photo.id), RustEditor.nativeSettings(handle))
                _open.value = previous.copy(
                    controls = inspectControls(handle),
                    preview = RustEditor.nativePreview(handle, 1500, false),
                    showingBefore = false
                )
            }
        } catch (failure: Exception) {
            _message.value = "Reset failed: ${failure.message ?: "unknown error"}"
        }
    }

    override suspend fun undo() = navigateHistory(redo = false)

    override suspend fun redo() = navigateHistory(redo = true)

    private suspend fun navigateHistory(redo: Boolean) = withContext(Dispatchers.IO) {
        try {
            editJob?.cancel()
            gate.withLock {
                applyPendingLocked()
                if (handle == 0L) return@withLock
                val selected = _open.value ?: return@withLock
                val changed = if (redo) RustEditor.nativeRedo(handle) else RustEditor.nativeUndo(handle)
                if (changed) {
                    writeAtomically(settingsFile(selected.photo.id), RustEditor.nativeSettings(handle))
                    _open.value = selected.copy(
                        controls = inspectControls(handle),
                        preview = RustEditor.nativePreview(handle, 1500, false), showingBefore = false
                    )
                }
            }
        } catch (failure: Exception) {
            _message.value = "History update failed: ${failure.message ?: "unknown error"}"
        }
    }

    override suspend fun applyPreset(id: String) = withContext(Dispatchers.IO) {
        try {
            editJob?.cancel()
            gate.withLock {
                applyPendingLocked()
                if (handle == 0L) return@withLock
                val selected = _open.value ?: return@withLock
                RustEditor.nativeApplyPreset(handle, id)
                writeAtomically(settingsFile(selected.photo.id), RustEditor.nativeSettings(handle))
                _open.value = selected.copy(controls = inspectControls(handle),
                    preview = RustEditor.nativePreview(handle, 1500, false), showingBefore = false)
            }
        } catch (failure: Exception) {
            _message.value = "Preset failed: ${failure.message ?: "unknown error"}"
        }
    }

    override suspend fun compare(show: Boolean) = withContext(Dispatchers.IO) {
        try {
            gate.withLock {
                applyPendingLocked()
                if (handle != 0L) RustEditor.nativeCommit(handle)
                val current = _open.value ?: return@withLock
                val old = if (show && current.before == null && handle != 0L)
                    RustEditor.nativePreview(handle, 1500, true) else current.before
                _open.value = current.copy(before = old, showingBefore = show)
            }
        } catch (failure: Exception) {
            _message.value = "Comparison failed: ${failure.message ?: "unknown error"}"
        }
    }

    /** Export to Android's local MediaStore gallery without a storage permission or server. */
    override suspend fun export(png: Boolean): String = withContext(Dispatchers.IO) {
        _busy.value = true
        val suffix = if (png) "png" else "jpg"
        val tmp = File(context.cacheDir, "export-${UUID.randomUUID()}.$suffix")
        try {
            gate.withLock {
                applyPendingLocked()
                if (handle != 0L) RustEditor.nativeCommit(handle)
                val current = _open.value ?: error("No open photo")
                RustEditor.nativeExport(handle, original(current.photo.id).absolutePath, tmp.absolutePath, 4096, png)
            }
            val name = "Huelith-${System.currentTimeMillis()}.$suffix"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, if (png) "image/png" else "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Huelith")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val dest = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Couldn't create an export in the local gallery")
            try {
                context.contentResolver.openOutputStream(dest)?.use { output ->
                    tmp.inputStream().use { it.copyTo(output, 64 * 1024) }
                } ?: error("Couldn't write image")
                val published = context.contentResolver.update(dest, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null)
                check(published == 1) { "Gallery could not finalize the export" }
            } catch (e: Exception) {
                context.contentResolver.delete(dest, null, null)
                throw e
            }
            "Saved $name to Pictures/Huelith"
        } catch (t: Exception) {
            "Export failed: ${t.message}"
        }.also { result ->
            _message.value = result
            tmp.delete()
            _busy.value = false
        }
    }

    override fun close() {
        editJob?.cancel()
        scope.launch {
            try {
                gate.withLock {
                    applyPendingLocked()
                    if (handle != 0L) { RustEditor.nativeCommit(handle); RustEditor.nativeClose(handle) }
                    handle = 0L
                    _open.value = null
                }
            } catch (failure: Exception) {
                // Keep the editor visible so the user can retry saving; do not lose edits.
                _message.value = "Unable to close photo safely: ${failure.message ?: "save failed"}"
            }
        }
    }

    override fun dismissMessage() { _message.value = null }

    private fun writeAtomically(dest: File, content: String) {
        val temp = File(dest.parentFile, "${dest.name}.tmp")
        FileOutputStream(temp).use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        if (!temp.renameTo(dest)) {
            temp.delete()
            error("Could not save edits safely")
        }
    }
}
