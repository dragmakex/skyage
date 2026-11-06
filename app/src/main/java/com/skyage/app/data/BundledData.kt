package com.skyage.app.data

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Offline reference data, parsed once from the bundled flat files (see `docs/DATA-SOURCES.md`).
 *
 * 9,000 airports and 1,000 airlines is small enough to keep entirely in memory, so there is no
 * database here on purpose: an index in a map is faster than Room for this size.
 */
class BundledData(private val assets: AssetManager) {

    private val mutex = Mutex()
    @Volatile private var loaded = false

    private var airports: List<Airport> = emptyList()
    private var airlines: List<Airline> = emptyList()

    private val byIata = HashMap<String, Airport>()
    private val byIcao = HashMap<String, Airport>()
    private val airlineByIata = HashMap<String, Airline>()

    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            withContext(Dispatchers.IO) {
                val parsedAirports = parseAirports()
                val parsedAirlines = parseAirlines()

                parsedAirports.forEach { a ->
                    a.iata?.takeIf { it.isNotBlank() }?.let { byIata.putIfAbsent(it.uppercase(), a) }
                    a.icao?.takeIf { it.isNotBlank() }?.let { byIcao.putIfAbsent(it.uppercase(), a) }
                }
                parsedAirlines.forEach { a ->
                    a.iata?.takeIf { it.isNotBlank() }?.let { airlineByIata.putIfAbsent(it.uppercase(), a) }
                }

                airports = parsedAirports
                airlines = parsedAirlines
                loaded = true
            }
        }
    }

    fun allAirports(): List<Airport> {
        check(loaded) { "BundledData.ensureLoaded() must be called first" }
        return airports
    }

    fun isLoaded(): Boolean = loaded

    fun airportByCode(code: String?): Airport? {
        if (code.isNullOrBlank()) return null
        val c = code.trim().uppercase()
        return byIata[c] ?: byIcao[c]
    }

    fun airlineByIata(iata: String?): Airline? {
        if (iata.isNullOrBlank()) return null
        return airlineByIata[iata.trim().uppercase()]
    }

    /**
     * Ranked airport search: exact code, then code prefix, then name/city substring,
     * with the dataset's own `relevance` as the tie-breaker (big hubs surface first).
     */
    fun searchAirports(query: String, limit: Int = 40): List<Airport> {
        val q = query.trim().uppercase()
        if (q.isEmpty()) return airports.sortedByDescending { it.relevance }.take(limit)

        val scored = ArrayList<Pair<Int, Airport>>()
        for (a in airports) {
            val iata = a.iata?.uppercase()
            val icao = a.icao?.uppercase()
            val name = a.name.uppercase()
            val city = a.city.uppercase()
            val rank = when {
                iata == q || icao == q -> 0
                iata?.startsWith(q) == true || icao?.startsWith(q) == true -> 1
                city.startsWith(q) -> 2
                name.startsWith(q) -> 3
                city.contains(q) -> 4
                name.contains(q) -> 5
                else -> -1
            }
            if (rank >= 0) scored.add(rank to a)
        }
        scored.sortWith(
            compareBy<Pair<Int, Airport>> { it.first }
                .thenByDescending { it.second.relevance }
                .thenBy { it.second.name }
        )
        return scored.take(limit).map { it.second }
    }

    fun parseFlightNumber(raw: String): ParsedFlightNumber? {
        val (letters, digits) = splitFlightNumber(raw) ?: return null
        val airline = airlineByIata(letters) ?: return null
        val icao = airline.icao?.takeIf { it.isNotBlank() } ?: return null
        return ParsedFlightNumber(
            airline = airline,
            airlineIata = letters,
            number = digits,
            displayNumber = "$letters$digits",
            callsign = icao.uppercase() + digits,
        )
    }

    private fun parseAirports(): List<Airport> =
        assets.open("airports.dat").bufferedReader().lineSequence().let(DatasetParser::airports)

    private fun parseAirlines(): List<Airline> =
        assets.open("airlines.dat").bufferedReader().lineSequence().let(DatasetParser::airlines)

}

data class ParsedFlightNumber(
    val airline: Airline,
    val airlineIata: String,
    val number: String,
    val displayNumber: String,
    val callsign: String,
)
