package moe.comico.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AppearanceOptions(
    val palette: String = "Default", val pureBlack: Boolean = false,
    val dateFormat: String = "M/d/yy", val relativeDates: Boolean = false,
    val alwaysShowNavLabels: Boolean = true
)
val LocalAppearance = staticCompositionLocalOf { AppearanceOptions() }
val dateFormats = listOf("M/d/yy", "MM/dd/yy", "dd/MM/yy", "yyyy-MM-dd", "MMM d, yyyy")

fun formatAppDate(value: String, options: AppearanceOptions, now: LocalDate = LocalDate.now(),
                  zone: ZoneId = ZoneId.systemDefault()): String = runCatching {
    val date = Instant.parse(value).atZone(zone).toLocalDate()
    if (options.relativeDates) when (date) {
        now -> return "Today"
        now.minusDays(1) -> return "Yesterday"
    }
    val pattern = options.dateFormat.takeIf { it in dateFormats } ?: "M/d/yy"
    date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}.getOrDefault(value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearancePanel(state: AppState, model: ReaderViewModel) {
    val options = state.appearance
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("System", "Light", "Dark").forEachIndexed { index, theme ->
                SegmentedButton(
                    selected = state.theme == theme || (theme == "Dark" && state.theme == "Website"),
                    onClick = { model.theme(theme) },
                    shape = SegmentedButtonDefaults.itemShape(index, 3)
                ) { Text(theme) }
            }
        }
        val dark = when(state.theme) { "Light" -> false; "System" -> isSystemInDarkTheme(); else -> true }
        val context = LocalContext.current
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            themePalettes.forEach { palette ->
                val selected = options.palette == palette
                val preview = if(palette == "Dynamic" && android.os.Build.VERSION.SDK_INT >= 31) {
                    if(dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else namedPalette(palette, dark) ?: if(dark) darkColorScheme(
                    primary = Color(0xFFE0573C), background = Color(0xFF0B0B0D),
                    surfaceContainerHighest = Color(0xFF35353C)
                ) else lightColorScheme(primary = Color(0xFFC0442A))
                Column(Modifier.width(104.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(onClick = { model.appearance(options.copy(palette = palette)) },
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.6f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(if(selected) 3.dp else 1.dp,
                            if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.background(preview.background).padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween) {
                            Box(Modifier.fillMaxWidth().height(14.dp).background(preview.primary, RoundedCornerShape(8.dp)))
                            Box(Modifier.width(32.dp).height(50.dp).background(preview.primaryContainer, RoundedCornerShape(8.dp)))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(14.dp).background(preview.primary, RoundedCornerShape(7.dp)))
                                Box(Modifier.weight(1f).height(14.dp).background(preview.surfaceContainerHighest, RoundedCornerShape(7.dp)))
                            }
                        }
                    }
                    Text(palette, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        AppearanceToggle("Pure black dark mode", options.pureBlack) {
            model.appearance(options.copy(pureBlack = it))
        }
        HorizontalDivider()
        Text("Dates and timestamps", style = MaterialTheme.typography.titleMedium)
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
            OutlinedTextField(options.dateFormat, {}, readOnly = true, label = { Text("Date format") },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
            ExposedDropdownMenu(expanded, { expanded = false }) {
                dateFormats.forEach { format -> DropdownMenuItem(
                    text = { Text("$format  (${LocalDate.of(2026, 10, 2).format(DateTimeFormatter.ofPattern(format))})") },
                    onClick = { model.appearance(options.copy(dateFormat = format)); expanded = false })
                }
            }
        }
        AppearanceToggle("Relative timestamps", options.relativeDates,
            "Show Today and Yesterday for recent dates") {
            model.appearance(options.copy(relativeDates = it))
        }
        HorizontalDivider()
        Text("Navigation bar", style = MaterialTheme.typography.titleMedium)
        AppearanceToggle("Always show nav labels", options.alwaysShowNavLabels,
            "When off, only the selected destination shows its label") {
            model.appearance(options.copy(alwaysShowNavLabels = it))
        }
    }
}
