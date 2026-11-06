package com.skyage.app

import com.skyage.app.data.LiveState
import com.skyage.app.data.decodeSeen
import com.skyage.app.data.encodeSeen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenCacheTest {

    private fun state(key: String, lat: Double?, lon: Double?, ground: Boolean) = LiveState(
        icao24 = "abc123",
        callsign = key,
        originCountry = "Netherlands",
        lat = lat,
        lon = lon,
        baroAltitudeM = 10_000.0,
        velocityMs = 240.0,
        headingDeg = 290.0,
        verticalRateMs = 0.0,
        onGround = ground,
        lastContact = 1_700_000_000L,
    )

    @Test
    fun `a restart reads back the same fixes`() {
        val written = mapOf(
            "ACA42" to state("ACA42", 28.5592, 77.0785, ground = true),
            "KLM641" to state("KLM641", 52.0, -30.0, ground = false),
        )
        val read = decodeSeen(encodeSeen(written))
        assertEquals(setOf("ACA42", "KLM641"), read.keys)
        assertEquals(28.5592, read.getValue("ACA42").lat!!, 1e-9)
        assertEquals(77.0785, read.getValue("ACA42").lon!!, 1e-9)
        assertEquals(1_700_000_000L, read.getValue("ACA42").lastContact)
        assertTrue(read.getValue("ACA42").onGround)
        assertFalse(read.getValue("KLM641").onGround)
    }

    @Test
    fun `a flight with no position is not written`() {
        val text = encodeSeen(mapOf("UAL123" to state("UAL123", null, null, ground = false)))
        assertEquals("", text)
    }

    @Test
    fun `a truncated or foreign line is skipped, not fatal`() {
        val read = decodeSeen("ACA42|28.5|77.0|1|1700000000\nbroken|x|y\n\nKLM641|52|-30|0|1700000100")
        assertEquals(setOf("ACA42", "KLM641"), read.keys)
    }
}
