package moe.comico.reader

import android.app.Application
import coil.imageLoader
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class AppState(
    val offline: Boolean = false,
    val chapterReadMarks: Map<String, Boolean> = emptyMap(),
    val chapterCommentIndicators: Map<String, Int> = emptyMap(),
    val feed: DiscoverFeed? = null, val feedPage: DiscoverPage = DiscoverPage(),
    val discoverSections: Map<DiscoverFeed,DiscoverSection> = emptyMap(), val history: List<HistoryEntry> = emptyList(),
    val syncLoading: Boolean = false, val syncError: String? = null, val lastSync: String? = null,
    val leaderboard: List<LeaderboardEntry> = emptyList(), val leaderboardLoading: Boolean = false, val leaderboardError: String? = null,
    val publicProfile: PublicProfile? = null, val comments: List<AccountComment> = emptyList(), val profileBio: String = "", val profileSettings: ProfileEdit = ProfileEdit(), val accountDataLoading: Boolean = false, val accountDataError: String? = null,
    val tab: String = "Discover", val query: String = "", val format: String = "All",
    val catalog: List<Manga> = emptyList(), val total: Int = 0, val offset: Int = 0,
    val loading: Boolean = false, val error: String? = null,
    val selected: Manga? = null, val chapters: List<Chapter> = emptyList(), val chapterTotal: Int = 0,
    val firstChapter: Chapter? = null, val startError: String? = null,
    val chapterOffset: Int = 0, val chapterLoading: Boolean = false, val chapterError: String? = null,
    val language: String = "en", val reader: Chapter? = null,
    val chapterGroup: String? = null, val chapterRoster: List<Chapter>? = null,
    val chapterGroupsLoading: Boolean = false, val chapterGroupsError: String? = null,
    val libraryQuery: String = "", val librarySearchVisible: Boolean = false,
    val libraryOptions: LibraryOptions = LibraryOptions(),
    val libraryCollections: List<LibraryCollection> = emptyList(),
    val offlineChapters: List<OfflineChapter> = emptyList(), val downloadFolder: String? = null,
    val downloadBusy: Boolean = false, val downloadMessage: String? = null,
    val library: List<Manga> = emptyList(), val progress: Map<String, Chapter> = emptyMap(),
    val theme: String = "Website", val dynamicColor: Boolean = false, val appearance: AppearanceOptions = AppearanceOptions(),
    val readerPreferences: ReaderPreferences = ReaderPreferences(),
    val readerOverrides: Map<String, ReaderOverride> = emptyMap(),
    val mangaSources: List<ReaderSource> = emptyList(), val knownSources: List<ReaderSource> = emptyList(),
    val nativeReader: NativeReaderState = NativeReaderState(),
    val searchFilters: SearchFilters = SearchFilters(), val comickTags: List<ComickTag> = emptyList(), val tagsLoading: Boolean = false, val tagsError: String? = null, val account: AccountState = AccountState()
)
fun AppState.effectiveReaderPreferences(): ReaderPreferences = readerOverrides[selected?.id]?.resolve(readerPreferences) ?: readerPreferences

class ReaderViewModel(application: Application): AndroidViewModel(application) {
    private val accountCookies = AccountCookieJar(application)
    private val api = ComicoApi(accountCookies, networkAvailable = { !mutable.value.offline })
    val discussion = DiscussionController(api, viewModelScope, application)
    val updates = AppUpdateController(application, viewModelScope)
    private val nativeRepository = NativeReaderRepository(api)
    private val offlineStore = OfflineChapterStore(application)
    private val prefs = application.getSharedPreferences("reader", 0)
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private var accountRefreshJob: Job? = null
    private var syncJob: Job? = null
    private var progressSyncJob: Job? = null
    private var accountDataJob: Job? = null
    private var activeOwner: String? = null
    private val discoverJobs = mutableMapOf<DiscoverFeed,Job>()
    private var feedPageJob: Job? = null
    private var catalogJob: Job? = null
    private var detailJob: Job? = null
    private var chaptersJob: Job? = null
    private var chapterGroupsJob: Job? = null
    private var sourceJob: Job? = null
    private var readerJob: Job? = null
    private var imageCacheJob: Job? = null
    private var localZipEntry: OfflineChapter? = null
    private val commentChecks = kotlinx.coroutines.sync.Semaphore(2)
    suspend fun checkChapterComments(chapter: Chapter) {
        if (mutable.value.offline || chapter.id.startsWith("local-") || chapter.id in mutable.value.chapterCommentIndicators) return
        commentChecks.acquire()
        try {
            if (mutable.value.offline) return
            val result = api.get("/api/comments", mapOf("chapterId" to chapter.id, "sort" to "newest", "limit" to "1"))
            mutable.update { it.copy(chapterCommentIndicators = it.chapterCommentIndicators + (chapter.id to commentIndicator(result))) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* A missing badge must not prevent reading a chapter. */ }
        finally { commentChecks.release() }
    }
    private val connectivity = application.getSystemService(android.net.ConnectivityManager::class.java)
    private val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onLost(network: android.net.Network) { setConnected(false) }
        override fun onCapabilitiesChanged(network: android.net.Network, capabilities: android.net.NetworkCapabilities) {
            setConnected(capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        }
    }
    private fun setConnected(connected: Boolean) {
        viewModelScope.launch {
            val changed = mutable.value.offline == connected
            mutable.update { it.copy(offline = !connected) }
            if (!connected) {
                discoverJobs.values.forEach { it.cancel() }
                imageCacheJob?.cancel()
                discussion.close()
                if (mutable.value.tab in listOf("Discover", "Search", "Feed", "Leaderboard")) mutable.update { it.copy(tab = "Library") }
                listOf(accountRefreshJob, syncJob, progressSyncJob, accountDataJob, feedPageJob, catalogJob, detailJob, chaptersJob, chapterGroupsJob, sourceJob).forEach { it?.cancel() }
                mutable.update { it.copy(loading = false, error = null, syncLoading = false, syncError = null,
                    chapterLoading = false, chapterError = null, chapterGroupsLoading = false, chapterGroupsError = null,
                    accountDataLoading = false, accountDataError = null, leaderboardLoading = false, leaderboardError = null,
                    account = it.account.copy(loading = false, error = null),
                    discoverSections = it.discoverSections.mapValues { (_, section) -> section.copy(loading = false, error = null) },
                    feedPage = it.feedPage.copy(loading = false, error = null)) }
                if (mutable.value.reader != null && mutable.value.offlineChapters.any { it.chapter.id == mutable.value.reader?.id })
                    loadReader()
            } else if (changed) {
                refreshAccount()
                tab(mutable.value.tab)
            }
        }
    }
    override fun onCleared() {
        connectivity.unregisterNetworkCallback(networkCallback)
        super.onCleared()
    }
    init {
        val saved = runCatching { JSONArray(prefs.getString("library", "[]")).objects().map { it.manga() } }.getOrDefault(emptyList())
        val progress = runCatching { JSONObject(prefs.getString("progress", "{}")!!).let { obj -> obj.keys().asSequence().associateWith { obj.getJSONObject(it).chapter() } } }.getOrDefault(emptyMap())
        mutable.update { it.copy(library = saved, progress = progress, theme = prefs.getString("theme", "Website")!!, dynamicColor = prefs.getBoolean("dynamic", false)) }
        val readerPreferences = runCatching { JSONObject(prefs.getString("readerGlobal", "{}")!!).readerPreferences() }.getOrDefault(ReaderPreferences())
        val overrides = runCatching { JSONObject(prefs.getString("readerOverrides", "{}")!!).let { obj -> obj.keys().asSequence().associateWith { obj.getJSONObject(it).readerOverride() } } }.getOrDefault(emptyMap())
        val knownSources = runCatching { JSONArray(prefs.getString("readerKnownSources", "[]")).objects().map { ReaderSource(it.getString("id"),it.getString("name")) } }.getOrDefault(emptyList())
        mutable.update { it.copy(readerPreferences = readerPreferences, readerOverrides = overrides, knownSources = knownSources) }
        val searchFilters = runCatching { JSONObject(prefs.getString("searchFilters", "{}")!!).searchFilters() }.getOrDefault(SearchFilters())
        mutable.update { it.copy(searchFilters = searchFilters, format = searchFilters.type?.apiValue?.replaceFirstChar { c -> c.uppercase() } ?: "All") }
        mutable.update { it.copy(appearance = AppearanceOptions(
            palette = prefs.getString("palette", if(it.dynamicColor) "Dynamic" else "Default")!!,
            pureBlack = prefs.getBoolean("pureBlack", false),
            dateFormat = prefs.getString("dateFormat", "M/d/yy")!!,
            relativeDates = prefs.getBoolean("relativeDates", false),
            alwaysShowNavLabels = prefs.getBoolean("alwaysShowNavLabels", true)
        )) }
        loadOwner(prefs.getString("localLibraryOwner", null))
        mutable.update { it.copy(offline = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) != true) }
        if (mutable.value.offline) mutable.update { it.copy(tab = "Library") }
        connectivity.registerDefaultNetworkCallback(networkCallback)
        loadDiscover()
        refreshAccount()
    }
    fun tab(value: String) {
        if (mutable.value.offline && value in listOf("Search", "Feed", "Leaderboard")) return
        val reset = value == "Discover" && mutable.value.query.isNotEmpty()
        mutable.update { it.copy(tab = value, query = if(reset) "" else it.query) }
        if(value == "Discover") loadDiscover()
        if(value == "Search") loadCatalog()
        if(value == "Library" || value == "History") syncAccount()
        if(value == "Leaderboard") loadLeaderboard()
    }
    fun useDefaultDownloadFolder() {
        if (mutable.value.downloadBusy) return
        offlineStore.resetFolder(activeOwner)
        mutable.update { it.copy(downloadFolder = null, downloadMessage = "New downloads will use app storage.") }
    }
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun storageUsage(): StorageUsage {
        val entries = mutable.value.offlineChapters
        val app = getApplication<Application>()
        return withContext(Dispatchers.IO) {
            var bytes = 0L
            var unknown = 0
            entries.forEach { entry ->
                val uri = android.net.Uri.parse(entry.archive)
                val size = runCatching {
                    if (uri.scheme == "file") java.io.File(uri.path!!).takeIf { it.exists() }?.length()
                    else app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it >= 0 } else null
                    }
                }.getOrNull()
                if (size == null) unknown++ else bytes += size
            }
            StorageUsage(bytes, chapterCacheFiles(app.cacheDir).sumOf { storageBytes(it) },
                app.imageLoader.diskCache?.size ?: 0L, android.os.StatFs(app.filesDir.absolutePath).availableBytes, unknown)
        }
    }
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun clearStorageCache(previews: Boolean) {
        check(!mutable.value.downloadBusy && mutable.value.reader == null) { "Finish downloading or close the reader before clearing its cache." }
        val app = getApplication<Application>()
        withContext(Dispatchers.IO) {
            if (previews) {
                app.imageLoader.memoryCache?.clear()
                app.imageLoader.diskCache?.clear()
            } else clearChapterFiles(app.cacheDir)
        }
    }
    fun chooseDownloadFolder(uri: android.net.Uri): Boolean {
        return runCatching { offlineStore.chooseFolder(activeOwner, uri) }.onSuccess {
            mutable.update { it.copy(downloadFolder = offlineStore.folder(activeOwner), downloadMessage = "Download folder saved.") }
        }.onFailure { error -> mutable.update { it.copy(downloadMessage = error.message) } }.isSuccess
    }
    fun downloadChapter(chapter: Chapter, importedZip: android.net.Uri? = null) =
        downloadChapters(listOf(chapter), importedZip = importedZip)

    fun allChaptersForSelection(onLoaded: (List<Chapter>) -> Unit) {
        val before = mutable.value
        val manga = before.selected ?: return
        viewModelScope.launch {
            try {
                val entries = filterChapterGroup(before.chapterRoster ?: api.chapterRoster(manga.id, before.language, before.chapterSource()), before.chapterGroup)
                if (mutable.value.selected?.id == manga.id && mutable.value.language == before.language &&
                    mutable.value.chapterSource() == before.chapterSource() && mutable.value.chapterGroup == before.chapterGroup) {
                    mutable.update { it.copy(chapters = entries, chapterRoster = before.chapterRoster ?: entries.takeIf { before.chapterGroup == null },
                        chapterTotal = entries.size, chapterOffset = entries.size) }
                    onLoaded(entries)
                } else onLoaded(emptyList())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(downloadMessage = e.message ?: "Couldn't load all chapters.") }; onLoaded(emptyList()) }
        }
    }

    fun markChapters(chapters: List<Chapter>, read: Boolean) {
        val marks = mutable.value.chapterReadMarks + chapters.associate { it.id to read }
        mutable.update { it.copy(chapterReadMarks = marks) }
        prefs.edit().putString(ownerKey("chapterReadMarks"), JSONObject(marks).toString()).apply()
    }

    fun downloadChapters(chapters: List<Chapter>, all: Boolean = false, importedZip: android.net.Uri? = null) {
        val before = mutable.value
        val manga = before.selected ?: return
        if (before.downloadBusy) return
        val owner = activeOwner
        val message = when {
            importedZip == null && before.offline -> "Connect to the internet to download chapters."
            importedZip == null && before.account.user == null -> "Sign in to download chapters."
            else -> null
        }
        if (message != null) { mutable.update { it.copy(downloadMessage = message) }; return }
        mutable.update { it.copy(downloadBusy = true, downloadMessage = "Preparing downloads…") }
        viewModelScope.launch {
            var completed = 0
            var skipped = 0
            try {
                val entries = if (all) filterChapterGroup(
                    before.chapterRoster ?: api.chapterRoster(manga.id, before.language, before.chapterSource()), before.chapterGroup
                ) else chapters
                val queue = chapterDownloadQueue(entries, before.offlineChapters)
                skipped = entries.size - queue.size
                for ((index, chapter) in queue.withIndex()) {
                    if (activeOwner != owner) throw CancellationException("Account changed.")
                    mutable.update { it.copy(downloadMessage = "Downloading ${index + 1}/${queue.size} · Chapter ${chapter.number}") }
                    val file = withContext(Dispatchers.IO) { java.io.File.createTempFile("chapter-", ".zip", getApplication<Application>().cacheDir) }
                    try {
                        if (importedZip == null) api.downloadChapter(chapter.id, file)
                        else withContext(Dispatchers.IO) {
                            getApplication<Application>().contentResolver.openInputStream(importedZip)?.use { input ->
                                file.outputStream().use { copyChapterBytes(input, it) }
                            } ?: throw java.io.IOException("Couldn't open the selected ZIP.")
                        }
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        if (activeOwner != owner) throw CancellationException("Account changed.")
                        val source = before.mangaSources.firstOrNull { it.id == chapter.sourceId }?.name ?: chapter.sourceId
                        offlineStore.save(owner, manga, chapter, source, file)
                        completed++
                        mutable.update { if (activeOwner == owner) it.copy(offlineChapters = offlineStore.entries(owner)) else it }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        if (importedZip == null && e.message?.startsWith("Comico already marks") == true) skipped++ else throw e
                    } finally { file.delete() }
                }
                mutable.update { it.copy(downloadMessage = "$completed chapters saved · $skipped skipped.") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(downloadMessage = "$completed chapters saved. ${e.message ?: "Download stopped."}") } }
            finally { mutable.update { it.copy(downloadBusy = false) } }
        }
    }
    fun openOfflineZip(uri: android.net.Uri) {
        readerJob?.cancel()
        readerJob = viewModelScope.launch {
            try {
                val entry = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: "Downloaded chapter"
                    val id = java.util.UUID.nameUUIDFromBytes(uri.toString().toByteArray()).toString()
                    OfflineChapter(Manga("local-$id", name.removeSuffix(".zip"), "", "", "", emptyList()),
                        Chapter("local-$id", "Offline", "", "", "", ""), uri.toString(), "Imported ZIP")
                }
                detailJob?.cancel(); chaptersJob?.cancel(); sourceJob?.cancel(); chapterGroupsJob?.cancel()
                localZipEntry = entry
                mutable.update { it.copy(selected = entry.manga, reader = entry.chapter,
                    nativeReader = NativeReaderState(loading = true)) }
                val session = offlineStore.session(entry)
                mutable.update { it.copy(nativeReader = NativeReaderState(session = session, notice = "Offline chapter")) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(downloadMessage = e.message ?: "Couldn't open this ZIP.",
                nativeReader = it.nativeReader.copy(loading = false, error = e.message)) } }
        }
    }
    fun openOfflineChapter(entry: OfflineChapter) {
        detailJob?.cancel(); chaptersJob?.cancel(); sourceJob?.cancel(); readerJob?.cancel(); chapterGroupsJob?.cancel()
        mutable.update { it.copy(selected = entry.manga, chapters = it.offlineChapters.filter { saved -> saved.manga.id == entry.manga.id }.map { saved -> saved.chapter }, chapterLoading = false) }
        read(entry.chapter)
    }
    fun removeOfflineChapter(entry: OfflineChapter) {
        val owner = activeOwner
        viewModelScope.launch {
            try {
                offlineStore.delete(owner, entry)
                mutable.update { if (activeOwner == owner) it.copy(offlineChapters = offlineStore.entries(owner), downloadMessage = "Download removed.") else it }
            } catch (e: Exception) { mutable.update { it.copy(downloadMessage = e.message ?: "Couldn't delete this download.") } }
        }
    }
    fun libraryOptions(value: LibraryOptions) {
        mutable.update { it.copy(libraryOptions = value) }
        prefs.edit().putString(ownerKey("libraryOptions"), value.toJson().toString()).apply()
    }
    fun saveCollection(name: String, id: String? = null): String? {
        val before = mutable.value
        collectionNameError(name, before.libraryCollections, id)?.let { return it }
        val collection = if (id == null) LibraryCollection(java.util.UUID.randomUUID().toString(), name.trim())
            else before.libraryCollections.firstOrNull { it.id == id }?.copy(name = name.trim()) ?: return "Collection no longer exists."
        mutable.update { it.copy(libraryCollections = if (id == null) it.libraryCollections + collection
            else it.libraryCollections.map { old -> if (old.id == id) collection else old }) }
        persistCollections()
        return null
    }
    fun moveCollection(id: String, direction: Int) {
        mutable.update { it.copy(libraryCollections = moveLibraryCollection(it.libraryCollections, id, direction)) }
        persistCollections()
    }
    fun deleteCollection(id: String) {
        mutable.update { it.copy(libraryCollections = it.libraryCollections.filterNot { c -> c.id == id }) }
        if (mutable.value.libraryOptions.collection == id) libraryOptions(mutable.value.libraryOptions.copy(collection = null))
        persistCollections()
    }
    fun updateCollectionTitles(collectionId: String, mangaIds: Set<String>, add: Boolean) {
        val ids = mangaIds.intersect(libraryTitles(mutable.value).map { it.id }.toSet())
        mutable.update { it.copy(libraryCollections = it.libraryCollections.map { collection ->
            if (collection.id != collectionId) collection else collection.copy(
                mangaIds = if (add) collection.mangaIds + ids else collection.mangaIds - ids)
        }) }
        persistCollections()
    }
    private fun persistCollections() {
        prefs.edit().putString(ownerKey("libraryCollections"), JSONArray(mutable.value.libraryCollections.map { it.toJson() }).toString()).apply()
    }
    fun librarySearch(value: String) { mutable.update { it.copy(libraryQuery = value) } }
    fun toggleLibrarySearch() { mutable.update { it.copy(librarySearchVisible = !it.librarySearchVisible,libraryQuery = if(it.librarySearchVisible) "" else it.libraryQuery) } }
    fun search(value: String) { mutable.update { it.copy(query = value) }; loadCatalog(debounce = true) }
    fun format(value: String) { applySearchFilters(mutable.value.searchFilters.copy(type = MangaType.entries.find { it.apiValue.equals(value,ignoreCase = true) })) }
    fun loadCatalog(more: Boolean = false, debounce: Boolean = false) {
        if (mutable.value.offline) return
        if(mutable.value.tab == "Discover") { loadDiscover(refresh = true);return }
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            val before = mutable.value
            val offset = if(more) before.offset else 0
            mutable.update { it.copy(loading = true, error = null, catalog = if(more) it.catalog else emptyList()) }
            try {
                if(debounce) delay(400)
                val result = api.catalog(before.query, offset, before.searchFilters)
                mutable.update { it.copy(catalog = (if(more) it.catalog else emptyList()).plus(result.items).distinctBy { m -> m.id }, total = result.total, offset = offset + 30, loading = false) }
            } catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(loading = false, error = e.message ?: "Could not connect to Comico.") } }
        }
    }
    fun open(manga: Manga) {
        detailJob?.cancel(); chaptersJob?.cancel(); sourceJob?.cancel(); readerJob?.cancel(); chapterGroupsJob?.cancel()
        mutable.update { it.copy(selected = manga, reader = null, chapters = emptyList(), chapterTotal = 0, chapterOffset = 0, chapterError = null, chapterGroup = null, chapterRoster = null, chapterGroupsLoading = false, chapterGroupsError = null, mangaSources = emptyList(), chapterLoading = true, firstChapter = null, startError = null) }
        if (mutable.value.offline) {
            val downloaded = mutable.value.offlineChapters.filter { it.manga.id == manga.id }.map { it.chapter }
            mutable.update { it.copy(chapters = downloaded, chapterTotal = downloaded.size, chapterLoading = false, firstChapter = downloaded.minByOrNull { c -> c.number.toDoubleOrNull() ?: 0.0 }) }
            return
        }
        detailJob = viewModelScope.launch {
            try { val detail = api.detail(manga.id); mutable.update { if(it.selected?.id == manga.id) it.copy(selected = detail) else it } }
            catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(chapterError = e.message) } }
        }
        sourceJob = viewModelScope.launch {
            try {
                val sources = api.mangaSources(manga.id)
                mutable.update { it.copy(mangaSources = sources) }
                rememberSources(sources)
            } catch(e: CancellationException) { throw e } catch(_: Exception) { /* Chapter browsing can still use Comico's default source. */ }
            loadChapters()
        }
    }
    private fun resetChapterGroups() {
        chapterGroupsJob?.cancel()
        mutable.update { it.copy(chapterGroup = null, chapterRoster = null, chapterGroupsLoading = false, chapterGroupsError = null) }
    }
    fun language(value: String) { resetChapterGroups(); mutable.update { it.copy(language = value) }; loadChapters() }
    fun chapterSource(value: String?) {
        val before = mutable.value
        val id = before.selected?.id ?: return
        resetChapterGroups()
        setMangaReader((before.readerOverrides[id] ?: ReaderOverride()).copy(sourceOverride = true, source = value))
        if(before.effectiveReaderPreferences().source == value) loadChapters()
    }
    fun chapterGroup(value: String?) {
        if(value != null && mutable.value.chapterRoster == null) return
        val state = mutable.value
        val id = state.selected?.id ?: return
        setMangaReader((state.readerOverrides[id] ?: ReaderOverride()).withGroup(state.chapterSource(), state.language, value))
        mutable.update { it.copy(chapterGroup = value) }
        loadChapters()
    }
    fun loadChapterGroups() {
        if (mutable.value.offline) return
        val before = mutable.value
        val manga = before.selected ?: return
        if(before.chapterGroupsLoading || before.chapterRoster != null) return
        mutable.update { it.copy(chapterGroupsLoading = true, chapterGroupsError = null) }
        chapterGroupsJob = viewModelScope.launch {
            try {
                val roster = api.chapterRoster(manga.id, before.language, before.chapterSource())
                mutable.update { it.copy(chapterRoster = roster, chapterGroupsLoading = false) }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(chapterGroupsLoading = false, chapterGroupsError = e.message ?: "Could not load groups.") } }
        }
    }
    fun loadChapters(more: Boolean = false) {
        if (mutable.value.offline) return
        chaptersJob?.cancel()
        chaptersJob = viewModelScope.launch {
            val current = mutable.value; val manga = current.selected ?: return@launch
            val savedGroup = current.readerOverrides[manga.id]?.groupFor(current.chapterSource(), current.language)
            val before = current.copy(chapterGroup = savedGroup)
            mutable.update { it.copy(chapterGroup = savedGroup) }
            val offset = if(more) before.chapterOffset else 0
            mutable.update { it.copy(chapterLoading = true, chapterError = null, firstChapter = if(more) it.firstChapter else null, startError = null, chapters = if(more) it.chapters else emptyList()) }
            try {
                val source = before.chapterSource()
                val fullRoster = before.chapterRoster ?: if (before.chapterGroup != null) {
                    api.chapterRoster(manga.id, before.language, source).also { roster ->
                        mutable.update { it.copy(chapterRoster = roster) }
                    }
                } else null
                val roster = fullRoster?.let { filterChapterGroup(it, before.chapterGroup) }
                val result = if(roster != null) PageResult(roster.drop(offset).take(50), roster.size)
                    else api.chapters(manga.id, before.language, offset, source)
                if(!more) {
                    try { val first = if(roster != null) roster.lastOrNull() else api.firstChapter(manga.id,before.language,source);mutable.update { it.copy(firstChapter = first) } }
                    catch(e: CancellationException) { throw e }
                    catch(_: Exception) { mutable.update { it.copy(startError = "Couldn't find the first chapter. Refresh the chapter list to try again.") } }
                }
                mutable.update { it.copy(chapters = (if(more) it.chapters else emptyList()).plus(result.items).distinctBy { c -> c.id }, chapterTotal = result.total, chapterOffset = offset + 50, chapterLoading = false) }
            } catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(chapterLoading = false, chapterError = e.message) } }
        }
    }
    fun back() {
        if(mutable.value.reader != null) { readerJob?.cancel(); mutable.update { it.copy(reader = null, nativeReader = NativeReaderState()) };progressSyncJob?.cancel();syncAccount() }
        else { detailJob?.cancel(); chaptersJob?.cancel(); sourceJob?.cancel(); chapterGroupsJob?.cancel(); mutable.update { it.copy(selected = null, chapterLoading = false) } }
    }
    fun read(chapter: Chapter) = openReader(chapter,false)
    fun startReading() { mutable.value.firstChapter?.let { openReader(it,true) } }
    private fun openReader(chapter: Chapter, fromBeginning: Boolean) {
        val id = mutable.value.selected?.id ?: return
        mutable.update { it.copy(reader = chapter, progress = it.progress + (id to chapter), nativeReader = NativeReaderState(page = if(fromBeginning) 0 else prefs.getInt(ownerKey("readerPage:${chapter.id}"),0))) }
        val data = JSONObject(); mutable.value.progress.forEach { (key, c) -> data.put(key, JSONObject().put("id",c.id).put("number",c.number).put("title",c.title).put("language",c.language).put("scanlationGroup",c.group).put("externalUrl",c.external)) }
        prefs.edit().putString(ownerKey("progress"), data.toString()).apply()
        if(fromBeginning) prefs.edit().putInt(ownerKey("readerPage:${chapter.id}"),0).apply()
        recordHistory(chapter,if(fromBeginning) 0 else mutable.value.nativeReader.page,0)
        loadReader()
    }
    fun bookmark(manga: Manga) {
        if(mutable.value.account.loading) return
        val saved = mutable.value.library.none { it.id == manga.id }
        mutable.update { it.copy(library = if(saved) it.library + manga else it.library.filterNot { m -> m.id == manga.id }) }
        persistLibrary()
        activeOwner?.let {
            val queue = pending("bookmarks").put(manga.id,saved)
            prefs.edit().putString(ownerKey("pending:bookmarks"),queue.toString()).apply()
            syncAccount()
        }
    }
    fun theme(value: String) { mutable.update { it.copy(theme = value) }; prefs.edit().putString("theme",value).apply() }
    fun appearance(value: AppearanceOptions) {
        mutable.update { it.copy(appearance = value, dynamicColor = value.palette == "Dynamic") }
        prefs.edit().putString("palette", value.palette).putBoolean("pureBlack", value.pureBlack)
            .putString("dateFormat", value.dateFormat).putBoolean("relativeDates", value.relativeDates)
            .putBoolean("alwaysShowNavLabels", value.alwaysShowNavLabels)
            .putBoolean("dynamic", value.palette == "Dynamic").apply()
    }
    fun dynamic(value: Boolean) { mutable.update { it.copy(dynamicColor = value) }; prefs.edit().putBoolean("dynamic",value).apply() }

    private fun rememberSources(sources: List<ReaderSource>) {
        mutable.update { it.copy(knownSources = (it.knownSources + sources).associateBy { source -> source.id }.values.sortedBy { source -> source.name }) }
        prefs.edit().putString("readerKnownSources", JSONArray(mutable.value.knownSources.map { JSONObject().put("id",it.id).put("name",it.name) }).toString()).apply()
    }
    fun setGlobalReader(preferences: ReaderPreferences) {
        val old = mutable.value.effectiveReaderPreferences()
        mutable.update { it.copy(readerPreferences = preferences) }
        prefs.edit().putString("readerGlobal",preferences.toJson().toString()).apply()
        applyReaderPreferenceChanges(old)
    }
    fun setMangaReader(override: ReaderOverride) {
        val id = mutable.value.selected?.id ?: return
        val old = mutable.value.effectiveReaderPreferences()
        mutable.update { it.copy(readerOverrides = if(override.isEmpty()) it.readerOverrides - id else it.readerOverrides + (id to override)) }
        val data = JSONObject(); mutable.value.readerOverrides.forEach { (id, value) -> data.put(id,value.toJson()) }
        prefs.edit().putString("readerOverrides",data.toString()).apply()
        applyReaderPreferenceChanges(old)
    }
    private fun applyReaderPreferenceChanges(old: ReaderPreferences) {
        val current = mutable.value.effectiveReaderPreferences()
        if(old.source != current.source && mutable.value.selected != null) { resetChapterGroups(); loadChapters() }
        if((old.source != current.source || old.proxy != current.proxy) && mutable.value.reader != null) loadReader()
    }
    fun loadReader() {
        readerJob?.cancel()
        readerJob = viewModelScope.launch {
            val chapter = mutable.value.reader ?: return@launch
            mutable.update { it.copy(nativeReader = it.nativeReader.copy(loading = true, session = null, error = null, notice = null)) }
            try {
                val offline = localZipEntry?.takeIf { it.chapter.id == chapter.id }
                    ?: mutable.value.offlineChapters.firstOrNull { it.chapter.id == chapter.id }
                if (offline != null) {
                    val session = offlineStore.session(offline)
                    val neighbors = mutable.value.offlineChapters.filter { it.manga.id == offline.manga.id && it.chapter.language == chapter.language && it.chapter.sourceId == chapter.sourceId }
                        .map { it.chapter }.sortedBy { it.number.toDoubleOrNull() ?: 0.0 }
                    val index = neighbors.indexOfFirst { it.id == chapter.id }
                    mutable.update { it.copy(nativeReader = it.nativeReader.copy(loading = false, session = session, notice = "Offline chapter",
                        previous = neighbors.getOrNull(index - 1), next = neighbors.getOrNull(index + 1),
                        page = it.nativeReader.page.coerceIn(0, session.pages.lastIndex))) }
                    recordHistory(chapter, mutable.value.nativeReader.page, session.pages.size)
                    return@launch
                }
                if (mutable.value.offline) {
                    mutable.update { it.copy(nativeReader = it.nativeReader.copy(loading = false, error = "This chapter isn't downloaded. Connect to the internet to download it first.")) }
                    return@launch
                }
                try {
                    val context = api.get("/api/chapters/${chapter.id}")
                    mutable.update { it.copy(nativeReader = it.nativeReader.copy(previous = context.optJSONObject("previous")?.chapter(), next = context.optJSONObject("next")?.chapter())) }
                } catch(e: CancellationException) { throw e } catch(_: Exception) { /* Image reading remains available if chapter navigation fails. */ }
                val sources = api.readerSources(chapter.id)
                rememberSources(sources)
                mutable.update { it.copy(nativeReader = it.nativeReader.copy(sources = sources)) }
                if(sources.none { it.readable } && chapter.external.isNotBlank()) {
                    mutable.update { it.copy(nativeReader = it.nativeReader.copy(loading = false)) }
                    return@launch
                }
                val preferences = mutable.value.effectiveReaderPreferences()
                val preferred = sources.firstOrNull { it.id == preferences.source && it.readable }
                val notice = if(preferences.source != null && preferred == null) "Your preferred source is unavailable for this chapter. Using Auto." else null
                val session = nativeRepository.open(chapter.id, preferred, preferences.proxy)
                mutable.update { it.copy(nativeReader = it.nativeReader.copy(loading = false, session = session, notice = notice, page = it.nativeReader.page.coerceIn(0,session.pages.lastIndex))) }
                recordHistory(chapter,mutable.value.nativeReader.page,session.pages.size)
                cacheOpenedChapter(chapter, session)
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(nativeReader = it.nativeReader.copy(loading = false, error = e.message ?: "Couldn't load this chapter. Try another source or proxy method.")) } }
        }
    }
    private fun cacheOpenedChapter(chapter: Chapter, session: ReaderSession) {
        val manga = mutable.value.selected ?: return
        val owner = activeOwner
        imageCacheJob?.cancel()
        imageCacheJob = viewModelScope.launch {
            val archive = java.io.File(getApplication<Application>().cacheDir, "chapter-${java.util.UUID.randomUUID()}.zip")
            try {
                api.cacheReaderImages(session, archive)
                if (activeOwner != owner || mutable.value.offline) return@launch
                offlineStore.save(owner, manga, chapter, session.source, archive)
                mutable.update { if (activeOwner == owner) it.copy(offlineChapters = offlineStore.entries(owner),
                    nativeReader = if (it.reader?.id == chapter.id) it.nativeReader.copy(notice = "Saved for offline reading") else it.nativeReader) else it }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Reading stays available if there is not enough storage or an image cannot be cached. */ }
            finally { archive.delete() }
        }
    }
    fun readerPage(page: Int) {
        val chapter = mutable.value.reader ?: return
        val count = mutable.value.nativeReader.session?.pages?.size ?: return
        val value = page.coerceIn(0,count - 1)
        if(value == mutable.value.nativeReader.page) return
        mutable.update { it.copy(nativeReader = it.nativeReader.copy(page = value)) }
        prefs.edit().putInt(ownerKey("readerPage:${chapter.id}"),value).apply()
        recordHistory(chapter,value,count)
    }

    fun applySearchFilters(filters: SearchFilters) {
        mutable.update { it.copy(searchFilters = filters,format = filters.type?.apiValue?.replaceFirstChar { c -> c.uppercase() } ?: "All") }
        prefs.edit().putString("searchFilters",filters.toJson().toString()).apply()
        loadCatalog()
    }
    fun loadComickTags() {
        if (mutable.value.offline) return
        if(mutable.value.tagsLoading || mutable.value.comickTags.isNotEmpty()) return
        viewModelScope.launch {
            mutable.update { it.copy(tagsLoading = true,tagsError = null) }
            try { val tags = api.comickTags();mutable.update { it.copy(comickTags = tags,tagsLoading = false) } }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(tagsLoading = false,tagsError = e.message ?: "Couldn't load Comick tags.") } }
        }
    }

    fun refreshAccount() {
        if (mutable.value.offline) return
        if(mutable.value.account.loading || mutable.value.syncLoading) return
        accountRefreshJob = viewModelScope.launch {
            mutable.update { it.copy(account = it.account.copy(loading = true,error = null)) }
            try { val user = api.account();mutable.update { it.copy(account = AccountState(user = user)) }; activateAccount(user) }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(account = it.account.copy(loading = false,error = e.message)) } }
        }
    }
    fun signIn(identifier: String, password: String) {
        if (mutable.value.offline) return
        if(mutable.value.account.loading || mutable.value.syncLoading) return
        viewModelScope.launch {
            mutable.update { it.copy(account = AccountState(loading = true)) }
            try { val user = api.signIn(identifier,password);mutable.update { it.copy(account = AccountState(user = user,message = "Signed in.")) }; activateAccount(user) }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(account = AccountState(error = e.message ?: "Couldn't sign in.")) } }
        }
    }
    fun register(username: String, name: String, email: String, password: String) {
        if (mutable.value.offline) return
        if(mutable.value.account.loading || mutable.value.syncLoading) return
        viewModelScope.launch {
            mutable.update { it.copy(account = AccountState(loading = true)) }
            try { api.register(username,name,email,password);mutable.update { it.copy(account = AccountState(message = "Account created. Check your email to verify it, then sign in.")) } }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(account = AccountState(error = e.message ?: "Couldn't create your account.")) } }
        }
    }
    fun signOut() {
        if(mutable.value.account.loading || mutable.value.syncLoading || mutable.value.accountDataLoading) return
        viewModelScope.launch {
            mutable.update { it.copy(account = it.account.copy(loading = true,error = null,message = null)) }
            var message = "Signed out."
            try { api.signOut() }
            catch(e: CancellationException) { throw e }
            catch(_: Exception) { message = "Signed out on this device. Comico could not confirm server-side sign-out." }
            finally { accountCookies.clear() }
            mutable.update { it.copy(account = AccountState(message = message)) }
            activateAccount(null)
        }
    }
    private fun ownerKey(key: String) = accountStorageKey(key,activeOwner)
    private fun pending(kind: String) = runCatching { JSONObject(prefs.getString(ownerKey("pending:$kind"),"{}")!!) }.getOrDefault(JSONObject())
    private fun persistLibrary() { prefs.edit().putString(ownerKey("library"),JSONArray(mutable.value.library.map { it.toJson() }).toString()).apply() }
    private fun persistHistory() { prefs.edit().putString(ownerKey("history"),JSONArray(mutable.value.history.map { it.toJson() }).toString()).apply() }
    private fun loadOwner(owner: String?) {
        discoverJobs.filterKeys { it.accountOnly }.values.forEach { it.cancel() }
        activeOwner = owner
        val marks = runCatching { JSONObject(prefs.getString(ownerKey("chapterReadMarks"), "{}")!!).let { obj ->
            obj.keys().asSequence().associateWith { obj.getBoolean(it) }
        } }.getOrDefault(emptyMap())
        mutable.update { it.copy(chapterReadMarks = marks) }
        mutable.update { it.copy(offlineChapters = offlineStore.entries(owner), downloadFolder = offlineStore.folder(activeOwner), downloadMessage = null) }
        val library = runCatching { JSONArray(prefs.getString(ownerKey("library"),"[]")).objects().map { it.manga() } }.getOrDefault(emptyList())
        val collections = runCatching { JSONArray(prefs.getString(ownerKey("libraryCollections"), "[]")).objects().map { it.libraryCollection() } }.getOrDefault(emptyList())
        val options = runCatching { JSONObject(prefs.getString(ownerKey("libraryOptions"), "{}")!!).libraryOptions() }.getOrDefault(LibraryOptions())
        mutable.update { it.copy(libraryCollections = collections, libraryOptions = options.copy(collection = options.collection?.takeIf { id -> collections.any { it.id == id } })) }
        val history = runCatching { JSONArray(prefs.getString(ownerKey("history"),"[]")).objects().map { it.historyEntry() }.filterNot { it.isLocalChapter() } }.getOrDefault(emptyList())
        val progress = runCatching { JSONObject(prefs.getString(ownerKey("progress"),"{}")!!).let { obj -> obj.keys().asSequence().associateWith { obj.getJSONObject(it).chapter() } } }.getOrDefault(emptyMap())
        mutable.update { it.copy(library = library,libraryQuery = "",librarySearchVisible = false,history = history,progress = progress.filterNot { (id, chapter) -> id.startsWith("local-") || chapter.id.startsWith("local-") },syncError = null,lastSync = null,publicProfile = null,comments = emptyList(),profileBio = "",profileSettings = ProfileEdit(),accountDataError = null,discoverSections = it.discoverSections.filterKeys { feed -> !feed.accountOnly }) }
    }
    private fun activateAccount(user: AccountUser?) {
        imageCacheJob?.cancel()
        prefs.edit().apply { if (user == null) remove("localLibraryOwner") else putString("localLibraryOwner", user.id) }.apply()
        progressSyncJob?.cancel();accountDataJob?.cancel()
        if(activeOwner != user?.id) loadOwner(user?.id)
        if(user != null) { syncAccount();loadAccountData() }
        if(mutable.value.tab == "Discover") loadDiscover()
    }
    fun openFeed(feed: DiscoverFeed) {
        feedPageJob?.cancel()
        mutable.update { it.copy(tab = "Feed", feed = feed, feedPage = DiscoverPage()) }
        loadFeedPage()
    }
    fun loadFeedPage(more: Boolean = false) {
        if (mutable.value.offline) return
        val before = mutable.value
        val feed = before.feed ?: return
        if(more && (before.feedPage.loading || !before.feedPage.hasMore)) return
        feedPageJob?.cancel()
        feedPageJob = viewModelScope.launch {
            val offset = if(more) before.feedPage.offset else 0
            mutable.update { it.copy(feedPage = if(more) it.feedPage.copy(loading = true, error = null)
                else DiscoverPage(loading = true)) }
            try {
                val result = api.discoverPage(feed, before.searchFilters, offset)
                mutable.update { current ->
                    val items = ((if(more) current.feedPage.items else emptyList()) + result.items).distinctBy { it.id }
                    if(current.feed != feed) current else current.copy(feedPage = result.copy(items = items,
                        hasMore = result.hasMore && (!more || items.size > current.feedPage.items.size)))
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(feedPage = it.feedPage.copy(loading = false, error = e.message)) } }
        }
    }
    fun loadDiscover(refresh: Boolean = false) {
        visibleDiscoverFeeds(mutable.value.account.user != null).filterNot { it == DiscoverFeed.HISTORY }.forEach { feed ->
            if(refresh || mutable.value.discoverSections[feed] == null) loadDiscoverFeed(feed)
        }
    }
    fun loadDiscoverFeed(feed: DiscoverFeed) {
        if (mutable.value.offline) return
        val userId = mutable.value.account.user?.id
        if(feed == DiscoverFeed.HISTORY || (feed.accountOnly && userId == null) || discoverJobs[feed]?.isActive == true) return
        val filters = mutable.value.searchFilters
        mutable.update { it.copy(discoverSections = it.discoverSections + (feed to (it.discoverSections[feed] ?: DiscoverSection()).copy(loading = true,error = null))) }
        discoverJobs[feed] = viewModelScope.launch {
            try {
                val items = if(feed == DiscoverFeed.UPDATES) api.followedUpdates() else api.discover(feed,filters)
                mutable.update { if(feed.accountOnly && it.account.user?.id != userId) it else it.copy(discoverSections = it.discoverSections + (feed to DiscoverSection(items = items))) }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                mutable.update { if(feed.accountOnly && it.account.user?.id != userId) it else it.copy(discoverSections = it.discoverSections + (feed to (it.discoverSections[feed] ?: DiscoverSection()).copy(loading = false,error = e.message ?: "Couldn't load this feed."))) }
            }
        }
    }
    private fun recordHistory(chapter: Chapter, page: Int, pageCount: Int) {
        if (chapter.id.startsWith("local-")) return // Imported ZIPs have no Comico chapter ID to sync.
        val manga = mutable.value.selected ?: return
        val previous = mutable.value.history.firstOrNull { it.chapter.id == chapter.id }
        val entry = HistoryEntry(manga,chapter,page,if(pageCount > 0) pageCount else previous?.pageCount ?: 0,java.time.Instant.now().toString())
        mutable.update { it.copy(history = mergeHistory(emptyList(),it.history.filterNot { h -> h.chapter.id == chapter.id } + entry).take(1000)) }
        persistHistory()
        if(activeOwner != null && entry.pageCount > 0) {
            prefs.edit().putString(ownerKey("pending:progress"),pending("progress").put(chapter.id,entry.toJson()).toString()).apply()
            progressSyncJob?.cancel()
            progressSyncJob = viewModelScope.launch { delay(1500);syncAccount() }
        }
    }
    fun openHistory(entry: HistoryEntry) {
        open(entry.manga)
    }
    fun syncAccount() = syncAccount(allowFollowUp = true)
    private fun syncAccount(allowFollowUp: Boolean) {
        if (mutable.value.offline) return
        val user = mutable.value.account.user ?: return
        if(mutable.value.syncLoading || mutable.value.account.loading) return
        mutable.update { it.copy(syncLoading = true,syncError = null) }
        syncJob = viewModelScope.launch {
            val failures = mutableListOf<String>()
            try {
                try {
                    val bookmarks = pending("bookmarks")
                    for(id in bookmarks.keys().asSequence().toList()) {
                        val saved = bookmarks.getBoolean(id)
                        api.changeBookmark(id,saved)
                        val current = pending("bookmarks")
                        if(current.opt(id) == saved) { current.remove(id);prefs.edit().putString(ownerKey("pending:bookmarks"),current.toString()).apply() }
                    }
                    val remote = api.library()
                    val queued = pending("bookmarks")
                    val additions = mutable.value.library.filter { queued.optBoolean(it.id,false) }
                    mutable.update { it.copy(library = (remote.filterNot { m -> queued.has(m.id) && !queued.getBoolean(m.id) } + additions).distinctBy { m -> m.id }) }
                    persistLibrary()
                } catch(e: CancellationException) { throw e } catch(e: Exception) { failures += "Library: ${e.message}" }
                try {
                    val remote = api.history()
                    val queued = remoteProgressQueue(pending("progress"))
                    prefs.edit().putString(ownerKey("pending:progress"), queued.toString()).apply()
                    val uploaded = mutableListOf<HistoryEntry>()
                    for(id in queued.keys().asSequence().toList()) {
                        val entry = queued.getJSONObject(id).historyEntry()
                        val latest = remote.firstOrNull { it.chapter.id == id }
                        if(latest == null || java.time.Instant.parse(entry.readAt) > java.time.Instant.parse(latest.readAt)) { api.saveProgress(entry);uploaded += entry }
                        val current = pending("progress")
                        if(matchesPendingProgress(current.optJSONObject(id), entry)) { current.remove(id);prefs.edit().putString(ownerKey("pending:progress"),current.toString()).apply() }
                    }
                    val stillPending = pending("progress").let { entries -> entries.keys().asSequence().map { entries.getJSONObject(it).historyEntry() }.toList() }
                    val merged = accountHistorySnapshot(remote,uploaded,stillPending,mutable.value.history).filterNot { it.isLocalChapter() }
                    mutable.update { it.copy(history = merged,progress = merged.asReversed().associate { h -> h.manga.id to h.chapter }) }
                    merged.forEach { prefs.edit().putInt(ownerKey("readerPage:${it.chapter.id}"),it.page).apply() }
                    persistHistory()
                    val progress = JSONObject();mutable.value.progress.forEach { (id, chapter) -> progress.put(id,chapter.toJson()) }
                    prefs.edit().putString(ownerKey("progress"),progress.toString()).apply()
                } catch(e: CancellationException) { throw e } catch(e: Exception) { failures += "History: ${e.message}" }
                mutable.update { it.copy(lastSync = if(failures.isEmpty()) java.time.Instant.now().toString() else it.lastSync,syncError = failures.joinToString("\n").ifEmpty { null }) }
                if(mutable.value.tab == "Discover") loadDiscoverFeed(DiscoverFeed.UPDATES)
            } finally {
                mutable.update { it.copy(syncLoading = false) }
                if(allowFollowUp && failures.isEmpty() && (pending("bookmarks").length() > 0 || pending("progress").length() > 0)) syncAccount(allowFollowUp = false)
            }
        }
    }
    fun loadLeaderboard() {
        if (mutable.value.offline) return
        if(mutable.value.leaderboardLoading) return
        viewModelScope.launch {
            mutable.update { it.copy(leaderboardLoading = true,leaderboardError = null) }
            try { val entries = api.leaderboard();mutable.update { it.copy(leaderboard = entries) } }
            catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(leaderboardError = e.message) } }
            finally { mutable.update { it.copy(leaderboardLoading = false) } }
        }
    }
    fun loadAccountData() {
        if (mutable.value.offline) return
        val user = mutable.value.account.user ?: return
        accountDataJob?.cancel()
        accountDataJob = viewModelScope.launch {
            mutable.update { it.copy(accountDataLoading = true,accountDataError = null) }
            try {
                val profile = api.publicProfile(user.username)
                mutable.update { if(it.account.user?.id == user.id) it.copy(publicProfile = profile) else it }
                val settings = api.profileSettings()
                mutable.update { it.copy(profileBio = settings.profileEdit().bio, profileSettings = settings.profileEdit()) }
                val comments = api.comments(user.username)
                mutable.update { it.copy(comments = comments) }
            } catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(accountDataError = e.message) } }
            finally { mutable.update { it.copy(accountDataLoading = false) } }
        }
    }
    fun saveProfile(name: String, username: String, profile: ProfileEdit) {
        if(mutable.value.accountDataLoading || mutable.value.account.user == null) return
        accountDataJob = viewModelScope.launch {
            mutable.update { it.copy(accountDataLoading = true,accountDataError = null) }
            try {
                require(profile.validationError() == null) { profile.validationError().orEmpty() }
                api.saveProfile(name,username,profile)
                val user = api.account()
                val updated = user?.let { api.publicProfile(it.username) }
                val settings = api.profileSettings().profileEdit()
                mutable.update { it.copy(account = it.account.copy(user = user), publicProfile = updated,
                    profileBio = settings.bio, profileSettings = settings) }
            }
            catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(accountDataError = e.message) } }
            finally { mutable.update { it.copy(accountDataLoading = false) } }
        }
    }
    fun importGuestData() {
        if(activeOwner == null || mutable.value.syncLoading || mutable.value.account.loading) return
        val guestLibrary = runCatching { JSONArray(prefs.getString("library","[]")).objects().map { it.manga() } }.getOrDefault(emptyList())
        val guestHistory = runCatching { JSONArray(prefs.getString("history","[]")).objects().map { it.historyEntry() } }.getOrDefault(emptyList())
        val bookmarks = pending("bookmarks")
        guestLibrary.filter { guest -> mutable.value.library.none { it.id == guest.id } }.forEach { bookmarks.put(it.id,true) }
        val progress = pending("progress")
        mergeHistory(guestHistory,mutable.value.history).filter { entry -> entry.pageCount > 0 && guestHistory.any { it.chapter.id == entry.chapter.id } }.forEach { progress.put(it.chapter.id,it.toJson()) }
        mutable.update { it.copy(library = (it.library + guestLibrary).distinctBy { m -> m.id },history = mergeHistory(it.history,guestHistory)) }
        persistLibrary();persistHistory()
        prefs.edit().putString(ownerKey("pending:bookmarks"),bookmarks.toString()).putString(ownerKey("pending:progress"),progress.toString()).apply()
        syncAccount()
    }
    fun exportData() = JSONObject().put("version",2).put("collections", JSONArray(mutable.value.libraryCollections.map { it.toJson() })).put("libraryOptions", mutable.value.libraryOptions.toJson()).put("library",JSONArray(mutable.value.library.map { it.toJson() })).put("history",JSONArray(mutable.value.history.map { it.toJson() })).put("readerDefaults",mutable.value.readerPreferences.toJson()).toString(2)
    fun clearHistory() {
        if(mutable.value.syncLoading || mutable.value.account.loading) return
        viewModelScope.launch {
            mutable.update { it.copy(syncLoading = true,syncError = null) }
            try {
                if(mutable.value.account.user != null && !mutable.value.offline) api.clearHistory()
                mutable.update { it.copy(history = emptyList(),progress = emptyMap()) }
                persistHistory()
                prefs.edit().apply {
                    remove(ownerKey("progress"));remove(ownerKey("pending:progress"))
                    val suffix = activeOwner?.let { ":account:$it" }
                    prefs.all.keys.filter { key -> key.startsWith("readerPage:") && if(suffix == null) !key.contains(":account:") else key.endsWith(suffix) }.forEach { remove(it) }
                }.apply()
            } catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(syncError = "Couldn't clear history: ${e.message}") } }
            finally { mutable.update { it.copy(syncLoading = false) } }
        }
    }
    fun clearAccountMessages() { if(!mutable.value.account.loading) mutable.update { it.copy(account = it.account.copy(error = null,message = null)) } }
}
