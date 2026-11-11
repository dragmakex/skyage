package com.skyage.app

import com.skyage.app.ui.heardWhen
import com.skyage.app.ui.noSignalCopy
import org.junit.Assert.assertEquals
import org.junit.Test

class NoSignalCopyTest {

    @Test
    fun `a fix short of the airport says how far short`() {
        // Grouping follows the device locale, so build the expectation the same way the app does.
        val distance = "%,d".format(2_300)
        assertEquals(
            "Last heard at 12:21Z, $distance km from DEL. Nothing since: it may have landed " +
                "elsewhere, or flown out of receiver coverage.",
            noSignalCopy("at 12:21Z", 2_300.4, "DEL"),
        )
    }

    @Test
    fun `a fix with no usable distance still gives the time`() {
        assertEquals(
            "Last heard at 12:21Z, and nothing since. It may have landed, or flown out of " +
                "receiver coverage.",
            noSignalCopy("at 12:21Z", null, "DEL"),
        )
    }

    @Test
    fun `never seen reads differently from lost`() {
        assertEquals(
            "Not picked up yet. It may not have left, or it is out of receiver coverage.",
            noSignalCopy(null, null, "DEL"),
        )
    }

    /** 2026-11-10T14:30:00Z, a fixed "now" so the day boundaries cannot drift with the clock. */
    private val now = 1_794_311_400_000L

    private fun utc(year: Int, month: Int, day: Int) = java.time.LocalDateTime
        .of(year, month, day, 13, 51)
        .toInstant(java.time.ZoneOffset.UTC)
        .epochSecond

    @Test
    fun `a fix from today gives the time alone`() {
        assertEquals("at 13:51Z", heardWhen(utc(2026, 11, 10), now))
    }

    @Test
    fun `a fix from yesterday says so`() {
        assertEquals("yesterday at 13:51Z", heardWhen(utc(2026, 11, 9), now))
    }

    @Test
    fun `an older fix names the day, in the device month names`() {
        val older = heardWhen(utc(2026, 11, 7), now)
        org.junit.Assert.assertTrue(older, Regex("^on 7 \\S+ at 13:51Z$").matches(older))
    }

    @Test
    fun `a fix dated in the future is not claimed as today`() {
        val later = heardWhen(utc(2026, 11, 12), now)
        org.junit.Assert.assertTrue(later, later.startsWith("on "))
    }
}
