package com.example.darktodo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.darktodo.UiTokens

@Composable fun ComingSoonScreen(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(icon, title, "Coming soon")
        EmptyState(icon, "$title is on the way", message)
    }
}

@Composable fun SupportClarityScreen(onBack: () -> Unit) {
    val amounts = listOf("\u20B950", "\u20B9100", "\u20B9250", "\u20B9500")
    var selected by remember { mutableStateOf<String?>(null) }
    var custom by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.clickable(onClick = onBack).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClarityGlyph(Icons.AutoMirrored.Outlined.ArrowBack, size = 22.dp)
            Text("Settings", color = UiTokens.Muted, fontSize = UiTokens.LabelSp.sp)
        }
        ScreenHeader(ClarityIcons.Support, "Support Clarity", "Optional and never required", Modifier.padding(bottom = 0.dp))
        Surface(color = UiTokens.Surface, shape = RoundedCornerShape(UiTokens.CardRadius)) {
            Text(
                "Supporting Clarity is completely optional and helps fund future updates.\nNo features, rewards, or advantages are unlocked through donations.",
                color = UiTokens.Muted, fontSize = UiTokens.LabelSp.sp, modifier = Modifier.padding(16.dp)
            )
        }
        amounts.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { amount -> OutlineChip(amount, selected == amount, { selected = amount; custom = "" }, Modifier.weight(1f)) }
            }
        }
        OutlinedTextField(
            custom, { value -> custom = value.filter { it.isDigit() }.take(6); if (custom.isNotEmpty()) selected = null },
            label = { Text("Custom Amount (\u20B9)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = UiTokens.Ink, unfocusedTextColor = UiTokens.Ink,
                focusedBorderColor = UiTokens.Accent, unfocusedBorderColor = UiTokens.Subdued,
                focusedLabelColor = UiTokens.Accent, unfocusedLabelColor = UiTokens.Muted, cursorColor = UiTokens.Accent
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Text("Payments are not available yet. This screen is a preview of the layout.", color = UiTokens.Subdued, fontSize = UiTokens.DetailSp.sp)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable fun AboutScreen(onBack: () -> Unit, versionName: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.clickable(onClick = onBack).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClarityGlyph(Icons.AutoMirrored.Outlined.ArrowBack, size = 22.dp)
            Text("Settings", color = UiTokens.Muted, fontSize = UiTokens.LabelSp.sp)
        }
        ScreenHeader(ClarityIcons.About, "About", "Clarity")
        Surface(color = UiTokens.Surface, shape = RoundedCornerShape(UiTokens.CardRadius)) {
            Text("Version $versionName", color = UiTokens.Ink, fontSize = UiTokens.LabelSp.sp, modifier = Modifier.fillMaxWidth().padding(16.dp))
        }
    }
}
