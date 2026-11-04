package com.skyage.app.geo

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

const val EARTH_RADIUS_KM = 6371.0088

fun toRadians(deg: Double) = deg * Math.PI / 180.0
fun toDegrees(rad: Double) = rad * 180.0 / Math.PI

/** Great-circle distance in km. */
fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = toRadians(lat1)
    val p2 = toRadians(lat2)
    val dp = p2 - p1
    val dl = toRadians(lon2 - lon1)
    val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/** Initial bearing A->B in degrees clockwise from true north. */
fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = toRadians(lat1)
    val p2 = toRadians(lat2)
    val dl = toRadians(lon2 - lon1)
    val y = sin(dl) * cos(p2)
    val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
    return (toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/**
 * Perpendicular distance from P to the A->B great circle, in km. Used to reject matches whose
 * position cannot plausibly belong to the route: a callsign is not unique per route, so the live
 * aircraft we matched may be a different flight entirely.
 */
fun crossTrackKm(
    latA: Double, lonA: Double,
    latB: Double, lonB: Double,
    latP: Double, lonP: Double,
): Double {
    val d13 = angularDistance(latA, lonA, latP, lonP)
    val t13 = toRadians(bearingDeg(latA, lonA, latP, lonP))
    val t12 = toRadians(bearingDeg(latA, lonA, latB, lonB))
    val xtd = asin((sin(d13) * sin(t13 - t12)).coerceIn(-1.0, 1.0))
    return abs(xtd) * EARTH_RADIUS_KM
}

/**
 * Fraction of the way from A to B at the point of the great circle closest to P, in 0..1.
 * Returns null when the track cannot be computed.
 */
fun progressAlongRoute(
    latA: Double, lonA: Double,
    latB: Double, lonB: Double,
    latP: Double, lonP: Double,
): Double? {
    val d12 = angularDistance(latA, lonA, latB, lonB)
    if (d12 < 1e-9) return null
    val d13 = angularDistance(latA, lonA, latP, lonP)
    val t13 = toRadians(bearingDeg(latA, lonA, latP, lonP))
    val t12 = toRadians(bearingDeg(latA, lonA, latB, lonB))
    // cross-track distance, then along-track distance from it
    val xtd = asin((sin(d13) * sin(t13 - t12)).coerceIn(-1.0, 1.0))
    val denominator = cos(xtd)
    if (abs(denominator) < 1e-9) return null
    val atd = acos((cos(d13) / denominator).coerceIn(-1.0, 1.0))
    val signed = if (cos(t13 - t12) < 0) -atd else atd
    return (signed / d12).coerceIn(0.0, 1.0)
}

private fun angularDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
    distanceKm(lat1, lon1, lat2, lon2) / EARTH_RADIUS_KM

/** Point at fraction [f] of the great circle from A to B (spherical interpolation). */
fun interpolateGreatCircle(
    latA: Double, lonA: Double,
    latB: Double, lonB: Double,
    f: Double,
): Pair<Double, Double> {
    val d = angularDistance(latA, lonA, latB, lonB)
    if (d < 1e-9) return latA to lonA
    val a = toRadians(latA)
    val b = toRadians(latB)
    val la = toRadians(lonA)
    val lb = toRadians(lonB)
    val sinD = sin(d)
    val w1 = sin((1 - f) * d) / sinD
    val w2 = sin(f * d) / sinD
    val x = w1 * cos(a) * cos(la) + w2 * cos(b) * cos(lb)
    val y = w1 * cos(a) * sin(la) + w2 * cos(b) * sin(lb)
    val z = w1 * sin(a) + w2 * sin(b)
    return toDegrees(atan2(z, sqrt(x * x + y * y))) to toDegrees(atan2(y, x))
}

/** Web-Mercator Y, normalised to a unit-ish range. Only used for drawing. */
fun mercatorY(latDeg: Double): Double {
    val clamped = latDeg.coerceIn(-85.0, 85.0)
    return Math.log(Math.tan(Math.PI / 4 + toRadians(clamped) / 2))
}

/** Folds a longitude back into (-180, 180]. */
fun normalizeLon(lon: Double): Double {
    val wrapped = (lon + 180.0).mod(360.0) - 180.0
    return if (wrapped <= -180.0) 180.0 else wrapped
}
