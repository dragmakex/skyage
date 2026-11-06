package com.skyage.app

import com.skyage.app.data.splitFlightNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlightNumberTest {

    @Test
    fun `codes containing a digit are two characters like any other`() {
        assertEquals("B6" to "1533", splitFlightNumber("B61533"))
        assertEquals("6E" to "6314", splitFlightNumber("6e6314"))
        assertEquals("U2" to "8471", splitFlightNumber("U2 8471"))
        assertEquals("4C" to "4001", splitFlightNumber("4c-4001"))
    }

    @Test
    fun `letter-only codes still work`() {
        assertEquals("UA" to "123", splitFlightNumber("UA123"))
        assertEquals("AC" to "42", splitFlightNumber(" ac42 "))
    }

    @Test
    fun `the number part is digits and at most four of them`() {
        assertEquals("UA" to "1234", splitFlightNumber("UA1234"))
        assertNull(splitFlightNumber("UA12345"))
        assertNull(splitFlightNumber("UA"))
        assertNull(splitFlightNumber("UAL123"))
    }
}
