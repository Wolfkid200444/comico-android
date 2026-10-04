@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package moe.comico.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ChapterGroup(val id: String, val name: String, val count: Int)
fun Chapter.groupKey() = groupId.ifBlank { group }
fun chapterGroups(chapters: List<Chapter>) = chapters.filter {
    it.groupId.isNotBlank() || (it.group.isNotBlank() && !it.group.equals("null", true) && !it.group.equals("Unknown group", true))
}.groupBy { it.groupKey() }.map { (id, entries) ->
    ChapterGroup(id, entries.first().group.takeUnless { it.isBlank() || it == "null" } ?: "Unknown group", entries.size)
}.sortedBy { it.name.lowercase() }
fun filterChapterGroup(chapters: List<Chapter>, group: String?) = if(group == null) chapters else chapters.filter { it.groupKey() == group }
fun AppState.chapterSource() = effectiveReaderPreferences().source?.takeIf { id -> mangaSources.any { it.id == id } }

@Composable
fun ChapterFilterControls(state: AppState, model: ReaderViewModel) {
    var sourceMenu by remember(state.selected?.id, state.chapterSource(), state.language) { mutableStateOf(false) }
    var groupMenu by remember(state.selected?.id, state.chapterSource(), state.language) { mutableStateOf(false) }
    val source = state.mangaSources.firstOrNull { it.id == state.chapterSource() }
    val groups = chapterGroups(state.chapterRoster.orEmpty())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { sourceMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Source, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(source?.name ?: "Auto source", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Icon(Icons.Rounded.ExpandMore, null)
            }
            DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                DropdownMenuItem(text = { Text("Auto source") }, onClick = { sourceMenu = false; model.chapterSource(null) })
                state.mangaSources.forEach { item ->
                    DropdownMenuItem(text = { Text(item.name + (item.chapterCount?.let { " · $it chapters" } ?: "")) },
                        onClick = { sourceMenu = false; model.chapterSource(item.id) })
                }
            }
        }
        if (state.chapterRoster == null || groups.isNotEmpty()) OutlinedButton(onClick = { model.loadChapterGroups(); groupMenu = true }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Groups, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(state.chapterGroup?.let { selected -> groups.firstOrNull { it.id == selected }?.name }
                ?: if (state.chapterRoster == null) "Groups" else "All groups", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Icon(Icons.Rounded.ExpandMore, null)
        }
    }
    if(groupMenu) AlertDialog(onDismissRequest = { groupMenu = false }, title = { Text("Scanlation group") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                if (groups.isNotEmpty()) item { TextButton(onClick = { model.chapterGroup(null); groupMenu = false }) { Text("All groups") } }
                if (state.chapterRoster != null && groups.isEmpty()) item { Text("This source has no scanlation groups for this language.") }
                if(state.chapterGroupsLoading) item {
                    CircularProgressIndicator()
                    Text("Loading groups for this source and language…", Modifier.padding(top = 12.dp))
                }
                state.chapterGroupsError?.let { error -> item {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = model::loadChapterGroups) { Text("Try again") }
                } }
                items(groups, key = { it.id }) { group ->
                    TextButton(onClick = { model.chapterGroup(group.id); groupMenu = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("${group.name} · ${group.count}", Modifier.weight(1f))
                        if(group.id == state.chapterGroup) Icon(Icons.Rounded.Check, null)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { groupMenu = false }) { Text("Close") } })
}
