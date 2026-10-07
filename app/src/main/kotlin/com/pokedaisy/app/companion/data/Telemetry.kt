package com.pokedaisy.app.companion.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

// Mirrors struct QolTelemetryData in build/pokefirered/include/qol_telemetry.h
// exactly - field order and sizes must match byte-for-byte since the ROM
// struct is __attribute__((packed)) and this reads it as a flat, sequential
// little-endian byte stream. See tools/telemetry-viewer/telemetry.go for the
// Go equivalent of this same layout.
const val PARTY_SIZE = 6
const val NUM_MOVES = 4
// v1-v3 ROMs only ever exported the single "Items" bag pocket, 42 slots of a
// 4-byte {itemId, quantity} entry (no pocket tag - LEGACY_ITEM_ENTRY_SIZE
// below reads it as one anyway, defaulted to POCKET_ITEMS).
const val LEGACY_BAG_ITEM_COUNT = 42
const val LEGACY_ITEM_ENTRY_SIZE = 4
// v4: items[] covers every bag pocket, flattened - see QOL_BAG_TOTAL_SLOTS in
// qol_telemetry.h - and each 5-byte entry is tagged with which pocket it came
// from. This RESIZES items[] (not just an appended trailing block like v2/v3
// were), which is why decoding branches on `version`, not just `structSize`.
const val BAG_TOTAL_SLOTS = 186
const val ITEM_ENTRY_SIZE = 5
// v2 struct: +16 bytes of icon-table ROM pointers appended after items[].
// v3 struct: +4 bytes of battleActiveBattler/battleInputState after that.
// v4 struct: items[] grown as above (LEGACY_BAG_ITEM_COUNT*4 -> BAG_TOTAL_SLOTS*5),
// icon-table + battle-input blocks unchanged, just shifted later.
const val TELEMETRY_SIZE_V1 = 444
const val TELEMETRY_SIZE_V2 = 460
const val TELEMETRY_SIZE_V3 = 464
const val TELEMETRY_SIZE_V4 = 1226
const val TELEMETRY_SIZE = TELEMETRY_SIZE_V4

const val BATTLE_POS_PLAYER_LEFT = 0
const val BATTLE_POS_OPPONENT_LEFT = 1
const val BATTLE_POS_PLAYER_RIGHT = 2
const val BATTLE_POS_OPPONENT_RIGHT = 3

const val TYPE_NONE = 255

// battleInputState values - mirrors QOL_BATTLE_INPUT_* in qol_telemetry.h.
const val BATTLE_INPUT_NONE = 0
const val BATTLE_INPUT_BUSY = 1
const val BATTLE_INPUT_ACTION_SELECT = 2
const val BATTLE_INPUT_MOVE_SELECT = 3
const val BATTLE_INPUT_BAG_OPEN = 4
const val BATTLE_INPUT_PARTY_OPEN = 5
const val BATTLE_INPUT_TARGET_SELECT = 6

// Item.pocket values - stable, game-independent ids matching QOL_POCKET_* in
// qol_telemetry.h. NOT the same as either game's own engine POCKET_*
// constants, which number the same pockets differently between FireRed and
// Emerald.
const val POCKET_ITEMS = 0
const val POCKET_POKE_BALLS = 1
const val POCKET_TM_HM = 2
const val POCKET_BERRIES = 3
const val POCKET_KEY_ITEMS = 4

data class Mon(
    val species: Int,
    val level: Int,
    val hp: Int,
    val maxHp: Int,
    val status: Long,
    val moves: IntArray,
    val pp: IntArray,
    // The party menu's gender symbol (GENDER_SYMBOL_*), when known.
    val genderSymbol: Int = GENDER_SYMBOL_UNKNOWN,
    // Total experience points (the Growth substruct), when read from the
    // decrypted RAM struct - not part of the QoL telemetry struct itself.
    val exp: Long? = null,
    // The mon's personality value (its RAM struct's first word): which mon
    // the battle POKéMON pane switches to. 0 = unknown.
    val personality: Long = 0,
    // IVs / EVs / nature from the decrypted RAM struct; null = unknown.
    val stats: MonStats? = null,
)

// QOL_GENDER_* in the ROM's qol_telemetry.h: what FireRed's party menu shows
// next to the level. UNKNOWN = a ROM from before this byte existed (it was
// padding, always 0) or a source that can't tell.
const val GENDER_SYMBOL_UNKNOWN = 0
const val GENDER_SYMBOL_MALE = 1
const val GENDER_SYMBOL_FEMALE = 2
const val GENDER_SYMBOL_NONE = 3

data class BattleMon(
    val species: Int,
    val level: Int,
    val type1: Int,
    val type2: Int,
    val hp: Int,
    val maxHp: Int,
    val status1: Long,
    val moves: IntArray,
    val pp: IntArray,
)

data class Item(val itemId: Int, val quantity: Int, val pocket: Int = POCKET_ITEMS)

data class Telemetry(
    val frameCounter: Long,
    val inBattle: Boolean,
    val isDoubleBattle: Boolean,
    val mapGroup: Int,
    val mapNum: Int,
    val regionMapSectionId: Int,
    val x: Int,
    val y: Int,
    val facingDirection: Int,
    val partyCount: Int,
    val party: List<Mon>,
    val battleMons: List<BattleMon>, // size 4, indexed by BATTLE_POS_*
    val itemCount: Int,
    val items: List<Item>,
    val icons: IconTables = IconTables(),
    // v3: which battler is choosing and what menu is up right now, for
    // driving synthetic button presses (bottom-screen touch battle control).
    // BATTLE_INPUT_NONE/battler 0 on a pre-v3 ROM.
    val battleActiveBattler: Int = 0,
    val battleInputState: Int = BATTLE_INPUT_NONE,
    /** Native reads of games with a [NativeConfig.pokedex] only. */
    val pokedex: PokedexState? = null,
    /** Event flags / vars, for games with [NativeConfig.guideTables]. */
    val progress: SaveProgress? = null,
    val guideTables: GuideTables? = null,
    /** A trainer battle's whole opposing party ([NativeConfig.enemyParty]). */
    val enemyParty: List<Mon> = emptyList(),
    /** gEnemyParty in any battle, wild ones included: where the foe battlers' IVs come from. */
    val battleFoes: List<Mon> = emptyList(),
    /** [enemyParty] index of the foe that's out, and of monToSwitchIntoId (its trainer's last pick); -1 = unknown. */
    val enemyActive: Int = -1,
    val enemyNext: Int = -1,
    /** The player's money; null = not read for this game (see [readMoney]). */
    val money: Long? = null,
    /** The current map's size in metatiles and gMapHeader.mapType (see [readMapShape]); 0 / -1 = unknown. */
    val mapWidth: Int = 0,
    val mapHeight: Int = 0,
    val mapType: Int = -1,
    /** SaveBlock2.playerGender: 0 male, 1 female, -1 unknown. */
    val playerGender: Int = -1,
    /** The name the game itself prints here, over the mapsec table's (Emerald Rogue's own hub name). */
    val mapSecName: String? = null,
    /** What the TRAINER CARD shows (TrainerCard.kt); null where the game has no [NativeConfig.trainerCard]. */
    val trainerCard: TrainerCardInfo? = null,
)

/** v2 struct: ROM addresses of the icon-graphics tables (0 if pre-v2 ROM). */
data class IconTables(
    val monIconTable: Long = 0,
    val monIconPaletteTable: Long = 0,
    val monIconPaletteIndices: Long = 0,
    val itemIconTable: Long = 0,
    // Per-entry strides, defaulting to the vanilla decomp arrays
    // (gMonIconTable: u32 ptr; gMonIconPaletteIndices: u8; gItemIconTable:
    // {tiles, pal} pair). pokeemerald-expansion hacks don't have those arrays -
    // the same fields live INSIDE each gSpeciesInfo/gItemsInfo entry - so they
    // point the three bases at the field within entry 0 and set the stride to
    // the whole struct's size instead (see NATIVE_EMERALD_SEAGLASS).
    val monIconStride: Int = 4,
    val monPalIdxStride: Int = 1,
    // Expansion packs iconPalIndex into a 3-bit bitfield sharing its byte
    // with iconPalIndexFemale.
    val monPalIdxMask: Int = 0xFF,
    val itemIconStride: Int = 8,
    // Newer pokeemerald-expansion stores item icon palettes as raw 32-byte
    // BGR555, not LZ77 like vanilla/older expansion (tiles stay LZ77).
    val itemPalCompressed: Boolean = true,
    // A second item table for ids [extraItemFirst, extraItemFirst + extraItemCount):
    // {tiles, pal} pointer pairs at extraItemIconTable + (id - first) * stride -
    // Emerald Rogue keeps its own items' icons in gRogueItems, not gItemIconTable.
    val extraItemIconTable: Long = 0,
    val extraItemFirst: Int = 0,
    val extraItemCount: Int = 0,
    val extraItemStride: Int = 8,
) {
    // Split per-kind: some hacks relocate one table but not the other (Gaia
    // keeps sItemIconTable at its vanilla address but moved gMonIconTable
    // elsewhere for its ~500 extra species - see NATIVE_GAIA_V3_2), so gating
    // both on a single combined flag would silently disable item icons too.
    val monPresent get() = monIconTable != 0L
    val itemPresent get() = itemIconTable != 0L
    val present get() = monPresent && itemPresent
}

class TelemetryDecodeException(message: String) : Exception(message)

fun decodeTelemetry(raw: ByteArray): Telemetry {
    if (raw.size < TELEMETRY_SIZE_V1) {
        throw TelemetryDecodeException("short read: got ${raw.size} bytes, want $TELEMETRY_SIZE_V1+")
    }
    val buf = ByteBuffer.wrap(raw, 0, minOf(raw.size, TELEMETRY_SIZE)).order(ByteOrder.LITTLE_ENDIAN)

    val magic = ByteArray(4)
    buf.get(magic)
    val magicStr = String(magic, Charsets.US_ASCII)
    if (magicStr != "QOLT") {
        throw TelemetryDecodeException("bad magic \"$magicStr\" - wrong address, or the ROM isn't the QoL telemetry build")
    }

    val version = buf.short.toInt() and 0xFFFF
    val structSize = buf.short.toInt() and 0xFFFF
    // v4 resized items[] itself (not just an appended trailing block, unlike
    // v2/v3), so which tier applies has to come from `version`, not just a
    // structSize magnitude comparison - a v3 ROM and a v4 ROM can't be told
    // apart by size alone once icon-table/battle-input bytes are involved.
    val isV4 = version >= 4
    val minSize = when {
        isV4 -> TELEMETRY_SIZE_V4
        else -> TELEMETRY_SIZE_V1
    }
    if (structSize < minSize) {
        throw TelemetryDecodeException("struct size mismatch: ROM reports $structSize bytes (version $version), app expects at least $minSize (rebuild the ROM/app together)")
    }
    // v4 always carries both (it's a strict extension of v3) - checked above.
    val hasIcons = isV4 || structSize >= TELEMETRY_SIZE_V2
    val hasBattleInput = isV4 || structSize >= TELEMETRY_SIZE_V3
    val frameCounter = buf.int.toLong() and 0xFFFFFFFFL

    val inBattle = buf.get().toInt() != 0
    val isDoubleBattle = buf.get().toInt() != 0
    val mapGroup = buf.get().toInt() and 0xFF
    val mapNum = buf.get().toInt() and 0xFF
    val regionMapSectionId = buf.get().toInt() and 0xFF
    val x = buf.short.toInt() and 0xFFFF
    val y = buf.short.toInt() and 0xFFFF
    val facingDirection = buf.get().toInt() and 0xFF

    val partyCount = buf.get().toInt() and 0xFF
    buf.get() // reserved1

    val party = (0 until PARTY_SIZE).map { readMon(buf) }
    val battleMons = (0 until 4).map { readBattleMon(buf) }

    val itemCount = buf.short.toInt() and 0xFFFF
    buf.short // reserved2
    val items = if (isV4) {
        (0 until BAG_TOTAL_SLOTS).map {
            Item(buf.short.toInt() and 0xFFFF, buf.short.toInt() and 0xFFFF, buf.get().toInt() and 0xFF)
        }
    } else {
        // Pre-v4 ROMs only ever exported the "Items" pocket - tag every entry
        // with that pocket so callers don't need a separate pre/post-v4 path.
        (0 until LEGACY_BAG_ITEM_COUNT).map {
            Item(buf.short.toInt() and 0xFFFF, buf.short.toInt() and 0xFFFF, POCKET_ITEMS)
        }
    }

    val icons = if (hasIcons) {
        fun u32() = buf.int.toLong() and 0xFFFFFFFFL
        IconTables(u32(), u32(), u32(), u32())
    } else {
        IconTables()
    }

    var battleActiveBattler = 0
    var battleInputState = BATTLE_INPUT_NONE
    if (hasBattleInput) {
        battleActiveBattler = buf.get().toInt() and 0xFF
        battleInputState = buf.get().toInt() and 0xFF
        buf.short // reserved3
    }

    return Telemetry(
        frameCounter = frameCounter,
        inBattle = inBattle,
        isDoubleBattle = isDoubleBattle,
        mapGroup = mapGroup,
        mapNum = mapNum,
        regionMapSectionId = regionMapSectionId,
        x = x,
        y = y,
        facingDirection = facingDirection,
        partyCount = partyCount,
        party = party,
        battleMons = battleMons,
        itemCount = itemCount,
        items = items,
        icons = icons,
        battleActiveBattler = battleActiveBattler,
        battleInputState = battleInputState,
    )
}

private fun readMon(buf: ByteBuffer): Mon {
    val species = buf.short.toInt() and 0xFFFF
    val level = buf.get().toInt() and 0xFF
    val genderSymbol = buf.get().toInt() and 0xFF // was padding before QOL_GENDER_*
    val hp = buf.short.toInt() and 0xFFFF
    val maxHp = buf.short.toInt() and 0xFFFF
    val status = buf.int.toLong() and 0xFFFFFFFFL
    val moves = IntArray(NUM_MOVES) { buf.short.toInt() and 0xFFFF }
    val pp = IntArray(NUM_MOVES) { buf.get().toInt() and 0xFF }
    return Mon(species, level, hp, maxHp, status, moves, pp, genderSymbol)
}

private fun readBattleMon(buf: ByteBuffer): BattleMon {
    val species = buf.short.toInt() and 0xFFFF
    val level = buf.get().toInt() and 0xFF
    val type1 = buf.get().toInt() and 0xFF
    val hp = buf.short.toInt() and 0xFFFF
    val maxHp = buf.short.toInt() and 0xFFFF
    val status1 = buf.int.toLong() and 0xFFFFFFFFL
    val type2 = buf.get().toInt() and 0xFF
    buf.get() // padding
    val moves = IntArray(NUM_MOVES) { buf.short.toInt() and 0xFFFF }
    val pp = IntArray(NUM_MOVES) { buf.get().toInt() and 0xFF }
    return BattleMon(species, level, type1, type2, hp, maxHp, status1, moves, pp)
}
