package moe.comico.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SearchFilterDialog(state: AppState, model: ReaderViewModel, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(state.searchFilters) }
    var tagQuery by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { model.loadComickTags() }
    AlertDialog(onDismissRequest = onDismiss,title = { Text("Search filters") },text = {
        Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingChoice("Sort by",draft.sort,SearchSort.entries.map { it to it.label }) { draft = draft.copy(sort = it) }
            SettingChoice("Content filter",draft.content,ContentFilter.entries.map { it to it.label }) { draft = draft.copy(content = it) }
            SettingChoice("Types",draft.type,listOf<Pair<MangaType?,String>>(null to "All") + MangaType.entries.map { it to it.label }) { draft = draft.copy(type = it) }
            SettingChoice("Demographic",draft.demographic,listOf<Pair<Demographic?,String>>(null to "Any demographic") + Demographic.entries.map { it to it.label }) { draft = draft.copy(demographic = it) }
            SettingChoice("Release status",draft.status,listOf<Pair<ReleaseStatus?,String>>(null to "Any status") + ReleaseStatus.entries.map { it to it.label }) { draft = draft.copy(status = it) }
            Text("Comick tags · ${draft.tags.size}/20",style = MaterialTheme.typography.labelLarge)
            draft.tags.forEach { id ->
                InputChip(selected = true,onClick = { draft = draft.copy(tags = draft.tags - id) },label = { Text(state.comickTags.find { it.id == id }?.name ?: id) },trailingIcon = { Text("×") })
            }
            OutlinedTextField(value = tagQuery,onValueChange = { tagQuery = it },label = { Text("Find a tag") },singleLine = true,modifier = Modifier.fillMaxWidth())
            when {
                state.tagsLoading -> CircularProgressIndicator(Modifier.size(24.dp))
                state.tagsError != null -> { Text(state.tagsError);TextButton(onClick = model::loadComickTags) { Text("Retry tags") } }
                else -> {
                    val matches = remember(state.comickTags,tagQuery) { state.comickTags.filter { it.name.contains(tagQuery,ignoreCase = true) }.take(20) }
                    if(matches.isEmpty()) Text("No matching tags.",style = MaterialTheme.typography.bodySmall)
                    matches.forEach { tag ->
                        val selected = tag.id in draft.tags
                        FilterChip(selected = selected,onClick = { draft = draft.copy(tags = if(selected) draft.tags - tag.id else if(draft.tags.size < 20) draft.tags + tag.id else draft.tags) },label = { Text(tag.name) },leadingIcon = { if(selected) Icon(Icons.Rounded.Check,null,Modifier.size(18.dp)) },enabled = selected || draft.tags.size < 20,modifier = Modifier.fillMaxWidth())
                    }
                    if(state.comickTags.size > 20) Text("Type to search ${state.comickTags.size} available tags.",style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    },confirmButton = { TextButton(onClick = { model.applySearchFilters(draft);onDismiss() }) { Text("Apply filters") } },dismissButton = { TextButton(onClick = { draft = SearchFilters();tagQuery = "" }) { Text("Reset") } })
}
