package com.skyage.app

import com.skyage.app.geo.GlobeCamera
import com.skyage.app.geo.GlobeView
import com.skyage.app.geo.normalizeLon
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Orthographic globe projection and the drag/zoom camera, checked against hand-computed values. */
class GlobeTest {

    private val out = DoubleArray(2)

    private fun depth(lat: Double, lon: Double, camLat: Double = 0.0, camLon: Double = 0.0): Double =
        GlobeView(camLon, camLat).project(lat, lon, out)

    @Test
    fun centreOfTheViewIsTheMiddleOfTheDisc() {
        val d = depth(lat = -20.0, lon = 30.0, camLat = -20.0, camLon = 30.0)
        assertEquals(1.0, d, 1e-9)
        assertEquals(0.0, out[0], 1e-9)
        assertEquals(0.0, out[1], 1e-9)
    }

    @Test
    fun theAntipodeIsBehindTheGlobe() {
        assertEquals(-1.0, depth(lat = 0.0, lon = 180.0), 1e-9)
        assertEquals(-1.0, depth(lat = 0.0, lon = -180.0), 1e-9)
    }

    @Test
    fun ninetyDegreesEastSitsOnTheRightLimb() {
        assertEquals(0.0, depth(lat = 0.0, lon = 90.0), 1e-9)
        assertEquals(1.0, out[0], 1e-9)
        assertEquals(0.0, out[1], 1e-9)
    }

    @Test
    fun sixtyDegreesEastIsHalfFacing() {
        assertEquals(0.5, depth(lat = 0.0, lon = 60.0), 1e-9)
        assertEquals(sin(Math.toRadians(60.0)), out[0], 1e-9)
        assertEquals(0.0, out[1], 1e-9)
    }

    @Test
    fun thePoleIsUpFromAnEquatorialView() {
        assertEquals(0.0, depth(lat = 90.0, lon = 0.0), 1e-9)
        assertEquals(1.0, out[1], 1e-9)
    }

    @Test
    fun everyPointNinetyDegreesAwayIsOnTheHorizon() {
        val globe = GlobeView(12.0, 33.0)
        var lon = -180.0
        while (lon < 180.0) {
            var lat = -85.0
            while (lat <= 85.0) {
                // Central angle from the camera centre, via the dot product of unit vectors.
                val d = Math.toRadians(lat)
                val centreDistance = Math.toDegrees(
                    Math.acos(
                        Math.sin(d) * Math.sin(Math.toRadians(33.0)) +
                            Math.cos(d) * Math.cos(Math.toRadians(33.0)) *
                            Math.cos(Math.toRadians(lon - 12.0))
                    )
                )
                if (abs(centreDistance - 90.0) < 1e-6) {
                    assertEquals("lat=$lat lon=$lon", 0.0, globe.project(lat, lon, out), 1e-9)
                }
                lat += 5.0
            }
            lon += 5.0
        }
    }

    @Test
    fun pointsBehindTheLimbHaveNoScreenPosition() {
        val globe = GlobeView(0.0, 0.0)
        assertNull(globe.screen(0.0, 170.0, 540f, 1100f, 500f))
        val front = globe.screen(10.0, 20.0, 540f, 1100f, 500f)
        assertNotNull(front)
        // East of centre is right of centre, north of centre is up (smaller y).
        assertTrue(front!![0] > 540.0)
        assertTrue(front[1] < 1100.0)
    }

    @Test
    fun draggingRightTurnsTheGlobeWest() {
        // A radius-wide drag turns the globe by 90°, so 100 px on a 500 px globe is 18°.
        val moved = GlobeCamera(lonDeg = 10.0).dragged(dxPx = 100f, dyPx = 0f, radiusPx = 500f)
        assertEquals(-8.0, moved.lonDeg, 1e-9)
    }

    @Test
    fun draggingDownLooksNorthAndStopsAtThePole() {
        val moved = GlobeCamera(latDeg = 0.0).dragged(dxPx = 0f, dyPx = 100f, radiusPx = 500f)
        assertEquals(18.0, moved.latDeg, 1e-9)

        val far = GlobeCamera(latDeg = 0.0).dragged(dxPx = 0f, dyPx = 1e6f, radiusPx = 500f)
        assertEquals(GlobeCamera.MAX_LAT, far.latDeg, 1e-9)

        val back = GlobeCamera(latDeg = 0.0).dragged(dxPx = 0f, dyPx = -1e6f, radiusPx = 500f)
        assertEquals(-GlobeCamera.MAX_LAT, back.latDeg, 1e-9)
    }

    @Test
    fun longitudeWrapsAndZoomIsClamped() {
        val wrapped = GlobeCamera(lonDeg = 179.0).dragged(dxPx = -100f, dyPx = 0f, radiusPx = 500f)
        assertEquals(-163.0, wrapped.lonDeg, 1e-9)

        assertEquals(GlobeCamera.MAX_ZOOM, GlobeCamera().zoomed(1000f).zoom, 1e-6f)
        assertEquals(GlobeCamera.MIN_ZOOM, GlobeCamera(zoom = 2f).zoomed(0.001f).zoom, 1e-6f)

        assertEquals(180.0, normalizeLon(-180.0), 1e-9)
    }
}
