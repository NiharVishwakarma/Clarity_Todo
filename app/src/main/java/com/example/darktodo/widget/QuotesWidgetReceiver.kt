package com.example.darktodo.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** Standalone picker entry; shares quote rendering and rotation with legacy quote-mode widgets. */
class QuotesWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { StickerWidget.update(context, manager, it) }
        QuotesWidgetRotation.reconcile(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        StickerWidget.update(context, manager, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        StickerWidget.forget(context, ids)
    }
}
