package com.pokedaisy.app.companion.data

/**
 * Using an item from the companion, the way the game does it - by calling the
 * game's own functions between frames (MgbaCore.pkCall), not by steering its
 * menus with button presses. So far the Repels: what ItemUseOutOfBattle_Repel
 * ends in, without its message box - if VarGet(VAR_REPEL_STEP_COUNT) is 0,
 * VarSet(VAR_REPEL_STEP_COUNT, the item's holdEffectParam) and
 * RemoveBagItem(item, 1). The game's own step counter then counts it down and
 * says "REPEL's effect wore off" by itself.
 *
 * Only where the player could open the bag: on the field, controls free
 * (ArePlayerFieldControlsLocked: a script, a message or the START menu holds
 * them) and no menu screen up. Each address is retail's (the pinned decomp
 * builds' functions, found byte for byte in the retail ROMs), checked headless
 * with mgba_dump's `call` on the user's saves: the Repel left the bag, the
 * counter read 100 and dropped 7 after 7 steps; FireRed's controls read locked
 * while its quest-log recap was up.
 */
data class FieldItemFns(
    val varGet: Long,
    val varSet: Long,
    val checkBagHasItem: Long,
    val removeBagItem: Long,
    val fieldControlsLocked: Long,
    /** VAR_REPEL_STEP_COUNT (FireRed 0x4020, Emerald 0x4021). */
    val repelVar: Int,
    /** gItems: 44-byte struct Item, holdEffectParam at +0x13 (a Repel's steps). */
    val items: Long,
    /** CRC32 of each function's first 32 bytes (varGet, varSet, checkBagHasItem, removeBagItem,
     * fieldControlsLocked): a build that moved them (FireRed's QoL build) is left alone. */
    val codeCrcs: List<Long>,
) {
    val functions get() = listOf(varGet, varSet, checkBagHasItem, removeBagItem, fieldControlsLocked)
}

/** Retail FireRed rev 1 (BPRE). */
val FIELD_ITEMS_FIRERED_REV1 = FieldItemFns(
    varGet = 0x0806E57CL, varSet = 0x0806E598L, checkBagHasItem = 0x08099F54L, removeBagItem = 0x0809A1ECL,
    fieldControlsLocked = 0x0806996CL, repelVar = 0x4020, items = 0x083DB098L,
    codeCrcs = listOf(0xE6DE47BAL, 0x6E7CC2BEL, 0xD30D656EL, 0xC40BF208L, 0x9C26CCA4L),
)

/** Retail Emerald (BPEE): its functions sit where the pinned pokeemerald build has them (the QoL build's too). */
val FIELD_ITEMS_EMERALD = FieldItemFns(
    varGet = 0x0809D694L, varSet = 0x0809D6B0L, checkBagHasItem = 0x080D6724L, removeBagItem = 0x080D6AA4L,
    fieldControlsLocked = 0x08098E6CL, repelVar = 0x4021, items = 0x085839A0L,
    codeCrcs = listOf(0x7D0D07B6L, 0x519C39C0L, 0x312F6576L, 0x379EF742L, 0x2532A407L),
)

/** Whether the running ROM has [fns]' functions where they're expected. */
fun fieldItemCodeMatches(fns: FieldItemFns, readRom: (addr: Long, len: Int) -> ByteArray): Boolean =
    fns.functions.zip(fns.codeCrcs).all { (fn, crc) ->
        java.util.zip.CRC32().apply { update(readRom(fn, 32)) }.value == crc
    }

/** REPEL / SUPER REPEL / MAX REPEL: the same ids in both games. */
val REPEL_ITEMS = setOf(86, 83, 84)

enum class ItemUseResult { USED, STILL_ACTIVE, NOT_NOW, NONE_LEFT, FAILED }

/** What using [itemId] did and, for a Repel, how many steps it lasts. */
data class ItemUseOutcome(val result: ItemUseResult, val steps: Int = 0)

/**
 * Uses [itemId] on the field: [call] runs a game function and returns its r0 (or -1),
 * [readRom] reads ROM bytes, [busy] is whether a battle or a menu screen is up. Emu thread.
 */
fun useFieldItem(
    fns: FieldItemFns,
    itemId: Int,
    busy: Boolean,
    call: (fn: Long, a0: Int, a1: Int) -> Long,
    readRom: (addr: Long, len: Int) -> ByteArray,
): ItemUseOutcome {
    if (itemId !in REPEL_ITEMS || !fieldItemCodeMatches(fns, readRom)) return ItemUseOutcome(ItemUseResult.FAILED)
    if (busy || call(fns.fieldControlsLocked, 0, 0) != 0L) return ItemUseOutcome(ItemUseResult.NOT_NOW)
    if (call(fns.varGet, fns.repelVar, 0) != 0L) return ItemUseOutcome(ItemUseResult.STILL_ACTIVE)
    if (call(fns.checkBagHasItem, itemId, 1) != 1L) return ItemUseOutcome(ItemUseResult.NONE_LEFT)
    val steps = readRom(fns.items + itemId * 44L + 0x13, 1)[0].toInt() and 0xFF
    if (steps == 0) return ItemUseOutcome(ItemUseResult.FAILED)
    if (call(fns.varSet, fns.repelVar, steps) < 0 || call(fns.removeBagItem, itemId, 1) != 1L) {
        return ItemUseOutcome(ItemUseResult.FAILED)
    }
    return ItemUseOutcome(ItemUseResult.USED, steps)
}
