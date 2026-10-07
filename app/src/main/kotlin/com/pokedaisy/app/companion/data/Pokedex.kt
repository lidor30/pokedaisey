package com.pokedaisy.app.companion.data

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap

/** Which memory a game's seen / caught arrays are offsets into. */
enum class DexFlagBlock { SAVE_BLOCK_1, SAVE_BLOCK_2, FIXED }

/**
 * Where the save keeps its seen / caught bits: two [bytes]-long bit arrays at
 * [seen] / [caught] in [block] ([DexFlagBlock.FIXED]: absolute addresses).
 * No. n is bit n - [firstBit] (bit n-1, like the games' own
 * GetSetPokedexFlag; Celia's hack keeps No. n at bit n).
 */
data class DexFlags(
    val block: DexFlagBlock,
    val seen: Long,
    val caught: Long,
    val bytes: Int,
    /**
     * SaveBlock1 offsets of FireRed / Emerald's two anti-cheat copies of
     * `seen`. When set, a bit only counts if every copy agrees, and caught
     * needs seen too - exactly what the game's own check does.
     */
    val seenCopies: List<Long> = emptyList(),
    val firstBit: Int = 1,
)

/** struct Pokedex in SaveBlock2 (+0x18: owned[52] at +0x10, seen[52] at +0x44) + SaveBlock1's copies. */
private fun vanillaDexFlags(seen1: Long, seen2: Long) = DexFlags(DexFlagBlock.SAVE_BLOCK_2, 0x5C, 0x28, 52, listOf(seen1, seen2))
val FIRERED_DEX_FLAGS = vanillaDexFlags(0x5F8, 0x3A18)
val EMERALD_DEX_FLAGS = vanillaDexFlags(0x988, 0x3B24)

/**
 * pokeemerald-expansion keeps a species' whole dex page in its gSpeciesInfo
 * entry ([PokedexTables.speciesInfo] is entry 0, [stride] bytes each) rather
 * than in gPokedexEntries / gMonFrontPicTable. Base stats (0-5) and types
 * (6, 7) are where vanilla has them; everything else moves per release, so
 * each hack lists its own offsets - found by dumping a known species' entry
 * and checking it field by field (natDexNum 25 / height 4 / weight 60 for
 * PIKACHU, its dex text, LZ77 / smol pic headers, the vanilla footprint bytes).
 */
data class SpeciesInfoDex(
    val stride: Int,
    val catchRateOff: Int = 0x08,
    val genderOff: Int = 0x12,
    val eggGroupsOff: Int = 0x16,
    /** Three u16 abilities, the third the hidden one. */
    val abilitiesOff: Int = 0x18,
    /** categoryName, inline (13 bytes). */
    val categoryOff: Int = 0x1F,
    /** natDexNum, then height and weight (u16 each). */
    val natDexOff: Int = 0x3C,
    val descriptionOff: Int = 0x4C,
    /** frontPic (LZ77 or smol, may hold two frames). */
    val frontPicOff: Int = 0x58,
    /** palette: LZ77 if it starts with 0x10, else 16 raw colours. */
    val paletteOff: Int,
    /** footprint (32 bytes, 1bpp); -1 = the game has none. */
    val footprintOff: Int = -1,
)

/**
 * Where the POKéDEX tab reads from: the ROM's own dex tables (entries,
 * front sprites, base stats, ...) and where the save keeps the seen / caught
 * flags. Only games with a config get the tab.
 *
 * The retail ROM addresses were found by matching the tables of a decomp
 * build (pokefirered_rev1.map / pokeemerald.map) against the retail ROMs
 * (FireRed rev 1 sha1 dd5945db…, Emerald sha1 f3ae0881…): the pointer-free
 * tables byte-for-byte, the rest by record shape, then decoded and checked
 * (entry 1 = SEED, 2'04", 15.2 lbs.). LeafGreen (pret/pokefirered @ c75f352
 * `leafgreen` / `leafgreen_rev1`) and Ruby / Sapphire (pret/pokeruby
 * `ruby_rev1/2`, `sapphire_rev1/2`) were each built unmodified to a ROM
 * byte-identical to the user's retail dump, so their addresses are straight
 * from those builds' .map files; the static tables a map doesn't list
 * (LeafGreen's species -> national, R/S's footprints) by content. The ROM
 * hacks' were found by content in their own ROMs (see each config).
 */
data class PokedexTables(
    /** gPokedexEntries: [entryStride]-byte records, indexed by national dex number. */
    val entries: Long,
    /** gMonFrontPicTable: {LZ77 tiles ptr, u16 size, u16 tag}, by species. */
    val frontPics: Long,
    /** gMonPaletteTable: {LZ77 palette ptr, u16 tag, u16 pad}, by species. */
    val palettes: Long,
    /** gSpeciesInfo (gBaseStats): 28-byte records, by species ([expansion]: its entry 0). */
    val speciesInfo: Long,
    /** sSpeciesToNationalPokedexNum: u16 per species, starting at species 1. */
    val speciesToNational: Long,
    /** gAbilityNames: [abilityNameLength]-byte strings, by ability id. */
    val abilityNames: Long,
    /** gMonFootprintTable: ptr to a 16x16 1bpp footprint, by species; 0 = none. */
    val footprints: Long,
    val abilityNameLength: Int = 13,
    /** Bytes from one ability name to the next; 0 = [abilityNameLength]
     * (expansion's gAbilitiesInfo has a description and AI rating after it). */
    val abilityNameStride: Int = 0,
    /** gSpeciesInfo offset of a third (hidden) ability byte; -1 = none (vanilla). */
    val hiddenAbilityOff: Int = -1,
    /** After the category on the dex page, in the game's own casing. */
    val categorySuffix: String = "POKéMON",
    /** FireRed's entries carry an extra unused description pointer (0x24); Emerald's don't (0x20). */
    val entryStride: Int = 0x24,
    /** Ruby/Sapphire split the dex text over two pages: the second page's
     * pointer's offset in the entry (0x14); -1 = one page (FireRed's 0x14 is unused). */
    val descriptionPage2Off: Int = -1,
    val speciesCount: Int = 412,
    val nationalCount: Int = 386,
    /** The regional dex before the National Dex is unlocked (Kanto: 1-151). */
    val regionalCount: Int = 151,
    /** null = the game has one dex only (no regional / national switch). */
    val regionName: String? = "KANTO",
    /** National numbers in regional-dex order (Emerald's sHoennToNationalOrder);
     * 0 = the regional dex is national 1..[regionalCount] (Kanto). */
    val regionalOrder: Long = 0,
    /** Bytes per [regionalOrder] entry: u16, or u32 (Heart and Soul's Johto). */
    val regionalOrderStride: Int = 2,
    /** [regionalOrder] lists species ids, not national numbers (Unbound). */
    val regionalOrderIsSpecies: Boolean = false,
    /** u16 species per dex number, from No. 0 - read instead of inverting
     * [speciesToNational] (Celia's hack, whose table is only this way round). */
    val nationalToSpecies: Long = 0,
    /** [entries] is indexed by species, not by dex number (Celia's hack). */
    val entriesBySpecies: Boolean = false,
    /** The dex page lives in gSpeciesInfo (pokeemerald-expansion); null = vanilla tables. */
    val expansion: SpeciesInfoDex? = null,
    val flags: DexFlags = FIRERED_DEX_FLAGS,
    /** The flags' SaveBlock2 struct Pokedex, whose nationalMagic says the National Dex is unlocked. */
    val sb2DexOff: Long = 0x18,
    /** pokedex.nationalMagic's offset and the value meaning "unlocked";
     * -1 = the National Dex is always there (Unbound). */
    val nationalMagicOff: Int = 3,
    val nationalMagic: Int = 0xB9,
    /** gEvolutionTable: 5 {u16 method, u16 param, u16 target, pad} per species
     * (see [EvolutionSource]); 0 = not located for this game yet. Found by
     * searching the retail ROMs for BULBASAUR's row (LEVEL 16 -> IVYSAUR). */
    val evolutions: Long = 0,
) {
    val hasRegional: Boolean get() = regionName != null
}

val POKEDEX_FIRERED_REV1 = PokedexTables(
    entries = 0x0844E8B0L,
    frontPics = 0x0823511CL,
    palettes = 0x0823737CL,
    speciesInfo = 0x082547F4L,
    speciesToNational = 0x0825205EL,
    abilityNames = 0x0824FCB0L,
    footprints = 0x0843FB20L,
    evolutions = 0x082597C4L,
)

/**
 * CFRU moves the flags out of struct Pokedex into SaveBlock1 (the old bag
 * area): seen at +0x310, owned at +0x38D, 1000 bits each, no anti-cheat copies.
 */
val CFRU_DEX_FLAGS = DexFlags(DexFlagBlock.SAVE_BLOCK_1, 0x310, 0x38D, 125)

/**
 * Pokémon Unbound v2.1.1.1 (sha1 b4776b82…), a closed-source CFRU/DPE hack:
 * no map file, so every table was found in the ROM by content (Bulbasaur's
 * base stats / dex entry, {ptr, size, tag = species} pic records, the
 * species -> national u16 run starting 1, 2, 3...) and checked by decoding
 * (sprites rendered and looked at; 905 entries through ENAMORUS). The flags
 * were found in a headless capture of the user's save - the only bits set
 * matched the party's GIBLE (owned) and the game's own "Seen: National 4" -
 * then confirmed by poking one more bit into each array and watching the
 * game's POKéDEX counts go up by one. The Borrius order was read off the
 * game's own "Numerical Mode: Borrius" list in RAM, then found in the ROM.
 */
val POKEDEX_UNBOUND = PokedexTables(
    entries = 0x09A357CCL,
    frontPics = 0x09A1D5B4L,
    palettes = 0x09A32BDCL,
    speciesInfo = 0x099E0C9CL,
    speciesToNational = 0x09A41FECL,
    abilityNames = 0x08A36398L,
    footprints = 0,
    abilityNameLength = 17,
    hiddenAbilityOff = 0x1A,
    categorySuffix = "Pokémon",
    speciesCount = 1294,
    nationalCount = 905,
    regionalCount = 498,
    regionName = "BORRIUS",
    regionalOrder = 0x09A3F398L,
    regionalOrderIsSpecies = true,
    nationalMagicOff = -1,
    flags = CFRU_DEX_FLAGS,
)

/** LeafGreen: FireRed's layout, its own tables (and dex text). */
val POKEDEX_LEAFGREEN_REV0 = PokedexTables(
    entries = 0x0844E270L,
    frontPics = 0x08235088L,
    palettes = 0x082372E8L,
    speciesInfo = 0x08254760L,
    speciesToNational = 0x08251FCAL,
    abilityNames = 0x0824FC1CL,
    footprints = 0x0843F8ECL,
    evolutions = 0x08259734L,
)

val POKEDEX_LEAFGREEN_REV1 = PokedexTables(
    entries = 0x0844E2E0L,
    frontPics = 0x082350F8L,
    palettes = 0x08237358L,
    speciesInfo = 0x082547D0L,
    speciesToNational = 0x0825203AL,
    abilityNames = 0x0824FC8CL,
    footprints = 0x0843F95CL,
    evolutions = 0x082597A4L,
)

/** Ruby (rev 1 and rev 2 share every address): Emerald's flag rules,
 * seen copies at SaveBlock1+0x938/+0x3A8C, two-page dex text. */
val POKEDEX_RUBY = PokedexTables(
    entries = 0x083B1874L,
    frontPics = 0x081E836CL,
    palettes = 0x081EA5CCL,
    speciesInfo = 0x081FEC30L,
    speciesToNational = 0x081FC52EL,
    abilityNames = 0x081FA260L,
    footprints = 0x083B4F00L,
    evolutions = 0x08203B80L,
    descriptionPage2Off = 0x14,
    regionalCount = 202,
    regionName = "HOENN",
    regionalOrder = 0x081FC864L,
    flags = vanillaDexFlags(0x938, 0x3A8C),
    nationalMagicOff = 2,
    nationalMagic = 0xDA,
)

/** Sapphire (rev 1 and rev 2): Ruby's layout, its own ROM addresses. */
val POKEDEX_SAPPHIRE = POKEDEX_RUBY.copy(
    entries = 0x083B18D0L,
    frontPics = 0x081E82FCL,
    palettes = 0x081EA55CL,
    speciesInfo = 0x081FEBC0L,
    speciesToNational = 0x081FC4BEL,
    abilityNames = 0x081FA1F0L,
    footprints = 0x083B4F5CL,
    evolutions = 0x08203B10L,
    regionalOrder = 0x081FC7F4L,
)

val POKEDEX_EMERALD = PokedexTables(
    entries = 0x0856B5B0L,
    frontPics = 0x0830A18CL,
    palettes = 0x08303678L,
    speciesInfo = 0x083203CCL,
    speciesToNational = 0x0831DC82L,
    abilityNames = 0x0831B6DBL,
    footprints = 0x0856E694L,
    entryStride = 0x20,
    regionalCount = 202, // HOENN_DEX_COUNT (Jirachi and Deoxys included)
    regionName = "HOENN",
    regionalOrder = 0x0831DFB8L,
    flags = EMERALD_DEX_FLAGS,
    nationalMagicOff = 2,
    nationalMagic = 0xDA,
    evolutions = 0x0832531CL,
)

/*
 * The FireRed-engine hacks below keep vanilla's record layouts (28-byte
 * gBaseStats, 0x24-byte gPokedexEntries, {ptr, size, tag} pic tables), just
 * relocated and longer. Each table was found in the hack's own ROM by content
 * - BULBASAUR's base stats and SEED entry, species 1's pic records, a
 * 1, 2, 3, ... species -> dex run, STENCH then DRIZZLE - picking the copy the
 * code references when a stale one is left behind, then checked by decoding
 * (category, height / weight, dex text, abilities) and rendering sprites.
 * The flags were found in headless captures of the user's saves (the bits of
 * the party / rival Pokémon) and confirmed against each game's own POKéDEX
 * screen, poking a bit and watching its counts or list change.
 */

/**
 * Pokémon Odyssey v4.1.1: vanilla rev-0 tables, except the dex entries
 * (moved and grown to 409) and ability names. Its 23 added species reuse
 * the old unused slots 252-274 as dex numbers 387-409 (ROSERADE, FROSLASS,
 * GLACEON, ...); the TALREGA dex is national 1-151. The flags are vanilla
 * struct Pokedex without SaveBlock1's anti-cheat copies; the game's own
 * table of contents read Seen National 8 / Owned 2 for the user's save.
 */
val POKEDEX_ODYSSEY = PokedexTables(
    entries = 0x0920C980L,
    frontPics = 0x082350ACL,
    palettes = 0x0823730CL,
    speciesInfo = 0x08254784L,
    speciesToNational = 0x08251FEEL,
    abilityNames = 0x090B7E00L,
    footprints = 0, // every species points at one blank footprint
    categorySuffix = "Pokémon",
    nationalCount = 409,
    regionName = "TALREGA",
    flags = DexFlags(DexFlagBlock.SAVE_BLOCK_2, 0x5C, 0x28, 52),
    nationalMagicOff = -1, // its table of contents lists National mode from the start
    evolutions = 0x08259754L,
)

/**
 * Pokémon Radical Red v4.1 (CFRU, like Unbound): 1025 national entries,
 * hidden abilities, 17-byte ability names; the regional dex is Kanto 1-151.
 * Its owned array sits 164 bytes after `seen` (SaveBlock1+0x3B4), not at
 * Unbound's +0x38D. The National Dex unlocks the vanilla way: setting
 * nationalMagic made the game's table of contents add National mode.
 */
val POKEDEX_RADICAL_RED = PokedexTables(
    entries = 0x09814030L,
    frontPics = 0x097FA1C4L,
    palettes = 0x09811208L,
    speciesInfo = 0x097B98ECL,
    speciesToNational = 0x098218F0L,
    abilityNames = 0x090E32C0L,
    footprints = 0,
    abilityNameLength = 17,
    hiddenAbilityOff = 0x1A,
    categorySuffix = "Pokémon",
    speciesCount = 1376,
    nationalCount = 1025,
    flags = DexFlags(DexFlagBlock.SAVE_BLOCK_1, 0x310, 0x3B4, 164),
)

/**
 * Pokémon Amethyst v1.3.0 (CFRU): numbers its dex its own way - No. 1-390 is
 * its regional dex (the game calls it KANTO; TEPIG is No. 340) and the rest
 * runs to 690 - and both the flags and gPokedexEntries are indexed by that
 * number. Read off the game's own list in RAM, then found in the ROM: the
 * species -> number table and the entries (a stale entry table with no code
 * references sits before them).
 */
val POKEDEX_AMETHYST = PokedexTables(
    entries = 0x09C13D58L,
    frontPics = 0x09BFDD0CL,
    palettes = 0x09C113F8L,
    speciesInfo = 0x09BC257CL,
    speciesToNational = 0x09C1E4B8L,
    abilityNames = 0x089272F8L,
    footprints = 0,
    abilityNameLength = 17,
    hiddenAbilityOff = 0x1A,
    categorySuffix = "Pokémon",
    speciesCount = 1268,
    nationalCount = 690,
    regionalCount = 390,
    flags = CFRU_DEX_FLAGS,
)

/**
 * Pokémon Gaia v3.2: one dex, national 1-721. Its flags live outside the save
 * blocks, at fixed EWRAM addresses: poking bits there made BULBASAUR (seen)
 * and VENUSAUR (caught) appear in the game's own list.
 */
val POKEDEX_GAIA = PokedexTables(
    entries = 0x087319D0L,
    frontPics = 0x08720100L,
    palettes = 0x08724108L,
    speciesInfo = 0x08ACD000L,
    speciesToNational = 0x0993C1D0L,
    abilityNames = 0x08A66000L,
    footprints = 0x087382BCL,
    categorySuffix = "Pokémon",
    speciesCount = 935,
    nationalCount = 721,
    regionalCount = 721,
    regionName = null,
    flags = DexFlags(DexFlagBlock.FIXED, 0x0203C400L, 0x0203C45BL, 91),
    nationalMagicOff = -1,
)

/**
 * Celia's Stupid Romhack v1.1.4: one 150-entry dex in its own order (No. 1
 * BULBASAUR = species 2, No. 0 is a joke VICTINI), only stored as a
 * number -> species table. Entries are indexed by species, ability names are
 * 19 bytes. The flags are in SaveBlock2 but resized (owned +0x28, seen +0x59,
 * 49 bytes each) and No. n is bit n: poking bits 1-3 marked No. 1-3 in the
 * game's own list.
 */
val POKEDEX_CELIA = PokedexTables(
    entries = 0x08CA6B70L,
    frontPics = 0x0870D508L,
    palettes = 0x08713CDCL,
    speciesInfo = 0x0874B058L,
    speciesToNational = 0,
    nationalToSpecies = 0x08745138L,
    entriesBySpecies = true,
    abilityNames = 0x0873B9B5L,
    abilityNameLength = 19,
    footprints = 0x0874323CL,
    speciesCount = 411,
    nationalCount = 150,
    regionalCount = 150,
    regionName = null,
    flags = DexFlags(DexFlagBlock.SAVE_BLOCK_2, 0x59, 0x28, 49, firstBit = 0),
    nationalMagicOff = -1,
)

/*
 * The pokeemerald-expansion hacks keep the dex page in gSpeciesInfo
 * ([SpeciesInfoDex]) and the flags in SaveBlock1 (dexSeen / dexCaught, no
 * copies), indexed by each game's own natDexNum. Ability names are inline in
 * gAbilitiesInfo (17 bytes, 0x1C apart). The National Dex unlocks the Emerald
 * way (struct Pokedex.nationalMagic = 0xDA). The flags were found from the
 * saves' starter / intro Pokémon, then confirmed in each game's own POKéDEX
 * (opened by calling the game's FlagSet for FLAG_SYS_POKEDEX_GET headlessly)
 * by poking bits, or for Heart and Soul by a wild encounter that added its bit.
 * The regional orders are the ones the games list once every species is
 * marked seen.
 */

/** Pokémon Heart and Soul v2.0.6: national 1-1080, the JOHTO dex is a u32 list (282). */
val POKEDEX_HEART_AND_SOUL = PokedexTables(
    entries = 0,
    frontPics = 0,
    palettes = 0,
    speciesInfo = 0x087E6544L,
    speciesToNational = 0,
    abilityNames = 0x08D35734L,
    footprints = 0,
    abilityNameLength = 17,
    abilityNameStride = 0x1C,
    speciesCount = 1524,
    nationalCount = 1080,
    regionalCount = 282,
    regionName = "JOHTO",
    regionalOrder = 0x08D34C78L,
    regionalOrderStride = 4,
    expansion = SpeciesInfoDex(stride = 0x10C, paletteOff = 0x60, footprintOff = 0x84),
    flags = DexFlags(DexFlagBlock.SAVE_BLOCK_1, 0x39C0, 0x3A7F, 191),
    nationalMagicOff = 2,
    nationalMagic = 0xDA,
)

/** Pokémon Lazarus v2.0: a sparse national dex up to 1028 (unused species are
 * zeroed), the ILIOS dex (430). No footprints. Its menu has no POKéDEX in the
 * user's save, so its flags are matched to the save (FENNEKIN seen and
 * caught), not to the game's screen. */
val POKEDEX_LAZARUS = PokedexTables(
    entries = 0,
    frontPics = 0,
    palettes = 0,
    speciesInfo = 0x08C7A338L,
    speciesToNational = 0,
    abilityNames = 0x088DD968L,
    footprints = 0,
    abilityNameLength = 17,
    abilityNameStride = 0x1C,
    categorySuffix = "Pokémon",
    speciesCount = 1561,
    nationalCount = 1028,
    regionalCount = 430,
    regionName = "ILIOS",
    regionalOrder = 0x088DFC5CL,
    expansion = SpeciesInfoDex(stride = 0xD4, paletteOff = 0x68),
    flags = DexFlags(DexFlagBlock.SAVE_BLOCK_1, 0x32A8, 0x3329, 129),
    nationalMagicOff = 2,
    nationalMagic = 0xDA,
)

/** Emerald Seaglass v3.0: its own numbering puts Hoenn first (TREECKO = No. 1,
 * BULBASAUR = 141), 430 in all; the HOENN dex has 212. */
val POKEDEX_EMERALD_SEAGLASS = PokedexTables(
    entries = 0,
    frontPics = 0,
    palettes = 0,
    speciesInfo = 0x088F0780L,
    speciesToNational = 0,
    abilityNames = 0x086E15B0L,
    footprints = 0,
    abilityNameLength = 17,
    abilityNameStride = 0x1C,
    categorySuffix = "Pokémon",
    speciesCount = 1489,
    nationalCount = 430,
    regionalCount = 212,
    regionName = "HOENN",
    regionalOrder = 0x086E37E0L,
    expansion = SpeciesInfoDex(stride = 0xD0, paletteOff = 0x68),
    flags = DexFlags(DexFlagBlock.SAVE_BLOCK_1, 0x28FC, 0x2932, 54),
    nationalMagicOff = 2,
    nationalMagic = 0xDA,
)

/** Too Many Types 2 v1.5.2: a newer gSpeciesInfo (a third type byte shifts
 * everything after the types by one or two), its own numbering up to 1052
 * (CHIMCHAR = 397), the HOENN dex (398, SPRIGATITO first). */
val POKEDEX_TMT2 = PokedexTables(
    entries = 0,
    frontPics = 0,
    palettes = 0,
    speciesInfo = 0x08712A48L,
    speciesToNational = 0,
    abilityNames = 0x08DF4F9CL,
    footprints = 0,
    abilityNameLength = 17,
    abilityNameStride = 0x1C,
    categorySuffix = "Pokémon",
    speciesCount = 1651,
    nationalCount = 1052,
    regionalCount = 398,
    regionName = "HOENN",
    regionalOrder = 0x08DF4C80L,
    expansion = SpeciesInfoDex(
        stride = 0x104, catchRateOff = 0x09, genderOff = 0x14, eggGroupsOff = 0x18, abilitiesOff = 0x1A,
        categoryOff = 0x21, natDexOff = 0x3E, paletteOff = 0x60, footprintOff = 0x80,
    ),
    flags = DexFlags(DexFlagBlock.SAVE_BLOCK_1, 0x289C, 0x2920, 132),
    nationalMagicOff = 2,
    nationalMagic = 0xDA,
)

/** Pokémon SoulGold v1.1.4: national 1-1025, 323 of them disabled (no species),
 * so its JOHTO dex is the other 702 in national order - a u32 list the game's own
 * national -> Johto lookup (0x081F0D84) scans: PIKACHU is its 023, CHIKORITA 133,
 * CYNDAQUIL 136. The page is in its 0x118-byte gSpeciesInfo (smol front pics, raw
 * palettes, no footprints); ability names 0x20 apart. The flags (dexSeen /
 * dexCaught in SaveBlock1, 0x81 apart) were matched to the game's own POKéDEX
 * (opened via FLAG_SYS_POKEDEX_GET 0x98D headlessly): the save's Seen 6 / Own 1,
 * then Seen 8 / Own 2 after poking PIKACHU seen and CHIKORITA caught. The game
 * never sets an Emerald-style nationalMagic (its "National" label is an
 * all-caught check), so it stays at 0 and the tab opens on Johto, like the game. */
val POKEDEX_SOULGOLD = PokedexTables(
    entries = 0,
    frontPics = 0,
    palettes = 0,
    speciesInfo = 0x087D44A8L,
    speciesToNational = 0,
    abilityNames = 0x08F04C28L,
    footprints = 0,
    abilityNameLength = 17,
    abilityNameStride = 0x20,
    categorySuffix = "Pokémon",
    speciesCount = 1578,
    nationalCount = 1025,
    regionalCount = 702,
    regionName = "JOHTO",
    regionalOrder = 0x08F03DD8L,
    regionalOrderStride = 4,
    expansion = SpeciesInfoDex(
        stride = 0x118, abilitiesOff = 0x18, categoryOff = 0x26, natDexOff = 0x42,
        descriptionOff = 0x50, frontPicOff = 0x5C, paletteOff = 0x64,
    ),
    flags = DexFlags(DexFlagBlock.SAVE_BLOCK_1, 0x31F8, 0x3279, 129),
    nationalMagicOff = 2,
    nationalMagic = 0xDA,
)

/**
 * Whether the running ROM really has these tables where [t] says: entry 1 is
 * BULBASAUR's (SEED, 7 dm, 69 hg), species 1 is dex number 1 and its front
 * pic is tagged 1. Unrecognised hacks of the same size fall back to the
 * retail configs, and must not get a dex built from someone else's tables.
 */
fun pokedexMatchesRom(c: MemoryReader, t: PokedexTables): Boolean = runCatching {
    val e = c.readCoreMemory(t.entries + t.entryStride, 0x10)
    Gen3Text.decode(e, 0, 12).equals("SEED", ignoreCase = true) && u16le(e, 0x0C) == 7 && u16le(e, 0x0E) == 69 &&
        u16le(c.readCoreMemory(t.speciesToNational, 2), 0) == 1 &&
        u16le(c.readCoreMemory(t.frontPics + 8 + 6, 2), 0) == 1
}.getOrDefault(false)

/** The save's dex progress, by national dex number (1-based). */
data class PokedexState(
    val tables: PokedexTables,
    val seen: Set<Int>,
    val caught: Set<Int>,
    /** SaveBlock2's nationalMagic: the National Dex has been unlocked. */
    val national: Boolean,
)

/**
 * The seen / caught flags ([PokedexTables.flags]), checked the way the game's
 * own GetSetPokedexFlag does: where there are anti-cheat copies of `seen`
 * (FireRed / Emerald), a bit only counts if they agree with it, and caught
 * needs seen too. Null if the save blocks aren't loaded yet (title screen).
 */
fun readPokedexState(c: MemoryReader, cfg: NativeConfig, t: PokedexTables): PokedexState? {
    val sb1 = saveBlock1(c, cfg)
    val sb2 = saveBlock2(c, cfg)
    if (sb1 !in 0x02000000L until 0x04000000L || sb2 !in 0x02000000L until 0x04000000L) return null
    val f = t.flags
    val base = when (f.block) {
        DexFlagBlock.SAVE_BLOCK_1 -> sb1
        DexFlagBlock.SAVE_BLOCK_2 -> sb2
        DexFlagBlock.FIXED -> 0L
    }
    val seenBits = c.readCoreMemory(base + f.seen, f.bytes)
    val caughtBits = c.readCoreMemory(base + f.caught, f.bytes)
    val copies = f.seenCopies.map { c.readCoreMemory(sb1 + it, f.bytes) }
    fun bit(b: ByteArray, n: Int): Boolean {
        val i = n - f.firstBit
        return i >= 0 && i / 8 < b.size && (b[i / 8].toInt() shr (i % 8)) and 1 != 0
    }
    val seen = mutableSetOf<Int>()
    val caught = mutableSetOf<Int>()
    for (n in 1..t.nationalCount) {
        val s = bit(seenBits, n)
        if (copies.isEmpty()) {
            if (s) seen.add(n)
            if (bit(caughtBits, n)) caught.add(n)
        } else if (s && copies.all { bit(it, n) }) {
            seen.add(n)
            if (bit(caughtBits, n)) caught.add(n)
        }
    }
    val national = t.nationalMagicOff < 0 ||
        (c.readCoreMemory(sb2 + t.sb2DexOff + t.nationalMagicOff, 1)[0].toInt() and 0xFF) == t.nationalMagic
    return PokedexState(t, seen, caught, national)
}

/** One dex page, straight from the ROM. */
data class DexEntry(
    val national: Int,
    val species: Int,
    val category: String,
    val heightDm: Int,
    val weightHg: Int,
    val description: String,
    val type1: Int,
    val type2: Int,
    /** HP, ATTACK, DEFENSE, SP. ATK, SP. DEF, SPEED. */
    val baseStats: List<Int>,
    val abilities: List<String>,
    val hiddenAbility: String? = null,
    val catchRate: Int,
    /** 0 = always male, 254 = always female, 255 = genderless. */
    val genderRatio: Int,
    val eggGroups: List<String>,
) {
    val types: List<String> get() = monTypes(type1, type2)
}

/**
 * Reads [DexEntry]s and sprites out of the ROM on demand and caches them -
 * like [DecompIconSource], called from Compose's IO dispatcher rather than the
 * emulator thread (ROM data never changes, so a read racing a frame is harmless).
 * [reader] is swappable so tests and previews can read a ROM file instead.
 */
object PokedexSource {
    @Volatile
    var reader: MemoryReader = InProcessReader

    private val entries = ConcurrentHashMap<Int, DexEntry>()
    private val sprites = ConcurrentHashMap<Int, Bitmap>()
    private val footprints = ConcurrentHashMap<Int, Bitmap>()
    @Volatile private var nationalToSpecies: IntArray? = null
    @Volatile private var speciesToNational: IntArray? = null
    @Volatile private var regional: IntArray? = null
    @Volatile private var cachedFor: PokedexTables? = null

    private fun sync(t: PokedexTables) {
        if (cachedFor == t) return
        synchronized(this) {
            if (cachedFor == t) return
            entries.clear(); sprites.clear(); footprints.clear(); nationalToSpecies = null; speciesToNational = null; regional = null
            cachedFor = t
        }
    }

    /** Already-loaded data only (no ROM read) - composables start from these,
     * so a page seen before draws at once instead of after a load. */
    fun cachedEntry(t: PokedexTables, national: Int): DexEntry? = if (cachedFor == t) entries[national] else null
    fun cachedFrontSprite(t: PokedexTables, species: Int): Bitmap? = if (cachedFor == t) sprites[species] else null
    fun cachedFootprint(t: PokedexTables, species: Int): Bitmap? = if (cachedFor == t) footprints[species] else null
    fun cachedSpeciesMap(t: PokedexTables): IntArray? = if (cachedFor == t) nationalToSpecies else null
    fun cachedRegionalOrder(t: PokedexTables): IntArray? =
        if (t.regionalOrder == 0L) IntArray(t.regionalCount) { it + 1 } else if (cachedFor == t) regional else null

    /** National numbers in regional-dex order ([PokedexTables.regionalCount] of them). */
    fun regionalOrder(t: PokedexTables): IntArray? {
        cachedRegionalOrder(t)?.let { return it }
        sync(t)
        return runCatching {
            val w = t.regionalOrderStride
            val raw = rd(t.regionalOrder, t.regionalCount * w)
            IntArray(t.regionalCount) { if (w == 4) u32le(raw, it * 4).toInt() else u16le(raw, it * 2) }.let { ids ->
                if (!t.regionalOrderIsSpecies) ids
                else IntArray(ids.size) { i -> ids[i].takeIf { it in 1 until t.speciesCount }?.let { nationalOf(t, it) } ?: 0 }
            }
        }.getOrNull()?.also { regional = it }
    }

    /** Species id for a national dex number (the first species that maps to it), or 0. */
    fun speciesFor(t: PokedexTables, national: Int): Int {
        sync(t)
        val map = nationalToSpecies ?: runCatching {
            if (t.nationalToSpecies != 0L) {
                val raw = rd(t.nationalToSpecies, (t.nationalCount + 1) * 2)
                IntArray(t.nationalCount + 1) { n -> if (n == 0) 0 else u16le(raw, n * 2).takeIf { it < t.speciesCount } ?: 0 }
            } else {
                val nat = speciesNationals(t)
                IntArray(t.nationalCount + 1).also { out ->
                    for (s in 1 until t.speciesCount) {
                        val n = nat[s]
                        if (n in 1..t.nationalCount && out[n] == 0) out[n] = s
                    }
                }
            }
        }.getOrNull()?.also { nationalToSpecies = it } ?: return 0
        return map.getOrElse(national) { 0 }
    }

    /** National dex number of a species id (Hoenn species aren't in national order), or 0. */
    fun nationalOf(t: PokedexTables, species: Int): Int {
        sync(t)
        val map = speciesToNational ?: runCatching { speciesNationals(t) }.getOrNull()?.also { speciesToNational = it } ?: return 0
        return map.getOrElse(species) { 0 }
    }

    /** Every species' dex number, by species id (0 = none). */
    private fun speciesNationals(t: PokedexTables): IntArray {
        speciesToNational?.let { return it }
        val x = t.expansion
        return when {
            // Each species' natDexNum sits in its own gSpeciesInfo entry.
            x != null -> {
                val raw = rd(t.speciesInfo, t.speciesCount * x.stride)
                IntArray(t.speciesCount) { s -> if (s == 0) 0 else u16le(raw, s * x.stride + x.natDexOff) }
            }
            // Only the other way round in the ROM: invert it.
            t.nationalToSpecies != 0L -> IntArray(t.speciesCount).also { out ->
                val raw = rd(t.nationalToSpecies, (t.nationalCount + 1) * 2)
                for (n in t.nationalCount downTo 1) u16le(raw, n * 2).takeIf { it in 1 until t.speciesCount }?.let { out[it] = n }
            }
            else -> {
                val raw = rd(t.speciesToNational, (t.speciesCount - 1) * 2)
                IntArray(t.speciesCount) { s -> if (s == 0) 0 else u16le(raw, (s - 1) * 2) }
            }
        }.also { speciesToNational = it }
    }

    fun entry(t: PokedexTables, national: Int): DexEntry? {
        sync(t)
        entries[national]?.let { return it }
        return runCatching { readEntry(t, national) }
            .onFailure { android.util.Log.w("pokedaisy", "dex entry $national failed", it) }
            .getOrNull()?.also { entries[national] = it }
    }

    /** The 64x64 front sprite, as the dex page shows it. */
    fun frontSprite(t: PokedexTables, species: Int): Bitmap? {
        sync(t)
        if (species <= 0) return null
        sprites[species]?.let { return it }
        return runCatching {
            val x = t.expansion
            val tilesPtr: Long
            val palPtr: Long
            if (x != null) {
                val info = rd(t.speciesInfo + species.toLong() * x.stride, x.stride)
                tilesPtr = Gfx.u32(info, x.frontPicOff)
                palPtr = Gfx.u32(info, x.paletteOff)
            } else {
                tilesPtr = Gfx.u32(rd(t.frontPics + species * 8L, 8), 0)
                palPtr = Gfx.u32(rd(t.palettes + species * 8L, 8), 0)
            }
            if (!Gfx.inRom(tilesPtr) || !Gfx.inRom(palPtr)) return null
            // Emerald's front pics hold two animation frames (4 KiB); the first is the still.
            val packed = rd(tilesPtr, 0x1800)
            val tiles = if (Smol.isSmol(packed)) Smol.decompress(packed) else Gfx.lz77(packed)
            val pal = rd(palPtr, 64)
            // Expansion keeps some palettes uncompressed: 16 raw colours.
            val colors = if ((pal[0].toInt() and 0xFF) == 0x10) Gfx.lz77(pal) else pal.copyOf(32)
            Gfx.decode4bpp(tiles, 0, colors, 0, 8, 8)
        }.getOrNull()?.also { sprites[species] = it }
    }

    /** The 16x16 footprint (black on transparent). */
    fun footprint(t: PokedexTables, species: Int): Bitmap? {
        sync(t)
        if (species <= 0) return null
        footprints[species]?.let { return it }
        return runCatching {
            val x = t.expansion
            val ptr = when {
                x != null -> if (x.footprintOff < 0) return null else Gfx.u32(rd(t.speciesInfo + species.toLong() * x.stride + x.footprintOff, 4), 0)
                t.footprints == 0L -> return null
                else -> Gfx.u32(rd(t.footprints + species * 4L, 4), 0)
            }
            if (!Gfx.inRom(ptr)) return null
            val bits = rd(ptr, 32)
            // Four 8x8 1bpp tiles (TL, TR, BL, BR), least significant bit leftmost.
            val px = IntArray(16 * 16)
            for (tile in 0 until 4) for (row in 0 until 8) {
                val v = bits[tile * 8 + row].toInt()
                for (col in 0 until 8) if ((v shr col) and 1 != 0) {
                    px[((tile / 2) * 8 + row) * 16 + (tile % 2) * 8 + col] = 0xFF000000.toInt()
                }
            }
            Bitmap.createBitmap(px, 16, 16, Bitmap.Config.ARGB_8888)
        }.getOrNull()?.also { footprints[species] = it }
    }

    private fun ability(t: PokedexTables, id: Int): String {
        val stride = if (t.abilityNameStride > 0) t.abilityNameStride else t.abilityNameLength
        return Gen3Text.decode(rd(t.abilityNames + id.toLong() * stride, t.abilityNameLength))
    }

    private fun readEntry(t: PokedexTables, national: Int): DexEntry? {
        val species = speciesFor(t, national)
        if (species == 0) return null
        t.expansion?.let { return readExpansionEntry(t, it, national, species) }
        val e = rd(t.entries + (if (t.entriesBySpecies) species else national).toLong() * t.entryStride, t.entryStride)
        val descPtr = Gfx.u32(e, 0x10)
        val info = rd(t.speciesInfo + species * 28L, 28)
        fun b(i: Int) = info[i].toInt() and 0xFF
        val abilities = listOf(b(0x16), b(0x17)).filter { it != 0 }.distinct().map { ability(t, it) }
        val hidden = t.hiddenAbilityOff.takeIf { it >= 0 }?.let { b(it) }?.takeIf { it != 0 }?.let { ability(t, it) }
        return DexEntry(
            national = national,
            species = species,
            category = Gen3Text.decode(e, 0, 12),
            heightDm = u16le(e, 0x0C),
            weightHg = u16le(e, 0x0E),
            description = listOfNotNull(
                descPtr.takeIf(Gfx::inRom),
                t.descriptionPage2Off.takeIf { it >= 0 }?.let { Gfx.u32(e, it) }?.takeIf(Gfx::inRom),
            ).joinToString(" ") { Gen3Text.decode(rd(it, 200)) },
            type1 = b(6),
            type2 = b(7),
            baseStats = listOf(b(0), b(1), b(2), b(4), b(5), b(3)),
            abilities = abilities,
            hiddenAbility = hidden,
            catchRate = b(8),
            genderRatio = b(0x10),
            eggGroups = listOf(b(0x14), b(0x15)).distinct().mapNotNull { EGG_GROUP_NAMES.getOrNull(it) },
        )
    }

    private fun readExpansionEntry(t: PokedexTables, x: SpeciesInfoDex, national: Int, species: Int): DexEntry {
        val info = rd(t.speciesInfo + species.toLong() * x.stride, x.stride)
        fun b(i: Int) = info[i].toInt() and 0xFF
        val ids = (0 until 3).map { u16le(info, x.abilitiesOff + it * 2) }
        val descPtr = Gfx.u32(info, x.descriptionOff)
        return DexEntry(
            national = national,
            species = species,
            category = Gen3Text.decode(info, x.categoryOff, 13),
            heightDm = u16le(info, x.natDexOff + 2),
            weightHg = u16le(info, x.natDexOff + 4),
            description = if (Gfx.inRom(descPtr)) Gen3Text.decode(rd(descPtr, 200)) else "",
            type1 = b(6),
            type2 = b(7),
            baseStats = listOf(b(0), b(1), b(2), b(4), b(5), b(3)),
            abilities = ids.take(2).filter { it != 0 }.distinct().map { ability(t, it) },
            hiddenAbility = ids[2].takeIf { it != 0 }?.let { ability(t, it) },
            catchRate = b(x.catchRateOff),
            genderRatio = b(x.genderOff),
            eggGroups = listOf(b(x.eggGroupsOff), b(x.eggGroupsOff + 1)).distinct().mapNotNull { EGG_GROUP_NAMES.getOrNull(it) },
        )
    }

    private fun rd(addr: Long, n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val chunk = minOf(4096, n - off)
            reader.readCoreMemory(addr + off, chunk).copyInto(out, off, 0, chunk)
            off += chunk
        }
        return out
    }
}

// include/constants/pokemon.h EGG_GROUP_*, as the games' own summary screens name them.
private val EGG_GROUP_NAMES = listOf(
    null, "MONSTER", "WATER 1", "BUG", "FLYING", "FIELD", "FAIRY", "GRASS", "HUMAN-LIKE",
    "WATER 3", "MINERAL", "AMORPHOUS", "WATER 2", "DITTO", "DRAGON", "UNDISCOVERED",
)

/** "HT 2'04"", the game's own rounding (DexScreen_PrintMonHeight). */
fun formatDexHeight(dm: Int): String {
    var inches = 10000L * dm / 254 // tenths of an inch
    if (inches % 10 >= 5) inches += 10
    val feet = inches / 120
    val rest = (inches - feet * 120) / 10
    return "%d'%02d\"".format(feet, rest)
}

/** "15.2 lbs.", the game's own rounding (DexScreen_PrintMonWeight). */
fun formatDexWeight(hg: Int): String {
    var lbs = 100000L * hg / 4536 // hundredths of a pound
    if (lbs % 10 >= 5) lbs += 10
    return "%d.%d lbs.".format(lbs / 100, (lbs % 100) / 10)
}

/** Gen 3 (Western) text: the characters dex entries and ability names use
 * (anything else decodes as [UNKNOWN]). */
object Gen3Text {
    const val UNKNOWN = '\uFFFD'
    private val table = CharArray(256) { UNKNOWN }.also { t ->
        t[0x00] = ' '
        t[0x1B] = 'é'
        for (i in 0..9) t[0xA1 + i] = '0' + i
        t[0xAB] = '!'; t[0xAC] = '?'; t[0xAD] = '.'; t[0xAE] = '-'; t[0xB0] = '…'
        t[0xB1] = '“'; t[0xB2] = '”'; t[0xB3] = '‘'; t[0xB4] = '’'
        t[0xB5] = '♂'; t[0xB6] = '♀'; t[0xB8] = ','; t[0xBA] = '/'
        for (i in 0 until 26) { t[0xBB + i] = 'A' + i; t[0xD5 + i] = 'a' + i }
        t[0xF0] = ':'
        t[0x2D] = '&'; t[0x2E] = '+'; t[0x5B] = '%'; t[0x5C] = '('; t[0x5D] = ')'
        t[0x2A] = '°' // expansion dex text ("3,600°F")
        t[0x35] = '='; t[0x36] = ';'
        // The rest of charmap.txt's Western letters (hack text: "capítulo", "organización").
        "ÀÁÂÇÈÉÊËÌ ÎÏÒÓÔŒÙÚÛÑßàá çèéêëì îïòóôœùúûñ".forEachIndexed { i, c -> if (c != ' ') t[0x01 + i] = c }
        t[0x2B] = 'ª'; t[0x51] = '¿'; t[0x52] = '¡'; t[0x5A] = 'Í'; t[0x68] = 'â'; t[0x6F] = 'í'
        t[0x85] = '<'; t[0x86] = '>'; t[0xAF] = '·'; t[0xB9] = '×'
    }

    // Argument bytes after an 0xFC control code (text colour, font, pause, ...),
    // by code - charmap.txt's EXT_CTRL_CODE_* list.
    private val FC_ARGS = intArrayOf(0, 1, 1, 1, 3, 1, 1, 0, 1, 0, 0, 2, 1, 1, 1, 0, 2, 1, 1, 1, 1, 0, 0, 0, 0)

    /** Up to [max] bytes from [off], stopping at 0xFF; line breaks become
     * spaces, control codes (0xFC + args, e.g. a font change) are dropped. */
    fun decode(b: ByteArray, off: Int = 0, max: Int = b.size - off): String = buildString {
        val end = minOf(b.size, off + max)
        var i = off
        while (i < end) {
            val c = b[i].toInt() and 0xFF
            if (c == 0xFF) break
            if (c == 0xFC && i + 1 < end) {
                i += 2 + FC_ARGS.getOrElse(b[i + 1].toInt() and 0xFF) { 0 }
                continue
            }
            append(if (c == 0xFE || c == 0xFA || c == 0xFB) ' ' else table[c])
            i++
        }
    }.trim()
}
