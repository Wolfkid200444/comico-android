@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package moe.comico.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

private val destinations = listOf("Discover" to Icons.Rounded.Explore, "Search" to Icons.Rounded.Search, "Library" to Icons.Rounded.Bookmarks, "History" to Icons.Rounded.History, "Profile" to Icons.Rounded.Person)

private fun unselectedDestinationIcon(name: String) = when(name) {
    "Search" -> Icons.Outlined.Search
    "Library" -> Icons.Outlined.Bookmarks
    "History" -> Icons.Outlined.History
    "Leaderboard" -> Icons.Outlined.Leaderboard
    "Profile" -> Icons.Rounded.PersonOutline
    "Settings" -> Icons.Outlined.Settings
    else -> Icons.Outlined.Explore
}

@Composable
fun ComicoApp(state: AppState, model: ReaderViewModel) {
    val discussion by model.discussion.state.collectAsState()
    if(discussion.target != null) DiscussionDialog(discussion, model.discussion, state.account.user != null)
    val screenState = rememberSaveableStateHolder()
    var libraryFilters by rememberSaveable { mutableStateOf(false) }
    var libraryMenu by remember { mutableStateOf(false) }
    var collectionManager by remember { mutableStateOf(false) }
    if (collectionManager && state.tab == "Library") CollectionManager(state, model) { collectionManager = false }
    if (libraryFilters && state.tab == "Library" && state.selected == null) LibraryFilterSheet(state, model) { libraryFilters = false }
    var settingsSection by rememberSaveable(state.tab) {
        mutableStateOf(when(state.tab) { "Comments" -> "Comments"; "Edit profile" -> "Profile"; else -> "Menu" })
    }
    val navigateBack: () -> Unit = {
        when {
            state.selected != null -> model.back()
            state.tab in listOf("Settings", "Comments", "Edit profile") && settingsSection != "Menu" -> settingsSection = "Menu"
            state.tab == "Leaderboard" -> model.tab("Settings")
            state.tab == "Statistics" -> model.tab("Profile")
            state.tab in listOf("Settings", "Comments", "Edit profile") -> model.tab("Profile")
            else -> model.tab(if (state.offline) "Library" else "Discover")
        }
    }
    BackHandler(state.selected != null || state.tab != "Discover", onBack = navigateBack)
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
                destinations.forEach { (name, icon) -> NavigationRailItem(selected = (state.tab == name || (name == "Discover" && state.tab == "Feed") || (name == "Profile" && state.tab in listOf("Settings", "Comments", "Edit profile", "Leaderboard", "Statistics"))), onClick = { model.tab(name) }, enabled = !state.offline || name !in listOf("Discover", "Search"), icon = { Icon(if((state.tab == name || (name == "Discover" && state.tab == "Feed") || (name == "Profile" && state.tab in listOf("Settings", "Comments", "Edit profile", "Leaderboard", "Statistics")))) icon else unselectedDestinationIcon(name), null) }, alwaysShowLabel = state.appearance.alwaysShowNavLabels, label = { Text(name) }) }
            }
            Scaffold(
                modifier = Modifier.weight(1f).nestedScroll(appBarScroll.nestedScrollConnection),
                topBar = {
                    if(state.selected != null) TopAppBar(title = {}, navigationIcon = { IconButton(onClick = model::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
                    else TopAppBar(scrollBehavior = appBarScroll,colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background,scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),navigationIcon = { if(state.tab in listOf("Feed", "Leaderboard", "Settings", "Comments", "Edit profile", "Statistics")) IconButton(onClick = navigateBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,"Back") } },title = {
                        if(state.tab == "Discover") Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Image(painterResource(R.drawable.comico_logo), "Comico logo", Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                            Text("comico.moe", style = MaterialTheme.typography.titleLarge)
                        } else Text(
                            if(state.tab in listOf("Settings", "Comments", "Edit profile")) {
                                when(settingsSection) { "Menu" -> "Settings"; "Profile" -> "Identity and social links"; else -> settingsSection }
                            } else if(state.tab == "Feed") state.feed?.label.orEmpty() else if(state.tab == "History") "Reading history" else state.tab,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }, actions = { if(state.tab == "Library") {
                        IconButton(onClick = model::toggleLibrarySearch) { Icon(Icons.Rounded.Search, "Search your library") }
                        IconButton(onClick = { libraryFilters = true }) {
                            BadgedBox(badge = { if (state.libraryOptions.filterCount > 0) Badge { Text("${state.libraryOptions.filterCount}") } }) {
                                Icon(Icons.Rounded.FilterList, "Filter library")
                            }
                        }
                        Box {
                            IconButton(onClick = { libraryMenu = true }) { Icon(Icons.Rounded.MoreVert, "Library options") }
                            DropdownMenu(libraryMenu, { libraryMenu = false }) {
                                DropdownMenuItem(text = { Text("Edit Collections") }, onClick = { libraryMenu = false; collectionManager = true })
                                val collection = state.libraryCollections.firstOrNull { it.id == state.libraryOptions.collection }
                                val entries = visibleLibrary(state, collection?.mangaIds)
                                DropdownMenuItem(text = { Text("Open Random Entry") }, enabled = entries.isNotEmpty(),
                                    onClick = { libraryMenu = false; entries.randomOrNull()?.let(model::open) })
                            }
                        }
                    } else if(state.tab != "Search") IconButton(enabled = !state.offline, onClick = { model.tab("Search") }) { Icon(Icons.Rounded.Search, "Search manga") }; if(state.tab == "Profile") AppOverflowMenu(state, model) })
                },
                floatingActionButton = {
                    val progress = state.selected?.let { state.resumeChapter(it.id) }
                    if(state.selected != null && (progress != null || state.firstChapter != null)) {
                        ExtendedFloatingActionButton(
                            onClick = { if(progress != null) model.read(progress) else model.startReading() },
                            icon = { Icon(Icons.Rounded.PlayArrow, null) },
                            text = { Text(if(progress != null) "Resume" else "Start reading") }
                        )
                    }
                },
                bottomBar = {
                    if(!wide && state.selected == null) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        destinations.forEach { (name, icon) -> NavigationBarItem(selected = (state.tab == name || (name == "Discover" && state.tab == "Feed") || (name == "Profile" && state.tab in listOf("Settings", "Comments", "Edit profile", "Leaderboard", "Statistics"))), onClick = { model.tab(name) }, enabled = !state.offline || name !in listOf("Discover", "Search"), icon = { Icon(if((state.tab == name || (name == "Discover" && state.tab == "Feed") || (name == "Profile" && state.tab in listOf("Settings", "Comments", "Edit profile", "Leaderboard", "Statistics")))) icon else unselectedDestinationIcon(name), null) }, alwaysShowLabel = state.appearance.alwaysShowNavLabels, label = { Text(name,maxLines = 1,style = MaterialTheme.typography.labelSmall) },colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,selectedTextColor = MaterialTheme.colorScheme.primary)) }
                    }
                }
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    if (state.offline) Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                        Text("Offline · Saved chapters are available in Library", Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                    if(state.selected != null) DetailScreen(state,model) else screenState.SaveableStateProvider(state.tab) {
                        when(state.tab) {
                            "Settings", "Comments", "Edit profile" -> SettingsScreen(state,model,settingsSection) { settingsSection = it }
                            "Profile" -> ProfileScreen(state,model)
                            "Statistics" -> androidx.compose.foundation.lazy.LazyColumn(
                                Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)
                            ) { item { ProfileStatistics(state) } }
                            "History" -> HistoryScreen(state,model)
                            "Leaderboard" -> LeaderboardScreen(state,model)
                            "Discover" -> if (state.offline) LibraryScreen(state,model) else DiscoverScreen(state,model)
                            "Feed" -> DiscoverPageScreen(state,model)
                            "Library" -> LibraryScreen(state, model)
                            else -> CatalogScreen(state,state.catalog,model)
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogScreen(state: AppState, manga: List<Manga>, model: ReaderViewModel) {
    var filtersOpen by remember { mutableStateOf(false) }
    if(filtersOpen) SearchFilterDialog(state,model) { filtersOpen = false }
    LazyVerticalGrid(modifier = Modifier.fillMaxSize(), columns = GridCells.Adaptive(145.dp), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A quiet place to discover, track and read manga, manhwa and webtoons from supported sources and scanlation groups.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(state.tab == "Search") item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(value = state.query, onValueChange = model::search,
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Search titles…") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.query.isNotEmpty()) IconButton(onClick = { model.search("") }) {
                            Icon(Icons.Rounded.Close, "Clear search")
                        }
                        IconButton(onClick = { filtersOpen = true }) {
                            BadgedBox(badge = {
                                if (state.searchFilters.activeCount > 0) Badge {
                                    Text(state.searchFilters.activeCount.toString())
                                }
                            }) { Icon(Icons.Rounded.FilterList, "Search filters") }
                        }
                    }
                })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Search results", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { model.loadCatalog() }, enabled = !state.loading) { Icon(Icons.Rounded.Refresh, "Refresh titles") }
            }
        }
        items(manga, key = { it.id }) { item ->
            MangaCard(item, state.progress[item.id]?.let { "Chapter ${it.number}" }, { model.open(item) })
        }
        if(state.error != null) item(span = { GridItemSpan(maxLineSpan) }) { MessageCard(Icons.Rounded.CloudOff, "Couldn't load stories", state.error) { model.loadCatalog(more = state.offset > 0 && manga.isNotEmpty()) } }
        if(state.loading) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        if(manga.isEmpty() && !state.loading && state.error == null) item(span = { GridItemSpan(maxLineSpan) }) {
            MessageCard(Icons.Rounded.Search, "No stories found", "Try another title or a different format.")
        }
        if(!state.loading && state.error == null && state.offset < state.total) item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedButton(onClick = { model.loadCatalog(more = true) }, modifier = Modifier.fillMaxWidth()) { Text("Load more stories") }
        }
    }
}

@Composable
fun MangaCard(manga: Manga, progress: String? = null, onClick: () -> Unit) {
    Card(onClick = onClick, shape = androidx.compose.ui.graphics.RectangleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        TitledCover(manga, Modifier.fillMaxWidth().aspectRatio(168f / 256f),
            subtitle = manga.latestChapterNumber.takeIf { it.isNotBlank() }?.let { "Chapter $it" }
                ?: progress ?: manga.format.replaceFirstChar { it.uppercase() },
            updateTime = updateAge(manga.updatedAt))
    }
}

@Composable
fun TitledCover(manga: Manga, modifier: Modifier, subtitle: String? = null, updateTime: String? = null) {
    Box(modifier) {
        Cover(manga, Modifier.fillMaxSize())
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.BottomCenter)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(
                androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.9f)))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(manga.title, color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null || updateTime != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(subtitle.orEmpty(), Modifier.weight(1f),
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    updateTime?.let {
                        Text(it, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
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
    var webVisible by remember(manga.id) { mutableStateOf(false) }
    var readerSettings by remember { mutableStateOf(false) }
    var downloadedOnly by remember(manga.id) { mutableStateOf(false) }
    val offlineOnly = state.offline || downloadedOnly
    var selectedChapters by remember(manga.id, state.language, state.chapterSource(), state.chapterGroup, offlineOnly) { mutableStateOf(emptySet<String>()) }
    var selectLoading by remember { mutableStateOf(false) }
    var downloadAll by remember { mutableStateOf(false) }
    val commentCounts by model.discussion.chapterCommentCounts.collectAsState()
    BackHandler(selectedChapters.isNotEmpty()) { selectedChapters = emptySet() }
    if (downloadAll) AlertDialog(onDismissRequest = { downloadAll = false },
        title = { Text("Download all chapters?") },
        text = { Text("Downloads every available chapter in the selected source, language and group. Already saved chapters are skipped. Your account's download limit still applies.") },
        confirmButton = { TextButton(onClick = { downloadAll = false; model.downloadChapters(emptyList(), all = true) }) { Text("Download all") } },
        dismissButton = { TextButton(onClick = { downloadAll = false }) { Text("Cancel") } })
    val downloaded = state.offlineChapters.filter { it.manga.id == manga.id }
    val displayedChapters = if (offlineOnly) downloaded.map { it.chapter }.sortedByDescending { it.number.toDoubleOrNull() ?: 0.0 } else state.chapters
    if(readerSettings) ReaderSettingsDialog(state, model) { readerSettings = false }
    if(webVisible) MangaWebDialog(manga, onDismiss = { webVisible = false })
    LazyColumn(contentPadding = PaddingValues(start = 20.dp,end = 20.dp,top = 20.dp,bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Cover(manga, Modifier.width(120.dp).aspectRatio(0.7f).clip(RoundedCornerShape(18.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(manga.format.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(manga.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    manga.credits.groupBy { it.role.trim().lowercase() }.forEach { (role, credits) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if(role == "artist") Icons.Rounded.Brush else Icons.Rounded.Edit,
                                null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${credits.map { it.name }.distinct().joinToString(", ")} · ${role.replaceFirstChar { it.uppercase() }}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(when(manga.status.lowercase()) {
                            "completed" -> Icons.Rounded.CheckCircle
                            "hiatus" -> Icons.Rounded.PauseCircle
                            "cancelled" -> Icons.Rounded.Cancel
                            else -> Icons.Rounded.Schedule
                        }, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(manga.status.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { model.bookmark(manga) }, enabled = !state.account.loading && !state.syncLoading,
                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(if(saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null, Modifier.size(24.dp))
                        Text(if(saved) "Saved" else "Save", style = MaterialTheme.typography.labelMedium)
                    }
                }
                TextButton(enabled = !state.offline, onClick = { model.discussion.open(DiscussionTarget(manga.id, manga.title)) }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(24.dp))
                        Text("Comments", style = MaterialTheme.typography.labelMedium)
                    }
                }
                TextButton(onClick = { readerSettings = true }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Tune, null, Modifier.size(24.dp))
                        Text("Reader", style = MaterialTheme.typography.labelMedium)
                    }
                }
                TextButton(enabled = !state.offline, onClick = { webVisible = true }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(24.dp))
                        Text("Open on Web", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
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
                    OutlinedButton(enabled = !state.offline, onClick = { menu = true }) { Text(state.language.uppercase()); Icon(Icons.Rounded.ExpandMore, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        listOf("en" to "English", "es" to "Spanish", "pt-br" to "Portuguese", "fr" to "French", "ja" to "Japanese").forEach { (code, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; model.language(code) }) }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Downloaded chapters · ${downloaded.size}", Modifier.weight(1f))
                Switch(offlineOnly, onCheckedChange = { downloadedOnly = it }, enabled = !state.offline)
            }
            DownloadStatus(state)
        }
        if (!offlineOnly) item { ChapterFilterControls(state, model) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${if (offlineOnly) downloaded.size else state.chapterTotal} chapters", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!offlineOnly) TextButton(onClick = { downloadAll = true }, enabled = !state.downloadBusy && !state.offline) { Text("Download all") }
            }
        }
        if (selectedChapters.isNotEmpty()) stickyHeader {
            Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selectedChapters.size} selected", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = {
                        if (offlineOnly || state.offline) selectedChapters = displayedChapters.map { it.id }.toSet()
                        else {
                            selectLoading = true
                            model.allChaptersForSelection { chapters ->
                                selectedChapters = chapters.map { it.id }.toSet()
                                selectLoading = false
                            }
                        }
                    }, enabled = !selectLoading) { Text(if (selectLoading) "Loading…" else "Select all") }
                    IconButton(onClick = { selectedChapters = emptySet() }) { Icon(Icons.Rounded.Close, "Clear chapter selection") }
                }
                Row {
                    val selected = displayedChapters.filter { it.id in selectedChapters }
                    TextButton(onClick = { model.markChapters(selected, true); selectedChapters = emptySet() }) { Text("Read") }
                    TextButton(onClick = { model.markChapters(selected, false); selectedChapters = emptySet() }) { Text("Unread") }
                    TextButton(onClick = { model.downloadChapters(selected); selectedChapters = emptySet() }, enabled = !state.downloadBusy) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)); Text("Download", Modifier.padding(start = 4.dp))
                    }
                }
            }
            }
        }
        items(displayedChapters, key = { it.id }) { chapter ->
            val selected = chapter.id in selectedChapters
            val read = state.chapterRead(chapter)
            LaunchedEffect(chapter.id, state.offline) { model.checkChapterComments(chapter) }
            val commentIndicator = commentCounts[chapter.id] ?: state.chapterCommentIndicators[chapter.id]
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).combinedClickable(
                    onClick = {
                        if (selectedChapters.isEmpty()) model.read(chapter)
                        else selectedChapters = if (selected) selectedChapters - chapter.id else selectedChapters + chapter.id
                    },
                    onLongClick = { selectedChapters = selectedChapters + chapter.id },
                    onLongClickLabel = "Select chapter"),
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selectedChapters.isNotEmpty()) Icon(
                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        if (selected) "Selected" else "Not selected", Modifier.padding(end = 8.dp).size(20.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Chapter ${chapter.number}", style = MaterialTheme.typography.titleSmall,
                                color = if (read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            if (read) Icon(Icons.Rounded.Done, "Read", Modifier.padding(start = 6.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        chapterSubtitle(chapter)?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        if (chapter.group.isNotBlank()) Text(chapter.group, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(enabled = !state.offline, onClick = { model.discussion.open(DiscussionTarget(chapter.id, "${manga.title} · Chapter ${chapter.number}", chapter = true)) }) {
                        BadgedBox(badge = { commentIndicator?.takeIf { it != 0 }?.let { count ->
                            if (count < 0) Badge() else Badge { Text(if (count > 99) "99+" else "$count") }
                        } }) {
                            Icon(Icons.Rounded.ChatBubbleOutline, "Comments for chapter ${chapter.number}")
                        }
                    }
                    ChapterDownloadAction(state, model, chapter)
                }
            }
        }
        if(!offlineOnly && state.chapterLoading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        if(!offlineOnly && state.startError != null) item { Text(state.startError,color = MaterialTheme.colorScheme.error) }
        if(!offlineOnly && state.chapterError != null) item { MessageCard(Icons.Rounded.CloudOff, "Couldn't load chapters", state.chapterError) { model.loadChapters() } }
        if(!offlineOnly && !state.chapterLoading && state.chapterError == null && state.chapters.isEmpty()) item { MessageCard(Icons.AutoMirrored.Rounded.MenuBook, "No chapters in this language", "Choose another language to check available translations.") }
        if(!offlineOnly && !state.chapterLoading && state.chapterOffset < state.chapterTotal) item { OutlinedButton(onClick = { model.loadChapters(true) }, Modifier.fillMaxWidth()) { Text("Load more chapters") } }
    }
}

@Composable
private fun AppOverflowMenu(state: AppState, model: ReaderViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.MoreVert, "More options") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Settings") }, onClick = { expanded = false; model.tab("Settings") })
            DropdownMenuItem(enabled = !state.offline, text = { Text("View on Comico.moe") }, onClick = {
                expanded = false
                val username = state.account.user?.username
                val url = if(state.tab == "Profile" && !username.isNullOrBlank()) "$BASE_URL/u/${android.net.Uri.encode(username)}" else BASE_URL
                runCatching { uriHandler.openUri(url) }
            })
        }
    }
}

@Composable
private fun MangaWebDialog(manga: Manga, onDismiss: () -> Unit) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var webView by remember { mutableStateOf<android.webkit.WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose { webView?.stopLoading(); webView?.destroy() }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column {
                TopAppBar(title = { Text(manga.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close website") } })
                androidx.compose.ui.viewinterop.AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
                    android.webkit.WebView(context).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: android.webkit.WebView, request: android.webkit.WebResourceRequest): Boolean {
                                if(request.url.scheme == "https" && request.url.host == "comico.moe") return false
                                if(request.url.scheme in listOf("https", "http")) runCatching { uriHandler.openUri(request.url.toString()) }
                                return true
                            }
                        }
                        loadUrl("$BASE_URL/manga/${android.net.Uri.encode(manga.id)}")
                    }
                })
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 180)
@Composable
private fun CardPreview() { ComicoTheme { MangaCard(Manga("preview", "Your next favorite story", "", "ongoing", "manga", listOf("Adventure", "Fantasy"))) {} } }
