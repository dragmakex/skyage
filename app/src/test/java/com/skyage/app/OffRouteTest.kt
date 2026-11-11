package com.skyage.app

import com.skyage.app.data.FlightPhase
import com.skyage.app.data.FlightView
import com.skyage.app.data.LiveState
import com.skyage.app.data.TrackedFlight
import com.skyage.app.data.offRoute
import com.skyage.app.ui.isOffRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Doubting a callsign match has to survive real routings, not ideal ones. Toronto-Delhi is filed
 * south of its great circle and runs 2,400 km off it over Poland, which no fraction of the route
 * length can express; a different aircraft with the same callsign is off the route in distance too.
 */
class OffRouteTest {

    @Test
    fun aFixCloseToTheRouteIsBelievedAtAnyLength() {
        assertFalse(offRoute(600.0, offRouteKm = 300.0, viaKm = 610.0))
        assertFalse(offRoute(11_640.0, offRouteKm = 300.0, viaKm = 11_700.0))
    }

    @Test
    fun aLongHaulFarOffTheGreatCircleIsStillOnItsWay() {
        // Seen live: AC42 over Poland, 2,417 km off its great circle, 12,456 km via the fix
        // against 11,640 km non-stop.
        assertFalse(offRoute(11_640.0, offRouteKm = 2_417.0, viaKm = 12_456.0))
    }

    @Test
    fun anAircraftThatWouldHaveToFlyMuchFurtherIsADifferentFlight() {
        // A callsign match 5,000 km off a transatlantic route: getting there and on would cost
        // more than a third again the non-stop distance.
        assertTrue(offRoute(5_848.0, offRouteKm = 5_000.0, viaKm = 9_000.0))
    }

    @Test
    fun aRouteWithNoEndpointsIsNeverInDoubt() {
        assertFalse(offRoute(routeKm = null, offRouteKm = 9_999.0, viaKm = null))
        assertFalse(offRoute(routeKm = 5_000.0, offRouteKm = null, viaKm = null))
    }

    private fun viewWith(live: LiveState?, viaKm: Double) = FlightView(
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
        offRouteKm = 2_417.0,
        viaKm = viaKm,
    )

    private val fix = LiveState(
        icao24 = "c05b1b",
        callsign = "ACA42",
        originCountry = "Canada",
        lat = 49.79,
        lon = 19.74,
        baroAltitudeM = 10_668.0,
        velocityMs = 260.0,
        headingDeg = 140.0,
        verticalRateMs = 0.0,
        onGround = false,
        lastContact = 1_794_000_000L,
    )

    @Test
    fun theCardWarnsOnlyWhenTheRouteItselfIsInDoubt() {
        assertFalse(isOffRoute(viewWith(fix, viaKm = 12_456.0)))
        assertTrue(isOffRoute(viewWith(fix, viaKm = 20_000.0)))
    }

    @Test
    fun aLostAircraftIsNotBlamedForBeingOffRoute() {
        // Same stale offset, no fix arriving: that is a missing aircraft, not a wrong match.
        assertFalse(isOffRoute(viewWith(null, viaKm = 20_000.0)))
    }

    @Test
    fun theDetourLimitLeavesRoomForWeatherButNotForAbsurdity() {
        assertEquals(1.35, com.skyage.app.data.DETOUR_LIMIT, 0.0)
    }
}
