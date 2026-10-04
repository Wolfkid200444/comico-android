@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package moe.comico.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun LibraryFilterSheet(state: AppState, model: ReaderViewModel, close: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val options = state.libraryOptions
    val formats = state.library.map { it.format }.filter { it.isNotBlank() }.distinct().sorted()
    val statuses = state.library.map { it.status }.filter { it.isNotBlank() }.distinct().sorted()
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            TabRow(selectedTabIndex = tab, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                listOf("Filter", "Sort", "Display").forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                }
            }
            LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    0 -> {
                        item { LibraryCheck("Downloaded manga", options.downloadedOnly) { model.libraryOptions(options.copy(downloadedOnly = it)) } }
                        item { LibraryCheck("Bookmarked", true, enabled = false) {} }
                        item { Text("Your library contains bookmarked titles.", Modifier.padding(horizontal = 20.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        item {
                            Text("Reading progress", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleSmall)
                            LibraryProgress.entries.forEach { progress ->
                                LibraryRadio(progress.label, options.progress == progress) { model.libraryOptions(options.copy(progress = progress)) }
                            }
                        }
                        item { LibraryCheck("Completed series", options.status.equals("completed", true)) {
                            model.libraryOptions(options.copy(status = if (it) "completed" else null))
                        } }
                        item { LibraryFilterChoices("Format", formats, options.format) { model.libraryOptions(options.copy(format = it)) } }
                        item { LibraryFilterChoices("Publication status", statuses, options.status) { model.libraryOptions(options.copy(status = it)) } }
                        item { TextButton(onClick = { model.libraryOptions(options.copy(format = null, status = null, progress = LibraryProgress.ALL, downloadedOnly = false)) },
                            modifier = Modifier.padding(horizontal = 12.dp)) { Text("Reset filters") } }
                    }
                    1 -> {
                        items(LibrarySort.entries) { sort ->
                            LibraryRadio(sort.label, options.sort == sort,
                                descending = options.descending.takeIf { options.sort == sort }) {
                                model.libraryOptions(options.copy(sort = sort, descending = if (options.sort == sort) !options.descending
                                    else sort in listOf(LibrarySort.READ, LibrarySort.CHAPTER)))
                            }
                        }
                        item { LibraryRadio("Total chapters", false, enabled = false) {} }
                        item { Text("Chapter totals aren't available for every library entry.", Modifier.padding(horizontal = 20.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    else -> {
                        item { LibraryHeading("Display mode") }
                        item {
                            FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(LibraryView.COMPACT, LibraryView.GRID, LibraryView.COVER, LibraryView.LIST).forEach { view ->
                                    FilterChip(options.view == view, onClick = { model.libraryOptions(options.copy(view = view)) }, label = { Text(view.label) })
                                }
                            }
                        }
                        item {
                            Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                                Text("Items per row", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                Text(if (options.columns == 0) "Auto" else options.columns.toString())
                            }
                            Slider(options.columns.toFloat(), onValueChange = { model.libraryOptions(options.copy(columns = it.toInt())) },
                                valueRange = 0f..6f, steps = 5, enabled = options.view != LibraryView.LIST,
                                modifier = Modifier.padding(horizontal = 20.dp).semantics { contentDescription = "Items per row, zero means automatic" })
                        }
                        item { LibraryHeading("Overlay") }
                        item { LibraryCheck("Downloaded chapters", options.showDownloads) { model.libraryOptions(options.copy(showDownloads = it)) } }
                        item { LibraryCheck("Unread chapters", false, enabled = false) {} }
                        item { LibraryCheck("Local source", false, enabled = false) {} }
                        item { Text("Unread chapter counts and local source overlays need data the app doesn't store yet.",
                            Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        item { LibraryCheck("Last read chapter", options.showProgress) { model.libraryOptions(options.copy(showProgress = it)) } }
                        item { LibraryCheck("Language", options.showLanguage) { model.libraryOptions(options.copy(showLanguage = it)) } }
                        item { LibraryCheck("Continue reading button", options.showContinue) { model.libraryOptions(options.copy(showContinue = it)) } }
                        item { LibraryHeading("Tabs") }
                        item { LibraryCheck("Show collection tabs", options.showTabs) { model.libraryOptions(options.copy(showTabs = it)) } }
                        item { LibraryCheck("Show number of items", options.showCounts) { model.libraryOptions(options.copy(showCounts = it)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeading(text: String) {
    Text(text, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun LibraryRadio(label: String, checked: Boolean, enabled: Boolean = true, descending: Boolean? = null, change: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 48.dp)
        .selectable(checked, enabled = enabled, role = Role.RadioButton, onClick = change), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(checked, onClick = null, enabled = enabled)
        Text(label, Modifier.padding(start = 12.dp).weight(1f), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
        descending?.let {
            Icon(if (it) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                if (it) "Descending order; tap to reverse" else "Ascending order; tap to reverse",
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun LibraryCheck(label: String, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 48.dp)
        .selectable(checked, enabled = enabled, role = Role.Checkbox, onClick = { change(!checked) }), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null, enabled = enabled)
        Text(label, Modifier.padding(start = 12.dp), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
    }
}

@Composable
private fun LibraryFilterChoices(label: String, values: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected == null, onClick = { onSelect(null) }, label = { Text("All") }) }
            items(values) { value ->
                FilterChip(selected.equals(value, true), onClick = { onSelect(value) },
                    label = { Text(value.replace('_', ' ').replaceFirstChar { it.uppercase() }) })
            }
        }
    }
}

@Composable
fun CollectionEditor(collection: LibraryCollection?, model: ReaderViewModel, close: () -> Unit) {
    var name by remember(collection?.id) { mutableStateOf(collection?.name.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    if (deleting && collection != null) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Delete \"${collection.name}\"?") },
        text = { Text("This removes the collection. Its titles stay in your library.") },
        confirmButton = { TextButton(onClick = { model.deleteCollection(collection.id); close() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } }
    ) else AlertDialog(
        onDismissRequest = close,
        title = { Text(if (collection == null) "New collection" else "Edit collection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it; error = null }, label = { Text("Name") }, singleLine = true,
                    isError = error != null, supportingText = { Text(error ?: "${name.length}/60") })
                if (collection == null) Text("Collections are saved on this device.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else TextButton(onClick = { deleting = true }) { Text("Delete collection", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = {
            error = model.saveCollection(name, collection?.id)
            if (error == null) close()
        }) { Text(if (collection == null) "Create" else "Save") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}
