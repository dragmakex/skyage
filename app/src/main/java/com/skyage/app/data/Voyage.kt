package com.skyage.app.data

import com.skyage.app.geo.distanceKm
import java.time.Instant
import java.time.ZoneId

/**
 * One flight the user has followed, logged with its route so it can be counted later.
 *
 * Only the day is meaningful: there is no schedule feed to say when a flight actually operated, so
 * the log records the day it was added and nothing pretends to know more.
 */
data class Trip(
    val date: Long,
    val number: String,
    val airlineIata: String,
    val originIata: String,
    val destIata: String,
)

/** Same flight, same route, same day: following it twice is still one trip. */
internal fun tripKey(trip: Trip, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = Instant.ofEpochMilli(trip.date).atZone(zone).toLocalDate()
    return "${trip.number}|${trip.originIata}|${trip.destIata}|$day"
}

internal fun encodeTrips(trips: List<Trip>): String = trips.joinToString("\n") {
    "${it.date}|${it.number}|${it.airlineIata}|${it.originIata}|${it.destIata}"
}

internal fun decodeTrips(text: String): List<Trip> = text.lineSequence().mapNotNull { line ->
    val p = line.split('|')
    if (p.size < 5) return@mapNotNull null
    val date = p[0].toLongOrNull() ?: return@mapNotNull null
    if (p[1].isBlank() || p[3].length != 3 || p[4].length != 3) return@mapNotNull null
    Trip(date = date, number = p[1], airlineIata = p[2], originIata = p[3], destIata = p[4])
}
    // A repeated line is the same trip: counting it twice would inflate every total.
    .distinctBy { tripKey(it) }
    .toList()

internal fun tripYear(date: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
    Instant.ofEpochMilli(date).atZone(zone).year

internal fun tripMonth(date: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
    Instant.ofEpochMilli(date).atZone(zone).monthValue

/** Years that have trips, newest first — the only years worth offering as a filter. */
internal fun tripYears(trips: List<Trip>, zone: ZoneId = ZoneId.systemDefault()): List<Int> =
    trips.map { tripYear(it.date, zone) }.distinct().sortedDescending()

/** Months with trips in the chosen year (or in any year), January first. */
internal fun tripMonths(
    trips: List<Trip>,
    year: Int?,
    zone: ZoneId = ZoneId.systemDefault(),
): List<Int> = trips
    .filter { year == null || tripYear(it.date, zone) == year }
    .map { tripMonth(it.date, zone) }
    .distinct()
    .sorted()

internal fun tripsIn(
    trips: List<Trip>,
    year: Int?,
    month: Int?,
    zone: ZoneId = ZoneId.systemDefault(),
): List<Trip> = trips.filter {
    (year == null || tripYear(it.date, zone) == year) &&
        (month == null || tripMonth(it.date, zone) == month)
}

/** An airport the user has been through, and how many of the selected trips touched it. */
data class AirportVisit(val code: String, val city: String, val country: String, val visits: Int)

data class VoyageStats(
    val flights: Int,
    val airports: Int,
    val countries: Int,
    val km: Double,
    val visits: List<AirportVisit>,
)

/**
 * Totals for the selected trips. Both ends of a route count as visited: being at the departure
 * airport is being there. Airports the bundled data cannot resolve are counted but not listed,
 * rather than listed with a blank city.
 */
internal fun voyageStats(trips: List<Trip>, airport: (String) -> Airport?): VoyageStats {
    val codes = trips.flatMap { listOf(it.originIata, it.destIata) }.distinct()
    val resolved = codes.mapNotNull { code -> airport(code)?.let { code to it } }.toMap()
    val visits = codes
        .mapNotNull { code -> resolved[code]?.let { AirportVisit(code, it.city, it.country, 0) } }
        .let { list ->
            list.map { v -> v.copy(visits = trips.count { it.originIata == v.code || it.destIata == v.code }) }
        }
        .sortedWith(compareByDescending<AirportVisit> { it.visits }.thenBy { it.code })
    val km = trips.sumOf { t ->
        val from = airport(t.originIata)
        val to = airport(t.destIata)
        if (from != null && to != null) distanceKm(from.lat, from.lon, to.lat, to.lon) else 0.0
    }
    return VoyageStats(
        flights = trips.size,
        airports = codes.size,
        countries = resolved.values.map { it.country }.distinct().size,
        km = km,
        visits = visits,
    )
}
