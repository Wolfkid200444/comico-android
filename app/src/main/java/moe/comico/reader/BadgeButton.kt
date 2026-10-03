package moe.comico.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BadgeButton(badge: BadgeArtwork) {
    var open by remember(badge) { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp)) {
        BadgeImage(badge)
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        icon = { BadgeImage(badge) },
        title = { Text(badge.name) },
        text = { Text(when (badge.name) {
            "Supporter" -> "Supports comico.moe."
            "Welcome to the game" -> "Linked their account with Discord."
            "Early Bud" -> "Registered before August 30, 2026."
            "First Page" -> "Finished their first chapter."
            "Completionist" -> "Completed a story."
            "Polyglot" -> "Reads chapters in multiple languages."
            else -> "Earned this badge on comico.moe."
        }) },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } }
    )
}
