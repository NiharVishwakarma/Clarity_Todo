package com.example.darktodo.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.darktodo.R

/** Bundled font families shared by the app UI and both widgets. */
val NunitoFamily = FontFamily(
    Font(R.font.nunito_variable, FontWeight.Normal),
    Font(R.font.nunito_italic_variable, FontWeight.Normal, FontStyle.Italic)
)

private val appFontFamilies = mapOf(
    "opensans" to FontFamily(Font(R.font.opensans_regular), Font(R.font.opensans_italic, FontWeight.Normal, FontStyle.Italic)),
    "googlesans" to FontFamily(Font(R.font.googlesans_regular), Font(R.font.googlesans_italic, FontWeight.Normal, FontStyle.Italic)),
    "montserrat" to FontFamily(Font(R.font.montserrat_regular), Font(R.font.montserrat_italic, FontWeight.Normal, FontStyle.Italic)),
    "notosans" to FontFamily(Font(R.font.notosans_regular), Font(R.font.notosans_italic, FontWeight.Normal, FontStyle.Italic)),
    "ubuntu" to FontFamily(Font(R.font.ubuntu_regular), Font(R.font.ubuntu_italic, FontWeight.Normal, FontStyle.Italic)),
    "nunito" to NunitoFamily
)

fun appFontFamily(id: String): FontFamily = appFontFamilies[id] ?: NunitoFamily

val appFontOptions = listOf(
    "opensans" to "Open Sans", "googlesans" to "Google Sans", "montserrat" to "Montserrat",
    "notosans" to "Noto Sans", "ubuntu" to "Ubuntu", "nunito" to "Nunito"
)
