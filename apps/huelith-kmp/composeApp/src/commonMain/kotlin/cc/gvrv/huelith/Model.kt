package cc.gvrv.huelith

import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToLong

/** Common KMP contract; no IO/network dependency is present in common code. */
data class LibraryPhoto(val id: String, val title: String, val thumbnail: ByteArray)

data class EditControl(
    val id: String, val label: String, val section: String,
    val min: Double, val max: Double, val default: Double,
    val step: Double, val decimals: Int, val value: Double
)

data class PhotoPreset(val id: String, val name: String, val group: String)

data class OpenPhoto(
    val photo: LibraryPhoto,
    val preview: ByteArray,
    val controls: List<EditControl>,
    val presets: List<PhotoPreset> = emptyList(),
    val before: ByteArray? = null,
    val showingBefore: Boolean = false
)

interface PhotoStore {
    val photos: StateFlow<List<LibraryPhoto>>
    val openPhoto: StateFlow<OpenPhoto?>
    val busy: StateFlow<Boolean>
    val message: StateFlow<String?>
    suspend fun selectPhoto(id: String)
    fun updateControl(id: String, value: Double)
    fun finishAdjustment()
    suspend fun reset()
    suspend fun undo()
    suspend fun redo()
    suspend fun applyPreset(id: String)
    suspend fun export(png: Boolean): String
    suspend fun compare(show: Boolean)
    fun close()
    fun dismissMessage()
}

fun controlGroups(controls: List<EditControl>): List<String> =
    controls.map { it.section }.distinct()

/** Rounds rather than truncating: visual slider values must agree with the saved controls. */
fun formatValue(control: EditControl, value: Double): String {
    val places = control.decimals.coerceIn(0, 2)
    val scale = when (places) { 0 -> 1L; 1 -> 10L; else -> 100L }
    val ticks = (value * scale).roundToLong()
    val magnitude = abs(ticks)
    val integer = magnitude / scale
    val fraction = if (places == 0) "" else "." + (magnitude % scale).toString().padStart(places, '0')
    val sign = when {
        ticks < 0 -> "-"
        ticks > 0 && control.min < 0 -> "+"
        else -> ""
    }
    return "$sign$integer$fraction"
}
