package com.skyage.app

import com.skyage.app.data.pollIntervalMs
import org.junit.Assert.assertEquals
import org.junit.Test

/** Anonymous access is 400 credits a day at 4 a snapshot; the poll rate has to respect that. */
class PollIntervalTest {

    @Test
    fun pollsEveryMinuteWhileTheBudgetIsHealthy() {
        assertEquals(60_000L, pollIntervalMs(400))
        assertEquals(60_000L, pollIntervalMs(150))
    }

    @Test
    fun slowsDownAsTheBudgetRunsLow() {
        assertEquals(5 * 60_000L, pollIntervalMs(149))
        assertEquals(15 * 60_000L, pollIntervalMs(39))
        assertEquals(15 * 60_000L, pollIntervalMs(0))
    }

    @Test
    fun firstLaunchAssumesAFullBudget() {
        assertEquals(60_000L, pollIntervalMs(null))
    }
}
