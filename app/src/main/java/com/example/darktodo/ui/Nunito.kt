package com.example.darktodo.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.darktodo.R
import com.example.darktodo.data.WidgetFontFamily

/** Bundled font families shared by the app UI and both widgets. */
val NunitoFamily = FontFamily(
    Font(R.font.nunito_variable, FontWeight.Normal),
    Font(R.font.nunito_italic_variable, FontWeight.Normal, FontStyle.Italic)
)

/** Default app typeface. The bundled Google Sans files are licensed under OFL-1.1. */
val GoogleSansFamily = FontFamily(
    Font(R.font.googlesans_regular, FontWeight.Normal),
    Font(R.font.googlesans_italic, FontWeight.Normal, FontStyle.Italic)
)

private val appFontFamilies = mapOf(
    WidgetFontFamily.OPEN_SANS to FontFamily(Font(R.font.opensans_regular), Font(R.font.opensans_italic, FontWeight.Normal, FontStyle.Italic)),
    WidgetFontFamily.GOOGLE_SANS to GoogleSansFamily,
    WidgetFontFamily.MONTSERRAT to FontFamily(Font(R.font.montserrat_regular), Font(R.font.montserrat_italic, FontWeight.Normal, FontStyle.Italic)),
    WidgetFontFamily.NOTO_SANS to FontFamily(Font(R.font.notosans_regular), Font(R.font.notosans_italic, FontWeight.Normal, FontStyle.Italic)),
    WidgetFontFamily.UBUNTU to FontFamily(Font(R.font.ubuntu_regular), Font(R.font.ubuntu_italic, FontWeight.Normal, FontStyle.Italic)),
    WidgetFontFamily.NUNITO to NunitoFamily
)

fun appFontFamily(family: WidgetFontFamily): FontFamily =
    checkNotNull(appFontFamilies[family]) { "No Compose font registered for ${family.key}" }
