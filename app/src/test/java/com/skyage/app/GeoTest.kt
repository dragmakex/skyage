package com.skyage.app

import com.skyage.app.data.OFF_ROUTE_THRESHOLD_KM
import com.skyage.app.geo.crossTrackKm
import com.skyage.app.geo.distanceKm
import com.skyage.app.geo.interpolateGreatCircle
import com.skyage.app.geo.progressAlongRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Expected values were computed independently (plain spherical trig in Python) so this guards the
 * implementation rather than restating it.
 *
 * Route under test: SFO (37.6188, -122.3754) -> NRT (35.7647, 140.3864), 8227.5 km.
 */
class GeoTest {

    private val sfoLat = 37.6188
    private val sfoLon = -122.3754
    private val nrtLat = 35.7647
    private val nrtLon = 140.3864

    @Test
    fun `distance matches the real great circle`() {
        val km = distanceKm(sfoLat, sfoLon, nrtLat, nrtLon)
        assertEquals(8227.5, km, 2.0)
        assertEquals(0.0, distanceKm(sfoLat, sfoLon, sfoLat, sfoLon), 1e-6)
    }

    @Test
    fun `midpoint of the route is halfway along it`() {
        val (lat, lon) = interpolateGreatCircle(sfoLat, sfoLon, nrtLat, nrtLon, 0.5)
        assertEquals(48.42, lat, 0.05)
        assertEquals(-171.779, lon, 0.05)
        assertEquals(0.5, progressAlongRoute(sfoLat, sfoLon, nrtLat, nrtLon, lat, lon)!!, 1e-3)
    }

    @Test
    fun `a point near new york is rejected as off route`() {
        val offRoute = crossTrackKm(sfoLat, sfoLon, nrtLat, nrtLon, 40.6413, -73.7781)
        assertEquals(3237.6, offRoute, 5.0)
        assertTrue(
            "JFK must trip the off-route gate",
            offRoute > OFF_ROUTE_THRESHOLD_KM,
        )
    }

    @Test
    fun `positions along the route stay well inside the gate`() {
        listOf(0.1, 0.25, 0.5, 0.75, 0.9).forEach { f ->
            val (lat, lon) = interpolateGreatCircle(sfoLat, sfoLon, nrtLat, nrtLon, f)
            val offRoute = crossTrackKm(sfoLat, sfoLon, nrtLat, nrtLon, lat, lon)
            assertTrue("f=$f drifted $offRoute km", offRoute < 1.0)
            assertEquals(f, progressAlongRoute(sfoLat, sfoLon, nrtLat, nrtLon, lat, lon)!!, 1e-3)
        }
    }

    @Test
    fun `progress saturates instead of going negative or past the destination`() {
        // Behind the origin.
        assertEquals(
            0.0,
            progressAlongRoute(sfoLat, sfoLon, nrtLat, nrtLon, 20.0, -122.0)!!,
            1e-6,
        )
        // Beyond the destination, along the same track.
        val (lat, lon) = interpolateGreatCircle(sfoLat, sfoLon, nrtLat, nrtLon, 1.3)
        assertEquals(1.0, progressAlongRoute(sfoLat, sfoLon, nrtLat, nrtLon, lat, lon)!!, 1e-6)
    }

    @Test
    fun `degenerate route has no progress`() {
        assertEquals(null, progressAlongRoute(sfoLat, sfoLon, sfoLat, sfoLon, 10.0, 10.0))
    }

    @Test
    fun `remaining distance decreases monotonically`() {
        val total = distanceKm(sfoLat, sfoLon, nrtLat, nrtLon)
        var previous = total
        listOf(0.2, 0.4, 0.6, 0.8).forEach { f ->
            val (lat, lon) = interpolateGreatCircle(sfoLat, sfoLon, nrtLat, nrtLon, f)
            val remaining = total * (1 - progressAlongRoute(sfoLat, sfoLon, nrtLat, nrtLon, lat, lon)!!)
            assertTrue("remaining grew at f=$f", remaining < previous)
            previous = remaining
        }
    }
}
