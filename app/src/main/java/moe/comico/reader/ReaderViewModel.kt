package moe.comico.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class AppState(
    val discoverSections: Map<DiscoverFeed,DiscoverSection> = emptyMap(), val history: List<HistoryEntry> = emptyList(),
    val syncLoading: Boolean = false, val syncError: String? = null, val lastSync: String? = null,
    val leaderboard: List<LeaderboardEntry> = emptyList(), val leaderboardLoading: Boolean = false, val leaderboardError: String? = null,
    val comments: List<AccountComment> = emptyList(), val profileBio: String = "", val accountDataLoading: Boolean = false, val accountDataError: String? = null,
    val tab: String = "Discover", val query: String = "", val format: String = "All",
    val catalog: List<Manga> = emptyList(), val total: Int = 0, val offset: Int = 0,
    val loading: Boolean = false, val error: String? = null,
    val selected: Manga? = null, val chapters: List<Chapter> = emptyList(), val chapterTotal: Int = 0,
    val firstChapter: Chapter? = null, val startError: String? = null,
    val chapterOffset: Int = 0, val chapterLoading: Boolean = false, val chapterError: String? = null,
    val language: String = "en", val reader: Chapter? = null,
    val libraryQuery: String = "", val librarySearchVisible: Boolean = false,
    val library: List<Manga> = emptyList(), val progress: Map<String, Chapter> = emptyMap(),
    val theme: String = "Website", val dynamicColor: Boolean = false,
    val readerPreferences: ReaderPreferences = ReaderPreferences(),
    val readerOverrides: Map<String, ReaderOverride> = emptyMap(),
    val mangaSources: List<ReaderSource> = emptyList(), val knownSources: List<ReaderSource> = emptyList(),
    val nativeReader: NativeReaderState = NativeReaderState(),
    val searchFilters: SearchFilters = SearchFilters(), val comickTags: List<ComickTag> = emptyList(), val tagsLoading: Boolean = false, val tagsError: String? = null, val account: AccountState = AccountState()
)
fun AppState.effectiveReaderPreferences(): ReaderPreferences = readerOverrides[selected?.id]?.resolve(readerPreferences) ?: readerPreferences

class ReaderViewModel(application: Application): AndroidViewModel(application) {
    private val accountCookies = AccountCookieJar(application)
    private val api = ComicoApi(accountCookies)
    private val nativeRepository = NativeReaderRepository(api)
    private val prefs = application.getSharedPreferences("reader", 0)
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private var syncJob: Job? = null
    private var progressSyncJob: Job? = null
    private var accountDataJob: Job? = null
    private var activeOwner: String? = null
    private val discoverJobs = mutableMapOf<DiscoverFeed,Job>()
    private var catalogJob: Job? = null
    private var detailJob: Job? = null
    private var chaptersJob: Job? = null
    private var sourceJob: Job? = null
    private var readerJob: Job? = null
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
        loadOwner(null)
        loadDiscover()
        refreshAccount()
    }
    fun tab(value: String) {
        val reset = value == "Discover" && mutable.value.query.isNotEmpty()
        mutable.update { it.copy(tab = value, query = if(reset) "" else it.query) }
        if(value == "Discover") loadDiscover()
        if(value == "Search") loadCatalog()
        if(value == "Library" || value == "History") syncAccount()
        if(value == "Leaderboard") loadLeaderboard()
    }
    fun librarySearch(value: String) { mutable.update { it.copy(libraryQuery = value) } }
    fun toggleLibrarySearch() { mutable.update { it.copy(librarySearchVisible = !it.librarySearchVisible,libraryQuery = if(it.librarySearchVisible) "" else it.libraryQuery) } }
    fun search(value: String) { mutable.update { it.copy(query = value) }; loadCatalog(debounce = true) }
    fun format(value: String) { applySearchFilters(mutable.value.searchFilters.copy(type = MangaType.entries.find { it.apiValue.equals(value,ignoreCase = true) })) }
    fun loadCatalog(more: Boolean = false, debounce: Boolean = false) {
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
        detailJob?.cancel(); chaptersJob?.cancel(); sourceJob?.cancel(); readerJob?.cancel()
        mutable.update { it.copy(selected = manga, reader = null, chapters = emptyList(), chapterTotal = 0, chapterOffset = 0, chapterError = null, mangaSources = emptyList(), chapterLoading = true, firstChapter = null, startError = null) }
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
    fun language(value: String) { mutable.update { it.copy(language = value) }; loadChapters() }
    fun loadChapters(more: Boolean = false) {
        chaptersJob?.cancel()
        chaptersJob = viewModelScope.launch {
            val before = mutable.value; val manga = before.selected ?: return@launch
            val offset = if(more) before.chapterOffset else 0
            mutable.update { it.copy(chapterLoading = true, chapterError = null, firstChapter = if(more) it.firstChapter else null, startError = null, chapters = if(more) it.chapters else emptyList()) }
            try {
                val source = before.effectiveReaderPreferences().source?.takeIf { source -> before.mangaSources.any { it.id == source && it.readable } }
                val result = api.chapters(manga.id, before.language, offset, source)
                if(!more) {
                    try { val first = api.firstChapter(manga.id,before.language,source);mutable.update { it.copy(firstChapter = first) } }
                    catch(e: CancellationException) { throw e }
                    catch(_: Exception) { mutable.update { it.copy(startError = "Couldn't find the first chapter. Refresh the chapter list to try again.") } }
                }
                mutable.update { it.copy(chapters = (if(more) it.chapters else emptyList()).plus(result.items).distinctBy { c -> c.id }, chapterTotal = result.total, chapterOffset = offset + 50, chapterLoading = false) }
            } catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(chapterLoading = false, chapterError = e.message) } }
        }
    }
    fun back() {
        if(mutable.value.reader != null) { readerJob?.cancel(); mutable.update { it.copy(reader = null, nativeReader = NativeReaderState()) };progressSyncJob?.cancel();syncAccount() }
        else { detailJob?.cancel(); chaptersJob?.cancel(); sourceJob?.cancel(); mutable.update { it.copy(selected = null, chapterLoading = false) } }
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
        if(old.source != current.source && mutable.value.selected != null) loadChapters()
        if((old.source != current.source || old.proxy != current.proxy) && mutable.value.reader != null) loadReader()
    }
    fun loadReader() {
        readerJob?.cancel()
        readerJob = viewModelScope.launch {
            val chapter = mutable.value.reader ?: return@launch
            mutable.update { it.copy(nativeReader = it.nativeReader.copy(loading = true, session = null, error = null, notice = null)) }
            try {
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
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(nativeReader = it.nativeReader.copy(loading = false, error = e.message ?: "Couldn't load this chapter. Try another source or proxy method.")) } }
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
        if(mutable.value.tagsLoading || mutable.value.comickTags.isNotEmpty()) return
        viewModelScope.launch {
            mutable.update { it.copy(tagsLoading = true,tagsError = null) }
            try { val tags = api.comickTags();mutable.update { it.copy(comickTags = tags,tagsLoading = false) } }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(tagsLoading = false,tagsError = e.message ?: "Couldn't load Comick tags.") } }
        }
    }

    fun refreshAccount() {
        if(mutable.value.account.loading || mutable.value.syncLoading) return
        viewModelScope.launch {
            mutable.update { it.copy(account = it.account.copy(loading = true,error = null)) }
            try { val user = api.account();mutable.update { it.copy(account = AccountState(user = user)) }; activateAccount(user) }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(account = it.account.copy(loading = false,error = e.message)) } }
        }
    }
    fun signIn(identifier: String, password: String) {
        if(mutable.value.account.loading || mutable.value.syncLoading) return
        viewModelScope.launch {
            mutable.update { it.copy(account = AccountState(loading = true)) }
            try { val user = api.signIn(identifier,password);mutable.update { it.copy(account = AccountState(user = user,message = "Signed in.")) }; activateAccount(user) }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(account = AccountState(error = e.message ?: "Couldn't sign in.")) } }
        }
    }
    fun register(username: String, name: String, email: String, password: String) {
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
        val library = runCatching { JSONArray(prefs.getString(ownerKey("library"),"[]")).objects().map { it.manga() } }.getOrDefault(emptyList())
        val history = runCatching { JSONArray(prefs.getString(ownerKey("history"),"[]")).objects().map { it.historyEntry() } }.getOrDefault(emptyList())
        val progress = runCatching { JSONObject(prefs.getString(ownerKey("progress"),"{}")!!).let { obj -> obj.keys().asSequence().associateWith { obj.getJSONObject(it).chapter() } } }.getOrDefault(emptyMap())
        mutable.update { it.copy(library = library,libraryQuery = "",librarySearchVisible = false,history = history,progress = progress,syncError = null,lastSync = null,comments = emptyList(),profileBio = "",accountDataError = null,discoverSections = it.discoverSections.filterKeys { feed -> !feed.accountOnly }) }
    }
    private fun activateAccount(user: AccountUser?) {
        progressSyncJob?.cancel();accountDataJob?.cancel()
        if(activeOwner != user?.id) loadOwner(user?.id)
        if(user != null) { syncAccount();loadAccountData() }
        if(mutable.value.tab == "Discover") loadDiscover()
    }
    fun loadDiscover(refresh: Boolean = false) {
        visibleDiscoverFeeds(mutable.value.account.user != null).filterNot { it == DiscoverFeed.HISTORY }.forEach { feed ->
            if(refresh || mutable.value.discoverSections[feed] == null) loadDiscoverFeed(feed)
        }
    }
    fun loadDiscoverFeed(feed: DiscoverFeed) {
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
        prefs.edit().putInt(ownerKey("readerPage:${entry.chapter.id}"),entry.page).apply()
        read(entry.chapter)
    }
    fun syncAccount() {
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
                    val queued = pending("progress")
                    val uploaded = mutableListOf<HistoryEntry>()
                    for(id in queued.keys().asSequence().toList()) {
                        val entry = queued.getJSONObject(id).historyEntry()
                        val latest = remote.firstOrNull { it.chapter.id == id }
                        if(latest == null || java.time.Instant.parse(entry.readAt) > java.time.Instant.parse(latest.readAt)) { api.saveProgress(entry);uploaded += entry }
                        val current = pending("progress")
                        if(current.optJSONObject(id)?.toString() == entry.toJson().toString()) { current.remove(id);prefs.edit().putString(ownerKey("pending:progress"),current.toString()).apply() }
                    }
                    val stillPending = pending("progress").let { entries -> entries.keys().asSequence().map { entries.getJSONObject(it).historyEntry() }.toList() }
                    val merged = accountHistorySnapshot(remote,uploaded,stillPending,mutable.value.history)
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
                if(failures.isEmpty() && (pending("bookmarks").length() > 0 || pending("progress").length() > 0)) syncAccount()
            }
        }
    }
    fun loadLeaderboard() {
        if(mutable.value.leaderboardLoading) return
        viewModelScope.launch {
            mutable.update { it.copy(leaderboardLoading = true,leaderboardError = null) }
            try { val entries = api.leaderboard();mutable.update { it.copy(leaderboard = entries) } }
            catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(leaderboardError = e.message) } }
            finally { mutable.update { it.copy(leaderboardLoading = false) } }
        }
    }
    fun loadAccountData() {
        val user = mutable.value.account.user ?: return
        accountDataJob?.cancel()
        accountDataJob = viewModelScope.launch {
            mutable.update { it.copy(accountDataLoading = true,accountDataError = null) }
            try {
                val settings = api.profileSettings()
                mutable.update { it.copy(profileBio = settings.optString("bio").takeUnless { value -> value == "null" }.orEmpty()) }
                val comments = api.comments(user.username)
                mutable.update { it.copy(comments = comments) }
            } catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(accountDataError = e.message) } }
            finally { mutable.update { it.copy(accountDataLoading = false) } }
        }
    }
    fun saveProfile(name: String, username: String, bio: String) {
        if(mutable.value.accountDataLoading || mutable.value.account.user == null) return
        accountDataJob = viewModelScope.launch {
            mutable.update { it.copy(accountDataLoading = true,accountDataError = null) }
            try { api.saveProfile(name,username,bio);val user = api.account();mutable.update { it.copy(account = it.account.copy(user = user),profileBio = bio) } }
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
    fun exportData() = JSONObject().put("version",1).put("library",JSONArray(mutable.value.library.map { it.toJson() })).put("history",JSONArray(mutable.value.history.map { it.toJson() })).put("readerDefaults",mutable.value.readerPreferences.toJson()).toString(2)
    fun clearHistory() {
        if(mutable.value.syncLoading || mutable.value.account.loading) return
        viewModelScope.launch {
            mutable.update { it.copy(syncLoading = true,syncError = null) }
            try {
                if(activeOwner != null) api.clearHistory()
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
