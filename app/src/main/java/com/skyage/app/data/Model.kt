package com.skyage.app.data

import com.skyage.app.geo.distanceKm

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
enum class FlightPhase { NO_DATA, ON_GROUND, AIRBORNE, LANDED }

/** How close the last fix must be to the destination airport to call a missing flight landed. */
const val ARRIVAL_RADIUS_KM = 25.0

/**
 * A flight that stops transmitting right at its destination has landed; one that stops mid-route
 * flew into a receiver gap. ADS-B cannot tell us which, but the last position can: an aircraft last
 * seen on the runway does not need a schedule to be believed, and one last seen over the Atlantic
 * must not be called arrived.
 */
fun arrivedAt(last: LiveState?, dest: Airport?, radiusKm: Double = ARRIVAL_RADIUS_KM): Boolean {
    val lat = last?.lat ?: return false
    val lon = last.lon ?: return false
    val airport = dest ?: return false
    return distanceKm(lat, lon, airport.lat, airport.lon) <= radiusKm
}

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
    /** Origin->fix->destination distance in km: how far the aircraft actually has to travel. */
    val viaKm: Double? = null,
)

/**
 * Above this offset from the route we stop believing the matched aircraft is our flight: wide
 * enough to tolerate real-world deviations on short routes.
 */
const val OFF_ROUTE_THRESHOLD_KM = 400.0

/**
 * How much longer the journey through the fix may be than the non-stop distance before the match
 * is doubted. Offset alone cannot decide this: Toronto-Delhi runs south of its great circle by
 * 2,400 km over Poland because winds and airspace beat geometry, yet only 7% longer in distance.
 * A different aircraft carrying the same callsign is off the route in distance too.
 */
const val DETOUR_LIMIT = 1.35

/**
 * Whether the matched aircraft can be believed to be flying this route: close to it, or far from it
 * but still plausibly on its way. Without a route there is nothing to doubt.
 */
fun offRoute(routeKm: Double?, offRouteKm: Double?, viaKm: Double?): Boolean {
    if (routeKm == null || offRouteKm == null) return false
    if (offRouteKm <= OFF_ROUTE_THRESHOLD_KM) return false
    return viaKm == null || viaKm > routeKm * DETOUR_LIMIT
}
