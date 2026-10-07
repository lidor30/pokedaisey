package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** [M4aSongs] on the retail ROMs on this machine (skips without them). */
class M4aSongsTest {
    private fun songs(path: String): M4aSongs {
        val rom = File(path).takeIf { it.isFile }?.readBytes()
        assumeTrue(rom != null)
        return assertNotNullAnd(M4aSongs.locate(rom!!))
    }

    private fun <T> assertNotNullAnd(v: T?): T { assertNotNull(v); return v!! }

    @Test fun fireRed() {
        val s = songs(RomFileReader.FIRERED_REV1_PATH)
        assertEquals(0x081DD165L, s.songNumStart) // m4aSongNumStart, Thumb bit set
        assertEquals(297, s.songId("song_06d96f4")) // MUS_VS_TRAINER
        assertEquals(300, s.songId("song_06dd8a4")) // MUS_PALLET
        assertEquals(0x08172328L, s.idleLoop) // the first Thumb `b .`
        // The BGM-player songs (sound/song_table.inc's `song x, 0, 0`), not SEs like SE_SELECT (player 2).
        assertTrue(297 in s.bgmSongIds && 300 in s.bgmSongIds)
        assertFalse(5 in s.bgmSongIds)
    }

    @Test fun emerald() {
        val s = songs(RomFileReader.EMERALD_PATH)
        assertEquals(0x082E0131L, s.songNumStart)
        assertEquals(360, s.songId("song_09092f8")) // MUS_ROUTE110 (Route 114, see FfMusicKeyTest)
    }

    @Test fun emeraldRogueGccBuild() {
        // m4aSongNumStart compiled by gcc (the second signature); song 647 is the
        // hub's music - headless: forced on the render core after the title
        // music is up, it plays with the same header the live game's BGM shows.
        val s = songs(File(File(RomFileReader.FIRERED_REV1_PATH).parentFile, "Pokemon Emerald Rogue (v2.2.1-EX).gba").path)
        assertEquals(0x08009B41L, s.songNumStart)
        assertEquals(0x092D4258L, s.songTable)
        assertEquals(647, s.songId("song_1d50948"))
        assertEquals(0x08087824L, s.idleLoop)
    }

    @Test fun newerExpansionBuilds() {
        // m4aSongNumStart with expansion's ident check (the third signature).
        val dir = File(RomFileReader.FIRERED_REV1_PATH).parentFile
        val lazarus = songs(File(dir, "Pokemon Lazarus (v2.0).gba").path)
        assertEquals(0x08009FA1L, lazarus.songNumStart)
        assertEquals(0x08FF5F48L, lazarus.songTable)
        val soulGold = songs(File(dir, "Pokemon-SoulGold-v1.1.4.gba").path)
        assertEquals(0x095FD42CL, soulGold.songTable)
        val soulGold12 = songs(File(dir, "Soulgold (v1.2).gba").path)
        assertEquals(0x081A2981L, soulGold12.songNumStart)
        assertEquals(0x096086FCL, soulGold12.songTable)
    }
}
