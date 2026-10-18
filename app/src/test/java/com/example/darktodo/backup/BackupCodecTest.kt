package com.example.darktodo.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupCodecTest {
    @Test fun roundTripsEveryPreferenceType() {
        val original = mapOf<String, Any>(
            "text" to "hello \"quoted\"", "flag" to true, "count" to 7, "big" to 1_700_000_000_000L,
            "size" to 14.5f, "tags" to setOf("a", "b")
        )
        val decoded = BackupCodec.decodePrefs(BackupCodec.encodePrefs(original))
        assertEquals(original, decoded)
        assertEquals(Long::class.javaObjectType, decoded.getValue("big").javaClass)
        assertEquals(Int::class.javaObjectType, decoded.getValue("count").javaClass)
    }

    @Test fun skipsUnknownTypes() {
        assertEquals(emptyMap<String, Any>(), BackupCodec.decodePrefs(BackupCodec.encodePrefs(mapOf("x" to Any()))))
    }

    @Test fun colorDepthPreferencesRoundTripWithoutChangingColorsOrTransparency() {
        val tasks = mapOf<String, Any>(
            "widget_setting_background_depth" to true,
            "widget_setting_background" to "#123456",
            "widget_setting_task_card" to "#ABCDEF",
            "widget_setting_task_card_transparency" to 50
        )
        val calendar = mapOf<String, Any>("background_depth" to false, "background" to "#000000")
        for (settings in listOf(tasks, calendar)) {
            assertEquals(settings, BackupCodec.decodePrefs(BackupCodec.encodePrefs(settings)))
        }
        val legacy = mapOf<String, Any>("background" to "#FFFFFF")
        assertEquals(legacy, BackupCodec.decodePrefs(BackupCodec.encodePrefs(legacy)))
    }
}
