package moe.comico.reader

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun NotificationSettings(state: AppState) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycle = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(ChapterAlerts.enabled(context)) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    var rationale by remember { mutableStateOf(false) }
    val permissionPrefs = remember { context.getSharedPreferences("notifications", 0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRevision++
        ChapterAlerts.sync(context)
    }
    fun requestPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            permissionPrefs.edit().putBoolean("asked", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun openSettings() {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRevision++
                ChapterAlerts.sync(context)
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    if (rationale) AlertDialog(
        onDismissRequest = { rationale = false },
        title = { Text("Allow chapter notifications?") },
        text = { Text("Comico needs notification permission to let you know when your library gets new chapters.") },
        confirmButton = { TextButton(onClick = { rationale = false; requestPermission() }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { rationale = false }) { Text("Cancel") } }
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ListItem(headlineContent = { Text("New chapters") },
            supportingContent = { Text("Notify me about chapter updates in my account library.") },
            trailingContent = { Switch(enabled, onCheckedChange = {
                enabled = it
                ChapterAlerts.setEnabled(context, it)
            }) })
        Text("Checks periodically while the app is closed and resumes after a reboot. Internet and a signed-in comico.moe account are required. Android may delay checks to save battery.",
            style = MaterialTheme.typography.bodyMedium)
        Text("Uses your account’s comic notification feed. Existing updates are skipped when alerts are first enabled.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.account.user == null) Text("Sign in to receive library chapter alerts.")
        // Re-evaluate the platform permission after returning from system settings.
        key(permissionRevision) {
            if (!ChapterAlerts.allowed(context)) {
                Text("Notifications are blocked by Android.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = {
                    when {
                        Build.VERSION.SDK_INT < 33 -> openSettings()
                        activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true -> rationale = true
                        permissionPrefs.getBoolean("asked", false) -> openSettings()
                        else -> requestPermission()
                    }
                }) { Text("Allow notifications") }
            }
        }
    }
}
