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
    val tab: String = "Discover", val query: String = "", val format: String = "All",
    val catalog: List<Manga> = emptyList(), val total: Int = 0, val offset: Int = 0,
    val loading: Boolean = false, val error: String? = null,
    val selected: Manga? = null, val chapters: List<Chapter> = emptyList(), val chapterTotal: Int = 0,
    val chapterOffset: Int = 0, val chapterLoading: Boolean = false, val chapterError: String? = null,
    val language: String = "en", val reader: Chapter? = null,
    val library: List<Manga> = emptyList(), val progress: Map<String, Chapter> = emptyMap(),
    val theme: String = "System", val dynamicColor: Boolean = false,
    val readerPreferences: ReaderPreferences = ReaderPreferences(),
    val readerOverrides: Map<String, ReaderOverride> = emptyMap(),
    val mangaSources: List<ReaderSource> = emptyList(), val knownSources: List<ReaderSource> = emptyList(),
    val nativeReader: NativeReaderState = NativeReaderState(),
    val searchFilters: SearchFilters = SearchFilters(), val comickTags: List<ComickTag> = emptyList(), val tagsLoading: Boolean = false, val tagsError: String? = null
)
fun AppState.effectiveReaderPreferences(): ReaderPreferences = readerOverrides[selected?.id]?.resolve(readerPreferences) ?: readerPreferences

class ReaderViewModel(application: Application): AndroidViewModel(application) {
    private val api = ComicoApi()
    private val nativeRepository = NativeReaderRepository(api)
    private val prefs = application.getSharedPreferences("reader", 0)
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private var catalogJob: Job? = null
    private var detailJob: Job? = null
    private var chaptersJob: Job? = null
    private var sourceJob: Job? = null
    private var readerJob: Job? = null
    init {
        val saved = runCatching { JSONArray(prefs.getString("library", "[]")).objects().map { it.manga() } }.getOrDefault(emptyList())
        val progress = runCatching { JSONObject(prefs.getString("progress", "{}")!!).let { obj -> obj.keys().asSequence().associateWith { obj.getJSONObject(it).chapter() } } }.getOrDefault(emptyMap())
        mutable.update { it.copy(library = saved, progress = progress, theme = prefs.getString("theme", "System")!!, dynamicColor = prefs.getBoolean("dynamic", false)) }
        val readerPreferences = runCatching { JSONObject(prefs.getString("readerGlobal", "{}")!!).readerPreferences() }.getOrDefault(ReaderPreferences())
        val overrides = runCatching { JSONObject(prefs.getString("readerOverrides", "{}")!!).let { obj -> obj.keys().asSequence().associateWith { obj.getJSONObject(it).readerOverride() } } }.getOrDefault(emptyMap())
        val knownSources = runCatching { JSONArray(prefs.getString("readerKnownSources", "[]")).objects().map { ReaderSource(it.getString("id"),it.getString("name")) } }.getOrDefault(emptyList())
        mutable.update { it.copy(readerPreferences = readerPreferences, readerOverrides = overrides, knownSources = knownSources) }
        val searchFilters = runCatching { JSONObject(prefs.getString("searchFilters", "{}")!!).searchFilters() }.getOrDefault(SearchFilters())
        mutable.update { it.copy(searchFilters = searchFilters, format = searchFilters.type?.apiValue?.replaceFirstChar { c -> c.uppercase() } ?: "All") }
        loadCatalog()
    }
    fun tab(value: String) {
        val reset = value == "Discover" && mutable.value.query.isNotEmpty()
        mutable.update { it.copy(tab = value, query = if(reset) "" else it.query) }
        if(reset) loadCatalog()
    }
    fun search(value: String) { mutable.update { it.copy(query = value) }; loadCatalog(debounce = true) }
    fun format(value: String) { applySearchFilters(mutable.value.searchFilters.copy(type = MangaType.entries.find { it.apiValue.equals(value,ignoreCase = true) })) }
    fun loadCatalog(more: Boolean = false, debounce: Boolean = false) {
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
        mutable.update { it.copy(selected = manga, reader = null, chapters = emptyList(), chapterTotal = 0, chapterOffset = 0, chapterError = null, mangaSources = emptyList(), chapterLoading = true) }
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
            mutable.update { it.copy(chapterLoading = true, chapterError = null, chapters = if(more) it.chapters else emptyList()) }
            try {
                val result = api.chapters(manga.id, before.language, offset, before.effectiveReaderPreferences().source?.takeIf { source -> before.mangaSources.any { it.id == source && it.readable } })
                mutable.update { it.copy(chapters = (if(more) it.chapters else emptyList()).plus(result.items).distinctBy { c -> c.id }, chapterTotal = result.total, chapterOffset = offset + 50, chapterLoading = false) }
            } catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(chapterLoading = false, chapterError = e.message) } }
        }
    }
    fun back() {
        if(mutable.value.reader != null) { readerJob?.cancel(); mutable.update { it.copy(reader = null, nativeReader = NativeReaderState()) } }
        else { detailJob?.cancel(); chaptersJob?.cancel(); sourceJob?.cancel(); mutable.update { it.copy(selected = null, chapterLoading = false) } }
    }
    fun read(chapter: Chapter) {
        val id = mutable.value.selected?.id ?: return
        mutable.update { it.copy(reader = chapter, progress = it.progress + (id to chapter), nativeReader = NativeReaderState(page = prefs.getInt("readerPage:${chapter.id}",0))) }
        val data = JSONObject(); mutable.value.progress.forEach { (key, c) -> data.put(key, JSONObject().put("id",c.id).put("number",c.number).put("title",c.title).put("language",c.language).put("scanlationGroup",c.group).put("externalUrl",c.external)) }
        prefs.edit().putString("progress", data.toString()).apply()
        loadReader()
    }
    fun bookmark(manga: Manga) {
        mutable.update { s -> s.copy(library = if(s.library.any { it.id == manga.id }) s.library.filterNot { it.id == manga.id } else s.library + manga) }
        val data = JSONArray(); mutable.value.library.forEach { m -> data.put(JSONObject().put("id",m.id).put("title",m.title).put("coverUrl",m.cover).put("status",m.status).put("format",m.format).put("description",m.description).put("contentRating",m.rating).put("tags",JSONArray(m.tags.map { JSONObject().put("name",it) }))) }
        prefs.edit().putString("library",data.toString()).apply()
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
        prefs.edit().putInt("readerPage:${chapter.id}",value).apply()
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
}
