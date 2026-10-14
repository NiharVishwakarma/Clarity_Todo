package com.example.darktodo

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.darktodo.data.Task
import com.example.darktodo.data.TaskEvent
import com.example.darktodo.data.TaskStore
import com.example.darktodo.data.TaskTimeFormatter
import com.example.darktodo.data.HistoryDateFormatter
import com.example.darktodo.data.TimeZoneCatalog
import com.example.darktodo.data.TimeZoneOption
import com.example.darktodo.data.WidgetPrefs
import com.example.darktodo.data.WidgetSettings
import com.example.darktodo.ui.NunitoFamily
import com.example.darktodo.ui.composeWeight
import com.example.darktodo.widget.TodoWidget
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val Bg = Color(0xFF000000)
private val Card = Color(0xFF171717)
private val Accent = Color(0xFFFF0000)
private val Ink = Color(0xFFF5F5F5)

class MainActivity : ComponentActivity() {
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            TodoWidget.updateAll(context)
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(screenOnReceiver)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
        com.example.darktodo.reminders.ReminderScheduler.restore(this)
        setContent { MaterialTheme { Surface(color = Bg) { TodoApp(intent.getStringExtra("edit_task_id")) } } }
    }

    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent) }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) android.widget.Toast.makeText(this, "Notifications are off. Enable them in Settings to receive task reminders.", android.widget.Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun TodoApp(openTaskId: String? = null) {
    val context = LocalContext.current
    var tasks by remember { mutableStateOf(TaskStore.tasks(context)) }
    var events by remember { mutableStateOf(TaskStore.history(context)) }
    var settings by remember { mutableStateOf(WidgetSettings.load(context)) }
    var page by remember { mutableStateOf("Tasks") }
    var title by remember { mutableStateOf("") }
    var timeTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var actionTask by remember { mutableStateOf<Task?>(null) }
    var confirmDeleteTask by remember { mutableStateOf<Task?>(null) }
    var addReminder by remember { mutableStateOf(false) }
    var route by remember { mutableStateOf(listOf<String>()) }
    var confirmClear by remember { mutableStateOf(false) }
    fun refresh() { tasks = TaskStore.tasks(context); events = TaskStore.history(context) }
    fun refreshSettings() { settings = WidgetSettings.load(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            timeTick = System.currentTimeMillis()
            com.example.darktodo.reminders.ReminderScheduler.restore(context)
            refresh()
        } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(60_000L)
                timeTick = System.currentTimeMillis()
                TodoWidget.updateAll(context)
            }
        }
    }
    LaunchedEffect(openTaskId, tasks) { if (openTaskId != null) editing = tasks.firstOrNull { it.id == openTaskId } }

    LaunchedEffect(settings) { TodoWidget.updateAll(context) }
    val selectedFontStyle = if (settings.fontItalic) FontStyle.Italic else FontStyle.Normal
    val iconColor = colorFromHex(settings.incomplete)
    val caretColor = if (iconColor.isBlackOrGray()) Color.White else iconColor
    ProvideTextStyle(TextStyle(fontFamily = NunitoFamily, fontWeight = composeWeight(settings.fontWeight), fontStyle = selectedFontStyle)) {
        Column(Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(18.dp)) {
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
                        BasicTextField(value = title, onValueChange = { title = it }, modifier = Modifier.weight(1f), singleLine = true, cursorBrush = SolidColor(caretColor), textStyle = TextStyle(color = Ink, fontSize = settings.taskTitleSizeSp.sp, fontFamily = NunitoFamily, fontWeight = composeWeight(settings.fontWeight), fontStyle = selectedFontStyle), decorationBox = { inner -> if (title.isEmpty()) Text("Add a task", color = Color.Gray); inner() })
                        TextButton(onClick = { if (title.isNotBlank()) addReminder = true }) { Text("Add", color = Accent) }
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tasks, key = { it.id }) { task ->
                            val taskColor = colorFromHex(settings.taskTitle)
                            val checkColor = colorFromHex(if (task.completed) settings.complete else settings.incomplete)
                            val groupPosition = tasks.filter { it.completed == task.completed }.indexOfFirst { it.id == task.id }
                            val groupSize = tasks.count { it.completed == task.completed }
                            Row(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { actionTask = task }).graphicsLayer(alpha = if (task.completed) 1f - settings.fadePercent / 100f else 1f).background(Card, RoundedCornerShape(14.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (task.completed) "\u25cf" else "\u25cb", modifier = Modifier.clickable { TaskStore.toggle(context, task.id); refresh() }, color = checkColor, fontSize = 22.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f).clickable { editing = task }) {
                                    Text(task.title, color = taskColor, textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None, fontWeight = composeWeight(settings.fontWeight), fontSize = settings.taskTitleSizeSp.sp)
                                    TaskTimeFormatter.label(context, task, wallNow = timeTick)?.let { Text(it, color = if (it == "OVERDUE") Accent else Color.LightGray, fontSize = 11.sp) }
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    TextButton(onClick = { TaskStore.move(context, task.id, -1); refresh() }, enabled = groupPosition > 0, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) { Text("\u2191", color = if (groupPosition > 0) Color.LightGray else Color.DarkGray) }
                                    TextButton(onClick = { TaskStore.move(context, task.id, 1); refresh() }, enabled = groupPosition < groupSize - 1, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) { Text("\u2193", color = if (groupPosition < groupSize - 1) Color.LightGray else Color.DarkGray) }
                                }
                                if (task.completed) TextButton(onClick = { TaskStore.delete(context, task.id); refresh() }) { Text("X", color = Accent) }
                            }
                        }
                    }
                    Text("Use arrows to reorder tasks. Long-press a task for actions.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = { TaskStore.clearCompleted(context); refresh() }) { Text("Clear completed", color = Accent) }
                }
                "History" -> HistoryScreen(events, route, onRoute = { route = it }, onDelete = { TaskStore.deleteEvent(context, it); refresh() }, onClear = { scope -> TaskStore.deleteEvents(context, scope); refresh() }, onClearAll = { confirmClear = true })
                else -> SettingsScreen(
                    settings = settings,
                    onColor = { key, value -> WidgetSettings.saveColor(context, key, value); refreshSettings() },
                    onFade = { WidgetSettings.saveFade(context, it); refreshSettings() },
                    onWeight = { WidgetSettings.saveFontWeight(context, it); refreshSettings() },
                    onItalic = { WidgetSettings.saveFontItalic(context, it); refreshSettings() },
                    onTaskTitleSize = { WidgetSettings.saveTaskTitleSize(context, it); refreshSettings() },
                    onReset = { WidgetSettings.reset(context); refreshSettings() }
                )
            }
        }
    }
    LaunchedEffect(tasks) { TodoWidget.updateAll(context) }
    editing?.let { task ->
        var value by remember(task.id) { mutableStateOf(task.title) }
        var kind by remember(task.id) { mutableStateOf(if (task.completed) "none" else task.reminderType) }
        val localPickerInstant = remember(task.id) { runCatching { LocalDateTime.parse(task.localDateTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull() }
        var deadline by remember(task.id) { mutableLongStateOf((if (task.reminderType == "time") localPickerInstant ?: task.reminderAt else task.deadlineAt).takeIf { it > System.currentTimeMillis() } ?: System.currentTimeMillis() + 3_600_000L) }
        var timeZone by remember(task.id) { mutableStateOf(if (task.followsDeviceTimeZone) "" else task.timeZoneId) }
        val remainMinutes = ((TaskTimeFormatter.remainingMillis(context, task).coerceAtLeast(0L) + 59_999L) / 60_000L).coerceAtLeast(1L)
        var hours by remember(task.id) { mutableStateOf((remainMinutes / 60L).toString()) }; var minutes by remember(task.id) { mutableStateOf((remainMinutes % 60L).toString()) }; var error by remember(task.id) { mutableStateOf("") }
        ReminderDialog(value, { value = it }, kind, { kind = it }, deadline, { deadline = it }, hours, { hours = it }, minutes, { minutes = it }, timeZone, { timeZone = it }, error, { error = it }, { editing = null }, {
            val at = when (kind) { "time" -> deadline; "duration" -> System.currentTimeMillis() + ((hours.toLongOrNull() ?: 0L) * 60 + (minutes.toLongOrNull() ?: 0L)) * 60_000L; else -> 0L }
            val badZone = kind == "time" && timeZone.isNotBlank() && runCatching { ZoneId.of(timeZone.trim()) }.isFailure
            if (value.isBlank()) error = "Enter a task name" else if (badZone) error = "Enter a valid IANA time zone, such as Europe/London" else if (kind == "time" && at <= System.currentTimeMillis()) error = "Choose a future time" else if (kind == "duration" && at <= System.currentTimeMillis()) error = "Enter a duration greater than zero" else { TaskStore.configure(context, task.id, value, kind, at, timeZone.trim()); editing = null; refresh() }
        }, task.completed)
    }
    if (addReminder) {
        var kind by remember { mutableStateOf("none") }; var deadline by remember { mutableLongStateOf(System.currentTimeMillis() + 3_600_000L) }; var hours by remember { mutableStateOf("1") }; var minutes by remember { mutableStateOf("0") }; var timeZone by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }
        ReminderDialog(title, { title = it }, kind, { kind = it }, deadline, { deadline = it }, hours, { hours = it }, minutes, { minutes = it }, timeZone, { timeZone = it }, error, { error = it }, { addReminder = false }, {
            val at = when (kind) { "time" -> deadline; "duration" -> System.currentTimeMillis() + ((hours.toLongOrNull() ?: 0L) * 60 + (minutes.toLongOrNull() ?: 0L)) * 60_000L; else -> 0L }
            val badZone = kind == "time" && timeZone.isNotBlank() && runCatching { ZoneId.of(timeZone.trim()) }.isFailure
            if (title.isBlank()) error = "Enter a task name" else if (badZone) error = "Enter a valid IANA time zone, such as Europe/London" else if (kind == "time" && at <= System.currentTimeMillis()) error = "Choose a future time" else if (kind == "duration" && at <= System.currentTimeMillis()) error = "Enter a duration greater than zero" else { TaskStore.addConfigured(context, title, kind, at, timeZone.trim()); title = ""; addReminder = false; refresh() }
        }, false)
    }
    actionTask?.let { task ->
        AlertDialog(onDismissRequest = { actionTask = null }, containerColor = Card, shape = RoundedCornerShape(20.dp), title = { Text(task.title, color = Ink) },
            confirmButton = { TextButton(onClick = { actionTask = null; editing = task }) { Text("Edit task", color = Accent) } },
            dismissButton = { TextButton(onClick = { actionTask = null; confirmDeleteTask = task }) { Text("Delete task", color = Accent) } })
    }
    confirmDeleteTask?.let { task ->
        AlertDialog(onDismissRequest = { confirmDeleteTask = null }, containerColor = Card, shape = RoundedCornerShape(20.dp), title = { Text("Delete task?", color = Ink) }, text = { Text("\"${task.title}\" will be permanently deleted.", color = Ink) },
            confirmButton = { TextButton(onClick = { TaskStore.delete(context, task.id); confirmDeleteTask = null; refresh() }) { Text("Delete task", color = Accent) } },
            dismissButton = { TextButton(onClick = { confirmDeleteTask = null }) { Text("Cancel", color = Ink) } })
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, containerColor = Card, title = { Text("Clear all history?", color = Ink) }, text = { Text("Active tasks and their completion status will not change.", color = Ink) }, confirmButton = { TextButton(onClick = { TaskStore.clearHistory(context); confirmClear = false; refresh() }) { Text("Delete history", color = Accent) } }, dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}

@Composable private fun ReminderDialog(value: String, onValue: (String) -> Unit, kind: String, onKind: (String) -> Unit, deadline: Long, onDeadline: (Long) -> Unit, hours: String, onHours: (String) -> Unit, minutes: String, onMinutes: (String) -> Unit, timeZone: String, onTimeZone: (String) -> Unit, error: String, onError: (String) -> Unit, dismiss: () -> Unit, save: () -> Unit, completed: Boolean) {
    val context = LocalContext.current
    var showTimeZonePicker by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, containerColor = Card, title = { Text(if (completed) "Edit task" else "Task and reminder", color = Ink) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.OutlinedTextField(value, onValue, label = { Text("Task name") }, singleLine = true, textStyle = TextStyle(color = Ink, fontFamily = NunitoFamily))
            Text("Reminder", color = Ink, fontSize = 14.sp)
            listOf("none" to "No reminder", "time" to "At a specific time", "duration" to "Countdown deadline").forEach { (key, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { if (!completed) onKind(key) }) {
                    RadioButton(selected = kind == key, onClick = { if (!completed) onKind(key) }, enabled = !completed); Text(label, color = if (completed) Color.Gray else Ink, fontSize = 14.sp)
                }
            }
            if (kind == "time" && !completed) TextButton(onClick = {
                val current = java.util.Calendar.getInstance().apply { timeInMillis = deadline }
                DatePickerDialog(context, { _, year, month, day ->
                    val date = java.util.Calendar.getInstance().apply { set(year, month, day) }
                    TimePickerDialog(context, { _, hour, minute -> date.set(java.util.Calendar.HOUR_OF_DAY, hour); date.set(java.util.Calendar.MINUTE, minute); date.set(java.util.Calendar.SECOND, 0); onDeadline(date.timeInMillis) }, current.get(java.util.Calendar.HOUR_OF_DAY), current.get(java.util.Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(context)).show()
                }, current.get(java.util.Calendar.YEAR), current.get(java.util.Calendar.MONTH), current.get(java.util.Calendar.DAY_OF_MONTH)).show()
            }) { Text("Choose date and time: ${TaskStore.dateTime(context, deadline, timeZone)} (${if (timeZone.isBlank()) friendlyZoneName(ZoneId.systemDefault().id) else TimeZoneCatalog.labelFor(timeZone) ?: timeZone})", color = Accent, fontSize = 12.sp) }
            if (kind == "time" && !completed) {
                val selectedZone = if (timeZone.isBlank()) "Device time zone - ${friendlyZoneName(ZoneId.systemDefault().id)}" else TimeZoneCatalog.labelFor(timeZone) ?: "Unavailable time zone - $timeZone"
                androidx.compose.material3.OutlinedTextField(
                    value = selectedZone,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().clickable { showTimeZonePicker = true },
                    readOnly = true,
                    label = { Text("Time zone (optional)") },
                    trailingIcon = { TextButton(onClick = { showTimeZonePicker = true }) { Text("Choose", color = Accent) } },
                    singleLine = true,
                    textStyle = TextStyle(color = Ink, fontFamily = NunitoFamily),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Accent, focusedBorderColor = Color.Gray, unfocusedBorderColor = Color.Gray, focusedLabelColor = Color.LightGray, unfocusedLabelColor = Color.LightGray)
                )
                if (timeZone.isNotBlank()) TextButton(onClick = { onTimeZone("") }) { Text("Use device time zone", color = Accent) }
                Text("Blank follows this device. Skipped local times move forward; repeated times use the earlier occurrence.", color = Color.LightGray, fontSize = 11.sp)
            }
            if (kind == "duration" && !completed) {
                val focusAccent = Color(0xFF7E57C2)
                val fieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Ink, unfocusedTextColor = Ink, disabledTextColor = Color.Gray, errorTextColor = Ink,
                    cursorColor = focusAccent, errorCursorColor = Accent,
                    focusedBorderColor = focusAccent, unfocusedBorderColor = Color.Gray, disabledBorderColor = Color.DarkGray, errorBorderColor = Accent,
                    focusedLabelColor = focusAccent, unfocusedLabelColor = Color.LightGray, disabledLabelColor = Color.Gray, errorLabelColor = Accent,
                    focusedPlaceholderColor = Color(0xFF9E9E9E), unfocusedPlaceholderColor = Color(0xFF9E9E9E)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(hours, { onHours(it.filter(Char::isDigit).take(4)) }, label = { Text("Hours") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(color = Ink, fontFamily = NunitoFamily), colors = fieldColors)
                    Spacer(Modifier.width(8.dp)); androidx.compose.material3.OutlinedTextField(minutes, { onMinutes(it.filter(Char::isDigit).take(2)) }, label = { Text("Minutes") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(color = Ink, fontFamily = NunitoFamily), colors = fieldColors)
                }
            }
            if (completed) Text("Reopened/completed tasks need a new reminder configured explicitly after reopening.", color = Color.Gray, fontSize = 12.sp)
            if (error.isNotEmpty()) Text(error, color = Accent, fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = save) { Text("Save", color = Accent) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel", color = Ink) } })
    if (showTimeZonePicker) TimeZonePickerDialog(
        selectedId = timeZone,
        onSelect = { onTimeZone(it); showTimeZonePicker = false },
        onDismiss = { showTimeZonePicker = false }
    )
}

@Composable private fun TimeZonePickerDialog(selectedId: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        title = { Text("Choose time zone", color = Ink) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search city, country or zone") },
                    singleLine = true,
                    textStyle = TextStyle(color = Ink, fontFamily = NunitoFamily),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Accent,
                        focusedBorderColor = Accent, unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Accent, unfocusedLabelColor = Color.LightGray
                    )
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 360.dp)) {
                    if (query.isBlank()) {
                        item { TimeZoneSectionHeader("Popular") }
                        items(TimeZoneCatalog.popular, key = { it.id }) { zone -> TimeZoneChoice(zone, selectedId == zone.id) { onSelect(zone.id) } }
                        item { TimeZoneSectionHeader("All available time zones") }
                        items(TimeZoneCatalog.other, key = { it.id }) { zone -> TimeZoneChoice(zone, selectedId == zone.id) { onSelect(zone.id) } }
                    } else {
                        val matches = TimeZoneCatalog.search(query)
                        if (matches.isEmpty()) item { Text("No matching time zones", color = Color.LightGray, modifier = Modifier.padding(12.dp)) }
                        items(matches, key = { it.id }) { zone -> TimeZoneChoice(zone, selectedId == zone.id) { onSelect(zone.id) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSelect("") }) { Text("Use device", color = Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ink) } }
    )
}

@Composable private fun TimeZoneSectionHeader(title: String) {
    Text(title, color = Accent, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().background(Card).padding(top = 12.dp, bottom = 4.dp))
}

@Composable private fun TimeZoneChoice(zone: TimeZoneOption, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).background(if (selected) Color(0xFF292020) else Card).padding(horizontal = 8.dp, vertical = 7.dp)) {
        Text(zone.label, color = if (selected) Accent else Ink, fontSize = 14.sp)
        Text(zone.id, color = Color.LightGray, fontSize = 11.sp)
    }
}

private fun friendlyZoneName(id: String): String = TimeZoneCatalog.labelFor(id) ?: id.replace('_', ' ')

@Composable private fun SettingsScreen(settings: WidgetPrefs, onColor: (String, String) -> Unit, onFade: (Int) -> Unit, onWeight: (Int) -> Unit, onItalic: (Boolean) -> Unit, onTaskTitleSize: (Float) -> Unit, onReset: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Widget colors (HEX)", color = Ink, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        val palette = listOf("FF0000", "000000", "FFFFFF", "171717", "F5F5F5", "2979FF", "00C853", "FFD600")
        ColorSetting("Incomplete checkbox", "incomplete", settings.incomplete, palette, settings, onColor)
        ColorSetting("Completed checkbox", "complete", settings.complete, palette, settings, onColor)
        ColorSetting("Task-count panel", "count_panel", settings.countPanel, palette, settings, onColor)
        ColorSetting("Widget background", "background", settings.widgetBackground, palette, settings, onColor)
        ColorSetting("Task card background", "task_card", settings.taskCard, palette, settings, onColor)
        ColorSetting("Task title", "task_title", settings.taskTitle, palette, settings, onColor)
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
        Text("Task title size: ${settings.taskTitleSizeSp.toInt()} sp", color = Ink)
        Slider(value = settings.taskTitleSizeSp, onValueChange = onTaskTitleSize, valueRange = 12f..24f, steps = 11)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Italic task text", color = Ink)
            Switch(
                checked = settings.fontItalic,
                onCheckedChange = onItalic,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Ink,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = Color.LightGray,
                    uncheckedTrackColor = Card
                )
            )
        }
        Text("Reminders need notification permission. Android may delay inexact alarms during Doze or battery restrictions.", color = Color.LightGray, fontSize = 12.sp)
        TextButton(onClick = {
            val intent = if (Build.VERSION.SDK_INT >= 26) android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName) else android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
        }) { Text("Open notification settings", color = Accent) }
        if (Build.VERSION.SDK_INT >= 31) TextButton(onClick = {
            runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))) }
        }) { Text("Manage exact alarm access", color = Accent) }
        TextButton(onClick = onReset) { Text("Reset settings to defaults", color = Accent) }
    }
}

@Composable private fun ColorSetting(label: String, key: String, initial: String, palette: List<String>, settings: WidgetPrefs, onColor: (String, String) -> Unit) {
    var input by remember(key, initial) { mutableStateOf(initial.removePrefix("#")) }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).background(Card, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(label, color = Ink, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#", color = Color.LightGray)
            BasicTextField(value = input, onValueChange = { value ->
                input = value.filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }.take(6).uppercase()
                if (input.length == 6) onColor(key, input)
            }, modifier = Modifier.weight(1f).padding(8.dp), singleLine = true, textStyle = TextStyle(color = Ink, fontSize = 15.sp, fontFamily = NunitoFamily, fontWeight = composeWeight(settings.fontWeight), fontStyle = if (settings.fontItalic) FontStyle.Italic else FontStyle.Normal))
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
        Text(
            if (route.isEmpty()) "Task History" else if (route.size == 3) HistoryDateFormatter.format(route.last()) else route.last(),
            color = Ink,
            fontSize = 18.sp
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onClearAll) { Text("Clear all", color = Accent) }
    }
    if (route.size < 3) {
        LazyColumn { items(shown) { value -> Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).background(Card, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Text(if (route.size == 2) HistoryDateFormatter.format(value) else value, color = Ink, modifier = Modifier.weight(1f)); TextButton(onClick = { onClear(value) }) { Text("Delete", color = Accent) }; TextButton(onClick = { onRoute(route + value) }) { Text("Open >", color = Accent) } } } }
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

private fun Color.isBlackOrGray(): Boolean =
    maxOf(red, green, blue) - minOf(red, green, blue) <= 0.10f
private fun weightName(weight: Int): String = when (weight) { 300 -> "Light"; 400 -> "Regular"; 500 -> "Medium"; 600 -> "SemiBold"; 700 -> "Bold"; 800 -> "ExtraBold"; 900 -> "Black"; else -> "Regular" }
