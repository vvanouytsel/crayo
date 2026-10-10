package app.crayo

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.remember
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
import app.crayo.data.Result
import coil3.compose.AsyncImage

private val Red = Color(0xFFE8523F)
private val Paper = Color(0xFFFFFDF8)
private val Line = Color(0xFFE3E0D8)
private val Ink = Color(0xFF222222)
private val Muted = Color(0xFF6B6B6B)

private val quickWords = listOf("dragon", "unicorn", "dinosaur", "princess", "car", "fire truck", "cat", "dog", "horse", "butterfly", "robot", "space")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Red, background = Paper, surface = Color.White, onSurface = Ink)) {
                CrayoScreen()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrayoScreen(vm: SearchViewModel = viewModel()) {
    val activity = LocalContext.current as Activity
    val focus = LocalFocusManager.current
    val shown by remember { derivedStateOf { vm.results.filter { vm.showWeak || it.looksOk } } }
    val goodCount by remember { derivedStateOf { vm.results.count { it.looksOk } } }
    val weakCount = vm.results.size - goodCount

    Scaffold(
        containerColor = Paper,
        bottomBar = { if (vm.selected.isNotEmpty()) PrintBar(vm, activity) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
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
                PageCard(result, vm.selected.contains(result.id)) { vm.toggle(result.id) }
            }
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
        fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 18.dp),
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 8.dp).height(52.dp)) {
        Text(text, color = Muted, textAlign = TextAlign.Center, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        if (weak > 0 && !vm.searching) {
            TextButton(onClick = { vm.showWeak = !vm.showWeak }) {
                Text(if (vm.showWeak) "Hide the rest" else "Show $weak more that may not print well", color = Red)
            }
        }
    }
}

@Composable
private fun PageCard(result: Result, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(if (selected) 3.dp else 1.5.dp, if (selected) Red else Line),
        modifier = Modifier.alpha(if (result.looksOk) 1f else 0.55f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(8.dp)) {
            Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                AsyncImage(model = result.preview, contentDescription = result.title, modifier = Modifier.fillMaxWidth().height(190.dp))
                if (selected) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(26.dp).clip(CircleShape).background(Red),
                        contentAlignment = Alignment.Center,
                    ) { Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            Text(result.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Text(result.source, fontSize = 11.sp, color = Muted, maxLines = 1)
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
                Text(
                    when {
                        vm.preparingPrint -> "Getting full size…"
                        count == 1 -> "Print page"
                        else -> "Print $count pages"
                    },
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}
