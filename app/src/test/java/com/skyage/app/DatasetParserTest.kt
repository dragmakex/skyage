package com.skyage.app

import com.skyage.app.data.DatasetParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the flat-dataset format and the parse cost. Unit tests run from the module directory, so
 * these read the exact files that ship inside the APK.
 */
class DatasetParserTest {

    private fun rows(name: String) =
        File("src/main/assets/$name").bufferedReader().use { it.lineSequence().toList() }.asSequence()

    @Test
    fun `airports parse completely`() {
        val airports = DatasetParser.airports(rows("airports.dat"))
        assertTrue("airport count ${airports.size}", airports.size > 8000)

        val sfo = airports.first { it.iata == "SFO" }
        assertEquals("San Francisco International Airport", sfo.name)
        assertEquals("KSFO", sfo.icao)
        assertEquals("San Francisco", sfo.city)
        assertEquals("United States", sfo.country)
        assertEquals(37.6198, sfo.lat, 0.001)
        assertEquals(-122.3748, sfo.lon, 0.001)
        assertTrue("SFO must rank above grass strips", sfo.relevance >= 300)

        val nrt = airports.first { it.iata == "NRT" }
        assertEquals("Narita International Airport", nrt.name)
        assertEquals("RJAA", nrt.icao)
        assertEquals("Japan", nrt.country)
    }

    @Test
    fun `airlines parse completely and keep the IATA to ICAO mapping used for callsigns`() {
        val airlines = DatasetParser.airlines(rows("airlines.dat"))
        assertTrue("airline count ${airlines.size}", airlines.size > 900)

        val united = airlines.first { it.iata == "UA" }
        assertEquals("United Airlines", united.name)
        assertEquals("UAL", united.icao)

        // Every IATA code the parser needs must be unique enough to resolve a flight number.
        val byIata = airlines.mapNotNull { a -> a.iata?.let { it to a } }.toMap()
        assertEquals("UAL", byIata["UA"]?.icao)
        assertEquals("AAL", byIata["AA"]?.icao)
        assertEquals("DAL", byIata["DL"]?.icao)
        assertEquals("BAW", byIata["BA"]?.icao)
        assertEquals("DLH", byIata["LH"]?.icao)
        assertEquals("RYR", byIata["FR"]?.icao)
        assertEquals("JBU", byIata["B6"]?.icao)
        // Wikidata carries no English label for some carriers; they must not be dropped.
        assertEquals("KAL", byIata["KE"]?.icao)
        assertEquals("QFA", byIata["QF"]?.icao)
    }

    @Test
    fun `malformed rows are skipped instead of failing the whole dataset`() {
        val input = sequenceOf(
            "#name|iata|icao|city|country|lat|lon|relevance",
            "Good|AAA|KAAA|City|Country|10.5|20.5|350",
            "TooFew|Columns",
            "BadCoords|BBB|KBBB|City|Country|north|20.5|350",
            "",
        )
        val airports = DatasetParser.airports(input)
        assertEquals(1, airports.size)
        assertEquals("AAA", airports[0].iata)
        assertNull(DatasetParser.airports(sequenceOf("TooFew|Columns")).firstOrNull())
    }

    @Test
    fun `parsing the shipped datasets stays well under a frame budget`() {
        // Regenerated datasets must not creep back toward the JSON cost this replaced.
        val airportRows = rows("airports.dat").toList().asSequence()
        val airlineRows = rows("airlines.dat").toList().asSequence()

        // Warm up, then measure.
        DatasetParser.airports(airportRows)
        val start = System.nanoTime()
        repeat(5) {
            DatasetParser.airports(airportRows)
            DatasetParser.airlines(airlineRows)
        }
        val perPass = (System.nanoTime() - start) / 5 / 1_000_000
        println("dataset parse: ${perPass}ms per pass")
        assertTrue("one pass took ${perPass}ms", perPass < 250)
    }
}
