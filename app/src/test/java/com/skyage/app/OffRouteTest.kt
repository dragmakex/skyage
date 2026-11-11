package com.skyage.app

import com.skyage.app.data.FlightPhase
import com.skyage.app.data.FlightView
import com.skyage.app.data.LiveState
import com.skyage.app.data.TrackedFlight
import com.skyage.app.data.offRouteToleranceKm
import com.skyage.app.ui.isOffRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tolerance has to follow the route: a long-haul is routinely filed well off its great circle
 * (Toronto-Delhi runs over Europe and Iran, 1,600 km off), a short hop is not.
 */
class OffRouteTest {

    @Test
    fun shortRoutesKeepTheFlatRadius() {
        assertEquals(400.0, offRouteToleranceKm(600.0), 0.01)
        assertEquals(400.0, offRouteToleranceKm(null), 0.01)
    }

    @Test
    fun longRoutesGetAProportionalSlack() {
        assertEquals(931.5, offRouteToleranceKm(6_210.0), 0.01)
        // Toronto-Delhi at 1,668 km off is a real routing, not a different aircraft.
        assertEquals(1_746.0, offRouteToleranceKm(11_640.0), 0.01)
    }

    private fun viewWith(live: LiveState?, offRouteKm: Double) = FlightView(
        flight = TrackedFlight(
            key = "ACA42|YYZ|DEL",
            number = "AC42",
            callsign = "ACA42",
            airlineIata = "AC",
            airlineName = "Air Canada",
            originIata = "YYZ",
            destIata = "DEL",
        ),
        live = live,
        origin = null,
        dest = null,
        phase = if (live == null) FlightPhase.NO_DATA else FlightPhase.AIRBORNE,
        progress = null,
        routeKm = 11_640.0,
        remainingKm = null,
        offRouteKm = offRouteKm,
    )

    private val fix = LiveState(
        icao24 = "c05b1b",
        callsign = "ACA42",
        originCountry = "Canada",
        lat = 28.5,
        lon = 77.0,
        baroAltitudeM = 10_000.0,
        velocityMs = 250.0,
        headingDeg = 90.0,
        verticalRateMs = 0.0,
        onGround = false,
        lastContact = 1_794_000_000L,
    )

    @Test
    fun aLiveAircraftFarOffItsRouteIsCalledOffRoute() {
        assertTrue(isOffRoute(viewWith(fix, offRouteKm = 1_900.0)))
    }

    @Test
    fun aLostAircraftIsNotBlamedForBeingOffRoute() {
        // Same stale offset, no fix arriving: that is a missing aircraft, not a wrong match.
        assertFalse(isOffRoute(viewWith(null, offRouteKm = 1_900.0)))
    }
}
