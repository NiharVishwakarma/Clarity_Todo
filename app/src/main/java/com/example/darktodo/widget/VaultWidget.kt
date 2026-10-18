package com.example.darktodo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.widget.RemoteViews
import com.example.darktodo.MainActivity
import com.example.darktodo.R
import com.example.darktodo.data.LocalVaultStore
import com.example.darktodo.data.LocalWidgetFonts
import com.example.darktodo.data.StickerHex
import com.example.darktodo.data.VaultAppearance
import com.example.darktodo.data.VaultAppearancePrefs
import kotlin.math.ceil

/** Locked entry point only; this widget never displays vault contents. */
class VaultWidgetReceiver : AppWidgetProvider() {
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val style = VaultAppearancePrefs.load(context)
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.vault_widget)
            val hasVault = LocalVaultStore.exists(context)
            val options = manager.getAppWidgetOptions(id)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).coerceIn(110, 600)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 78).coerceIn(78, 400)
            val textHeight = height - 20f
            val totalSize = style.titleSizeSp + style.statusSizeSp
            views.setImageViewBitmap(R.id.vault_widget_background, VaultWidgetRendering.background(context, style, width, height))
            val status = context.getString(if (hasVault) R.string.vault_widget_locked_status else R.string.vault_widget_setup_status)
            views.setTextViewText(
                R.id.vault_widget_status,
                status
            )
            views.setViewVisibility(R.id.vault_widget_title, View.GONE)
            views.setViewVisibility(R.id.vault_widget_status, View.GONE)
            views.setViewVisibility(R.id.vault_widget_title_image, View.VISIBLE)
            views.setViewVisibility(R.id.vault_widget_status_image, View.VISIBLE)
            views.setImageViewBitmap(R.id.vault_widget_title_image, VaultWidgetRendering.text(context,
                context.getString(R.string.vault_widget_label), style, true, (width - 108).toFloat(),
                textHeight * style.titleSizeSp / totalSize))
            views.setImageViewBitmap(R.id.vault_widget_status_image, VaultWidgetRendering.text(context,
                status, style, false, (width - 108).toFloat(), textHeight * style.statusSizeSp / totalSize))
            views.setContentDescription(R.id.vault_widget_title_image, context.getString(R.string.vault_widget_label))
            views.setContentDescription(R.id.vault_widget_status_image, status)
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

object VaultWidget {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, VaultWidgetReceiver::class.java))
        if (ids.isNotEmpty()) VaultWidgetReceiver().onUpdate(context, manager, ids)
    }
}

/** Typeface stays in the app process; only bounded text/background bitmaps cross into the launcher. */
object VaultWidgetRendering {
    fun text(
        context: Context, text: String, appearance: VaultAppearance, title: Boolean,
        maxWidthDp: Float, maxHeightDp: Float = 100f
    ): Bitmap {
        val style = appearance.sanitized()
        val metrics = context.resources.displayMetrics
        val scale = minOf(metrics.density, 2f)
        val base = LocalWidgetFonts.typeface(context, style.fontFileId)
            ?: Typeface.create(style.fontFamily, Typeface.NORMAL)
        val weight = if (title) style.titleWeight else style.statusWeight
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false)
                else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
            textSize = (if (title) style.titleSizeSp else style.statusSizeSp) * metrics.scaledDensity * scale / metrics.density
            color = StickerHex.toArgb(if (title) style.titleColor else style.statusColor)
        }
        val padding = ceil(2 * scale).toInt()
        val availableHeight = (maxHeightDp.coerceIn(1f, 200f) * scale - padding * 2).coerceAtLeast(1f)
        val naturalHeight = paint.fontMetrics.run { descent - ascent }
        if (naturalHeight > availableHeight) paint.textSize *= availableHeight / naturalHeight
        val width = (maxWidthDp.coerceIn(1f, 600f) * scale).toInt().coerceAtLeast(1)
        val clipped = TextUtils.ellipsize(text, paint, (width - padding * 2).coerceAtLeast(1).toFloat(), TextUtils.TruncateAt.END).toString()
        val fm = paint.fontMetrics
        val bitmap = Bitmap.createBitmap(
            (ceil(paint.measureText(clipped)).toInt() + padding * 2).coerceIn(1, width),
            (ceil(fm.descent - fm.ascent).toInt() + padding * 2).coerceAtLeast(1), Bitmap.Config.ARGB_8888
        )
        bitmap.density = (metrics.densityDpi * scale / metrics.density).toInt()
        Canvas(bitmap).drawText(clipped, padding.toFloat(), padding - fm.ascent, paint)
        return bitmap
    }

    fun background(context: Context, appearance: VaultAppearance, widthDp: Int, heightDp: Int): Bitmap {
        val style = appearance.sanitized()
        val scale = minOf(context.resources.displayMetrics.density, 1f)
        val width = (widthDp.coerceIn(1, 600) * scale).toInt().coerceAtLeast(1)
        val height = (heightDp.coerceIn(1, 400) * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = scale
        val bounds = RectF(stroke / 2, stroke / 2, width - stroke / 2, height - stroke / 2)
        val radius = style.cornerRadiusDp * scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.backgroundArgb() }
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = style.outlineArgb()
        canvas.drawRoundRect(bounds, radius, radius, paint)
        return bitmap
    }
}
