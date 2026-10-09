package com.pokedaisy.app.companion.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Item descriptions come from the player's ROM (RomItemText), not bundled text:
 * each game's table ([NativeConfig.itemDescs], or the QoL builds' found by shape)
 * read for a few known items. A game whose ROM isn't on this machine is skipped
 * (the ROMs aren't in the repo; `-DromDir=` points elsewhere). Checked once against
 * every bundled table these replaced (2026-10-09): see docs/DEVELOPMENT.md.
 */
class RomItemTextTest {
    private class Case(
        val rom: String, val kind: GameKind, val cfg: NativeConfig?, val expect: Map<Int, String>,
    )

    private val multiLanguage = System.getProperty("user.home") + "/Downloads/Pokemon Multi Language/"

    private val cases = listOf(
        // FireRed's TMs point at their moves' descriptions (TM01 = FOCUS PUNCH's).
        Case("Pokemon - FireRed Version (USA, Europe) (Rev 1).gba", GameKind.FIRERED, NATIVE_FIRERED_REV1,
            mapOf(13 to "by 20 points", 289 to "executed last")),
        Case("Pokemon - LeafGreen Version (USA, Europe) (Rev 1).gba", GameKind.FIRERED, NATIVE_LEAFGREEN_REV1, mapOf(13 to "20 points")),
        Case("Pokemon - Emerald Version (USA, Europe).gba", GameKind.EMERALD, NATIVE_EMERALD_RETAIL, mapOf(13 to "by 20 points")),
        // Ruby's own wording, not Emerald's; nothing past its last item.
        Case("Pokemon - Ruby Version (USA, Europe) (Rev 1).gba", GameKind.EMERALD, NATIVE_RUBY,
            mapOf(10 to "more turns are taken", 349 to "")),
        Case("Pokemon - Sapphire Version (USA, Europe) (Rev 2).gba", GameKind.EMERALD, NATIVE_SAPPHIRE, mapOf(13 to "20 points")),
        // The QoL builds have no config: gItems is found by its shape.
        Case("firered-qol.gba", GameKind.FIRERED, null, mapOf(13 to "20 points", 289 to "executed last")),
        Case("emerald-qol.gba", GameKind.EMERALD, null, mapOf(13 to "20 points")),
        Case("emerald-multilang/Pokemon - Edicion Esmeralda (Spain).gba", GameKind.EMERALD, NATIVE_EMERALD_ES,
            mapOf(13 to "Restaura 20 PS", 310 to "2.º turno")),
        Case("emerald-multilang/Pokemon - Smaragd-Edition (Germany).gba", GameKind.EMERALD, NATIVE_EMERALD_DE, mapOf(13 to "um 20 Punkte")),
        Case("emerald-multilang/Pokemon - Version Emeraude (France).gba", GameKind.EMERALD, NATIVE_EMERALD_FR, mapOf(13 to "de 20 points")),
        Case("emerald-multilang/Pokemon - Versione Smeraldo (Italy).gba", GameKind.EMERALD, NATIVE_EMERALD_IT, mapOf(13 to "20 PS")),
        Case("emerald-multilang/Pocket Monsters - Emerald (Japan).gba", GameKind.EMERALD, NATIVE_EMERALD_JA, mapOf(13 to "20　かいふく")),
        Case("Pokémon Unbound (v2.1.1.1).gba", GameKind.UNBOUND, NATIVE_UNBOUND_WITH_DEX, mapOf(16 to "frostbite")),
        Case("Pokemon - Gaia (v3.2).gba", GameKind.GAIA, NATIVE_GAIA_V3_2, mapOf(13 to "20 points")),
        Case("Pokémon Odyssey (English) (v4.1.1).gba", GameKind.ODYSSEY, NATIVE_ODYSSEY, mapOf(13 to "restores 50 HP")),
        Case("Pokemon Amethyst (v1.4.1).gba", GameKind.AMETHYST, NATIVE_AMETHYST_V1_4_1, mapOf(13 to "20 points")),
        // By slot, as the game looks it up (slots 192 / 193 carry a stale itemId 255).
        Case("Pokemon - Radical Red (v4.1).gba", GameKind.RADICAL_RED, NATIVE_RADICAL_RED_V4_1,
            mapOf(13 to "20 points", 675 to "special moves", 255 to "Beauty condition")),
        Case("Pokemon Celia's Stupid Romhack (v1.1.4).gba", GameKind.CELIA, NATIVE_CELIA, mapOf(28 to "80 points")),
        Case("Pokemon Too Many Types 2 (v1.5.2).gba", GameKind.TMT2, NATIVE_TMT2, mapOf(28 to "20 points")),
        Case("Pokémon Heart and Soul (v2.0.6).gba", GameKind.HEART_AND_SOUL, NATIVE_HEART_AND_SOUL, mapOf(28 to "by 20 points")),
        Case("Pokemon Lazarus (v2.0).gba", GameKind.LAZARUS, NATIVE_LAZARUS, mapOf(28 to "by 20 points")),
        Case("Pokemon-SoulGold-v1.1.4.gba", GameKind.SOULGOLD, NATIVE_SOULGOLD, mapOf(28 to "by 20 points", SOULGOLD_V12_TM75 to "Attack")),
        // v1.2's TM75 is Agility, with its own text.
        Case("Soulgold (v1.2).gba", GameKind.SOULGOLD, NATIVE_SOULGOLD_V1_2, mapOf(28 to "by 20 points", SOULGOLD_V12_TM75 to "Speed")),
        Case("Pokemon-SoulGold-v1.2.gba", GameKind.SOULGOLD, NATIVE_SOULGOLD_V1_2B, mapOf(SOULGOLD_V12_TM75 to "Speed")),
        // Rogue's own items (827+) are in a second table.
        Case("Pokemon Emerald Rogue (v2.2.1-EX).gba", GameKind.EMERALD_ROGUE, NATIVE_EMERALD_ROGUE,
            mapOf(39 to "by 20 points", 827 to "species of Pokémon evolve")),
        Case("Pokemon Emerald Seaglass (v3.0).gba", GameKind.EMERALD_SEAGLASS, NATIVE_EMERALD_SEAGLASS, mapOf(28 to "by 20 points")),
        Case("Glazed (9.2.0).gba", GameKind.GLAZED, NATIVE_GLAZED, mapOf(168 to "donut")),
        Case("Emerald Imperium (v1.3.1).gba", GameKind.IMPERIUM, NATIVE_IMPERIUM, mapOf(28 to "by 20 points")),
        Case("PokemonQuetzalEnglishAlpha9v0.gba", GameKind.QUETZAL, NATIVE_QUETZAL, mapOf(28 to "by 20 points")),
        // Its Spanish release has its own (Spanish) descriptions; Unbound's French translation rewrote them in place.
        Case("QuetzalDaisy/PokemonQuetzalSpanishAlpha9v0.gba", GameKind.QUETZAL, NATIVE_QUETZAL_ES, mapOf(28 to "Restaura 20 PS")),
        Case("Pokémon Unbound v2.1.1.1 FR.gba", GameKind.UNBOUND, NATIVE_UNBOUND_FR, mapOf(13 to "Rend 20 PV")),
        Case("Pokemon Orange Islands.gba", GameKind.ORANGE_ISLANDS, NATIVE_ORANGE_ISLANDS, mapOf(13 to "20 points")),
        Case("Pokémon R.O.W.E. (v2.1.9.1 Experimental).gba", GameKind.ROWE, NATIVE_ROWE, mapOf(28 to "by 20 points")),
    )

    /** What the Poller does on detecting [cfg]'s game: its table, or the vanilla one found by shape. */
    private fun select(file: File, kind: GameKind, cfg: NativeConfig?): RomFileReader {
        val rom = RomFileReader(file.readBytes())
        activeGame = kind
        romLanguage = cfg?.language ?: 'E'
        romGameCode = cfg?.gameCode.orEmpty()
        PokedexSource.reader = rom
        val table = cfg?.itemDescs ?: RomItemText.findVanillaItems(rom, file.length())
        assertNotNull(file.name, table)
        assertTrue("${file.name}: the table isn't where it should be", RomItemText.matchesRom(rom, table!!))
        RomItemText.use(table)
        return rom
    }

    @After fun reset() {
        RomItemText.use(null)
        activeGame = GameKind.FIRERED
        romLanguage = 'E'
        romGameCode = ""
    }

    @Test fun `each game's own text, from its rom`() {
        val present = cases.filter { File(RETAIL_ROM_DIR + it.rom).isFile }
        assumeTrue("no ROMs on this machine", present.isNotEmpty())
        for (c in present) {
            select(File(RETAIL_ROM_DIR + c.rom), c.kind, c.cfg)
            for ((id, want) in c.expect) {
                val got = itemDescription(id)
                if (want.isEmpty()) assertEquals("${c.rom} item $id", "", got)
                else assertTrue("${c.rom} item $id: '$got'", want in got)
            }
            // One line: the game's line breaks are spaces now, collapsed.
            assertTrue(c.rom, "  " !in itemDescription(c.expect.keys.first()))
        }
    }

    /** scripts/port_retail.py's configs carry their ROM's table too. */
    @Test fun `ported languages`() {
        val roms = File(multiLanguage).walkTopDown().filter { it.isFile && it.name.endsWith(".gba") }.toList()
        val ports = mapOf("BPRD0" to "um 20 Punkte", "AXVJ1" to "20　かいふく", "BPGS0" to "20 PS")
        var checked = 0
        for ((key, want) in ports) {
            val rom = roms.firstOrNull { f ->
                f.inputStream().use { s -> val h = ByteArray(0xC0); s.read(h); String(h, 0xAC, 4) == key.dropLast(1) && h[0xBC].toInt() == key.last().digitToInt() }
            } ?: continue
            select(rom, if (key.startsWith("BP")) GameKind.FIRERED else GameKind.EMERALD, RETAIL_PORTS.getValue(key)())
            assertTrue("$key: '${itemDescription(13)}'", want in itemDescription(13))
            checked++
        }
        assumeTrue("no ported ROMs on this machine", checked > 0)
    }

    /** Every item in a later Radical Red save's bag has its description (they're past vanilla's ids). */
    @Test fun `radical red's bag`() {
        val file = File(RETAIL_ROM_DIR + "Pokemon - Radical Red (v4.1).gba")
        assumeTrue(file.isFile)
        select(file, GameKind.RADICAL_RED, NATIVE_RADICAL_RED_V4_1)
        decodeNative("radical_red_1636", NATIVE_RADICAL_RED_V4_1).items.forEach {
            assertTrue("no description for ${it.itemId}", itemDescription(it.itemId).isNotEmpty())
        }
    }

    @Test fun `no rom, no text`() {
        RomItemText.use(null)
        assertEquals("", itemDescription(13))
    }

    @Test fun `yellow keeps ours`() {
        activeGame = GameKind.YELLOW
        RomItemText.use(null)
        assertTrue(itemDescriptionsYellow.keys.all { itemDescription(it).isNotEmpty() })
        assertTrue(tmDescriptionsYellow.keys.all { itemDescription(it).isNotEmpty() })
    }
}
