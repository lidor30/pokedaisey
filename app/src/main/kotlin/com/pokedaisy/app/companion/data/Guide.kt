package com.pokedaisy.app.companion.data

/**
 * The GUIDE tab's content: a few short pages per game (how the game works,
 * where the useful things are, what to do when stuck) plus pages built live
 * from the ROM and the save (HERE, NEXT BOSS, EVOLUTIONS). Every entry is
 * spoiler-safe until tapped - its [GuideEntry.title] is always shown, the
 * [GuideEntry.hint] after one tap and the [GuideEntry.answer] after another
 * (or straight away when there's no hint).
 *
 * Hand-written content is in our own words, from each game's own data (for
 * FireRed / Emerald, the decomp's map scripts), never copied from community
 * docs. It's AI-assisted, so the tab says so before it's first read
 * ([GameGuide.verified] false adds that it hasn't been checked in-game).
 */
data class GuideEntry(
    val title: String,
    val answer: String,
    val hint: String? = null,
    /** How to tell the player already has this (WHERE IS); null = nothing to have. */
    val have: Have? = null,
    /** Short text right of the title, always shown (levels / odds on HERE). */
    val detail: String? = null,
    /** Species whose icon leads the row; 0 = none. */
    val icon: Int = 0,
    /** Resolved [have] (pages built from the save set it directly). */
    val owned: Boolean = false,
    /** Area names (as the game's map shows them) this is about - HERE lists it there under TO DO. */
    val areas: List<String> = emptyList(),
) {
    /** Taps until the answer shows: one straight to it, two via the hint; 0 = nothing hidden. */
    val steps: Int get() = when {
        answer.isEmpty() -> 0
        hint == null -> 1
        else -> 2
    }
}

/** [note]: a line under the heading (a boss's ace level vs. yours). */
data class GuideSection(val heading: String, val entries: List<GuideEntry>, val note: String? = null)

data class GuidePage(val title: String, val sections: List<GuideSection>) {
    val size: Int get() = sections.sumOf { it.entries.size }
}

data class GameGuide(
    val pages: List<GuidePage>,
    val verified: Boolean = true,
    /** The boss order NEXT BOSS walks through; empty = no such page. */
    val bosses: List<Boss> = emptyList(),
)

/** Something the save can show the player already has. */
sealed interface Have {
    /** An event flag the game sets when it's handed over (key items that aren't bag items, like the RUNNING SHOES). */
    data class Flag(val flag: Int) : Have
    /** An item in the bag (HMs and key items can't be tossed). */
    data class Item(val item: Int) : Have
    /** Any of these national dex numbers caught. */
    data class Caught(val national: List<Int>) : Have {
        constructor(vararg n: Int) : this(n.toList())
    }
}

/** What the save says the player has, for [Have] checks; any part may be missing (null = unknown). */
class GuideContext(val progress: SaveProgress?, val bag: Set<Int>?, val caught: Set<Int>?) {
    fun has(h: Have?): Boolean = when (h) {
        null -> false
        is Have.Flag -> progress?.flag(h.flag) == true
        is Have.Item -> bag?.contains(h.item) == true
        is Have.Caught -> caught != null && h.national.any { it in caught }
    }
}

/**
 * A boss fight NEXT BOSS can point at. [done] = the flag set once it's won
 * (a badge, or an Elite Four room's own flag); [trainer] picks the trainer
 * id from the save (the champion depends on the starter, the Elite Four on
 * whether rematches are on).
 */
data class Boss(
    val title: String,
    val where: String,
    val done: Int,
    /** Teams the game picks between at run time that the save can't settle
     * (Unbound's difficulty): each shown under its label. Empty = [trainer]. */
    val variants: List<Pair<String, Int>> = emptyList(),
    /** [variants] that follow the save (Amethyst's RAINE brings a team for
     * the badges you hold, in each difficulty); null = [variants]. */
    val variantsFor: ((SaveProgress) -> List<Pair<String, Int>>)? = null,
    /** When beating it isn't a single flag (Heart and Soul's Elite Four count
     * up a var during a League run); null = [done]. */
    val doneIf: ((SaveProgress) -> Boolean)? = null,
    val trainer: (SaveProgress) -> Int,
) {
    fun isDone(p: SaveProgress): Boolean = doneIf?.invoke(p) ?: p.flag(done)

    /** The teams to show: (label or null, trainer id). */
    fun teams(p: SaveProgress): List<Pair<String?, Int>> =
        (variantsFor?.invoke(p) ?: variants).ifEmpty { null }?.map { it.first to it.second } ?: listOf(null to trainer(p))

    /** Every trainer id this boss may be, for loading their teams. */
    fun trainers(p: SaveProgress): List<Int> = teams(p).map { it.second }
}

/**
 * Which guide (hand-written text and HERE's area data) a game gets. LeafGreen
 * runs as FireRed and Ruby / Sapphire as Emerald - same species, items and
 * map sections - but their guides differ, so each game's [GuideTables] names
 * its own.
 */
enum class GuideId { FIRERED, LEAFGREEN, EMERALD, RUBY, SAPPHIRE, HEART_AND_SOUL, UNBOUND, RADICAL_RED, ODYSSEY, GAIA, AMETHYST, AMETHYST_V141, CELIA }

/** [live]'s guide on the native path; else by [game] (the QoL builds have no live tables). */
fun guideId(game: GameKind?, live: GuideTables?): GuideId? = live?.guide ?: when (game) {
    GameKind.FIRERED -> GuideId.FIRERED
    GameKind.EMERALD -> GuideId.EMERALD
    else -> null
}

/** The hand-written guide [id] has, if one has been written. */
fun gameGuide(id: GuideId?): GameGuide? = when (id) {
    GuideId.FIRERED -> GUIDE_FIRERED
    GuideId.LEAFGREEN -> GUIDE_LEAFGREEN
    GuideId.EMERALD -> GUIDE_EMERALD
    GuideId.RUBY -> GUIDE_RUBY
    GuideId.SAPPHIRE -> GUIDE_SAPPHIRE
    GuideId.HEART_AND_SOUL -> GUIDE_HEART_AND_SOUL
    GuideId.UNBOUND -> GUIDE_UNBOUND
    GuideId.RADICAL_RED -> GUIDE_RADICAL_RED
    GuideId.ODYSSEY -> GUIDE_ODYSSEY
    GuideId.GAIA -> GUIDE_GAIA
    GuideId.AMETHYST, GuideId.AMETHYST_V141 -> GUIDE_AMETHYST
    GuideId.CELIA -> GUIDE_CELIA
    null -> null
}

// Small builders so the per-game files read like the content they hold.
internal fun page(title: String, vararg sections: GuideSection) = GuidePage(title, sections.toList())
internal fun section(heading: String, vararg entries: GuideEntry) = GuideSection(heading, entries.toList())
internal fun entry(title: String, answer: String, hint: String? = null, have: Have? = null, areas: List<String> = emptyList()) =
    GuideEntry(title, answer, hint, have, areas = areas)
