package com.example.darktodo.ui

import com.example.darktodo.UiTokens
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    val Info = Icons.Outlined.Info
    val Stickers = Icons.Outlined.StickyNote2
    val Wallpapers = Icons.Outlined.Wallpaper
    val Settings = Icons.Outlined.Settings
    val Backup = Icons.Outlined.CloudSync
    val Security = Icons.Outlined.Shield
    val Support = Icons.Outlined.FavoriteBorder
    val Personalization = Icons.Outlined.Palette
    val About = Icons.Outlined.Info
    val Fonts = Icons.Outlined.TextFields
    val Colors = Icons.Outlined.Palette
    val Background = Icons.Outlined.Wallpaper
    val Layout = Icons.Outlined.Tune

    fun forModule(id: String): ImageVector = when (id) {
        ModuleNavigation.TASKS -> Tasks
        ModuleNavigation.CALENDAR -> Calendar
        ModuleNavigation.VAULT -> Vault
        ModuleNavigation.QUOTES -> Quotes
        ModuleNavigation.INFO -> Info
        ModuleNavigation.STICKERS -> Stickers
        ModuleNavigation.WALLPAPERS -> Wallpapers
        ModuleNavigation.SETTINGS -> Settings
        else -> Tasks
    }
}

/** Expansion is transient UI state, never restored from a previous launch. */
@Composable
fun rememberSettingsSectionExpanded(key: Any? = null): MutableState<Boolean> =
    remember(key) { mutableStateOf(false) }

@Composable
fun CollapsibleSettingsSection(
    title: String,
    summary: String? = null,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val focusManager = LocalFocusManager.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180),
        label = "settingsSectionChevron"
    )
    Surface(
        color = UiTokens.SurfaceRaised,
        shape = RoundedCornerShape(UiTokens.CardRadius),
        border = BorderStroke(1.dp, UiTokens.Subdued.copy(alpha = .65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.animateContentSize(animationSpec = tween(200))) {
            Surface(
                onClick = {
                    if (expanded) focusManager.clearFocus()
                    onExpandedChange(!expanded)
                },
                color = Color.Transparent,
                shape = RoundedCornerShape(UiTokens.CardRadius),
                modifier = Modifier.fillMaxWidth().semantics {
                    role = Role.Button
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (icon != null) ClarityGlyph(icon, tint = UiTokens.Accent)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, color = UiTokens.Ink, fontSize = UiTokens.LabelSp.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                        summary?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
                        }
                    }
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = UiTokens.Muted,
                        modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = chevronRotation }
                    )
                }
            }
            Box(
                Modifier.fillMaxWidth()
                    .then(if (expanded) Modifier.wrapContentHeight() else Modifier.height(0.dp))
                    .clipToBounds()
                    .then(if (expanded) Modifier else Modifier.clearAndSetSemantics {})
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
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

/** The one gear used to open settings from every module, so size, spacing and alignment never drift. */
@Composable fun SettingsGearButton(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp).semantics { contentDescription = label }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ClarityGlyph(Icons.Outlined.Settings, tint = UiTokens.Accent, size = 24.dp) }
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
            Text(
                label,
                color = if (selected) UiTokens.Accent else UiTokens.Ink,
                fontSize = UiTokens.SectionTitleSp.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
