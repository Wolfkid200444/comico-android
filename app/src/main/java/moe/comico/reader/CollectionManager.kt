@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package moe.comico.reader

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CollectionManager(state: AppState, model: ReaderViewModel, close: () -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf(false) }
    val current = state.libraryCollections.firstOrNull { it.id == selected }
    if (editor && current != null) CollectionEditor(current, model) { editor = false }
    else AlertDialog(onDismissRequest = close, title = { Text("Edit Collections") }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            if (state.libraryCollections.isEmpty()) item { Text("Use + in the library to create a collection.") }
            itemsIndexed(state.libraryCollections, key = { _, collection -> collection.id }) { index, collection ->
                Surface(color = if (selected == collection.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small) {
                    Row(Modifier.fillMaxWidth().combinedClickable(
                        onClick = { selected = collection.id },
                        onLongClick = { selected = collection.id; editor = true },
                        onLongClickLabel = "Edit collection").padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(collection.name, Modifier.weight(1f))
                        IconButton(onClick = { model.moveCollection(collection.id, -1) }, enabled = index > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, "Move ${collection.name} earlier")
                        }
                        IconButton(onClick = { model.moveCollection(collection.id, 1) }, enabled = index < state.libraryCollections.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Move ${collection.name} later")
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { editor = true }, enabled = current != null) { Text("Edit") } },
        dismissButton = { TextButton(onClick = close) { Text("Done") } })
}
