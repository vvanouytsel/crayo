package app.crayo

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.crayo.data.ExampleMode
import app.crayo.data.HistoryItem
import app.crayo.data.Result
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal val Red = Color(0xFFE8523F)
internal val Paper = Color(0xFFFFFDF8)
internal val Line = Color(0xFFE3E0D8)
internal val Ink = Color(0xFF222222)
internal val Muted = Color(0xFF6B6B6B)

private val quickWords = listOf("dragon", "unicorn", "dinosaur", "princess", "car", "fire truck", "cat", "dog", "horse", "butterfly", "robot", "space")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Red, background = Paper, surface = Color.White, onSurface = Ink)) {
                CrayoApp()
            }
        }
    }
}

@Composable
private fun CrayoApp(vm: SearchViewModel = viewModel()) {
    val activity = LocalContext.current as Activity
    BackHandler(enabled = vm.screen != Screen.Search) { vm.back() }
    Scaffold(
        containerColor = Paper,
        bottomBar = { if (vm.selected.isNotEmpty() && vm.screen != Screen.Settings) PrintBar(vm, activity) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (vm.screen) {
            Screen.Search -> SearchScreen(vm, modifier)
            Screen.History -> HistoryScreen(vm, modifier)
            Screen.Settings -> SettingsScreen(vm, modifier)
        }
    }

    when (val notice = vm.notice) {
        null -> Unit
        Notice.MissingKey -> AlertDialog(
            onDismissRequest = { vm.notice = null },
            title = { Text("Add your OpenAI key") },
            text = { Text("Color examples need an OpenAI key. Add it in Settings, or print without the example.") },
            confirmButton = { TextButton(onClick = { vm.notice = null; vm.show(Screen.Settings) }) { Text("Open settings", color = Red) } },
            dismissButton = { TextButton(onClick = { vm.notice = null; vm.print(activity, withExamples = false) }) { Text("Print without") } },
        )
        is Notice.ExampleFailed -> AlertDialog(
            onDismissRequest = { vm.notice = null },
            title = { Text("The example could not be made") },
            text = { Text(notice.reason) },
            confirmButton = { TextButton(onClick = { vm.notice = null; vm.print(activity, withExamples = false) }) { Text("Print without", color = Red) } },
            dismissButton = { TextButton(onClick = { vm.notice = null }) { Text("Cancel") } },
        )
    }
}

// ------------------------------------------------------------------------------ search

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchScreen(vm: SearchViewModel, modifier: Modifier) {
    val focus = LocalFocusManager.current
    val shown by remember { derivedStateOf { vm.results.filter { vm.showWeak || it.looksOk } } }
    val goodCount by remember { derivedStateOf { vm.results.count { it.looksOk } } }
    val weakCount = vm.results.size - goodCount

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { focus.clearFocus(); vm.show(Screen.History) }) {
                        Text(if (vm.history.isEmpty()) "History" else "History (${vm.history.size})", color = Red, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = { focus.clearFocus(); vm.show(Screen.Settings) }) {
                        Text("Settings", color = Red, fontWeight = FontWeight.SemiBold)
                    }
                }
                Logo()
                Text("Find a coloring page and print it", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                SearchRow(vm, focus)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                ) {
                    quickWords.forEach { word -> Chip(word) { vm.query = word; focus.clearFocus(); vm.search(word) } }
                }
                Status(vm, goodCount, weakCount)
            }
        }
        items(shown, key = { it.id }) { result ->
            PageCard(
                model = result.preview, title = result.title,
                subtitle = if (vm.wasPrinted(result.id)) "Printed before · ${result.source}" else result.source,
                highlight = vm.wasPrinted(result.id),
                selected = vm.selected.contains(result.id), faded = !result.looksOk,
                onClick = { vm.toggle(result.id) },
            )
        }
    }
}

@Composable
private fun Logo() {
    val colors = listOf(Color(0xFFE8523F), Color(0xFFF2A23A), Color(0xFFE7C52B), Color(0xFF4FAE5A), Color(0xFF3A8FD9))
    Text(
        buildAnnotatedString {
            "Crayo".forEachIndexed { index, letter -> withStyle(SpanStyle(color = colors[index])) { append(letter) } }
        },
        fontSize = 44.sp, fontWeight = FontWeight.ExtraBold,
    )
}

@Composable
private fun SearchRow(vm: SearchViewModel, focus: FocusManager) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text("dragon, draak, unicorn…", color = Muted) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.search() }),
        )
        Button(
            onClick = { focus.clearFocus(); vm.search() },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Red),
            modifier = Modifier.height(56.dp),
        ) { Text("Search", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(99.dp), color = Color.White, border = BorderStroke(1.dp, Line),
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).clickable(onClick = onClick),
    ) { Text(text, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) }
}

@Composable
private fun Status(vm: SearchViewModel, good: Int, weak: Int) {
    val text = when {
        !vm.searched -> ""
        vm.searching -> "Searching… $good found so far."
        good + weak == 0 -> "No coloring pages found for \"${vm.terms}\". Try another word."
        else -> "$good coloring pages for \"${vm.terms}\"."
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 8.dp).heightIn(min = 52.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            if (vm.searching) CircularProgressIndicator(Modifier.size(18.dp), color = Red, strokeWidth = 2.5.dp)
            Text(text, color = Muted, textAlign = TextAlign.Center, fontSize = 14.sp)
        }
        if (weak > 0 && !vm.searching) {
            TextButton(onClick = { vm.showWeak = !vm.showWeak }) {
                Text(if (vm.showWeak) "Hide the rest" else "Show $weak more that may not print well", color = Red)
            }
        }
    }
}

// ------------------------------------------------------------------------------ history

@Composable
private fun HistoryScreen(vm: SearchViewModel, modifier: Modifier) {
    var confirmClear by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.back() }) { Text("‹ Back", color = Red, fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.weight(1f))
                    if (vm.history.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear all", color = Muted) }
                }
                Text("History", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 12.dp))
                Text(
                    if (vm.history.isEmpty()) "Nothing printed yet. Pages you print show up here, so you can print them again."
                    else "Everything you printed, newest first. Tick a page to print it again.",
                    color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp),
                )
            }
        }
        items(vm.history, key = { it.id }) { item ->
            PageCard(
                model = item.image, title = item.title, subtitle = printedText(item), highlight = false,
                selected = vm.selected.contains(item.id), faded = false,
                onClick = { vm.toggle(item.id) }, onDelete = { vm.removeFromHistory(item.id) },
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the history?") },
            text = { Text("All ${vm.history.size} saved pages are removed from this phone.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Clear", color = Red) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep") } },
        )
    }
}

private fun printedText(item: HistoryItem): String {
    val date = Instant.ofEpochMilli(item.printedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val day = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
    return if (item.count > 1) "$day · printed ${item.count}×" else day
}

// ------------------------------------------------------------------------------ shared pieces

@Composable
private fun PageCard(
    model: Any, title: String, subtitle: String, highlight: Boolean, selected: Boolean, faded: Boolean,
    onClick: () -> Unit, onDelete: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(if (selected) 3.dp else 1.5.dp, if (selected) Red else Line),
        modifier = Modifier.alpha(if (faded) 0.55f else 1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(8.dp)) {
            Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                AsyncImage(model = model, contentDescription = title, modifier = Modifier.fillMaxWidth().height(190.dp))
                if (onDelete != null) {
                    Box(
                        Modifier.align(Alignment.TopStart).size(28.dp).clip(CircleShape).background(Color(0xFFF1EFE8)).clickable(onClick = onDelete),
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", color = Muted, fontSize = 13.sp) }
                }
                if (selected) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(26.dp).clip(CircleShape).background(Red),
                        contentAlignment = Alignment.Center,
                    ) { Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            Text(title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Text(subtitle, fontSize = 11.sp, color = if (highlight) Red else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PrintBar(vm: SearchViewModel, activity: Activity) {
    val count = vm.selected.size
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { vm.clearSelection() }) { Text("Clear", color = Muted) }
            Button(
                onClick = { vm.print(activity) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) {
                val examples = vm.exampleMode != ExampleMode.Off
                if (vm.printStatus != null) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    vm.printStatus ?: when {
                        count == 1 -> if (examples) "Print page with example" else "Print page"
                        else -> if (examples) "Print $count pages with examples" else "Print $count pages"
                    },
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}
