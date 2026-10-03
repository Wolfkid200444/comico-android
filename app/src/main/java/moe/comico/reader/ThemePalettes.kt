package moe.comico.reader

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color

val themePalettes = listOf("Default", "Dynamic", "Catppuccin", "Ocean", "Forest", "Rose", "Lavender")

fun namedPalette(name: String, dark: Boolean): ColorScheme? {
    val accent = when(name) {
        "Catppuccin" -> if(dark) 0xFFCBA6F7 else 0xFF8839EF
        "Ocean" -> if(dark) 0xFF8DCDFF else 0xFF00629E
        "Forest" -> if(dark) 0xFF95D5A3 else 0xFF286B3D
        "Rose" -> if(dark) 0xFFFFB1C8 else 0xFFA63761
        "Lavender" -> if(dark) 0xFFC6B9FF else 0xFF6550A4
        else -> return null
    }
    return if(dark) darkColorScheme(
        primary = Color(accent), secondary = Color(accent),
        primaryContainer = Color(accent).copy(red = Color(accent).red * 0.3f,
            green = Color(accent).green * 0.3f, blue = Color(accent).blue * 0.3f),
        onPrimaryContainer = Color(0xFFF1EFFF),
        background = if(name == "Catppuccin") Color(0xFF1E1E2E) else Color(0xFF15151B),
        surface = if(name == "Catppuccin") Color(0xFF1E1E2E) else Color(0xFF15151B)
    ) else lightColorScheme(primary = Color(accent), secondary = Color(accent),
        primaryContainer = Color(accent).copy(red = 0.85f + Color(accent).red * 0.15f,
            green = 0.85f + Color(accent).green * 0.15f, blue = 0.85f + Color(accent).blue * 0.15f))
}
