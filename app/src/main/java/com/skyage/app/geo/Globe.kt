package com.skyage.app.geo

/**
 * Where the globe is looking and how close. Pure math so it is testable off-device; Compose only
 * stores one of these in state.
 */
data class GlobeCamera(
    val lonDeg: Double = 10.0,
    val latDeg: Double = 25.0,
    val zoom: Float = 1f,
) {
    /**
     * Turns the globe so the surface follows the finger. [radiusPx] is the globe radius on screen,
     * which is what makes the drag feel the same at every zoom level.
     */
    fun dragged(dxPx: Float, dyPx: Float, radiusPx: Float): GlobeCamera {
        if (radiusPx <= 0f) return this
        val degPerPx = 90.0 / radiusPx
        return copy(
            lonDeg = normalizeLon(lonDeg - dxPx * degPerPx),
            latDeg = (latDeg + dyPx * degPerPx).coerceIn(-MAX_LAT, MAX_LAT),
        )
    }

    fun zoomed(factor: Float): GlobeCamera =
        copy(zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM))

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 8f

        /** Double-tapping beyond this resets to the whole globe instead of zooming further. */
        const val RESET_ZOOM = 4f

        /** Latitude of the camera centre: the poles are reachable, the singularity is not. */
        const val MAX_LAT = 85.0
    }
}

/**
 * Orthographic projection of the unit sphere seen from (latDeg, lonDeg): the visible half is what
 * an astronaut would see, the rest is behind the limb.
 */
class GlobeView(lonDeg: Double, latDeg: Double) {
    private val lon0 = toRadians(lonDeg)
    private val sinLat = Math.sin(toRadians(latDeg))
    private val cosLat = Math.cos(toRadians(latDeg))

    /**
     * Projects a geo point. [out] receives the offset from the disc centre in globe radii
     * (`out[0]` east, `out[1]` north) and the return value is the depth: 1 dead centre, 0 on the
     * limb, negative on the far side.
     */
    fun project(latDeg: Double, lonDeg: Double, out: DoubleArray): Double {
        val lat = toRadians(latDeg)
        val dLon = toRadians(lonDeg) - lon0
        val cp = Math.cos(lat)
        val sp = Math.sin(lat)
        val cosDLon = Math.cos(dLon)
        out[0] = cp * Math.sin(dLon)
        out[1] = cosLat * sp - sinLat * cp * cosDLon
        return sinLat * sp + cosLat * cp * cosDLon
    }

    /**
     * Same as [project] but in screen pixels, or null when the point is behind the limb.
     * Allocates, so only the cold paths (arcs, markers) use it.
     */
    fun screen(
        latDeg: Double,
        lonDeg: Double,
        centreX: Float,
        centreY: Float,
        radiusPx: Float,
    ): DoubleArray? {
        val out = DoubleArray(2)
        if (project(latDeg, lonDeg, out) <= 0.0) return null
        out[0] = centreX + out[0] * radiusPx
        out[1] = centreY - out[1] * radiusPx
        return out
    }
}
