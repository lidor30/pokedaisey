package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The ROM hacks' POKéDEX: each `<game>_dex` fixture is a headless capture of
 * the user's save taken while (or right before) the game's own POKéDEX was on
 * screen - its README says what the game showed, and the flags must say the
 * same. The ROM half decodes every entry the dex lists from the user's ROM
 * (skipped when the ROM isn't on this machine).
 */
class HackPokedexTest {
    private fun state(key: String, cfg: NativeConfig): PokedexState {
        val dex = readPokedexState(FixtureMemoryReader.load(key), cfg, cfg.pokedex!!)
        assertNotNull("no pokedex state decoded", dex)
        return dex!!
    }

    /** Emerald Rogue: a 2-bit state per species - its dex said Seen 2 / Caught 1 (COTTONEE seen, ROCKRUFF caught). */
    @Test
    fun `Emerald Rogue - species-indexed 2-bit state`() {
        // No. n reads its species' bit, and species come from the ROM (Gen 9 isn't No. = species).
        val rom = RomFileReader.load("$ROM_DIR/Pokemon Emerald Rogue (v2.2.1-EX).gba")
        assumeTrue(rom != null)
        PokedexSource.reader = rom!!
        val dex = state("emerald_rogue", NATIVE_EMERALD_ROGUE)
        assertEquals(setOf(546, 744), dex.seen)
        assertEquals(setOf(744), dex.caught)
    }

    /** Quetzal: 3-bit seen levels (6 species), caught per region (CHARMANDER, Kanto) - its dex said SEEN 6 / OWN 1. */
    @Test
    fun `Quetzal - seen levels and caught per region`() {
        val dex = state("quetzal", NATIVE_QUETZAL)
        assertEquals(6, dex.seen.size)
        assertTrue(dex.seen.containsAll(listOf(4, 7, 19, 133)))
        assertEquals(setOf(4), dex.caught)
        assertTrue(dex.national)
    }

    /** Quetzal's Spanish release (its dex said AVISTADOS 468 / ATRAPADOS 349) and a second English save. */
    @Test
    fun `Quetzal - the Spanish save and the Johto one`() {
        val es = state("quetzal_es", NATIVE_QUETZAL_ES)
        assertEquals(468 to 349, es.seen.size to es.caught.size)
        assertTrue(es.caught.containsAll(listOf(6, 130)))
        val johto = state("quetzal_johto", NATIVE_QUETZAL)
        assertTrue(johto.caught.containsAll(listOf(4, 215, 408, 442, 627)))
    }

    /** Unbound FR: CFRU's flags, as English's - its dex said Seen 6 Borrius / 8 National, Caught 2 / 3. */
    @Test
    fun `Unbound FR - seen 8, caught 3`() {
        val dex = state("unbound_fr", NATIVE_UNBOUND_FR)
        assertEquals(8 to 3, dex.seen.size to dex.caught.size)
        assertEquals(setOf(225, 246, 361), dex.caught)
    }

    /** Orange Islands: FireRed's flags - its continue screen said POKéDEX 2 (PIKACHU and the MEWTWO it starts with). */
    @Test
    fun `Orange Islands - two caught`() {
        val dex = state("orange_islands", NATIVE_ORANGE_ISLANDS)
        assertEquals(setOf(25, 150), dex.caught)
        assertFalse(dex.national)
    }

    /** Glazed: retail's flags (its own numbering, CHIMCHAR No.322); Imperium: SaveBlock1's, by national number. */
    @Test
    fun `Glazed and Imperium - the starter caught`() {
        assertEquals(setOf(322), state("glazed", NATIVE_GLAZED).caught)
        assertEquals(setOf(4), state("imperium", NATIVE_IMPERIUM).caught)
    }

    @Test
    fun `Odyssey - Talrega 1, National 8 seen, 2 owned`() {
        val dex = state("odyssey_dex", NATIVE_ODYSSEY)
        assertEquals(setOf(137, 263, 285, 303, 311, 312, 316, 352), dex.seen)
        assertEquals(setOf(311, 312), dex.caught)
        assertEquals(1, dex.seen.count { it <= 151 })
        assertTrue(dex.national)
    }

    @Test
    fun `Radical Red - CHARMANDER caught, SQUIRTLE seen`() {
        val dex = state("radical_red_dex", NATIVE_RADICAL_RED_V4_1)
        assertEquals(setOf(4, 7), dex.seen)
        assertEquals(setOf(4), dex.caught)
        assertFalse(dex.national)
    }

    @Test
    fun `Amethyst - its own numbers, TEPIG is No 340`() {
        val dex = state("amethyst_dex", NATIVE_AMETHYST)
        assertEquals(setOf(182, 340), dex.seen)
        assertEquals(setOf(340), dex.caught)
    }

    @Test
    fun `Gaia - flags at fixed EWRAM addresses`() {
        val dex = state("gaia_dex", NATIVE_GAIA_V3_2)
        assertEquals(setOf(390), dex.seen)
        assertEquals(setOf(390), dex.caught)
        assertTrue(dex.national)
    }

    @Test
    fun `Celia - No n is bit n`() {
        val dex = state("celia_dex", NATIVE_CELIA)
        assertEquals(setOf(2), dex.seen)
        assertEquals(setOf(2), dex.caught)
    }

    @Test
    fun `Heart and Soul - Johto seen 2, own 1`() {
        val dex = state("heart_and_soul_dex", NATIVE_HEART_AND_SOUL)
        assertEquals(setOf(16, 155), dex.seen)
        assertEquals(setOf(155), dex.caught)
        assertFalse(dex.national)
    }

    @Test
    fun `Lazarus - FENNEKIN seen and caught`() {
        val dex = state("lazarus_dex", NATIVE_LAZARUS)
        assertEquals(setOf(653), dex.seen)
        assertEquals(setOf(653), dex.caught)
    }

    @Test
    fun `SoulGold - Johto seen 6, own 1`() {
        // The game's own POKéDEX on this save: Seen 6 / Own 1, CYNDAQUIL caught.
        val dex = state("soulgold", NATIVE_SOULGOLD)
        assertEquals(setOf(16, 19, 155, 163, 183, 263), dex.seen)
        assertEquals(setOf(155), dex.caught)
        assertFalse(dex.national)
    }

    @Test
    fun `SoulGold v1_2 - the same save, the same dex`() {
        val dex = state("soulgold_v12", NATIVE_SOULGOLD_V1_2)
        assertEquals(setOf(16, 19, 155, 163, 183, 263), dex.seen)
        assertEquals(setOf(155), dex.caught)
    }

    @Test
    fun `Seaglass - Hoenn seen 2, own 1`() {
        // Its own numbering: TORCHIC is No. 4, POOCHYENA No. 12.
        val dex = state("emerald_seaglass_dex", NATIVE_EMERALD_SEAGLASS)
        assertEquals(setOf(4, 12), dex.seen)
        assertEquals(setOf(4), dex.caught)
    }

    @Test
    fun `TMT2 - CHIMCHAR (its No 397) seen and caught`() {
        val dex = state("tmt2_dex", NATIVE_TMT2)
        assertEquals(setOf(397), dex.seen)
        assertEquals(setOf(397), dex.caught)
    }

    @Test
    fun `Odyssey entries`() = roms("Pokémon Odyssey (English) (v4.1.1).gba", GameKind.ODYSSEY, POKEDEX_ODYSSEY) { t ->
        assertEquals("Seed", PokedexSource.entry(t, 1)!!.category)
        val roserade = PokedexSource.entry(t, 387)!!
        assertEquals(252, roserade.species)
        assertEquals("Bouquet", roserade.category)
        assertEquals(listOf("Natural Cure", "Poison Point"), roserade.abilities)
        assertEquals(listOf("Fairy"), PokedexSource.entry(t, 35)!!.types)
    }

    @Test
    fun `Radical Red entries`() = roms("Pokemon - Radical Red (v4.1).gba", GameKind.RADICAL_RED, POKEDEX_RADICAL_RED) { t ->
        val charmander = PokedexSource.entry(t, 4)!!
        assertEquals("Lizard", charmander.category)
        assertEquals("Solar Power", charmander.hiddenAbility)
        assertEquals(listOf("Fairy"), PokedexSource.entry(t, 35)!!.types)
    }

    @Test
    fun `Amethyst entries`() = roms("Pokemon Amethyst (v1.3.0).gba", GameKind.AMETHYST, POKEDEX_AMETHYST) { t ->
        val tepig = PokedexSource.entry(t, 340)!!
        assertEquals(551, tepig.species)
        assertEquals("Fire Pig", tepig.category)
        assertEquals(340, PokedexSource.nationalOf(t, 551))
        assertEquals("Seed", PokedexSource.entry(t, 301)!!.category)
    }

    @Test
    fun `Amethyst v1_4_1 entries`() = roms("Pokemon Amethyst (v1.4.1).gba", GameKind.AMETHYST, POKEDEX_AMETHYST_V1_4_1) { t ->
        val tepig = PokedexSource.entry(t, 340)!!
        assertEquals(551, tepig.species)
        assertEquals("Fire Pig", tepig.category)
        assertEquals(340, PokedexSource.nationalOf(t, 551))
        assertEquals("Seed", PokedexSource.entry(t, 301)!!.category)
    }

    @Test
    fun `Gaia entries`() = roms("Pokemon - Gaia (v3.2).gba", GameKind.GAIA, POKEDEX_GAIA) { t ->
        val chimchar = PokedexSource.entry(t, 390)!!
        assertEquals(443, chimchar.species)
        assertEquals("Chimp", chimchar.category)
        assertFalse(t.hasRegional)
    }

    @Test
    fun `Celia entries`() = roms("Pokemon Celia's Stupid Romhack (v1.1.4).gba", GameKind.CELIA, POKEDEX_CELIA) { t ->
        val charmander = PokedexSource.entry(t, 2)!!
        assertEquals(3, charmander.species)
        assertEquals("FIGHTING", charmander.category)
        assertEquals(2, PokedexSource.nationalOf(t, 3))
        assertEquals(150, PokedexSource.entry(t, 150)!!.national)
    }

    @Test
    fun `Heart and Soul entries and the Johto order`() =
        roms("Pokémon Heart and Soul (v2.0.6).gba", GameKind.HEART_AND_SOUL, POKEDEX_HEART_AND_SOUL) { t ->
            val johto = PokedexSource.regionalOrder(t)!!
            assertEquals(listOf(152, 153, 154, 155, 156, 157, 158, 159, 160, 16), johto.take(10))
            assertEquals(251, johto.last())
            val cyndaquil = PokedexSource.entry(t, 155)!!
            assertEquals("FIRE MOUSE", cyndaquil.category.uppercase())
            assertEquals(listOf("Blaze"), cyndaquil.abilities.map { it.lowercase().replaceFirstChar(Char::uppercase) })
            assertEquals(5, cyndaquil.heightDm)
        }

    @Test
    fun `Lazarus entries`() = roms("Pokemon Lazarus (v2.0).gba", GameKind.LAZARUS, POKEDEX_LAZARUS) { t ->
        val pikachu = PokedexSource.entry(t, 25)!!
        assertEquals("Mouse", pikachu.category)
        assertEquals(listOf("Static"), pikachu.abilities)
        assertEquals("Lightning Rod", pikachu.hiddenAbility)
        assertEquals(60, pikachu.weightHg)
        assertTrue(653 in PokedexSource.regionalOrder(t)!!)
    }

    @Test
    fun `SoulGold entries and the Johto order`() = roms("Pokemon-SoulGold-v1.1.4.gba", GameKind.SOULGOLD, POKEDEX_SOULGOLD) { t ->
        val pikachu = PokedexSource.entry(t, 25)!!
        assertEquals(listOf("Static"), pikachu.abilities)
        assertEquals("Lightning Rod", pikachu.hiddenAbility)
        assertEquals(4 to 60, pikachu.heightDm to pikachu.weightHg)
        assertEquals("Fire Mouse", PokedexSource.entry(t, 155)!!.category)
        val johto = PokedexSource.regionalOrder(t)!!
        assertEquals(23, johto.indexOf(25) + 1) // the game's Johto 023
        assertEquals(136, johto.indexOf(155) + 1)
    }

    @Test
    fun `SoulGold v1_2 entries and the Johto order`() = roms("Soulgold (v1.2).gba", GameKind.SOULGOLD, POKEDEX_SOULGOLD_V1_2) { t ->
        val pikachu = PokedexSource.entry(t, 25)!!
        assertEquals(listOf("Static"), pikachu.abilities)
        assertEquals("Lightning Rod", pikachu.hiddenAbility)
        assertEquals("Fire Mouse", PokedexSource.entry(t, 155)!!.category)
        assertEquals(136, PokedexSource.regionalOrder(t)!!.indexOf(155) + 1)
    }

    @Test
    fun `SoulGold v1_2 second build entries and the Johto order`() = roms("Pokemon-SoulGold-v1.2.gba", GameKind.SOULGOLD, POKEDEX_SOULGOLD_V1_2B) { t ->
        val pikachu = PokedexSource.entry(t, 25)!!
        assertEquals(listOf("Static"), pikachu.abilities)
        assertEquals("Lightning Rod", pikachu.hiddenAbility)
        assertEquals("Fire Mouse", PokedexSource.entry(t, 155)!!.category)
        assertEquals(136, PokedexSource.regionalOrder(t)!!.indexOf(155) + 1)
    }

    @Test
    fun `Seaglass entries`() = roms("Pokemon Emerald Seaglass (v3.0).gba", GameKind.EMERALD_SEAGLASS, POKEDEX_EMERALD_SEAGLASS) { t ->
        assertEquals(252, PokedexSource.speciesFor(t, 1)) // TREECKO
        assertEquals(1, PokedexSource.speciesFor(t, 141)) // BULBASAUR
        assertEquals(255, PokedexSource.entry(t, 4)!!.species) // TORCHIC
    }

    @Test
    fun `TMT2 entries and the Hoenn order`() = roms("Pokemon Too Many Types 2 (v1.5.2).gba", GameKind.TMT2, POKEDEX_TMT2) { t ->
        val hoenn = PokedexSource.regionalOrder(t)!!
        assertEquals(listOf(913, 914, 915, 397), hoenn.take(4))
        val chimchar = PokedexSource.entry(t, 397)!!
        assertEquals(390, chimchar.species)
        assertEquals("Chimp", chimchar.category)
        assertEquals(listOf("Blaze"), chimchar.abilities)
        assertEquals("Iron Fist", chimchar.hiddenAbility)
        assertEquals(62, chimchar.weightHg)
    }

    @Test
    fun `Emerald Rogue entries and the MODERN list`() = roms("Pokemon Emerald Rogue (v2.2.1-EX).gba", GameKind.EMERALD_ROGUE, POKEDEX_EMERALD_ROGUE) { t ->
        assertEquals(179, PokedexSource.regionalOrder(t)!!.first()) // MAREEP is MODERN's No. 001
        assertEquals("Seed", PokedexSource.entry(t, 1)!!.category)
        assertEquals(1305, PokedexSource.speciesFor(t, 921)) // PAWMI
    }

    @Test
    fun `Quetzal entries`() = roms("PokemonQuetzalEnglishAlpha9v0.gba", GameKind.QUETZAL, POKEDEX_QUETZAL) { t ->
        val charmander = PokedexSource.entry(t, 4)!!
        assertEquals("Lizard" to (6 to 85), charmander.category to (charmander.heightDm to charmander.weightHg))
        assertEquals(listOf("Blaze"), charmander.abilities)
        assertEquals("Solar Power", charmander.hiddenAbility)
        assertEquals(31, charmander.genderRatio)
        assertEquals(1244, PokedexSource.speciesFor(t, 915)) // LECHONK
    }

    @Test
    fun `Quetzal Spanish entries`() = roms("QuetzalDaisy/PokemonQuetzalSpanishAlpha9v0.gba", GameKind.QUETZAL, POKEDEX_QUETZAL_ES) { t ->
        val charmander = PokedexSource.entry(t, 4)!!
        assertEquals("Lagartija" to (6 to 85), charmander.category to (charmander.heightDm to charmander.weightHg))
        assertTrue(charmander.description.startsWith("Prefiere las cosas calientes"))
        assertEquals(1244, PokedexSource.speciesFor(t, 915)) // LECHONK
    }

    @Test
    fun `Unbound FR entries in French`() = roms("Pokémon Unbound v2.1.1.1 FR.gba", GameKind.UNBOUND, POKEDEX_UNBOUND_FR) { t ->
        val charmander = PokedexSource.entry(t, 4)!!
        assertEquals("Lézard" to (6 to 85), charmander.category to (charmander.heightDm to charmander.weightHg))
        assertEquals(listOf("Brasier"), charmander.abilities)
    }

    @Test
    fun `Orange Islands entries - FireRed rev 0's tables, edited`() = roms("Pokemon Orange Islands.gba", GameKind.ORANGE_ISLANDS, POKEDEX_ORANGE_ISLANDS) { t ->
        val charmander = PokedexSource.entry(t, 4)!!
        assertEquals("LIZARD" to (6 to 85), charmander.category to (charmander.heightDm to charmander.weightHg))
        assertEquals(listOf("BLAZE"), charmander.abilities)
    }

    @Test
    fun `Imperium entries and the Hoenn order`() = roms("Emerald Imperium (v1.3.1).gba", GameKind.IMPERIUM, POKEDEX_IMPERIUM) { t ->
        assertEquals(252, PokedexSource.regionalOrder(t)!!.first())
        val charmander = PokedexSource.entry(t, 4)!!
        assertEquals("Lizard" to (6 to 85), charmander.category to (charmander.heightDm to charmander.weightHg))
        assertEquals(listOf("Blaze"), charmander.abilities)
    }

    @Test
    fun `Glazed entries in its own numbering`() = roms("Glazed (9.2.0).gba", GameKind.GLAZED, POKEDEX_EMERALD) { t ->
        val chimchar = PokedexSource.entry(t, 322)!!
        assertEquals(298 to "CHIMP", chimchar.species to chimchar.category)
        assertEquals(5 to 62, chimchar.heightDm to chimchar.weightHg)
    }

    /** Loads the user's ROM (or skips), checks [check], then that every entry
     * the national and regional lists show decodes to clean text. */
    private fun roms(file: String, game: GameKind, t: PokedexTables, check: (PokedexTables) -> Unit) {
        val rom = RomFileReader.load("$ROM_DIR/$file")
        assumeTrue("$file not on this machine", rom != null)
        activeGame = game
        PokedexSource.reader = rom!!
        check(t)
        val listed = (1..t.nationalCount).filter { PokedexSource.speciesFor(t, it) != 0 }
        // Lazarus and SoulGold leave many numbers without a species (disabled / unused).
        assertTrue("most numbers have a species", listed.size > t.nationalCount * 9 / 10 || game == GameKind.LAZARUS || game == GameKind.SOULGOLD)
        if (t.hasRegional && t.regionalOrder != 0L) {
            val order = PokedexSource.regionalOrder(t)!!
            assertEquals(t.regionalCount, order.size)
            assertTrue("regional entries are national numbers", order.all { it in listed })
        }
        for (n in listed) {
            val e = PokedexSource.entry(t, n)
            assertNotNull("entry $n", e)
            assertTrue("entry $n category '${e!!.category}'", e.category.isNotBlank() && Gen3Text.UNKNOWN !in e.category)
            // TMT2's MissingNo. (No. 1034) has deliberately glitched text.
            if (!(game == GameKind.TMT2 && n == 1034)) {
                assertTrue("entry $n description '${e.description}'", Gen3Text.UNKNOWN !in e.description)
            }
            assertTrue("entry $n abilities ${e.abilities}", e.abilities.none { Gen3Text.UNKNOWN in it })
        }
    }

    private companion object {
        val ROM_DIR: String = System.getProperty("romDir") ?: RETAIL_ROM_DIR.trimEnd('/')
    }
}
