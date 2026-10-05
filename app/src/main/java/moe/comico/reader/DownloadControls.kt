package moe.comico.reader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun DownloadFolderButton(state: AppState, model: ReaderViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(model::chooseDownloadFolder) }
    TextButton(onClick = { picker.launch(state.downloadFolder?.let(Uri::parse)) }, enabled = !state.downloadBusy && state.downloadQueue.none { it.active }) {
        Icon(Icons.Rounded.FolderOpen, null)
        Text(if (state.downloadFolder == null) "Choose download folder" else "Change download folder", Modifier.padding(start = 8.dp))
    }
}

@Composable
fun ChapterDownloadAction(state: AppState, model: ReaderViewModel, chapter: Chapter) {
    var menu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val saved = state.offlineChapters.firstOrNull { it.chapter.id == chapter.id }
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { model.downloadChapter(chapter, it) }
    }
    fun run(action: String) {
        menu = false
        if (action == "import") zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        else model.downloadChapter(chapter)
    }
    if (deleting && saved != null) AlertDialog(onDismissRequest = { deleting = false },
        title = { Text("Delete downloaded chapter ${chapter.number}?") },
        text = { Text("This deletes its downloaded ZIP. You can still read the chapter online.") },
        confirmButton = { TextButton(onClick = { deleting = false; model.removeOfflineChapter(saved) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
    Box {
        IconButton(onClick = { menu = true }, enabled = !state.downloadBusy) {
            Icon(if (saved == null) Icons.Rounded.Download else Icons.Rounded.DownloadDone,
                if (saved == null) "Download chapter ${chapter.number}" else "Downloaded chapter ${chapter.number}")
        }
        DropdownMenu(menu, { menu = false }) {
            if (saved != null) {
                DropdownMenuItem(text = { Text("Read offline") }, onClick = { menu = false; model.openOfflineChapter(saved) })
                DropdownMenuItem(text = { Text("Delete download") }, onClick = { menu = false; deleting = true })
            } else {
                DropdownMenuItem(text = { Text("Download for offline reading") }, enabled = state.account.user != null && chapter.external.isBlank(),
                    onClick = { run("download") })
                DropdownMenuItem(text = { Text("Import chapter ZIP") }, onClick = { run("import") })
            }
        }
    }
}

@Composable
fun DownloadStatus(state: AppState) {
    val task = state.downloadQueue.lastOrNull { it.manga.id == state.selected?.id }
    if (task?.active == true || (state.downloadBusy && state.downloadMessageMangaId == state.selected?.id)) LinearProgressIndicator(Modifier.fillMaxWidth())
    val message = chapterDownloadMessage(state.downloadQueue, state.selected?.id)
        ?: state.downloadMessage.takeIf { state.downloadMessageMangaId == state.selected?.id }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
fun DownloadsScreen(state: AppState, model: ReaderViewModel) {
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::openOfflineZip) }
    var queue by rememberSaveable { mutableStateOf(state.downloadQueue.any { it.status != "Completed" }) }
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    fun notificationsAllowed() = android.os.Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    var notificationsEnabled by remember { mutableStateOf(notificationsAllowed()) }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) notificationsEnabled = notificationsAllowed()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(queue, { queue = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Queue") }
                SegmentedButton(!queue, { queue = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Saved") }
            }
        }
        if (queue) {
            if (!notificationsEnabled) item {
                Text("Enable notifications to see download progress outside the app.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text("Notification settings") }
            }
            if (state.downloadQueue.isEmpty()) item { Text("No downloads queued.") }
            items(state.downloadQueue, key = { it.id }) { task ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(task.manga.title, style = MaterialTheme.typography.titleMedium)
                        Text(task.status, color = MaterialTheme.colorScheme.primary)
                        Text("${task.completed} saved · ${task.skipped} skipped · ${task.chapters.size} remaining",
                            style = MaterialTheme.typography.bodySmall)
                        if (task.active) LinearProgressIndicator(Modifier.fillMaxWidth())
                        task.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        if (task.active) TextButton(onClick = { model.stopDownload(task.id) }) {
                            Icon(Icons.Rounded.Stop, null); Text("Stop manga", Modifier.padding(start = 8.dp))
                        } else if (task.status != "Completed") TextButton(onClick = { model.retryDownload(task.id) }) {
                            Icon(Icons.Rounded.Refresh, null); Text("Retry", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        } else {
        item {
            TextButton(onClick = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, enabled = !state.downloadBusy) {
                Icon(Icons.Rounded.FolderZip, null); Text("Open chapter ZIP", Modifier.padding(start = 8.dp))
            }
            if (state.downloadMessageMangaId == null) state.downloadMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        if (state.offlineChapters.isEmpty()) item { Text("No downloaded chapters yet. Open a manga's chapter list and tap its download icon.") }
        items(state.offlineChapters, key = { it.chapter.id }) { entry ->
            Surface(onClick = { model.openOfflineChapter(entry) }, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.manga.title, style = MaterialTheme.typography.titleSmall)
                        Text("Chapter ${entry.chapter.number} · ${entry.chapter.language.uppercase()}", style = MaterialTheme.typography.bodySmall)
                    }
                    var deleting by remember { mutableStateOf(false) }
                    IconButton(onClick = { deleting = true }, enabled = !state.downloadBusy) { Icon(Icons.Rounded.DeleteOutline, "Delete downloaded chapter ${entry.chapter.number}") }
                    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete downloaded chapter?") },
                        text = { Text("The downloaded chapter ZIP will be deleted.") },
                        confirmButton = { TextButton(onClick = { deleting = false; model.removeOfflineChapter(entry) }) { Text("Delete") } },
                        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
                }
            }
        }
        }
    }
}
