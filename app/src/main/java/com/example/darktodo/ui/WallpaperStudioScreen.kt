package com.example.darktodo.ui

import android.app.WallpaperManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.darktodo.UiTokens
import com.example.darktodo.data.WallpaperConfig
import com.example.darktodo.data.WallpaperEngine
import com.example.darktodo.data.WallpaperPalette
import com.example.darktodo.data.WallpaperPalettes
import com.example.darktodo.data.SavedWallpaper
import com.example.darktodo.data.WallpaperStudioStore
import com.example.darktodo.data.WallpaperStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Random

private val StudioInk = Color(0xFFF4F3F5)
private val StudioMuted = Color(0xFFAAA8B0)

@Composable
fun WallpaperStudioScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(WallpaperStudioStore.loadConfig(context)) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var colorPickerIndex by remember { mutableStateOf<Int?>(null) }
    var showPalettes by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var previewLoading by remember { mutableStateOf(true) }
    var selectedTarget by remember { mutableStateOf(WallpaperStudioStore.target(context)) }
    var selectedSaved by remember { mutableStateOf<SavedWallpaper?>(null) }
    var saved by remember { mutableStateOf(WallpaperStudioStore.list(context)) }
    val latestPreview by rememberUpdatedState(preview)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val exportConfig = config
        val savedId = selectedSaved?.id
        if (uri != null) {
            scope.launch {
                busy = true
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            if (savedId != null) {
                                WallpaperStudioStore.copySavedTo(context, savedId, stream)
                            } else {
                                val metrics = context.resources.displayMetrics
                                val bitmap = WallpaperEngine.render(exportConfig, metrics.widthPixels, metrics.heightPixels)
                                try {
                                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Could not export wallpaper." }
                                } finally { bitmap.recycle() }
                            }
                        } ?: error("Could not open the selected destination.")
                    }
                }
                busy = false
                message = result.fold({ "Wallpaper exported." }, { it.message ?: "Could not export wallpaper." })
            }
        }
    }

    LaunchedEffect(config, selectedSaved?.id) {
        WallpaperStudioStore.saveConfig(context, config)
        previewLoading = true
        val selected = selectedSaved
        val result = withContext(Dispatchers.Default) {
            runCatching {
                if (selected == null) WallpaperEngine.render(config, 360, 720)
                else WallpaperStudioStore.preview(context, selected.id, 360, 720)
                    ?: error("The saved wallpaper could not be decoded.")
            }
        }
        result.fold(
            onSuccess = { preview = it },
            onFailure = { error ->
                android.util.Log.e("WallpaperStudio", "Could not render wallpaper preview", error)
                message = error.message ?: "Could not render wallpaper preview."
            }
        )
        previewLoading = false
    }
    DisposableEffect(Unit) {
        onDispose { latestPreview?.recycle() }
    }
    fun change(next: WallpaperConfig) {
        config = next.sanitized()
    }
    fun createVariation(preserveDesign: Boolean) {
        val random = Random()
        val next = if (preserveDesign) config.copy(
            seed = random.nextLong(),
            complexity = random.nextInt(3),
            texture = (config.texture + (random.nextFloat() - .5f) * .15f).coerceIn(0f, 1f)
        ) else config.copy(seed = random.nextLong())
        change(next)
    }
    fun applyWallpaper(target: Int) {
        val current = config
        val savedId = selectedSaved?.id
        scope.launch {
            busy = true
            message = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val metrics = context.resources.displayMetrics
                    val bitmap = if (savedId != null) {
                        WallpaperStudioStore.image(context, savedId) ?: error("The saved wallpaper is no longer available.")
                    } else WallpaperEngine.render(current, metrics.widthPixels.coerceAtLeast(1), metrics.heightPixels.coerceAtLeast(1))
                    try {
                        val manager = WallpaperManager.getInstance(context)
                        val which = when (target) {
                            WallpaperStudioStore.TARGET_LOCK -> WallpaperManager.FLAG_LOCK
                            WallpaperStudioStore.TARGET_BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                            else -> WallpaperManager.FLAG_SYSTEM
                        }
                        manager.setBitmap(bitmap, null, true, which)
                    } finally { bitmap.recycle() }
                    Unit
                }
            }
            busy = false
            message = result.fold({ "Wallpaper applied." }, { it.message ?: "Could not apply wallpaper." })
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Wallpaper Studio", color = StudioInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Make your screen yours. Generated on your device.", color = StudioMuted, fontSize = 12.sp)
        }
        preview?.let { image ->
            Box(contentAlignment = Alignment.Center) {
                Image(
                    image.asImageBitmap(),
                    contentDescription = "Wallpaper preview, ${config.style.label} style",
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.57f).clip(RoundedCornerShape(18.dp))
                )
                if (previewLoading) CircularProgressIndicator(color = UiTokens.Accent)
            }
        } ?: Box(Modifier.fillMaxWidth().aspectRatio(.57f).background(UiTokens.Surface, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            if (previewLoading) CircularProgressIndicator(color = UiTokens.Accent)
        }
        if (busy) Text("Rendering wallpaper…", color = StudioMuted, fontSize = 12.sp)

        Column(Modifier.fillMaxWidth().background(UiTokens.Surface, RoundedCornerShape(UiTokens.CardRadius)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your colors", color = StudioInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${config.colors.size} / 3 colors", color = StudioMuted, fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                config.colors.forEachIndexed { index, argb ->
                    Box(
                        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color(argb)).border(1.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(10.dp))
                            .clickable { colorPickerIndex = index }
                    )
                }
                if (config.colors.size < 3) {
                    OutlinedButton(onClick = { colorPickerIndex = config.colors.size }, modifier = Modifier.weight(1f).height(42.dp)) { Text("+", color = StudioInk) }
                }
            }
            if (config.colors.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    config.colors.indices.forEach { index ->
                        TextButton(onClick = { change(config.copy(colors = config.colors.filterIndexed { colorIndex, _ -> colorIndex != index })) }) {
                            Text("Remove ${index + 1}", color = StudioMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showPalettes = true }) { Text("Presets", color = StudioMuted) }
                TextButton(onClick = {
                    val chosen = WallpaperPalettes.all.random()
                    change(config.copy(colors = chosen.colors))
                }) { Text("Random colors", color = StudioMuted) }
                TextButton(onClick = { change(WallpaperConfig(seed = Random().nextLong())) }) { Text("Restore defaults", color = StudioMuted) }
            }
            Text("Tap a color to edit it, or use Remove to delete a swatch.", color = StudioMuted, fontSize = 11.sp)
        }

        Column(Modifier.fillMaxWidth().background(UiTokens.Surface, RoundedCornerShape(UiTokens.CardRadius)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Choose a style", color = StudioInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WallpaperStyle.entries.forEach { style ->
                    Text(
                        style.label,
                        color = if (config.style == style) StudioInk else StudioMuted,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (config.style == style) UiTokens.Accent else UiTokens.SurfaceRaised)
                            .clickable { change(config.copy(style = style)) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        fontSize = 12.sp
                    )
                }
            }
            Text("Complexity", color = StudioInk, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Minimal", "Balanced", "Intricate").forEachIndexed { index, label ->
                    Text(label, color = if (config.complexity == index) StudioInk else StudioMuted,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (config.complexity == index) UiTokens.SurfaceRaised else Color.Transparent)
                            .clickable { change(config.copy(complexity = index)) }.padding(vertical = 9.dp),
                        fontSize = 11.sp)
                }
            }
            SettingSlider("Brightness", config.brightness, .35f..1.5f) { change(config.copy(brightness = it)) }
            if (config.style != WallpaperStyle.SOLID && config.style != WallpaperStyle.PATTERN) {
                SettingSlider("Texture", config.texture, 0f..1f) { change(config.copy(texture = it)) }
            }
            if (config.style == WallpaperStyle.PATTERN || config.style == WallpaperStyle.CONCENTRIC) {
                SettingSlider("Pattern scale", config.patternScale, .1f..1f) { change(config.copy(patternScale = it)) }
            }
            if (config.style == WallpaperStyle.LINEAR) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("↘", "↙", "→", "↑").forEachIndexed { index, direction ->
                        Text(direction, color = StudioInk, modifier = Modifier.clip(CircleShape)
                            .background(if (config.gradientDirection == index) UiTokens.Accent else UiTokens.SurfaceRaised)
                            .clickable { change(config.copy(gradientDirection = index)) }.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = { createVariation(false) }, modifier = Modifier.weight(1f)) { Text("Shuffle") }
            OutlinedButton(enabled = !busy, onClick = { createVariation(true) }, modifier = Modifier.weight(1f)) { Text("Remix") }
        }
        Button(
            enabled = !busy,
            onClick = { change(config.copy(seed = Random().nextLong())) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = UiTokens.Accent),
            shape = RoundedCornerShape(12.dp)
        ) { Text(if (busy) "Working…" else "✦  Generate wallpaper", color = Color.White, fontWeight = FontWeight.SemiBold) }

        Column(Modifier.fillMaxWidth().background(UiTokens.Surface, RoundedCornerShape(UiTokens.CardRadius)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Apply to", color = StudioInk, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Home" to WallpaperStudioStore.TARGET_HOME, "Lock" to WallpaperStudioStore.TARGET_LOCK, "Both" to WallpaperStudioStore.TARGET_BOTH).forEach { (label, target) ->
                    OutlinedButton(
                        onClick = { selectedTarget = target; WallpaperStudioStore.saveTarget(context, target); applyWallpaper(target) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedTarget == target) UiTokens.SurfaceRaised else Color.Transparent)
                    ) { Text(label) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (!busy) scope.launch {
                        busy = true
                        val saveConfig = config
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val metrics = context.resources.displayMetrics
                                val bitmap = WallpaperEngine.render(saveConfig, metrics.widthPixels, metrics.heightPixels)
                                try { WallpaperStudioStore.save(context, bitmap, saveConfig) }
                                finally { bitmap.recycle() }
                            }
                        }
                        busy = false
                        result.fold({ saved = WallpaperStudioStore.list(context); message = "Saved to My Wallpapers." }, { message = it.message ?: "Could not save wallpaper." })
                    }
                }, enabled = !busy && selectedSaved == null, modifier = Modifier.weight(1f)) {
                    Text(if (selectedSaved == null) "Save" else "Saved")
                }
                OutlinedButton(onClick = { exportLauncher.launch("clarity-${config.style.name.lowercase()}-${config.seed}.png") }, enabled = !busy && preview != null, modifier = Modifier.weight(1f)) { Text("Export") }
            }
            TextButton(onClick = { change(WallpaperConfig(seed = Random().nextLong())) }, enabled = !busy) { Text("Reset settings", color = StudioMuted) }
            message?.let { Text(it, color = if (it.contains("Could not")) UiTokens.Accent else StudioMuted, fontSize = 12.sp) }
        }
        if (saved.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(UiTokens.Surface, RoundedCornerShape(UiTokens.CardRadius)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("My Wallpapers", color = StudioInk, fontWeight = FontWeight.SemiBold)
                saved.forEach { entry ->
                    Row(Modifier.fillMaxWidth().clickable {
                        selectedSaved = entry
                        config = entry.config
                    }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${entry.style.label} · ${entry.seed}", color = StudioInk, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        TextButton(onClick = {
                            try {
                                WallpaperStudioStore.delete(context, entry.id)
                                if (selectedSaved?.id == entry.id) selectedSaved = null
                                saved = WallpaperStudioStore.list(context)
                            } catch (error: Exception) {
                                android.util.Log.e("WallpaperStudio", "Could not delete saved wallpaper ${entry.id}", error)
                                message = error.message ?: "Could not remove saved wallpaper."
                            }
                        }) { Text("Remove", color = StudioMuted) }
                    }
                }
            }
        }
    }

    colorPickerIndex?.let { index ->
        val colorValue = config.colors.getOrNull(index) ?: android.graphics.Color.WHITE
        ColorPickerDialog(
            label = "wallpaper color",
            currentColor = "#%06X".format(colorValue and 0xFFFFFF),
            onApply = { value ->
                val parsed = runCatching { android.graphics.Color.parseColor(value) }.getOrElse { colorValue }
                val colors = config.colors.toMutableList()
                if (index == colors.size && colors.size < 3) colors.add(parsed) else if (index in colors.indices) colors[index] = parsed
                change(config.copy(colors = colors))
                colorPickerIndex = null
            },
            onDismiss = { colorPickerIndex = null }
        )
    }
    if (showPalettes) AlertDialog(
        onDismissRequest = { showPalettes = false }, containerColor = UiTokens.Surface,
        title = { Text("Color palettes", color = StudioInk) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WallpaperPalettes.all.forEach { palette -> PaletteRow(palette) {
                    change(config.copy(colors = palette.colors))
                    showPalettes = false
                } }
            }
        },
        confirmButton = { TextButton(onClick = { showPalettes = false }) { Text("Close", color = UiTokens.Accent) } }
    )
}

@Composable
private fun PaletteRow(palette: WallpaperPalette, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(palette.name, color = StudioInk, modifier = Modifier.weight(1f), fontSize = 13.sp)
        palette.colors.forEach { color -> Box(Modifier.padding(start = 4.dp).size(20.dp).clip(CircleShape).background(Color(color))) }
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row { Text(label, color = StudioInk, modifier = Modifier.weight(1f), fontSize = 13.sp); Text("%d%%".format((value * 100).toInt()), color = StudioMuted, fontSize = 11.sp) }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
