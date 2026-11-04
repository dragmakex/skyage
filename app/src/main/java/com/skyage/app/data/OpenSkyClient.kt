package com.skyage.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Live aircraft states from the OpenSky Network REST API.
 *
 * Anonymous access works without a key but is credit-limited, so [snapshot] is cached for
 * [CACHE_MS] and the whole world is fetched once per window rather than per flight. Because the
 * API has no callsign filter, the caller looks flights up inside the cached snapshot.
 *
 * shortcut: one global snapshot per [CACHE_MS] instead of a per-flight subscription. Anonymous
 * access is 400 credits/day and one `states/all` costs 4, so 60 s polling is about 100 calls a
 * day; stream from a paid feed before this app needs always-on tracking.
 */
class OpenSkyClient {

    private data class Cache(val at: Long, val states: List<LiveState>)

    @Volatile private var cache: Cache? = null

    suspend fun snapshot(force: Boolean = false): List<LiveState> = withContext(Dispatchers.IO) {
        val cached = cache
        val now = System.currentTimeMillis()
        if (!force && cached != null && now - cached.at < CACHE_MS) return@withContext cached.states

        val states = fetch()
        cache = Cache(now, states)
        states
    }

    private fun fetch(): List<LiveState> {
        val conn = (URL(STATES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            // OpenSky asks clients to identify themselves.
            setRequestProperty("User-Agent", "Skyage/0.1 (Android)")
        }
        try {
            val code = conn.responseCode
            if (code == 429) throw IOException("Live feed rate limit reached. Try again in a minute.")
            if (code !in 200..299) throw IOException("Live feed returned HTTP $code")

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val states = root.optJSONArray("states") ?: return emptyList()
            val out = ArrayList<LiveState>(states.length())
            for (i in 0 until states.length()) {
                val s = states.optJSONArray(i) ?: continue
                if (s.length() < 11) continue
                out.add(
                    LiveState(
                        icao24 = s.optString(0),
                        callsign = s.optString(1).trim(),
                        originCountry = s.optString(2),
                        lastContact = s.optLong(4, 0L),
                        lon = s.optDoubleOrNull(5),
                        lat = s.optDoubleOrNull(6),
                        baroAltitudeM = s.optDoubleOrNull(7),
                        onGround = s.optBoolean(8, false),
                        velocityMs = s.optDoubleOrNull(9),
                        headingDeg = s.optDoubleOrNull(10),
                        verticalRateMs = if (s.length() > 11) s.optDoubleOrNull(11) else null,
                    )
                )
            }
            return out
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val STATES_URL = "https://opensky-network.org/api/states/all"
        const val CACHE_MS = 60_000L
    }
}

/** OpenSky encodes "not reported" as JSON null; optDouble would hand back 0.0 or NaN. */
private fun org.json.JSONArray.optDoubleOrNull(index: Int): Double? {
    if (index >= length() || isNull(index)) return null
    val v = optDouble(index, Double.NaN)
    return if (v.isNaN()) null else v
}
