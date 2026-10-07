package com.pokedaisy.app

import com.pokedaisy.app.companion.data.TelemetrySampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTitlesTest {
    @Test
    fun `every hack the companion supports has a name`() {
        val missing = TelemetrySampler.SUPPORTED_HACK_SHA1S - GameTitles.BY_SHA1.keys
        assertTrue("no title for $missing", missing.isEmpty())
    }

    @Test
    fun `tidy drops dump tags and numbering`() {
        assertEquals("Pokemon - FireRed Version", GameTitles.tidy("Pokemon - FireRed Version (USA, Europe) (Rev 1)"))
        assertEquals("Pokemon Emerald Imperium", GameTitles.tidy("Pokemon Emerald Imperium (World) (v1.3.1)"))
        assertEquals("Pokemon Radical Red", GameTitles.tidy("1636 - Pokemon Radical Red"))
        assertEquals("Pokemon Fire Red", GameTitles.tidy("Pokemon_Fire_Red [!]"))
        assertEquals("FireRed QoL", GameTitles.tidy("FireRed QoL"))
        assertEquals("firered-qol", GameTitles.tidy("firered-qol"))
        // Nothing left: the name as it was.
        assertEquals("(Rev 1)", GameTitles.tidy("(Rev 1)"))
    }
}
