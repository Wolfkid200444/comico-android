package moe.comico.reader

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

@Composable
fun UpdatePrompt(controller: AppUpdateController) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current
    val release = state.release
    fun install() {
        state.downloaded?.let { file ->
            runCatching { launchUpdateInstaller(context, file) }.onFailure {
                controller.installError(it.message ?: "Could not open Android's installer.")
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(context.packageManager.canRequestPackageInstalls()) install()
        else controller.installError("Installation wasn't allowed. You can try again or update later.")
    }
    fun requestInstall() {
        if(context.packageManager.canRequestPackageInstalls()) install()
        else runCatching {
            permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
        }.onFailure { controller.installError("Could not open installation settings on this device.") }
    }
    LaunchedEffect(state.downloaded) {
        if(state.downloaded != null && state.prompt) requestInstall()
    }
    if(state.prompt && release != null) AlertDialog(onDismissRequest = controller::dismiss,
        title = { Text("Update to ${release.version}?") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Installed version: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                Text("What's new", style = MaterialTheme.typography.titleSmall)
                CommentBody(releaseNotes(release.notes))
                if(state.downloading) {
                    val progress = state.progress
                    if(progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if(progress != null) "Downloading… ${(progress * 100).toInt()}%" else "Downloading update…")
                } else Text("Android will ask you to confirm installation.", style = MaterialTheme.typography.bodySmall)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = !state.downloading, onClick = {
            if(state.downloaded != null) requestInstall() else controller.download()
        }) { Text(if(state.downloaded != null) "Install update" else "Update") } },
        dismissButton = { TextButton(onClick = controller::dismiss) { Text(if(state.downloading) "Cancel" else "Later") } })
}
@Composable
fun UpdateSettings(controller: AppUpdateController) {
    val state by controller.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppearanceToggle("Check for updates automatically", state.automatic, onChange = controller::automatic)
        Text("Checks GitHub for a new stable release when you open the app, at most once a day.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { controller.check(manual = true) }, enabled = !state.checking && !state.downloading) {
            Text(if(state.checking) "Checking…" else "Check for updates")
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.release?.let { release ->
            TextButton(onClick = controller::showPrompt) { Text("Update to ${release.version}") }
            TextButton(onClick = { runCatching { uriHandler.openUri(release.pageUrl) } }) { Text("View release on GitHub") }
        }
    }
}
