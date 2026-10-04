@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package moe.comico.reader

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun LibraryScreen(state: AppState, model: ReaderViewModel) {
    val options = state.libraryOptions
    val collection = state.libraryCollections.firstOrNull { it.id == options.collection }
    val titles = remember(state.library, state.libraryQuery, options, state.progress, state.history, state.offlineChapters, collection) {
        visibleLibrary(state, collection?.mangaIds)
    }
    var collectionMenu by remember { mutableStateOf(false) }
    var collectionEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LibraryCollection?>(null) }
    var addingToCollection by remember { mutableStateOf(false) }
    var selecting by remember(state.account.user?.id) { mutableStateOf(false) }
    var selected by remember(state.account.user?.id) { mutableStateOf(emptySet<String>()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(options.collection, state.account.user?.id) { selected = emptySet(); selecting = false }
    LaunchedEffect(state.library) { selected = selected.intersect(state.library.map { it.id }.toSet()) }
    val toggle: (String) -> Unit = { id -> selected = if (id in selected) selected - id else selected + id }
    val longClick: (String) -> Unit = { id -> selecting = true; toggle(id) }
    if (collectionEditor) CollectionEditor(editing, model) {
        collectionEditor = false
        if (selecting && selected.isNotEmpty()) addingToCollection = true
    }
    if (addingToCollection) AlertDialog(
        onDismissRequest = { addingToCollection = false },
        title = { Text("Add to collection") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                if (state.libraryCollections.isEmpty()) item { Text("Create a collection first. Collections are saved on this device.") }
                items(state.libraryCollections, key = { it.id }) { target ->
                    TextButton(onClick = {
                        model.updateCollectionTitles(target.id, selected, true)
                        addingToCollection = false; selecting = false; selected = emptySet()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Folder, null); Spacer(Modifier.width(12.dp))
                        Text(target.name, Modifier.weight(1f), maxLines = 2)
                    }
                }
                item { TextButton(onClick = { addingToCollection = false; editing = null; collectionEditor = true }) {
                    Icon(Icons.Rounded.Add, null); Text("New collection", Modifier.padding(start = 8.dp))
                } }
            }
        },
        confirmButton = { TextButton(onClick = { addingToCollection = false }) { Text("Cancel") } }
    )
    SyncRefreshBox(state, model) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val selectedTab = state.libraryCollections.indexOfFirst { it.id == collection?.id } + 1
                if (options.showTabs) ScrollableTabRow(selectedTabIndex = selectedTab, modifier = Modifier.weight(1f),
                    edgePadding = 16.dp, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(selected = collection == null, onClick = { model.libraryOptions(options.copy(collection = null)) },
                        text = { Text(if (options.showCounts) "All · ${state.library.size}" else "All", maxLines = 1) })
                    state.libraryCollections.forEach { item ->
                        Tab(selected = collection?.id == item.id,
                            onClick = { model.libraryOptions(options.copy(collection = item.id)) },
                            text = { Text(item.name + if (options.showCounts) " · ${state.library.count { it.id in item.mangaIds }}" else "", maxLines = 1, overflow = TextOverflow.Ellipsis) })
                    }
                }
                if (!options.showTabs) Box(Modifier.weight(1f)) {
                    TextButton(onClick = { collectionMenu = true }) { Text(collection?.name ?: "All"); Icon(Icons.Rounded.ExpandMore, null) }
                    DropdownMenu(collectionMenu, { collectionMenu = false }) {
                        DropdownMenuItem(text = { Text("All") }, onClick = { model.libraryOptions(options.copy(collection = null)); collectionMenu = false })
                        state.libraryCollections.forEach { item ->
                            DropdownMenuItem(text = { Text(item.name) }, onClick = { model.libraryOptions(options.copy(collection = item.id)); collectionMenu = false })
                        }
                    }
                }
                IconButton(onClick = { editing = null; collectionEditor = true }) { Icon(Icons.Rounded.Add, "Create collection") }
            }
            if (state.librarySearchVisible) {
                LaunchedEffect(Unit) { focus.requestFocus() }
                OutlinedTextField(state.libraryQuery, model::librarySearch,
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
                    singleLine = true, shape = MaterialTheme.shapes.extraLarge,
                    placeholder = { Text("Search your library") },
                    trailingIcon = { IconButton(onClick = {
                        if (state.libraryQuery.isNotEmpty()) model.librarySearch("") else model.toggleLibrarySearch()
                    }) { Icon(Icons.Rounded.Close, if (state.libraryQuery.isNotEmpty()) "Clear search" else "Close search") } })
            }
            if (selecting) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${selected.size} selected", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { selected = if (titles.all { it.id in selected }) emptySet() else titles.map { it.id }.toSet() }) {
                    Icon(Icons.Rounded.SelectAll, "Select all visible titles")
                }
                IconButton(onClick = { addingToCollection = true }, enabled = selected.isNotEmpty()) {
                    Icon(Icons.Rounded.CreateNewFolder, "Add selected titles to collection")
                }
                if (collection != null) IconButton(onClick = {
                    model.updateCollectionTitles(collection.id, selected, false); selected = emptySet(); selecting = false
                }, enabled = selected.isNotEmpty()) { Icon(Icons.Rounded.FolderOff, "Remove selected titles from this collection") }
            } else if (options.showCounts) Text("${titles.size} ${if (titles.size == 1) "title" else "titles"}", Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyVerticalGrid(
                columns = if (options.view == LibraryView.LIST) GridCells.Fixed(1)
                    else if (options.columns > 0) GridCells.Fixed(options.columns)
                    else GridCells.Adaptive(if (options.view == LibraryView.COMPACT) 105.dp else 145.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.syncError?.let { error -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                } }
                items(titles, key = { it.id }) { manga ->
                    LibraryTitle(manga, state.progress[manga.id], options, state.offlineChapters.count { it.manga.id == manga.id },
                        selecting, manga.id in selected,
                        onClick = { if (selecting) toggle(manga.id) else model.open(manga) },
                        onLongClick = { longClick(manga.id) },
                        onAddToCollection = { selected = setOf(manga.id); selecting = true; addingToCollection = true },
                        onContinue = { model.open(manga); state.progress[manga.id]?.let(model::read) })
                }
                if (titles.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(if (collection != null) Icons.Rounded.FolderOpen else Icons.Rounded.BookmarkBorder,
                            null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(if (state.library.isEmpty()) "Your library is empty" else if (options.filterCount > 0 || state.libraryQuery.isNotBlank()) "No matching titles" else "This collection is empty",
                            style = MaterialTheme.typography.titleMedium)
                        Text(if (state.library.isEmpty()) "Save a manga to start your library."
                            else if (options.filterCount > 0 || state.libraryQuery.isNotBlank()) "Try clearing your filters or search."
                            else "Select titles from All, then add them to this collection.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (options.filterCount > 0 || state.libraryQuery.isNotBlank()) TextButton(onClick = {
                            model.librarySearch(""); model.libraryOptions(options.copy(format = null, status = null, progress = LibraryProgress.ALL, downloadedOnly = false))
                        }) { Text("Clear filters and search") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTitle(manga: Manga, chapter: Chapter?, options: LibraryOptions, downloadCount: Int, selecting: Boolean, selected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onAddToCollection: () -> Unit, onContinue: () -> Unit) {
    val progress = chapter?.takeIf { options.showProgress }?.let { "Chapter ${it.number}" }
    Surface(shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select title")) {
        if (options.view == LibraryView.LIST) Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Cover(manga, Modifier.width(62.dp).height(88.dp).clip(MaterialTheme.shapes.small))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(manga.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(manga.format, manga.status).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                progress?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                if (options.showDownloads && downloadCount > 0) Text("$downloadCount downloaded", style = MaterialTheme.typography.labelSmall)
                if (options.showLanguage && chapter?.language?.isNotBlank() == true) Text(chapter.language.uppercase(), style = MaterialTheme.typography.labelSmall)
            }
            if (options.showContinue && chapter != null && !selecting) IconButton(onClick = onContinue) { Icon(Icons.Rounded.PlayArrow, "Continue reading ${manga.title}") }
            if (selecting) Checkbox(selected, onCheckedChange = { onClick() }, modifier = Modifier.semantics { contentDescription = "Select ${manga.title}" })
            else IconButton(onClick = onAddToCollection) { Icon(Icons.Rounded.CreateNewFolder, "Add ${manga.title} to a collection") }
        } else Column {
            Box {
                Cover(manga, Modifier.fillMaxWidth().aspectRatio(.7f))
                if (options.view == LibraryView.COMPACT) Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .9f))))
                    .padding(start = 10.dp, end = 10.dp, top = 32.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(manga.title, style = MaterialTheme.typography.titleSmall, color = Color.White,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    progress?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .85f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Column(Modifier.align(Alignment.TopStart).padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (options.showDownloads && downloadCount > 0) LibraryCoverBadge("↓ $downloadCount")
                    if (options.showLanguage && chapter?.language?.isNotBlank() == true) LibraryCoverBadge(chapter.language.uppercase())
                    if (options.view == LibraryView.COVER && progress != null) LibraryCoverBadge(progress)
                }
                Surface(Modifier.align(Alignment.TopEnd).padding(4.dp), shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = .55f), contentColor = Color.White) {
                    if (selecting) Checkbox(selected, onCheckedChange = { onClick() },
                        modifier = Modifier.semantics { contentDescription = "Select ${manga.title}" })
                    else IconButton(onClick = onAddToCollection) {
                        Icon(Icons.Rounded.CreateNewFolder, "Add ${manga.title} to a collection")
                    }
                }
                if (options.showContinue && chapter != null && !selecting) Surface(
                    Modifier.align(Alignment.BottomEnd).padding(4.dp), shape = MaterialTheme.shapes.small,
                    color = Color.Black.copy(alpha = .65f), contentColor = Color.White) {
                    IconButton(onClick = onContinue) { Icon(Icons.Rounded.PlayArrow, "Continue reading ${manga.title}") }
                }
            }
            if (options.view == LibraryView.GRID) Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(manga.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                progress?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
private fun LibraryCoverBadge(text: String) {
    Surface(color = Color.Black.copy(alpha = .65f), contentColor = Color.White, shape = MaterialTheme.shapes.extraSmall) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
