package com.example.darktodo

import android.graphics.Color as AndroidColor
import android.graphics.Typeface as AndroidTypeface
import android.graphics.Paint as AndroidPaint
import android.text.TextUtils
import android.view.Gravity
import android.widget.TextView
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import com.example.darktodo.data.Task
import com.example.darktodo.data.TaskEvent
import com.example.darktodo.data.TaskStore
import com.example.darktodo.data.TaskTimeFormatter
import com.example.darktodo.data.CountdownEditPolicy
import com.example.darktodo.data.HistoryDateFormatter
import com.example.darktodo.data.TimeZoneCatalog
import com.example.darktodo.data.TimeZoneOption
import com.example.darktodo.data.WidgetPrefs
import com.example.darktodo.data.WidgetFontFamily
import com.example.darktodo.data.WidgetSettings
import com.example.darktodo.data.AppFontSettings
import com.example.darktodo.data.AppTypographySettings
import com.example.darktodo.data.CalendarWidgetSettings
import com.example.darktodo.data.CalendarDates
import com.example.darktodo.data.CalendarTaskStatus
import com.example.darktodo.data.ImportedWidgetFont
import com.example.darktodo.data.LocalWidgetFonts
import com.example.darktodo.data.LocalVaultStore
import com.example.darktodo.data.VaultEntry
import com.example.darktodo.ui.ColorPickerDialog
import com.example.darktodo.navigation.BackStep
import com.example.darktodo.navigation.ModuleNavigation
import com.example.darktodo.navigation.ModulePrefs
import androidx.activity.compose.BackHandler
import com.example.darktodo.ui.ClarityIcons
import com.example.darktodo.ui.ModuleTabBar
import com.example.darktodo.ui.SettingsCategoryHeader
import com.example.darktodo.ui.SettingsNavRow
import com.example.darktodo.widget.TodoWidget
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal object UiTokens {
    val Background = Color(0xFF0D0D10)
    val Surface = Color(0xFF19191E)
    val SurfaceRaised = Color(0xFF222228)
    val Accent = Color(0xFFF20D24)
    val Secondary = Color(0xFFAA91C6)
    val Ink = Color(0xFFF4F3F5)
    val Muted = Color(0xFFAAA8B0)
    val Subdued = Color(0xFF817F88)
    val PageMargin = 18.dp
    val SectionGap = 22.dp
    val ControlGap = 10.dp
    val CompactGap = 4.dp
    val SmallGap = 8.dp
    val CardRadius = 16.dp
    val RowRadius = 12.dp
    val SegmentRadius = 9.dp
    val ControlHeight = 52.dp
    val NavigationHeight = 40.dp
    val PanelPadding = 14.dp
    val SwatchSize = 24.dp
    val SwatchRadius = 7.dp
    val SegmentHeight = 40.dp
    const val PageTitleSp = 24f
    const val SectionTitleSp = 17f
    const val LabelSp = 14f
    const val DetailSp = 12f
}
private val Bg = UiTokens.Background
private val Card = UiTokens.Surface
private val Accent = UiTokens.Accent
private val Ink = UiTokens.Ink
class MainActivity : FragmentActivity() {
    companion object {
        const val ACTION_CREATE_TASK = "com.example.darktodo.action.CREATE_TASK"
        const val ACTION_OPEN_VAULT = "com.example.darktodo.action.OPEN_VAULT"
        const val ACTION_OPEN_NOTE = "com.example.darktodo.action.OPEN_NOTE"
        const val ACTION_OPEN_INFO = ModuleNavigation.ACTION_OPEN_INFO
        const val EXTRA_NOTE_ID = "note_id"
    }

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
        if (intent.action == com.example.darktodo.widget.CalendarWidget.ACTION_DATE) {
            com.example.darktodo.widget.CalendarWidget.selectDate(this, intent.getIntExtra(com.example.darktodo.widget.CalendarWidget.EXTRA_WIDGET, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID), intent.getStringExtra("task_date") ?: return)
        }
        val appContext = applicationContext
        kotlin.concurrent.thread(name = "reminder-restore") {
            runCatching { com.example.darktodo.reminders.ReminderScheduler.restore(appContext) }
                .onFailure { android.util.Log.e("MainActivity", "Reminder recovery failed", it) }
        }
        setContent { MaterialTheme { Surface(color = Bg) {
            var welcome by remember {
                mutableStateOf(intent.action != ACTION_OPEN_VAULT && com.example.darktodo.backup.BackupManager.needsWelcome(this@MainActivity))
            }
            if (welcome) WelcomeBackGate { restored ->
                if (restored) recreate() else welcome = false
            } else TodoApp(intent.getStringExtra("edit_task_id"), intent.getStringExtra("task_date"), intent.action == ACTION_CREATE_TASK, intent.action == ACTION_OPEN_VAULT,
                if (intent.action == ACTION_OPEN_NOTE) intent.getStringExtra(EXTRA_NOTE_ID) else null, intent.action == ACTION_OPEN_NOTE,
                ModuleNavigation.intentModule(intent.action) == ModuleNavigation.INFO)
        } } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_VAULT || intent.action == ACTION_OPEN_NOTE ||
            ModuleNavigation.intentModule(intent.action) == ModuleNavigation.INFO) recreate()
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun TodoApp(openTaskId: String? = null, openTaskDate: String? = null, startCreateTask: Boolean = false, openVault: Boolean = false, openNoteId: String? = null, openNotes: Boolean = false, openInfo: Boolean = false) {
    val context = LocalContext.current
    var tasks by remember { mutableStateOf(TaskStore.tasks(context)) }
    var events by remember { mutableStateOf(TaskStore.history(context)) }
    var settings by remember { mutableStateOf(WidgetSettings.load(context)) }
    val startRoute = remember {
        when {
            openVault -> ModuleNavigation.routeFor(ModuleNavigation.VAULT)
            openInfo -> ModuleNavigation.routeFor(ModuleNavigation.INFO)
            openNotes || openNoteId != null -> ModuleNavigation.routeFor(ModuleNavigation.STICKERS)
            openTaskId != null || openTaskDate != null || startCreateTask -> ModuleNavigation.home
            else -> ModulePrefs.last(context)?.let { ModuleNavigation.routeFor(it) } ?: ModuleNavigation.routeFor(ModuleNavigation.CALENDAR)
        }
    }
    var page by remember { mutableStateOf(startRoute.page) }
    var todoPage by remember { mutableStateOf(startRoute.todoPage) }
    var wallpaperTarget by remember { mutableStateOf(com.example.darktodo.data.WidgetBackgrounds.TODO) }
    var noteSettingsId by remember { mutableStateOf<String?>(null) }
    var pendingNoteId by remember { mutableStateOf(openNoteId) }
    var title by remember { mutableStateOf("") }
    var timeTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var actionTask by remember { mutableStateOf<Task?>(null) }
    var confirmDeleteTask by remember { mutableStateOf<Task?>(null) }
    var addReminder by remember { mutableStateOf(false) }
    var route by remember { mutableStateOf(listOf<String>()) }
    var confirmClear by remember { mutableStateOf(false) }
    var dateFilter by remember { mutableStateOf(openTaskDate) }
    var appFontSettings by remember { mutableStateOf(AppTypographySettings.load(context)) }
    var calendarSettings by remember { mutableStateOf(CalendarWidgetSettings.load(context)) }
    var importedWidgetFonts by remember { mutableStateOf(LocalWidgetFonts.list(context)) }
    var fontImportTarget by remember { mutableStateOf<String?>(null) }
    var fontImportMessage by remember { mutableStateOf<String?>(null) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val permissionStatusRevision = permissionRevision
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
    fun requestReminderNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun refresh() { tasks = TaskStore.tasks(context); events = TaskStore.history(context) }
    fun refreshSettings() {
        settings = WidgetSettings.load(context)
        appFontSettings = AppTypographySettings.load(context)
        calendarSettings = CalendarWidgetSettings.load(context)
        importedWidgetFonts = LocalWidgetFonts.list(context)
    }
    val fontImportScope = rememberCoroutineScope()
    val fontFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = fontImportTarget
        fontImportTarget = null
        if (uri != null && target != null) fontImportScope.launch {
            val readPermission = Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, readPermission) }
            val result = withContext(Dispatchers.IO) { runCatching { LocalWidgetFonts.import(context, uri) } }
            runCatching { context.contentResolver.releasePersistableUriPermission(uri, readPermission) }
            result.fold(
                onSuccess = { font ->
                    when (target) {
                        "app" -> AppTypographySettings.saveImportedFont(context, font.id)
                        "calendar" -> CalendarWidgetSettings.saveImportedFont(context, font.id)
                        "todo" -> WidgetSettings.saveImportedFont(context, font.id)
                    }
                    refreshSettings()
                    if (target == "todo") TodoWidget.updateAll(context)
                    if (target == "calendar") com.example.darktodo.widget.CalendarWidget.updateAll(context)
                    fontImportMessage = "${font.name} is ready for ${when (target) { "app" -> "the app"; "calendar" -> "the Calendar widget"; else -> "the To-Do widget" }}."
                },
                onFailure = { error -> fontImportMessage = error.message ?: "Could not import that font file." }
            )
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val restoreScope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            timeTick = System.currentTimeMillis()
            permissionRevision++
            refresh()
            // restore() is synchronized and re-registers every alarm; refresh again once it finishes.
            restoreScope.launch {
                withContext(Dispatchers.Default) {
                    runCatching { com.example.darktodo.reminders.ReminderScheduler.restore(context) }
                        .onFailure { android.util.Log.e("MainActivity", "Reminder recovery failed", it) }
                }
                refresh()
            }
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
    // A notification task ID is a one-shot navigation request. Do not key this on task data:
    // doing so re-opened the same edit dialog after any save changed the task list.
    LaunchedEffect(openTaskId) { if (openTaskId != null) { page = "To-Do"; todoPage = "Tasks"; editing = tasks.firstOrNull { it.id == openTaskId } } }
    LaunchedEffect(startCreateTask) { if (startCreateTask) { page = "To-Do"; todoPage = "Tasks"; editing = null; title = ""; addReminder = true } }
    LaunchedEffect(openTaskDate) { if (openTaskDate != null) { dateFilter = runCatching { LocalDate.parse(openTaskDate).toString() }.getOrNull(); page = "To-Do"; todoPage = "Tasks" } }

    LaunchedEffect(settings) { TodoWidget.updateAll(context) }
    val appUiFontFamily = remember(appFontSettings) { composeFontFamily(context, appFontSettings.family, appFontSettings.fontFileId) }
    val iconColor = colorFromHex(settings.incomplete)
    val caretColor = if (iconColor.isBlackOrGray()) Color.White else iconColor
    ProvideTextStyle(TextStyle(fontFamily = appUiFontFamily)) {
        Column(Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(UiTokens.PageMargin)) {
            Text("Clarity", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(bottom = 12.dp))
            val currentModule = ModuleNavigation.moduleFor(page, todoPage)
            fun openModule(id: String) {
                val target = ModuleNavigation.routeFor(id)
                page = target.page; todoPage = target.todoPage; route = emptyList()
                if (target.page == "To-Do") refresh()
                if (target.page == "Settings") refreshSettings()
            }
            LaunchedEffect(currentModule) { ModulePrefs.save(context, currentModule) }
            var exitArmed by remember { mutableStateOf(false) }
            LaunchedEffect(exitArmed) { if (exitArmed) { delay(2_000L); exitArmed = false } }
            val backStep = ModuleNavigation.backStep(page, todoPage, route.size)
            BackHandler(enabled = !(backStep is BackStep.AtHome && exitArmed)) {
                when (backStep) {
                    is BackStep.PopRoute -> route = route.dropLast(1)
                    is BackStep.GoTo -> { page = backStep.route.page; todoPage = backStep.route.todoPage; route = emptyList(); if (page == "To-Do") refresh() }
                    is BackStep.AtHome -> { exitArmed = true; android.widget.Toast.makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show() }
                }
            }
            ModuleTabBar(ModuleNavigation.modules, currentModule, ::openModule, Modifier.padding(bottom = 16.dp))
            val swipeThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.toPx() }
            Column(Modifier.weight(1f).fillMaxWidth().pointerInput(currentModule) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (kotlin.math.abs(drag) > swipeThresholdPx) ModuleNavigation.adjacent(currentModule, forward = drag < 0f)?.let(::openModule)
                    },
                    onDragCancel = { drag = 0f },
                    onHorizontalDrag = { _, amount -> drag += amount }
                )
            }) {
            when {
                page == "To-Do" -> {
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NavigationItem("Tasks", todoPage == "Tasks") { todoPage = "Tasks"; route = emptyList(); refresh() }
                        NavigationItem("History", todoPage == "History") { todoPage = "History"; route = emptyList(); refresh() }
                        Spacer(Modifier.weight(1f))
                        com.example.darktodo.ui.SettingsGearButton("Tasks settings") { page = "TodoSettings"; refreshSettings() }
                    }
                    if (todoPage == "History") HistoryScreen(events, route, onRoute = { route = it }, onDelete = { TaskStore.deleteEvent(context, it); refresh() }, onClear = { scope -> TaskStore.deleteEvents(context, scope); refresh() }, onClearAll = { confirmClear = true })
                    else Column(Modifier.weight(1f).fillMaxWidth()) {
                    if (dateFilter != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Tasks for $dateFilter", color = Accent, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { dateFilter = null }) { Text("Show all", color = Ink) }
                    } else Text("${tasks.count { !it.completed }} incomplete", color = Accent, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(UiTokens.CardRadius)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(value = title, onValueChange = { title = it }, modifier = Modifier.weight(1f), singleLine = true, cursorBrush = SolidColor(caretColor), textStyle = TextStyle(color = Ink, fontSize = 16.sp, fontFamily = appUiFontFamily, fontStyle = FontStyle.Normal, fontWeight = FontWeight.Normal), decorationBox = { inner -> if (title.isEmpty()) Text("Add a task", color = Color.Gray); inner() })
                        TextButton(onClick = { if (title.isNotBlank()) addReminder = true }) { Text("Add", color = Accent) }
                    }
                    Spacer(Modifier.height(12.dp))
                    val groupPositions = remember(tasks) {
                        val counts = mutableMapOf(true to 0, false to 0)
                        val positions = HashMap<String, Int>()
                        tasks.forEach { positions[it.id] = counts.getValue(it.completed); counts[it.completed] = counts.getValue(it.completed) + 1 }
                        positions to counts.toMap()
                    }
                    val visibleTasks = tasks.filter { dateFilter == null || it.taskDate == dateFilter }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(visibleTasks, key = { it.id }) { task ->
                            val taskColor = colorFromHex(settings.taskTitle)
                            val checkColor = colorFromHex(if (task.completed) settings.complete else settings.incomplete)
                            val groupPosition = groupPositions.first[task.id] ?: -1
                            val groupSize = groupPositions.second.getValue(task.completed)
                            Row(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { actionTask = task }).graphicsLayer(alpha = if (task.completed) 1f - settings.fadePercent / 100f else 1f).background(Card, RoundedCornerShape(14.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (task.completed) "\u25cf" else "\u25cb", modifier = Modifier.clickable { TaskStore.toggle(context, task.id); refresh() }, color = checkColor, fontSize = 22.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f).clickable { editing = task }) {
                                    Text(task.title, color = taskColor, textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None, fontSize = 16.sp, fontFamily = appUiFontFamily, fontStyle = FontStyle.Normal, fontWeight = FontWeight.Normal)
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        if (dateFilter == null) TextButton(onClick = { TaskStore.clearCompleted(context); refresh() }) { Text("Clear completed", color = Accent) }
                        else Spacer(Modifier.width(1.dp))
                    }
                    }
                }
                page == "Calendar" -> {
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Your month at a glance", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp, modifier = Modifier.weight(1f))
                        com.example.darktodo.ui.SettingsGearButton("Calendar settings") { page = "CalendarSettings"; refreshSettings() }
                    }
                    CalendarHomeScreen(tasks, calendarSettings, onDate = { selectedDate -> dateFilter = selectedDate.toString(); todoPage = "Tasks"; page = "To-Do" })
                }
                page == "Vault" -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        com.example.darktodo.ui.SettingsGearButton("Vault widget appearance") { page = "VaultAppearance" }
                    }
                    VaultScreen()
                }
                page == "Info" -> com.example.darktodo.ui.InfoScreen {
                    com.example.darktodo.widget.SystemMonitorWidget.renderAll(context)
                }
                page == "VaultAppearance" -> com.example.darktodo.ui.VaultAppearanceScreen { page = "Vault" }
                page == "Settings" -> GlobalSettingsScreen(
                    onOpenPermissions = { page = "Permissions" },
                    appFontSettings = appFontSettings,
                    importedFonts = importedWidgetFonts,
                    onAppFontFamily = { AppTypographySettings.saveFamily(context, it); refreshSettings() },
                    onAppImportedFont = { font -> AppTypographySettings.saveImportedFont(context, font.id); refreshSettings() },
                    onImportAppFont = { fontImportTarget = "app"; fontFilePicker.launch(arrayOf("*/*")) },
                    remindersEnabled = tasks.any { !it.completed && it.reminderType != "none" },
                    notificationGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                    notificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled(),
                    channelImportance = if (Build.VERSION.SDK_INT >= 26) context.getSystemService(android.app.NotificationManager::class.java).getNotificationChannel(com.example.darktodo.reminders.ReminderScheduler.CHANNEL)?.importance else null,
                    onOpenVaultSecurity = { page = "VaultSecurity" },
                    onOpenSupport = { page = "SupportClarity" },
                    onOpenAbout = { page = "About" }
                )
                page == "TodoWallpaper" || page == "CalendarWallpaper" -> WidgetBackgroundEditor(wallpaperTarget) { page = if (page == "TodoWallpaper") "TodoSettings" else "CalendarSettings" }
                page == "VaultSecurity" -> VaultSecurityScreen { page = "Settings" }
                page == "Permissions" -> PermissionsScreen(
                    remindersEnabled = tasks.any { !it.completed && it.reminderType != "none" },
                    notificationGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                    notificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled(),
                    channelImportance = if (Build.VERSION.SDK_INT >= 26) context.getSystemService(android.app.NotificationManager::class.java).getNotificationChannel(com.example.darktodo.reminders.ReminderScheduler.CHANNEL)?.importance else null,
                    exactAlarmsEnabled = com.example.darktodo.reminders.ReminderScheduler.canScheduleExactly(context),
                    onBack = { page = "Settings" },
                    onRequestNotifications = { if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else permissionRevision++ },
                    onNotificationSettings = { context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)) },
                    onExactAlarmSettings = { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))) } }
                )
                page == "TodoSettings" || page == "CalendarSettings" -> SettingsScreen(
                    screen = page,
                    onBack = { page = if (page == "TodoSettings") "To-Do" else "Calendar" },
                    onOpenWallpaper = { wallpaperTarget = it; page = if (it == com.example.darktodo.data.WidgetBackgrounds.TODO) "TodoWallpaper" else "CalendarWallpaper" },
                    onTodoLayout = { pad, gap -> WidgetSettings.saveLayout(context, pad, gap); refreshSettings(); TodoWidget.updateAll(context) },
                    onSaveDefault = { calendar -> if (calendar) CalendarWidgetSettings.saveAsDefault(context) else WidgetSettings.saveAsDefault(context) },
                    onRestoreDefault = { calendar ->
                        val ok = if (calendar) CalendarWidgetSettings.restoreDefault(context) else WidgetSettings.restoreDefault(context)
                        refreshSettings()
                        if (calendar) com.example.darktodo.widget.CalendarWidget.updateAll(context) else TodoWidget.updateAll(context)
                        ok
                    },
                    settings = settings,
                    calendarSettings = calendarSettings,
                    importedFonts = importedWidgetFonts,
                    fontImportMessage = fontImportMessage,
                    onDismissFontImportMessage = { fontImportMessage = null },
                    onImportFont = { targetCalendar ->
                        fontImportTarget = if (targetCalendar) "calendar" else "todo"
                        fontFilePicker.launch(arrayOf("*/*"))
                    },
                    onColor = { key, value -> WidgetSettings.saveColor(context, key, value); refreshSettings(); TodoWidget.updateAll(context) },
                    onFade = { WidgetSettings.saveFade(context, it); refreshSettings(); TodoWidget.updateAll(context) },
                    onTaskCardTransparency = { WidgetSettings.saveTaskCardTransparency(context, it); refreshSettings(); TodoWidget.updateAll(context) },
                    onFontFamily = { WidgetSettings.saveFontFamily(context, it); refreshSettings(); TodoWidget.updateAll(context) },
                    onTodoImportedFont = { font -> WidgetSettings.saveImportedFont(context, font.id); refreshSettings(); TodoWidget.updateAll(context) },
                    onCalendarFontFamily = { CalendarWidgetSettings.saveFontFamily(context, it); refreshSettings(); com.example.darktodo.widget.CalendarWidget.updateAll(context) },
                    onCalendarImportedFont = { font -> CalendarWidgetSettings.saveImportedFont(context, font.id); refreshSettings(); com.example.darktodo.widget.CalendarWidget.updateAll(context) },
                    onTaskTitleSize = { WidgetSettings.saveTaskTitleSize(context, it); refreshSettings(); TodoWidget.updateAll(context) },
                    onReset = {
                        WidgetSettings.reset(context)
                        refreshSettings()
                        TodoWidget.updateAll(context)
                    },
                    onCalendarColor = { key, value -> CalendarWidgetSettings.saveColor(context, key, value); refreshSettings(); com.example.darktodo.widget.CalendarWidget.updateAll(context) },
                    onCalendarInt = { key, value -> CalendarWidgetSettings.saveInt(context, key, value); refreshSettings(); com.example.darktodo.widget.CalendarWidget.updateAll(context) },
                    onCalendarDots = { CalendarWidgetSettings.saveDots(context, it); refreshSettings(); com.example.darktodo.widget.CalendarWidget.updateAll(context) },
                    onCalendarReset = { CalendarWidgetSettings.reset(context); refreshSettings(); com.example.darktodo.widget.CalendarWidget.updateAll(context) }
                )
                page == "Quotes" -> com.example.darktodo.ui.ComingSoonScreen(com.example.darktodo.ui.ClarityIcons.Quotes, "Quotes", "Browse and save motivational quotes here in a future update.")
                page == "Wallpapers" -> com.example.darktodo.ui.ComingSoonScreen(com.example.darktodo.ui.ClarityIcons.Wallpapers, "Wallpapers", "A wallpaper library is planned. Widget backgrounds can be set today in Settings, under Personalization.")
                page == "Stickers" -> com.example.darktodo.ui.NotesScreen(initialEditId = pendingNoteId, onInitialEditHandled = { pendingNoteId = null }, onOpenNoteSettings = { id -> noteSettingsId = id; page = "StickerSettings" })
                page == "StickerSettings" -> com.example.darktodo.ui.NoteSettingsScreen(noteSettingsId.orEmpty(), onBack = { page = "Stickers" })
                page == "SupportClarity" -> com.example.darktodo.ui.SupportClarityScreen { page = "Settings" }
                page == "About" -> com.example.darktodo.ui.AboutScreen({ page = "Settings" }, runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "")
            }
            }
        }
    }
    LaunchedEffect(tasks) { TodoWidget.updateAll(context) }
    ProvideTextStyle(TextStyle(fontFamily = appUiFontFamily)) {
    editing?.let { task ->
        var value by remember(task.id) { mutableStateOf(task.title) }
        var kind by remember(task.id) { mutableStateOf(if (task.completed) "none" else task.reminderType) }
        var reminderEdited by remember(task.id) { mutableStateOf(false) }
        val localPickerInstant = remember(task.id) { task.reminderAt.takeIf { task.reminderType == "time" && it > 0L } }
        var deadline by remember(task.id) { mutableLongStateOf((if (task.reminderType == "time") localPickerInstant ?: task.reminderAt else task.deadlineAt).takeIf { it > System.currentTimeMillis() } ?: System.currentTimeMillis() + 3_600_000L) }
        var timeZone by remember(task.id) { mutableStateOf(if (task.followsDeviceTimeZone) "" else task.timeZoneId) }
        var taskDate by remember(task.id) { mutableStateOf(task.taskDate) }
        val remainMinutes = CountdownEditPolicy.displayedMinutes(TaskTimeFormatter.remainingMillis(context, task))
        var hours by remember(task.id) { mutableStateOf((remainMinutes / 60L).toString()) }; var minutes by remember(task.id) { mutableStateOf((remainMinutes % 60L).toString()) }; var error by remember(task.id) { mutableStateOf("") }
        ReminderDialog(value, { value = it }, kind, { kind = it; reminderEdited = true }, deadline, { deadline = it; reminderEdited = true }, hours, { hours = it; reminderEdited = true }, minutes, { minutes = it; reminderEdited = true }, timeZone, { timeZone = it; reminderEdited = true }, taskDate, { taskDate = it }, error, { error = it }, { editing = null }, {
            val at = when (kind) { "time" -> deadline; "duration" -> System.currentTimeMillis() + ((hours.toLongOrNull() ?: 0L) * 60 + (minutes.toLongOrNull() ?: 0L)) * 60_000L; else -> 0L }
            val badZone = kind == "time" && timeZone.isNotBlank() && runCatching { ZoneId.of(timeZone.trim()) }.isFailure
            val renewExpiredCountdown = task.reminderType == "duration" && TaskTimeFormatter.remainingMillis(context, task) <= 0L
            if (value.isBlank()) error = "Enter a task name" else if (reminderEdited && badZone) error = "Enter a valid IANA time zone, such as Europe/London" else if (reminderEdited && kind == "time" && at <= System.currentTimeMillis()) error = "Choose a future time" else if ((reminderEdited || renewExpiredCountdown) && kind == "duration" && at <= System.currentTimeMillis()) error = "Enter a duration greater than zero" else {
                // Saving an expired countdown renews it from the duration shown in the dialog,
                // even when the user leaves the hours/minutes fields untouched.
                if (reminderEdited && kind != "none") requestReminderNotificationPermissionIfNeeded()
                if (CountdownEditPolicy.shouldConfigure(reminderEdited, renewExpiredCountdown)) TaskStore.configure(context, task.id, value, kind, at, timeZone.trim(), taskDate)
                else TaskStore.updateTitleAndDate(context, task.id, value, taskDate)
                editing = null; refresh()
            }
        }, task.completed)
    }
    if (addReminder) {
        var kind by remember { mutableStateOf("none") }; var deadline by remember { mutableLongStateOf(System.currentTimeMillis() + 3_600_000L) }; var hours by remember { mutableStateOf("1") }; var minutes by remember { mutableStateOf("0") }; var timeZone by remember { mutableStateOf("") }; var taskDate by remember { mutableStateOf(LocalDate.now().toString()) }; var error by remember { mutableStateOf("") }
        ReminderDialog(title, { title = it }, kind, { kind = it }, deadline, { deadline = it }, hours, { hours = it }, minutes, { minutes = it }, timeZone, { timeZone = it }, taskDate, { taskDate = it }, error, { error = it }, { addReminder = false }, {
            val at = when (kind) { "time" -> deadline; "duration" -> System.currentTimeMillis() + ((hours.toLongOrNull() ?: 0L) * 60 + (minutes.toLongOrNull() ?: 0L)) * 60_000L; else -> 0L }
            val badZone = kind == "time" && timeZone.isNotBlank() && runCatching { ZoneId.of(timeZone.trim()) }.isFailure
            if (title.isBlank()) error = "Enter a task name" else if (badZone) error = "Enter a valid IANA time zone, such as Europe/London" else if (kind == "time" && at <= System.currentTimeMillis()) error = "Choose a future time" else if (kind == "duration" && at <= System.currentTimeMillis()) error = "Enter a duration greater than zero" else { if (kind != "none") requestReminderNotificationPermissionIfNeeded(); TaskStore.addConfigured(context, title, kind, at, timeZone.trim(), taskDate); title = ""; addReminder = false; refresh() }
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
}

private fun widgetTypeface(context: Context, family: WidgetFontFamily, fontFileId: String?): AndroidTypeface =
    LocalWidgetFonts.typeface(context, fontFileId) ?: AndroidTypeface.create(family.key, AndroidTypeface.NORMAL)

private fun composeFontFamily(context: Context, family: WidgetFontFamily, fontFileId: String?): FontFamily =
    FontFamily(widgetTypeface(context, family, fontFileId))

private sealed class VaultSensitiveAction(open val entryId: String) {
    data class Show(override val entryId: String) : VaultSensitiveAction(entryId)
    data class Edit(override val entryId: String) : VaultSensitiveAction(entryId)
    data class Delete(override val entryId: String) : VaultSensitiveAction(entryId)
}

@Composable private fun VaultScreen() {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var hasVault by remember { mutableStateOf(LocalVaultStore.exists(context)) }
    var entries by remember { mutableStateOf<List<VaultEntry>?>(null) }
    var masterPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var revealedId by remember { mutableStateOf<String?>(null) }
    var authMode by remember { mutableStateOf(LocalVaultStore.primaryAuth(context)) }
    val biometricStatus = remember(authMode) { if (authMode == LocalVaultStore.AUTH_BIOMETRIC) LocalVaultStore.canAuthenticateBiometric(context) else BiometricManager.BIOMETRIC_SUCCESS }
    var message by remember { mutableStateOf(if (authMode == LocalVaultStore.AUTH_BIOMETRIC && biometricStatus != BiometricManager.BIOMETRIC_SUCCESS) biometricUnavailableMessage(biometricStatus) else "") }
    var authMenu by remember { mutableStateOf(false) }
    var passwordFallback by remember { mutableStateOf(authMode != LocalVaultStore.AUTH_BIOMETRIC || biometricStatus != BiometricManager.BIOMETRIC_SUCCESS) }
    var biometricPromptActive by remember { mutableStateOf(false) }
    var pendingSensitiveAction by remember { mutableStateOf<VaultSensitiveAction?>(null) }
    var showSensitiveAuthChoice by remember { mutableStateOf(false) }
    var showSensitivePasswordPrompt by remember { mutableStateOf(false) }
    var sensitivePassword by remember { mutableStateOf("") }
    var activeSensitivePrompt by remember { mutableStateOf<BiometricPrompt?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    var addExpanded by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    DisposableEffect(activity) {
        activity?.window?.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val lastActivity = remember { longArrayOf(System.currentTimeMillis()) }
    val autoLockMillis = remember(entries != null) { LocalVaultStore.autoLockSeconds(context) * 1000L }
    LaunchedEffect(entries != null, autoLockMillis) {
        if (entries == null || autoLockMillis <= 0L) return@LaunchedEffect
        lastActivity[0] = System.currentTimeMillis()
        while (true) {
            delay(1000)
            if (pendingSensitiveAction == null && !biometricPromptActive && System.currentTimeMillis() - lastActivity[0] >= autoLockMillis) {
                entries = null; masterPassword = ""; secret = ""; title = ""; username = ""; notes = ""; editingId = null; revealedId = null
                passwordFallback = authMode != LocalVaultStore.AUTH_BIOMETRIC
                message = "Vault locked after inactivity."
                break
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { activeSensitivePrompt?.cancelAuthentication(); activeSensitivePrompt = null; biometricPromptActive = false; entries = null; masterPassword = ""; confirmation = ""; secret = ""; title = ""; username = ""; notes = ""; editingId = null; revealedId = null; pendingSensitiveAction = null; showSensitiveAuthChoice = false; showSensitivePasswordPrompt = false; sensitivePassword = ""; authMode = LocalVaultStore.primaryAuth(context); val status = LocalVaultStore.canAuthenticateBiometric(context); passwordFallback = authMode != LocalVaultStore.AUTH_BIOMETRIC || status != BiometricManager.BIOMETRIC_SUCCESS; message = if (authMode == LocalVaultStore.AUTH_BIOMETRIC && status != BiometricManager.BIOMETRIC_SUCCESS) biometricUnavailableMessage(status) else "" } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun saveEntries(updated: List<VaultEntry>) {
        val passwordChars = masterPassword.toCharArray()
        try {
            LocalVaultStore.save(context, passwordChars, updated)
            entries = updated
            message = ""
        } catch (error: Exception) { message = error.message ?: "Could not save the vault." }
        finally { passwordChars.fill('\u0000') }
    }
    // Reordering happens in memory; the encrypted vault is written once per drop because each save is slow (PBKDF2).
    fun persistOrder() {
        val list = entries ?: return
        val password = masterPassword
        scope.launch {
            val chars = password.toCharArray()
            try {
                withContext(Dispatchers.Default) { LocalVaultStore.save(context, chars, list) }
            } catch (error: Exception) {
                message = error.message ?: "Could not save the vault."
            } finally { chars.fill('\u0000') }
        }
    }
    fun moveEntry(id: String, delta: Int) {
        val list = entries?.toMutableList() ?: return
        val from = list.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        entries = list
        persistOrder()
    }
    fun dragMoved(id: String) {
        val list = entries ?: return
        val ids = list.map { it.id }.toSet()
        val info = listState.layoutInfo.visibleItemsInfo
        val current = info.firstOrNull { it.key == id } ?: return
        val center = current.offset + current.size / 2f + dragOffset
        val target = info.firstOrNull { it.key != id && it.key is String && ids.contains(it.key as String) && center > it.offset && center < it.offset + it.size } ?: return
        val from = list.indexOfFirst { it.id == id }
        val to = list.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0) return
        val newOffset = if (target.offset > current.offset) target.offset + target.size - current.size else target.offset
        dragOffset += current.offset - newOffset
        entries = list.toMutableList().apply { add(to, removeAt(from)) }
    }
    fun failSensitiveAction(expectedAction: VaultSensitiveAction? = null) {
        if (expectedAction != null && pendingSensitiveAction !== expectedAction) return
        entries = null
        masterPassword = ""
        confirmation = ""
        title = ""
        username = ""
        secret = ""
        notes = ""
        editingId = null
        revealedId = null
        pendingSensitiveAction = null
        showSensitiveAuthChoice = false
        showSensitivePasswordPrompt = false
        sensitivePassword = ""
        message = "Authentication required to access vault data."
    }
    fun executeSensitiveAction(action: VaultSensitiveAction, unlocked: List<VaultEntry>, password: String) {
        if (pendingSensitiveAction !== action) return
        val entry = unlocked.firstOrNull { it.id == action.entryId }
        if (entry == null) {
            failSensitiveAction(action)
            return
        }
        entries = unlocked
        masterPassword = password
        pendingSensitiveAction = null
        showSensitiveAuthChoice = false
        showSensitivePasswordPrompt = false
        sensitivePassword = ""
        when (action) {
            is VaultSensitiveAction.Show -> {
                revealedId = entry.id
                message = ""
            }
            is VaultSensitiveAction.Edit -> {
                editingId = entry.id
                title = entry.title
                username = entry.username
                secret = entry.password
                notes = entry.notes
                addExpanded = true
                message = ""
                scope.launch { listState.animateScrollToItem(0) }
            }
            is VaultSensitiveAction.Delete -> saveEntries(unlocked.filterNot { it.id == entry.id })
        }
    }
    fun authenticateSensitiveAction(password: String) {
        val action = pendingSensitiveAction ?: return
        val passwordChars = password.toCharArray()
        val unlocked = try {
            LocalVaultStore.unlock(context, passwordChars)
        } catch (error: Exception) {
            failSensitiveAction(action)
            return
        } finally {
            passwordChars.fill('\u0000')
        }
        if (unlocked == null) {
            failSensitiveAction()
            return
        }
        executeSensitiveAction(action, unlocked, password)
    }
    fun beginSensitiveAction(action: VaultSensitiveAction) {
        if (biometricPromptActive) return
        entries = null
        masterPassword = ""
        confirmation = ""
        title = ""
        username = ""
        secret = ""
        notes = ""
        editingId = null
        revealedId = null
        pendingSensitiveAction = action
        authMode = LocalVaultStore.primaryAuth(context)
        val status = LocalVaultStore.canAuthenticateBiometric(context)
        if (authMode == LocalVaultStore.AUTH_BIOMETRIC && status == BiometricManager.BIOMETRIC_SUCCESS) {
            showSensitiveAuthChoice = true
            showSensitivePasswordPrompt = false
            message = ""
        } else {
            showSensitiveAuthChoice = false
            showSensitivePasswordPrompt = true
            message = if (authMode == LocalVaultStore.AUTH_BIOMETRIC) biometricUnavailableMessage(status) else ""
        }
    }
    LaunchedEffect(pendingSensitiveAction) {
        val action = pendingSensitiveAction
        if (action != null) {
            delay(60_000)
            if (pendingSensitiveAction === action) {
                activeSensitivePrompt?.cancelAuthentication()
                activeSensitivePrompt = null
                biometricPromptActive = false
                failSensitiveAction(action)
            }
        }
    }
    fun showBiometricPrompt(
        cipher: javax.crypto.Cipher,
        encryptedPassword: ByteArray? = null,
        enrollingPassword: String? = null,
        sensitiveAction: VaultSensitiveAction? = null
    ) {
        val host = activity ?: run {
            if (sensitiveAction != null) failSensitiveAction(sensitiveAction)
            else message = "Biometric unlock is unavailable here."
            return
        }
        if (biometricPromptActive) return
        biometricPromptActive = true
        lateinit var prompt: BiometricPrompt
        prompt = BiometricPrompt(host, ContextCompat.getMainExecutor(host), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                biometricPromptActive = false
                if (sensitiveAction != null) activeSensitivePrompt = null
                val authenticatedCipher = result.cryptoObject?.cipher
                if (authenticatedCipher == null) {
                    if (sensitiveAction != null) failSensitiveAction(sensitiveAction)
                    else message = "Biometric authentication did not unlock the vault."
                    return
                }
                runCatching {
                    if (enrollingPassword != null) {
                        LocalVaultStore.saveBiometricWrapper(context, authenticatedCipher, enrollingPassword)
                        authMode = LocalVaultStore.AUTH_BIOMETRIC
                        passwordFallback = false
                        message = "Fingerprint unlock is now your primary option."
                    } else {
                        val clear = authenticatedCipher.doFinal(encryptedPassword ?: error("Fingerprint key is unavailable."))
                        val password = String(clear, Charsets.UTF_8)
                        clear.fill(0)
                        val chars = password.toCharArray()
                        val unlocked = try { LocalVaultStore.unlock(context, chars) } finally { chars.fill('\u0000') }
                        if (unlocked == null) error("The fingerprint key could not unlock this vault. Use the master password.")
                        if (sensitiveAction != null) executeSensitiveAction(sensitiveAction, unlocked, password)
                        else {
                            masterPassword = password
                            entries = unlocked
                            message = ""
                        }
                    }
                }.onFailure {
                    if (sensitiveAction != null) failSensitiveAction(sensitiveAction)
                    else message = it.message ?: "Biometric unlock failed. Use the master password."
                }
            }
            override fun onAuthenticationFailed() {
                if (sensitiveAction != null) {
                    activeSensitivePrompt = null
                    prompt.cancelAuthentication()
                    biometricPromptActive = false
                    failSensitiveAction(sensitiveAction)
                } else {
                    message = "Fingerprint not recognized. Try again or use your master password."
                }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                biometricPromptActive = false
                if (sensitiveAction != null) activeSensitivePrompt = null
                if (sensitiveAction != null) failSensitiveAction(sensitiveAction)
                else message = if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) "Fingerprint unlock cancelled. The vault remains locked." else "$errString Use your master password if needed."
            }
        })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (enrollingPassword != null) "Enable fingerprint unlock" else if (sensitiveAction != null) "Verify vault access" else "Unlock Clarity Vault")
            .setSubtitle(if (enrollingPassword != null) "Confirm to protect vault access" else if (sensitiveAction != null) "Authenticate to access this password" else "Authenticate to open your vault")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")
            .build()
        if (sensitiveAction != null) activeSensitivePrompt = prompt
        runCatching { prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher)) }
            .onFailure {
                biometricPromptActive = false
                if (sensitiveAction != null) activeSensitivePrompt = null
                if (sensitiveAction != null) failSensitiveAction(sensitiveAction)
                else message = "Could not start fingerprint authentication. Use your master password."
            }
    }
    fun beginBiometricEnrollment() {
        val status = LocalVaultStore.canAuthenticateBiometric(context)
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            message = biometricUnavailableMessage(status)
            return
        }
        runCatching { LocalVaultStore.prepareBiometricEnrollment() }
            .onSuccess { showBiometricPrompt(it, enrollingPassword = masterPassword) }
            .onFailure { message = "Could not prepare fingerprint unlock. Keep using your master password." }
    }
    fun beginBiometricUnlock(sensitiveAction: VaultSensitiveAction? = null) {
        val status = LocalVaultStore.canAuthenticateBiometric(context)
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            if (sensitiveAction != null) {
                showSensitiveAuthChoice = false
                showSensitivePasswordPrompt = true
                message = biometricUnavailableMessage(status)
            } else {
                passwordFallback = true
                message = biometricUnavailableMessage(status)
            }
            return
        }
        runCatching { LocalVaultStore.prepareBiometricUnlock(context) }
            .onSuccess { (cipher, encrypted) -> showBiometricPrompt(cipher, encryptedPassword = encrypted, sensitiveAction = sensitiveAction) }
            .onFailure {
                if (sensitiveAction != null) {
                    showSensitiveAuthChoice = false
                    showSensitivePasswordPrompt = true
                    message = "Fingerprint unlock is unavailable or needs setup. Use your master password."
                } else {
                    passwordFallback = true
                    message = "Fingerprint unlock is unavailable or needs setup. Unlock with your master password."
                }
            }
    }

    Column(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitPointerEventScope { while (true) { awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial); lastActivity[0] = System.currentTimeMillis() } }
    }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Long privacy copy scrolls inside a short box so it never permanently takes half the screen.
        Text("Your passwords stay encrypted with your master password. If Backup & Restore is on, only the encrypted vault file is uploaded to your private Google Drive app folder; the password and biometric keys never leave this device.", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp, modifier = Modifier.fillMaxWidth().heightIn(max = 56.dp).verticalScroll(rememberScrollState()))
        val opened = entries
        if (opened == null) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SettingsPanel {
                Text(if (!hasVault) "Create your vault" else if (authMode == LocalVaultStore.AUTH_BIOMETRIC && !passwordFallback) "Unlock with fingerprint" else "Unlock your vault", color = Ink, fontSize = UiTokens.SectionTitleSp.sp, fontWeight = FontWeight.SemiBold)
                if (hasVault && authMode == LocalVaultStore.AUTH_BIOMETRIC && !passwordFallback) {
                    Text("Your selected primary unlock is fingerprint.", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
                    TextButton(onClick = { beginBiometricUnlock() }, enabled = !biometricPromptActive) { Text(if (biometricPromptActive) "Waiting for fingerprint…" else "Unlock with fingerprint", color = Accent) }
                    TextButton(onClick = { passwordFallback = true; message = "Enter your master password to unlock." }) { Text("Use password", color = UiTokens.Muted) }
                } else {
                    VaultInput("Password", masterPassword, { masterPassword = it }, secret = true)
                    if (!hasVault) VaultInput("Confirm password", confirmation, { confirmation = it }, secret = true)
                    TextButton(onClick = {
                        val passwordChars = masterPassword.toCharArray()
                        try {
                            if (hasVault) {
                                val result = LocalVaultStore.unlock(context, passwordChars)
                                if (result == null) message = "That password could not unlock the vault."
                                else { entries = result; message = "" }
                            } else if (masterPassword != confirmation) message = "Passwords do not match."
                            else {
                                LocalVaultStore.create(context, passwordChars)
                                hasVault = true
                                entries = emptyList()
                                authMode = LocalVaultStore.AUTH_PASSWORD
                                message = ""
                            }
                        } catch (error: Exception) { message = error.message ?: "Could not open the vault." }
                        finally { passwordChars.fill('\u0000') }
                    }, enabled = !biometricPromptActive) { Text(if (hasVault) "Unlock" else "Create encrypted vault", color = Accent) }
                }
                if (message.isNotEmpty()) Text(message, color = Accent, fontSize = UiTokens.DetailSp.sp)
            }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { authMenu = true }) { Text("Primary unlock: ${if (authMode == LocalVaultStore.AUTH_BIOMETRIC) "Fingerprint" else "Password"}  ▾", color = Accent) }
                    DropdownMenu(expanded = authMenu, onDismissRequest = { authMenu = false }) {
                        DropdownMenuItem(text = { Text("Password") }, onClick = { LocalVaultStore.usePasswordAuthentication(context); authMode = LocalVaultStore.AUTH_PASSWORD; passwordFallback = true; authMenu = false; message = "Password is the primary unlock." })
                        DropdownMenuItem(text = { Text("Fingerprint") }, onClick = { authMenu = false; beginBiometricEnrollment() })
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("${opened.size} saved", color = Ink)
                TextButton(onClick = { entries = null; masterPassword = ""; secret = ""; title = ""; username = ""; notes = ""; editingId = null; revealedId = null; passwordFallback = authMode != LocalVaultStore.AUTH_BIOMETRIC }) { Text("Lock", color = Accent) }
            }
            if (message.isNotEmpty()) Text(message, color = Accent, fontSize = UiTokens.DetailSp.sp)
            Surface(onClick = { addExpanded = !addExpanded }, color = UiTokens.SurfaceRaised, shape = RoundedCornerShape(UiTokens.RowRadius), modifier = Modifier.fillMaxWidth().semantics { contentDescription = if (addExpanded) "Collapse add a password form" else "Expand add a password form" }) {
                Row(Modifier.heightIn(min = UiTokens.ControlHeight).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (editingId == null) "Add a password" else "Edit password", color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(if (addExpanded) "⌃" else "⌄", color = Accent, fontSize = 20.sp)
                }
            }
            val visibleEntries = remember(opened, searchQuery) {
                opened.filter { entry ->
                    searchQuery.isBlank() || listOf(entry.title, entry.username, entry.notes).any { it.contains(searchQuery, ignoreCase = true) }
                }
            }
            LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "password_form") {
                    AnimatedVisibility(visible = addExpanded, enter = expandVertically(animationSpec = tween(180)) + fadeIn(animationSpec = tween(140)), exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(120))) {
                        SettingsPanel {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                VaultInput("Title", title, { title = it })
                                VaultInput("Username or email", username, { username = it })
                                VaultInput("Password", secret, { secret = it }, secret = true)
                                VaultInput("Notes", notes, { notes = it })
                                TextButton(onClick = {
                                    if (title.isBlank() || secret.isEmpty()) message = "Enter a title and password."
                                    else {
                                        val replacement = VaultEntry(editingId ?: java.util.UUID.randomUUID().toString(), title.trim(), username.trim(), secret, notes.trim())
                                        val updated = if (editingId == null) opened + replacement else opened.map { if (it.id == editingId) replacement else it }
                                        saveEntries(updated)
                                        if (entries == updated) { title = ""; username = ""; secret = ""; notes = ""; editingId = null; addExpanded = false }
                                    }
                                }) { Text(if (editingId == null) "Save securely" else "Save changes", color = Accent) }
                                if (editingId != null) TextButton(onClick = { editingId = null; title = ""; username = ""; secret = ""; notes = "" }) { Text("Cancel edit", color = UiTokens.Muted) }
                            }
                        }
                    }
                }
                item(key = "vault_search") { VaultInput("Search titles, usernames, and notes", searchQuery, { searchQuery = it }) }
                if (visibleEntries.isEmpty()) item(key = "vault_empty") { Text(if (opened.isEmpty()) "No passwords saved yet." else "No matching passwords.", color = UiTokens.Muted) }
                itemsIndexed(visibleEntries, key = { _, entry -> entry.id }) { _, entry ->
                    val originalIndex = opened.indexOfFirst { it.id == entry.id }
                    val dragging = draggingId == entry.id
                    val reorderLabel = "Reorder ${entry.title}. Long press and drag to move."
                    Surface(
                        color = UiTokens.Surface,
                        shape = RoundedCornerShape(UiTokens.CardRadius),
                        shadowElevation = if (dragging) 8.dp else 0.dp,
                        modifier = Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                            .semantics {
                                contentDescription = reorderLabel
                                customActions = listOf(
                                    CustomAccessibilityAction("Move up") { moveEntry(entry.id, -1); originalIndex > 0 },
                                    CustomAccessibilityAction("Move down") { moveEntry(entry.id, 1); originalIndex < opened.lastIndex }
                                )
                            }
                            .pointerInput(entry.id, searchQuery.isBlank()) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        if (searchQuery.isNotBlank()) {
                                            message = "Clear the search to reorder passwords."
                                        } else {
                                            draggingId = entry.id
                                            dragOffset = 0f
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    },
                                    onDrag = { change, amount ->
                                        if (draggingId == entry.id) {
                                            change.consume()
                                            dragOffset += amount.y
                                            dragMoved(entry.id)
                                            val layout = listState.layoutInfo
                                            val item = layout.visibleItemsInfo.firstOrNull { it.key == entry.id }
                                            if (item != null) {
                                                val top = item.offset + dragOffset
                                                val bottom = top + item.size
                                                val edge = 48f
                                                val delta = when {
                                                    top < layout.viewportStartOffset + edge -> -24f
                                                    bottom > layout.viewportEndOffset - edge -> 24f
                                                    else -> 0f
                                                }
                                                if (delta != 0f) scope.launch { dragOffset += listState.scrollBy(delta) }
                                            }
                                        }
                                    },
                                    onDragEnd = { if (draggingId == entry.id) { draggingId = null; dragOffset = 0f; persistOrder() } },
                                    onDragCancel = { if (draggingId == entry.id) { draggingId = null; dragOffset = 0f; persistOrder() } }
                                )
                            }
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(entry.title, color = Ink, fontWeight = FontWeight.SemiBold)
                                if (entry.username.isNotEmpty()) Text(entry.username, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
                                Text(if (revealedId == entry.id) entry.password else "••••••••", color = Ink)
                                if (entry.notes.isNotEmpty()) Text(entry.notes, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                TextButton(
                                    onClick = {
                                        if (revealedId == entry.id) revealedId = null
                                        else beginSensitiveAction(VaultSensitiveAction.Show(entry.id))
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) { Text(if (revealedId == entry.id) "Hide" else "Show", color = Accent) }
                                TextButton(
                                    onClick = { beginSensitiveAction(VaultSensitiveAction.Edit(entry.id)) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) { Text("Edit", color = Accent) }
                                TextButton(
                                    onClick = { beginSensitiveAction(VaultSensitiveAction.Delete(entry.id)) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) { Text("Delete", color = Accent) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showSensitiveAuthChoice) {
        AlertDialog(
            onDismissRequest = { failSensitiveAction() },
            containerColor = Card,
            title = { Text("Authenticate to continue", color = Ink) },
            text = {
                Column {
                    Text("Verify your identity before accessing this password.", color = Ink)
                    TextButton(onClick = { failSensitiveAction() }) { Text("Cancel", color = UiTokens.Muted) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val action = pendingSensitiveAction
                    showSensitiveAuthChoice = false
                    if (action != null) beginBiometricUnlock(action)
                    else failSensitiveAction()
                }) { Text("Fingerprint", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSensitiveAuthChoice = false
                    showSensitivePasswordPrompt = true
                }) { Text("Password", color = Accent) }
            }
        )
    }
    if (showSensitivePasswordPrompt) {
        AlertDialog(
            onDismissRequest = { failSensitiveAction() },
            containerColor = Card,
            title = { Text("Verify master password", color = Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your master password to access this vault entry.", color = Ink)
                    VaultInput("Password", sensitivePassword, { sensitivePassword = it }, secret = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val password = sensitivePassword
                    authenticateSensitiveAction(password)
                }) { Text("Authenticate", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { failSensitiveAction() }) { Text("Cancel", color = UiTokens.Muted) }
            }
        )
    }
}

@Composable private fun VaultInput(label: String, value: String, onValueChange: (String) -> Unit, secret: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = secret || label != "Notes",
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = TextStyle(color = Ink, fontSize = UiTokens.LabelSp.sp),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth().background(UiTokens.SurfaceRaised, RoundedCornerShape(UiTokens.RowRadius)).padding(12.dp),
            decorationBox = { inner -> if (value.isEmpty()) Text(label, color = UiTokens.Subdued); inner() }
        )
    }
}

private fun biometricUnavailableMessage(status: Int): String = when (status) {
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This device has no supported biometric sensor. Use the master password."
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Enroll a fingerprint in Android Settings, or use the master password."
    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "The biometric sensor is temporarily unavailable. Use the master password."
    else -> "Fingerprint unlock is not available on this device. Use the master password."
}

@Composable private fun CalendarHomeScreen(
    tasks: List<Task>,
    settings: com.example.darktodo.data.CalendarWidgetPrefs,
    onDate: (LocalDate) -> Unit
) {
    var month by remember { mutableStateOf(java.time.YearMonth.now()) }
    var selected by remember(month) { mutableStateOf(LocalDate.now().takeIf { it.month == month.month && it.year == month.year } ?: month.atDay(1)) }
    val cells = remember(month) { CalendarDates.monthCells(month, java.util.Locale.getDefault()).chunked(7) }
    val weekdays = remember { CalendarDates.weekdays(java.util.Locale.getDefault()) }
    val tasksByDate = remember(tasks) { tasks.groupBy { it.taskDate } }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(color = colorFromHex(settings.background), shape = RoundedCornerShape(settings.cornerRadius.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", java.util.Locale.getDefault())), color = colorFromHex(settings.heading), fontSize = settings.headingSize.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconAction("Previous month", "chevron_left", colorFromHex(settings.navigationArrows)) { month = month.minusMonths(1) }
                    IconAction("Next month", "chevron_right", colorFromHex(settings.navigationArrows)) { month = month.plusMonths(1) }
                }
                Row(Modifier.fillMaxWidth()) {
                    weekdays.forEach { day -> Box(Modifier.weight(1f).height(32.dp), contentAlignment = Alignment.Center) { Text(day.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()).take(2), color = colorFromHex(settings.weekday), fontSize = settings.weekdaySize.sp) } }
                }
                cells.forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                                if (date != null) {
                                    val status = CalendarDates.status(tasksByDate[date.toString()].orEmpty(), date)
                                    Column(
                                        Modifier.size(38.dp).clip(RoundedCornerShape(50)).background(if (date == selected) colorFromHex(settings.selectedBackground) else Color.Transparent).clickable { selected = date }.padding(2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(date.dayOfMonth.toString(), color = colorFromHex(if (date == selected) settings.selectedText else settings.date), fontSize = settings.dateSize.sp, maxLines = 1)
                                        if (settings.showDots && status != CalendarTaskStatus.NONE) Box(Modifier.size(4.dp).background(colorFromHex(when (status) {
                                            CalendarTaskStatus.COMPLETE -> settings.completeDot
                                            CalendarTaskStatus.MIXED -> settings.mixedDot
                                            CalendarTaskStatus.OPEN -> settings.openDot
                                            CalendarTaskStatus.NONE -> settings.openDot
                                        }), RoundedCornerShape(50)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Surface(color = UiTokens.Surface, shape = RoundedCornerShape(UiTokens.CardRadius), modifier = Modifier.fillMaxWidth().clickable { onDate(selected) }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.getDefault())), color = Ink, fontWeight = FontWeight.SemiBold)
                    val dayTasks = tasks.filter { it.taskDate == selected.toString() }
                    Text(if (dayTasks.isEmpty()) "No tasks scheduled" else "${dayTasks.count { !it.completed }} open · ${dayTasks.count { it.completed }} completed", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
                }
                Text("View tasks  ›", color = Accent, fontSize = UiTokens.LabelSp.sp)
            }
        }
    }
}

@Composable private fun GlobalSettingsScreen(
    onOpenPermissions: () -> Unit,
    appFontSettings: AppFontSettings,
    importedFonts: List<ImportedWidgetFont>,
    onAppFontFamily: (WidgetFontFamily) -> Unit,
    onAppImportedFont: (ImportedWidgetFont) -> Unit,
    onImportAppFont: () -> Unit,
    remindersEnabled: Boolean,
    notificationGranted: Boolean,
    notificationsEnabled: Boolean,
    channelImportance: Int?,
    onOpenVaultSecurity: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenAbout: () -> Unit
) {
    var typographyExpanded by remember { mutableStateOf(false) }
    val channelEnabled = channelImportance != android.app.NotificationManager.IMPORTANCE_NONE
    val notificationsOkay = notificationGranted && notificationsEnabled && channelEnabled
    val permissionNeeded = remindersEnabled && !notificationsOkay
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UiTokens.ControlGap)) {
        SettingsCategoryHeader(ClarityIcons.Personalization, "Personalization")
        CollapsibleSettingsSection("Fonts Customization", selectedFontLabel(appFontSettings.family, appFontSettings.fontFileId, importedFonts), typographyExpanded, { typographyExpanded = it }) {
            SettingsPanel {
                FontFamilyRow("App font", appFontSettings.family, appFontSettings.fontFileId, importedFonts, onAppFontFamily, onAppImportedFont)
                LocalFontImportRow(importedFonts.size, onImportAppFont)
            }
        }
        Spacer(Modifier.height(UiTokens.CompactGap))
        SettingsCategoryHeader(ClarityIcons.Security, "Privacy & Security")
        SettingsNavRow(ClarityIcons.Vault, "Vault Security", "Master password, biometrics, auto lock, audit", onClick = onOpenVaultSecurity)
        SettingsNavRow(ClarityIcons.Security, "Permissions", "Notifications and exact alarms", alert = permissionNeeded, onClick = onOpenPermissions)
        Spacer(Modifier.height(UiTokens.CompactGap))
        SettingsCategoryHeader(ClarityIcons.Backup, "Data")
        BackupSettingsSection()
        Spacer(Modifier.height(UiTokens.CompactGap))
        SettingsCategoryHeader(ClarityIcons.Support, "Support Clarity")
        SettingsNavRow(ClarityIcons.Support, "Support Clarity", "Optional, nothing is unlocked", onClick = onOpenSupport)
        Spacer(Modifier.height(UiTokens.CompactGap))
        SettingsCategoryHeader(ClarityIcons.About, "About")
        SettingsNavRow(ClarityIcons.About, "About Clarity", "Version information", onClick = onOpenAbout)
        Spacer(Modifier.height(UiTokens.SectionGap))
    }
}

@Composable private fun PermissionsScreen(
    remindersEnabled: Boolean,
    notificationGranted: Boolean,
    notificationsEnabled: Boolean,
    channelImportance: Int?,
    exactAlarmsEnabled: Boolean,
    onBack: () -> Unit,
    onRequestNotifications: () -> Unit,
    onNotificationSettings: () -> Unit,
    onExactAlarmSettings: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UiTokens.ControlGap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Permissions", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Access used by Clarity features", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
            }
            TextButton(onClick = onBack) { Text("Done", color = Accent) }
        }
        PermissionDetails(remindersEnabled, notificationGranted, notificationsEnabled, channelImportance, exactAlarmsEnabled, onRequestNotifications, onNotificationSettings, onExactAlarmSettings)
    }
}

@Composable private fun PermissionDetails(
    remindersEnabled: Boolean,
    notificationGranted: Boolean,
    notificationsEnabled: Boolean,
    channelImportance: Int?,
    exactAlarmsEnabled: Boolean,
    onRequestNotifications: () -> Unit,
    onNotificationSettings: () -> Unit,
    onExactAlarmSettings: () -> Unit
) {
    val channelEnabled = channelImportance != android.app.NotificationManager.IMPORTANCE_NONE
    Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ControlGap)) {
        SettingsPanel {
            Text("Task notifications", color = Ink, fontWeight = FontWeight.SemiBold)
            Text("Used to deliver reminders for tasks with a reminder set. ${if (remindersEnabled) "Reminders are currently in use." else "No active task reminders currently need this permission."}", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
            SettingsInfoRow("Notification permission", if (Build.VERSION.SDK_INT < 33) "Not required on this Android version" else if (notificationGranted) "Allowed" else "Not granted")
            SettingsInfoRow("App notifications", if (notificationsEnabled) "On" else "Off")
            if (Build.VERSION.SDK_INT >= 26) SettingsInfoRow("Reminder channel", when (channelImportance) {
                android.app.NotificationManager.IMPORTANCE_NONE -> "Blocked"
                null -> "Created when the first reminder is scheduled"
                else -> "On"
            })
            if (Build.VERSION.SDK_INT >= 33 && !notificationGranted) TextButton(onClick = onRequestNotifications) { Text("Allow notifications", color = Accent) }
            if (!notificationsEnabled || !channelEnabled) TextButton(onClick = onNotificationSettings) { Text("Open notification settings", color = Accent) }
        }
        if (Build.VERSION.SDK_INT >= 31) SettingsPanel {
            Text("Alarms & reminders", color = Ink, fontWeight = FontWeight.SemiBold)
            Text("Exact-alarm access can improve reminder timing. Clarity can still schedule reminders without it, though Android may deliver them later.", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
            SettingsInfoRow("Exact alarms", if (exactAlarmsEnabled) "Allowed" else "Not allowed (optional)")
            if (!exactAlarmsEnabled) TextButton(onClick = onExactAlarmSettings) { Text("Open alarm access settings", color = Accent) }
        }
    }
}

@Composable private fun SettingsInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, fontSize = UiTokens.LabelSp.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun SettingsDescription(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Ink, fontSize = UiTokens.LabelSp.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(description, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp, modifier = Modifier.fillMaxWidth())
    }
}

@Composable private fun PermissionsDialog(
    notificationGranted: Boolean,
    notificationsEnabled: Boolean,
    channelImportance: Int?,
    exactAlarmsEnabled: Boolean,
    onRequestNotifications: () -> Unit,
    onNotificationSettings: () -> Unit,
    onExactAlarmSettings: () -> Unit,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    val channelEnabled = channelImportance != android.app.NotificationManager.IMPORTANCE_NONE
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        title = { Text("Permissions", color = Ink) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Manage the Android access used by task reminders. Android controls whether each permission is granted.", color = Color.LightGray)
                Text("Notification permission: ${if (Build.VERSION.SDK_INT < 33) "Not required on this Android version" else if (notificationGranted) "Allowed" else "Not granted"}", color = Ink)
                Text("App notifications: ${if (notificationsEnabled) "On" else "Off"}", color = Ink)
                if (Build.VERSION.SDK_INT >= 33 && !notificationGranted) TextButton(onClick = onRequestNotifications) { Text("Allow notifications", color = Accent) }
                if (!notificationsEnabled || !channelEnabled) TextButton(onClick = onNotificationSettings) { Text("Open notification settings", color = Accent) }
                if (Build.VERSION.SDK_INT >= 26) {
                    Text("Reminder channel: ${when (channelImportance) { android.app.NotificationManager.IMPORTANCE_NONE -> "Blocked"; null -> "Created when a reminder is first scheduled"; else -> "On" }}", color = Ink)
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    Text("Exact alarms: ${if (exactAlarmsEnabled) "Available" else "Access needed; alarms may be delayed"}", color = Ink)
                    if (!exactAlarmsEnabled) TextButton(onClick = onExactAlarmSettings) { Text("Allow Alarms & reminders", color = Accent) }
                } else {
                    Text("Exact-alarm special access: Not required on this Android version", color = Ink)
                }
                Text("If exact-alarm access is unavailable, Android may deliver reminders later. Notification permission and channel settings are controlled by Android.", color = Color.LightGray)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done", color = Accent) } }
    )
}

@Composable private fun ReminderDialog(value: String, onValue: (String) -> Unit, kind: String, onKind: (String) -> Unit, deadline: Long, onDeadline: (Long) -> Unit, hours: String, onHours: (String) -> Unit, minutes: String, onMinutes: (String) -> Unit, timeZone: String, onTimeZone: (String) -> Unit, taskDate: String, onTaskDate: (String) -> Unit, error: String, onError: (String) -> Unit, dismiss: () -> Unit, save: () -> Unit, completed: Boolean) {
    val context = LocalContext.current
    var showTimeZonePicker by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, containerColor = Card, title = { Text(if (completed) "Edit task" else "Task and reminder", color = Ink) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.OutlinedTextField(value, onValue, label = { Text("Task name") }, singleLine = true, textStyle = TextStyle(color = Ink))
            TextButton(onClick = {
                val date = runCatching { LocalDate.parse(taskDate) }.getOrDefault(LocalDate.now())
                DatePickerDialog(context, { _, year, month, day -> onTaskDate(LocalDate.of(year, month + 1, day).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
            }) { Text("Task date: ${runCatching { LocalDate.parse(taskDate).format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")) }.getOrDefault(taskDate)}", color = Accent) }
            Text("Reminder", color = Ink, fontSize = 14.sp)
            listOf("none" to "No reminder", "time" to "At a specific time", "duration" to "Countdown deadline").forEach { (key, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { if (!completed) onKind(key) }) {
                    RadioButton(selected = kind == key, onClick = { if (!completed) onKind(key) }, enabled = !completed); Text(label, color = if (completed) Color.Gray else Ink, fontSize = 14.sp)
                }
            }
            if (kind == "time" && !completed) Surface(
                onClick = {
                    val current = java.util.Calendar.getInstance().apply { timeInMillis = deadline }
                    DatePickerDialog(context, { _, year, month, day ->
                        val date = java.util.Calendar.getInstance().apply { set(year, month, day) }
                        TimePickerDialog(context, { _, hour, minute ->
                            date.set(java.util.Calendar.HOUR_OF_DAY, hour)
                            date.set(java.util.Calendar.MINUTE, minute)
                            date.set(java.util.Calendar.SECOND, 0)
                            onDeadline(date.timeInMillis)
                        }, current.get(java.util.Calendar.HOUR_OF_DAY), current.get(java.util.Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(context)).show()
                    }, current.get(java.util.Calendar.YEAR), current.get(java.util.Calendar.MONTH), current.get(java.util.Calendar.DAY_OF_MONTH)).show()
                },
                modifier = Modifier.fillMaxWidth().border(1.dp, UiTokens.Secondary.copy(alpha = .55f), RoundedCornerShape(UiTokens.RowRadius)),
                color = UiTokens.Secondary.copy(alpha = .10f),
                shape = RoundedCornerShape(UiTokens.RowRadius)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("TIME", color = UiTokens.Secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Choose date and time", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            TaskStore.dateTime(context, deadline, timeZone),
                            color = UiTokens.Muted,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(">", color = UiTokens.Secondary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
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
                    textStyle = TextStyle(color = Ink),
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
                    androidx.compose.material3.OutlinedTextField(hours, { onHours(it.filter(Char::isDigit).take(4)) }, label = { Text("Hours") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(color = Ink), colors = fieldColors)
                    Spacer(Modifier.width(8.dp)); androidx.compose.material3.OutlinedTextField(minutes, { onMinutes(it.filter(Char::isDigit).take(2)) }, label = { Text("Minutes") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(color = Ink), colors = fieldColors)
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
                    textStyle = TextStyle(color = Ink),
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

private data class ColorPickTarget(val key: String, val label: String, val value: String, val calendar: Boolean)

@Composable private fun SettingsScreen(
    screen: String,
    onBack: () -> Unit,
    settings: WidgetPrefs,
    calendarSettings: com.example.darktodo.data.CalendarWidgetPrefs,
    importedFonts: List<ImportedWidgetFont>,
    fontImportMessage: String?,
    onDismissFontImportMessage: () -> Unit,
    onImportFont: (calendar: Boolean) -> Unit,
    onColor: (String, String) -> Unit,
    onFade: (Int) -> Unit,
    onTaskCardTransparency: (Int) -> Unit,
    onFontFamily: (WidgetFontFamily) -> Unit,
    onTodoImportedFont: (ImportedWidgetFont) -> Unit,
    onCalendarFontFamily: (WidgetFontFamily) -> Unit,
    onCalendarImportedFont: (ImportedWidgetFont) -> Unit,
    onTaskTitleSize: (Float) -> Unit,
    onReset: () -> Unit,
    onCalendarColor: (String, String) -> Unit,
    onCalendarInt: (String, Int) -> Unit,
    onCalendarDots: (Boolean) -> Unit,
    onCalendarReset: () -> Unit,
    onOpenWallpaper: (String) -> Unit,
    onTodoLayout: (Int, Int) -> Unit,
    onSaveDefault: (Boolean) -> Unit,
    onRestoreDefault: (Boolean) -> Boolean
) {
    val isTodo = screen == "TodoSettings"
    var pickTarget by remember { mutableStateOf<ColorPickTarget?>(null) }
    var confirmTodoReset by remember { mutableStateOf(false) }
    var confirmCalendarReset by remember { mutableStateOf(false) }
    var todoColorsExpanded by remember { mutableStateOf(false) }
    var todoLayoutExpanded by remember { mutableStateOf(false) }
    var calendarColorsExpanded by remember { mutableStateOf(false) }
    var todoFontExpanded by remember { mutableStateOf(false) }
    var todoDisplayExpanded by remember { mutableStateOf(false) }
    var calendarFontExpanded by remember { mutableStateOf(false) }
    var calendarSizesExpanded by remember { mutableStateOf(false) }
    var calendarLayoutExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UiTokens.ControlGap)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (isTodo) "Tasks settings" else "Calendar settings", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Widget preferences", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
            }
            TextButton(onClick = onBack) { Text("Done", color = Accent) }
        }
        if (isTodo) SettingsSection("To-Do Widget", "Independent task widget appearance") {
            WallpaperRow(com.example.darktodo.data.WidgetBackgrounds.TODO, "To-Do widget wallpaper", onOpenWallpaper)
            CollapsibleSettingsSection("Font", selectedFontLabel(settings.fontFamily, settings.fontFileId, importedFonts), todoFontExpanded, { todoFontExpanded = it }) {
                SettingsPanel {
                    FontFamilyRow("Font family", settings.fontFamily, settings.fontFileId, importedFonts, onFontFamily, onTodoImportedFont)
                    LocalFontImportRow(importedFonts.size) { onImportFont(false) }
                }
            }
            CollapsibleSettingsSection("Text & completion", "${settings.taskTitleSizeSp.toInt()} sp title · ${settings.fadePercent}% fade", todoDisplayExpanded, { todoDisplayExpanded = it }) {
                SettingsPanel {
                    SettingSlider("Task-title size", "${settings.taskTitleSizeSp.toInt()} sp", settings.taskTitleSizeSp, 12f..24f, onTaskTitleSize)
                    SettingSlider("Completed-task fade", "${settings.fadePercent}%", settings.fadePercent.toFloat(), 0f..100f, { onFade(it.toInt()) }, intValued = true)
                }
            }
            ExpandableColorPalette("To-Do widget colors", 6, todoColorsExpanded, { todoColorsExpanded = it }) {
              Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ControlGap)) {
                ColorSettingRow("Incomplete checkbox", settings.incomplete) { pickTarget = ColorPickTarget("incomplete", "Incomplete checkbox", settings.incomplete, false) }
                ColorSettingRow("Completed checkbox", settings.complete) { pickTarget = ColorPickTarget("complete", "Completed checkbox", settings.complete, false) }
                ColorSettingRow("Task-count panel", settings.countPanel) { pickTarget = ColorPickTarget("count_panel", "Task-count panel", settings.countPanel, false) }
                ColorSettingRow("Widget background", settings.widgetBackground) { pickTarget = ColorPickTarget("background", "Widget background", settings.widgetBackground, false) }
                ColorSettingRow("Task card background", settings.taskCard) { pickTarget = ColorPickTarget("task_card", "Task card background", settings.taskCard, false) }
                SettingSlider("Task card background transparency", "${settings.taskCardTransparencyPercent}%", settings.taskCardTransparencyPercent.toFloat(), 0f..100f, { onTaskCardTransparency(it.toInt()) }, intValued = true)
                ColorSettingRow("Task title", settings.taskTitle) { pickTarget = ColorPickTarget("task_title", "Task title", settings.taskTitle, false) }
              }
            }
            CollapsibleSettingsSection("Size & layout", "${settings.paddingDp} dp padding · ${settings.rowSpacingDp} dp row spacing", todoLayoutExpanded, { todoLayoutExpanded = it }) {
                SettingsPanel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Compact" to (4 to 0), "Comfortable" to (8 to 2), "Spacious" to (14 to 6)).forEach { (label, v) ->
                            com.example.darktodo.ui.OutlineChip(label, settings.paddingDp == v.first && settings.rowSpacingDp == v.second, { onTodoLayout(v.first, v.second) }, Modifier.weight(1f))
                        }
                    }
                    SettingSlider("Widget padding", "${settings.paddingDp} dp", settings.paddingDp.toFloat(), 0f..20f, { onTodoLayout(it.toInt(), settings.rowSpacingDp) }, intValued = true)
                    SettingSlider("Row spacing", "${settings.rowSpacingDp} dp", settings.rowSpacingDp.toFloat(), 0f..12f, { onTodoLayout(settings.paddingDp, it.toInt()) }, intValued = true)
                }
            }
            DefaultStyleActions(isTodo = true, onSave = onSaveDefault, onRestore = onRestoreDefault)
            ResetAction("Reset To-Do settings") { confirmTodoReset = true }
        } else SettingsSection("Calendar Widget", "Independent calendar appearance and task indicators") {
            WallpaperRow(com.example.darktodo.data.WidgetBackgrounds.CALENDAR, "Calendar widget wallpaper", onOpenWallpaper)
            CollapsibleSettingsSection("Font", selectedFontLabel(calendarSettings.fontFamily, calendarSettings.fontFileId, importedFonts), calendarFontExpanded, { calendarFontExpanded = it }) {
                SettingsPanel {
                    FontFamilyRow("Font family", calendarSettings.fontFamily, calendarSettings.fontFileId, importedFonts, onCalendarFontFamily, onCalendarImportedFont)
                    LocalFontImportRow(importedFonts.size) { onImportFont(true) }
                }
            }
            CollapsibleSettingsSection("Text sizes", "Date ${calendarSettings.dateSize} sp · Weekday ${calendarSettings.weekdaySize} sp · Month ${calendarSettings.headingSize} sp", calendarSizesExpanded, { calendarSizesExpanded = it }) {
                SettingsPanel {
                    SettingSlider("Date size", "${calendarSettings.dateSize} sp", calendarSettings.dateSize.toFloat(), 12f..20f, { onCalendarInt("date_size", it.toInt()) }, intValued = true)
                    SettingSlider("Weekday size", "${calendarSettings.weekdaySize} sp", calendarSettings.weekdaySize.toFloat(), 8f..14f, { onCalendarInt("weekday_size", it.toInt()) }, intValued = true)
                    SettingSlider("Month heading size", "${calendarSettings.headingSize} sp", calendarSettings.headingSize.toFloat(), 14f..24f, { onCalendarInt("heading_size", it.toInt()) }, intValued = true)
                }
            }
            ExpandableColorPalette("Calendar widget colors", 10, calendarColorsExpanded, { calendarColorsExpanded = it }) {
              Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ControlGap)) {
                val colors = listOf(
                    "background" to ("Background" to calendarSettings.background),
                    "date" to ("Date text" to calendarSettings.date),
                    "weekday" to ("Weekday text" to calendarSettings.weekday),
                    "heading" to ("Month heading" to calendarSettings.heading),
                    "selected_background" to ("Selected-date circle" to calendarSettings.selectedBackground),
                    "selected_text" to ("Selected-date text" to calendarSettings.selectedText),
                    "navigation_arrows" to ("Month navigation arrows" to calendarSettings.navigationArrows),
                    "complete_dot" to ("Completed-task dot" to calendarSettings.completeDot),
                    "mixed_dot" to ("Mixed-status dot" to calendarSettings.mixedDot),
                    "open_dot" to ("Incomplete-task dot" to calendarSettings.openDot)
                )
                colors.forEach { (key, pair) ->
                    ColorSettingRow(pair.first, pair.second) { pickTarget = ColorPickTarget(key, pair.first, pair.second, true) }
                }
              }
            }
            CollapsibleSettingsSection("Layout", "${calendarSettings.cornerRadius} dp corners · ${calendarSettings.rowSpacing} dp row spacing", calendarLayoutExpanded, { calendarLayoutExpanded = it }) {
                SettingsPanel {
                    SettingSlider("Corner radius", "${calendarSettings.cornerRadius} dp", calendarSettings.cornerRadius.toFloat(), 8f..32f, { onCalendarInt("corner_radius", it.toInt()) }, intValued = true)
                    SettingSlider("Vertical row spacing", "${calendarSettings.rowSpacing} dp", calendarSettings.rowSpacing.toFloat(), 0f..8f, { onCalendarInt("row_spacing", it.toInt()) }, intValued = true)
                }
            }
            SettingsPanel {
                SettingsSwitchRow("Show task-status dots", "Mark dates with completed or open tasks", calendarSettings.showDots, onCalendarDots)
            }
            DefaultStyleActions(isTodo = false, onSave = onSaveDefault, onRestore = onRestoreDefault)
            ResetAction("Reset Calendar settings") { confirmCalendarReset = true }
        }
    }

    pickTarget?.let { target ->
        ColorPickerDialog(target.label, target.value, onApply = { value ->
            if (target.calendar) onCalendarColor(target.key, value) else onColor(target.key, value)
            pickTarget = null
        }, onDismiss = { pickTarget = null })
    }
    if (confirmTodoReset) {
        AlertDialog(
            onDismissRequest = { confirmTodoReset = false },
            containerColor = UiTokens.SurfaceRaised,
            title = { Text("Reset To-Do settings?", color = Ink) },
            text = { Text("To-Do widget preferences will return to their defaults. Tasks and history are kept.", color = UiTokens.Muted) },
            confirmButton = { TextButton(onClick = { onReset(); confirmTodoReset = false }) { Text("Reset", color = Accent) } },
            dismissButton = { TextButton(onClick = { confirmTodoReset = false }) { Text("Cancel", color = Ink) } }
        )
    }
    if (confirmCalendarReset) {
        AlertDialog(
            onDismissRequest = { confirmCalendarReset = false },
            containerColor = UiTokens.SurfaceRaised,
            title = { Text("Reset Calendar settings?", color = Ink) },
            text = { Text("Calendar appearance will return to its defaults. Tasks and To-Do settings are kept.", color = UiTokens.Muted) },
            confirmButton = { TextButton(onClick = { onCalendarReset(); confirmCalendarReset = false }) { Text("Reset", color = Accent) } },
            dismissButton = { TextButton(onClick = { confirmCalendarReset = false }) { Text("Cancel", color = Ink) } }
        )
    }
    fontImportMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissFontImportMessage,
            containerColor = UiTokens.SurfaceRaised,
            title = { Text("Widget font", color = Ink) },
            text = { Text(message, color = UiTokens.Muted) },
            confirmButton = { TextButton(onClick = onDismissFontImportMessage) { Text("OK", color = Accent) } }
        )
    }
}

@Composable private fun IconAction(label: String, icon: String, iconColor: Color = Accent, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp).semantics { contentDescription = label }) {
        Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) { ClarityIcon(icon, iconColor, Modifier.fillMaxSize()) }
    }
}


@Composable private fun ClarityIcon(name: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (size.minDimension * .09f).coerceAtLeast(1.6f)
        val line = Stroke(width = stroke, cap = StrokeCap.Round)
        when (name) {
            "calendar" -> {
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(w * .14f, h * .20f), size = androidx.compose.ui.geometry.Size(w * .72f, h * .68f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .09f), style = line)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * .14f, h * .39f), androidx.compose.ui.geometry.Offset(w * .86f, h * .39f), stroke, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * .34f, h * .10f), androidx.compose.ui.geometry.Offset(w * .34f, h * .30f), stroke, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * .66f, h * .10f), androidx.compose.ui.geometry.Offset(w * .66f, h * .30f), stroke, StrokeCap.Round)
                listOf(.36f to .57f, .62f to .57f, .36f to .75f, .62f to .75f).forEach { (x, y) -> drawCircle(color, stroke * .72f, androidx.compose.ui.geometry.Offset(w * x, h * y)) }
            }
            "todo" -> {
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(w * .16f, h * .13f), size = androidx.compose.ui.geometry.Size(w * .68f, h * .74f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .12f), style = line)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * .31f, h * .52f), androidx.compose.ui.geometry.Offset(w * .45f, h * .66f), stroke, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * .45f, h * .66f), androidx.compose.ui.geometry.Offset(w * .71f, h * .36f), stroke, StrokeCap.Round)
            }
            "lock" -> {
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(w * .18f, h * .42f), size = androidx.compose.ui.geometry.Size(w * .64f, h * .48f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .08f), style = line)
                drawArc(color, 180f, 180f, false, androidx.compose.ui.geometry.Offset(w * .32f, h * .12f), androidx.compose.ui.geometry.Size(w * .36f, h * .52f), style = line)
                drawCircle(color, stroke * .7f, androidx.compose.ui.geometry.Offset(w * .5f, h * .62f))
            }
            "chevron_left", "chevron_right" -> {
                val direction = if (name == "chevron_left") -1f else 1f
                val centerX = w * .5f
                val innerX = centerX - direction * w * .16f
                val outerX = centerX + direction * w * .16f
                drawLine(color, androidx.compose.ui.geometry.Offset(innerX, h * .25f), androidx.compose.ui.geometry.Offset(outerX, h * .5f), stroke, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(outerX, h * .5f), androidx.compose.ui.geometry.Offset(innerX, h * .75f), stroke, StrokeCap.Round)
            }
            else -> {
                listOf(.24f, .50f, .76f).forEachIndexed { index, y ->
                    drawLine(color, androidx.compose.ui.geometry.Offset(w * .22f, h * y), androidx.compose.ui.geometry.Offset(w * .78f, h * y), stroke, StrokeCap.Round)
                    val x = when (index) { 0 -> .43f; 1 -> .62f; else -> .36f }
                    drawCircle(UiTokens.Background, stroke * 1.9f, androidx.compose.ui.geometry.Offset(w * x, h * y))
                    drawCircle(color, stroke * 1.35f, androidx.compose.ui.geometry.Offset(w * x, h * y), style = line)
                }
            }
        }
    }
}

@Composable private fun NavigationItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) Accent.copy(alpha = .16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = UiTokens.NavigationHeight)
            .padding(horizontal = UiTokens.ControlGap, vertical = UiTokens.SmallGap),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Accent else UiTokens.Muted, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable private fun SettingsSection(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ControlGap), content = {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Ink, fontSize = UiTokens.SectionTitleSp.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
        }
        content()
    })
}

@Composable private fun SettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = UiTokens.Surface, shape = RoundedCornerShape(UiTokens.CardRadius)) {
        Column(Modifier.fillMaxWidth().padding(UiTokens.PanelPadding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable private fun CollapsibleSettingsSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ControlGap)) {
        Surface(
            onClick = { onExpandedChange(!expanded) },
            color = UiTokens.SurfaceRaised,
            shape = RoundedCornerShape(UiTokens.RowRadius),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = UiTokens.ControlHeight).padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiTokens.ControlGap)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, color = Ink, fontSize = UiTokens.LabelSp.sp, fontWeight = FontWeight.Medium)
                    Text(summary, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (expanded) "\u2303" else "\u2304", color = UiTokens.Secondary, fontSize = 20.sp)
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(160)),
            exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(120))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ControlGap), content = content)
        }
    }
}

private fun selectedFontLabel(
    family: WidgetFontFamily,
    fileId: String?,
    importedFonts: List<ImportedWidgetFont>
): String = importedFonts.firstOrNull { it.id == fileId }?.name ?: family.label

@Composable private fun ExpandableColorPalette(
    title: String,
    colorCount: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    CollapsibleSettingsSection(
        title = title,
        summary = "$colorCount color options",
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        content = content
    )
}
@Composable private fun ColorSettingRow(label: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = UiTokens.Surface,
        shape = RoundedCornerShape(UiTokens.RowRadius),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.heightIn(min = UiTokens.ControlHeight).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, color = Ink, modifier = Modifier.weight(1f), fontSize = UiTokens.LabelSp.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value.uppercase(), color = UiTokens.Subdued, fontSize = UiTokens.DetailSp.sp, maxLines = 1)
            Box(Modifier.size(UiTokens.SwatchSize).background(colorFromHex(value), RoundedCornerShape(UiTokens.SwatchRadius)).border(1.dp, Color.White.copy(alpha = .32f), RoundedCornerShape(UiTokens.SwatchRadius)))
        }
    }
}

@Composable private fun FontFamilyRow(
    label: String,
    selected: WidgetFontFamily,
    selectedFontId: String?,
    importedFonts: List<ImportedWidgetFont>,
    onSelect: (WidgetFontFamily) -> Unit,
    onSelectImported: (ImportedWidgetFont) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val selectedFontFamily = remember(selected, selectedFontId) { composeFontFamily(context, selected, selectedFontId) }
    Row(Modifier.fillMaxWidth().heightIn(min = UiTokens.ControlHeight), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, fontSize = UiTokens.LabelSp.sp, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    importedFonts.firstOrNull { it.id == selectedFontId }?.name ?: selected.label,
                    color = UiTokens.Secondary, fontSize = UiTokens.LabelSp.sp, fontFamily = selectedFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                WidgetFontFamily.entries.forEach { family ->
                    DropdownMenuItem(text = { Text(family.label, fontFamily = composeFontFamily(context, family, null)) }, onClick = { onSelect(family); expanded = false })
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) importedFonts.forEach { font ->
                    DropdownMenuItem(text = { Text(font.name, fontFamily = composeFontFamily(context, selected, font.id), maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { onSelectImported(font); expanded = false })
                }
            }
        }
    }
}

@Composable private fun LocalFontImportRow(importedCount: Int, onImport: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (importedCount == 0) "Use a local .ttf, .otf, or .ttc file" else "$importedCount local font${if (importedCount == 1) "" else "s"} available"
            } else "Local widget fonts require Android 9 or newer",
            color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp, modifier = Modifier.weight(1f)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            TextButton(onClick = onImport) { Text("Import", color = UiTokens.Secondary) }
        }
    }
}

@Composable private fun SettingSlider(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, steps: Int = 0, intValued: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Ink, fontSize = UiTokens.LabelSp.sp, modifier = Modifier.weight(1f))
            Text(valueLabel, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = { next -> if (!intValued || next.toInt() != value.toInt()) onChange(next) },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(thumbColor = UiTokens.Secondary, activeTrackColor = UiTokens.Secondary, inactiveTrackColor = UiTokens.Secondary.copy(alpha = .25f), activeTickColor = UiTokens.Background, inactiveTickColor = UiTokens.Secondary.copy(alpha = .5f))
        )
    }
}

@Composable private fun SettingsSwitchRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = Ink, fontSize = UiTokens.LabelSp.sp)
            Text(description, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent, uncheckedThumbColor = UiTokens.Muted, uncheckedTrackColor = UiTokens.SurfaceRaised))
    }
}

@Composable private fun ResetAction(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, color = Accent, fontSize = UiTokens.LabelSp.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable private fun HistoryScreen(events: List<TaskEvent>, route: List<String>, onRoute: (List<String>) -> Unit, onDelete: (String) -> Unit, onClear: (String) -> Unit, onClearAll: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val yearFmt = DateTimeFormatter.ofPattern("yyyy").withZone(zone)
    val monthFmt = DateTimeFormatter.ofPattern("yyyy-MM").withZone(zone)
    val dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)
    val shown = remember(events, route) { when (route.size) {
        0 -> events.map { yearFmt.format(Instant.ofEpochMilli(it.at)) }.distinct().sorted()
        1 -> events.map { monthFmt.format(Instant.ofEpochMilli(it.at)) }.filter { it.startsWith(route[0]) }.distinct().sorted()
        2 -> events.map { dayFmt.format(Instant.ofEpochMilli(it.at)) }.filter { it.startsWith(route[1]) }.distinct().sorted()
        else -> emptyList()
    } }
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

@Composable private fun WallpaperRow(key: String, label: String, onOpen: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val status = if (com.example.darktodo.data.WidgetBackgrounds.has(context, key)) "${com.example.darktodo.data.WidgetBackgrounds.fade(context, key)}% fade" else "Not set"
    SettingsNavRow(ClarityIcons.Wallpapers, label, "", status) { onOpen(key) }
}


@Composable private fun DefaultStyleActions(isTodo: Boolean, onSave: (Boolean) -> Unit, onRestore: (Boolean) -> Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val calendar = !isTodo
    var saved by remember { mutableStateOf(if (calendar) CalendarWidgetSettings.hasSavedDefault(context) else WidgetSettings.hasSavedDefault(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    SettingsPanel {
        Text("Default style", color = Ink, fontSize = UiTokens.SectionTitleSp.sp, fontWeight = FontWeight.SemiBold)
        Text("Save the current look, then restore it any time after experimenting.", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onSave(calendar); saved = true; message = "Saved as default style." }) { Text("Save As Default Style", color = Accent) }
            TextButton(onClick = { message = if (onRestore(calendar)) "Default style restored." else "No default style saved yet." }, enabled = saved) { Text("Restore default", color = if (saved) Accent else UiTokens.Subdued) }
        }
        message?.let { Text(it, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp) }
    }
}
