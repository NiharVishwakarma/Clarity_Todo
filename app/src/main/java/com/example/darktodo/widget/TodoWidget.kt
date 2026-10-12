package com.example.darktodo.widget

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionRunCallback
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight as GlanceFontWeight
import androidx.glance.text.TextDecoration
import androidx.glance.unit.ColorProvider
import androidx.glance.unit.dp
import androidx.glance.unit.sp
import com.example.darktodo.MainActivity
import com.example.darktodo.data.Task
import com.example.darktodo.data.TaskStore
import com.example.darktodo.data.WidgetPrefs
import com.example.darktodo.data.WidgetSettings

class TodoWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent(TaskStore.tasks(context), WidgetSettings.load(context)) }
    }

    @Composable private fun WidgetContent(tasks: List<Task>, settings: WidgetPrefs) {
        val checkboxIncomplete = ColorProvider(Color(AndroidColor.parseColor(settings.incomplete)))
        val checkboxComplete = ColorProvider(Color(AndroidColor.parseColor(settings.complete)))
        val countPanel = ColorProvider(Color(AndroidColor.parseColor(settings.countPanel)))
        val widgetBackground = ColorProvider(Color(AndroidColor.parseColor(settings.widgetBackground)))
        val taskCard = ColorProvider(Color(AndroidColor.parseColor(settings.taskCard)))
        val taskTitle = Color(AndroidColor.parseColor(settings.taskTitle))
        val white = ColorProvider(Color(0xFFF5F5F5))
        val chosenWeight = when {
            settings.fontWeight < 500 -> GlanceFontWeight.Normal
            settings.fontWeight < 600 -> GlanceFontWeight.Medium
            else -> GlanceFontWeight.Bold
        }
        val openApp = actionStartActivity<MainActivity>()
        Row(GlanceModifier.fillMaxSize().background(widgetBackground).padding(10.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Column(GlanceModifier.width(58.dp).fillMaxSize().background(countPanel).padding(7.dp), horizontalAlignment = Alignment.Horizontal.CenterHorizontally, verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text(tasks.count { !it.completed }.toString(), style = TextStyle(color = white, fontSize = 22.sp))
                Text("Tasks", style = TextStyle(color = white, fontSize = 11.sp))
            }
            Spacer(GlanceModifier.width(9.dp))
            Column(GlanceModifier.defaultWeight().fillMaxSize().background(taskCard).padding(horizontal = 8.dp, vertical = 5.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text("TODO", GlanceModifier.defaultWeight().clickable(openApp), style = TextStyle(color = white, fontSize = 12.sp, fontWeight = chosenWeight, fontFamily = FontFamily.SansSerif))
                    Text("X", GlanceModifier.clickable(actionRunCallback<ClearCompletedAction>()).padding(horizontal = 5.dp), style = TextStyle(color = checkboxIncomplete, fontSize = 18.sp, fontWeight = chosenWeight))
                }
                LazyColumn(GlanceModifier.defaultWeight()) {
                    items(tasks.take(5), itemId = { it.id.hashCode().toLong() }) { task ->
                        val rowAlpha = if (task.completed) 1f - settings.fadePercent / 100f else 1f
                        val fadedTitle = ColorProvider(taskTitle.copy(alpha = rowAlpha))
                        val fadedCheck = ColorProvider(Color(AndroidColor.parseColor(if (task.completed) settings.complete else settings.incomplete)).copy(alpha = rowAlpha))
                        Row(GlanceModifier.fillMaxWidth().background(ColorProvider(Color(AndroidColor.parseColor(settings.taskCard)).copy(alpha = rowAlpha))).padding(vertical = 2.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
                            Text(if (task.completed) "\u25cf" else "\u25cb", GlanceModifier.clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(TodoWidget.TaskIdKey to task.id))), style = TextStyle(color = fadedCheck, fontSize = 17.sp))
                            Spacer(GlanceModifier.width(7.dp))
                            Text(task.title, GlanceModifier.defaultWeight().clickable(openApp), style = TextStyle(color = fadedTitle, fontSize = 13.sp, fontWeight = chosenWeight, fontFamily = FontFamily.SansSerif, textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None))
                        }
                    }
                }
                Text("+ Add task", GlanceModifier.fillMaxWidth().clickable(openApp).padding(top = 2.dp), style = TextStyle(color = checkboxIncomplete, fontSize = 12.sp, fontWeight = chosenWeight))
            }
        }
    }

    companion object { val TaskIdKey = ActionParameters.Key<String>("task_id") }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TodoWidget() }

class ToggleTaskAction : ActionCallback {
    override suspend fun onRun(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        parameters[TodoWidget.TaskIdKey]?.let { TaskStore.toggle(context, it) }
        TodoWidget().updateAll(context)
    }
}

class ClearCompletedAction : ActionCallback {
    override suspend fun onRun(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        TaskStore.clearCompleted(context)
        TodoWidget().updateAll(context)
    }
}
