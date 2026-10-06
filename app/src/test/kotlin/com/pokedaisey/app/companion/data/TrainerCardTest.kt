package com.pokedaisey.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.CRC32

/** The TRAINER CARD: its data per fixture save, and the card drawn from the retail ROMs. */
class TrainerCardTest {
    private fun card(key: String, cfg: NativeConfig) = readTrainerCard(FixtureMemoryReader.load(key), cfg)!!

    @Test fun `FireRed - the save's card`() {
        val c = card("firered_vanilla", NATIVE_FIRERED_REV1)
        assertEquals("LIDOR", c.nameText)
        assertEquals(49151, c.trainerId)
        assertEquals(224300L, c.money)
        assertEquals(0xFF, c.badges)
        assertEquals(1, c.stars) // the Hall of Fame
        assertEquals((45 shl 16) or (20 shl 8) or 53, c.hofDebut)
        assertEquals(69, c.dexCaught) // Kanto, before the National Dex
        assertEquals(CardStyle.KANTO, c.style)
    }

    @Test fun `FireRed QoL - through the save blocks the struct path found`() {
        val reader = FixtureMemoryReader.load("firered_qol")
        val (cfg, _) = findStructMoney(reader, GameKind.FIRERED, 3, 16)!!
        val c = readTrainerCard(reader, cfg)!!
        assertEquals(49151, c.trainerId)
        assertEquals(69, c.dexCaught)
    }

    @Test fun `Emerald - the save's card`() {
        val c = card("emerald_vanilla", NATIVE_EMERALD_RETAIL)
        assertEquals("LIDOR", c.nameText)
        assertEquals(7450, c.trainerId)
        assertEquals(185092L, c.money)
        assertEquals(0x3F, c.badges)
        assertEquals(0, c.stars)
        assertEquals(0, c.hofDebut)
        assertEquals(28, c.dexCaught) // Hoenn
        assertEquals(CardStyle.HOENN, c.style)
    }

    @Test fun `LeafGreen - FireRed's card`() {
        val c = card("leafgreen_rev1", NATIVE_LEAFGREEN_REV1)
        assertEquals(6098L, c.money)
        assertEquals(CardStyle.KANTO, c.style)
    }

    @Test fun `Unbound - its save, read like FireRed's`() {
        // What Unbound's own card shows for this save: Lidor, IDNo.06174, ₽3848, Pokédex 1, 0:25.
        val c = card("unbound", NATIVE_UNBOUND_WITH_DEX)
        assertEquals("Lidor", c.nameText)
        assertEquals(6174, c.trainerId)
        assertEquals(3848L, c.money)
        assertEquals(1, c.dexCaught)
        assertEquals(0 to 25, c.hours to c.minutes)
        assertEquals(CardStyle.UNBOUND, c.style)
    }

    @Test fun `other hacks get no card`() {
        assertNull(readTrainerCard(FixtureMemoryReader.load("unbound"), NATIVE_UNBOUND))
        assertNull(readTrainerCard(FixtureMemoryReader.load("emerald_rogue"), NATIVE_EMERALD_ROGUE))
    }

    private fun art(path: String, style: CardStyle): TrainerCardArt.Art {
        val rom = File(path).takeIf { it.isFile }?.readBytes()
        assumeTrue("no ROM at $path", rom != null)
        val found = RomArt.find(rom!!)
        return TrainerCardArt.art(style) { found[it] }!!
    }

    private fun crc(img: RomArt.Image): String {
        val crc = CRC32()
        img.argb.forEach { crc.update(byteArrayOf((it ushr 24).toByte(), (it ushr 16).toByte(), (it ushr 8).toByte(), it.toByte())) }
        return "%08x".format(crc.value)
    }

    /** Also writes each card to build/trainer-card/ to look at. */
    private fun rendered(art: TrainerCardArt.Art, c: TrainerCardInfo, back: Boolean, name: String): String {
        val img = TrainerCardArt.render(art, c, back)
        File("build/trainer-card").apply { mkdirs() }.resolve("$name.png").writeBytes(img.png())
        return crc(img)
    }

    @Test fun `FireRed card - drawn like the game`() {
        val art = art(RomFileReader.FIRERED_REV1_PATH, CardStyle.KANTO)
        val c = card("firered_vanilla", NATIVE_FIRERED_REV1)
        val more = c.copy(female = true, stars = 4, trades = 12, linkWins = 3, linkLosses = 1, unionRoom = 7, berryCrush = 99)
        rendered(art, more, false, "firered_female_front")
        rendered(art, more, true, "firered_female_back")
        val front = rendered(art, c, false, "firered_front")
        val back = rendered(art, c, true, "firered_back")
        assertEquals(FR_FRONT to FR_BACK, front to back)
    }

    @Test fun `Emerald card - drawn like the game`() {
        val art = art(RomFileReader.EMERALD_PATH, CardStyle.HOENN)
        val c = card("emerald_vanilla", NATIVE_EMERALD_RETAIL)
        val more = c.copy(
            female = true, stars = 4, hofDebut = (12 shl 16) or (3 shl 8) or 4, trades = 12, linkWins = 3, linkLosses = 1,
            linkContests = 2, linkPokeblocks = 5, battlePoints = 140,
        )
        rendered(art, more, false, "emerald_female_front")
        rendered(art, more, true, "emerald_female_back")
        // The screenshot was a minute later than this capture (31:26).
        val front = rendered(art, c.copy(minutes = 27), false, "emerald_front")
        val back = rendered(art, c, true, "emerald_back")
        assertEquals(EM_FRONT to EM_BACK, front to back)
    }

    @Test fun `Unbound card - FireRed's in Unbound's colours`() {
        val art = art(RomFileReader.UNBOUND_PATH, CardStyle.UNBOUND)
        val c = card("unbound", NATIVE_UNBOUND_WITH_DEX)
        rendered(art, c.copy(female = true), false, "unbound_female_front")
        val front = rendered(art, c, false, "unbound_front")
        val back = rendered(art, c, true, "unbound_back")
        assertEquals(UB_FRONT to UB_BACK, front to back)
    }

    private companion object {
        // CRC32 of the ARGB pixels, recorded once each matched a headless
        // screenshot of the game's own card for this save pixel for pixel.
        const val FR_FRONT = "d62339db"
        const val FR_BACK = "045996a2"
        const val EM_FRONT = "f3e14960"
        const val EM_BACK = "74484c21"
        // Not a copy of a game screen: recorded once it looked right.
        const val UB_FRONT = "be68c6aa"
        const val UB_BACK = "5155c5ba"
    }
}
