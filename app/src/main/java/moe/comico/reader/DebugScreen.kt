package moe.comico.reader

import android.app.job.JobScheduler
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Composable
fun DebugScreen(state: AppState, model: ReaderViewModel, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var revision by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("Choose a test below.") }
    val diagnostics = remember(state, revision) {
        buildString {
            appendLine("Comico ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build: ${if (BuildConfig.DEBUG) "debug" else "release"}")
            appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Internet validated: ${hasInternet(context)}")
            appendLine("App offline: ${state.offline}")
            appendLine("Signed in: ${state.account.user != null}")
            appendLine("Sync: ${if (state.syncLoading) "running" else if (state.syncError != null) "failed" else "idle"}")
            appendLine("Library: ${state.library.size} titles")
            appendLine("Saved chapters: ${state.offlineChapters.size}")
            appendLine("Downloads: ${state.downloadQueue.size}; ${state.downloadQueue.count { it.active }} active")
            appendLine("Chapter alerts enabled: ${ChapterAlerts.enabled(context)}")
            appendLine("Notifications allowed: ${ChapterAlerts.allowed(context)}")
            val jobs = context.getSystemService(JobScheduler::class.java).allPendingJobs
            appendLine("Scheduled background jobs: ${jobs.size}")
            jobs.forEach { appendLine("Job ${it.id}: periodic=${it.isPeriodic}, persisted=${it.isPersisted}") }
        }.trim()
    }
    fun test(action: suspend () -> String) {
        if (running) return
        running = true
        result = "Running…"
        scope.launch {
            try { result = withTimeout(120_000L) { action() } }
            catch (_: kotlinx.coroutines.TimeoutCancellationException) { result = "Timed out. Check your connection and try again." }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { result = "Failed: ${error.message ?: error.javaClass.simpleName}" }
            finally { running = false; revision++ }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Debug tools", style = MaterialTheme.typography.headlineSmall)
            Text("Local diagnostics and read-only server checks. No votes, replies, deletes or automatic downloads.",
                style = MaterialTheme.typography.bodySmall)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                SelectionContainer { Text(diagnostics, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
            }
            Row {
                TextButton(onClick = { model.refreshConnectivity(); ChapterAlerts.sync(context); revision++ }, enabled = !running) { Text("Refresh") }
                TextButton(onClick = { clipboard.setText(AnnotatedString(diagnostics)) }) { Text("Copy diagnostics") }
            }
        }
        item {
            Text("Chapter notifications", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { test { ChapterAlerts.sample(context) } }, enabled = !running) { Text("Send sample notification") }
            OutlinedButton(onClick = {
                test {
                    val owner = ChapterAlerts.owner(context) ?: return@test "Sign in first."
                    ChapterAlerts.check(context, owner)
                }
            }, enabled = !running && !state.offline && state.account.user != null) { Text("Check for chapters now") }
            Text("The sample leaves real update history untouched. The real check uses normal duplicate protection.",
                style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text("API checks", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = {
                test {
                    val api = ComicoApi(AccountCookieJar(context), networkAvailable = { hasInternet(context) })
                    val user = api.account() ?: return@test "Server reachable; account session is signed out."
                    if (user.id != ChapterAlerts.owner(context)) return@test "Account session differs from the cached account. Sign in again."
                    val updates = api.chapterNotifications()
                    "Account session OK. Comic notification feed OK: ${updates.size} updates."
                }
            }, enabled = !running && !state.offline) { Text("Test account and update feed") }
            OutlinedButton(onClick = { test {
                val data = ComicoApi(networkAvailable = { hasInternet(context) }).get("/api/openapi.json")
                "Public API reachable: ${data.optJSONObject("info")?.optString("title").orEmpty()}."
            } }, enabled = !running && !state.offline) { Text("Test public API") }
        }
        item {
            if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
            SelectionContainer { Text(result, style = MaterialTheme.typography.bodyMedium) }
        }
        item {
            Text("Manual tests", style = MaterialTheme.typography.titleMedium)
            Text("For offline testing, enable airplane mode and refresh diagnostics. Open a saved chapter in Library. Download stop/retry controls are in Downloads.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onSelect("Downloads") }) { Text("Downloads") }
            TextButton(onClick = { onSelect("Notifications") }) { Text("Notification permission settings") }
            TextButton(onClick = { onSelect("Help") }) { Text("Close debug menu") }
        }
    }
}
