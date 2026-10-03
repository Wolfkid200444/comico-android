@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package moe.comico.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DiscoverScreen(state: AppState, model: ReaderViewModel) {
    DiscoverContent(state,onSearch = { model.tab("Search") },onRefresh = { model.loadDiscover(refresh = true);model.syncAccount() },onRetry = model::loadDiscoverFeed,onOpen = model::open,onHistory = model::openHistory,onLibrary = { model.tab("Library") },onHistoryTab = { model.tab("History") },onFeed = model::openFeed)
}

@Composable
private fun DiscoverContent(
    state: AppState,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: (DiscoverFeed) -> Unit,
    onOpen: (Manga) -> Unit,
    onHistory: (HistoryEntry) -> Unit,
    onLibrary: () -> Unit,
    onHistoryTab: () -> Unit,
    onFeed: (DiscoverFeed) -> Unit
) {
    val feeds = visibleDiscoverFeeds(state.account.user != null)
    val refreshing = state.syncLoading || feeds.any { state.discoverSections[it]?.loading == true }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { if(!refreshing) onRefresh() },
        modifier = Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp,bottom = 24.dp),verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item(key = "header") {
            Column(Modifier.padding(horizontal = 20.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Discover",style = MaterialTheme.typography.headlineLarge)
                Text("A quiet place to discover, track and read manga, manhwa and webtoons from supported sources and scanlation groups.",style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onSurfaceVariant)
                DockedSearchBar(inputField = { SearchBarDefaults.InputField(query = "",onQueryChange = {},onSearch = { onSearch() },expanded = false,onExpandedChange = { if(it) onSearch() },placeholder = { Text("Search manga and webtoons") },leadingIcon = { Icon(Icons.Rounded.Search,null) }) },expanded = false,onExpandedChange = { if(it) onSearch() },modifier = Modifier.fillMaxWidth()) {}
            }
        }
        items(feeds,key = { if(it.accountOnly) "${state.account.user?.id}:${it.name}" else it.name }) { feed ->
            val history = if(feed == DiscoverFeed.HISTORY) state.history.distinctBy { it.manga.id } else emptyList()
            val section = if(feed == DiscoverFeed.HISTORY) DiscoverSection(items = history.map { it.manga },loading = state.syncLoading && history.isEmpty(),error = state.syncError) else state.discoverSections[feed] ?: DiscoverSection(loading = true)
            DiscoverCarousel(feed,section,history,state.progress,onRetry = { onRetry(feed) },onOpen = onOpen,onHistory = onHistory,onMore = when(feed) { DiscoverFeed.HISTORY -> onHistoryTab;else -> { { onFeed(feed) } } })
        }
    }
    }
}

@Composable
private fun DiscoverCarousel(
    feed: DiscoverFeed,
    section: DiscoverSection,
    history: List<HistoryEntry>,
    progress: Map<String,Chapter>,
    onRetry: () -> Unit,
    onOpen: (Manga) -> Unit,
    onHistory: (HistoryEntry) -> Unit,
    onMore: (() -> Unit)?
) {
    val textHeight = with(LocalDensity.current) {
        MaterialTheme.typography.titleSmall.lineHeight.toDp() * 2 +
            MaterialTheme.typography.labelMedium.lineHeight.toDp()
    }
    val cardHeight = 216.dp + textHeight + 44.dp
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp,end = 12.dp)) {
            Text(feed.label,style = MaterialTheme.typography.titleLarge,modifier = Modifier.weight(1f).padding(top = 8.dp))
            if(onMore != null) IconButton(onClick = onMore) { Icon(Icons.AutoMirrored.Rounded.ArrowForward,"Open ${feed.label}") }
        }
        if(section.loading && section.items.isEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(3) { Surface(Modifier.width(168.dp).height(cardHeight),shape = MaterialTheme.shapes.extraLarge,color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(20.dp),verticalArrangement = Arrangement.Center) { LinearProgressIndicator(Modifier.fillMaxWidth());Spacer(Modifier.height(12.dp));Text("Loading stories",style = MaterialTheme.typography.bodySmall) }
                } }
            }
        } else if(section.items.isNotEmpty()) {
            HorizontalUncontainedCarousel(
                state = rememberCarouselState { minOf(12, section.items.size) },
                itemWidth = 168.dp,itemSpacing = 12.dp,
                contentPadding = PaddingValues(horizontal = 20.dp),modifier = Modifier.fillMaxWidth().height(cardHeight)
            ) { index ->
                val manga = section.items[index]
                val entry = history.firstOrNull { it.manga.id == manga.id }
                Surface(onClick = { if(entry != null) onHistory(entry) else onOpen(manga) },modifier = Modifier.fillMaxHeight().maskClip(MaterialTheme.shapes.extraLarge),shape = MaterialTheme.shapes.extraLarge,color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column {
                        Cover(manga,Modifier.fillMaxWidth().height(216.dp))
                        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 28.dp),verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(manga.title,style = MaterialTheme.typography.titleSmall,maxLines = 2,minLines = 2,overflow = TextOverflow.Ellipsis)
                            Text(entry?.let { "Chapter ${it.chapter.number} · Page ${it.page + 1}" } ?: progress[manga.id]?.let { "Chapter ${it.number}" } ?: manga.format.replaceFirstChar { it.uppercase() },style = MaterialTheme.typography.labelMedium,maxLines = 1,overflow = TextOverflow.Ellipsis,color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if(section.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        }
        if(section.error != null) {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp),shape = MaterialTheme.shapes.large,color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(section.error,color = MaterialTheme.colorScheme.onErrorContainer,style = MaterialTheme.typography.bodyMedium);if(feed != DiscoverFeed.HISTORY) TextButton(onClick = onRetry) { Text("Retry this feed") } }
            }
        } else if(!section.loading && section.items.isEmpty()) {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp),shape = MaterialTheme.shapes.large,color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text(when(feed) { DiscoverFeed.HISTORY -> "Chapters you read will appear here.";DiscoverFeed.UPDATES -> "New chapters from titles in your account library will appear here.";else -> "No titles are available in this feed yet." },Modifier.padding(20.dp),style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
