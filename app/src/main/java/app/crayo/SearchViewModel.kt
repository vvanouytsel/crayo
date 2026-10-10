package app.crayo

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.crayo.data.Result
import app.crayo.data.SearchEngine
import app.crayo.data.Translate
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = SearchEngine(File(app.cacheDir, "previews").apply { mkdirs() })

    var query by mutableStateOf("")
    var terms by mutableStateOf("")
    var searching by mutableStateOf(false)
    var searched by mutableStateOf(false)
    var showWeak by mutableStateOf(false)
    var preparingPrint by mutableStateOf(false)
    val results = mutableStateListOf<Result>()
    val selected = mutableStateListOf<String>()   // in the order they were ticked

    private var job: Job? = null
    private var searchNumber = 0

    fun search(typed: String = query) {
        val word = typed.trim()
        if (word.isEmpty()) return
        query = word
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

    fun toggle(id: String) {
        if (!selected.remove(id)) selected.add(id)
    }

    fun clearSelection() = selected.clear()

    fun print(activity: Activity) {
        if (preparingPrint || selected.isEmpty()) return
        val chosen = selected.mapNotNull { id -> results.firstOrNull { it.id == id } }
        viewModelScope.launch {
            preparingPrint = true
            val pages = try {
                coroutineScope { chosen.map { async { engine.printBitmap(it) } }.awaitAll().filterNotNull() }
            } finally {
                preparingPrint = false
            }
            if (pages.isNotEmpty()) PrintPages.print(activity, pages)
        }
    }
}
