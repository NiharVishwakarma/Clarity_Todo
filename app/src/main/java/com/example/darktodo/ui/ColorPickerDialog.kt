package com.example.darktodo.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private val PickerInk = Color(0xFFF5F5F5)
private val PickerCard = Color(0xFF171717)
private val PickerAccent = Color(0xFFF20D24)
private val QuickColors = listOf("#FFFFFF", "#000000", "#F20D24", "#35D07F", "#FF9F1C", "#2979FF")

/** Shared HSV picker used by both widget settings; values are committed only on Apply. */
@Composable
fun ColorPickerDialog(
    label: String,
    currentColor: String,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val start = remember(currentColor) { runCatching { AndroidColor.parseColor(currentColor) }.getOrDefault(AndroidColor.WHITE) }
    val startHsv = remember(start) { FloatArray(3).also { AndroidColor.colorToHSV(start, it) } }
    var hue by remember(currentColor) { mutableFloatStateOf(startHsv[0]) }
    var saturation by remember(currentColor) { mutableFloatStateOf(startHsv[1]) }
    var brightness by remember(currentColor) { mutableFloatStateOf(startHsv[2]) }
    var hex by remember(currentColor) { mutableStateOf("#%06X".format(start and 0xFFFFFF)) }
    var validHex by remember(currentColor) { mutableStateOf(true) }
    val selected = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)))

    fun selectHex(value: String) {
        val normalized = value.trim().let { if (it.startsWith("#")) it.uppercase() else "#${it.uppercase()}" }
        hex = value.uppercase().take(7)
        if (normalized.matches(Regex("^#[0-9A-F]{6}$"))) {
            val color = AndroidColor.parseColor(normalized)
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(color, hsv)
            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
            hex = normalized
            validHex = true
        } else validHex = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PickerCard,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Choose $label", color = PickerInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Canvas(
                    Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(12.dp))
                        .pointerInput(hue) {
                            fun choose(position: Offset) {
                                saturation = (position.x / size.width).coerceIn(0f, 1f)
                                brightness = (1f - position.y / size.height).coerceIn(0f, 1f)
                                hex = "#%06X".format(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)) and 0xFFFFFF)
                                validHex = true
                            }
                            detectTapGestures { choose(it) }
                        }
                        .pointerInput(hue) {
                            fun choose(position: Offset) {
                                saturation = (position.x / size.width).coerceIn(0f, 1f)
                                brightness = (1f - position.y / size.height).coerceIn(0f, 1f)
                                hex = "#%06X".format(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)) and 0xFFFFFF)
                                validHex = true
                            }
                            detectDragGestures { change, _ -> change.consume(); choose(change.position) }
                        }
                ) {
                    drawRect(Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))))
                    drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val x = saturation * size.width
                    val y = (1f - brightness) * size.height
                    drawCircle(Color.Black.copy(alpha = .75f), 10.dp.toPx(), Offset(x, y), style = Stroke(3.dp.toPx()))
                    drawCircle(Color.White, 8.dp.toPx(), Offset(x, y), style = Stroke(2.dp.toPx()))
                }

                Canvas(
                    Modifier.fillMaxWidth().height(24.dp)
                        .pointerInput(Unit) {
                            fun choose(x: Float) {
                                hue = (x / size.width).coerceIn(0f, 1f) * 360f
                                hex = "#%06X".format(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)) and 0xFFFFFF)
                                validHex = true
                            }
                            detectTapGestures { choose(it.x) }
                        }
                        .pointerInput(Unit) {
                            fun choose(x: Float) {
                                hue = (x / size.width).coerceIn(0f, 1f) * 360f
                                hex = "#%06X".format(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)) and 0xFFFFFF)
                                validHex = true
                            }
                            detectDragGestures { change, _ -> change.consume(); choose(change.position.x) }
                        }
                ) {
                    val colors = (0..6).map { Color(AndroidColor.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }
                    drawRoundRect(Brush.horizontalGradient(colors), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f))
                    val x = hue / 360f * size.width
                    drawCircle(Color.White, 10.dp.toPx(), Offset(x, size.height / 2f))
                    drawCircle(Color.Black, 8.dp.toPx(), Offset(x, size.height / 2f), style = Stroke(2.dp.toPx()))
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(selected).border(1.dp, Color.White.copy(alpha = .35f), RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f)) {
                        Text("HEX color", color = Color.LightGray)
                        BasicTextField(
                            value = hex,
                            onValueChange = ::selectHex,
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = PickerInk),
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp).background(Color.Black, RoundedCornerShape(6.dp)).padding(9.dp)
                        )
                        if (!validHex) Text("Enter a six-digit hex color", color = PickerAccent)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    QuickColors.forEach { value ->
                        Box(
                            Modifier.size(27.dp).clip(CircleShape).background(Color(AndroidColor.parseColor(value)))
                                .border(if (hex.equals(value, true)) 2.dp else 1.dp, Color.White.copy(alpha = .75f), CircleShape)
                                .pointerInput(value) { detectTapGestures { selectHex(value) } }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (validHex) onApply(hex) }, enabled = validHex) { Text("Apply", color = PickerAccent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PickerInk) } }
    )
}
