package moe.comico.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class DiscoverPage(val items: List<Manga> = emptyList(), val offset: Int = 0,
    val hasMore: Boolean = true, val loading: Boolean = false, val error: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverPageScreen(state: AppState, model: ReaderViewModel) {
    val page = state.feedPage
    val grid = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let {
            it >= grid.layoutInfo.totalItemsCount - 6
        } ?: false }
    }
    LaunchedEffect(nearEnd, page.offset, page.loading, page.error) {
        if(nearEnd && page.hasMore && !page.loading && page.error == null) model.loadFeedPage(more = true)
    }
    PullToRefreshBox(isRefreshing = page.loading && page.items.isEmpty(),
        onRefresh = { model.loadFeedPage() }, modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Adaptive(145.dp), state = grid,
            contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(page.items, key = { it.id }) { manga ->
                MangaCard(manga, state.progress[manga.id]?.let { "Chapter ${it.number}" }) { model.open(manga) }
            }
            if(page.loading) item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            page.error?.let { error -> item(span = { GridItemSpan(maxLineSpan) }) {
                Column { Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { model.loadFeedPage(more = page.items.isNotEmpty()) }) { Text("Retry") } }
            } }
            if(!page.loading && page.error == null && page.items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No titles are available in this feed yet.")
            }
        }
    }
}
