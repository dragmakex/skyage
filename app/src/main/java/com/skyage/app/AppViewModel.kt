package com.skyage.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skyage.app.data.Airport
import com.skyage.app.data.arrivedAt
import com.skyage.app.data.BundledData
import com.skyage.app.data.FlightPhase
import com.skyage.app.data.FlightStore
import com.skyage.app.data.FlightView
import com.skyage.app.data.LiveState
import com.skyage.app.data.offRouteToleranceKm
import com.skyage.app.data.OpenSkyClient
import com.skyage.app.data.ParsedFlightNumber
import com.skyage.app.data.TrackedFlight
import com.skyage.app.data.Trip
import com.skyage.app.data.tripKey
import com.skyage.app.geo.crossTrackKm
import com.skyage.app.geo.distanceKm
import com.skyage.app.geo.progressAlongRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class UiState(
    val ready: Boolean = false,
    val views: List<FlightView> = emptyList(),
    val refreshing: Boolean = false,
    val lastUpdate: Long? = null,
    val message: String? = null,
    val airportQuery: String = "",
    val airportResults: List<Airport> = emptyList(),
    /** Every flight ever followed, for the voyage totals. */
    val trips: List<Trip> = emptyList(),
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val bundled = BundledData(app.assets)
    private val store = FlightStore(app)
    private val liveClient = OpenSkyClient()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var lastLive: Map<String, LiveState> = emptyMap()
    // Kept across polls: a callsign that drops out still tells us when and where it was last seen.
    private val lastKnown = mutableMapOf<String, LiveState>()
    private var tracked: List<TrackedFlight> = emptyList()
    private var trips: List<Trip> = emptyList()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            // Parsing the datasets and the saved flights is file IO: never do it on the main thread.
            val loaded = withContext(Dispatchers.IO) {
                bundled.ensureLoaded()
                store.load().also { lastKnown.putAll(store.loadSeen()) }
            }
            tracked = loaded
            trips = withContext(Dispatchers.IO) { store.loadTrips() }
            _state.update { it.copy(ready = true, views = buildViews(), trips = trips) }
            refresh()
        }
    }

    fun refresh(force: Boolean = false) {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            try {
                val states = try {
                    liveClient.snapshot(force = force)
                } catch (first: Exception) {
                    // A DNS answer that fails during a VPN or Wi-Fi/LTE handoff is cached as
                    // negative for a second or two; one retry almost always clears it.
                    android.util.Log.w("Skyage", "live refresh failed, retrying", first)
                    delay(2_000)
                    liveClient.snapshot(force = true)
                }
                lastLive = states.associateBy { it.callsign.uppercase().trim() }
                states.forEach { lastKnown[it.callsign.uppercase().trim()] = it }
                // Bounded by the followed flights, so this cannot grow without limit.
                val followed = tracked.map { it.callsign.uppercase().trim() }.toSet()
                val keep = lastKnown.filterKeys { it in followed }
                withContext(Dispatchers.IO) { store.saveSeen(keep) }
                _state.update {
                    it.copy(
                        refreshing = false,
                        lastUpdate = System.currentTimeMillis(),
                        message = null,
                        views = buildViews(),
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("Skyage", "live refresh failed twice", e)
                _state.update {
                    it.copy(
                        refreshing = false,
                        message = when (e) {
                            is java.net.UnknownHostException,
                            is java.net.SocketTimeoutException,
                            is java.net.ConnectException,
                            -> "Can't reach the live feed. Check your connection."
                            else -> e.message ?: "Live positions are unavailable right now."
                        },
                        views = buildViews(),
                    )
                }
            }
        }
    }

    fun pollIntervalMs(): Long = liveClient.pollIntervalMs()

    fun liveFor(flight: TrackedFlight): LiveState? =
        lastLive[flight.callsign.uppercase().trim()]

    fun allAirports(): List<Airport> = if (bundled.isLoaded()) bundled.allAirports() else emptyList()

    fun resolve(raw: String): ParsedFlightNumber? = bundled.parseFlightNumber(raw)

    fun searchAirports(query: String) {
        _state.update { it.copy(airportQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(airportResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(120)
            val results = withContext(Dispatchers.Default) {
                bundled.searchAirports(query, limit = 30)
            }
            _state.update { it.copy(airportResults = results) }
        }
    }

    fun clearSearch() = _state.update { it.copy(airportQuery = "", airportResults = emptyList()) }

    fun addFlight(parsed: ParsedFlightNumber, origin: Airport?, dest: Airport?): Boolean {
        val key = listOf(parsed.callsign, origin?.code ?: "", dest?.code ?: "").joinToString("|")
        if (tracked.any { it.key == key }) {
            _state.update { it.copy(message = "${parsed.displayNumber} is already being followed.") }
            return false
        }
        val flight = TrackedFlight(
            key = key,
            number = parsed.displayNumber,
            callsign = parsed.callsign,
            airlineIata = parsed.airlineIata,
            airlineName = parsed.airline.name,
            originIata = origin?.code,
            destIata = dest?.code,
            addedAt = System.currentTimeMillis(),
        )
        tracked = tracked + flight
        // Only a flight with both ends known can be counted as a place visited.
        if (origin != null && dest != null) {
            val trip = Trip(
                date = flight.addedAt,
                number = flight.number,
                airlineIata = flight.airlineIata,
                originIata = origin.code,
                destIata = dest.code,
            )
            if (trips.none { tripKey(it) == tripKey(trip) }) {
                trips = trips + trip
                viewModelScope.launch(Dispatchers.IO) { store.saveTrips(trips) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) { store.save(tracked) }
        _state.update { it.copy(views = buildViews(), trips = trips, message = null) }
        return true
    }

    fun removeFlight(key: String) {
        tracked = tracked.filterNot { it.key == key }
        // Same rule as adding: the file write leaves the main thread, so a tap never waits on disk.
        val kept = tracked
        viewModelScope.launch(Dispatchers.IO) { store.save(kept) }
        _state.update { it.copy(views = buildViews()) }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun viewFor(key: String): FlightView? = _state.value.views.firstOrNull { it.flight.key == key }

    private fun buildViews(): List<FlightView> = tracked
        .map { f ->
            val origin = bundled.airportByCode(f.originIata)
            val dest = bundled.airportByCode(f.destIata)
            val key = f.callsign.uppercase().trim()
            val live = lastLive[key]
            // Without a live state, the last one we saw still says where it went: that is the
            // difference between a flight that landed and one that flew out of coverage.
            val known = if (live == null) lastKnown[key] else null
            val fix = live ?: known
            val routeKm = if (origin != null && dest != null) {
                distanceKm(origin.lat, origin.lon, dest.lat, dest.lon)
            } else null

            val positioned = origin != null && dest != null && fix?.lat != null && fix.lon != null
            val offRouteKm = if (positioned) {
                crossTrackKm(origin.lat, origin.lon, dest.lat, dest.lon, fix.lat, fix.lon)
            } else null

            // A callsign is not unique per route, so a position far from the route means the
            // aircraft we matched is probably a different flight. Report no progress instead of a
            // confident 0%.
            val progress = if (positioned && (offRouteKm ?: 0.0) <= offRouteToleranceKm(routeKm)) {
                progressAlongRoute(origin.lat, origin.lon, dest.lat, dest.lon, fix.lat, fix.lon)
            } else null

            // "Landed" is the same fact from two angles: an aircraft still transmitting on the
            // ground at its destination, or one that stopped transmitting there.
            val landed = arrivedAt(live ?: known, dest)
            val phase = when {
                live == null && landed -> FlightPhase.LANDED
                live == null -> FlightPhase.NO_DATA
                live.onGround && landed -> FlightPhase.LANDED
                live.onGround -> FlightPhase.ON_GROUND
                else -> FlightPhase.AIRBORNE
            }

            FlightView(
                flight = f,
                live = live,
                origin = origin,
                dest = dest,
                phase = phase,
                lastSeenAt = known?.lastContact,
                progress = progress,
                routeKm = routeKm,
                remainingKm = if (routeKm != null && progress != null) routeKm * (1 - progress) else null,
                offRouteKm = offRouteKm,
            )
        }
        .sortedWith(
            compareBy<FlightView> { if (it.phase == FlightPhase.AIRBORNE) 0 else 1 }
                .thenBy { it.flight.number }
        )
}
