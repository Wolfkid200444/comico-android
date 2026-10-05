package moe.comico.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(state: AppState, controller: AppUpdateController) {
    val updates by controller.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val rowColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
    val tooltip = rememberTooltipState()
    var changelog by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    LaunchedEffect(state.offline) { if (!state.offline) controller.loadHistory() }
    val installed = updates.history.firstOrNull { it.version == BuildConfig.VERSION_NAME }
    val versionDetails = installed?.let {
        "${if (it.prerelease) "Pre-release" else "Stable"} · " +
            (it.publishedAt.takeIf(String::isNotBlank)?.let { date -> formatAppDate(date, LocalAppearance.current) } ?: "Release date unavailable")
    } ?: "Release status and date unavailable"
    if (changelog) AlertDialog(onDismissRequest = { changelog = false },
        title = { Text("Changelog") },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (updates.historyLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                updates.historyError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
                if (updates.history.isEmpty() && !updates.historyLoading) item {
                    Text(if (state.offline) "Connect once to load published changelogs." else "No published releases yet.")
                }
                items(updates.history, key = { it.version }) { release ->
                    var expanded by remember(release.version) { mutableStateOf(false) }
                    Surface(onClick = { expanded = !expanded }, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Version ${release.version}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "Hide notes" else "Show notes")
                            }
                            if (release.publishedAt.isNotBlank()) Text(formatAppDate(release.publishedAt, LocalAppearance.current), style = MaterialTheme.typography.labelSmall)
                            if (expanded) CommentBody(releaseNotes(release.notes))
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { changelog = false }) { Text("Close") } })
    if (licenses) AlertDialog(onDismissRequest = { licenses = false },
        title = { Text("Open source licenses") },
        text = {
            LazyColumn {
                items(listOf(
                    "AndroidX and Jetpack Compose" to "https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt",
                    "Kotlin and kotlinx.coroutines" to "https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt",
                    "Coil" to "https://github.com/coil-kt/coil/blob/2.7.0/LICENSE.txt",
                    "OkHttp" to "https://github.com/square/okhttp/blob/parent-4.12.0/LICENSE.txt",
                    "Okio" to "https://github.com/square/okio/blob/master/LICENSE.txt"
                )) { (name, url) ->
                    Surface(onClick = { uriHandler.openUri(url) }, enabled = !state.offline) {
                        ListItem(colors = rowColors, headlineContent = { Text(name) }, supportingContent = { Text("Apache License 2.0") },
                            trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "View license") })
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { licenses = false }) { Text("Close") } })
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(R.drawable.comico_logo), "Comico.moe", Modifier.size(96.dp))
                    Text("Comico.moe", style = MaterialTheme.typography.headlineSmall)
                }
                HorizontalDivider()
            }
            item {
                TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(versionDetails) } }, state = tooltip) {
                    Surface(onClick = { scope.launch { tooltip.show() } }) {
                        ListItem(colors = rowColors, headlineContent = { Text("Version") },
                            supportingContent = { Text(BuildConfig.VERSION_NAME) },
                            leadingContent = { Icon(Icons.Rounded.Info, null) })
                    }
                }
            }
            item {
                Surface(onClick = { controller.check(manual = true) },
                    enabled = !state.offline && !updates.checking && !updates.downloading) {
                    ListItem(colors = rowColors, headlineContent = { Text(if (updates.checking) "Checking…" else "Check for updates") },
                        supportingContent = { Text(if (state.offline) "Internet required" else updates.message ?: "Check GitHub for a new version") },
                        leadingContent = { Icon(Icons.Rounded.SystemUpdate, null) })
                }
            }
            item {
                Surface(onClick = { changelog = true }) {
                    ListItem(colors = rowColors, headlineContent = { Text("Changelog") }, leadingContent = { Icon(Icons.Rounded.History, null) },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) })
                }
            }
            item {
                Surface(onClick = { licenses = true }) {
                    ListItem(colors = rowColors, headlineContent = { Text("Open source licenses") }, leadingContent = { Icon(Icons.Rounded.Description, null) },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) })
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Automatic update checks", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(updates.automatic, controller::automatic, enabled = !state.offline)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
            IconButton(enabled = !state.offline, onClick = { uriHandler.openUri("https://github.com/$UPDATE_REPOSITORY") }) {
                Icon(painterResource(R.drawable.ic_github), "GitHub", Modifier.size(28.dp))
            }
        }
    }
}
