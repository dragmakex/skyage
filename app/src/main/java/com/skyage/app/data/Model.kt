package com.skyage.app.data

/**
 * Domain model. [Airport] and [Airline] come from `tools/build_dataset.py` (OurAirports and
 * Wikidata, both public-domain/CC0); see `docs/DATA-SOURCES.md`.
 */
data class Airport(
    val name: String,
    val iata: String?,
    val icao: String?,
    val city: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    val relevance: Int,
) {
    val code: String get() = iata ?: icao ?: "---"
}

data class Airline(
    val name: String,
    val iata: String?,
    val icao: String?,
)

/** A flight the user is following. [callsign] is what we query the live feed with. */
data class TrackedFlight(
    val key: String,
    val number: String,
    val callsign: String,
    val airlineIata: String,
    val airlineName: String,
    val originIata: String? = null,
    val destIata: String? = null,
    val addedAt: Long = 0L,
)

/** One aircraft state vector from the live feed. Nulls mean "not reported". */
data class LiveState(
    val icao24: String,
    val callsign: String,
    val originCountry: String,
    val lat: Double?,
    val lon: Double?,
    val baroAltitudeM: Double?,
    val velocityMs: Double?,
    val headingDeg: Double?,
    val verticalRateMs: Double?,
    val onGround: Boolean,
    val lastContact: Long,
)

/**
 * Mirrors the status vocabulary recovered from the app's Badge.Status.* strings:
 * LEAVES GATE -> TAKING OFF -> LANDING -> ARRIVED AT GATE, plus DIVERTED.
 *
 * We can only prove GROUND/AIR from ADS-B, so [DERIVED_LIVE] carries the honest subset and
 * the finer states stay reserved for a schedule-aware data source.
 */
enum class FlightPhase { NO_DATA, ON_GROUND, AIRBORNE }

/** A tracked flight joined with its live state and computed route progress. */
data class FlightView(
    val flight: TrackedFlight,
    val live: LiveState?,
    val origin: Airport?,
    val dest: Airport?,
    val phase: FlightPhase,
    /** Epoch seconds of the last time this callsign was in the feed, null if never seen. */
    val lastSeenAt: Long? = null,
    /** 0..1 along the great circle origin->dest, null when it cannot be computed. */
    val progress: Double?,
    /** Great-circle distance origin->dest in km, null without both airports. */
    val routeKm: Double?,
    val remainingKm: Double?,
    /** Perpendicular offset from the route, in km. Large values mean the match is doubtful. */
    val offRouteKm: Double? = null,
)

/**
 * Above this offset from the route we stop believing the matched aircraft is our flight: wide
 * enough to tolerate real-world deviations on short routes.
 */
const val OFF_ROUTE_THRESHOLD_KM = 400.0

/**
 * A flat radius is too strict on long flights — Toronto-Delhi is filed 1,600 km off the great
 * circle because winds and airspace beat geometry — so the tolerance grows with the route.
 */
fun offRouteToleranceKm(routeKm: Double?): Double =
    maxOf(OFF_ROUTE_THRESHOLD_KM, (routeKm ?: 0.0) * 0.15)
