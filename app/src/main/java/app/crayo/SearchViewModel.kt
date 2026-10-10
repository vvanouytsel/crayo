package app.crayo

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.crayo.color.ColorError
import app.crayo.color.ColorExample
import app.crayo.color.OpenAi
import app.crayo.data.ExampleMode
import app.crayo.data.HistoryItem
import app.crayo.data.HistoryStore
import app.crayo.data.InkLevel
import app.crayo.data.Result
import app.crayo.data.SearchEngine
import app.crayo.data.Settings
import app.crayo.data.Translate
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Search, History, Settings }

/** Something the user has to decide before printing can go on. */
sealed interface Notice {
    data object MissingKey : Notice
    data class ExampleFailed(val reason: String) : Notice
}

/** A page ready to print, and where it came from. */
private class Page(val id: String, val title: String, val source: String, val bitmap: Bitmap)

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = SearchEngine(File(app.cacheDir, "previews").apply { mkdirs() })
    private val store = HistoryStore(File(app.filesDir, "history"))
    private val settings = Settings(app)
    private val colorExample = ColorExample(File(app.filesDir, "colors"))

    var screen by mutableStateOf(Screen.Search)
    var query by mutableStateOf("")
    var terms by mutableStateOf("")
    var searching by mutableStateOf(false)
    var searched by mutableStateOf(false)
    var showWeak by mutableStateOf(false)
    var printStatus by mutableStateOf<String?>(null)   // what the print button says while it is busy
    var notice by mutableStateOf<Notice?>(null)
    val results = mutableStateListOf<Result>()
    val history = mutableStateListOf<HistoryItem>()   // newest first
    val selected = mutableStateListOf<String>()       // in the order they were ticked

    var exampleMode by mutableStateOf(settings.exampleMode)
        private set
    var ink by mutableStateOf(settings.ink)
        private set
    var openAiKey by mutableStateOf(settings.openAiKey)
        private set
    var model by mutableStateOf(settings.model)
        private set
    var server by mutableStateOf(settings.server)
        private set

    private var job: Job? = null
    private var searchNumber = 0

    init {
        viewModelScope.launch { history.addAll(withContext(Dispatchers.IO) { store.load() }) }
    }

    val models = mutableStateListOf<String>()   // what the model picker offers, fetched from OpenAI
    var loadingModels by mutableStateOf(false)
        private set
    var modelsNote by mutableStateOf("")
        private set
    private var modelsJob: Job? = null
    private var modelsRequest = 0

    fun choose(mode: ExampleMode) { exampleMode = mode; settings.exampleMode = mode; refreshModels() }
    fun choose(value: InkLevel) { ink = value; settings.ink = value }
    fun changeKey(value: String) { openAiKey = value.trim(); settings.openAiKey = value; refreshModels(waitMs = 700) }
    fun changeModel(value: String) { model = value; settings.model = value; describeModels() }
    fun changeServer(value: String) { server = value; settings.server = value; refreshModels(waitMs = 700) }

    /** Asks OpenAI which models this key may use. Waits a moment first while the key is still being typed. */
    fun refreshModels(waitMs: Long = 0) {
        modelsJob?.cancel()
        val request = ++modelsRequest
        if (exampleMode == ExampleMode.Off) return
        if (openAiKey.isBlank()) {
            models.clear(); loadingModels = false
            modelsNote = "Add your key to choose from your account's models."
            return
        }
        loadingModels = true
        modelsNote = ""
        modelsJob = viewModelScope.launch {
            try {
                delay(waitMs)
                val list = OpenAi.models(server.ifBlank { OpenAi.DEFAULT_SERVER }, openAiKey)
                models.clear(); models.addAll(list)
                describeModels()
            } catch (e: ColorError) {
                models.clear()
                modelsNote = e.message?.lineSequence()?.firstOrNull() ?: "The list of models could not be loaded."
            } finally {
                if (request == modelsRequest) loadingModels = false
            }
        }
    }

    private fun describeModels() {
        if (models.isEmpty()) return
        modelsNote = if (model in models) "${models.size} models from your OpenAI account, newest first."
        else "\"$model\" is not in your account's list. Pick another."
    }

    fun search(typed: String = query) {
        val word = typed.trim()
        if (word.isEmpty()) return
        query = word
        screen = Screen.Search
        job?.cancel()
        val number = ++searchNumber
        results.clear(); selected.clear()
        showWeak = false; searched = true; searching = true; terms = ""
        job = viewModelScope.launch {
            try {
                val english = Translate.searchTerms(word)
                terms = english.distinct().joinToString(" / ")
                engine.search(english).collect { results.add(it) }
            } finally {
                if (number == searchNumber) searching = false
            }
        }
    }

    private var beforeSettings = Screen.Search

    fun show(next: Screen) {
        if (next == screen) return
        // Ticked pages survive a visit to Settings, but not a switch between Search and History.
        if (next == Screen.Settings) beforeSettings = screen else if (screen != Screen.Settings) selected.clear()
        screen = next
        if (next == Screen.Settings) refreshModels()
    }

    fun back() = show(if (screen == Screen.Settings) beforeSettings else Screen.Search)

    fun toggle(id: String) {
        if (!selected.remove(id)) selected.add(id)
    }

    fun clearSelection() = selected.clear()

    fun wasPrinted(id: String) = history.any { it.id == id }

    /** Prints the ticked pages. With examples switched on, each page is colored first. */
    fun print(activity: Activity, withExamples: Boolean = exampleMode != ExampleMode.Off) {
        if (printStatus != null || selected.isEmpty()) return
        if (withExamples && openAiKey.isBlank()) {
            notice = Notice.MissingKey
            return
        }
        val ids = selected.toList()
        viewModelScope.launch {
            printStatus = "Getting full size…"
            try {
                val pages = coroutineScope { ids.map { id -> async { loadPage(id) } }.awaitAll().filterNotNull() }
                if (pages.isEmpty()) return@launch
                val examples = HashMap<String, Bitmap>()
                if (withExamples) {
                    pages.forEachIndexed { index, page ->
                        printStatus = if (pages.size == 1) "Coloring the example…" else "Coloring example ${index + 1} of ${pages.size}…"
                        examples[page.id] = colorExample.make(page.id, page.title, page.bitmap, server.ifBlank { OpenAi.DEFAULT_SERVER }, openAiKey, model.ifBlank { OpenAi.DEFAULT_MODEL }, ink.wash)
                    }
                }
                recordPrinted(pages)
                PrintPages.print(activity, pages.map { Sheet(it.bitmap, examples[it.id], exampleMode == ExampleMode.Small) })
            } catch (e: ColorError) {
                notice = Notice.ExampleFailed(e.message ?: "The example could not be made.")
            } finally {
                printStatus = null
            }
        }
    }

    /** The picture to print: the saved copy from the history if there is one, otherwise a fresh download. */
    private suspend fun loadPage(id: String): Page? {
        history.firstOrNull { it.id == id }?.let { item ->
            val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(item.image.path) }
            if (bitmap != null) return Page(id, item.title, item.source, bitmap)
        }
        val result = results.firstOrNull { it.id == id } ?: return null
        val bitmap = engine.printBitmap(result) ?: return null
        return Page(id, result.title, result.source, bitmap)
    }

    /** Puts the printed pages at the top of the history, and counts repeats. */
    private suspend fun recordPrinted(pages: List<Page>) {
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            for (page in pages.reversed()) {
                val old = history.firstOrNull { it.id == page.id }
                if (old == null) store.writeImage(page.id, page.bitmap)
                val item = HistoryItem(
                    page.id, page.title, page.source, now, (old?.count ?: 0) + 1, store.imageFile(page.id),
                )
                withContext(Dispatchers.Main) {
                    history.removeAll { it.id == page.id }
                    history.add(0, item)
                }
            }
            store.save(history.toList())
        }
    }

    fun removeFromHistory(id: String) {
        val item = history.firstOrNull { it.id == id } ?: return
        history.remove(item); selected.remove(id)
        viewModelScope.launch(Dispatchers.IO) { item.image.delete(); store.save(history.toList()) }
    }

    fun clearHistory() {
        val items = history.toList()
        history.clear(); selected.clear()
        viewModelScope.launch(Dispatchers.IO) { items.forEach { it.image.delete() }; store.save(emptyList()) }
    }
}
