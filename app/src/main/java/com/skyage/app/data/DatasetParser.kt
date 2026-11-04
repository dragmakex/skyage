package com.skyage.app.data

/**
 * Parsers for the flat datasets written by `tools/build_dataset.py`.
 *
 * Kept as plain functions over lines so they can be benchmarked and asserted on the host JVM
 * (`DatasetParserTest`) rather than on a device: the same data parsed with org.json cost ~13 s of
 * tokenizing on a slow emulator, which is why the app ships `|`-delimited rows instead.
 */
internal object DatasetParser {

    private const val SEPARATOR = '|'

    /** Columns: name|iata|icao|city|country|lat|lon|relevance */
    fun airports(rows: Sequence<String>): List<Airport> {
        val out = ArrayList<Airport>(9500)
        for (row in rows) {
            if (row.isEmpty() || row[0] == '#') continue
            val c = row.split(SEPARATOR)
            if (c.size < 8) continue
            val lat = c[5].toDoubleOrNull() ?: continue
            val lon = c[6].toDoubleOrNull() ?: continue
            out.add(
                Airport(
                    name = c[0],
                    iata = c[1].ifBlank { null },
                    icao = c[2].ifBlank { null },
                    city = c[3],
                    country = c[4],
                    lat = lat,
                    lon = lon,
                    relevance = c[7].toIntOrNull() ?: 0,
                )
            )
        }
        return out
    }

    /** Columns: name|iata|icao */
    fun airlines(rows: Sequence<String>): List<Airline> {
        val out = ArrayList<Airline>(1200)
        for (row in rows) {
            if (row.isEmpty() || row[0] == '#') continue
            val c = row.split(SEPARATOR)
            if (c.size < 3) continue
            out.add(
                Airline(
                    name = c[0],
                    iata = c[1].ifBlank { null },
                    icao = c[2].ifBlank { null },
                )
            )
        }
        return out
    }
}
