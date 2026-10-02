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
import androidx.compose.runtime.saveable.rememberSaveable
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

private fun displayTime(value: String) = runCatching { DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value)) }.getOrDefault(value)

@Composable
fun SyncStatus(state: AppState, model: ReaderViewModel) {
    if(state.account.user != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if(state.syncLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(state.syncError ?: state.lastSync?.let { "Synced ${displayTime(it)}" } ?: "Account data is cached on this device. Changes sync when connected.",style = MaterialTheme.typography.bodySmall,color = if(state.syncError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = model::syncAccount,enabled = !state.syncLoading && !state.account.loading) { Text(if(state.syncError != null) "Retry sync" else "Sync now") }
    } else Text("History and saved titles stay on this device while signed out.",style = MaterialTheme.typography.bodySmall)
}

@Composable
fun HistoryScreen(state: AppState, model: ReaderViewModel) {
    LazyColumn(contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Reading history",style = MaterialTheme.typography.headlineLarge) }
        item { SyncStatus(state,model) }
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

@Composable
fun LeaderboardScreen(state: AppState, model: ReaderViewModel) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Leaderboard",style = MaterialTheme.typography.headlineLarge);Text("Accounts ranked by experience on comico.moe.");TextButton(onClick = model::loadLeaderboard,enabled = !state.leaderboardLoading) { Text("Refresh") } }
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
fun SettingsScreen(state: AppState, model: ReaderViewModel) {
    var selected by rememberSaveable { mutableStateOf("Reading") }
    val signedIn = state.account.user != null
    val tabs = listOf("Profile","Comments","Reading","Preferences","Data")
    LaunchedEffect(signedIn) { if(!signedIn && selected !in listOf("Reading","Preferences")) selected = "Reading" }
    val index = tabs.indexOf(selected)
    LazyColumn(contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("Settings",style = MaterialTheme.typography.headlineLarge) }
        item { AccountSettings(state,model) }
        item { ScrollableTabRow(selectedTabIndex = index,edgePadding = 0.dp) {
            tabs.forEach { tab -> Tab(selected = tab == selected,onClick = { selected = tab },enabled = signedIn || tab in listOf("Reading","Preferences"),text = { Text(tab) }) }
        } }
        when(selected) {
            "Profile" -> item { ProfilePanel(state,model) }
            "Comments" -> {
                item { Text("Your comments",style = MaterialTheme.typography.titleLarge);TextButton(onClick = model::loadAccountData,enabled = !state.accountDataLoading) { Text("Refresh") } }
                if(state.accountDataLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.accountDataError?.let { error -> item { Text(error,color = MaterialTheme.colorScheme.error) } }
                if(!state.accountDataLoading && state.accountDataError == null && state.comments.isEmpty()) item { Text("You haven't posted any comments yet.") }
                items(state.comments,key = { it.id }) { comment -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) { if(comment.mangaTitle.isNotBlank()) Text(comment.mangaTitle,style = MaterialTheme.typography.titleSmall);Text(comment.body);Text(displayTime(comment.createdAt),style = MaterialTheme.typography.labelSmall) } } }
            }
            "Reading" -> item { Text("Global reader defaults",style = MaterialTheme.typography.titleLarge);Spacer(Modifier.height(16.dp));GlobalReaderOptions(state,model);Spacer(Modifier.height(16.dp));Text("Individual manga can override each global setting.") }
            "Preferences" -> {
                item { FilledTonalButton(onClick = { model.tab("Leaderboard") }) { Icon(Icons.Rounded.Leaderboard,null);Spacer(Modifier.width(8.dp));Text("Leaderboard") } }
                item { Text("Appearance",style = MaterialTheme.typography.titleLarge);Column { listOf("Website","System","Light","Dark").forEach { theme -> FilterChip(selected = state.theme == theme,onClick = { model.theme(theme) },label = { Text(theme) }) } } }
                item { ListItem(headlineContent = { Text("Wallpaper colors") },supportingContent = { Text("Use your Android palette on Android 12 and later") },trailingContent = { Switch(state.dynamicColor,model::dynamic) }) }
            }
            "Data" -> item { DataPanel(state,model) }
        }
        item { Text("comico.moe for Android · ${BuildConfig.VERSION_NAME}",style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ProfilePanel(state: AppState, model: ReaderViewModel) {
    val user = state.account.user ?: return
    var name by remember(user.id,user.name) { mutableStateOf(user.name) }
    var username by remember(user.id,user.username) { mutableStateOf(user.username) }
    var bio by remember(user.id,state.profileBio) { mutableStateOf(state.profileBio) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Profile",style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(name,{ name = it },label = { Text("Display name") },modifier = Modifier.fillMaxWidth(),enabled = !state.accountDataLoading)
        OutlinedTextField(username,{ username = it },label = { Text("Username") },modifier = Modifier.fillMaxWidth(),enabled = !state.accountDataLoading,singleLine = true)
        OutlinedTextField(bio,{ bio = it },label = { Text("Bio") },modifier = Modifier.fillMaxWidth(),enabled = !state.accountDataLoading)
        Text(user.email)
        if(state.accountDataLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.accountDataError?.let { Text(it,color = MaterialTheme.colorScheme.error) }
        Button(onClick = { model.saveProfile(name,username,bio) },enabled = !state.accountDataLoading && !state.account.loading && name.isNotBlank() && username.isNotBlank()) { Text("Save profile") }
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
