package com.example.darktodo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.darktodo.MainActivity
import com.example.darktodo.R
import com.example.darktodo.data.LocalVaultStore

/** Locked entry point only; this widget never displays vault contents. */
class VaultWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.vault_widget)
            val hasVault = LocalVaultStore.exists(context)
            views.setTextViewText(
                R.id.vault_widget_status,
                context.getString(if (hasVault) R.string.vault_widget_locked_status else R.string.vault_widget_setup_status)
            )
            views.setContentDescription(
                R.id.vault_widget_root,
                context.getString(if (hasVault) R.string.vault_widget_locked_content_description else R.string.vault_widget_setup_content_description)
            )
            val intent = Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_VAULT)
                .setData(android.net.Uri.parse("clarity://vault/open/$id"))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.vault_widget_root, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
