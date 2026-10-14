package moe.comico.reader

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.util.UUID

@Composable
fun ReadingSessionTracker(chapter: String, pages: List<Int>, enabled: Boolean, model: ReaderViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val currentPages by rememberUpdatedState(pages)
    val currentEnabled by rememberUpdatedState(enabled)
    val owner = ChapterAlerts.owner(context)
    val viewedPages = remember(chapter, owner) { mutableSetOf<Int>() }
    LaunchedEffect(pages, enabled, owner) { if (enabled) viewedPages.addAll(pages) }
    LaunchedEffect(chapter, owner, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val session = UUID.randomUUID().toString()
            var elapsed = SystemClock.elapsedRealtime()
            var wasEnabled = currentEnabled
            fun record() {
                val now = SystemClock.elapsedRealtime()
                val duration = now - elapsed
                if (wasEnabled && currentEnabled && duration in 1..15_000) {
                    val end = Instant.now()
                    model.recordReading(chapter, owner, session, end.minusMillis(duration), end, (viewedPages + currentPages).toList())
                }
                viewedPages.clear()
                elapsed = now
                wasEnabled = currentEnabled
            }
            try {
                while (isActive) { delay(5_000); record() }
            } finally { record() }
        }
    }
}
