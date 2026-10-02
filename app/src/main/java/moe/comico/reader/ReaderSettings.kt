@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package moe.comico.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReaderSettingsDialog(state: AppState, model: ReaderViewModel, onDismiss: () -> Unit) {
    var global by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Reader settings") }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !global, onClick = { global = false }, label = { Text("This manga") })
                FilterChip(selected = global, onClick = { global = true }, label = { Text("Global defaults") })
            }
            Text(if(global) "Applies to all manga unless that setting has an override." else "Only ${state.selected?.title.orEmpty()}. Each setting can inherit its global default.", style = MaterialTheme.typography.bodySmall)
            if(global) GlobalReaderOptions(state, model) else MangaReaderOptions(state, model)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }, dismissButton = {
        if(!global) TextButton(onClick = { model.setMangaReader(ReaderOverride()) }) { Text("Use all global defaults") }
    })
}

@Composable
fun GlobalReaderOptions(state: AppState, model: ReaderViewModel) {
    val config = state.readerPreferences
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingChoice("Reading mode", config.mode, ReadingMode.entries.map { it to it.label }) { model.setGlobalReader(config.copy(mode = it)) }
        SettingChoice("Page width", config.width, PageWidth.entries.map { it to it.label }) { model.setGlobalReader(config.copy(width = it)) }
        SettingChoice("Proxy method", config.proxy, ProxyMethod.entries.map { it to it.label }) { model.setGlobalReader(config.copy(proxy = it)) }
        SettingChoice("Preferred source", config.source, sourceChoices(state.knownSources,config.source)) { model.setGlobalReader(config.copy(source = it)) }
        Text("Sources appear here as you open manga. Auto selects an available source. A preferred source falls back to Auto when missing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingChoice("Page direction", config.direction, ReadingDirection.entries.map { it to it.label }) { model.setGlobalReader(config.copy(direction = it)) }
        Text("Auto mode uses long strip for webtoons, manhwa, and manhua, and single pages for manga. Auto proxy tries direct images, then Method 1 and Method 2. Manual methods stay on your selection.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MangaReaderOptions(state: AppState, model: ReaderViewModel) {
    val config = state.readerOverrides[state.selected?.id] ?: ReaderOverride()
    val global = state.readerPreferences
    SettingChoice("Reading mode", config.mode, listOf< Pair<ReadingMode?,String> >(null to "Use global · ${global.mode.label}") + ReadingMode.entries.map { it to it.label }) { model.setMangaReader(config.copy(mode = it)) }
    SettingChoice("Page width", config.width, listOf<Pair<PageWidth?,String>>(null to "Use global · ${global.width.label}") + PageWidth.entries.map { it to it.label }) { model.setMangaReader(config.copy(width = it)) }
    SettingChoice("Proxy method", config.proxy, listOf<Pair<ProxyMethod?,String>>(null to "Use global · ${global.proxy.label}") + ProxyMethod.entries.map { it to it.label }) { model.setMangaReader(config.copy(proxy = it)) }
    val available = (state.nativeReader.sources + state.mangaSources).distinctBy { it.id }
    val current = if(config.sourceOverride) config.source ?: "__auto" else "__global"
    val sourceItems = listOf("__global" to "Use global · ${global.source?.let { id -> state.knownSources.find { it.id == id }?.name ?: id } ?: "Auto"}", "__auto" to "Auto") + available.filter { it.readable }.map { it.id to it.name } + if(config.source != null && available.none { it.id == config.source }) listOf(config.source to "${config.source} · unavailable") else emptyList()
    SettingChoice("Source for this manga", current, sourceItems) { source -> model.setMangaReader(config.copy(sourceOverride = source != "__global", source = source.takeUnless { it.startsWith("__") })) }
    SettingChoice("Page direction", config.direction, listOf<Pair<ReadingDirection?,String>>(null to "Use global · ${global.direction.label}") + ReadingDirection.entries.map { it to it.label }) { model.setMangaReader(config.copy(direction = it)) }
    Text("Only sources reported by Comico for this manga or chapter are listed. If a saved preference is unavailable for a chapter, the reader uses Auto and shows the active source.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun sourceChoices(sources: List<ReaderSource>, selected: String?): List<Pair<String?, String>> = listOf<Pair<String?, String>>(null to "Auto") + sources.filter { it.readable }.map { it.id to it.name } + if(selected != null && sources.none { it.id == selected }) listOf(selected to selected) else emptyList()

@Composable
fun <T> SettingChoice(title: String, value: T, options: List<Pair<T,String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Text(options.firstOrNull { it.first == value }?.second ?: value.toString(), Modifier.weight(1f))
                Icon(Icons.Rounded.ExpandMore, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (choice,label) -> DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; onSelect(choice) }) }
            }
        }
    }
}
