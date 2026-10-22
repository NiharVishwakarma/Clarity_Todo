package com.example.darktodo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.darktodo.UiTokens
import com.example.darktodo.data.ClarityQuote
import com.example.darktodo.data.QuoteLibrary

@Composable
fun QuotesSection() {
    val context = LocalContext.current
    val quotes = remember { QuoteLibrary.load(context) }
    var expanded by remember { mutableStateOf(QuoteLibrary.isExpanded(context)) }
    var selectedTopics by remember { mutableStateOf(QuoteLibrary.selectedTopics(context)) }
    var customTopics by remember { mutableStateOf(QuoteLibrary.customTopics(context)) }
    var recentIds by remember { mutableStateOf(QuoteLibrary.recentIds(context)) }
    var favorites by remember { mutableStateOf(QuoteLibrary.favoriteIds(context)) }
    var quote by remember { mutableStateOf<ClarityQuote?>(null) }
    var customTopicText by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf<QuoteListDialog?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun refresh(topics: Set<String> = selectedTopics) {
        val next = QuoteLibrary.choose(quotes, topics, recentIds)
        quote = next
        notice = null
        if (next != null) recentIds = QuoteLibrary.recordViewed(context, next)
    }

    fun selectTopic(id: String) {
        selectedTopics = QuoteLibrary.toggleTopic(selectedTopics, id)
        QuoteLibrary.saveSelectedTopics(context, selectedTopics)
        if (expanded) refresh()
    }

    LaunchedEffect(expanded) {
        if (expanded && quote == null) refresh()
    }

    CollapsibleSettingsSection(
        title = "Quotes",
        summary = "A small, offline collection",
        expanded = expanded,
        onExpandedChange = { next ->
            expanded = next
            QuoteLibrary.setExpanded(context, next)
        },
        icon = ClarityIcons.Quotes
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = QuoteLibrary.ALL_TOPICS in selectedTopics,
                    onClick = { selectTopic(QuoteLibrary.ALL_TOPICS) },
                    label = { Text("All Topics") }
                )
            }
            items(QuoteLibrary.topics, key = { it.id }) { topic ->
                FilterChip(
                    selected = topic.id in selectedTopics,
                    onClick = { selectTopic(topic.id) },
                    label = { Text(topic.label, maxLines = 1) }
                )
            }
            items(customTopics, key = { it.lowercase() }) { topic ->
                val id = QuoteLibrary.CUSTOM_PREFIX + topic
                FilterChip(
                    selected = id in selectedTopics,
                    onClick = { selectTopic(id) },
                    label = { Text(topic, maxLines = 1) }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = customTopicText,
                onValueChange = { customTopicText = it.take(40) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Custom topic") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = UiTokens.Ink,
                    unfocusedTextColor = UiTokens.Ink,
                    focusedBorderColor = UiTokens.Accent,
                    unfocusedBorderColor = UiTokens.Subdued,
                    focusedLabelColor = UiTokens.Accent,
                    unfocusedLabelColor = UiTokens.Muted,
                    cursorColor = UiTokens.Accent
                )
            )
            TextButton(
                onClick = {
                    customTopics = QuoteLibrary.addCustomTopic(context, customTopicText)
                    val added = customTopics.firstOrNull { it.equals(customTopicText.trim(), ignoreCase = true) }
                    if (added != null) selectTopic(QuoteLibrary.CUSTOM_PREFIX + added)
                    customTopicText = ""
                },
                enabled = customTopicText.isNotBlank()
            ) { Text("Add", color = UiTokens.Accent) }
        }
        quote?.let { current ->
            QuoteArtwork(current)
            Text(
                current.source,
                color = UiTokens.Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp).clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current.sourceUrl)))
                }
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    favorites = QuoteLibrary.toggleFavorite(context, current.id)
                    notice = if (current.id in favorites) "Added to Favorites" else "Removed from Favorites"
                }) {
                    Icon(
                        if (current.id in favorites) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (current.id in favorites) "Remove from Favorites" else "Add to Favorites",
                        tint = if (current.id in favorites) UiTokens.Accent else UiTokens.Muted
                    )
                }
                IconButton(onClick = {
                    val clipboard = requireNotNull(context.getSystemService(ClipboardManager::class.java)) {
                        "Clipboard service is unavailable"
                    }
                    clipboard.setPrimaryClip(ClipData.newPlainText("Quote", current.attributedText()))
                    notice = "Quote copied"
                }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy quote", tint = UiTokens.Muted)
                }
                IconButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${current.attributedText()}\n${current.source}")
                    }
                    context.startActivity(Intent.createChooser(share, "Share quote"))
                }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share quote", tint = UiTokens.Muted)
                }
                IconButton(onClick = { dialog = QuoteListDialog.RECENT }) {
                    Icon(Icons.Outlined.History, contentDescription = "Browse recent quotes", tint = UiTokens.Muted)
                }
                IconButton(onClick = { dialog = QuoteListDialog.FAVORITES }) {
                    Icon(Icons.Outlined.Favorite, contentDescription = "View Favorites", tint = UiTokens.Muted)
                }
            }
            notice?.let { Text(it, color = UiTokens.Muted, fontSize = 12.sp) }
        } ?: EmptyQuoteState()
        Button(
            onClick = { refresh() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = UiTokens.Accent, contentColor = Color.White)
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  Refresh Quote")
        }
    }

    dialog?.let { listDialog ->
        val ids = when (listDialog) {
            QuoteListDialog.RECENT -> recentIds
            QuoteListDialog.FAVORITES -> favorites.toList()
        }
        val entries = ids.mapNotNull { id -> quotes.firstOrNull { it.id == id } }
        AlertDialog(
            onDismissRequest = { dialog = null },
            containerColor = UiTokens.SurfaceRaised,
            title = { Text(if (listDialog == QuoteListDialog.RECENT) "Recent Quotes" else "Favorites", color = UiTokens.Ink) },
            text = {
                if (entries.isEmpty()) {
                    Text(
                        if (listDialog == QuoteListDialog.RECENT) "No viewed quotes yet." else "No favorite quotes saved yet.",
                        color = UiTokens.Muted
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(entries, key = { it.id }) { saved ->
                            TextButton(
                                onClick = {
                                    quote = saved
                                    recentIds = QuoteLibrary.recordViewed(context, saved)
                                    dialog = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text("\u201c${saved.text}\u201d", color = UiTokens.Ink)
                                    Text("\u2014 ${saved.author}", color = UiTokens.Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialog = null }) { Text("Done", color = UiTokens.Accent) }
            }
        )
    }
}

@Composable
private fun QuoteArtwork(quote: ClarityQuote) {
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp)) {
        Box(
            Modifier.matchParentSize()
                .padding(top = 7.dp)
                .clip(shape)
                .background(Color(0xFF090A12))
        )
        Box(
            Modifier.matchParentSize()
                .clip(shape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x553C68A3), Color.Transparent),
                        center = Offset.Zero,
                        radius = 520f
                    )
                )
        )
        Column(
            Modifier.fillMaxWidth()
                .shadow(14.dp, shape)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF20202D), Color(0xFF151521), Color(0xFF10151E))
                    )
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.linearGradient(listOf(Color(0xFFB36AF2), Color(0xFF405D9C), Color(0x66F20D24)))
                    ),
                    shape
                )
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "\u201c${quote.text}\u201d",
                color = Color(0xFFF7F4FC),
                fontSize = 23.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Medium
            )
            Text("\u2014 ${quote.author}", color = Color(0xFFD0C8E0), fontSize = 14.sp)
        }
    }
}

@Composable
private fun EmptyQuoteState() {
    Surface(
        color = UiTokens.Surface.copy(alpha = .82f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, UiTokens.Subdued.copy(alpha = .65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No local quotes for this topic yet.", color = UiTokens.Ink, fontWeight = FontWeight.Medium)
            Text("Choose another topic or All Topics to browse the bundled collection.", color = UiTokens.Muted)
        }
    }
}

private enum class QuoteListDialog { RECENT, FAVORITES }
