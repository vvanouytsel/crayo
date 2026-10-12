package app.crayo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.crayo.color.OpenAi
import app.crayo.data.InkLevel

@Composable
internal fun SettingsScreen(vm: SearchViewModel, modifier: Modifier) {
    var showKey by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        TextButton(onClick = { vm.back() }) { Text("‹ Back", color = Red, fontWeight = FontWeight.SemiBold) }
        Text("Settings", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))

        Column(Modifier.padding(horizontal = 12.dp).widthIn(max = 560.dp)) {
            Text(
                "Color examples are chosen each time you print. These settings are used whenever you print one.",
                fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 8.dp),
            )

            Setting("OpenAI key", "Stays on this phone. Each example costs a little on your OpenAI account.")
            OutlinedTextField(
                value = vm.openAiKey,
                onValueChange = { vm.changeKey(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("sk-…", color = Muted) },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show", color = Red) } },
            )

            Setting("Model", "The OpenAI model that picks the colors.")
            ModelPicker(vm)

            if (BuildConfig.DEBUG) {
                Setting("Server", "Test builds only.")
                OutlinedTextField(
                    value = vm.server,
                    onValueChange = { vm.changeServer(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
            }

            Setting("Ink", "Lighter colors use less ink.")
            Choice(InkLevel.entries, vm.ink, { it.label }) { vm.choose(it) }
            Spacer(Modifier.height(10.dp))
            InkSample(vm.ink.wash)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A list of the models the key may use, loaded from OpenAI so it is always current. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPicker(vm: SearchViewModel) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it && vm.models.isNotEmpty() }) {
        OutlinedTextField(
            value = vm.model.ifBlank { OpenAi.DEFAULT_MODEL },
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            trailingIcon = {
                if (vm.loadingModels) CircularProgressIndicator(Modifier.size(20.dp), color = Red, strokeWidth = 2.dp)
                else ExposedDropdownMenuDefaults.TrailingIcon(expanded = open)
            },
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Color.White) {
            vm.models.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { vm.changeModel(name); open = false })
            }
        }
    }
    if (vm.modelsNote.isNotBlank()) Hint(vm.modelsNote)
}

@Composable
private fun Setting(name: String, hint: String) {
    Text(name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 22.dp))
    Text(hint, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> Choice(options: List<T>, current: T, label: (T) -> String, onPick: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == current,
                onClick = { onPick(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Red.copy(alpha = 0.14f), activeContentColor = Ink, activeBorderColor = Red,
                    inactiveContainerColor = Color.White,
                ),
            ) { Text(label(option)) }
        }
    }
}

/** A little drawing painted with the chosen ink level, so the difference is visible right away. */
@Composable
private fun InkSample(wash: Float) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.5.dp, Line)) {
        Canvas(Modifier.fillMaxWidth().height(170.dp).padding(12.dp)) {
            fun ink(color: Long) = lerp(Color(color), Color.White, wash)
            val w = size.width
            val h = size.height
            val line = Stroke(width = 4f)

            fun box(color: Long, left: Float, top: Float, width: Float, height: Float) {
                drawRect(ink(color), Offset(left, top), Size(width, height), style = Fill)
                drawRect(Color.Black, Offset(left, top), Size(width, height), style = line)
            }

            fun shape(color: Long, points: List<Offset>) {
                val path = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                drawPath(path, ink(color), style = Fill)
                drawPath(path, Color.Black, style = line)
            }

            box(0xFF6CBF5A, 0f, h * 0.80f, w, h * 0.20f)                                   // grass
            circle(ink(0xFFFFD23F), Offset(w * 0.86f, h * 0.22f), h * 0.15f, line)          // sun
            val left = w * 0.30f
            val houseW = w * 0.30f
            box(0xFFF29B38, left, h * 0.42f, houseW, h * 0.38f)                             // wall
            shape(0xFFD7261E, listOf(Offset(left - w * 0.03f, h * 0.42f), Offset(left + houseW / 2, h * 0.10f), Offset(left + houseW + w * 0.03f, h * 0.42f)))   // roof
            box(0xFF8A5A3B, left + houseW * 0.38f, h * 0.56f, houseW * 0.24f, h * 0.24f)    // door
            box(0xFF7CC4F0, left + houseW * 0.70f, h * 0.50f, houseW * 0.20f, h * 0.14f)    // window
            shape(0xFF3F9E4A, listOf(Offset(w * 0.08f, h * 0.80f), Offset(w * 0.14f, h * 0.36f), Offset(w * 0.20f, h * 0.80f)))                                   // tree
        }
    }
}

private fun DrawScope.circle(color: Color, center: Offset, radius: Float, line: Stroke) {
    drawCircle(color, radius, center)
    drawCircle(Color.Black, radius, center, style = line)
}
