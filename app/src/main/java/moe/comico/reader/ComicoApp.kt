@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package moe.comico.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage

private val destinations = listOf("Discover" to Icons.Rounded.Explore, "Search" to Icons.Rounded.Search, "Library" to Icons.Rounded.Bookmarks, "History" to Icons.Rounded.History, "Settings" to Icons.Rounded.Settings)

private fun unselectedDestinationIcon(name: String) = when(name) {
    "Search" -> Icons.Outlined.Search
    "Library" -> Icons.Outlined.Bookmarks
    "History" -> Icons.Outlined.History
    "Leaderboard" -> Icons.Outlined.Leaderboard
    "Settings" -> Icons.Outlined.Settings
    else -> Icons.Outlined.Explore
}

@Composable
fun ComicoApp(state: AppState, model: ReaderViewModel) {
    val screenState = rememberSaveableStateHolder()
    BackHandler(state.selected != null || state.tab != "Discover") { if(state.selected != null) model.back() else model.tab(if(state.tab == "Leaderboard") "Settings" else "Discover") }
    if(state.reader != null) {
        ReaderScreen(state, model)
        return
    }
    val appBarState = key(state.tab,state.selected?.id) { rememberTopAppBarState() }
    val appBarScroll = TopAppBarDefaults.pinnedScrollBehavior(appBarState)
    BoxWithConstraints {
        val wide = maxWidth >= 700.dp
        Row {
            if(wide && state.selected == null) NavigationRail(Modifier.fillMaxHeight()) {
                Spacer(Modifier.height(24.dp))
                Icon(Icons.AutoMirrored.Rounded.MenuBook, "Comico", tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(36.dp))
                destinations.forEach { (name, icon) -> NavigationRailItem(selected = state.tab == name, onClick = { model.tab(name) }, icon = { Icon(if(state.tab == name) icon else unselectedDestinationIcon(name), null) }, label = { Text(name) }) }
            }
            Scaffold(
                modifier = Modifier.weight(1f).nestedScroll(appBarScroll.nestedScrollConnection),
                topBar = {
                    if(state.selected != null) TopAppBar(title = { Text("Title details", style = MaterialTheme.typography.titleMedium) }, navigationIcon = { IconButton(onClick = model::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
                    else TopAppBar(scrollBehavior = appBarScroll,colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background,scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),navigationIcon = { if(state.tab == "Leaderboard") IconButton(onClick = { model.tab("Settings") }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,"Back to settings") } },title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(painterResource(R.drawable.comico_logo), "Comico logo", Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                        Text("comico.moe", style = MaterialTheme.typography.titleLarge)
                    } }, actions = { if(state.tab == "Library") IconButton(onClick = model::toggleLibrarySearch) { Icon(Icons.Rounded.Search, "Search your library") } else if(state.tab != "Search") IconButton(onClick = { model.tab("Search") }) { Icon(Icons.Rounded.Search, "Search manga") } })
                },
                floatingActionButton = {
                    if(state.selected != null && state.firstChapter != null) ExtendedFloatingActionButton(onClick = model::startReading,icon = { Icon(Icons.Rounded.PlayArrow,null) },text = { Text("Start reading") })
                },
                bottomBar = {
                    if(!wide && state.selected == null) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        destinations.forEach { (name, icon) -> NavigationBarItem(selected = state.tab == name, onClick = { model.tab(name) }, icon = { Icon(if(state.tab == name) icon else unselectedDestinationIcon(name), null) }, label = { Text(name,maxLines = 1,style = MaterialTheme.typography.labelSmall) },colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,selectedTextColor = MaterialTheme.colorScheme.primary)) }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    if(state.selected != null) DetailScreen(state,model) else screenState.SaveableStateProvider(state.tab) {
                        when(state.tab) {
                            "Settings" -> SettingsScreen(state,model)
                            "History" -> HistoryScreen(state,model)
                            "Leaderboard" -> LeaderboardScreen(state,model)
                            "Discover" -> DiscoverScreen(state,model)
                            "Library" -> CatalogScreen(state,filterLibrary(state.library,state.libraryQuery),model,library = true)
                            else -> CatalogScreen(state,state.catalog,model,library = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogScreen(state: AppState, manga: List<Manga>, model: ReaderViewModel, library: Boolean) {
    val libraryFocus = remember { FocusRequester() }
    var filtersOpen by remember { mutableStateOf(false) }
    if(filtersOpen) SearchFilterDialog(state,model) { filtersOpen = false }
    LazyVerticalGrid(columns = GridCells.Adaptive(145.dp), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if(library) "Library" else "Search", style = MaterialTheme.typography.headlineLarge,modifier = Modifier.weight(1f))
                    if(library) IconButton(onClick = model::toggleLibrarySearch) { Icon(Icons.Rounded.Search,"Search your library") }
                }
                Text(if(library) "Saved stories, ready when you are." else "A quiet place to discover, track and read manga, manhwa and webtoons from supported sources and scanlation groups.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(library && state.librarySearchVisible) item(span = { GridItemSpan(maxLineSpan) }) {
            LaunchedEffect(Unit) { libraryFocus.requestFocus() }
            OutlinedTextField(value = state.libraryQuery,onValueChange = model::librarySearch,modifier = Modifier.fillMaxWidth().focusRequester(libraryFocus),singleLine = true,shape = MaterialTheme.shapes.extraLarge,placeholder = { Text("Search saved titles") },leadingIcon = { Icon(Icons.Rounded.Search,null) },trailingIcon = { IconButton(onClick = { if(state.libraryQuery.isNotEmpty()) model.librarySearch("") else model.toggleLibrarySearch() }) { Icon(Icons.Rounded.Close,if(state.libraryQuery.isNotEmpty()) "Clear library search" else "Close library search") } })
        }
        if(!library && state.tab == "Search") item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(value = state.query, onValueChange = model::search, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(28.dp), placeholder = { Text("Search titles…") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = { if(state.query.isNotEmpty()) IconButton(onClick = { model.search("") }) { Icon(Icons.Rounded.Close, "Clear search") } })
        }
        if(!library && state.tab == "Search") item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = state.format == "All", onClick = { model.format("All") },label = { Text("All") })
                FilterChip(selected = state.format == "Manga", onClick = { model.format("Manga") },label = { Text("Manga") })
                OutlinedButton(onClick = { filtersOpen = true },modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.FilterList, null,Modifier.size(18.dp));Spacer(Modifier.width(4.dp));Text(if(state.searchFilters.activeCount == 0) "Filters" else "Filters · ${state.searchFilters.activeCount}")
                }
            }
        }
        if(library) item(span = { GridItemSpan(maxLineSpan) }) { SyncStatus(state,model) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if(library) "Saved titles · ${manga.size}" else "Search results", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if(!library) IconButton(onClick = { model.loadCatalog() }, enabled = !state.loading) { Icon(Icons.Rounded.Refresh, "Refresh titles") }
            }
        }
        items(manga, key = { it.id }) { item ->
            MangaCard(item, state.progress[item.id]?.let { "Chapter ${it.number}" }, { model.open(item) })
        }
        if(!library && state.error != null) item(span = { GridItemSpan(maxLineSpan) }) { MessageCard(Icons.Rounded.CloudOff, "Couldn't load stories", state.error) { model.loadCatalog(more = state.offset > 0 && manga.isNotEmpty()) } }
        if(!library && state.loading) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        if(manga.isEmpty() && (library || (!state.loading && state.error == null))) item(span = { GridItemSpan(maxLineSpan) }) {
            MessageCard(if(library) Icons.Rounded.BookmarkBorder else Icons.Rounded.Search, if(library && state.libraryQuery.isNotBlank()) "No saved titles match" else if(library) "Make room for your favorites" else "No stories found", if(library && state.libraryQuery.isNotBlank()) "Try another title or clear your library search." else if(library) "Open a title and save it to your library." else "Try another title or a different format.")
        }
        if(!library && !state.loading && state.error == null && state.offset < state.total) item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedButton(onClick = { model.loadCatalog(more = true) }, modifier = Modifier.fillMaxWidth()) { Text("Load more stories") }
        }
    }
}

@Composable
fun MangaCard(manga: Manga, progress: String? = null, onClick: () -> Unit) {
    Card(onClick = onClick, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Cover(manga, Modifier.fillMaxWidth().aspectRatio(0.7f))
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(manga.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            Text(progress ?: manga.tags.take(2).joinToString(" · ").ifBlank { manga.format }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun Cover(manga: Manga, modifier: Modifier) {
    SubcomposeAsyncImage(model = manga.cover, contentDescription = "Cover of ${manga.title}", modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentScale = ContentScale.Crop,
        loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } },
        error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline) } })
}

@Composable
fun MessageCard(icon: ImageVector, title: String, body: String, retry: (() -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if(retry != null) FilledTonalButton(onClick = retry) { Text("Try again") }
        }
    }
}

@Composable
private fun DetailScreen(state: AppState, model: ReaderViewModel) {
    val manga = state.selected ?: return
    val saved = state.library.any { it.id == manga.id }
    val progress = state.progress[manga.id]
    var readerSettings by remember { mutableStateOf(false) }
    if(readerSettings) ReaderSettingsDialog(state, model) { readerSettings = false }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp,end = 20.dp,top = 20.dp,bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Cover(manga, Modifier.width(120.dp).aspectRatio(0.7f).clip(RoundedCornerShape(18.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(manga.format.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(manga.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(manga.status.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = { model.bookmark(manga) },enabled = !state.account.loading && !state.syncLoading) { Icon(if(saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if(saved) "Saved" else "Save") }
                }
            }
        }
        item { OutlinedButton(onClick = { readerSettings = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Tune, null); Spacer(Modifier.width(8.dp)); Text("Reader settings for this manga") } }
        if(progress != null) item { Button(onClick = { model.read(progress) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.PlayArrow, null); Text("Continue chapter ${progress.number}") } }
        item { Text(manga.tags.take(6).joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        if(manga.description.isNotBlank()) item {
            var expanded by remember(manga.id) { mutableStateOf(false) }
            Column {
                Text("About this story", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(manga.description, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium, maxLines = if(expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { expanded = !expanded }) { Text(if(expanded) "Read less" else "Read more") }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chapters", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                var menu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text(state.language.uppercase()); Icon(Icons.Rounded.ExpandMore, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        listOf("en" to "English", "es" to "Spanish", "pt-br" to "Portuguese", "fr" to "French", "ja" to "Japanese").forEach { (code, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; model.language(code) }) }
                    }
                }
            }
        }
        items(state.chapters, key = { it.id }) { chapter ->
            Surface(onClick = { model.read(chapter) }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Chapter ${chapter.number}", style = MaterialTheme.typography.titleMedium)
                        if(chapter.title.isNotBlank()) Text(chapter.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(chapter.group, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if(chapter.external.isNotEmpty()) Icons.AutoMirrored.Rounded.OpenInNew else Icons.Rounded.ChevronRight, "Read chapter ${chapter.number}", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if(state.chapterLoading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        if(state.startError != null) item { Text(state.startError,color = MaterialTheme.colorScheme.error) }
        if(state.chapterError != null) item { MessageCard(Icons.Rounded.CloudOff, "Couldn't load chapters", state.chapterError) { model.loadChapters() } }
        if(!state.chapterLoading && state.chapterError == null && state.chapters.isEmpty()) item { MessageCard(Icons.AutoMirrored.Rounded.MenuBook, "No chapters in this language", "Choose another language to check available translations.") }
        if(!state.chapterLoading && state.chapterOffset < state.chapterTotal) item { OutlinedButton(onClick = { model.loadChapters(true) }, Modifier.fillMaxWidth()) { Text("Load more chapters") } }
    }
}

@Preview(showBackground = true, widthDp = 180)
@Composable
private fun CardPreview() { ComicoTheme { MangaCard(Manga("preview", "Your next favorite story", "", "ongoing", "manga", listOf("Adventure", "Fantasy"))) {} } }
