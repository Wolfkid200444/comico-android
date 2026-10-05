package moe.comico.reader

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File

data class StorageUsage(
    val downloads: Long = 0,
    val chapterCache: Long = 0,
    val previews: Long = 0,
    val available: Long = 0,
    val unknownDownloads: Int = 0
)

fun chapterCacheFiles(directory: File): List<File> = directory.listFiles()?.filter {
    it.name.startsWith("offline-") || it.name.startsWith("validate-") ||
        (it.name.startsWith("chapter-") && it.name.endsWith(".zip"))
}.orEmpty()

fun storageBytes(file: File): Long = if (file.isDirectory)
    file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()

fun clearChapterFiles(directory: File) {
    chapterCacheFiles(directory).forEach { check(it.deleteRecursively()) { "Couldn't clear part of the chapter cache." } }
}

@Composable
fun StorageSettingsScreen(state: AppState, model: ReaderViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<StorageUsage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var clear by remember { mutableStateOf<String?>(null) }
    fun size(bytes: Long) = Formatter.formatFileSize(context, bytes)
    LaunchedEffect(state.account.user?.id, state.offlineChapters, state.downloadBusy) {
        runCatching { model.storageUsage() }.onSuccess { usage = it }.onFailure { message = it.message }
    }
    if (clear != null) AlertDialog(onDismissRequest = { clear = null },
        title = { Text("Clear ${clear!!.lowercase()}?") },
        text = { Text("Downloaded chapter ZIPs will be kept. Cached images can be loaded again when needed.") },
        confirmButton = { TextButton(onClick = {
            val previews = clear == "Page preview cache"
            clear = null
            busy = true
            scope.launch {
                runCatching {
                    model.clearStorageCache(previews)
                    usage = model.storageUsage()
                }.onSuccess { message = "Cache cleared." }.onFailure { message = it.message ?: "Couldn't clear the cache." }
                busy = false
            }
        }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { clear = null }) { Text("Cancel") } })
    SyncRefreshBox(state, model, enabled = state.account.user != null) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { DataPanel(state, model) }
        item { HorizontalDivider() }
        item {
            Text("Download location", style = MaterialTheme.typography.titleMedium)
            Text(state.downloadFolder?.let { Uri.decode(it.substringAfterLast('/')) } ?: "App storage (default)",
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            Text("Applies to new downloads. Existing chapters stay in their current location.",
                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DownloadFolderButton(state, model)
            if (state.downloadFolder != null) TextButton(onClick = model::useDefaultDownloadFolder, enabled = !state.downloadBusy && !busy && state.downloadQueue.none { it.active }) {
                Text("Use app storage")
            }
            if (state.downloadFolder == null) Text("Chapters in app storage are removed if you uninstall the app.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { HorizontalDivider() }
        item {
            Text("Storage usage", style = MaterialTheme.typography.titleMedium)
            if (usage == null) CircularProgressIndicator(Modifier.padding(top = 12.dp).size(24.dp))
            usage?.let {
                ListItem(headlineContent = { Text("Downloaded chapters") },
                    supportingContent = { Text("${state.offlineChapters.size} chapters" + if (it.unknownDownloads > 0) " · ${it.unknownDownloads} file sizes unavailable" else "") },
                    trailingContent = { Text(size(it.downloads)) })
                ListItem(headlineContent = { Text("Chapter cache") }, supportingContent = { Text("Temporary files used to open chapter ZIPs") },
                    trailingContent = { Text(size(it.chapterCache)) })
                ListItem(headlineContent = { Text("Page preview cache") }, supportingContent = { Text("Cached reader images, covers and previews") },
                    trailingContent = { Text(size(it.previews)) })
                ListItem(headlineContent = { Text("Available on device") }, trailingContent = { Text(size(it.available)) })
            }
        }
        item { HorizontalDivider() }
        item {
            Text("Cache", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { clear = "Chapter cache" }, enabled = !busy && !state.downloadBusy && state.downloadQueue.none { it.active }) {
                Icon(Icons.Rounded.CleaningServices, null); Text("Clear chapter cache", Modifier.padding(start = 8.dp))
            }
            TextButton(onClick = { clear = "Page preview cache" }, enabled = !busy && !state.downloadBusy && state.downloadQueue.none { it.active }) {
                Icon(Icons.Rounded.HideImage, null); Text("Clear page preview cache", Modifier.padding(start = 8.dp))
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.downloadMessage.takeIf { state.downloadMessageMangaId == null }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        item {
            HorizontalDivider()
            Text("Free accounts: 50 chapters daily, reset at 00:00 UTC. Supporter accounts: unlimited.",
                Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    }
}
