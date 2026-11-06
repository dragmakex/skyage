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
 * access is 400 credits/day and one `states/all` costs 4 — a hundred calls a day — so the poll
 * rate follows [pollIntervalMs] and slows down as the budget runs out. Stream from a paid feed
 * before this app needs always-on tracking.
 */
class OpenSkyClient {

    private data class Cache(val at: Long, val states: List<LiveState>)

    @Volatile private var cache: Cache? = null

    /** Credits left today, as reported by the last response; null until the first one. */
    @Volatile private var remaining: Int? = null

    fun pollIntervalMs(): Long = pollIntervalMs(remaining)

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
            conn.getHeaderField("X-Rate-Limit-Remaining")?.toIntOrNull()?.let { remaining = it }
            val code = conn.responseCode
            if (code == 429) {
                // Not every 429 carries the remaining-credits header; assume the budget is gone.
                remaining = 0
                throw IOException("Live feed limit reached. Try again later.")
            }
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

/**
 * Anonymous access is 400 credits a day and a snapshot costs 4. Spend the budget where it is
 * useful — fresh positions while there is plenty left, a slow heartbeat once it runs low.
 */
internal fun pollIntervalMs(remainingCredits: Int?): Long = when {
    (remainingCredits ?: 400) >= 150 -> 60_000L
    (remainingCredits ?: 400) >= 40 -> 5 * 60_000L
    else -> 15 * 60_000L
}

/** OpenSky encodes "not reported" as JSON null; optDouble would hand back 0.0 or NaN. */
private fun org.json.JSONArray.optDoubleOrNull(index: Int): Double? {
    if (index >= length() || isNull(index)) return null
    val v = optDouble(index, Double.NaN)
    return if (v.isNaN()) null else v
}
