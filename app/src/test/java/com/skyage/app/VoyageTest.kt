package com.skyage.app

import com.skyage.app.data.Airport
import com.skyage.app.data.Trip
import com.skyage.app.data.decodeTrips
import com.skyage.app.data.encodeTrips
import com.skyage.app.data.tripKey
import com.skyage.app.data.tripMonths
import com.skyage.app.data.tripYears
import com.skyage.app.data.tripsIn
import com.skyage.app.data.voyageStats
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VoyageTest {

    private val zone = ZoneId.of("UTC")

    /** Local noon on a given day, so the test cannot straddle a date boundary. */
    private fun date(year: Int, month: Int, day: Int): Long = LocalDateTime
        .of(year, month, day, 12, 0)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun airport(code: String, city: String, country: String, lat: Double, lon: Double) =
        Airport(
            name = "$city Airport",
            iata = code,
            icao = null,
            city = city,
            country = country,
            lat = lat,
            lon = lon,
            relevance = 0,
        )

    private val lookups = listOf(
        airport("JFK", "New York", "United States", 40.6413, -73.7781),
        airport("LHR", "London", "United Kingdom", 51.4700, -0.4543),
        airport("AMS", "Amsterdam", "Netherlands", 52.3105, 4.7683),
    ).associateBy { it.code }

    private val trips = listOf(
        Trip(date(2026, 3, 4), "KL100", "KL", "AMS", "LHR"),
        Trip(date(2026, 3, 9), "KL101", "KL", "LHR", "AMS"),
        Trip(date(2025, 11, 2), "UA200", "UA", "JFK", "LHR"),
    )

    @Test
    fun `trips round-trip through the store format`() {
        assertEquals(trips, decodeTrips(encodeTrips(trips)))
    }

    @Test
    fun `malformed lines are skipped, not fatal`() {
        val text = "nope\n${encodeTrips(trips)}\n123|UA1|UA|JF|LHR"
        assertEquals(trips, decodeTrips(text))
    }

    @Test
    fun `a repeated flight on the same day is the same trip`() {
        assertEquals(tripKey(trips[0], zone), tripKey(trips[0].copy(date = trips[0].date + 3_600_000), zone))
        assertNotEquals(tripKey(trips[0], zone), tripKey(trips[0].copy(date = date(2026, 3, 5)), zone))
    }

    @Test
    fun `years and months come only from the log and in order`() {
        assertEquals(listOf(2026, 2025), tripYears(trips, zone))
        assertEquals(listOf(3), tripMonths(trips, 2026, zone))
        assertEquals(listOf(3, 11), tripMonths(trips, null, zone))
        assertEquals(listOf(11), tripMonths(trips, 2025, zone))
    }

    @Test
    fun `filters narrow by year then month`() {
        assertEquals(2, tripsIn(trips, 2026, null, zone).size)
        assertEquals(0, tripsIn(trips, 2026, 11, zone).size) // the November trip is 2025
        assertEquals(1, tripsIn(trips, null, 11, zone).size)
        assertEquals(3, tripsIn(trips, null, null, zone).size)
    }

    @Test
    fun `totals count both ends of a route as visited`() {
        val stats = voyageStats(trips) { lookups[it] }
        assertEquals(3, stats.flights)
        assertEquals(3, stats.airports)
        assertEquals(3, stats.countries)
        assertEquals(listOf("LHR", "AMS", "JFK"), stats.visits.map { it.code })
        assertEquals(listOf(3, 2, 1), stats.visits.map { it.visits })
    }

    @Test
    fun `distance adds up the routes it can resolve`() {
        val stats = voyageStats(trips) { lookups[it] }
        // AMS->LHR twice (~371 km each) plus JFK->LHR (~5,555 km).
        assertEquals(6_280.6, stats.km, 5.0)
    }

    @Test
    fun `an airport the dataset cannot resolve is counted but not listed`() {
        val stats = voyageStats(listOf(Trip(date(2026, 1, 1), "ZZ1", "ZZ", "AMS", "QQQ"))) { lookups[it] }
        assertEquals(2, stats.airports)
        assertEquals(1, stats.countries)
        assertEquals(listOf("AMS"), stats.visits.map { it.code })
        assertEquals(0.0, stats.km, 0.001)
    }

    @Test
    fun `a repeated line counts as one trip`() {
        val line = "1793983866003|B61533|B6|SFO|JFK"
        val trips = decodeTrips("$line\n$line")
        assertEquals(1, trips.size)
    }

    @Test
    fun `a file written with windows line endings still reads`() {
        val text = encodeTrips(trips).replace("\n", "\r\n")
        assertEquals(trips, decodeTrips(text))
    }

    @Test
    fun `a half written line at the end is dropped, not fatal`() {
        val text = encodeTrips(trips) + "\n1793983866003|B61533"
        assertEquals(trips, decodeTrips(text))
    }

    @Test
    fun `a trip that starts and ends at the same airport is one airport, no distance`() {
        val stats = voyageStats(listOf(Trip(date(2026, 5, 1), "KL1", "KL", "AMS", "AMS"))) { lookups[it] }
        assertEquals(1, stats.flights)
        assertEquals(1, stats.airports)
        assertEquals(1, stats.countries)
        assertEquals(1, stats.visits.single().visits)
        assertEquals(0.0, stats.km, 0.001)
    }

    @Test
    fun `an empty log totals nothing`() {
        val stats = voyageStats(emptyList()) { lookups[it] }
        assertEquals(0, stats.flights)
        assertEquals(0, stats.airports)
        assertEquals(0, stats.countries)
        assertEquals(emptyList<String>(), stats.visits.map { it.code })
    }

    @Test
    fun `a timestamp instant cannot represent is dropped, not fatal`() {
        val text = "9223372036854775807|B61533|B6|SFO|JFK\n-1|B61533|B6|SFO|JFK\n" + encodeTrips(trips)
        assertEquals(trips, decodeTrips(text))
    }

    @Test
    fun `airlines counts carriers, not flights`() {
        val log = listOf(
            Trip(date(2026, 5, 1), "BA286", "BA", "SFO", "LHR"),
            Trip(date(2026, 5, 2), "BA287", "BA", "LHR", "SFO"),
            Trip(date(2026, 5, 3), "KL641", "KL", "AMS", "JFK"),
            Trip(date(2026, 5, 4), "KLM641", "", "AMS", "JFK"),
        )
        val stats = voyageStats(log) { lookups[it] }
        assertEquals(4, stats.flights)
        assertEquals(2, stats.airlines)
    }
}
