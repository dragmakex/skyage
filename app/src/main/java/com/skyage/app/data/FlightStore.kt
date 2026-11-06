package com.skyage.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Followed flights, persisted as a small JSON array in app storage. */
class FlightStore(context: Context) {

    private val file = File(context.filesDir, "flights.json")
    private val seenFile = File(context.filesDir, "last-seen.dat")
    private val tripsFile = File(context.filesDir, "trips.dat")

    fun load(): List<TrackedFlight> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TrackedFlight(
                    key = o.getString("key"),
                    number = o.getString("number"),
                    callsign = o.getString("callsign"),
                    airlineIata = o.optString("airlineIata"),
                    airlineName = o.optString("airlineName"),
                    originIata = o.optString("originIata").takeIf { it.isNotBlank() },
                    destIata = o.optString("destIata").takeIf { it.isNotBlank() },
                    addedAt = o.optLong("addedAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(flights: List<TrackedFlight>) {
        val arr = JSONArray()
        flights.forEach { f ->
            arr.put(
                JSONObject().apply {
                    put("key", f.key)
                    put("number", f.number)
                    put("callsign", f.callsign)
                    put("airlineIata", f.airlineIata)
                    put("airlineName", f.airlineName)
                    put("originIata", f.originIata ?: "")
                    put("destIata", f.destIata ?: "")
                    put("addedAt", f.addedAt)
                }
            )
        }
        runCatching { file.writeText(arr.toString()) }
    }

    /** Every flight ever followed, with its route: the raw material for the voyage totals. */
    fun loadTrips(): List<Trip> {
        if (!tripsFile.exists()) return emptyList()
        return runCatching { decodeTrips(tripsFile.readText()) }.getOrDefault(emptyList())
    }

    fun saveTrips(trips: List<Trip>) {
        runCatching { tripsFile.writeText(encodeTrips(trips)) }
    }

    /** Where each followed callsign was when we last saw it, so a restart does not forget. */
    fun loadSeen(): Map<String, LiveState> {
        if (!seenFile.exists()) return emptyMap()
        return runCatching { decodeSeen(seenFile.readText()) }.getOrDefault(emptyMap())
    }

    fun saveSeen(seen: Map<String, LiveState>) {
        runCatching { seenFile.writeText(encodeSeen(seen)) }
    }
}

/**
 * Last known fix per callsign, in a `|`-delimited line format rather than JSON: [FlightStore]
 * already writes JSON for the followed flights, but this cache is written on every poll and read
 * at startup, and a plain split keeps it testable off-device.
 */
internal fun encodeSeen(seen: Map<String, LiveState>): String = seen.entries
    .mapNotNull { (key, s) ->
        val lat = s.lat ?: return@mapNotNull null
        val lon = s.lon ?: return@mapNotNull null
        "$key|$lat|$lon|${if (s.onGround) 1 else 0}|${s.lastContact}"
    }
    .joinToString("\n")

internal fun decodeSeen(text: String): Map<String, LiveState> = text.lineSequence()
    .mapNotNull { line ->
        val p = line.split('|')
        if (p.size < 5) return@mapNotNull null
        val lat = p[1].toDoubleOrNull() ?: return@mapNotNull null
        val lon = p[2].toDoubleOrNull() ?: return@mapNotNull null
        val contact = p[4].toLongOrNull() ?: return@mapNotNull null
        p[0] to LiveState(
            icao24 = "",
            callsign = p[0],
            originCountry = "",
            lat = lat,
            lon = lon,
            baroAltitudeM = null,
            velocityMs = null,
            headingDeg = null,
            verticalRateMs = null,
            onGround = p[3] == "1",
            lastContact = contact,
        )
    }
    .toMap()
