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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
                        color = Sky.Pro,
                    )
                }
                Text(
                    text = view.remainingKm?.let { "%,d km to go".format(it.toInt()) } ?: routeSummary(view),
                    style = MaterialTheme.typography.labelLarge,
                    color = Sky.SecondaryLabel,
                )
            }

            Spacer(Modifier.height(20.dp))

            if (view.live != null) {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            StatCell(
                                label = "Altitude",
                                value = view.live.baroAltitudeM?.let {
                                    "%,d ft".format((it * 3.28084).toInt())
                                } ?: "—",
                                modifier = Modifier.weight(1f),
                            )
                            StatCell(
                                label = "Ground speed",
                                value = view.live.velocityMs?.let {
                                    "%,d km/h".format((it * 3.6).toInt())
                                } ?: "—",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth()) {
                            StatCell(
                                label = "Heading",
                                value = view.live.headingDeg?.let { "%03d°".format(it.toInt()) } ?: "—",
                                modifier = Modifier.weight(1f),
                            )
                            StatCell(
                                label = "Vertical rate",
                                value = view.live.verticalRateMs?.let {
                                    "%+,.0f ft/min".format(it * 196.85)
                                } ?: "—",
                                modifier = Modifier.weight(1f),
                            )
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
