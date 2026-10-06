package com.pokedaisey.app.companion.data

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
 * Decodes one 100-byte struct Pokemon at [off]. Tries the retail Gen 3 format
 * (substructs encrypted with key = PID ^ OTID, ordered by PID % 24, gated on the
 * checksum), falling back to Unbound's variant (plaintext, fixed order, no
 * checksum). Returns null for an empty slot.
 */
fun decodePartyMon(raw: ByteArray, off: Int): Mon? {
    if (raw.size < off + MON_STRUCT_SIZE) return null

    var species: Int
    val moves: IntArray
    val pp: IntArray
    var exp: Long? = null

    val dec = decryptGen3Substructs(raw, off)
    if (dec != null) {
        species = dec.species
        moves = dec.moves
        pp = dec.pp
        exp = dec.exp
    } else {
        species = u16(raw, off + 0x20)
        if (species == 0 || species > 4000) return null
        moves = IntArray(NUM_MOVES) { u16(raw, off + 0x2C + it * 2) }
        pp = IntArray(NUM_MOVES) { raw[off + 0x2C + 8 + it].toInt() and 0xFF }
    }
    if (species == 0) return null

    return Mon(
        species = species,
        level = raw[off + 0x54].toInt() and 0xFF,
        hp = u16(raw, off + 0x56),
        maxHp = u16(raw, off + 0x58),
        status = u32(raw, off + 0x50),
        moves = moves,
        pp = pp,
        exp = exp,
        personality = u32(raw, off),
    )
}

private class Gen3Substructs(val species: Int, val exp: Long, val moves: IntArray, val pp: IntArray)

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
    for (slot in 0 until 4) {
        when (order[slot]) {
            0 -> growthOff = slot * 12
            1 -> attacksOff = slot * 12
        }
    }
    // Growth: species u16, held item u16, experience u32.
    val species = u16(block, growthOff)
    val exp = u32(block, growthOff + 4)
    val moves = IntArray(NUM_MOVES) { u16(block, attacksOff + it * 2) }
    val pp = IntArray(NUM_MOVES) { block[attacksOff + 8 + it].toInt() and 0xFF }
    return Gen3Substructs(species, exp, moves, pp)
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
)

val VANILLA_BATTLE_MON = BattleMonLayout(
    size = BATTLE_POKEMON_SIZE, moves = 0x0C, type1 = 0x21, type2 = 0x22, pp = 0x24,
    hp = 0x28, level = 0x2A, maxHp = 0x2C, status1 = 0x4C,
)

/** Decodes one struct BattlePokemon (a gBattleMons entry) at [off]. */
fun decodeBattleMon(raw: ByteArray, off: Int, layout: BattleMonLayout = VANILLA_BATTLE_MON): BattleMon? {
    if (raw.size < off + layout.size) return null
    val species = u16(raw, off + 0x00)
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
