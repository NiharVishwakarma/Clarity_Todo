package com.example.darktodo.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.darktodo.R

/** Bundled variable Nunito font; all configured weights work offline. */
@OptIn(ExperimentalTextApi::class)
val NunitoFamily = FontFamily(
    Font(R.font.nunito_variable, FontWeight.W300, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.nunito_variable, FontWeight.W400, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.nunito_variable, FontWeight.W500, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.nunito_variable, FontWeight.W600, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.nunito_variable, FontWeight.W700, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.nunito_variable, FontWeight.W800, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    Font(R.font.nunito_variable, FontWeight.W900, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
    Font(R.font.nunito_italic_variable, FontWeight.W300, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.nunito_italic_variable, FontWeight.W400, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.nunito_italic_variable, FontWeight.W500, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.nunito_italic_variable, FontWeight.W600, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.nunito_italic_variable, FontWeight.W700, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.nunito_italic_variable, FontWeight.W800, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    Font(R.font.nunito_italic_variable, FontWeight.W900, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(900)))
)

fun composeWeight(value: Int): FontWeight = when (value) {
    300 -> FontWeight.Light
    500 -> FontWeight.Medium
    600 -> FontWeight.SemiBold
    700 -> FontWeight.Bold
    800 -> FontWeight.ExtraBold
    900 -> FontWeight.Black
    else -> FontWeight.Normal
}
