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
    val theme: String = "System", val dynamicColor: Boolean = false
)
class ReaderViewModel(application: Application): AndroidViewModel(application) {
    private val api = ComicoApi()
    private val prefs = application.getSharedPreferences("reader", 0)
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private var catalogJob: Job? = null
    private var detailJob: Job? = null
    private var chaptersJob: Job? = null
    init {
        val saved = runCatching { JSONArray(prefs.getString("library", "[]")).objects().map { it.manga() } }.getOrDefault(emptyList())
        val progress = runCatching { JSONObject(prefs.getString("progress", "{}")!!).let { obj -> obj.keys().asSequence().associateWith { obj.getJSONObject(it).chapter() } } }.getOrDefault(emptyMap())
        mutable.update { it.copy(library = saved, progress = progress, theme = prefs.getString("theme", "System")!!, dynamicColor = prefs.getBoolean("dynamic", false)) }
        loadCatalog()
    }
    fun tab(value: String) {
        val reset = value == "Discover" && mutable.value.query.isNotEmpty()
        mutable.update { it.copy(tab = value, query = if(reset) "" else it.query) }
        if(reset) loadCatalog()
    }
    fun search(value: String) { mutable.update { it.copy(query = value) }; loadCatalog(debounce = true) }
    fun format(value: String) { mutable.update { it.copy(format = value) }; loadCatalog() }
    fun loadCatalog(more: Boolean = false, debounce: Boolean = false) {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            val before = mutable.value
            val offset = if(more) before.offset else 0
            mutable.update { it.copy(loading = true, error = null, catalog = if(more) it.catalog else emptyList()) }
            try {
                if(debounce) delay(400)
                val result = api.catalog(before.query, offset, before.format)
                mutable.update { it.copy(catalog = (if(more) it.catalog else emptyList()).plus(result.items).distinctBy { m -> m.id }, total = result.total, offset = offset + 30, loading = false) }
            } catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(loading = false, error = e.message ?: "Could not connect to Comico.") } }
        }
    }
    fun open(manga: Manga) {
        detailJob?.cancel(); chaptersJob?.cancel()
        mutable.update { it.copy(selected = manga, reader = null, chapters = emptyList(), chapterTotal = 0, chapterOffset = 0, chapterError = null) }
        detailJob = viewModelScope.launch {
            try { val detail = api.detail(manga.id); mutable.update { if(it.selected?.id == manga.id) it.copy(selected = detail) else it } }
            catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(chapterError = e.message) } }
        }
        loadChapters()
    }
    fun language(value: String) { mutable.update { it.copy(language = value) }; loadChapters() }
    fun loadChapters(more: Boolean = false) {
        chaptersJob?.cancel()
        chaptersJob = viewModelScope.launch {
            val before = mutable.value; val manga = before.selected ?: return@launch
            val offset = if(more) before.chapterOffset else 0
            mutable.update { it.copy(chapterLoading = true, chapterError = null, chapters = if(more) it.chapters else emptyList()) }
            try {
                val result = api.chapters(manga.id, before.language, offset)
                mutable.update { it.copy(chapters = (if(more) it.chapters else emptyList()).plus(result.items).distinctBy { c -> c.id }, chapterTotal = result.total, chapterOffset = offset + 50, chapterLoading = false) }
            } catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(chapterLoading = false, chapterError = e.message) } }
        }
    }
    fun back() {
        if(mutable.value.reader != null) mutable.update { it.copy(reader = null) }
        else { detailJob?.cancel(); chaptersJob?.cancel(); mutable.update { it.copy(selected = null, chapterLoading = false) } }
    }
    fun read(chapter: Chapter) {
        val id = mutable.value.selected?.id ?: return
        mutable.update { it.copy(reader = chapter, progress = it.progress + (id to chapter)) }
        val data = JSONObject(); mutable.value.progress.forEach { (key, c) -> data.put(key, JSONObject().put("id",c.id).put("number",c.number).put("title",c.title).put("language",c.language).put("scanlationGroup",c.group).put("externalUrl",c.external)) }
        prefs.edit().putString("progress", data.toString()).apply()
    }
    fun bookmark(manga: Manga) {
        mutable.update { s -> s.copy(library = if(s.library.any { it.id == manga.id }) s.library.filterNot { it.id == manga.id } else s.library + manga) }
        val data = JSONArray(); mutable.value.library.forEach { m -> data.put(JSONObject().put("id",m.id).put("title",m.title).put("coverUrl",m.cover).put("status",m.status).put("format",m.format).put("description",m.description).put("contentRating",m.rating).put("tags",JSONArray(m.tags.map { JSONObject().put("name",it) }))) }
        prefs.edit().putString("library",data.toString()).apply()
    }
    fun theme(value: String) { mutable.update { it.copy(theme = value) }; prefs.edit().putString("theme",value).apply() }
    fun dynamic(value: Boolean) { mutable.update { it.copy(dynamicColor = value) }; prefs.edit().putBoolean("dynamic",value).apply() }
}
