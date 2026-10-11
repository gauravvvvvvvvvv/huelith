package cc.gvrv.huelith

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val editorDark = Color(0xFF101116)
private val panel = Color(0xFF1D2027)
private val accent = Color(0xFFE2BA77)

@Composable
expect fun PhotoBytesImage(bytes: ByteArray, modifier: Modifier = Modifier)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuelithApp(store: PhotoStore, onImport: () -> Unit) {
    val photos by store.photos.collectAsState()
    val selected by store.openPhoto.collectAsState()
    val busy by store.busy.collectAsState()
    val message by store.message.collectAsState()
    val scope = rememberCoroutineScope()
    var exportMenu by remember { mutableStateOf(false) }
    var activeTab by remember(selected?.photo?.id) { mutableStateOf("Presets") }

    MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = editorDark, surface = panel)) {
        Scaffold(containerColor = editorDark, topBar = {
            TopAppBar(
                title = { Column {
                    Text(if (selected == null) "Huelith" else selected!!.photo.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Local photo studio · Offline", style = MaterialTheme.typography.labelSmall, color = accent)
                } },
                navigationIcon = { if (selected != null) IconButton(onClick = { store.close() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to library")
                } },
                actions = {
                    if (selected == null) {
                        IconButton(onClick = onImport) { Icon(Icons.Default.AddPhotoAlternate, "Import photo") }
                    } else {
                        IconButton(onClick = { scope.launch { store.undo() } }, enabled = !busy) {
                            Icon(Icons.Default.Undo, "Undo")
                        }
                        IconButton(onClick = { scope.launch { store.redo() } }, enabled = !busy) {
                            Icon(Icons.Default.Redo, "Redo")
                        }
                        IconButton(onClick = { scope.launch { store.reset() } }, enabled = !busy) {
                            Icon(Icons.Default.Refresh, "Reset all edits")
                        }
                        Box {
                            IconButton(onClick = { exportMenu = true }, enabled = !busy) {
                                Icon(Icons.Default.FileDownload, "Export")
                            }
                            DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                                DropdownMenuItem(text = { Text("Save JPEG to Gallery") }, onClick = {
                                    exportMenu = false; scope.launch { store.export(png = false) }
                                })
                                DropdownMenuItem(text = { Text("Save PNG to Gallery") }, onClick = {
                                    exportMenu = false; scope.launch { store.export(png = true) }
                                })
                            }
                        }
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = panel)
            )
        }, snackbarHost = {
            if (message != null) {
                Snackbar(modifier = Modifier.padding(12.dp), action = {
                    TextButton(onClick = store::dismissMessage) { Text("Dismiss") }
                }) { Text(message.orEmpty()) }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (selected == null) {
                    if (photos.isEmpty()) EmptyLibrary(onImport)
                    else PhotoLibrary(photos, onImport, onSelect = { scope.launch { store.selectPhoto(it) } })
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black), contentAlignment = Alignment.Center) {
                            val image = if (selected!!.showingBefore) selected!!.before ?: selected!!.preview else selected!!.preview
                            PhotoBytesImage(image, Modifier.fillMaxSize())
                            if (busy) CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(14.dp))
                            TextButton(
                                onClick = { scope.launch { store.compare(!selected!!.showingBefore) } },
                                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                                colors = ButtonDefaults.textButtonColors(containerColor = Color.Black.copy(alpha = .65f))
                            ) { Text(if (selected!!.showingBefore) "Original · tap to return" else "Compare original / edited") }
                        }
                        val groups = listOf("Presets") + controlGroups(selected!!.controls)
                        val current = if (activeTab in groups) activeTab else groups.firstOrNull() ?: "Light"
                        ScrollableTabRow(selectedTabIndex = groups.indexOf(current).coerceAtLeast(0),
                            edgePadding = 10.dp, containerColor = panel) {
                            groups.forEach { tab ->
                                Tab(selected = current == tab, onClick = { activeTab = tab },
                                    text = { Text(tab, maxLines = 1) })
                            }
                        }
                        LazyColumn(
                            Modifier.fillMaxWidth().heightIn(min = 190.dp, max = 300.dp).background(panel),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            if (current == "Presets") {
                                items(selected!!.presets, key = { it.id }) { preset ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text(preset.name)
                                            Text(preset.group, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        }
                                        TextButton(onClick = { scope.launch { store.applyPreset(preset.id) } }) { Text("Apply") }
                                    }
                                }
                            } else {
                                items(selected!!.controls.filter { it.section == current }, key = { it.id }) { ctl ->
                                    EditSlider(selected!!.photo.id, ctl, store)
                                }
                            }
                        }
                    }
                }
                if (busy && selected == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(52.dp), tint = accent)
        Spacer(Modifier.height(18.dp))
        Text("Your darkroom, on your device", style = MaterialTheme.typography.headlineSmall)
        Text("Import a JPEG, PNG, or supported RAW file. Your originals and edits never leave your phone.",
            color = Color.LightGray, modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))
        Button(onClick = onImport) { Text("Import photos") }
    }
}

@Composable
private fun PhotoLibrary(photos: List<LibraryPhoto>, onImport: () -> Unit, onSelect: (String) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("My Photos · ${photos.size}", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onImport) { Text("Add photos") }
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(108.dp), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(6.dp)) {
            items(photos, key = { it.id }) { item ->
                Column(Modifier.padding(4.dp).clickable { onSelect(item.id) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(panel, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center) {
                        PhotoBytesImage(item.thumbnail, Modifier.fillMaxSize())
                    }
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun EditSlider(photoId: String, control: EditControl, store: PhotoStore) {
    var current by remember(photoId, control.id) { mutableFloatStateOf(control.value.toFloat()) }
    LaunchedEffect(control.value) {
        // Changes from reset / external actions are reflected in the control.
        if (kotlin.math.abs(control.value.toFloat() - current) > 0.0001f) current = control.value.toFloat()
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(control.label, style = MaterialTheme.typography.bodySmall)
            Text(formatValue(control, current.toDouble()), color = accent,
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = current,
            onValueChange = {
                current = it
                store.updateControl(control.id, it.toDouble())
            },
            onValueChangeFinished = store::finishAdjustment,
            valueRange = control.min.toFloat()..control.max.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
