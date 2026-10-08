package com.pokedaisy.app.companion.data

/**
 * A game's names in its own language - the other-language Emeralds (read from
 * each ROM by scripts/gen_emerald_lang_tables.py, EmeraldText<Lang>Gen.kt) and
 * FireRed / LeafGreen / Ruby / Sapphire (scripts/port_retail.py,
 * GameText<Code>Gen.kt): the same ids as English, their own names. The companion
 * shows a game's names in the game's language; types, powers and map rectangles
 * stay English's (the same in every release) - [baseMapSecs] is the English table
 * the names go over.
 */
class GameText(
    species: () -> Map<Int, String>,
    moves: () -> Map<Int, String>,
    items: () -> Map<Int, String>,
    itemDescriptions: () -> Map<Int, String>,
    val natures: List<String>,
    mapSecs: () -> Map<Int, String>,
    smallFontWidths: () -> IntArray,
    englishMapSecs: () -> Map<Int, MapSecInfo> = { mapSecDataEmerald },
) {
    /** English's map sections (what hand-written GUIDE entries are tagged with). */
    val baseMapSecs: Map<Int, MapSecInfo> by lazy(englishMapSecs)
    val species by lazy(species)
    val items by lazy(items)
    val itemDescriptions by lazy(itemDescriptions)
    val moveData: Map<Int, MoveInfo> by lazy {
        val names = moves()
        englishMoveData.mapValues { (id, m) -> names[id]?.let { m.copy(name = it) } ?: m }
    }
    val mapSecData: Map<Int, MapSecInfo> by lazy {
        val names = mapSecs()
        baseMapSecs.mapValues { (id, s) -> names[id]?.let { s.copy(name = it) } ?: s }
    }

    /** The party slot's FONT_SMALL advances: a few localized glyphs are wider or narrower (empty for Japanese). */
    val smallFontWidths by lazy(smallFontWidths)
}

// The shared FireRed / Emerald move table (the member above shadows its name).
private val englishMoveData get() = moveData

private val EMERALD_ES by lazy {
    GameText({ speciesNamesEmeraldEs }, { moveNamesEmeraldEs }, { itemNamesEmeraldEs }, { itemDescriptionsEmeraldEs },
        natureNamesEmeraldEs, { mapSecNamesEmeraldEs }, { smallFontWidthsEmeraldEs })
}
private val EMERALD_DE by lazy {
    GameText({ speciesNamesEmeraldDe }, { moveNamesEmeraldDe }, { itemNamesEmeraldDe }, { itemDescriptionsEmeraldDe },
        natureNamesEmeraldDe, { mapSecNamesEmeraldDe }, { smallFontWidthsEmeraldDe })
}
private val EMERALD_FR by lazy {
    GameText({ speciesNamesEmeraldFr }, { moveNamesEmeraldFr }, { itemNamesEmeraldFr }, { itemDescriptionsEmeraldFr },
        natureNamesEmeraldFr, { mapSecNamesEmeraldFr }, { smallFontWidthsEmeraldFr })
}
private val EMERALD_IT by lazy {
    GameText({ speciesNamesEmeraldIt }, { moveNamesEmeraldIt }, { itemNamesEmeraldIt }, { itemDescriptionsEmeraldIt },
        natureNamesEmeraldIt, { mapSecNamesEmeraldIt }, { smallFontWidthsEmeraldIt })
}

private val EMERALD_JA by lazy {
    GameText({ speciesNamesEmeraldJa }, { moveNamesEmeraldJa }, { itemNamesEmeraldJa }, { itemDescriptionsEmeraldJa },
        natureNamesEmeraldJa, { mapSecNamesEmeraldJa }, { smallFontWidthsEmeraldJa })
}

private val portTexts = java.util.concurrent.ConcurrentHashMap<String, GameText>()

/** The names of the game with [code] ("BPED", "AXVF"); null for English and every game without its own. */
fun gameText(code: String): GameText? = when (code) {
    "BPES" -> EMERALD_ES
    "BPED" -> EMERALD_DE
    "BPEF" -> EMERALD_FR
    "BPEI" -> EMERALD_IT
    "BPEJ" -> EMERALD_JA
    else -> RETAIL_PORT_TEXTS[code]?.let { make -> portTexts.getOrPut(code, make) }
}

/** The European releases' game codes, by language letter. */
val EMERALD_EUROPEAN_CODES = setOf("BPES", "BPED", "BPEF", "BPEI")

/** Every Emerald but English's: the European ones and Japanese (BPEJ). */
val EMERALD_LOCALIZED_CODES = EMERALD_EUROPEAN_CODES + "BPEJ"
