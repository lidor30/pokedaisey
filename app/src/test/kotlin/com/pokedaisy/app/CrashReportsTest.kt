package com.pokedaisy.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportsTest {
    private val hour = 60L * 60 * 1000
    private val installed = 1_700_000_000_000L

    @Test
    fun `asks from the third open, two days after the install`() {
        assertTrue(CrashReports.shouldAsk(opens = 3, installedAt = installed, now = installed + 48 * hour))
        assertTrue(CrashReports.shouldAsk(opens = 10, installedAt = installed, now = installed + 30 * 24 * hour))
    }

    @Test
    fun `not before both have happened`() {
        assertFalse(CrashReports.shouldAsk(opens = 2, installedAt = installed, now = installed + 30 * 24 * hour))
        assertFalse(CrashReports.shouldAsk(opens = 50, installedAt = installed, now = installed + 47 * hour))
    }
}
