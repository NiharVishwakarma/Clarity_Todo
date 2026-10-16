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
}
