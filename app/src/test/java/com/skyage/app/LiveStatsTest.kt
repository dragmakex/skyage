package com.skyage.app

import com.skyage.app.data.LiveState
import com.skyage.app.ui.liveStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A field the feed did not report is left out; the screen shows what it knows, not a dash. */
class LiveStatsTest {

    private fun fix(
        altitude: Double? = null,
        speed: Double? = null,
        heading: Double? = null,
        verticalRate: Double? = null,
    ) = LiveState(
        icao24 = "a1b2c3",
        callsign = "SWA4902",
        originCountry = "United States",
        lat = 38.8,
        lon = -77.0,
        baroAltitudeM = altitude,
        velocityMs = speed,
        headingDeg = heading,
        verticalRateMs = verticalRate,
        onGround = altitude == null,
        lastContact = 1_794_000_000L,
    )

    @Test
    fun anAirborneFixShowsAllFourInReadingOrder() {
        val stats = liveStats(fix(altitude = 10_000.0, speed = 250.0, heading = 90.0, verticalRate = 0.0))
        // Built with the same format as the app, since grouping follows the device locale.
        assertEquals(
            listOf(
                "Altitude" to "%,d ft".format(32_808),
                "Ground speed" to "%,d km/h".format(900),
                "Heading" to "090°",
                "Vertical rate" to "%+,.0f ft/min".format(0.0),
            ),
            stats,
        )
    }

    @Test
    fun anOnGroundFixWithNoAltitudeShowsOnlyWhatItKnows() {
        // Seen on the phone: an on-ground aircraft rendered "Altitude —" and "Vertical rate —".
        val stats = liveStats(fix(speed = 0.3, heading = 357.0))
        assertEquals(listOf("Ground speed", "Heading"), stats.map { it.first })
        assertTrue(stats.none { it.second == "—" })
    }

    @Test
    fun aFixWithNothingReportedProducesNoCellsAtAll() {
        assertTrue(liveStats(fix()).isEmpty())
    }
}
