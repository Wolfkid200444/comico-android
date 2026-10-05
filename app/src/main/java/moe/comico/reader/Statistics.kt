package moe.comico.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun ProfileStatistics(state: AppState) {
    val library = state.library.distinctBy { it.id }
    val statuses = library.groupingBy { it.status.ifBlank { "Unknown" } }.eachCount()
        .entries.sortedByDescending { it.value }
    val metrics = listOf(
        "Titles in library" to library.size,
        "Chapters read" to state.publicProfile?.chaptersRead,
        "Downloaded chapters" to state.offlineChapters.size,
        "Downloaded titles" to state.offlineChapters.map { it.manga.id }.distinct().size,
        "Collections" to state.libraryCollections.size,
        "Tags" to library.flatMap { it.tags }.distinct().size
    )
    // Keep status colors stable even when the library counts change.
    val colors = statuses.map { entry ->
        androidx.compose.ui.graphics.Color(when (entry.key.lowercase(java.util.Locale.ROOT)) {
            "completed" -> 0xFF66BB6A
            "ongoing" -> 0xFF42A5F5
            "hiatus", "on hiatus" -> 0xFFFFCA28
            "cancelled", "canceled" -> 0xFFEF5350
            "licensed" -> 0xFFFFA726
            "publishing finished", "finished" -> 0xFFAB47BC
            else -> 0xFF9E9E9E
        })
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Statistics", style = MaterialTheme.typography.headlineSmall)
            metrics.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { (label, count) ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(count?.toString() ?: "—", style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary)
                            Text(label, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            HorizontalDivider()
            Text("Library status", style = MaterialTheme.typography.titleMedium)
            if (statuses.isEmpty()) {
                Text("Add titles to your library to see their status distribution.",
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                val description = statuses.joinToString { "${it.key}: ${it.value}" }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(180.dp).padding(14.dp).semantics { contentDescription = description }) {
                        var angle = -90f
                        statuses.forEachIndexed { index, entry ->
                            val sweep = entry.value.toFloat() / library.size * 360f
                            drawArc(colors[index % colors.size], angle, sweep, false,
                                style = Stroke(width = 26.dp.toPx()))
                            angle += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(library.size.toString(), style = MaterialTheme.typography.headlineMedium)
                        Text("titles", style = MaterialTheme.typography.labelMedium)
                    }
                }
                statuses.forEachIndexed { index, entry ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(12.dp).background(colors[index % colors.size], CircleShape))
                        Text(entry.key, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(entry.value.toString(), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Text("Library and download counts reflect data available on this device. Chapters read comes from your Comico account.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
