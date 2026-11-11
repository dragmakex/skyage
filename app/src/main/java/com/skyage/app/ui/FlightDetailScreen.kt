package com.skyage.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.skyage.app.data.Airport
import com.skyage.app.data.FlightPhase
import com.skyage.app.data.LiveState
import com.skyage.app.data.FlightView
import com.skyage.app.ui.isOffRoute
import com.skyage.app.ui.theme.Sky
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun FlightDetailScreen(
    view: FlightView,
    airports: List<Airport>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassScaffold(
        topBar = {
            GlassTopBar(
                title = view.flight.number,
                onBack = onBack,
            ) {
                GlassIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    onClick = onRefresh,
                    size = 44.dp,
                )
                GlassIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "Stop following",
                    onClick = onDelete,
                    size = 44.dp,
                )
            }
        },
    ) { padding ->
        // A fixed 260 dp map fills a landscape window on its own, leaving the phase and the numbers
        // below the fold with nothing on screen to suggest they are there.
        val mapHeight =
            if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 150.dp
            else 260.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            FlightMap(
                airports = airports,
                origin = view.origin,
                dest = view.dest,
                live = view.live,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mapHeight)
                    .clip(RoundedCornerShape(18.dp)),
            )

            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PhaseBadge(view.phase)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AirlineBadge(view.flight.airlineIata, size = 20.dp)
                    Text(
                        text = view.flight.airlineName,
                        style = MaterialTheme.typography.labelLarge,
                        color = Sky.SecondaryLabel,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            RouteHeader(
                origin = view.origin,
                originCode = view.flight.originIata,
                dest = view.dest,
                destCode = view.flight.destIata,
            )

            val cities = listOfNotNull(view.origin?.city, view.dest?.city).joinToString("  →  ")
            if (cities.isNotEmpty()) {
                Text(
                    text = cities,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Sky.SecondaryLabel,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            if (view.progress != null) {
                ProgressRail(view.progress)
                Spacer(Modifier.height(12.dp))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (view.progress == null) {
                    Arrangement.End
                } else {
                    Arrangement.SpaceBetween
                },
            ) {
                view.progress?.let {
                    Text(
                        text = "${(it * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = Sky.Accent,
                    )
                }
                Text(
                    text = view.remainingKm?.let { "%,d km to go".format(it.toInt()) } ?: routeSummary(view),
                    style = MaterialTheme.typography.labelLarge,
                    color = Sky.SecondaryLabel,
                )
            }

            Spacer(Modifier.height(20.dp))

            val live = view.live
            if (live != null) {
                val stats = liveStats(live)
                if (stats.isNotEmpty()) {
                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            stats.chunked(2).forEachIndexed { index, row ->
                                if (index > 0) Spacer(Modifier.height(16.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    row.forEach { (label, value) ->
                                        StatCell(
                                            label = label,
                                            value = value,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isOffRoute(view)) {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "The aircraft showing as %,d km off this route is most likely "
                            .format(view.offRouteKm?.toInt() ?: 0) +
                            "another flight with the same number.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sky.Warn,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            if (view.phase == FlightPhase.LANDED) {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (view.live != null) {
                            "On the ground at ${view.dest?.code ?: "its destination"}."
                        } else {
                            "Landed at ${view.dest?.code ?: "its destination"}." +
                                (view.lastSeenAt?.let { " Last picked up ${heardWhen(it)}." } ?: "")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sky.SecondaryLabel,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            if (view.phase == FlightPhase.NO_DATA) {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = noSignalCopy(
                            time = view.lastSeenAt?.let { heardWhen(it) },
                            remainingKm = view.remainingKm,
                            destCode = view.dest?.code,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sky.SecondaryLabel,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * What "no signal" actually means, in the three cases the app can tell apart: never seen, seen
 * but gone from the feed without knowing where, and seen a known distance short of the airport.
 */
internal fun noSignalCopy(time: String?, remainingKm: Double?, destCode: String?): String = when {
    time != null && destCode != null && remainingKm != null -> {
        val distance = "%,d".format(remainingKm.toInt())
        "Last heard $time, $distance km from $destCode. Nothing since: it may have landed " +
            "elsewhere, or flown out of receiver coverage."
    }
    time != null ->
        "Last heard $time, and nothing since. It may have landed, or flown out of receiver " +
            "coverage."
    else -> "Not picked up yet. It may not have left, or it is out of receiver coverage."
}

/**
 * When the aircraft was last heard, phrased to follow "Last heard". A fix cached on an earlier day
 * also
 * says which day: the cache outlives the process, so "13:51Z" on its own would read as a fix
 * from this afternoon when it is in fact from yesterday.
 */
internal fun heardWhen(epochSeconds: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val time = utcTime(epochSeconds)
    return when (utcDay(epochSeconds * 1_000)) {
        utcDay(nowMillis) -> "at $time"
        utcDay(nowMillis) - 1 -> "yesterday at $time"
        else -> "on ${utcDate(epochSeconds * 1_000)} at $time"
    }
}

private fun utcDay(millis: Long): Long = millis / 86_400_000L

private fun utcDate(millis: Long): String {
    val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date(millis))
}

/**
 * The numbers worth showing for a live fix, in reading order. A value the feed did not report is
 * left out rather than printed as a dash: an on-ground aircraft has no altitude and no vertical
 * rate, and two dashes say less than the two figures that are actually known.
 */
internal fun liveStats(live: LiveState): List<Pair<String, String>> = listOfNotNull(
    live.baroAltitudeM?.let { "Altitude" to "%,d ft".format((it * 3.28084).toInt()) },
    live.velocityMs?.let { "Ground speed" to "%,d km/h".format((it * 3.6).toInt()) },
    live.headingDeg?.let { "Heading" to "%03d°".format(it.toInt()) },
    live.verticalRateMs?.let { "Vertical rate" to "%+,.0f ft/min".format(it * 196.85) },
)

/** Ground receivers report in UTC; the phone shows local time, so say which. */
private fun utcTime(epochSeconds: Long): String {
    val fmt = SimpleDateFormat("HH:mm'Z'", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date(epochSeconds * 1000))
}
