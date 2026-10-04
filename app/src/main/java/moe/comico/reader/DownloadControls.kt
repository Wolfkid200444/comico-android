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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DownloadFolderButton(state: AppState, model: ReaderViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(model::chooseDownloadFolder) }
    TextButton(onClick = { picker.launch(state.downloadFolder?.let(Uri::parse)) }, enabled = !state.downloadBusy) {
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
    if (state.downloadBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.downloadMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
fun DownloadsScreen(state: AppState, model: ReaderViewModel) {
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::openOfflineZip) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, enabled = !state.downloadBusy) {
                Icon(Icons.Rounded.FolderZip, null); Text("Open chapter ZIP", Modifier.padding(start = 8.dp))
            }
            DownloadStatus(state)
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
