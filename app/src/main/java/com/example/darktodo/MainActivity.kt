package com.example.darktodo

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.darktodo.data.Task
import com.example.darktodo.data.TaskEvent
import com.example.darktodo.data.TaskStore
import com.example.darktodo.data.WidgetPrefs
import com.example.darktodo.data.WidgetSettings
import com.example.darktodo.ui.NunitoFamily
import com.example.darktodo.ui.composeWeight
import com.example.darktodo.widget.TodoWidget
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Bg = Color(0xFF000000)
private val Card = Color(0xFF171717)
private val Accent = Color(0xFFFF0000)
private val Ink = Color(0xFFF5F5F5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(color = Bg) { TodoApp() } } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun TodoApp() {
    val context = LocalContext.current
    var tasks by remember { mutableStateOf(TaskStore.tasks(context)) }
    var events by remember { mutableStateOf(TaskStore.history(context)) }
    var settings by remember { mutableStateOf(WidgetSettings.load(context)) }
    var page by remember { mutableStateOf("Tasks") }
    var title by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Task?>(null) }
    var route by remember { mutableStateOf(listOf<String>()) }
    var confirmClear by remember { mutableStateOf(false) }
    fun refresh() { tasks = TaskStore.tasks(context); events = TaskStore.history(context) }
    fun refreshSettings() { settings = WidgetSettings.load(context) }

    LaunchedEffect(settings) { TodoWidget().updateAll(context) }
    ProvideTextStyle(TextStyle(fontFamily = NunitoFamily, fontWeight = composeWeight(settings.fontWeight))) {
        Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Dark Todo", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { page = "Tasks"; refresh() }) { Text("Tasks", color = if (page == "Tasks") Accent else Ink) }
                TextButton(onClick = { page = "History"; route = emptyList(); refresh() }) { Text("History", color = if (page == "History") Accent else Ink) }
                TextButton(onClick = { page = "Settings"; refreshSettings() }) { Text("Settings", color = if (page == "Settings") Accent else Ink) }
            }
            when (page) {
                "Tasks" -> {
                    Text("${tasks.count { !it.completed }} incomplete", color = Accent, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(value = title, onValueChange = { title = it }, modifier = Modifier.weight(1f), singleLine = true, textStyle = TextStyle(color = Ink, fontSize = 16.sp, fontFamily = NunitoFamily), decorationBox = { inner -> if (title.isEmpty()) Text("Add a task", color = Color.Gray); inner() })
                        TextButton(onClick = { if (title.isNotBlank()) { TaskStore.add(context, title); title = ""; refresh() } }) { Text("Add", color = Accent) }
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tasks, key = { it.id }) { task ->
                            val taskColor = colorFromHex(settings.taskTitle)
                            val checkColor = colorFromHex(if (task.completed) settings.complete else settings.incomplete)
                            Row(Modifier.fillMaxWidth().graphicsLayer(alpha = if (task.completed) 1f - settings.fadePercent / 100f else 1f).background(Card, RoundedCornerShape(14.dp)).combinedClickable(onClick = { TaskStore.toggle(context, task.id); refresh() }, onLongClick = { if (!task.completed) { TaskStore.delete(context, task.id); refresh() } }).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (task.completed) "\u25cf" else "\u25cb", color = checkColor, fontSize = 22.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(task.title, color = taskColor, modifier = Modifier.weight(1f), textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None, fontWeight = composeWeight(settings.fontWeight))
                                TextButton(onClick = { editing = task }) { Text("Edit", color = Color.LightGray) }
                                if (task.completed) TextButton(onClick = { TaskStore.delete(context, task.id); refresh() }) { Text("X", color = Accent) }
                            }
                        }
                    }
                    Text("Long-press an incomplete task to delete it.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = { TaskStore.clearCompleted(context); refresh() }) { Text("Clear completed", color = Accent) }
                }
                "History" -> HistoryScreen(events, route, onRoute = { route = it }, onDelete = { TaskStore.deleteEvent(context, it); refresh() }, onClear = { scope -> TaskStore.deleteEvents(context, scope); refresh() }, onClearAll = { confirmClear = true })
                else -> SettingsScreen(
                    settings = settings,
                    onColor = { key, value -> WidgetSettings.saveColor(context, key, value); refreshSettings() },
                    onFade = { WidgetSettings.saveFade(context, it); refreshSettings() },
                    onWeight = { WidgetSettings.saveFontWeight(context, it); refreshSettings() },
                    onReset = { WidgetSettings.reset(context); refreshSettings() }
                )
            }
        }
    }
    LaunchedEffect(tasks) { TodoWidget().updateAll(context) }
    editing?.let { task ->
        var value by remember(task.id) { mutableStateOf(task.title) }
        AlertDialog(onDismissRequest = { editing = null }, containerColor = Card, title = { Text("Edit task", color = Ink) }, text = { androidx.compose.material3.OutlinedTextField(value, { value = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { TaskStore.rename(context, task.id, value); editing = null; refresh() }) { Text("Save", color = Accent) } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } })
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, containerColor = Card, title = { Text("Clear all history?", color = Ink) }, text = { Text("Active tasks and their completion status will not change.", color = Ink) }, confirmButton = { TextButton(onClick = { TaskStore.clearHistory(context); confirmClear = false; refresh() }) { Text("Delete history", color = Accent) } }, dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}

@Composable private fun SettingsScreen(settings: WidgetPrefs, onColor: (String, String) -> Unit, onFade: (Int) -> Unit, onWeight: (Int) -> Unit, onReset: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Widget colors (HEX)", color = Ink, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        val palette = listOf("FF0000", "000000", "FFFFFF", "171717", "F5F5F5", "2979FF", "00C853", "FFD600")
        ColorSetting("Incomplete checkbox", "incomplete", settings.incomplete, palette, onColor)
        ColorSetting("Completed checkbox", "complete", settings.complete, palette, onColor)
        ColorSetting("Task-count panel", "count_panel", settings.countPanel, palette, onColor)
        ColorSetting("Widget background", "background", settings.widgetBackground, palette, onColor)
        ColorSetting("Task card background", "task_card", settings.taskCard, palette, onColor)
        ColorSetting("Task title", "task_title", settings.taskTitle, palette, onColor)
        Spacer(Modifier.height(8.dp))
        Text("Completed task fading: ${settings.fadePercent}%", color = Ink)
        Slider(value = settings.fadePercent.toFloat(), onValueChange = { onFade(it.toInt()) }, valueRange = 0f..100f)
        Text("Font weight: ${weightName(settings.fontWeight)} (${settings.fontWeight})", color = Ink)
        var expanded by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { expanded = true }) { Text("Choose font weight", color = Accent) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf(300, 400, 500, 600, 700, 800, 900).forEach { weight ->
                    DropdownMenuItem(text = { Text("${weightName(weight)} ($weight)") }, onClick = { onWeight(weight); expanded = false })
                }
            }
        }
        Text("Nunito is bundled in the app and works offline. Android home-screen widgets use system fonts, so the widget uses the selected weight with the closest available system sans-serif style.", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        TextButton(onClick = onReset) { Text("Reset settings to defaults", color = Accent) }
    }
}

@Composable private fun ColorSetting(label: String, key: String, initial: String, palette: List<String>, onColor: (String, String) -> Unit) {
    var input by remember(key, initial) { mutableStateOf(initial.removePrefix("#")) }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).background(Card, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(label, color = Ink, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#", color = Color.LightGray)
            BasicTextField(value = input, onValueChange = { value ->
                input = value.filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }.take(6).uppercase()
                if (input.length == 6) onColor(key, input)
            }, modifier = Modifier.weight(1f).padding(8.dp), singleLine = true, textStyle = TextStyle(color = Ink, fontSize = 15.sp, fontFamily = NunitoFamily))
            Box(Modifier.size(22.dp).background(colorFromHex(initial), RoundedCornerShape(5.dp)))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            palette.forEach { hex ->
                TextButton(onClick = { input = hex; onColor(key, hex) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp), modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(18.dp).background(colorFromHex("#$hex"), RoundedCornerShape(5.dp)))
                }
            }
        }
    }
}

@Composable private fun HistoryScreen(events: List<TaskEvent>, route: List<String>, onRoute: (List<String>) -> Unit, onDelete: (String) -> Unit, onClear: (String) -> Unit, onClearAll: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val yearFmt = DateTimeFormatter.ofPattern("yyyy").withZone(zone)
    val monthFmt = DateTimeFormatter.ofPattern("yyyy-MM").withZone(zone)
    val dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)
    val shown = when (route.size) {
        0 -> events.map { yearFmt.format(Instant.ofEpochMilli(it.at)) }.distinct().sorted()
        1 -> events.map { monthFmt.format(Instant.ofEpochMilli(it.at)) }.filter { it.startsWith(route[0]) }.distinct().sorted()
        2 -> events.map { dayFmt.format(Instant.ofEpochMilli(it.at)) }.filter { it.startsWith(route[1]) }.distinct().sorted()
        else -> emptyList()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (route.isNotEmpty()) TextButton(onClick = { onRoute(route.dropLast(1)) }) { Text("< Back", color = Accent) }
        Text(if (route.isEmpty()) "Task History" else route.last(), color = Ink, fontSize = 18.sp)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onClearAll) { Text("Clear all", color = Accent) }
    }
    if (route.size < 3) {
        LazyColumn { items(shown) { value -> Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).background(Card, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Text(value, color = Ink, modifier = Modifier.weight(1f)); TextButton(onClick = { onClear(value) }) { Text("Delete", color = Accent) }; TextButton(onClick = { onRoute(route + value) }) { Text("Open >", color = Accent) } } } }
    } else {
        val dayEvents = events.filter { dayFmt.format(Instant.ofEpochMilli(it.at)) == route[2] }.sortedBy { it.at }
        TextButton(onClick = { onClear(route[2]) }) { Text("Delete this date", color = Accent) }
        LazyColumn { items(dayEvents, key = { it.id }) { event ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Card, RoundedCornerShape(12.dp)).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(DateTimeFormatter.ofPattern("hh:mm a").withZone(zone).format(Instant.ofEpochMilli(event.at)), color = Accent)
                    Spacer(Modifier.width(10.dp)); Text(event.type, color = Ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onDelete(event.id) }) { Text("Delete", color = Color.Gray) }
                }
                Text(event.title, color = Ink)
            }
        } }
    }
}

private fun colorFromHex(hex: String): Color = try { Color(AndroidColor.parseColor(if (hex.startsWith("#")) hex else "#$hex")) } catch (_: IllegalArgumentException) { Color.White }
private fun weightName(weight: Int): String = when (weight) { 300 -> "Light"; 400 -> "Regular"; 500 -> "Medium"; 600 -> "SemiBold"; 700 -> "Bold"; 800 -> "ExtraBold"; 900 -> "Black"; else -> "Regular" }
