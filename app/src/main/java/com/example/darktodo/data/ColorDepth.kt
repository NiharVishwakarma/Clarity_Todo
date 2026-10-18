package com.example.darktodo.data

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.roundToInt

/** Static raised color fill; every stop retains the base alpha, including fully transparent fills. */
object ColorDepth {
    val positions: List<Float> = listOf(0f, 0.08f, 0.45f, 0.88f, 1f)

    fun colors(color: Int): IntArray = intArrayOf(
        blend(color, 255, 0.22f), blend(color, 255, 0.07f), color,
        blend(color, 0, 0.06f), blend(color, 0, 0.23f)
    )

    private fun blend(color: Int, target: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val value = (color ushr shift) and 255
            return (value + (target - value) * amount).roundToInt().coerceIn(0, 255)
        }
        return (color and -0x1000000) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun draw(canvas: Canvas, area: RectF, radius: Float, color: Int, enabled: Boolean = true) {
        if (area.width() <= 0f || area.height() <= 0f) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            if (enabled) {
                alpha = 255
                shader = LinearGradient(
                    area.left, area.top, area.right, area.bottom,
                    colors(color), positions.toFloatArray(), Shader.TileMode.CLAMP
                )
            }
        }
        canvas.drawRoundRect(area, radius, radius, paint)
    }
}
