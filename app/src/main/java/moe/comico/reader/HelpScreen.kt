package moe.comico.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

@Composable
fun HelpContent(offline: Boolean, onSelect: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Make yourself at home", style = MaterialTheme.typography.headlineSmall)
            Text("A quick guide to reading with Comico.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HelpCard(Icons.AutoMirrored.Rounded.MenuBook, "Reading",
            "Open a title and choose Start reading or Resume. Tap the middle of the reader to show its controls, or the chapter title to switch chapters.",
            "Reader settings", { onSelect("Reading") })
        HelpCard(Icons.Rounded.CollectionsBookmark, "Your library",
            "Save titles to keep them together. Use the search and filter controls to find, sort or change how they appear. Create collections with the + button and hold a title to select it.")
        HelpCard(Icons.Rounded.Download, "Read offline",
            "Opened chapters are saved once all their images finish loading. You can also download chapters in advance. Saved titles appear in Library when you're offline.",
            "Manage downloads", { onSelect("Downloads") })
        HelpCard(Icons.Rounded.Sync, "Data and sync",
            "Library and reading history sync with your account when you're signed in and connected. Offline, you can read saved chapters; features that need the internet are unavailable.",
            "Data and storage", { onSelect("Data and storage") })
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Something not working?", style = MaterialTheme.typography.titleMedium)
        Text(
            "Include your app version, phone model and the steps that caused the problem.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = { uriHandler.openUri("https://github.com/Wolfkid200444/comico-android/issues") },
            enabled = !offline,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.BugReport, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Report an issue")
        }
        if (offline) Text(
            "Connect to the internet to report an issue.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HelpCard(
    icon: ImageVector,
    title: String,
    description: String,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}
