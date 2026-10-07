package com.pokedaisy.app.companion.data

/**
 * Generation 1 (Game Boy) games' RAM, read over the GB core's own 16-bit bus:
 * WRAM C000-DFFF. Addresses are pret/pokeyellow's symbols (its build is
 * byte-identical to retail Yellow), checked on headless captures of the
 * user's save (the `yellow` fixtures).
 *
 * Gen 1's structs are plaintext and big-endian (HP, stats, experience), the
 * species byte is the game's internal index ([internalToDex] maps it to the
 * National Dex number every table here is keyed by), and there's no gender,
 * ability or held item. The bag is one list.
 */
data class Gen1Config(
    val partyCount: Long,       // wPartyCount, then wPartySpecies
    val partyMons: Long,        // wPartyMons: 6 x party_struct (44 bytes)
    val bagCount: Long,         // wNumBagItems, then wBagItems: {item, qty} pairs
    val money: Long,            // wPlayerMoney: 3 bytes BCD
    val badges: Long,           // wObtainedBadges: a bit per badge
    val curMap: Long,           // wCurMap (wYCoord / wXCoord at +3 / +4)
    val yCoord: Long,
    val xCoord: Long,
    val isInBattle: Long,       // wIsInBattle: 0 none, 1 wild, 2 trainer, 0xFF lost
    val battleMon: Long,        // wBattleMon: battle_struct (29 bytes)
    val enemyMon: Long,         // wEnemyMon
    val enemyMonPartyPos: Long, // wEnemyMonPartyPos: which wEnemyMons slot is out
    val enemyPartyCount: Long,  // wEnemyPartyCount, then its species list
    val enemyMons: Long,        // wEnemyMons: a trainer's party_structs
    val internalToDex: IntArray,
)

/** Pokémon Yellow (USA, Europe). Red/Blue's own addresses are one byte later (wPartyCount D163). */
val GEN1_YELLOW = Gen1Config(
    partyCount = 0xD162L,
    partyMons = 0xD16AL,
    bagCount = 0xD31CL,
    money = 0xD346L,
    badges = 0xD355L,
    curMap = 0xD35DL,
    yCoord = 0xD360L,
    xCoord = 0xD361L,
    isInBattle = 0xD056L,
    battleMon = 0xD013L,
    enemyMon = 0xCFE4L,
    enemyMonPartyPos = 0xCFE7L,
    enemyPartyCount = 0xD89BL,
    enemyMons = 0xD8A3L,
    internalToDex = gen1InternalToDexYellow,
)

const val GEN1_PARTY_MON_SIZE = 44
const val GEN1_BATTLE_MON_SIZE = 29
private const val GEN1_BAG_SLOTS = 20

private fun be16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
private fun u8(b: ByteArray, o: Int) = b[o].toInt() and 0xFF

private fun Gen1Config.dex(internal: Int): Int = internalToDex.getOrElse(internal) { 0 }

/** A party_struct at [off] in [raw], or null for an empty / garbage slot. */
fun decodeGen1PartyMon(raw: ByteArray, off: Int, cfg: Gen1Config): Mon? {
    val species = cfg.dex(u8(raw, off))
    if (species == 0) return null
    val level = u8(raw, off + 33)
    if (level !in 1..100) return null
    val exp = ((u8(raw, off + 14).toLong() shl 16) or (u8(raw, off + 15).toLong() shl 8) or u8(raw, off + 16).toLong())
    return Mon(
        species = species,
        level = level,
        hp = be16(raw, off + 1),
        maxHp = be16(raw, off + 34),
        status = u8(raw, off + 4).toLong(),   // same bits as Gen 3's: sleep 0-2, PSN, BRN, FRZ, PAR
        moves = IntArray(4) { u8(raw, off + 8 + it) },
        pp = IntArray(4) { u8(raw, off + 29 + it) and 0x3F },   // top 2 bits: PP Ups
        genderSymbol = GENDER_SYMBOL_NONE,
        exp = exp,
    )
}

/** A battle_struct (wBattleMon / wEnemyMon); its types are the battler's current ones. */
fun decodeGen1BattleMon(raw: ByteArray, cfg: Gen1Config): BattleMon? {
    val species = cfg.dex(u8(raw, 0))
    if (species == 0) return null
    return BattleMon(
        species = species,
        level = u8(raw, 14),
        type1 = u8(raw, 5),
        type2 = u8(raw, 6),
        hp = be16(raw, 1),
        maxHp = be16(raw, 15),
        status1 = u8(raw, 4).toLong(),
        moves = IntArray(4) { u8(raw, 8 + it) },
        pp = IntArray(4) { u8(raw, 25 + it) and 0x3F },
    )
}

/** Three BCD bytes (wPlayerMoney 99 99 99 = ¥999999). */
fun bcd(b: ByteArray): Long = b.fold(0L) { acc, x -> acc * 100 + ((x.toInt() shr 4) and 0xF) * 10 + (x.toInt() and 0xF) }

private val EMPTY_BATTLE_MON = BattleMon(0, 0, 0, 0, 0, 0, 0, IntArray(4), IntArray(4))

fun readGen1Telemetry(r: MemoryReader, cfg: Gen1Config): Telemetry {
    val count = u8(r.readCoreMemory(cfg.partyCount, 1), 0).coerceAtMost(PARTY_SIZE)
    val partyRaw = r.readCoreMemory(cfg.partyMons, PARTY_SIZE * GEN1_PARTY_MON_SIZE)
    val party = (0 until count).mapNotNull { decodeGen1PartyMon(partyRaw, it * GEN1_PARTY_MON_SIZE, cfg) }

    val bagN = u8(r.readCoreMemory(cfg.bagCount, 1), 0).coerceAtMost(GEN1_BAG_SLOTS)
    val bagRaw = r.readCoreMemory(cfg.bagCount + 1, GEN1_BAG_SLOTS * 2)
    val items = (0 until bagN).map { bagRaw[2 * it].toInt() and 0xFF to (bagRaw[2 * it + 1].toInt() and 0xFF) }
        .takeWhile { it.first != 0xFF }
        .map { (id, qty) -> Item(id, qty) }   // one list, in the player's own order, like the game's bag

    val battle = u8(r.readCoreMemory(cfg.isInBattle, 1), 0)
    val inBattle = battle == 1 || battle == 2
    val battleMons = MutableList(4) { EMPTY_BATTLE_MON }
    var enemyParty = emptyList<Mon>()
    var enemyActive = -1
    if (inBattle) {
        decodeGen1BattleMon(r.readCoreMemory(cfg.battleMon, GEN1_BATTLE_MON_SIZE), cfg)?.let { battleMons[BATTLE_POS_PLAYER_LEFT] = it }
        decodeGen1BattleMon(r.readCoreMemory(cfg.enemyMon, GEN1_BATTLE_MON_SIZE), cfg)?.let { battleMons[BATTLE_POS_OPPONENT_LEFT] = it }
        if (battle == 2) {
            val n = u8(r.readCoreMemory(cfg.enemyPartyCount, 1), 0).coerceAtMost(PARTY_SIZE)
            val raw = r.readCoreMemory(cfg.enemyMons, PARTY_SIZE * GEN1_PARTY_MON_SIZE)
            enemyParty = (0 until n).mapNotNull { decodeGen1PartyMon(raw, it * GEN1_PARTY_MON_SIZE, cfg) }
            enemyActive = u8(r.readCoreMemory(cfg.enemyMonPartyPos, 1), 0).takeIf { it < enemyParty.size } ?: -1
        }
    }

    val map = u8(r.readCoreMemory(cfg.curMap, 1), 0)
    return Telemetry(
        frameCounter = 0,
        inBattle = inBattle,
        isDoubleBattle = false,
        mapGroup = 0,
        mapNum = map,
        regionMapSectionId = map,
        x = u8(r.readCoreMemory(cfg.xCoord, 1), 0),
        y = u8(r.readCoreMemory(cfg.yCoord, 1), 0),
        facingDirection = 0,
        partyCount = party.size,
        party = party,
        battleMons = battleMons,
        itemCount = items.size,
        items = items,
        enemyParty = enemyParty,
        enemyActive = enemyActive,
        money = bcd(r.readCoreMemory(cfg.money, 3)),
    )
}
