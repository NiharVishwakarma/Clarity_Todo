package com.example.darktodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class TimeZoneCatalogTest {
    @Test fun catalogContainsAvailableIdsOnceAndPutsPopularZonesFirst() {
        val available = ZoneId.getAvailableZoneIds()
        assertEquals(available, TimeZoneCatalog.all.map { it.id }.toSet())
        assertEquals(TimeZoneCatalog.all.size, TimeZoneCatalog.all.map { it.id }.toSet().size)
        assertTrue(TimeZoneCatalog.popular.isNotEmpty())
        assertEquals("Asia/Kolkata", TimeZoneCatalog.popular.first().id)
    }

    @Test fun searchMatchesCityCountryAndIanaIdentifier() {
        assertTrue(TimeZoneCatalog.search("new york").any { it.id == "America/New_York" })
        assertTrue(TimeZoneCatalog.search("United States").any { it.id == "America/Los_Angeles" })
        assertTrue(TimeZoneCatalog.search("Asia/Kolkata").any { it.city == "Kolkata" })
        assertTrue(TimeZoneCatalog.search("nothing matches").isEmpty())
    }

    @Test fun chosenZoneResolvesWallTimeUsingItsOwnRules() {
        val local = LocalDateTime.parse("2026-10-14T09:59:00")
        val resolved = TimeZoneCatalog.resolveLocalDateTime(local, "Asia/Kolkata")
        assertEquals(Instant.parse("2026-10-14T04:29:00Z"), resolved.toInstant())
    }

    @Test fun daylightSavingGapMovesForwardAndOverlapChoosesEarlierOccurrence() {
        val gap = TimeZoneCatalog.resolveLocalDateTime(
            LocalDateTime.parse("2026-03-08T02:30:00"), "America/New_York"
        )
        assertEquals(LocalDateTime.parse("2026-03-08T03:30:00"), gap.toLocalDateTime())

        val overlap = TimeZoneCatalog.resolveLocalDateTime(
            LocalDateTime.parse("2026-11-01T01:30:00"), "America/New_York"
        )
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), overlap.toInstant())
    }

    @Test fun unavailableIdentifierFailsClearlyAndCanBeHandledByCaller() {
        assertFalse(TimeZoneCatalog.all.any { it.id == "Mars/Olympus" })
        try {
            TimeZoneCatalog.resolveLocalDateTime(LocalDateTime.now(), "Mars/Olympus")
            throw AssertionError("Expected invalid zone identifier to be rejected")
        } catch (_: DateTimeException) {
            // The UI can keep the saved value visible and let the user select an available replacement.
        }
    }
}
