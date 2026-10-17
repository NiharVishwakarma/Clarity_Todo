package com.example.darktodo.data

import java.time.ZoneId
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.Locale

data class TimeZoneOption(
    val id: String,
    val city: String,
    val country: String,
    val popular: Boolean
) {
    val label: String get() = "$city, $country"
    val searchText: String get() = "$city $country $id".lowercase(Locale.ROOT)
}

/** A searchable catalog backed by the complete set of time zones available to java.time. */
object TimeZoneCatalog {
    private data class PopularZone(val id: String, val city: String, val country: String)

    private val popularZones = listOf(
        PopularZone("Asia/Kolkata", "Kolkata", "India"),
        PopularZone("Asia/Shanghai", "Shanghai", "China"),
        PopularZone("Asia/Tokyo", "Tokyo", "Japan"),
        PopularZone("Asia/Riyadh", "Riyadh", "Saudi Arabia"),
        PopularZone("Asia/Dubai", "Dubai", "United Arab Emirates"),
        PopularZone("Asia/Singapore", "Singapore", "Singapore"),
        PopularZone("Asia/Seoul", "Seoul", "South Korea"),
        PopularZone("Asia/Jakarta", "Jakarta", "Indonesia"),
        PopularZone("Asia/Karachi", "Karachi", "Pakistan"),
        PopularZone("Asia/Dhaka", "Dhaka", "Bangladesh"),
        PopularZone("Asia/Hong_Kong", "Hong Kong", "Hong Kong"),
        PopularZone("Europe/Istanbul", "Istanbul", "Turkey"),
        PopularZone("America/New_York", "New York", "United States"),
        PopularZone("America/Chicago", "Chicago", "United States"),
        PopularZone("America/Denver", "Denver", "United States"),
        PopularZone("America/Los_Angeles", "Los Angeles", "United States"),
        PopularZone("America/Anchorage", "Anchorage", "United States"),
        PopularZone("Pacific/Honolulu", "Honolulu", "United States"),
        PopularZone("America/Toronto", "Toronto", "Canada"),
        PopularZone("America/Vancouver", "Vancouver", "Canada"),
        PopularZone("America/Mexico_City", "Mexico City", "Mexico"),
        PopularZone("Europe/London", "London", "United Kingdom"),
        PopularZone("Europe/Paris", "Paris", "France"),
        PopularZone("Europe/Berlin", "Berlin", "Germany"),
        PopularZone("Europe/Madrid", "Madrid", "Spain"),
        PopularZone("Europe/Rome", "Rome", "Italy"),
        PopularZone("Europe/Amsterdam", "Amsterdam", "Netherlands"),
        PopularZone("Europe/Zurich", "Zurich", "Switzerland"),
        PopularZone("Europe/Stockholm", "Stockholm", "Sweden"),
        PopularZone("Europe/Warsaw", "Warsaw", "Poland"),
        PopularZone("Europe/Athens", "Athens", "Greece"),
        PopularZone("Europe/Lisbon", "Lisbon", "Portugal"),
        PopularZone("Europe/Dublin", "Dublin", "Ireland"),
        PopularZone("Europe/Moscow", "Moscow", "Russia"),
        PopularZone("Europe/Kaliningrad", "Kaliningrad", "Russia"),
        PopularZone("Asia/Yekaterinburg", "Yekaterinburg", "Russia"),
        PopularZone("Asia/Novosibirsk", "Novosibirsk", "Russia"),
        PopularZone("Asia/Vladivostok", "Vladivostok", "Russia"),
        PopularZone("Australia/Sydney", "Sydney", "Australia"),
        PopularZone("Australia/Perth", "Perth", "Australia"),
        PopularZone("Pacific/Auckland", "Auckland", "New Zealand"),
        PopularZone("America/Sao_Paulo", "Sao Paulo", "Brazil"),
        PopularZone("Africa/Johannesburg", "Johannesburg", "South Africa"),
        PopularZone("Africa/Cairo", "Cairo", "Egypt"),
        PopularZone("Africa/Lagos", "Lagos", "Nigeria"),
        PopularZone("Africa/Nairobi", "Nairobi", "Kenya")
    )

    private val canadianCities = setOf(
        "Atikokan", "Blanc-Sablon", "Cambridge_Bay", "Creston", "Dawson", "Dawson_Creek",
        "Edmonton", "Fort_Nelson", "Glace_Bay", "Goose_Bay", "Halifax", "Inuvik", "Iqaluit",
        "Moncton", "Montreal", "Nipigon", "Pangnirtung", "Rainy_River", "Rankin_Inlet",
        "Regina", "Resolute", "St_Johns", "Swift_Current", "Thunder_Bay", "Toronto",
        "Vancouver", "Whitehorse", "Winnipeg", "Yellowknife"
    )
    private val unitedStatesCities = setOf(
        "Adak", "Anchorage", "Boise", "Chicago", "Denver", "Detroit", "Juneau", "Los_Angeles",
        "Menominee", "Metlakatla", "New_York", "Nome", "Phoenix", "Sitka", "Yakutat",
        "Indiana/Indianapolis", "Indiana/Knox", "Indiana/Marengo", "Indiana/Petersburg",
        "Indiana/Tell_City", "Indiana/Vevay", "Indiana/Vincennes", "Indiana/Winamac",
        "Kentucky/Louisville", "Kentucky/Monticello", "North_Dakota/Beulah",
        "North_Dakota/Center", "North_Dakota/New_Salem"
    )
    private val russianCities = setOf(
        "Europe/Kaliningrad", "Europe/Moscow", "Europe/Samara", "Europe/Volgograd",
        "Europe/Kirov", "Europe/Astrakhan", "Europe/Ulyanovsk", "Asia/Anadyr", "Asia/Barnaul",
        "Asia/Chita", "Asia/Irkutsk", "Asia/Khandyga", "Asia/Krasnoyarsk", "Asia/Magadan",
        "Asia/Novokuznetsk", "Asia/Novosibirsk", "Asia/Omsk", "Asia/Sakhalin",
        "Asia/Srednekolymsk", "Asia/Tomsk", "Asia/Ust-Nera", "Asia/Vladivostok",
        "Asia/Yakutsk", "Asia/Yekaterinburg"
    )

    val all: List<TimeZoneOption> by lazy {
        val availableIds = ZoneId.getAvailableZoneIds()
        val preferred = popularZones.filter { it.id in availableIds }.map {
            TimeZoneOption(it.id, it.city, it.country, popular = true)
        }
        val remaining = (availableIds - preferred.map { it.id }.toSet())
            .map { id ->
                val parts = id.split('/')
                val city = parts.last().replace('_', ' ').replace('-', ' ')
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
                val area = parts.firstOrNull()?.replace('_', ' ') ?: "Other"
                TimeZoneOption(id, city, countryFor(id, parts, area), popular = false)
            }
            .sortedWith(compareBy<TimeZoneOption>({ it.city.lowercase(Locale.ENGLISH) }, { it.id }))
        preferred + remaining
    }

    val popular: List<TimeZoneOption> by lazy { all.filter { it.popular } }
    val other: List<TimeZoneOption> by lazy { all.filterNot { it.popular } }

    fun find(id: String): TimeZoneOption? = all.firstOrNull { it.id == id }

    fun search(query: String): List<TimeZoneOption> {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return all
        return all.filter { it.searchText.contains(needle) }
    }

    fun labelFor(id: String): String? = find(id)?.label

    /** Resolves a wall-clock date/time with java.time's deterministic gap/overlap rules. */
    fun resolveLocalDateTime(local: LocalDateTime, id: String): ZonedDateTime = local.atZone(ZoneId.of(id))

    private fun countryFor(id: String, parts: List<String>, fallback: String): String = when {
        id.startsWith("Australia/") -> "Australia"
        id == "Pacific/Honolulu" || id.startsWith("America/") && parts.drop(1).joinToString("/") in unitedStatesCities -> "United States"
        id.startsWith("America/") && parts.last() in canadianCities -> "Canada"
        id in russianCities -> "Russia"
        else -> fallback
    }
}
