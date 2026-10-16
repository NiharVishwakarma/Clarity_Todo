package com.example.darktodo.widget

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import com.example.darktodo.data.TaskStore

/**
 * Invisible trampoline for To-Do widget row taps. Toggles a task, or shows the full title of a
 * task whose title is truncated in the widget.
 */
class TaskActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val taskId = intent.getStringExtra(TodoWidget.EXTRA_TASK_ID)
        when (intent.getStringExtra(TodoWidget.EXTRA_COLLECTION_ACTION)) {
            TodoWidget.ACTION_SHOW_TITLE -> {
                val title = intent.getStringExtra(TodoWidget.EXTRA_TASK_TITLE).orEmpty()
                if (title.isBlank()) { finish(); return }
                AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Task")
                    .setMessage(title)
                    .setPositiveButton("Close", null)
                    .setNeutralButton("Toggle done") { _, _ -> taskId?.let { TaskStore.toggle(applicationContext, it) } }
                    .setOnDismissListener { finish() }
                    .show()
            }
            else -> {
                if (taskId != null) TaskStore.toggle(applicationContext, taskId)
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        overridePendingTransition(0, 0)
    }
}
