package com.skyage.app.ui

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skyage.app.data.Airport
import com.skyage.app.data.FlightPhase
import com.skyage.app.data.FlightView
import com.skyage.app.ui.isOffRoute
import com.skyage.app.ui.theme.Sky
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightDetailScreen(
    view: FlightView,
    airports: List<Airport>,
    refreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassScaffold(
        topBar = {
            TopAppBar(
                title = { Text(view.flight.number, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        size = 40.dp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp).height(20.dp),
                            strokeWidth = 2.dp,
                            color = Sky.Pro,
                        )
                    } else {
                        GlassIconButton(
                            icon = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            onClick = onRefresh,
                            size = 40.dp,
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.padding(end = 4.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Stop following", tint = Sky.SecondaryLabel)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
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
                    .height(260.dp)
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

            Text(
                text = listOfNotNull(view.origin?.city, view.dest?.city).joinToString("  →  "),
                style = MaterialTheme.typography.bodyMedium,
                color = Sky.SecondaryLabel,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(16.dp))
            ProgressRail(view.progress)
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = view.progress?.let { "${(it * 100).toInt()}% complete" } ?: "Progress unavailable",
                    style = MaterialTheme.typography.labelLarge,
                    color = Sky.Pro,
                )
                Text(
                    text = view.remainingKm?.let { "%,d km to go".format(it.toInt()) } ?: routeSummary(view),
                    style = MaterialTheme.typography.labelLarge,
                    color = Sky.SecondaryLabel,
                )
            }

            Spacer(Modifier.height(20.dp))

            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        StatCell(
                            label = "Altitude",
                            value = view.live?.baroAltitudeM?.let { "%,d ft".format((it * 3.28084).toInt()) } ?: "—",
                            modifier = Modifier.weight(1f),
                        )
                        StatCell(
                            label = "Ground speed",
                            value = view.live?.velocityMs?.let { "%,d km/h".format((it * 3.6).toInt()) } ?: "—",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        StatCell(
                            label = "Heading",
                            value = view.live?.headingDeg?.let { "%03d°".format(it.toInt()) } ?: "—",
                            modifier = Modifier.weight(1f),
                        )
                        StatCell(
                            label = "Vertical rate",
                            value = view.live?.verticalRateMs?.let {
                                val fpm = it * 196.85
                                "%+,.0f ft/min".format(fpm)
                            } ?: "—",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    StatCell(
                        label = "Position",
                        value = if (view.live?.lat != null && view.live.lon != null) {
                            "%.3f, %.3f".format(view.live.lat, view.live.lon)
                        } else "Not reported",
                        sub = null,
                    )
                    Spacer(Modifier.height(14.dp))
                    StatCell(
                        label = "Transponder",
                        value = view.live?.icao24?.uppercase() ?: "—",
                        sub = view.live?.let {
                            "${it.originCountry} · last seen ${utcTime(it.lastContact)}"
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    StatCell(
                        label = "Callsign queried",
                        value = view.flight.callsign,
                        sub = "OpenSky callsign lookup from the bundled IATA→ICAO airline map",
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isOffRoute(view)) {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "The aircraft broadcasting this callsign is %,d km off the "
                            .format(view.offRouteKm?.toInt() ?: 0) +
                            "${view.origin?.code ?: "origin"}–${view.dest?.code ?: "destination"} " +
                            "route, so it is most likely a different flight using the same " +
                            "callsign. Positions and progress are not attributed to this route.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sky.Warn,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            if (view.phase == FlightPhase.NO_DATA) {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No aircraft is currently broadcasting this callsign. The flight may " +
                            "not have departed, may have already landed, or may be outside " +
                            "ground-receiver coverage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sky.SecondaryLabel,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun utcTime(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "unknown"
    val fmt = SimpleDateFormat("HH:mm:ss'Z'", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date(epochSeconds * 1000))
}
