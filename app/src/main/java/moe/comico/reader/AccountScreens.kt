@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package moe.comico.reader

import android.content.Intent
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun displayTime(value: String) = formatAppDate(value, LocalAppearance.current)

@Composable
fun SyncStatus(state: AppState, model: ReaderViewModel) {
    if(state.account.user != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if(state.syncLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(state.syncError ?: state.lastSync?.let { "Synced ${displayTime(it)}" } ?: "Account data is cached on this device. Changes sync when connected.",style = MaterialTheme.typography.bodySmall,color = if(state.syncError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    } else Text("History and saved titles stay on this device while signed out.",style = MaterialTheme.typography.bodySmall)
}

@Composable
fun HistoryScreen(state: AppState, model: ReaderViewModel) {
    SyncRefreshBox(state, model) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.syncError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        if(state.history.isEmpty()) item { Text("Chapters you read will appear here.") }
        items(state.history,key = { it.chapter.id }) { entry ->
            Card(onClick = { model.openHistory(entry) },modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp),horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Cover(entry.manga,Modifier.width(56.dp).height(80.dp))
                    Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.manga.title,style = MaterialTheme.typography.titleMedium,maxLines = 2)
                        Text("Chapter ${entry.chapter.number} · Page ${entry.page + 1}" + if(entry.pageCount > 0) " / ${entry.pageCount}" else "",style = MaterialTheme.typography.bodySmall)
                        Text(displayTime(entry.readAt),style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    }
}

@Composable
fun LeaderboardScreen(state: AppState, model: ReaderViewModel) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Accounts ranked by experience on comico.moe.");TextButton(onClick = model::loadLeaderboard,enabled = !state.leaderboardLoading) { Text("Refresh") } }
        if(state.leaderboardLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.leaderboardError?.let { error -> item { MessageCard(Icons.Rounded.CloudOff,"Couldn't load leaderboard",error,model::loadLeaderboard) } }
        if(!state.leaderboardLoading && state.leaderboardError == null && state.leaderboard.isEmpty()) item { Text("No rankings are available yet.") }
        items(state.leaderboard,key = { it.id }) { entry ->
            val rank = state.leaderboard.indexOf(entry) + 1
            Card(onClick = { if(entry.username.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("$BASE_URL/u/${Uri.encode(entry.username)}"))) },modifier = Modifier.fillMaxWidth()) {
                ListItem(leadingContent = { Text("#$rank");if(entry.image.isNotBlank()) AsyncImage(entry.image,null,Modifier.size(40.dp)) },headlineContent = { Text(entry.name.ifBlank { entry.username }) },supportingContent = { Text("Level ${entry.level} · ${java.text.NumberFormat.getIntegerInstance().format(entry.exp)} XP") })
            }
        }
    }
}

@Composable
fun SettingsScreen(state: AppState, model: ReaderViewModel, selected: String, onSelect: (String) -> Unit) {
    val signedIn = state.account.user != null
    val sections = listOf("Account", "Profile", "Comments", "Reading", "Appearance", "Data", "Leaderboard", "Help", "About")
    LaunchedEffect(signedIn) { if(!signedIn && selected in listOf("Profile", "Comments", "Data")) onSelect("Menu") }
    if (selected == "Comments" && signedIn) {
        CommentsScreen(state, model)
        return
    }
    SyncRefreshBox(state, model, enabled = selected == "Data") {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if(selected == "Menu") items(sections) { section ->
            val enabled = signedIn || section !in listOf("Profile", "Comments", "Data")
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(settingsTooltip(section)) } },
                state = rememberTooltipState()
            ) {
            Surface(onClick = { if(section == "Leaderboard") model.tab("Leaderboard") else onSelect(section) },
                enabled = enabled, shape = MaterialTheme.shapes.large) {
                ListItem(headlineContent = { Text(if(section == "Profile") "Identity and social links" else section) },
                    leadingContent = { Icon(when(section) {
                        "Account" -> Icons.Rounded.ManageAccounts
                        "Profile" -> Icons.Rounded.Person
                        "Comments" -> Icons.Rounded.ChatBubbleOutline
                        "Reading" -> Icons.Rounded.MenuBook
                        "Appearance" -> Icons.Rounded.Palette
                        "Data" -> Icons.Rounded.Storage
                        "Leaderboard" -> Icons.Rounded.Leaderboard
                        "Help" -> Icons.Rounded.HelpOutline
                        else -> Icons.Rounded.Info
                    }, null) },
                    supportingContent = { if(!enabled) Text("Sign in to access") },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, null) })
            }
            }
        }
        when(selected) {
            "Account" -> item { AccountSettings(state,model) }
            "Profile" -> item { IdentityPanel(state,model) }
            "Reading" -> item { Text("Global reader defaults",style = MaterialTheme.typography.titleLarge);Spacer(Modifier.height(16.dp));GlobalReaderOptions(state,model);Spacer(Modifier.height(16.dp));Text("Individual manga can override each global setting.") }
            "Appearance" -> item { AppearancePanel(state,model) }
            "Data" -> item { DataPanel(state,model) }
            "Help" -> item {
                Text("Save titles to your library, then use Start reading or Resume on manga details. Reader settings can apply globally or to one manga.")
                Text("Account library and history sync when signed in. Use Data to retry sync or import guest data.")
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                TextButton(onClick = { uriHandler.openUri("https://github.com/Wolfkid200444/comico-android/issues") }) { Text("Report an issue") }
            }
            "About" -> item {
                Text("An independent Kotlin client for comico.moe, built with Jetpack Compose and Material Design 3.")
                Text("Version ${BuildConfig.VERSION_NAME}")
                UpdateSettings(model.updates)
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                TextButton(onClick = { uriHandler.openUri("https://github.com/Wolfkid200444/comico-android") }) { Text("Source code") }
            }
        }
    }
    }
}

@Composable
private fun DataPanel(state: AppState, model: ReaderViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var clear by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri != null) {
            val data = model.exportData()
            scope.launch {
                message = runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) } ?: error("Couldn't open the selected file.") };"Data exported." }.getOrElse { "Export failed: ${it.message}" }
            }
        }
    }
    if(clear) AlertDialog(onDismissRequest = { clear = false },title = { Text("Clear reading history?") },text = { Text("This clears history on this device and your comico.moe account. Saved titles remain in your library.") },confirmButton = { TextButton(onClick = { clear = false;model.clearHistory() }) { Text("Clear history") } },dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } })
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Account data",style = MaterialTheme.typography.titleLarge)
        SyncStatus(state,model)
        Text("Your account library and reading history sync with comico.moe. Guest data stays separate unless you import it. Reader settings are stored on this device.")
        OutlinedButton(onClick = model::importGuestData,enabled = !state.syncLoading && !state.account.loading) { Text("Add guest library and history to this account") }
        OutlinedButton(onClick = { export.launch("comico-data.json") }) { Text("Export library, history and reader defaults") }
        OutlinedButton(onClick = { clear = true },enabled = !state.syncLoading && !state.account.loading) { Text("Clear reading history") }
        message?.let { Text(it) }
    }
}
