package com.example.darktodo.data

/** Canonical font family keys shared by preference storage, settings, and widget rendering. */
enum class WidgetFontFamily(val key: String, val label: String) {
    OPEN_SANS("opensans", "Open Sans"),
    GOOGLE_SANS("googlesans", "Google Sans"),
    MONTSERRAT("montserrat", "Montserrat"),
    NOTO_SANS("notosans", "Noto Sans"),
    UBUNTU("ubuntu", "Ubuntu"),
    NUNITO("nunito", "Nunito");

    companion object {
        fun fromKey(key: String?): WidgetFontFamily? = entries.firstOrNull { it.key == key }
    }
}

/** Canonical style values shared by both independent widget preference sets. */
enum class WidgetFontStyle(val key: String, val label: String) {
    REGULAR("regular", "Regular"),
    BOLD("bold", "Bold"),
    ITALIC("italic", "Italic");

    companion object {
        fun fromKey(key: String?): WidgetFontStyle? = entries.firstOrNull { it.key == key }
    }
}
