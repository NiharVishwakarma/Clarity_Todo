package com.example.darktodo

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.darktodo.data.WidgetBackgrounds
import com.example.darktodo.widget.CalendarWidget
import com.example.darktodo.widget.TodoWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

private val EditorAccent = Color(0xFFF20D24)
private val EditorInk = Color(0xFFF4F3F5)
private val EditorMuted = Color(0xFFAAA8B0)
private val EditorSurface = Color(0xFF19191E)

private const val OUTPUT_WIDTH = 800
private const val MAX_ZOOM = 6f

private fun coverScale(bitmap: Bitmap, frameWidth: Float, frameHeight: Float) =
    max(frameWidth / bitmap.width, frameHeight / bitmap.height)

/** Shared by the live preview and the saved result, so what you see is what the widget gets. */
private fun drawTransformed(canvas: android.graphics.Canvas, bitmap: Bitmap, frameWidth: Float, frameHeight: Float, zoom: Float, offset: Offset) {
    val scale = coverScale(bitmap, frameWidth, frameHeight) * zoom
    canvas.save()
    canvas.translate(frameWidth / 2f + offset.x, frameHeight / 2f + offset.y)
    canvas.scale(scale, scale)
    canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, Paint(Paint.FILTER_BITMAP_FLAG))
    canvas.restore()
}

private fun clampOffset(bitmap: Bitmap, frame: IntSize, zoom: Float, offset: Offset): Offset {
    if (frame.width == 0 || frame.height == 0) return Offset.Zero
    val scale = coverScale(bitmap, frame.width.toFloat(), frame.height.toFloat()) * zoom
    val maxX = max(0f, (bitmap.width * scale - frame.width) / 2f)
    val maxY = max(0f, (bitmap.height * scale - frame.height) / 2f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
    if (degrees % 360f == 0f) return bitmap
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun decodeUri(context: Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val orientation = context.contentResolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    rotate(decoded, when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    })
}.getOrNull()

private fun deviceWallpaper(context: Context): Bitmap? = runCatching {
    val drawable = WallpaperManager.getInstance(context).drawable ?: return null
    val width = drawable.intrinsicWidth.coerceIn(1, 2048)
    val height = drawable.intrinsicHeight.coerceIn(1, 2048)
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        drawable.setBounds(0, 0, width, height)
        drawable.draw(android.graphics.Canvas(it))
    }
}.getOrNull()

/** Crop/zoom/reposition/rotate editor with live fade preview for one widget's wallpaper. */
@Composable
internal fun WidgetBackgroundEditor(target: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isTodo = target == WidgetBackgrounds.TODO
    val aspect = if (isTodo) 5f / 3f else 6f / 5f
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var fade by remember { mutableFloatStateOf(WidgetBackgrounds.fade(context, target).toFloat()) }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val hasWallpaper = WidgetBackgrounds.has(context, target)

    fun load(next: Bitmap?, failure: String) {
        if (next == null) { message = failure; return }
        bitmap = next; zoom = 1f; offset = Offset.Zero; message = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { load(withContext(Dispatchers.IO) { decodeUri(context, uri) }, "That image could not be opened.") }
    }
    fun refreshWidget() { if (isTodo) TodoWidget.updateAll(context) else CalendarWidget.updateAll(context) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (isTodo) "To-Do widget wallpaper" else "Calendar widget wallpaper", color = EditorInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Pinch to zoom, drag to reposition", color = EditorMuted, fontSize = 13.sp)
            }
            TextButton(onClick = onClose) { Text("Cancel", color = EditorAccent) }
        }
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(16.dp)).background(Color.Black)
                .onSizeChanged { frame = it }
                .semantics { contentDescription = "Wallpaper preview. Pinch to zoom and drag to reposition." }
                .pointerInput(bitmap) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        val image = bitmap ?: return@detectTransformGestures
                        zoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                        offset = clampOffset(image, frame, zoom, offset + pan)
                    }
                }
        ) {
            bitmap?.let { image -> drawIntoCanvas { drawTransformed(it.nativeCanvas, image, size.width, size.height, zoom, offset) } }
            drawRect(Color.Black.copy(alpha = fade / 100f))
        }
        if (bitmap == null) Text("Choose an image below to start.", color = EditorMuted, fontSize = 13.sp)
        message?.let { Text(it, color = EditorAccent, fontSize = 13.sp) }

        Text("Zoom", color = EditorInk, fontSize = 14.sp)
        Slider(
            value = zoom, onValueChange = { value ->
                zoom = value
                bitmap?.let { offset = clampOffset(it, frame, zoom, offset) }
            }, valueRange = 1f..MAX_ZOOM, enabled = bitmap != null,
            colors = SliderDefaults.colors(thumbColor = EditorAccent, activeTrackColor = EditorAccent)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = bitmap != null, onClick = { bitmap = bitmap?.let { rotate(it, 90f) }; zoom = 1f; offset = Offset.Zero }) { Text("Rotate", color = EditorInk) }
            OutlinedButton(enabled = bitmap != null, onClick = { zoom = 1f; offset = Offset.Zero }) { Text("Fit", color = EditorInk) }
        }

        Text("Image source", color = EditorInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { gallery.launch("image/*") }) { Text("Gallery", color = EditorInk) }
            OutlinedButton(onClick = {
                scope.launch {
                    load(withContext(Dispatchers.IO) { deviceWallpaper(context) }, "Android did not allow Clarity to read the device wallpaper. Choose it from the gallery instead.")
                }
            }) { Text("Device wallpaper", color = EditorInk) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetBackgrounds.bundledNames.forEachIndexed { index, name ->
                OptionChip(name, false) { load(WidgetBackgrounds.bundled(index), "") }
            }
        }

        Text("Background intensity", color = EditorInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val current = fade.toInt()
            WidgetBackgrounds.presets.forEach { (label, value) -> OptionChip(label, current == value) { fade = value.toFloat() } }
            OptionChip("Custom", WidgetBackgrounds.presets.none { it.second == current }) { }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Original", color = EditorMuted, fontSize = 12.sp)
            Slider(
                value = fade, onValueChange = { fade = it }, valueRange = 0f..100f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(thumbColor = EditorAccent, activeTrackColor = EditorAccent)
            )
            Text("${fade.toInt()}%", color = EditorInk, fontSize = 12.sp)
        }

        Button(
            enabled = bitmap != null && !saving && frame.width > 0,
            colors = ButtonDefaults.buttonColors(containerColor = EditorAccent, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val image = bitmap ?: return@Button
                saving = true
                scope.launch {
                    val saved = withContext(Dispatchers.Default) {
                        val outHeight = (OUTPUT_WIDTH / aspect).toInt()
                        val output = Bitmap.createBitmap(OUTPUT_WIDTH, outHeight, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(output).apply { drawColor(android.graphics.Color.BLACK) }
                        val ratio = OUTPUT_WIDTH.toFloat() / frame.width
                        drawTransformed(canvas, image, OUTPUT_WIDTH.toFloat(), outHeight.toFloat(), zoom, Offset(offset.x * ratio, offset.y * ratio))
                        WidgetBackgrounds.save(context, target, output, fade.toInt()).also { output.recycle() }
                    }
                    saving = false
                    if (saved) { refreshWidget(); onClose() } else message = "Could not save the wallpaper."
                }
            }
        ) { Text("Save wallpaper") }
        if (hasWallpaper) TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
            WidgetBackgrounds.clear(context, target); refreshWidget(); onClose()
        }) { Text("Remove wallpaper", color = EditorAccent) }
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(50),
        color = if (selected) EditorAccent.copy(alpha = .2f) else EditorSurface,
        border = BorderStroke(1.dp, if (selected) EditorAccent else Color(0xFF33333A))
    ) { Text(label, color = EditorInk, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
}
