package com.pokedaisy.app.companion.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Gen 3 raw Pokémon-structure decoding, shared by every native-RAM reader
 * (retail FireRed / Emerald, Unbound). Turns gPlayerParty / gBattleMons bytes
 * into the same Mon / BattleMon values the packed gQolTelemetry struct yields.
 * Kotlin port of tools/telemetry-viewer/gen3mon.go.
 */
const val MON_STRUCT_SIZE = 100
const val BATTLE_POKEMON_SIZE = 0x58

// gen3SubstructOrder[personality % 24][physicalSlot] = logical substruct index
// (0=Growth, 1=Attacks, 2=EVs, 3=Misc). Classic "GAEM" permutation table.
private val gen3SubstructOrder = arrayOf(
    intArrayOf(0, 1, 2, 3), intArrayOf(0, 1, 3, 2), intArrayOf(0, 2, 1, 3),
    intArrayOf(0, 2, 3, 1), intArrayOf(0, 3, 1, 2), intArrayOf(0, 3, 2, 1),
    intArrayOf(1, 0, 2, 3), intArrayOf(1, 0, 3, 2), intArrayOf(1, 2, 0, 3),
    intArrayOf(1, 2, 3, 0), intArrayOf(1, 3, 0, 2), intArrayOf(1, 3, 2, 0),
    intArrayOf(2, 0, 1, 3), intArrayOf(2, 0, 3, 1), intArrayOf(2, 1, 0, 3),
    intArrayOf(2, 1, 3, 0), intArrayOf(2, 3, 0, 1), intArrayOf(2, 3, 1, 0),
    intArrayOf(3, 0, 1, 2), intArrayOf(3, 0, 2, 1), intArrayOf(3, 1, 0, 2),
    intArrayOf(3, 1, 2, 0), intArrayOf(3, 2, 0, 1), intArrayOf(3, 2, 1, 0),
)

private fun u32(b: ByteArray, off: Int): Long =
    (ByteBuffer.wrap(b, off, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong()) and 0xFFFFFFFFL

private fun u16(b: ByteArray, off: Int): Int =
    (ByteBuffer.wrap(b, off, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()) and 0xFFFF

/**
 * Where struct Pokemon keeps the party-only fields after the BoxPokemon, and
 * the box's flags byte (isBadEgg / hasSpecies / isEgg) - [size] is the whole
 * struct. Vanilla: 100 bytes, status at +0x50. SoulGold's is 96 bytes: a
 * 12-character nickname pushes the flags to +0x15, and the box ends at +0x4C
 * with status, then level / HP / max HP from +0x50 (headless: a poisoned copy
 * at +0x4C showed PSN, copies 96 bytes apart showed as party mons). Quetzal's is
 * its own too ([QUETZAL_PARTY_MON]).
 */
data class PartyMonLayout(
    val size: Int = MON_STRUCT_SIZE,
    val status: Int = 0x50,
    val level: Int = 0x54,
    val hp: Int = 0x56,
    val maxHp: Int = 0x58,
    val flags: Int = 0x13,
    /** The species bits of a plaintext (unencrypted) box: SoulGold packs teraType above its 11. */
    val plainSpeciesMask: Int = 0xFFFF,
    /** Where a plaintext box's substructs start (growth, attacks, EVs, misc, in that order). */
    val plainBox: Int = 0x20,
    /** A plaintext box's experience is read (growth +4); the older plaintext games' never was. */
    val plainExp: Boolean = false,
    /** HP's bits in the u16 at [hp] (Quetzal: 10, packed with other fields); 16 = all of it. */
    val hpBits: Int = 16,
    /** status1 is the u32 at [status] shifted down this far, then masked. */
    val statusShift: Int = 0,
    val statusMask: Long = 0xFFFFFFFFL,
    /** The egg flag is the IV word's bit 30 (vanilla's isEgg bit), not [flags]' bit 2. */
    val eggInIvWord: Boolean = false,
    /** IVs / EVs / nature / stats come out right from these offsets (checked by recomputing the stats). */
    val hasStats: Boolean = size == MON_STRUCT_SIZE,
    /** The egg bit in the [flags] byte (vanilla's isEgg is 0x04). */
    val eggMask: Int = 0x04,
    /** R.O.W.E.'s bit-packed struct ([ROWE_PARTY_MON], [decodeRoweMon]) rather than a box of substructs. */
    val rowe: Boolean = false,
)

val VANILLA_PARTY_MON = PartyMonLayout()

/**
 * R.O.W.E. v2.x's 0x4C-byte struct Pokemon: plaintext and bit-packed, no substructs, checksum or
 * IVs (stats recompute with IV 0). Read off its GetBoxMonData / GetMonData (a jump table over every
 * field) and checked headless by poking each one: personality, otId, a 12-byte nickname; u32 +0x14
 * = move 1 (10 bits) | experience (21) << 10; u32 +0x18 = moves 2 and 3 (10 bits each); u32 +0x1C =
 * species (12 bits, National Dex ids; a 5-bit formId above) with move 4 in the u16 at +0x1E (bits
 * 1-10); EVs +0x20; +0x27 = nature (bits 2-6, what the summary shows - not PID % 25) | isEgg (bit 7);
 * PP +0x34; status +0x38, level +0x3C, HP / max HP +0x3E / +0x40, the stats after.
 */
val ROWE_PARTY_MON = PartyMonLayout(
    size = 0x4C, status = 0x38, level = 0x3C, hp = 0x3E, maxHp = 0x40, flags = 0x27, eggMask = 0x80, hasStats = true, rowe = true,
)

private fun decodeRoweMon(raw: ByteArray, off: Int): Mon? {
    val w14 = u32(raw, off + 0x14)
    val w18 = u32(raw, off + 0x18)
    val species = (u32(raw, off + 0x1C) and 0xFFF).toInt()
    if (species == 0) return null
    return Mon(
        species = species,
        level = raw[off + 0x3C].toInt() and 0xFF,
        hp = u16(raw, off + 0x3E),
        maxHp = u16(raw, off + 0x40),
        status = u32(raw, off + 0x38),
        moves = intArrayOf((w14 and 0x3FF).toInt(), (w18 and 0x3FF).toInt(), ((w18 shr 10) and 0x3FF).toInt(), (u16(raw, off + 0x1E) shr 1) and 0x3FF),
        pp = IntArray(NUM_MOVES) { raw[off + 0x34 + it].toInt() and 0xFF },
        exp = (w14 shr 10) and 0x1FFFFF,
        personality = u32(raw, off),
        stats = MonStats(
            ivs = List(6) { 0 },
            evs = List(6) { raw[off + 0x20 + it].toInt() and 0xFF },
            nature = ((raw[off + 0x27].toInt() and 0xFF) shr 2) and 0x1F,
            stats = List(6) { u16(raw, off + 0x40 + 2 * it) },
        ),
    )
}

/**
 * Pokémon Quetzal's 104-byte plaintext struct Pokemon (checksum 0): vanilla's box
 * header, then the growth / attacks / EVs / misc substructs from +0x28 (species,
 * item, exp; moves +0x34, PP +0x3C (3 PP Ups on everything: Tackle 56); EVs +0x40;
 * the IV word +0x50, whose bit 30 is the egg flag), level +0x58, max HP and the
 * stats from +0x5A. Current HP is 10 bits at +0x23 and status1 the u32 at +0x24
 * shifted down 2, its low 12 bits (+0x26 is 0x80 on every Pokémon: not status). Each checked headless by poking it and looking at the party
 * menu / summary (gen_quetzal_tables.py; NATIVE_QUETZAL).
 */
val QUETZAL_PARTY_MON = PartyMonLayout(
    size = 0x68, status = 0x24, level = 0x58, hp = 0x23, maxHp = 0x5A,
    plainBox = 0x28, plainExp = true, hpBits = 10, statusShift = 2, statusMask = 0xFFF, eggInIvWord = true, hasStats = true,
)
val SOULGOLD_PARTY_MON = PartyMonLayout(
    size = 96, status = 0x4C, level = 0x50, hp = 0x52, maxHp = 0x54, flags = 0x15, plainSpeciesMask = 0x07FF,
)

/**
 * Decodes one struct Pokemon at [off]. Tries the retail Gen 3 format
 * (substructs encrypted with key = PID ^ OTID, ordered by PID % 24, gated on the
 * checksum), falling back to Unbound's variant (plaintext, fixed order, no
 * checksum). Returns null for an empty slot.
 */
fun decodePartyMon(raw: ByteArray, off: Int, layout: PartyMonLayout = VANILLA_PARTY_MON): Mon? {
    if (raw.size < off + layout.size) return null
    if (layout.rowe) return decodeRoweMon(raw, off)

    var species: Int
    val moves: IntArray
    val pp: IntArray
    var exp: Long? = null
    val evs: List<Int>
    val ivWord: Long

    val dec = decryptGen3Substructs(raw, off)
    if (dec != null) {
        species = dec.species
        moves = dec.moves
        pp = dec.pp
        exp = dec.exp
        evs = dec.evs
        ivWord = dec.ivWord
    } else {
        val box = off + layout.plainBox
        species = u16(raw, box) and layout.plainSpeciesMask
        if (species == 0 || species > 4000) return null
        moves = IntArray(NUM_MOVES) { u16(raw, box + 0x0C + it * 2) }
        pp = IntArray(NUM_MOVES) { raw[box + 0x0C + 8 + it].toInt() and 0xFF }
        // Plaintext boxes keep the substructs in G-A-E-M order: EVs at +0x18, IVs at Misc +4.
        evs = List(6) { raw[box + 0x18 + it].toInt() and 0xFF }
        ivWord = u32(raw, box + 0x28)
        if (layout.plainExp) exp = u32(raw, box + 4)
    }
    if (species == 0) return null

    return Mon(
        species = species,
        level = raw[off + layout.level].toInt() and 0xFF,
        hp = u16(raw, off + layout.hp) and ((1 shl layout.hpBits) - 1),
        maxHp = u16(raw, off + layout.maxHp),
        status = (u32(raw, off + layout.status) shr layout.statusShift) and layout.statusMask,
        moves = moves,
        pp = pp,
        exp = exp,
        personality = u32(raw, off),
        stats = if (layout.hasStats) monStats(raw, off, layout, evs, ivWord) else null,
    )
}

/**
 * IVs (5 bits each, HP first), EVs and nature - checked against every fixture
 * by recomputing its stats from the ROM's base stats. Not for SoulGold's
 * shorter struct: its stats didn't add up from these offsets.
 */
private fun monStats(raw: ByteArray, off: Int, layout: PartyMonLayout, evs: List<Int>, ivWord: Long): MonStats {
    val pidNature = (u32(raw, off) % 25).toInt()
    // pokeemerald-expansion's Mints: hiddenNatureModifier sits above the 3
    // language bits at +0x12 (0 in every other game) and is XORed in.
    val nature = (pidNature xor ((raw[off + 0x12].toInt() and 0xFF) ushr 3)).takeIf { it < 25 } ?: pidNature
    return MonStats(
        ivs = List(6) { ((ivWord shr (5 * it)) and 0x1F).toInt() },
        evs = evs,
        nature = nature,
        stats = List(6) { u16(raw, off + layout.maxHp + 2 * it) },
    )
}

private class Gen3Substructs(
    val species: Int, val exp: Long, val moves: IntArray, val pp: IntArray,
    val evs: List<Int>, val ivWord: Long,
)

private fun decryptGen3Substructs(raw: ByteArray, off: Int): Gen3Substructs? {
    val personality = u32(raw, off + 0)
    val otId = u32(raw, off + 4)
    val storedChecksum = u16(raw, off + 28)
    val key = personality xor otId

    val block = ByteArray(48)
    var sum = 0
    for (i in 0 until 12) {
        val w = (u32(raw, off + 0x20 + i * 4) xor key) and 0xFFFFFFFFL
        block[i * 4] = (w and 0xFF).toByte()
        block[i * 4 + 1] = ((w shr 8) and 0xFF).toByte()
        block[i * 4 + 2] = ((w shr 16) and 0xFF).toByte()
        block[i * 4 + 3] = ((w shr 24) and 0xFF).toByte()
        sum = (sum + (w.toInt() and 0xFFFF) + ((w ushr 16).toInt() and 0xFFFF)) and 0xFFFF
    }
    if (sum != storedChecksum || storedChecksum == 0) return null

    val order = gen3SubstructOrder[(personality % 24).toInt()]
    var growthOff = 0
    var attacksOff = 0
    var evsOff = 0
    var miscOff = 0
    for (slot in 0 until 4) {
        when (order[slot]) {
            0 -> growthOff = slot * 12
            1 -> attacksOff = slot * 12
            2 -> evsOff = slot * 12
            3 -> miscOff = slot * 12
        }
    }
    // Growth: species u16, held item u16, experience u32.
    val species = u16(block, growthOff)
    val exp = u32(block, growthOff + 4)
    val moves = IntArray(NUM_MOVES) { u16(block, attacksOff + it * 2) }
    val pp = IntArray(NUM_MOVES) { block[attacksOff + 8 + it].toInt() and 0xFF }
    // EVs: HP, ATK, DEF, SPEED, SP.ATK, SP.DEF bytes. Misc: pokerus, met location, origin, then the IV word.
    val evs = List(6) { block[evsOff + it].toInt() and 0xFF }
    val ivWord = u32(block, miscOff + 4)
    return Gen3Substructs(species, exp, moves, pp, evs, ivWord)
}

/**
 * struct BattlePokemon's size and field offsets. Vanilla FireRed/Emerald (and
 * CFRU) share [VANILLA_BATTLE_MON]; pokeemerald-expansion reshuffles and grows
 * it, so each expansion hack whose battle was actually captured gets its own.
 */
data class BattleMonLayout(
    val size: Int,
    val moves: Int,
    val type1: Int,
    val type2: Int,
    val pp: Int,
    val hp: Int,
    val level: Int,
    val maxHp: Int,
    val status1: Int,
    /** Species bits: R.O.W.E. keeps a 5-bit formId above its 11. */
    val speciesMask: Int = 0xFFFF,
)

val VANILLA_BATTLE_MON = BattleMonLayout(
    size = BATTLE_POKEMON_SIZE, moves = 0x0C, type1 = 0x21, type2 = 0x22, pp = 0x24,
    hp = 0x28, level = 0x2A, maxHp = 0x2C, status1 = 0x4C,
)

/**
 * pokeemerald-expansion's 0x60-byte struct BattlePokemon (Emerald Rogue's,
 * from its headers compiled for ARM; Lazarus's matches it in memory).
 */
val EXPANSION_BATTLE_MON = BattleMonLayout(
    size = 0x60, moves = 0x0C, type1 = 0x22, type2 = 0x23, pp = 0x25,
    hp = 0x2A, level = 0x2C, maxHp = 0x2E, status1 = 0x50,
)

/** Too Many Types 2's: expansion's, but PP a byte later (a third type byte at +0x24). */
val TMT2_BATTLE_MON = EXPANSION_BATTLE_MON.copy(pp = 0x26)

/** R.O.W.E.'s: expansion's layout, the species' form id in its top 5 bits. */
val ROWE_BATTLE_MON = EXPANSION_BATTLE_MON.copy(speciesMask = 0x7FF)

/**
 * SoulGold's 0x98-byte struct BattlePokemon: expansion's fields up to max HP
 * where [EXPANSION_BATTLE_MON] has them, then a longer nickname (+0x34) that
 * pushes experience / personality to +0x4C / +0x50 and status1 to +0x54
 * (headless: poison poked there hurt the mon at the end of the turn).
 */
val SOULGOLD_BATTLE_MON = EXPANSION_BATTLE_MON.copy(size = 0x98, status1 = 0x54)

/** Decodes one struct BattlePokemon (a gBattleMons entry) at [off]. */
fun decodeBattleMon(raw: ByteArray, off: Int, layout: BattleMonLayout = VANILLA_BATTLE_MON): BattleMon? {
    if (raw.size < off + layout.size) return null
    val species = u16(raw, off + 0x00) and layout.speciesMask
    if (species == 0) return null
    return BattleMon(
        species = species,
        level = raw[off + layout.level].toInt() and 0xFF,
        type1 = raw[off + layout.type1].toInt() and 0xFF,
        type2 = raw[off + layout.type2].toInt() and 0xFF,
        hp = u16(raw, off + layout.hp),
        maxHp = u16(raw, off + layout.maxHp),
        status1 = u32(raw, off + layout.status1),
        moves = IntArray(NUM_MOVES) { u16(raw, off + layout.moves + it * 2) },
        pp = IntArray(NUM_MOVES) { raw[off + layout.pp + it].toInt() and 0xFF },
    )
}
