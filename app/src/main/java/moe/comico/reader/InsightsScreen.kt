package moe.comico.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StatisticsScreen(state: AppState) {
    var range by rememberSaveable { mutableStateOf(InsightRange.MONTH) }
    var info by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val insights = remember(state.readingDays, range, today) { readingInsights(state.readingDays, range, today) }
    if (info) AlertDialog(onDismissRequest = { info = false },
        title = { Text("About your statistics") },
        text = { Text("Reading activity is recorded on this device, including offline chapters. Time counts while a loaded chapter is in the foreground. Dialogs and background time are excluded. Pages and chapters count distinct items viewed in the chosen period. Streaks use all recorded days.\n\nLibrary and downloads show current local totals; account chapters read comes from Comico. Activity from before tracking began cannot be reconstructed.") },
        confirmButton = { TextButton(onClick = { info = false }) { Text("Got it") } })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your reading activity", style = MaterialTheme.typography.titleLarge)
                    Text("Time, progress and reading habits.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { info = true }) { Icon(Icons.Rounded.Info, "How statistics are counted") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InsightRange.entries.forEach { choice ->
                    FilterChip(range == choice, onClick = { range = choice }, label = { Text(choice.label) })
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Reading time", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(readingDuration(insights.millis), style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary)
                    ReadingTimeChart(insights)
                    val formatter = DateTimeFormatter.ofPattern("MMM d")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(insights.daily.first().first.format(formatter), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(today.format(formatter), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatisticValue("Sessions", insights.sessions.toString(), Modifier.weight(1f),
                            if (insights.sessions > 0) "Avg ${readingDuration(insights.millis / insights.sessions)}" else null)
                        StatisticValue("Pages viewed", insights.pages.toString(), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatisticValue("Chapters viewed", insights.chapters.toString(), Modifier.weight(1f))
                        StatisticValue("Reading days", insights.readingDays.toString(), Modifier.weight(1f))
                    }
                    if (insights.millis == 0L) Text("Open a chapter to start tracking your reading.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${insights.currentStreak}-day streak", style = MaterialTheme.typography.titleLarge)
                        Text("Best: ${insights.bestStreak} days", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { ProfileStatistics(state) }
    }
}

@Composable
fun StatisticValue(title: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ReadingTimeChart(insights: ReadingInsights) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val actualPeak = insights.daily.maxOf { it.second }
    val peak = actualPeak.coerceAtLeast(60_000L)
    Text("Daily total · peak ${readingDuration(actualPeak)}", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics {
        contentDescription = "Daily reading time chart; ${insights.readingDays} active days, total ${readingDuration(insights.millis)}"
    }) {
        repeat(4) { line ->
            val y = size.height * line / 3
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        val points = insights.daily.map { it.second }
        val path = Path()
        points.forEachIndexed { index, millis ->
            val x = if (points.size == 1) size.width / 2 else size.width * index / (points.size - 1)
            val y = size.height * (1f - millis.toFloat() / peak)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, primary, style = Stroke(2.dp.toPx()))
        if (points.size == 1) drawCircle(primary, 3.dp.toPx(),
            Offset(size.width / 2, size.height * (1f - points.single().toFloat() / peak)))
    }
}
