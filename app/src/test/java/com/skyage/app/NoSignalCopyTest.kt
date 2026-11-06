package com.skyage.app

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
            noSignalCopy("12:21Z", 2_300.4, "DEL"),
        )
    }

    @Test
    fun `a fix with no usable distance still gives the time`() {
        assertEquals(
            "Last heard at 12:21Z, and nothing since. It may have landed, or flown out of " +
                "receiver coverage.",
            noSignalCopy("12:21Z", null, "DEL"),
        )
    }

    @Test
    fun `never seen reads differently from lost`() {
        assertEquals(
            "Not picked up yet. It may not have left, or it is out of receiver coverage.",
            noSignalCopy(null, null, "DEL"),
        )
    }
}
