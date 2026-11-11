package com.skyage.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.skyage.app.data.Airport
import com.skyage.app.data.FlightView
import com.skyage.app.data.LiveState
import com.skyage.app.data.offRoute
import com.skyage.app.geo.GlobeCamera
import com.skyage.app.geo.GlobeView
import com.skyage.app.geo.interpolateGreatCircle
import com.skyage.app.geo.progressAlongRoute
import com.skyage.app.ui.theme.Sky
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt

private val NightSky = Sky.Night

/** The globe fits the narrow side of the screen, so the whole disc is always on screen at min zoom. */
private fun globeRadius(widthPx: Float, heightPx: Float, zoom: Float): Float =
    min(widthPx, heightPx) * 0.46f * zoom

/** Centre of the disc: slightly above the middle, because the sheet peeks in from the bottom. */
private fun globeCentreY(heightPx: Float): Float = heightPx * 0.46f

/**
 * The whole world as a hand-drawn globe: an orthographic sphere with the airport dataset as a dot
 * grid, followed routes as great-circle arcs and live aircraft where they really are.
 *
 * Drag to turn the globe, pinch or double-tap to zoom. Only the visible hemisphere is drawn, so the
 * far side costs nothing but a cosine.
 */
@Composable
fun FlightGlobe(
    airports: List<Airport>,
    views: List<FlightView>,
    camera: MutableState<GlobeCamera>,
    modifier: Modifier = Modifier,
) {
    val dust = remember(airports) { GlobeDust(airports.size) }
    val labelPx = with(LocalDensity.current) { 11.sp.toPx() }
    val labels = remember(labelPx) { GlobeLabels(labelPx) }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                // Written straight into the state: a recomposition can lag a slow frame behind the
                // gesture, and a stale base would drop most of the drag.
                detectTransformGestures { _, pan, zoom, _ ->
                    val radius = globeRadius(
                        size.width.toFloat(),
                        size.height.toFloat(),
                        camera.value.zoom,
                    )
                    camera.value = camera.value.dragged(pan.x, pan.y, radius).zoomed(zoom)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    val c = camera.value
                    camera.value = if (c.zoom >= GlobeCamera.RESET_ZOOM) GlobeCamera()
                    else c.zoomed(2f)
                })
            },
    ) {
        drawRect(NightSky)
        drawAurora()

        val cam = camera.value
        val radius = globeRadius(size.width, size.height, cam.zoom)
        val centre = Offset(size.width / 2f, globeCentreY(size.height))
        val globe = GlobeView(cam.lonDeg, cam.latDeg)

        drawAtmosphere(centre, radius)
        drawGraticule(globe, centre, radius)
        drawAirportDust(airports, globe, centre, radius, dust)
        // Dims the far half of the disc after the grid: it is what makes the rim read as a curve.
        drawLimbShade(centre, radius)

        labels.reset()
        for (flight in views) {
            val origin = flight.origin ?: continue
            val dest = flight.dest ?: continue
            val onRoute = !offRoute(flight.routeKm, flight.offRouteKm, flight.viaKm)
            drawRoute(origin, dest, globe, if (onRoute) flight.live else null, centre, radius)
            place(origin.lat, origin.lon, globe, centre, radius)
                ?.let { drawMarker(it, origin, Sky.Label, labels) }
            place(dest.lat, dest.lon, globe, centre, radius)
                ?.let { drawMarker(it, dest, Sky.Accent, labels) }

            val live = flight.live
            if (onRoute && live != null) {
                drawAircraft(live, globe, centre, radius)
            }
        }
    }
}

private fun DrawScope.drawAurora() {
    val violet = size.width * 0.16f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x551D4ED8), Color.Transparent),
            center = Offset(size.width * 0.16f, size.height * 0.10f),
            radius = violet * 2.2f,
        ),
        radius = violet * 2.2f,
        center = Offset(size.width * 0.16f, size.height * 0.10f),
    )
    val blue = size.width * 0.5f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x440F3A7A), Color.Transparent),
            center = Offset(size.width * 0.9f, size.height * 0.46f),
            radius = blue,
        ),
        radius = blue,
        center = Offset(size.width * 0.9f, size.height * 0.46f),
    )
}

private fun DrawScope.drawAtmosphere(centre: Offset, radius: Float) {
    // Thin halo just outside the limb.
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.78f to Color.Transparent,
                0.90f to Color(0x3D2E6BE6),
                1f to Color.Transparent,
            ),
            center = centre,
            radius = radius * 1.16f,
        ),
        radius = radius * 1.16f,
        center = centre,
    )
    // Ocean: lit from the upper left, falling off to the night side at the rim.
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFF0C1B33),
                0.55f to Color(0xFF081222),
                1f to Color(0xFF05080F),
            ),
            center = Offset(centre.x - radius * 0.35f, centre.y - radius * 0.38f),
            radius = radius * 1.75f,
        ),
        radius = radius,
        center = centre,
    )
    drawCircle(Color(0x594C8DFF), radius = radius, center = centre, style = Stroke(1.5f))
}

private fun DrawScope.drawLimbShade(centre: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(0.62f to Color.Transparent, 1f to Color(0xB304070F)),
            center = centre,
            radius = radius,
        ),
        radius = radius,
        center = centre,
    )
}

/**
 * Traces a curve over the sphere, cutting the path where it dives behind the limb. `point` writes
 * the projected offset into `out` and returns its depth; inline so no lambdas are allocated.
 */
private inline fun traceGlobePath(
    samples: Int,
    centre: Offset,
    radius: Float,
    point: (Int, DoubleArray) -> Double,
): Path {
    val path = Path()
    val out = DoubleArray(2)
    var open = false
    for (i in 0..samples) {
        if (point(i, out) <= 0.0) {
            open = false
            continue
        }
        val x = centre.x + out[0].toFloat() * radius
        val y = centre.y - out[1].toFloat() * radius
        if (open) path.lineTo(x, y) else path.moveTo(x, y)
        open = true
    }
    return path
}

private fun DrawScope.drawGraticule(globe: GlobeView, centre: Offset, radius: Float) {
    val line = Color(0x26FFFFFF)
    var lat = -60
    while (lat <= 60) {
        val parallel = lat.toDouble()
        drawPath(
            traceGlobePath(120, centre, radius) { i, out ->
                globe.project(parallel, -180.0 + i * 3.0, out)
            },
            color = if (lat == 0) Color(0x3DFFFFFF) else line,
            style = Stroke(width = 1f),
        )
        lat += 30
    }
    var lon = -180
    while (lon < 180) {
        val meridian = lon.toDouble()
        drawPath(
            traceGlobePath(88, centre, radius) { i, out ->
                globe.project(-88.0 + i * 2.0, meridian, out)
            },
            color = line,
            style = Stroke(width = 1f),
        )
        lon += 30
    }
}

/**
 * The dot grid's reusable buffers: packed x,y pairs plus the paint and a projection scratch pair.
 * Compose 1.8 has no raw FloatArray `drawPoints`, so this goes straight to the platform canvas,
 * and one native call per frame is what keeps turning the globe cheap.
 */
private class GlobeDust(airportCount: Int) {
    val xy = FloatArray(airportCount * 2)
    val scratch = DoubleArray(2)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x734C8DFF
        strokeWidth = 2f
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
}

/** Every airport on the visible hemisphere: back-facing points are dropped before they are packed. */
private fun DrawScope.drawAirportDust(
    airports: List<Airport>,
    globe: GlobeView,
    centre: Offset,
    radius: Float,
    dust: GlobeDust,
) {
    var n = 0
    for (a in airports) {
        if (globe.project(a.lat, a.lon, dust.scratch) <= 0.0) continue
        dust.xy[n++] = centre.x + dust.scratch[0].toFloat() * radius
        dust.xy[n++] = centre.y - dust.scratch[1].toFloat() * radius
    }
    drawContext.canvas.nativeCanvas.drawPoints(dust.xy, 0, n, dust.paint)
}

private fun DrawScope.drawRoute(
    origin: Airport,
    dest: Airport,
    globe: GlobeView,
    live: LiveState?,
    centre: Offset,
    radius: Float,
) {
    val samples = 128
    fun arc(upTo: Int): Path = traceGlobePath(upTo, centre, radius) { i, out ->
        val (lat, lon) = interpolateGreatCircle(
            origin.lat, origin.lon, dest.lat, dest.lon, i / samples.toDouble()
        )
        globe.project(lat, lon, out)
    }

    drawPath(
        arc(samples),
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
            drawPath(arc(upTo), color = Sky.Accent, style = Stroke(width = 3f, cap = StrokeCap.Round))
        }
    }
}

/** Screen position of a geo point, or null when it is behind the globe. */
private fun place(
    lat: Double,
    lon: Double,
    globe: GlobeView,
    centre: Offset,
    radius: Float,
): Offset? = globe.screen(lat, lon, centre.x, centre.y, radius)
    ?.let { Offset(it[0].toFloat(), it[1].toFloat()) }

/**
 * The label's paint and placement state, reused every frame: one Paint instead of one per marker,
 * and the list of boxes already spoken for. Reset at the start of each frame.
 */
private class GlobeLabels(textSizePx: Float) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = textSizePx
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
        // A shadow under the glyphs keeps the code readable over the dot grid and the bright limb.
        setShadowLayer(textSizePx * 0.45f, 0f, textSizePx * 0.14f, 0xFF04070F.toInt())
    }
    val layout = LabelLayout()
    val seen = HashSet<String>(16)
    val out = FloatArray(2)

    fun reset() {
        layout.clear()
        seen.clear()
    }
}

private fun DrawScope.drawMarker(
    at: Offset,
    airport: Airport,
    colour: Color,
    labels: GlobeLabels,
) {
    drawCircle(Color(0x33FFFFFF), radius = 9f, center = at)
    drawCircle(colour, radius = 4f, center = at)

    val code = airport.iata ?: airport.icao ?: return
    // Two routes through the same hub are one label, not two on top of each other.
    if (!labels.seen.add(code)) return

    val paint = labels.paint
    paint.color = colour.toArgb()
    val width = paint.measureText(code)
    val metrics = paint.fontMetrics
    val height = metrics.descent - metrics.ascent
    val placed = labels.layout.place(
        x = at.x,
        y = at.y,
        w = width,
        h = height,
        gap = 13f,
        pad = 8f,
        boundsW = size.width,
        boundsH = size.height,
        out = labels.out,
    )
    if (!placed) return
    drawContext.canvas.nativeCanvas.drawText(
        code,
        labels.out[0] + width / 2f,
        labels.out[1] - metrics.ascent,
        paint,
    )
}

private fun DrawScope.drawAircraft(live: LiveState, globe: GlobeView, centre: Offset, radius: Float) {
    val lat = live.lat ?: return
    val lon = live.lon ?: return
    val at = place(lat, lon, globe, centre, radius) ?: return

    drawCircle(Color(0x224C8DFF), radius = 26f, center = at)
    drawCircle(Color(0x444C8DFF), radius = 16f, center = at)

    // Screen-up is not north away from the centre meridian, so borrow the local north direction.
    val north = globe.screen(
        (lat + 1.0).coerceAtMost(89.0), lon, centre.x, centre.y, radius
    )
    val northAngle = if (north == null) 0f else Math.toDegrees(
        atan2(at.y - north[1], north[0] - at.x)
    ).toFloat()

    rotate(degrees = (live.headingDeg ?: 0.0).toFloat() + northAngle, pivot = at) {
        val plane = Path().apply {
            moveTo(at.x, at.y - 11f)
            lineTo(at.x + 7.5f, at.y + 9f)
            lineTo(at.x, at.y + 4.5f)
            lineTo(at.x - 7.5f, at.y + 9f)
            close()
        }
        drawPath(plane, color = Sky.Label)
    }
}
