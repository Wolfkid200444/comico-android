@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package moe.comico.reader

import android.app.Activity
import kotlin.math.roundToInt
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private val readerOverlay = Color(0xFF18191F).copy(alpha = .88f)
@Composable
fun ReaderSystemBars(show: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
    }
    DisposableEffect(activity, view) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        val oldBehavior = controller?.systemBarsBehavior
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            oldStatus?.let { controller?.isAppearanceLightStatusBars = it }
            oldNavigation?.let { controller?.isAppearanceLightNavigationBars = it }
            oldBehavior?.let { controller?.systemBarsBehavior = it }
        }
    }
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if(show) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
@Composable
fun ReaderHeader(state: AppState, model: ReaderViewModel, onChapters: () -> Unit) {
    val chapter = state.reader ?: return
    TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = readerOverlay,
        titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
        title = {
            Row(Modifier.clickable(onClickLabel = "Choose chapter", onClick = onChapters).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Chapter ${chapter.number}", style = MaterialTheme.typography.titleMedium)
                    Text(state.selected?.title.orEmpty(), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.ExpandMore, "Choose chapter")
            }
        }, navigationIcon = { IconButton(onClick = model::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to manga") } },
        actions = { IconButton(onClick = { model.discussion.open(DiscussionTarget(chapter.id,
            "${state.selected?.title.orEmpty()} · Chapter ${chapter.number}", chapter = true)) }) {
            Icon(Icons.Rounded.ChatBubbleOutline, "Chapter comments")
        } })
}
fun readerAdjacentChapters(state: AppState): Pair<Chapter?, Chapter?> {
    if(state.chapterGroup != null && state.chapterRoster != null) {
        val chapters = filterChapterGroup(state.chapterRoster, state.chapterGroup)
        val current = chapters.indexOfFirst { it.id == state.reader?.id }
        return (if(current >= 0) chapters.getOrNull(current + 1) else null) to
            (if(current > 0) chapters.getOrNull(current - 1) else null)
    }
    val index = state.chapters.indexOfFirst { it.id == state.reader?.id }
    return (state.nativeReader.previous ?: if(index >= 0) state.chapters.getOrNull(index + 1) else null) to
        (state.nativeReader.next ?: if(index > 0) state.chapters.getOrNull(index - 1) else null)
}
@Composable
fun ReaderBottomControls(state: AppState, mode: ReadingMode, onSettings: () -> Unit, onChapters: () -> Unit,
                         onPrevious: (Chapter) -> Unit, onNext: (Chapter) -> Unit, onPage: (Int) -> Unit) {
    val session = state.nativeReader.session
    val (previous, next) = readerAdjacentChapters(state)
    Surface(color = readerOverlay, contentColor = Color.White) {
        Column(Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if(session != null) "${state.nativeReader.page + 1} / ${session.pages.size}" else "Reader",
                    Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text(mode.label, style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = onChapters) { Icon(Icons.Rounded.FormatListNumbered, "Choose chapter") }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, "Reader settings") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { previous?.let(onPrevious) }, enabled = previous != null) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous chapter", tint = if(previous != null) Color.White else Color.White.copy(alpha = .35f))
                }
                if(session != null && session.pages.size > 1) {
                    var slider by remember(state.reader?.id, state.nativeReader.page) { mutableFloatStateOf(state.nativeReader.page.toFloat()) }
                    Slider(value = slider, onValueChange = { slider = it }, onValueChangeFinished = { onPage(slider.roundToInt()) },
                        valueRange = 0f..session.pages.lastIndex.toFloat(), modifier = Modifier.weight(1f))
                } else Spacer(Modifier.weight(1f))
                IconButton(onClick = { next?.let(onNext) }, enabled = next != null) {
                    Icon(Icons.Rounded.SkipNext, "Next chapter", tint = if(next != null) Color.White else Color.White.copy(alpha = .35f))
                }
            }
            session?.let {
                Text("${it.source} · ${it.method.label}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .7f))
                if(it.attribution.isNotBlank()) Text(it.attribution, style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = .7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
@Composable
fun ReaderPageCounter(page: Int, count: Int, modifier: Modifier = Modifier) {
    Box(modifier.windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).padding(bottom = 12.dp)) {
        Surface(color = Color.Black.copy(alpha = .6f), contentColor = Color.White.copy(alpha = .9f), shape = MaterialTheme.shapes.small) {
            Text("$page / $count", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable
fun ReaderChapterPicker(state: AppState, model: ReaderViewModel, onDismiss: () -> Unit) {
    val current = state.chapters.indexOfFirst { it.id == state.reader?.id }.coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = current)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Chapters", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
            items(state.chapters, key = { it.id }) { chapter ->
                Surface(onClick = { onDismiss(); if(chapter.id != state.reader?.id) model.read(chapter) },
                    color = if(chapter.id == state.reader?.id) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    shape = MaterialTheme.shapes.medium) {
                    ListItem(headlineContent = { Text("Chapter ${chapter.number}") },
                        supportingContent = { Text(chapter.title.takeUnless { it.isBlank() || it == "null" } ?: chapter.group) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        trailingContent = { if(chapter.id == state.reader?.id) Icon(Icons.Rounded.Check, "Current chapter") })
                }
            }
            if(state.chapterLoading) item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            state.chapterError?.let { error -> item { Text(error, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) } }
            if(!state.chapterLoading && state.chapterOffset < state.chapterTotal) item {
                TextButton(onClick = { model.loadChapters(true) }, modifier = Modifier.fillMaxWidth()) { Text("Load more chapters") }
            }
            if(state.chapters.isEmpty() && !state.chapterLoading) item { Text("No chapters loaded.", Modifier.padding(20.dp)) }
        }
    }
}
