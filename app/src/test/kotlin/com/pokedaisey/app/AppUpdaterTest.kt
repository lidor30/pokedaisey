package com.pokedaisey.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The updater offers a release only when its tag is a higher version than this build. */
class AppUpdaterTest {

    @Test
    fun semverOrder() {
        val ordered = listOf(
            "0.9.0", "1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-beta.1", "1.0.0-beta.2", "1.0.0-beta.10",
            "1.0.0-rc.1", "1.0.0", "1.0.1", "1.1.0", "2.0.0",
        )
        for (i in ordered.indices) for (j in ordered.indices) {
            assertEquals("${ordered[i]} vs ${ordered[j]}", i.compareTo(j).coerceIn(-1, 1), AppUpdater.compareVersions(ordered[i], ordered[j]).coerceIn(-1, 1))
        }
    }

    @Test
    fun tagsAndPadding() {
        assertEquals(0, AppUpdater.compareVersions("v1.0.0-beta.1", "1.0.0-beta.1"))
        assertEquals(0, AppUpdater.compareVersions("1.0", "1.0.0"))
        assertEquals(0, AppUpdater.compareVersions("1.0.0+build.5", "1.0.0"))
    }

    @Test
    fun onlyNewerIsOffered() {
        assertTrue(AppUpdater.isNewer("1.0.0-beta.2", "1.0.0-beta.1"))
        assertTrue(AppUpdater.isNewer("1.0.0", "1.0.0-beta.1"))
        assertFalse(AppUpdater.isNewer("1.0.0-beta.1", "1.0.0-beta.1"))
        assertFalse(AppUpdater.isNewer("0.9.0", "1.0.0-beta.1"))
    }
}
