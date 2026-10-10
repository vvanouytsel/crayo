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
import app.crayo.data.HistoryItem
import app.crayo.data.HistoryStore
import app.crayo.data.Result
import app.crayo.data.SearchEngine
import app.crayo.data.Translate
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Search, History }

/** A page ready to print, and where it came from. */
private class Page(val id: String, val title: String, val source: String, val bitmap: Bitmap)

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = SearchEngine(File(app.cacheDir, "previews").apply { mkdirs() })
    private val store = HistoryStore(File(app.filesDir, "history"))

    var screen by mutableStateOf(Screen.Search)
    var query by mutableStateOf("")
    var terms by mutableStateOf("")
    var searching by mutableStateOf(false)
    var searched by mutableStateOf(false)
    var showWeak by mutableStateOf(false)
    var preparingPrint by mutableStateOf(false)
    val results = mutableStateListOf<Result>()
    val history = mutableStateListOf<HistoryItem>()   // newest first
    val selected = mutableStateListOf<String>()       // in the order they were ticked

    private var job: Job? = null
    private var searchNumber = 0

    init {
        viewModelScope.launch { history.addAll(withContext(Dispatchers.IO) { store.load() }) }
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

    fun show(next: Screen) {
        if (next == screen) return
        selected.clear()
        screen = next
    }

    fun toggle(id: String) {
        if (!selected.remove(id)) selected.add(id)
    }

    fun clearSelection() = selected.clear()

    fun wasPrinted(id: String) = history.any { it.id == id }

    fun print(activity: Activity) {
        if (preparingPrint || selected.isEmpty()) return
        val ids = selected.toList()
        viewModelScope.launch {
            preparingPrint = true
            val pages = try {
                coroutineScope { ids.map { id -> async { loadPage(id) } }.awaitAll().filterNotNull() }
            } finally {
                preparingPrint = false
            }
            if (pages.isEmpty()) return@launch
            recordPrinted(pages)
            PrintPages.print(activity, pages.map { it.bitmap })
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
