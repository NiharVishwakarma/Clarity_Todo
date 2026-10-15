package com.example.darktodo.data

/** Android system font families usable directly by TextViews in widget RemoteViews. */
enum class WidgetFontFamily(val key: String, val label: String) {
    SANS_SERIF("sans-serif", "Sans serif"),
    SANS_SERIF_CONDENSED("sans-serif-condensed", "Sans serif condensed"),
    SANS_SERIF_LIGHT("sans-serif-light", "Sans serif light"),
    SERIF("serif", "Serif"),
    MONOSPACE("monospace", "Monospace");

    companion object {
        fun fromKey(key: String?): WidgetFontFamily? = entries.firstOrNull { it.key == key }

        /** Old bundled-font selections fall back to the standard system sans family. */
        fun fromStoredKey(key: String?): WidgetFontFamily? = fromKey(key) ?: when (key) {
            "googlesans", "opensans", "montserrat", "notosans", "ubuntu", "nunito" -> SANS_SERIF
            else -> null
        }
    }
}
