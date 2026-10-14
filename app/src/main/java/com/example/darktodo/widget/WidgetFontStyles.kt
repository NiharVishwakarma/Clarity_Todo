package com.example.darktodo.widget

import com.example.darktodo.R

/** TextAppearance resource ids used by RemoteViews, whose text views cannot accept Typeface objects. */
internal object WidgetFontStyles {
    fun resolve(family: String, italic: Boolean): Int = when (family) {
        "opensans" -> if (italic) R.style.FontOpenSansItalic else R.style.FontOpenSansRegular
        "googlesans" -> if (italic) R.style.FontGoogleSansItalic else R.style.FontGoogleSansRegular
        "montserrat" -> if (italic) R.style.FontMontserratItalic else R.style.FontMontserratRegular
        "notosans" -> if (italic) R.style.FontNotoSansItalic else R.style.FontNotoSansRegular
        "ubuntu" -> if (italic) R.style.FontUbuntuItalic else R.style.FontUbuntuRegular
        else -> if (italic) R.style.FontNunitoItalic else R.style.FontNunitoRegular
    }
}
