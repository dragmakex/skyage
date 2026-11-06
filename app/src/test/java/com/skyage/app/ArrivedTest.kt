package com.skyage.app

import com.skyage.app.data.Airport
import com.skyage.app.data.LiveState
import com.skyage.app.data.arrivedAt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivedTest {

    private val jfk = Airport("John F. Kennedy", "JFK", "KJFK", "New York", "US", 40.6413, -73.7781, 0)

    private fun state(lat: Double?, lon: Double?) = LiveState(
        icao24 = "a1b2c3",
        callsign = "KLM641",
        originCountry = "Netherlands",
        lat = lat,
        lon = lon,
        baroAltitudeM = null,
        velocityMs = null,
        headingDeg = null,
        verticalRateMs = null,
        onGround = true,
        lastContact = 1_700_000_000L,
    )

    @Test
    fun `touching down at the destination counts as landed`() {
        // A few km off the reference point: on the runway, inside the gate area.
        assertTrue(arrivedAt(state(40.65, -73.79), jfk))
    }

    @Test
    fun `a flight that vanished mid-route has not landed`() {
        // 400 km short: a receiver gap, not a touchdown.
        assertFalse(arrivedAt(state(41.0, -69.0), jfk))
    }

    @Test
    fun `no position or no destination cannot be called landed`() {
        assertFalse(arrivedAt(state(null, null), jfk))
        assertFalse(arrivedAt(null, jfk))
        assertFalse(arrivedAt(state(40.65, -73.79), null))
    }
}
