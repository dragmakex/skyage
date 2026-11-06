package com.skyage.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import com.skyage.app.data.Airport
import com.skyage.app.data.LiveState
import com.skyage.app.data.OFF_ROUTE_THRESHOLD_KM
import com.skyage.app.geo.crossTrackKm
import com.skyage.app.geo.interpolateGreatCircle
import com.skyage.app.geo.mercatorY
import com.skyage.app.geo.progressAlongRoute
import com.skyage.app.geo.toRadians
import com.skyage.app.ui.theme.Sky
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Self-contained route card: Web-Mercator, auto-fitted to the route, drawing the recovered
 * airport dataset as the landmass reference so no tile server or API key is needed.
 */
@Composable
fun FlightMap(
    airports: List<Airport>,
    origin: Airport?,
    dest: Airport?,
    live: LiveState?,
    modifier: Modifier = Modifier,
) {
    val dust = remember(airports) { Dust(airports.size) }
    Canvas(modifier = modifier) {
        drawRect(color = Color(0xFF070C18))

        val lat = live?.lat
        val lon = live?.lon
        val crossesAntimeridian =
            origin != null && dest != null && abs(origin.lon - dest.lon) > 180
        val shift = if (crossesAntimeridian) 360.0 else 0.0

        // Only treat the live position as part of this route when it is plausibly on it. A callsign
        // is not unique per route, so an off-route aircraft must not stretch the viewport.
        val onRoute = if (origin != null && dest != null && lat != null && lon != null) {
            crossTrackKm(origin.lat, origin.lon, dest.lat, dest.lon, lat, lon) <=
                OFF_ROUTE_THRESHOLD_KM
        } else true

        val points = ArrayList<Pair<Double, Double>>(3)
        origin?.let { points.add(it.lon to it.lat) }
        dest?.let { points.add(it.lon to it.lat) }
        if (lat != null && lon != null && onRoute) points.add(lon to lat)

        val proj = Projection.build(points, size.width, size.height, shift)

        drawGraticule(proj)
        drawAirportDust(airports, proj, dust)

        if (origin != null && dest != null) {
            drawRoute(origin, dest, proj, if (onRoute) live else null)
        }

        origin?.let { drawAirportMarker(it, proj, Sky.Label) }
        dest?.let { drawAirportMarker(it, proj, Sky.Accent) }

        if (onRoute && lat != null && lon != null) drawAircraft(lon, lat, live.headingDeg, proj)
    }
}

private fun DrawScope.drawGraticule(proj: Projection, line: Color = Color(0x14FFFFFF)) {
    val step = when {
        proj.lonSpan > 120 -> 30
        proj.lonSpan > 45 -> 15
        proj.lonSpan > 15 -> 5
        else -> 1
    }
    var lon = -180
    while (lon <= 180) {
        val x = proj.x(lon.toDouble())
        if (x > -2f && x < size.width + 2f) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        }
        lon += step
    }
    var lat = -80
    while (lat <= 80) {
        val y = proj.y(lat.toDouble())
        if (y > -2f && y < size.height + 2f) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        lat += step
    }
}

/**
 * The dot grid's reusable buffers: packed x,y pairs plus the paint. Compose dropped the raw
 * FloatArray `drawPoints` overload in 1.8, and a `List<Offset>` would allocate thousands of
 * objects on every frame, so this goes straight to the platform canvas.
 */
private class Dust(airportCount: Int) {
    val xy = FloatArray(airportCount * 6)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x734C8DFF
        strokeWidth = 2f
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
}

/**
 * Every airport in the dataset as one faint dot: one native call; the ±360° copies keep the grid
 * continuous across the antimeridian.
 */
private fun DrawScope.drawAirportDust(airports: List<Airport>, proj: Projection, dust: Dust) {
    var i = 0
    for (copy in -360..360 step 360) {
        for (a in airports) {
            dust.xy[i] = proj.x(a.lon + copy)
            dust.xy[i + 1] = proj.y(a.lat)
            i += 2
        }
    }
    // The dataset is the only landmass reference on this map, so keep it clearly visible.
    drawContext.canvas.nativeCanvas.drawPoints(dust.xy, dust.paint)
}

private fun DrawScope.drawRoute(
    origin: Airport,
    dest: Airport,
    proj: Projection,
    live: LiveState?,
) {
    val samples = 96
    val pts = (0..samples).map { i ->
        val (la, lo) = interpolateGreatCircle(
            origin.lat, origin.lon, dest.lat, dest.lon, i / samples.toDouble()
        )
        Offset(proj.x(lo), proj.y(la))
    }

    fun buildPath(subset: List<Offset>): Path = Path().apply {
        subset.forEachIndexed { index, p ->
            if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
    }

    drawPath(
        buildPath(pts),
        color = Color(0x66FFFFFF),
        style = Stroke(
            width = 2.5f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        ),
    )

    val liveLat = live?.lat
    val liveLon = live?.lon
    if (liveLat != null && liveLon != null) {
        val progress = progressAlongRoute(
            origin.lat, origin.lon, dest.lat, dest.lon, liveLat, liveLon
        )
        if (progress != null && progress > 0.001) {
            val upTo = (progress * samples).roundToInt().coerceIn(1, samples)
            drawPath(
                buildPath(pts.subList(0, upTo + 1)),
                color = Sky.Accent,
                style = Stroke(width = 3f, cap = StrokeCap.Round),
            )
        }
    }
}

private fun DrawScope.drawAirportMarker(airport: Airport, proj: Projection, color: Color) {
    val center = Offset(proj.x(airport.lon), proj.y(airport.lat))
    drawCircle(Color(0x33FFFFFF), radius = 9f, center = center)
    drawCircle(color, radius = 4f, center = center)

    val label = airport.iata ?: airport.icao ?: return
    val textPx = 11.sp.toPx()
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textSize = textPx
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    drawContext.canvas.nativeCanvas.drawText(label, center.x, center.y - 14f, paint)
}

private fun DrawScope.drawAircraft(lon: Double, lat: Double, heading: Double?, proj: Projection) {
    val center = Offset(proj.x(lon), proj.y(lat))
    drawCircle(Color(0x224C8DFF), radius = 26f, center = center)
    drawCircle(Color(0x444C8DFF), radius = 16f, center = center)

    rotate(degrees = (heading ?: 0.0).toFloat(), pivot = center) {
        val plane = Path().apply {
            moveTo(center.x, center.y - 11f)
            lineTo(center.x + 7.5f, center.y + 9f)
            lineTo(center.x, center.y + 4.5f)
            lineTo(center.x - 7.5f, center.y + 9f)
            close()
        }
        drawPath(plane, color = Sky.Label)
    }
}

/** Fits Mercator coordinates into a canvas, preserving aspect ratio. */
private class Projection(
    private val scale: Double,
    private val centerX: Double,
    private val centerY: Double,
    private val w: Float,
    private val h: Float,
    private val shiftDeg: Double,
) {
    val lonSpan: Double = if (scale > 0) Math.toDegrees(w / scale) else 360.0

    /** Longitudes east of the antimeridian are folded back so a route stays contiguous. */
    private fun shift(lonDeg: Double): Double =
        if (shiftDeg != 0.0 && lonDeg < 0) lonDeg + shiftDeg else lonDeg

    fun x(lonDeg: Double): Float = ((toRadians(shift(lonDeg)) - centerX) * scale + w / 2).toFloat()

    fun y(latDeg: Double): Float = ((centerY - mercatorY(latDeg)) * scale + h / 2).toFloat()

    companion object {
        fun build(points: List<Pair<Double, Double>>, w: Float, h: Float, shiftDeg: Double): Projection {
            if (points.isEmpty() || w <= 0f || h <= 0f) {
                return Projection(
                    scale = min(w / (2 * Math.PI), h / 3.4),
                    centerX = 0.0,
                    centerY = (mercatorY(50.0) + mercatorY(-40.0)) / 2,
                    w = w,
                    h = h,
                    shiftDeg = 0.0,
                )
            }

            val xs = points.map { lon ->
                toRadians(if (shiftDeg != 0.0 && lon.first < 0) lon.first + shiftDeg else lon.first)
            }
            val ys = points.map { mercatorY(it.second) }
            var minX = xs.min()
            var maxX = xs.max()
            var minY = ys.min()
            var maxY = ys.max()

            if (maxX - minX < 0.02) { minX -= 0.01; maxX += 0.01 }
            if (maxY - minY < 0.02) { minY -= 0.01; maxY += 0.01 }

            val scale = min(w / (maxX - minX), h / (maxY - minY)) * 0.72

            return Projection(
                scale = scale,
                centerX = (minX + maxX) / 2,
                centerY = (minY + maxY) / 2,
                w = w,
                h = h,
                shiftDeg = shiftDeg,
            )
        }
    }
}
