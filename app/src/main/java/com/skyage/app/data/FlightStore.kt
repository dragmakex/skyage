package com.skyage.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Followed flights, persisted as a small JSON array in app storage. */
class FlightStore(context: Context) {

    private val file = File(context.filesDir, "flights.json")

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
}
