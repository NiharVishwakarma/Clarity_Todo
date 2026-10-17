package com.example.darktodo.ui

import com.example.darktodo.UiTokens
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.darktodo.navigation.ModuleNavigation
import com.example.darktodo.navigation.ModuleSpec

/** Single icon language: Material Symbols-style Outlined glyphs, one color, 20-24dp. */
object ClarityIcons {
    val Tasks = Icons.Outlined.TaskAlt
    val History = Icons.Outlined.History
    val Calendar = Icons.Outlined.CalendarMonth
    val Vault = Icons.Outlined.Lock
    val Quotes = Icons.Outlined.FormatQuote
    val Stickers = Icons.Outlined.StickyNote2
    val Wallpapers = Icons.Outlined.Wallpaper
    val Settings = Icons.Outlined.Settings
    val Backup = Icons.Outlined.CloudSync
    val Security = Icons.Outlined.Shield
    val Support = Icons.Outlined.FavoriteBorder
    val Personalization = Icons.Outlined.Palette
    val About = Icons.Outlined.Info

    fun forModule(id: String): ImageVector = when (id) {
        ModuleNavigation.TASKS -> Tasks
        ModuleNavigation.HISTORY -> History
        ModuleNavigation.CALENDAR -> Calendar
        ModuleNavigation.VAULT -> Vault
        ModuleNavigation.QUOTES -> Quotes
        ModuleNavigation.STICKERS -> Stickers
        ModuleNavigation.WALLPAPERS -> Wallpapers
        ModuleNavigation.SETTINGS -> Settings
        else -> Tasks
    }
}

@Composable fun ClarityGlyph(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = UiTokens.Ink, size: androidx.compose.ui.unit.Dp = 22.dp) {
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** Floating, horizontally scrollable tab bar. LazyRow keeps rendering cheap at 20+ modules. */
@Composable fun ModuleTabBar(modules: List<ModuleSpec>, selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedId) {
        val index = modules.indexOfFirst { it.id == selectedId }
        if (index >= 0) listState.animateScrollToItem((index - 1).coerceAtLeast(0))
    }
    Surface(color = UiTokens.Surface, shape = RoundedCornerShape(UiTokens.CardRadius), shadowElevation = 6.dp, tonalElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        LazyRow(state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(modules, key = { it.id }) { module ->
                val selected = module.id == selectedId
                val container by animateColorAsState(if (selected) UiTokens.Accent.copy(alpha = .18f) else Color.Transparent, label = "tabContainer")
                val content by animateColorAsState(if (selected) UiTokens.Accent else UiTokens.Muted, label = "tabContent")
                Surface(onClick = { onSelect(module.id) }, color = container, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = "${module.label} tab" }) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ClarityGlyph(ClarityIcons.forModule(module.id), tint = content, size = 20.dp)
                        Text(module.label, color = content, fontSize = 13.sp, maxLines = 1, softWrap = false, fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable fun ScreenHeader(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ClarityGlyph(icon, size = 24.dp)
        Column {
            Text(title, color = UiTokens.Ink, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(subtitle, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
        }
    }
}

@Composable fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        ClarityGlyph(icon, tint = UiTokens.Muted, size = 24.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, color = UiTokens.Ink, fontSize = UiTokens.SectionTitleSp.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = UiTokens.Muted, fontSize = UiTokens.LabelSp.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable fun SettingsCategoryHeader(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ClarityGlyph(icon, size = 20.dp)
        Text(title, color = UiTokens.Ink, fontSize = UiTokens.SectionTitleSp.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}

@Composable fun SettingsNavRow(icon: ImageVector, title: String, subtitle: String, status: String? = null, alert: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, color = UiTokens.SurfaceRaised, shape = RoundedCornerShape(UiTokens.CardRadius), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = UiTokens.ControlHeight).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ClarityGlyph(icon, size = 22.dp)
            Column(Modifier.weight(1f)) {
                Text(title, color = UiTokens.Ink, fontSize = UiTokens.LabelSp.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                if (subtitle.isNotBlank()) Text(subtitle, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
            }
            if (status != null) Text(status, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
            if (alert) Box(Modifier.size(8.dp).background(UiTokens.Accent, CircleShape).semantics { contentDescription = "Attention needed" })
            Text("›", color = UiTokens.Muted, fontSize = 22.sp)
        }
    }
}

@Composable fun OutlineChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = if (selected) UiTokens.Accent.copy(alpha = .18f) else UiTokens.SurfaceRaised,
        shape = RoundedCornerShape(UiTokens.CardRadius),
        border = BorderStroke(1.dp, if (selected) UiTokens.Accent else Color.Transparent),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(Modifier.heightIn(min = UiTokens.ControlHeight).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) UiTokens.Accent else UiTokens.Ink, fontSize = UiTokens.SectionTitleSp.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
    }
}
