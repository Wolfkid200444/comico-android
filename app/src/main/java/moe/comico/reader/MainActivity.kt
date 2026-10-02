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
            ComicoTheme(state.theme, state.dynamicColor) { ComicoApp(state, model) }
        }
    }
}

@Composable
fun ComicoTheme(theme: String = "System", dynamic: Boolean = false, content: @Composable () -> Unit) {
    val dark = when(theme) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
    val context = LocalContext.current
    val scheme = if(dynamic && android.os.Build.VERSION.SDK_INT >= 31) {
        if(dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if(dark) darkColorScheme(primary = Color(0xFFD3BCFF), primaryContainer = Color(0xFF503773), secondaryContainer = Color(0xFF393344), background = Color(0xFF141218), surface = Color(0xFF141218))
    else lightColorScheme(primary = Color(0xFF70518D), onPrimary = Color.White, primaryContainer = Color(0xFFEFDCFF), onPrimaryContainer = Color(0xFF29123E), secondaryContainer = Color(0xFFEDE5F3), background = Color(0xFFFFF8FC), surface = Color(0xFFFFF8FC), tertiaryContainer = Color(0xFFFFDBCA))
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
