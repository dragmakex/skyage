package com.skyage.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.skyage.app.UiState
import com.skyage.app.data.Airport
import com.skyage.app.data.ParsedFlightNumber
import com.skyage.app.ui.theme.Sky

private enum class Slot { ORIGIN, DEST }

@Composable
fun AddFlightScreen(
    state: UiState,
    onBack: () -> Unit,
    onSearchAirports: (String) -> Unit,
    onResolve: (String) -> ParsedFlightNumber?,
    onSave: (ParsedFlightNumber, Airport?, Airport?) -> Boolean,
) {
    var numberText by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf<Airport?>(null) }
    var dest by remember { mutableStateOf<Airport?>(null) }
    var slot by remember { mutableStateOf<Slot?>(null) }

    val parsed = remember(numberText) { onResolve(numberText) }
    val scroll = rememberScrollState()
    var searchTop by remember { mutableStateOf(0) }

    // The results sit below a three-field form, which the keyboard then covers: pull the search
    // block to the top as soon as it has something to show.
    LaunchedEffect(slot, state.airportResults) {
        if (slot != null) scroll.animateScrollTo(searchTop.coerceIn(0, scroll.maxValue))
    }
    val canSave = parsed != null && slot == null

    GlassScaffold(
        topBar = {
            GlassTopBar(title = "Follow a flight", onBack = onBack)
        },
    ) { padding ->
        // The keyboard covers the lower half of this screen, and the airport results live there:
        // imePadding lifts the form above it and the whole body scrolls so nothing is unreachable.
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp)
        ) {
            GlassTextField(
                value = numberText,
                onValueChange = {
                    numberText = it
                    // Typing a new number invalidates the picked route.
                    origin = null
                    dest = null
                    slot = null
                },
                label = "Flight number",
                placeholder = "UA123",
                isError = numberText.isNotBlank() && parsed == null,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (parsed != null) {
                    AirlineBadge(parsed.airlineIata, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = when {
                        numberText.isBlank() -> "Airline code and number, e.g. BA286."
                        parsed != null -> parsed.airline.name
                        else -> "No airline uses that code."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (numberText.isNotBlank() && parsed == null) Sky.Alert
                    else Sky.SecondaryLabel,
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AirportSlot(
                    label = "From",
                    airport = origin,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        slot = if (slot == Slot.ORIGIN) null else Slot.ORIGIN
                        onSearchAirports("")
                    },
                )
                AirportSlot(
                    label = "To",
                    airport = dest,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        slot = if (slot == Slot.DEST) null else Slot.DEST
                        onSearchAirports("")
                    },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Optional",
                style = MaterialTheme.typography.labelLarge,
                color = Sky.TertiaryLabel,
            )

            if (slot != null) {
                Spacer(Modifier.height(16.dp))
                GlassTextField(
                    value = state.airportQuery,
                    onValueChange = onSearchAirports,
                    label = if (slot == Slot.ORIGIN) "Search origin airport" else "Search destination",
                    placeholder = "JFK, Heathrow, Tokyo",
                    trailingIcon = {
                        IconButton(onClick = { slot = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Sky.SecondaryLabel)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { searchTop = it.positionInParent().y.toInt() },
                )
                state.airportResults.forEach { airport ->
                    AirportRow(airport) {
                        if (slot == Slot.ORIGIN) origin = airport else dest = airport
                        slot = null
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            GlassButton(
                text = "Follow flight",
                onClick = {
                    val p = parsed ?: return@GlassButton
                    if (onSave(p, origin, dest)) onBack()
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun AirportSlot(
    label: String,
    airport: Airport?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SurfaceCard(modifier = modifier.clickable(onClick = onClick)) {
        Column {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Sky.TertiaryLabel,
            )
            if (airport != null) {
                Text(
                    text = airport.code,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Sky.Label,
                )
            }
            Text(
                text = airport?.city ?: "Select",
                style = if (airport == null) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (airport == null) Sky.TertiaryLabel else Sky.SecondaryLabel,
            )
        }
    }
}

@Composable
private fun AirportRow(airport: Airport, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(52.dp)) {
            Text(
                text = airport.code,
                style = MaterialTheme.typography.titleMedium,
                color = Sky.Accent,
            )
        }
        Column {
            Text(
                text = airport.name,
                style = MaterialTheme.typography.bodyLarge,
                color = Sky.Label,
            )
            Text(
                text = listOfNotNull(airport.city, airport.country).joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = Sky.SecondaryLabel,
            )
        }
    }
}
