package com.pokedaisy.app.companion.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Where the GUIDE's live pages (HERE, NEXT BOSS) and the battle's foe team
 * read from: the ROM's wild-encounter, trainer and learnset tables, and the
 * save's event flags / vars (badges, key items, the starter choice).
 *
 * The retail FireRed rev 1 addresses were found by taking each table's
 * pointer-free bytes from the decomp build and searching the retail ROM
 * (sha1 dd5945db…) for them, then for the pointers to them - BROCK's party
 * then decoded to GEODUDE Lv12 (TACKLE, DEFENSE CURL) / ONIX Lv14.
 */
data class GuideTables(
    /** gTrainers: 0x28-byte records (party flags, class, name at +4, size at +0x20, party ptr at +0x24). */
    val trainers: Long,
    /** gLevelUpLearnsets: a pointer per species to u16 (level << 9 | move) runs ending 0xFFFF
     * (CFRU's: {u16 move, u8 level} ending at level 0xFF, see [learnsetCfru]); 0 = not located. */
    val learnsets: Long,
    /** gWildMonHeaders: {u8 group, u8 num, pad, land*, water*, rockSmash*, fishing*}, ending at group 0xFF. */
    val wildHeaders: Long,
    /** SaveBlock1.flags / .vars (include/global.h). */
    val sb1FlagsOff: Long = 0xEE0,
    val sb1VarsOff: Long = 0x1000,
    val flagBytes: Int = 0x120,
    val varCount: Int = 0x100,
    /** A trainer id and the name its record must hold, to check the tables are where we think ([guideTablesMatchRom]). */
    val probeTrainer: Int = 414, // TRAINER_LEADER_BROCK
    val probeName: String = "BROCK",
    /** The hand-written guide and area data this game gets ([gameGuide]). */
    val guide: GuideId = GuideId.FIRERED,
    /** struct Trainer's size and where its name / party size / party pointer sit. */
    val trainerStride: Int = 0x28,
    val trainerNameOff: Int = 4,
    val trainerNameLen: Int = 12,
    val trainerSizeOff: Int = 0x20,
    val trainerPartyOff: Int = 0x24,
    /** pokeemerald-expansion's struct TrainerMon (always custom moves); null = vanilla's party structs. */
    val trainerMon: TrainerMonLayout? = null,
    /** WildPokemonHeader: expansion keeps one set of four tables per time of day. */
    val wildSets: Int = 1,
    /** Which set HERE shows (expansion's TIME_DAY); a missing table falls back to the others. */
    val wildSet: Int = 0,
    /** CFRU's 3-byte learnset entries (Radical Red, Unbound, Amethyst). */
    val learnsetCfru: Boolean = false,
    /** More gTrainers tables a hack picks between (Amethyst's per difficulty): a
     * trainer id or'd with (k + 1) << [TRAINER_TABLE_SHIFT] reads table k, and
     * an empty record there falls back to [trainers], as the game does. */
    val altTrainers: List<Long> = emptyList(),
    /** A WildPokemonHeader of its own shape (Imperium's 24 bytes, Quetzal's 28); null = vanilla's / [wildSets]'. */
    val wildLayout: WildLayout? = null,
    /** Flags / vars past the main arrays that live elsewhere in SaveBlock1 (Quetzal's 0x1000+ flags, 0x5000+ vars). */
    val flagBanks: List<SaveBank> = emptyList(),
    val varBanks: List<SaveBank> = emptyList(),
)

/** A WildPokemonHeader's size and the pointer slot (after the 4-byte map id) of each method. */
data class WildLayout(val stride: Int, val land: Int = 0, val water: Int = 1, val rockSmash: Int = 2, val fishing: Int = 3)

/** [count] flags (or vars) numbered from [firstId], kept at SaveBlock1 + [sb1Off]. */
data class SaveBank(val firstId: Int, val sb1Off: Long, val count: Int)

const val TRAINER_TABLE_SHIFT = 16

/** pokeemerald-expansion's struct TrainerMon: moves (u16 x4), species, held item, level. */
data class TrainerMonLayout(val stride: Int, val movesOff: Int, val speciesOff: Int, val itemOff: Int, val levelOff: Int)

val GUIDE_TABLES_FIRERED_REV1 = GuideTables(
    trainers = 0x0823EB38L,
    learnsets = 0x0825D824L,
    wildHeaders = 0x083C9D28L,
)

// LeafGreen, from pret/pokefirered's `leafgreen` / `leafgreen_rev1` maps (both
// builds byte-identical to retail): same layouts, its own addresses and data
// (version-exclusive wild encounters).
val GUIDE_TABLES_LEAFGREEN_REV0 = GuideTables(
    trainers = 0x0823EAA4L,
    learnsets = 0x0825D794L,
    wildHeaders = 0x083C9AF4L,
    guide = GuideId.LEAFGREEN,
)

val GUIDE_TABLES_LEAFGREEN_REV1 = GuideTables(
    trainers = 0x0823EB14L,
    learnsets = 0x0825D804L,
    wildHeaders = 0x083C9B64L,
    guide = GuideId.LEAFGREEN,
)

/**
 * Retail Emerald, from the pinned pokeemerald commit built unpatched with
 * agbcc - byte-identical to retail (sha1 f3ae0881…) - and its ELF
 * (arm-none-eabi-nm). The tables have FireRed's layouts (struct Trainer is
 * the same 0x28 bytes, same party structs, learnsets and wild headers);
 * SaveBlock1.flags / .vars sit at 0x1270 / 0x139C (offsetof, compiled with
 * agbcc) with 300 flag bytes. Trainer 265 is ROXANNE.
 */
val GUIDE_TABLES_EMERALD = GuideTables(
    trainers = 0x08310030L,
    learnsets = 0x0832937CL,
    wildHeaders = 0x08552D48L,
    sb1FlagsOff = 0x1270,
    sb1VarsOff = 0x139C,
    flagBytes = 0x12C,
    probeTrainer = 265,
    probeName = "ROXANNE",
    guide = GuideId.EMERALD,
)

// The European Emerald releases: English's tables, moved (found through the
// literal pools of English's code); ROXANNE is each game's own name for her.
val GUIDE_TABLES_EMERALD_ES = GUIDE_TABLES_EMERALD.copy(
    trainers = 0x08316294L, learnsets = 0x0832F638L, wildHeaders = 0x085563A4L, probeName = "PETRA",
)
val GUIDE_TABLES_EMERALD_DE = GUIDE_TABLES_EMERALD.copy(
    trainers = 0x083249A0L, learnsets = 0x0833DD3CL, wildHeaders = 0x08564A78L, probeName = "FELIZIA",
)
val GUIDE_TABLES_EMERALD_FR = GUIDE_TABLES_EMERALD.copy(
    trainers = 0x08317B60L, learnsets = 0x08330EECL, wildHeaders = 0x08557C34L, probeName = "ROXANNE",
)
val GUIDE_TABLES_EMERALD_IT = GUIDE_TABLES_EMERALD.copy(
    trainers = 0x0830F9F4L, learnsets = 0x08328D7CL, wildHeaders = 0x0854FA8CL, probeName = "PETRA",
)

// Japanese Emerald: 0x20-byte trainers - trainerName[6] at +4, partySize at +0x18, the party at +0x1C.
val GUIDE_TABLES_EMERALD_JA = GUIDE_TABLES_EMERALD.copy(
    trainers = 0x082E383CL, learnsets = 0x082F9D04L, wildHeaders = 0x0852D9F4L, probeName = "ツツジ",
    trainerStride = 0x20, trainerNameLen = 6, trainerSizeOff = 0x18, trainerPartyOff = 0x1C,
)

/**
 * Ruby / Sapphire (rev 1 and rev 2 share every address), from pret/pokeruby's
 * ruby_rev1 / sapphire_rev1 maps (both builds byte-identical to the user's
 * ROMs). Same table layouts as Emerald; SaveBlock1 (static, see
 * [NativeConfig.staticSaveBlocks]) has flags at 0x1220 (288 bytes) and vars at
 * 0x1340 (include/global.h). Trainer 265 is ROXANNE here too.
 */
val GUIDE_TABLES_RUBY = GuideTables(
    trainers = 0x081F0514L,
    learnsets = 0x08207BE0L,
    wildHeaders = 0x0839D46CL,
    sb1FlagsOff = 0x1220,
    sb1VarsOff = 0x1340,
    flagBytes = 0x120,
    probeTrainer = 265,
    probeName = "ROXANNE",
    guide = GuideId.RUBY,
)

val GUIDE_TABLES_SAPPHIRE = GUIDE_TABLES_RUBY.copy(
    trainers = 0x081F04A4L,
    learnsets = 0x08207B70L,
    wildHeaders = 0x0839D2B4L,
    guide = GuideId.SAPPHIRE,
)

private const val TRAINER_PARTY_CUSTOM_MOVESET = 1
private const val TRAINER_PARTY_HELD_ITEM = 2
private const val WILD_SET_SIZE = 16 // four WildPokemonInfo pointers (+ expansion's hidden one, see below)
private const val MAP_GROUP_UNDEFINED = 0xFF

/** Whether the running ROM has these tables where [t] says: its probe trainer (FireRed's BROCK) has the right name. */
fun guideTablesMatchRom(c: MemoryReader, t: GuideTables): Boolean = runCatching {
    val name = c.readCoreMemory(t.trainers + t.probeTrainer.toLong() * t.trainerStride + t.trainerNameOff, t.trainerNameLen)
    Gen3Text.decode(name) == t.probeName && c.readCoreMemory(t.wildHeaders, 1)[0].toInt() and 0xFF != MAP_GROUP_UNDEFINED
}.getOrDefault(false)

/** The save's event flags and vars, as the scripts see them. */
class SaveProgress(
    private val flags: ByteArray,
    private val vars: ByteArray,
    /** [GuideTables.flagBanks] / [GuideTables.varBanks], each with its bytes. */
    private val flagBanks: List<Pair<SaveBank, ByteArray>> = emptyList(),
    private val varBanks: List<Pair<SaveBank, ByteArray>> = emptyList(),
) {
    /** FlagGet: bit n of the flags array (or of the bank holding n). */
    fun flag(n: Int): Boolean {
        flagBanks.firstOrNull { (b, _) -> n - b.firstId in 0 until b.count }?.let { (b, bytes) ->
            val i = n - b.firstId
            return (bytes.getOrNull(i / 8)?.toInt() ?: 0) shr (i % 8) and 1 != 0
        }
        return (flags.getOrNull(n / 8)?.toInt() ?: 0) shr (n % 8) and 1 != 0
    }

    /** VarGet for a 0x4000+ var (or one in a bank). */
    fun variable(id: Int): Int {
        varBanks.firstOrNull { (b, _) -> id - b.firstId in 0 until b.count }?.let { (b, bytes) ->
            return u16le(bytes, (id - b.firstId) * 2)
        }
        val i = (id - 0x4000) * 2
        return if (i in 0 until vars.size - 1) u16le(vars, i) else 0
    }

    override fun equals(other: Any?) = other is SaveProgress && flags.contentEquals(other.flags) && vars.contentEquals(other.vars) &&
        flagBanks.map { it.second.toList() } == other.flagBanks.map { it.second.toList() } &&
        varBanks.map { it.second.toList() } == other.varBanks.map { it.second.toList() }
    override fun hashCode() = 31 * flags.contentHashCode() + vars.contentHashCode() +
        flagBanks.sumOf { it.second.contentHashCode() } + varBanks.sumOf { it.second.contentHashCode() }
}

fun readSaveProgress(c: MemoryReader, cfg: NativeConfig, t: GuideTables): SaveProgress? {
    val sb1 = saveBlock1(c, cfg)
    if (sb1 !in 0x02000000L until 0x04000000L) return null
    return SaveProgress(
        c.readCoreMemory(sb1 + t.sb1FlagsOff, t.flagBytes), c.readCoreMemory(sb1 + t.sb1VarsOff, t.varCount * 2),
        t.flagBanks.map { it to c.readCoreMemory(sb1 + it.sb1Off, (it.count + 7) / 8) },
        t.varBanks.map { it to c.readCoreMemory(sb1 + it.sb1Off, it.count * 2) },
    )
}

/** One wild slot group: a species, its level range and its share of that method's encounters. */
data class EncounterSlot(val species: Int, val minLevel: Int, val maxLevel: Int, val percent: Int)

/** A map's wild Pokémon, by method; empty lists = none that way. */
data class MapEncounters(
    val grass: List<EncounterSlot>,
    val water: List<EncounterSlot>,
    val rockSmash: List<EncounterSlot>,
    val oldRod: List<EncounterSlot>,
    val goodRod: List<EncounterSlot>,
    val superRod: List<EncounterSlot>,
) {
    val isEmpty get() = listOf(grass, water, rockSmash, oldRod, goodRod, superRod).all { it.isEmpty() }
}

/** A boss's Pokémon, as its trainer data defines it. [moves] are the custom set or the level-up default. */
data class TrainerMon(val species: Int, val level: Int, val heldItem: Int, val moves: List<Int>)

// src/wild_encounter.c's slot odds (ChooseWildMonIndex_*), per slot.
private val LAND_ODDS = listOf(20, 20, 10, 10, 10, 10, 5, 5, 4, 4, 1, 1)
private val WATER_ODDS = listOf(60, 30, 5, 4, 1)
// Fishing: slots 0-1 OLD ROD, 2-4 GOOD ROD, 5-9 SUPER ROD.
private val OLD_ROD_ODDS = listOf(70, 30)
private val GOOD_ROD_ODDS = listOf(60, 20, 20)
private val SUPER_ROD_ODDS = listOf(40, 40, 15, 4, 1)

/**
 * Reads the GUIDE's ROM tables on demand and caches them. ROM data never
 * changes, so (like [PokedexSource], whose reader it shares) this runs on
 * Compose's IO dispatcher rather than the emulator thread.
 */
object GuideRomSource {
    private val encounters = ConcurrentHashMap<Int, MapEncounters>()
    private val parties = ConcurrentHashMap<Int, List<TrainerMon>>()
    @Volatile private var headers: Map<Int, Long>? = null
    @Volatile private var cachedFor: GuideTables? = null

    private fun sync(t: GuideTables) {
        if (cachedFor == t) return
        synchronized(this) {
            if (cachedFor == t) return
            encounters.clear(); parties.clear(); headers = null
            cachedFor = t
        }
    }

    fun cachedEncounters(t: GuideTables, group: Int, num: Int): MapEncounters? =
        if (cachedFor == t) encounters[group shl 8 or num] else null

    fun cachedParty(t: GuideTables, trainer: Int): List<TrainerMon>? = if (cachedFor == t) parties[trainer] else null

    /** The wild Pokémon of map [group].[num] - empty when it has none. */
    fun encounters(t: GuideTables, group: Int, num: Int): MapEncounters? {
        sync(t)
        val key = group shl 8 or num
        encounters[key]?.let { return it }
        return runCatching {
            val hdr = headerIndex(t)[key]
                ?: return@runCatching MapEncounters(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
            val h = rd(hdr, wildHeaderStride(t))
            // Each method from the chosen time of day, or the first set that has it.
            val sets = listOf(t.wildSet) + (0 until t.wildSets).filter { it != t.wildSet }
            fun ptr(method: Int) = t.wildLayout?.let { l -> u32le(h, 4 + 4 * listOf(l.land, l.water, l.rockSmash, l.fishing)[method]) }
                ?: sets.map { u32le(h, 4 + it * wildSetStride(t) + 4 * method) }.firstOrNull { it != 0L } ?: 0L
            val fishing = slots(ptr(3), 10)
            MapEncounters(
                grass = merge(slots(ptr(0), 12), LAND_ODDS),
                water = merge(slots(ptr(1), 5), WATER_ODDS),
                rockSmash = merge(slots(ptr(2), 5), WATER_ODDS),
                oldRod = merge(fishing.take(2), OLD_ROD_ODDS),
                goodRod = merge(fishing.drop(2).take(3), GOOD_ROD_ODDS),
                superRod = merge(fishing.drop(5), SUPER_ROD_ODDS),
            )
        }.onFailure { android.util.Log.w("pokedaisy", "encounters $group.$num failed", it) }
            .getOrNull()?.also { encounters[key] = it }
    }

    /** Trainer [id]'s party as the game builds it (CreateNPCTrainerParty). */
    fun party(t: GuideTables, id: Int): List<TrainerMon>? {
        sync(t)
        parties[id]?.let { return it }
        return runCatching {
            val local = (id and 0xFFFF).toLong()
            val alt = t.altTrainers.getOrNull((id shr TRAINER_TABLE_SHIFT) - 1)
            val rec = alt?.let { rd(it + local * t.trainerStride, t.trainerStride) }
                ?.takeIf { it[0].toInt() != 0 || u32le(it, t.trainerPartyOff) != 0L }
                ?: rd(t.trainers + local * t.trainerStride, t.trainerStride)
            val flags = rec[0].toInt() and 0xFF
            val size = rec[t.trainerSizeOff].toInt() and 0xFF
            val ptr = u32le(rec, t.trainerPartyOff)
            if (size !in 1..6 || !Gfx.inRom(ptr)) return@runCatching emptyList()
            t.trainerMon?.let { m ->
                val raw = rd(ptr, size * m.stride)
                return@runCatching (0 until size).map { i ->
                    val o = i * m.stride
                    TrainerMon(
                        u16le(raw, o + m.speciesOff), raw[o + m.levelOff].toInt() and 0xFF,
                        heldItem = u16le(raw, o + m.itemOff),
                        moves = (0 until 4).map { u16le(raw, o + m.movesOff + 2 * it) }.filter { it != 0 },
                    )
                }
            }
            val custom = flags and TRAINER_PARTY_CUSTOM_MOVESET != 0
            val item = flags and TRAINER_PARTY_HELD_ITEM != 0
            // {u16 iv, u8 lvl, pad, u16 species, [u16 item], [u16 moves[4]]}, padded to 8 / 16 bytes.
            val stride = if (custom) 16 else 8
            val raw = rd(ptr, size * stride)
            (0 until size).map { i ->
                val o = i * stride
                val species = u16le(raw, o + 4)
                val level = raw[o + 2].toInt() and 0xFF
                val movesOff = if (item) o + 8 else o + 6
                TrainerMon(
                    species, level,
                    heldItem = if (item) u16le(raw, o + 6) else 0,
                    moves = if (custom) (0 until 4).map { u16le(raw, movesOff + 2 * it) }.filter { it != 0 }
                    else defaultMoves(t, species, level),
                )
            }
        }.onFailure { android.util.Log.w("pokedaisy", "trainer $id failed", it) }
            .getOrNull()?.also { parties[id] = it }
    }

    /** GiveMonInitialMoveset: the level-up moves up to [level], newest four, no repeats. */
    private fun defaultMoves(t: GuideTables, species: Int, level: Int): List<Int> {
        if (t.learnsets == 0L) return emptyList() // not located for this game: no moves shown
        val ptr = u32le(rd(t.learnsets + species * 4L, 4), 0)
        if (!Gfx.inRom(ptr)) return emptyList()
        val moves = ArrayDeque<Int>()
        fun learn(move: Int) {
            if (move in moves) return
            if (moves.size == 4) moves.removeFirst()
            moves.addLast(move)
        }
        if (t.learnsetCfru) { // {u16 move, u8 level}, ending at level 0xFF
            val raw = rd(ptr, 3 * 64)
            for (o in 0 until raw.size - 2 step 3) {
                val lv = raw[o + 2].toInt() and 0xFF
                if (lv == 0xFF || lv > level) break
                learn(u16le(raw, o))
            }
            return moves.toList()
        }
        val raw = rd(ptr, 2 * 64)
        var o = 0
        while (o + 1 < raw.size) {
            val v = u16le(raw, o); o += 2
            if (v == 0xFFFF) break
            if (v shr 9 > level) break
            learn(v and 0x1FF)
        }
        return moves.toList()
    }

    // Vanilla: {group, num, pad, 4 pointers}; expansion adds a hidden (DexNav) pointer per set, one set per time of day.
    private fun wildSetStride(t: GuideTables) = if (t.wildSets > 1) WILD_SET_SIZE + 4 else WILD_SET_SIZE
    private fun wildHeaderStride(t: GuideTables) = t.wildLayout?.stride ?: (4 + t.wildSets * wildSetStride(t))

    /** Map (group << 8 | num) -> its header's address, read once. */
    private fun headerIndex(t: GuideTables): Map<Int, Long> {
        headers?.let { return it }
        val out = HashMap<Int, Long>()
        var addr = t.wildHeaders
        while (out.size < 512) {
            val h = rd(addr, 2)
            val group = h[0].toInt() and 0xFF
            if (group == MAP_GROUP_UNDEFINED) break
            // The first header wins, as in GetCurrentMapWildMonHeaderId.
            out.putIfAbsent(group shl 8 or (h[1].toInt() and 0xFF), addr)
            addr += wildHeaderStride(t)
        }
        return out.also { headers = it }
    }

    /** A WildPokemonInfo's [count] slots: {u8 min, u8 max, u16 species} each; none for a null pointer. */
    private fun slots(infoPtr: Long, count: Int): List<Triple<Int, Int, Int>> {
        if (!Gfx.inRom(infoPtr)) return emptyList()
        val monsPtr = u32le(rd(infoPtr + 4, 4), 0)
        if (!Gfx.inRom(monsPtr)) return emptyList()
        val raw = rd(monsPtr, count * 4)
        return (0 until count).map { i ->
            Triple(u16le(raw, i * 4 + 2), raw[i * 4].toInt() and 0xFF, raw[i * 4 + 1].toInt() and 0xFF)
        }
    }

    /** Slots of the same species folded into one line, odds summed, most common first. A slot that
     * can't be one (levels out of order or past 100) is dropped: TMT2 lists 11 land slots, so the 12th
     * is the next table's pointer - the game reads it too on its 1% roll. */
    private fun merge(slots: List<Triple<Int, Int, Int>>, odds: List<Int>): List<EncounterSlot> =
        slots.withIndex().filter { it.value.first != 0 && it.value.second in 1..100 && it.value.third in it.value.second..100 }.groupBy { it.value.first }.map { (species, group) ->
            EncounterSlot(
                species,
                group.minOf { it.value.second },
                group.maxOf { it.value.third },
                group.sumOf { odds.getOrElse(it.index) { 0 } },
            )
        }.sortedByDescending { it.percent }

    private fun rd(addr: Long, n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val chunk = minOf(512, n - off)
            PokedexSource.reader.readCoreMemory(addr + off, chunk).copyInto(out, off, 0, chunk)
            off += chunk
        }
        return out
    }
}

/**
 * Heart and Soul v2.0.6 (pokeemerald-expansion), found in the release ROM:
 * gTrainers from FALKNER's record (0x34 bytes: party pointer at +8, name at
 * +0x1F, party size at +0x2B; trainer 402 = TRAINER_FALKNER_1_HNS in its
 * source), its TrainerMons from his party (PIDGEY Lv8 / NOCTOWL Lv11, 0x24
 * bytes each); gWildMonHeaders from the header pointing at ROUTE 29's day
 * table (PIDGEY, SENTRET, HOPPIP - as wild_encounters.json has it), four
 * time-of-day sets per map (HERE shows the day one). SaveBlock1's flags and
 * vars from the game's own GetFlagPointer / GetVarPointer literals.
 */
val GUIDE_TABLES_HEART_AND_SOUL = GuideTables(
    trainers = 0x084C9C8CL,
    learnsets = 0,
    wildHeaders = 0x091856C4L,
    sb1FlagsOff = 0x198C,
    sb1VarsOff = 0x1ABC,
    flagBytes = 0x130,
    probeTrainer = 402,
    probeName = "FALKNER",
    guide = GuideId.HEART_AND_SOUL,
    trainerStride = 0x34,
    trainerNameOff = 0x1F,
    trainerNameLen = 11,
    trainerSizeOff = 0x2B,
    trainerPartyOff = 8,
    trainerMon = TrainerMonLayout(stride = 0x24, movesOff = 0x0C, speciesOff = 0x14, itemOff = 0x16, levelOff = 0x1A),
    wildSets = 4,
    wildSet = 1,
)

/**
 * Pokémon Unbound v2.1.1.1 (CFRU), found in its ROM: gTrainers is rewritten in
 * place at FireRed rev 0's address (same 0x28-byte records; trainer 6 is its
 * first gym leader, MIRSKLE), gWildMonHeaders moved to 0x08C230D8 (vanilla
 * layout - from the literal pools of FireRed's wild-encounter code), CFRU's
 * 3-byte learnsets at 0x09A2457C (the same literal-pool route).
 */
val GUIDE_TABLES_UNBOUND = GuideTables(
    trainers = 0x0823EAC8L,
    learnsets = 0x09A2457CL,
    learnsetCfru = true,
    wildHeaders = 0x08C230D8L,
    probeTrainer = 6,
    probeName = "Mirskle",
    guide = GuideId.UNBOUND,
)

/**
 * Radical Red v4.1 (CFRU): gTrainers rewritten in place at FireRed rev 0's
 * address (trainer 414 is BROCK, as in FireRed), gWildMonHeaders moved to
 * 0x0872C984 (vanilla layout, found by shape: 142 headers). Learnsets are
 * CFRU's 3-byte format at 0x0980175C.
 */
val GUIDE_TABLES_RADICAL_RED = GuideTables(
    trainers = 0x0823EAC8L,
    learnsets = 0x0980175CL,
    learnsetCfru = true,
    wildHeaders = 0x0872C984L,
    probeTrainer = 414,
    probeName = "Brock",
    guide = GuideId.RADICAL_RED,
)

/** Odyssey v4.1.1: FireRed rev 0's gTrainers, learnsets (vanilla format) and save layout; its own gWildMonHeaders. */
val GUIDE_TABLES_ODYSSEY = GuideTables(
    trainers = 0x0823EAC8L,
    learnsets = 0x0825D7B4L,
    wildHeaders = 0x092C5540L,
    probeTrainer = 33,
    probeName = "Karin",
    guide = GuideId.ODYSSEY,
)

/** Gaia v3.2: rev 0's gTrainers (its leaders at FireRed's 414..421), learnsets and wild headers moved. */
val GUIDE_TABLES_GAIA = GuideTables(
    trainers = 0x0823EAC8L,
    learnsets = 0x082BDD70L,
    wildHeaders = 0x08A2E1CCL,
    probeTrainer = 414,
    probeName = "Fernando",
    guide = GuideId.GAIA,
)

/**
 * Amethyst v1.3.0: its trainer loader (0x088B3A98) picks one of four tables by
 * two save flags - 0x93C (HARD MODE) and 0x945 (the DIVERGENT Pokémon set) -
 * falling back to the standard one for an empty record. Parties are 18-byte
 * records; learnsets are CFRU's.
 */
val GUIDE_TABLES_AMETHYST = GuideTables(
    trainers = 0x089DC0D0L,
    learnsets = 0x09C04590L,
    wildHeaders = 0x083AFD00L,
    probeTrainer = 14,
    probeName = "Terrence",
    guide = GuideId.AMETHYST,
    trainerMon = TrainerMonLayout(stride = 18, movesOff = 8, speciesOff = 4, itemOff = 6, levelOff = 2),
    learnsetCfru = true,
    altTrainers = listOf(0x089CECCCL, 0x089B8A34L, 0x089C63B8L), // HARD, DIVERGENT, DIVERGENT + HARD
)

/** Amethyst v1.4.1: the same loader and flags (0x93C / 0x945), the tables moved; the gym leaders'
 * records and parties are byte for byte v1.3.0's in all four. Its own area data (AMETHYST_V141). */
val GUIDE_TABLES_AMETHYST_V1_4_1 = GUIDE_TABLES_AMETHYST.copy(
    trainers = 0x08A25A14L,
    learnsets = 0x09ADB8D0L,
    guide = GuideId.AMETHYST_V141,
    altTrainers = listOf(0x08A1861CL, 0x08A02384L, 0x08A0FD08L),
)

/**
 * Celia's Stupid Romhack v1.1.4 (a full pokefirered recompile): everything
 * moved, SaveBlock1's flags 16 bytes later (found by setting the POKéDEX flag
 * and diffing the save). Its learnsets aren't located; the leaders carry their
 * own moves anyway.
 */
// Pokémon Glazed: retail Emerald's gTrainers / gLevelUpLearnsets at retail's addresses (its literal
// pools still name them), its gWildMonHeaders repointed to 0x094CA3F4 (all 13 refs); retail's
// SaveBlock1 flags / vars. Its first gym leader, SPARKY, holds ROXANNE's slot.
val GUIDE_TABLES_GLAZED = GUIDE_TABLES_EMERALD.copy(
    wildHeaders = 0x094CA3F4L,
    probeName = "SPARKY",
    guide = GuideId.GLAZED,
)

/**
 * Emerald Imperium v1.3.1 (pokeemerald-expansion 1.10.0), from the release ROM: gTrainers
 * 0x0854FA40 (36 refs; 0x78-byte records - party +4, class +0x10, name +0x13, size +0x20;
 * 265 = Roxanne: Rhyhorn 14, Wooper / Gligar / Nosepass 15, abilities at +0x18 Lightning Rod,
 * Water Absorb, Sand Veil, Sturdy), expansion's 0x24-byte TrainerMon, gWildMonHeaders
 * 0x08ED1CD0 (16 refs, 135 headers of 24 bytes: one set of land / water / rock smash / fishing /
 * hidden - Route 101's land table has the Shinx a walk there met), SaveBlock1 flags / vars at
 * 0x19C4 / 0x1AF0 (GetFlagPointer / GetVarPointer literals).
 */
val GUIDE_TABLES_IMPERIUM = GuideTables(
    trainers = 0x0854FA40L,
    learnsets = 0,
    wildHeaders = 0x08ED1CD0L,
    sb1FlagsOff = 0x19C4,
    sb1VarsOff = 0x1AF0,
    flagBytes = 0x12C,
    probeTrainer = 265,
    probeName = "Roxanne",
    guide = GuideId.IMPERIUM,
    trainerStride = 0x78,
    trainerNameOff = 0x13,
    trainerNameLen = 11,
    trainerSizeOff = 0x20,
    trainerPartyOff = 4,
    trainerMon = TrainerMonLayout(stride = 0x24, movesOff = 0x0C, speciesOff = 0x14, itemOff = 0x16, levelOff = 0x1A),
    wildLayout = WildLayout(stride = 24),
)

/**
 * Pokémon Quetzal English Alpha 9 v0 (its own engine), from the ROM: GetTrainer (0x082942F0)
 * reads gTrainers 0x084FCCB0 (0x20-byte records: party +4, class +0x10, name +0x13, size +0x1F;
 * 265 = ROXANNE) for ids up to 0x3CD, Johto's table 0x09985F94 for 0x1000+, Kanto's 0x099914E8 for
 * 0x2000+ (altTrainers 0 / 1). Boss parties are 28-byte mons (level +8, species +0xA, item +0xE,
 * moves +0x12). gWildMonHeaders 0x0917A8C4: 353 headers of 28 bytes - land / water / a 3-mon
 * table / rock smash / fishing / unused (Kanto Route 1's land table holds all 8 species its
 * overworld showed). Flags 0..0xE97 at SaveBlock1+0, 0x1000..0x2FFF at +0x2F2C; vars 0x4000+
 * at +0x1D4, 0x5000+ at +0x332C (GetFlagPointer / GetVarPointer).
 */
val GUIDE_TABLES_QUETZAL = GuideTables(
    trainers = 0x084FCCB0L,
    learnsets = 0,
    wildHeaders = 0x0917A8C4L,
    sb1FlagsOff = 0,
    sb1VarsOff = 0x1D4,
    flagBytes = 0x1D3,
    probeTrainer = 265,
    probeName = "ROXANNE",
    guide = GuideId.QUETZAL,
    trainerStride = 0x20,
    trainerNameOff = 0x13,
    trainerNameLen = 11,
    trainerSizeOff = 0x1F,
    trainerPartyOff = 4,
    trainerMon = TrainerMonLayout(stride = 28, movesOff = 0x12, speciesOff = 0x0A, itemOff = 0x0E, levelOff = 8),
    altTrainers = listOf(0x09985F94L, 0x099914E8L),
    wildLayout = WildLayout(stride = 28, rockSmash = 3, fishing = 4),
    flagBanks = listOf(SaveBank(firstId = 0x1000, sb1Off = 0x2F2C, count = 0x2000)),
    varBanks = listOf(SaveBank(firstId = 0x5000, sb1Off = 0x332C, count = 0x100)),
)

// The four below from their release ROMs (expansion 1.9.4 / 1.9.2 / 1.12.3 / 1.15.2 per their RHHEXP
// headers): SaveBlock1 flags / vars from the GF ROM header (+0x50 / +0x54); gTrainers found by a gym
// leader's record and its literal-pool refs; HERE checked by warping onto grass headless and walking
// until a wild battle - every foe the game sent (species and level) is in the table read here.

/** Pokémon Lazarus v2.0: gTrainers 0x085DA84C (0x24-byte records; 8 = Polymnia), 0x24-byte TrainerMons,
 * gWildMonHeaders 0x08E67010 (21 refs, 210 headers of 24 bytes: land, water, rock smash, unused, fishing). */
val GUIDE_TABLES_LAZARUS = GuideTables(
    trainers = 0x085DA84CL, learnsets = 0, wildHeaders = 0x08E67010L,
    sb1FlagsOff = 0x12E8, sb1VarsOff = 0x1414, flagBytes = 0x12C,
    probeTrainer = 8, probeName = "Polymnia", guide = GuideId.LAZARUS,
    trainerStride = 0x24, trainerNameOff = 0x13, trainerNameLen = 11, trainerSizeOff = 0x20, trainerPartyOff = 4,
    trainerMon = TrainerMonLayout(stride = 0x24, movesOff = 0x0C, speciesOff = 0x14, itemOff = 0x16, levelOff = 0x1A),
    wildLayout = WildLayout(stride = 24, fishing = 4),
)

/** Emerald Seaglass v3.0: gTrainers 0x0851C8CC (34 refs, Emerald's ids: 265 = ROXANNE), 0x20-byte
 * TrainerMons, gWildMonHeaders 0x08A4E8B8 (17 refs, 137 headers of 24 bytes: land, water, rock smash,
 * hidden, fishing). */
val GUIDE_TABLES_SEAGLASS = GuideTables(
    trainers = 0x0851C8CCL, learnsets = 0, wildHeaders = 0x08A4E8B8L,
    sb1FlagsOff = 0x13C0, sb1VarsOff = 0x14EC, flagBytes = 0x12C,
    probeTrainer = 265, probeName = "ROXANNE", guide = GuideId.SEAGLASS,
    trainerStride = 0x24, trainerNameOff = 0x13, trainerNameLen = 11, trainerSizeOff = 0x20, trainerPartyOff = 4,
    trainerMon = TrainerMonLayout(stride = 0x20, movesOff = 0x0C, speciesOff = 0x14, itemOff = 0x16, levelOff = 0x1A),
    wildLayout = WildLayout(stride = 24, fishing = 4),
)

/** Too Many Types 2 v1.5.2: gTrainers[EASY][NORMAL][HARD][943] at 0x0853C758 (EASY / HARD empty), so its
 * NORMAL array 0x0854696C (0x2C-byte records); 0x24-byte TrainerMons; gWildMonHeaders 0x08F3C590 (14 refs,
 * 135 headers of 84 bytes: four time-of-day sets, only the first filled). */
val GUIDE_TABLES_TMT2 = GuideTables(
    trainers = 0x0854696CL, learnsets = 0, wildHeaders = 0x08F3C590L,
    sb1FlagsOff = 0x14A8, sb1VarsOff = 0x15D4, flagBytes = 0x12C,
    probeTrainer = 265, probeName = "ROXANNE", guide = GuideId.TMT2,
    trainerStride = 0x2C, trainerNameOff = 0x17, trainerNameLen = 11, trainerSizeOff = 0x24, trainerPartyOff = 8,
    trainerMon = TrainerMonLayout(stride = 0x24, movesOff = 0x0C, speciesOff = 0x14, itemOff = 0x16, levelOff = 0x1A),
    wildSets = 4, wildSet = 0,
)

/** SoulGold v1.1.4: gTrainers[EASY][NORMAL][HARD][1164] at 0x0849606C - NORMAL 0x084A4CDC (0x34-byte records
 * like Heart and Soul's; 19 = Falkner), HARD 0x084B394C (used when SaveBlock2+0x16 bit 3 is set and the
 * record has a party). 0x28-byte TrainerMons (level +0x1C). gWildMonHeaders 0x094158D8 (15 refs, 139
 * headers of 84 bytes; set 0 the default). Flags 0x1898 (0x2AA bytes), vars 0x1B42. */
val GUIDE_TABLES_SOULGOLD = GuideTables(
    trainers = 0x084A4CDCL, learnsets = 0, wildHeaders = 0x094158D8L,
    sb1FlagsOff = 0x1898, sb1VarsOff = 0x1B42, flagBytes = 0x2AA,
    probeTrainer = 19, probeName = "Falkner", guide = GuideId.SOULGOLD,
    trainerStride = 0x34, trainerNameOff = 0x1F, trainerNameLen = 11, trainerSizeOff = 0x2B, trainerPartyOff = 8,
    trainerMon = TrainerMonLayout(stride = 0x28, movesOff = 0x0C, speciesOff = 0x14, itemOff = 0x16, levelOff = 0x1C),
    wildSets = 4, wildSet = 0,
    altTrainers = listOf(0x084B394CL), // HARD
)

// v1.2 / v1.2b: the same layouts, ids, flags and scripts; only the tables moved.
val GUIDE_TABLES_SOULGOLD_V1_2 = GUIDE_TABLES_SOULGOLD.copy(trainers = 0x084A5800L, wildHeaders = 0x09420BA8L, altTrainers = listOf(0x084B4470L))
val GUIDE_TABLES_SOULGOLD_V1_2B = GUIDE_TABLES_SOULGOLD.copy(trainers = 0x084A5768L, wildHeaders = 0x09420B04L, altTrainers = listOf(0x084B43D8L))

val GUIDE_TABLES_CELIA = GuideTables(
    trainers = 0x0871FCA0L,
    learnsets = 0,
    wildHeaders = 0x08C01578L,
    sb1FlagsOff = 0xEF0,
    sb1VarsOff = 0x1010,
    guide = GuideId.CELIA,
)
