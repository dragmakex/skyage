package com.skyage.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skyage.app.UiState
import com.skyage.app.data.Airport
import com.skyage.app.data.FlightPhase
import com.skyage.app.data.FlightView
import com.skyage.app.data.offRoute
import com.skyage.app.geo.GlobeCamera
import com.skyage.app.ui.theme.Sky
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main screen: the whole world behind, followed flights in a glass sheet over it. The sheet peeks
 * at [SHEET_PEEK] and can be dragged up for the full list; it never hides completely, so the map
 * always stays the background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    airports: List<Airport>,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onVoyage: () -> Unit,
    onRefresh: () -> Unit,
) {
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    // The globe is the haze source: the sheet and the pill blur it for real.
    val hazeState = rememberHazeState()
    val camera = rememberSaveable(stateSaver = CameraSaver) { mutableStateOf(GlobeCamera()) }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        BottomSheetScaffold(
            scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState),
            containerColor = Sky.Night,
            sheetPeekHeight = SHEET_PEEK,
            sheetContainerColor = Color.Transparent,
            sheetDragHandle = null,
            sheetShadowElevation = 0.dp,
            sheetTonalElevation = 0.dp,
            sheetSwipeEnabled = true,
            sheetContent = { FlightsSheet(state, onAdd, onOpen, onVoyage) },
        ) { _ ->
            // Inner padding is ignored on purpose: the map runs edge to edge and under the sheet.
            Box(Modifier.fillMaxSize()) {
                FlightGlobe(
                    airports = airports,
                    views = state.views,
                    camera = camera,
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusPill(state = state, onRefresh = onRefresh)
                    // A rate limit or outage is the usual reason a followed flight suddenly has
                    // no live data, and it must be readable while the sheet still peeks. Once
                    // the sheet is expanded it covers this spot, so the list repeats it there.
                    if (sheetState.currentValue != SheetValue.Expanded) {
                        state.message?.let { msg -> Banner(msg) }
                    }
                }
            }
        }
    }
}

/** Survives rotation: zoom, centre longitude, centre latitude. */
private val CameraSaver = listSaver<GlobeCamera, Any>(
    save = { listOf(it.zoom, it.lonDeg, it.latDeg) },
    restore = { GlobeCamera(it[1] as Double, it[2] as Double, it[0] as Float) },
)

// Just the grabber + "Flights" header: the map stays the subject until the user drags.
private val SHEET_PEEK = 100.dp

@Composable
private fun StatusPill(state: UiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .liquidGlass(RoundedCornerShape(28.dp), GlassMaterial.Clear, blur = 24.dp)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Skyage",
                style = MaterialTheme.typography.titleLarge,
                color = Sky.Label,
            )
            Text(
                text = state.lastUpdate?.let {
                    "Updated ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))}"
                } ?: if (state.refreshing) "Updating…" else "Live traffic",
                style = MaterialTheme.typography.bodyMedium,
                color = Sky.SecondaryLabel,
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = if (state.refreshing) Sky.TertiaryLabel else Sky.SecondaryLabel,
            )
        }
    }
}

@Composable
private fun FlightsSheet(
    state: UiState,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onVoyage: () -> Unit,
) {
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    GlassPanel(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f),
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        material = GlassMaterial.Regular,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp),
        blur = 40.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(40.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(Color(0x4DFFFFFF))
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Flights", style = MaterialTheme.typography.displaySmall, color = Sky.Label)
                if (state.views.isNotEmpty()) {
                    Text(
                        text = "${state.views.size} tracked",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sky.SecondaryLabel,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassIconButton(
                    icon = Icons.Default.Luggage,
                    contentDescription = "Voyage",
                    onClick = onVoyage,
                    size = 44.dp,
                )
                GlassIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Follow a flight",
                    onClick = onAdd,
                    size = 44.dp,
                )
            }
        }

        // Shown here too: the expanded sheet covers the pill row where the same message
        // normally appears, and the expanded sheet is exactly where a stale flight is read.
        state.message?.let { msg -> Banner(msg, Modifier.padding(bottom = 12.dp)) }

        when {
            !state.ready -> Spacer(Modifier.fillMaxWidth().weight(1f))

            state.views.isEmpty() -> CenteredMessage(
                title = "No flights yet",
                body = "Tap + and enter a flight number like UA123.",
                modifier = Modifier.weight(1f),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = navBar + 24.dp),
            ) {
                items(state.views, key = { it.flight.key }) { view ->
                    FlightCard(view, onClick = { onOpen(view.flight.key) })
                }
            }
        }
    }
}

@Composable
internal fun Banner(message: String, modifier: Modifier = Modifier) {
    SurfaceCard(modifier = modifier.fillMaxWidth()) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Sky.Warn)
    }
}

@Composable
private fun FlightCard(view: FlightView, onClick: () -> Unit) {
    SurfaceCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhaseBadge(view.phase)
                Spacer(Modifier.weight(1f))
                AirlineBadge(view.flight.airlineIata, size = 22.dp)
                Text(
                    text = view.flight.number,
                    style = MaterialTheme.typography.titleMedium,
                    color = Sky.SecondaryLabel,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            RouteHeader(
                origin = view.origin,
                originCode = view.flight.originIata,
                dest = view.dest,
                destCode = view.flight.destIata,
                compact = true,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = listOfNotNull(view.flight.airlineName, view.origin?.city, view.dest?.city)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = Sky.SecondaryLabel,
            )

            if (view.progress != null) {
                Spacer(Modifier.height(12.dp))
                ProgressRail(view.progress)
                Spacer(Modifier.height(8.dp))
            } else {
                Spacer(Modifier.height(10.dp))
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        view.progress != null -> "${(view.progress * 100).toInt()}%"
                        isOffRoute(view) -> "Aircraft off route"
                        else -> routeSummary(view)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Sky.TertiaryLabel,
                )
                Spacer(Modifier.weight(1f))
                view.live?.let { live ->
                    Text(
                        text = liveSummary(view),
                        style = MaterialTheme.typography.labelLarge,
                        color = Sky.Label,
                    )
                }
            }
        }
    }
}

internal fun routeSummary(view: FlightView): String =
    view.routeKm?.let { "%,d km".format(it.toInt()) } ?: "Route unknown"

/**
 * Only a live fix can be off the route it was filed on. A position cached from an earlier day, or
 * one that stopped arriving, is a missing aircraft rather than a mis-matched one, and the NO SIGNAL
 * badge already says so; calling it "off route" would blame the route for a lost signal.
 */
internal fun isOffRoute(view: FlightView): Boolean =
    view.live != null && offRoute(view.routeKm, view.offRouteKm, view.viaKm)

internal fun liveSummary(view: FlightView): String {
    val live = view.live ?: return ""
    val alt = live.baroAltitudeM
    val speed = live.velocityMs
    return when {
        view.phase == FlightPhase.ON_GROUND -> "Ground"
        alt != null && speed != null -> "%,d ft · %,d km/h".format(
            (alt * 3.28084).toInt(),
            (speed * 3.6).toInt(),
        )
        alt != null -> "%,d ft".format((alt * 3.28084).toInt())
        else -> "Airborne"
    }
}
