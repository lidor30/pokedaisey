package com.pokedaisey.app.companion.data

/** Which card a game draws: FireRed / LeafGreen's Kanto card, Emerald's Hoenn one, or the Kanto
 * card in Unbound's colours (see [TrainerCardArt]). */
enum class CardStyle { KANTO, HOENN, UNBOUND }

/**
 * Where a game keeps what its TRAINER CARD shows - both decomps'
 * src/trainer_card.c (SetPlayerCardData) and include/global.h. Game stats
 * are u32s XOR'd with SaveBlock2.encryptionKey, like money.
 */
data class TrainerCardSave(
    val style: CardStyle,
    /** SaveBlock1.gameStats. */
    val gameStatsOff: Long,
    /** SaveBlock1.flags / .vars. */
    val flagsOff: Long,
    val varsOff: Long,
    val badgeFlag: Int,
    /** FLAG_SYS_POKEDEX_GET: the POKéDEX line only shows once the player has one. */
    val pokedexFlag: Int,
    /** IsNationalPokedexEnabled: SaveBlock2.pokedex.nationalMagic, VAR_NATIONAL_DEX and FLAG_SYS_NATIONAL_DEX all set. */
    val nationalMagicOff: Long,
    val nationalMagic: Int,
    val nationalVar: Int,
    val nationalVarValue: Int,
    val nationalFlag: Int,
    val dexFlags: DexFlags,
    /** The National Dex is always there (Unbound), counted up to [nationalCount]. */
    val nationalAlways: Boolean = false,
    val nationalCount: Int = 386,
    /** Before the National Dex: Kanto counts No. 1-151, Hoenn its own 202 in sHoennToNationalOrder. */
    val regionalDex: IntArray,
    /** Emerald's extra stars: the Battle Frontier symbols (FLAG_SYS_TOWER_SILVER, gold right after,
     * a pair per facility) and the museum's five contest paintings (SaveBlock1.contestWinners). */
    val frontierSymbolFlag: Int = -1,
    val museumWinnersOff: Long = -1,
    /** SaveBlock2.frontier.cardBattlePoints (Emerald). */
    val frontierBpOff: Long = -1,
    /** VAR_HOF_BRAG_STATE, the egg and link-win ones right after (FireRed's stickers). */
    val stickerVar: Int = -1,
)

/** pokeemerald's sHoennToNationalOrder: the Hoenn dex's national numbers, in its order. */
val HOENN_TO_NATIONAL = intArrayOf(
    252, 253, 254, 255, 256, 257, 258, 259, 260, 261, 262, 263, 264, 265, 266, 267, 268, 269, 270, 271,
    272, 273, 274, 275, 276, 277, 278, 279, 280, 281, 282, 283, 284, 285, 286, 287, 288, 289, 63, 64,
    65, 290, 291, 292, 293, 294, 295, 296, 297, 118, 119, 129, 130, 298, 183, 184, 74, 75, 76, 299,
    300, 301, 41, 42, 169, 72, 73, 302, 303, 304, 305, 306, 66, 67, 68, 307, 308, 309, 310, 311,
    312, 81, 82, 100, 101, 313, 314, 43, 44, 45, 182, 84, 85, 315, 316, 317, 318, 319, 320, 321,
    322, 323, 218, 219, 324, 88, 89, 109, 110, 325, 326, 27, 28, 327, 227, 328, 329, 330, 331, 332,
    333, 334, 335, 336, 337, 338, 339, 340, 341, 342, 343, 344, 345, 346, 347, 348, 174, 39, 40, 349,
    350, 351, 120, 121, 352, 353, 354, 355, 356, 357, 358, 359, 37, 38, 172, 25, 26, 54, 55, 360,
    202, 177, 178, 203, 231, 232, 127, 214, 111, 112, 361, 362, 363, 364, 365, 366, 367, 368, 369, 222,
    170, 171, 370, 116, 117, 230, 371, 372, 373, 374, 375, 376, 377, 378, 379, 380, 381, 382, 383, 384,
    385, 386,
)

val TRAINER_CARD_FIRERED = TrainerCardSave(
    style = CardStyle.KANTO,
    gameStatsOff = 0x1200, flagsOff = 0xEE0, varsOff = 0x1000,
    badgeFlag = 0x820, pokedexFlag = 0x829,
    nationalMagicOff = 0x18 + 3, nationalMagic = 0xB9, nationalVar = 0x404E, nationalVarValue = 0x6258, nationalFlag = 0x840,
    dexFlags = FIRERED_DEX_FLAGS,
    regionalDex = IntArray(151) { it + 1 },
    stickerVar = 0x4049,
)

/**
 * Pokémon Unbound v2.1.1.1 (CFRU): FireRed's SaveBlock layout for everything the
 * card reads (name, ID, time, money, flags, game stats - checked on the
 * fixture save against Unbound's own card: Lidor, 06174, 0:25, 1 caught), with
 * CFRU's dex flags in SaveBlock1 and the National Dex always on.
 */
val TRAINER_CARD_UNBOUND = TRAINER_CARD_FIRERED.copy(
    style = CardStyle.UNBOUND,
    dexFlags = CFRU_DEX_FLAGS,
    nationalAlways = true,
    nationalCount = 905,
    stickerVar = -1,
)

val TRAINER_CARD_EMERALD = TrainerCardSave(
    style = CardStyle.HOENN,
    gameStatsOff = 0x159C, flagsOff = 0x1270, varsOff = 0x139C,
    badgeFlag = 0x867, pokedexFlag = 0x861,
    nationalMagicOff = 0x18 + 2, nationalMagic = 0xDA, nationalVar = 0x4046, nationalVarValue = 0x302, nationalFlag = 0x896,
    dexFlags = EMERALD_DEX_FLAGS,
    regionalDex = HOENN_TO_NATIONAL,
    frontierSymbolFlag = 0x8C4,
    museumWinnersOff = 0x2E90 + 8 * 32,
    frontierBpOff = 0xEBA,
)

/** What the card prints, as the game's SetPlayerCardData gathers it. */
data class TrainerCardInfo(
    val style: CardStyle,
    /** SaveBlock2.playerName in the game's own encoding (up to 7 bytes). */
    val name: List<Int>,
    val female: Boolean,
    val trainerId: Int,
    val hours: Int,
    val minutes: Int,
    val money: Long,
    /** Caught count (regional until the National Dex); null = no POKéDEX yet. */
    val dexCaught: Int?,
    /** Bit i = badge i+1. */
    val badges: Int,
    val stars: Int,
    /** h << 16 | m << 8 | s of the first Hall of Fame entry; 0 = none. */
    val hofDebut: Int,
    val linkWins: Int = 0,
    val linkLosses: Int = 0,
    val trades: Int = 0,
    val unionRoom: Int = 0,
    val berryCrush: Int = 0,
    val linkContests: Int = 0,
    val linkPokeblocks: Int = 0,
    val battlePoints: Int = 0,
    /** FireRed's Sticker Man stickers (VAR_HOF / EGG / LINK_WIN_BRAG_STATE): a level 1-4 each, 0 = none. */
    val stickers: List<Int> = emptyList(),
) {
    val nameText: String get() = Gen3Text.decode(ByteArray(name.size) { name[it].toByte() })
}

private const val STAT_FIRST_HOF_PLAY_TIME = 1
private const val STAT_ENTERED_HOF = 10
private const val STAT_POKEMON_TRADES = 21
private const val STAT_LINK_BATTLE_WINS = 23
private const val STAT_LINK_BATTLE_LOSSES = 24
private const val STAT_POKEBLOCKS_WITH_FRIENDS = 34
private const val STAT_WON_LINK_CONTEST = 35
private const val STAT_UNION_ROOM_BATTLES = 50
private const val STAT_BERRY_CRUSH_POINTS = 51

/** The card for the save [cfg] points at, or null without a [NativeConfig.trainerCard] layout or a loaded save. */
fun readTrainerCard(c: MemoryReader, cfg: NativeConfig): TrainerCardInfo? = runCatching {
    val s = cfg.trainerCard ?: return null
    val sb1 = saveBlock1(c, cfg)
    val sb2 = saveBlock2(c, cfg)
    if (sb1 !in 0x02000000L until 0x04000000L || sb2 !in 0x02000000L until 0x04000000L) return null
    val head = c.readCoreMemory(sb2, 0x12)
    val key = u32le(c.readCoreMemory(sb2 + cfg.encryptionKeyOff, 4), 0)
    val statBytes = c.readCoreMemory(sb1 + s.gameStatsOff, 4 * (STAT_BERRY_CRUSH_POINTS + 1))
    fun stat(id: Int) = (u32le(statBytes, id * 4) xor key)
    fun capped(id: Int, max: Long) = minOf(stat(id), max).toInt()
    val flags = c.readCoreMemory(sb1 + s.flagsOff, 0x12C)
    fun flag(n: Int) = (flags[n / 8].toInt() shr (n % 8)) and 1 != 0
    fun variable(id: Int) = u16le(c.readCoreMemory(sb1 + s.varsOff + (id - 0x4000) * 2, 2), 0)

    val caught = readCaught(c, sb1, sb2, s.dexFlags)
    val national = s.nationalAlways ||
        ((c.readCoreMemory(sb2 + s.nationalMagicOff, 1)[0].toInt() and 0xFF) == s.nationalMagic &&
            variable(s.nationalVar) == s.nationalVarValue && flag(s.nationalFlag))
    val dexCaught = if (national) (1..s.nationalCount).count { caught(it) } else s.regionalDex.count { caught(it) }

    // GAME_STAT_FIRST_HOF_PLAY_TIME counts only once ENTERED_HOF is set; hours cap at 999:59:59.
    var hof = if (stat(STAT_ENTERED_HOF) != 0L) stat(STAT_FIRST_HOF_PLAY_TIME).toInt() else 0
    if ((hof ushr 16) > 999) hof = (999 shl 16) or (59 shl 8) or 59
    // HasAllHoennMons: the Hoenn dex minus Jirachi and Deoxys.
    val allHoenn = HOENN_TO_NATIONAL.take(HOENN_TO_NATIONAL.size - 2).all { caught(it) }
    var stars = (if (hof != 0) 1 else 0) + (if (allHoenn) 1 else 0)
    if (s.museumWinnersOff >= 0) {
        val winners = c.readCoreMemory(sb1 + s.museumWinnersOff, 5 * 32)
        if ((0 until 5).all { u16le(winners, it * 32 + 8) != 0 }) stars++
    }
    if (s.frontierSymbolFlag >= 0 && (0 until 7).all { flag(s.frontierSymbolFlag + 2 * it) && flag(s.frontierSymbolFlag + 2 * it + 1) }) stars++

    val name = (0 until 7).map { head[it].toInt() and 0xFF }.takeWhile { it != 0xFF }
    TrainerCardInfo(
        style = s.style,
        name = name,
        female = head[8].toInt() != 0,
        trainerId = u16le(head, 0x0A),
        hours = minOf(u16le(head, 0x0E), 999),
        minutes = minOf(head[0x10].toInt() and 0xFF, 59),
        money = readMoney(c, cfg) ?: 0L,
        dexCaught = dexCaught.takeIf { flag(s.pokedexFlag) },
        badges = (0 until 8).fold(0) { acc, i -> if (flag(s.badgeFlag + i)) acc or (1 shl i) else acc },
        stars = stars,
        hofDebut = hof,
        linkWins = capped(STAT_LINK_BATTLE_WINS, 9999),
        linkLosses = capped(STAT_LINK_BATTLE_LOSSES, 9999),
        trades = capped(STAT_POKEMON_TRADES, 0xFFFF),
        unionRoom = if (s.style != CardStyle.HOENN) capped(STAT_UNION_ROOM_BATTLES, 0xFFFF) else 0,
        berryCrush = if (s.style != CardStyle.HOENN) capped(STAT_BERRY_CRUSH_POINTS, 0xFFFF) else 0,
        linkContests = if (s.style == CardStyle.HOENN) capped(STAT_WON_LINK_CONTEST, 999) else 0,
        linkPokeblocks = if (s.style == CardStyle.HOENN) capped(STAT_POKEBLOCKS_WITH_FRIENDS, 0xFFFF) else 0,
        battlePoints = if (s.frontierBpOff >= 0) u16le(c.readCoreMemory(sb2 + s.frontierBpOff, 2), 0) else 0,
        stickers = if (s.stickerVar >= 0) (0 until 3).map { variable(s.stickerVar + it) } else emptyList(),
    )
}.getOrNull()

/** GetSetPokedexFlag(n, FLAG_GET_CAUGHT): owned, and seen in all three copies. */
private fun readCaught(c: MemoryReader, sb1: Long, sb2: Long, f: DexFlags): (Int) -> Boolean {
    val base = if (f.block == DexFlagBlock.SAVE_BLOCK_1) sb1 else sb2
    val owned = c.readCoreMemory(base + f.caught, f.bytes)
    val seen = listOf(c.readCoreMemory(base + f.seen, f.bytes)) + f.seenCopies.map { c.readCoreMemory(sb1 + it, f.bytes) }
    fun bit(b: ByteArray, n: Int) = (b[(n - 1) / 8].toInt() shr ((n - 1) % 8)) and 1 != 0
    return { n -> n in 1..f.bytes * 8 && bit(owned, n) && seen.all { bit(it, n) } }
}

