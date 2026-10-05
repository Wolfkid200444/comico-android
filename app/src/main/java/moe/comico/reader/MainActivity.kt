package moe.comico.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: ReaderViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppearance provides state.appearance) {
                ComicoTheme(state.theme, state.dynamicColor) {
                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner, model, state.offline) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if(!state.offline && event == androidx.lifecycle.Lifecycle.Event.ON_START) model.updates.check()
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        if(!state.offline && lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) model.updates.check()
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }
                    ComicoApp(state, model)
                    if (!state.offline) UpdatePrompt(model.updates)
                }
            }
        }
    }
}

@Composable
fun ComicoTheme(theme: String = "Website", dynamic: Boolean = false, content: @Composable () -> Unit) {
    val dark = when(theme) { "Website", "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
    val context = LocalContext.current
    val appearance = LocalAppearance.current
    val baseScheme = if(dynamic && android.os.Build.VERSION.SDK_INT >= 31) {
        if(dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if(dark) darkColorScheme(
        primary = Color(0xFFE0573C), onPrimary = Color(0xFF17070A),
        primaryContainer = Color(0xFF53251D), onPrimaryContainer = Color(0xFFECedef),
        surfaceTint = Color(0xFFE0573C),
        secondary = Color(0xFFE0573C), onSecondary = Color(0xFF17070A), secondaryContainer = Color(0xFF35353C),
        onSecondaryContainer = Color(0xFFECedef), tertiary = Color(0xFFE0573C), onTertiary = Color(0xFF17070A), tertiaryContainer = Color(0xFF53251D), onTertiaryContainer = Color(0xFFECEDEF),
        background = Color(0xFF0B0B0D), onBackground = Color(0xFFECedef),
        surface = Color(0xFF131316), onSurface = Color(0xFFECedef),
        surfaceVariant = Color(0xFF1B1B1F), onSurfaceVariant = Color(0xFF8C8C96),
        surfaceContainerLowest = Color(0xFF0B0B0D), surfaceContainerLow = Color(0xFF131316),
        surfaceContainer = Color(0xFF1B1B1F), surfaceContainerHigh = Color(0xFF26262B),
        surfaceContainerHighest = Color(0xFF35353C), outline = Color(0xFF8C8C96),
        outlineVariant = Color(0xFF26262B)
    ) else lightColorScheme(
        primary = Color(0xFFC0442A), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDAD1), onPrimaryContainer = Color(0xFF16161A),
        surfaceTint = Color(0xFFC0442A),
        secondary = Color(0xFFC0442A), onSecondary = Color.White, secondaryContainer = Color(0xFFE5E3DD),
        onSecondaryContainer = Color(0xFF16161A), tertiary = Color(0xFFC0442A), onTertiary = Color.White, tertiaryContainer = Color(0xFFFFDAD1), onTertiaryContainer = Color(0xFF16161A),
        background = Color(0xFFFBFBF9), onBackground = Color(0xFF16161A),
        surface = Color.White, onSurface = Color(0xFF16161A),
        surfaceVariant = Color(0xFFE5E3DD), onSurfaceVariant = Color(0xFF6A6A73),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBFBF9),
        surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF3F2EE),
        surfaceContainerHighest = Color(0xFFE5E3DD), outline = Color(0xFFD2CFC7),
        outlineVariant = Color(0xFFE5E3DD)
    )
    val palette = if(dynamic) baseScheme else namedPalette(appearance.palette, dark) ?: baseScheme
    val scheme = if(dark && appearance.pureBlack) palette.copy(
        background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black, surfaceContainer = Color.Black
    ) else palette
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
