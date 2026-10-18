package com.example.darktodo.ui

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.darktodo.data.ColorDepth

/** Call after the surface's clip, just like a flat background. No extra opaque layer or shadow. */
fun Modifier.depthBackground(color: Color, enabled: Boolean): Modifier =
    if (!enabled) background(color) else background(
        Brush.linearGradient(
            *ColorDepth.positions.zip(ColorDepth.colors(color.toArgb()).map { Color(it) }).toTypedArray()
        )
    )
