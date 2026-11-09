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

    @Test
    fun `typed separators and stray spaces are ignored`() {
        assertEquals("B6" to "1533", splitFlightNumber(" B6\t1533\n"))
        assertEquals("B6" to "1533", splitFlightNumber("B6 - 1533"))
        assertEquals("B6" to "1533", splitFlightNumber("b6\u00A01533"))
    }

    @Test
    fun `only ascii letters and digits count`() {
        // Android keyboards and pasted text can carry full-width or Arabic-Indic digits, which
        // Char.isDigit accepts; none of them can be part of a real airline code.
        assertNull(splitFlightNumber("B6\u0661\u0662\u0663"))
        assertNull(splitFlightNumber("B6\uFF11\uFF15\uFF13\uFF13"))
        assertNull(splitFlightNumber("\u0411\u041661533"))
    }

    @Test
    fun `trailing characters make the input unusable rather than partially read`() {
        assertNull(splitFlightNumber("B615330"))
        assertNull(splitFlightNumber("B6A1533"))
        assertNull(splitFlightNumber(""))
        assertNull(splitFlightNumber("   "))
    }
}
