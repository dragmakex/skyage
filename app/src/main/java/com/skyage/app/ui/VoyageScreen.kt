package com.skyage.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.skyage.app.data.Airport
import com.skyage.app.data.Trip
import com.skyage.app.data.tripMonths
import com.skyage.app.data.tripYears
import com.skyage.app.data.tripsIn
import com.skyage.app.data.voyageStats
import com.skyage.app.ui.theme.Sky
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** Keeps a nullable Int filter across a rotation; the default saver cannot hold null. */
private val NullableIntSaver = Saver<Int?, Int>(save = { it ?: -1 }, restore = { if (it < 0) null else it })

/**
 * Where the user has been: every followed flight, counted by airport and country, filtered by the
 * year and month it was logged.
 */
@Composable
fun VoyageScreen(
    trips: List<Trip>,
    airports: List<Airport>,
    onBack: () -> Unit,
) {
    // rememberSaveable: turning the phone should not throw the filter away.
    var year by rememberSaveable(stateSaver = NullableIntSaver) { mutableStateOf<Int?>(null) }
    var month by rememberSaveable(stateSaver = NullableIntSaver) { mutableStateOf<Int?>(null) }

    val byCode = remember(airports) { airports.associateBy { it.code } }
    val years = remember(trips) { tripYears(trips) }
    val months = remember(trips, year) { tripMonths(trips, year) }
    // A month can disappear from the list when the year changes; drop the filter rather than show
    // an empty screen for a month that no longer has anything in it.
    val selectedMonth = month?.takeIf { it in months }

    val shown = remember(trips, year, selectedMonth) { tripsIn(trips, year, selectedMonth) }
    val stats = remember(shown, byCode) { voyageStats(shown) { byCode[it] } }

    GlassScaffold(topBar = { GlassTopBar(title = "Voyage", onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (trips.isEmpty()) {
                item {
                    CenteredMessage(
                        title = "Nothing logged yet",
                        body = "Every flight you follow is added here with its route.",
                        modifier = Modifier.padding(top = 48.dp),
                    )
                }
                return@LazyColumn
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterPill("All years", year == null) { year = null } }
                    items(years) { y -> FilterPill("$y", year == y) { year = y } }
                }
            }

            if (months.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterPill("All months", selectedMonth == null) { month = null } }
                        items(months) { m ->
                            FilterPill(m.monthShort(), selectedMonth == m) { month = m }
                        }
                    }
                }
            }

            item {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            StatCell(
                                label = "Flights",
                                value = "%d".format(stats.flights),
                                modifier = Modifier.weight(1f),
                            )
                            StatCell(
                                label = "Airports",
                                value = "%d".format(stats.airports),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth()) {
                            StatCell(
                                label = "Countries",
                                value = "%d".format(stats.countries),
                                modifier = Modifier.weight(1f),
                            )
                            StatCell(
                                label = "Distance",
                                value = "%,d km".format(stats.km.toInt()),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item {
                // The log only knows the day a flight was added, and it says so rather than
                // implying it knows the day the flight operated.
                Text(
                    text = "Dated by the day you started following each flight.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Sky.TertiaryLabel,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }

            items(stats.visits, key = { it.code }) { visit ->
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AirlineBadge(iata = visit.code, size = 34.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                text = listOfNotNull(
                                    visit.city.takeIf { it.isNotBlank() },
                                    visit.country.takeIf { it.isNotBlank() },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.titleMedium,
                                color = Sky.Label,
                            )
                            Text(
                                text = if (visit.visits == 1) "1 trip" else "${visit.visits} trips",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Sky.SecondaryLabel,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Sky.Label else Sky.SecondaryLabel,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) Sky.AccentDeep else Sky.Fill,
                CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

private fun Int.monthShort(): String =
    Month.of(this).getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(Locale.getDefault())
