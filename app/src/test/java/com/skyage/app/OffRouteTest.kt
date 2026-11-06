package com.skyage.app

import com.skyage.app.data.offRouteToleranceKm
import org.junit.Assert.assertEquals
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
}
