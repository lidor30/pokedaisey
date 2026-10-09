package com.pokedaisy.app.companion.data

import com.pokedaisy.app.MgbaCore

/**
 * Builds a [SnapshotView] from the running core's memory. In-process replacement
 * for android-companion's UDP `telemetryFlow` — no sockets, no reconnect loop.
 *
 * [sample] must run on the emulator thread (EmulatorEngine.onSample) so bus
 * reads never race a frame. Detection (ROM code/size, struct search) is cached.
 */
class TelemetrySampler {

    init {
        resetNativeBagCache()
    }

    private var kind: GameKind? = null
    private var isEmeraldStruct = false
    private var structAddr: Long = -1
    private var nativeCfg: NativeConfig? = null
    // A Game Boy game's (Gen 1) addresses instead of [nativeCfg].
    private var gen1Cfg: Gen1Config? = null
    private var lastError: String? = null
    private var magicProbes = 0
    // Set instead of nativeCfg for a large BPRE ROM that isn't recognized as
    // Unbound (see detect()) - a different hack we have no addresses for.
    private var unsupportedHackLabel: String? = null
    // detect() re-runs while the QOLT probe is still undecided - don't
    // re-hash a 16 MB ROM on every retry.
    private var smallBpeeHashChecked = false

    /**
     * The QoL struct doesn't carry experience or IVs / EVs, but the party's own
     * RAM struct does (encrypted substructs). The QoL ROMs keep gPlayerParty at
     * the vanilla address (checked against FireRed/Emerald captures) - still, a
     * rebuild can move any global, so a slot only takes the RAM values when
     * that slot decodes (checksum) to the same species and level as the struct.
     * In battle, gEnemyParty (at retail's address too) gives the foes' IVs:
     * kept only while every foe it decodes is one of the struct's battlers.
     */
    private fun withPartyExp(reader: MemoryReader, t: Telemetry): Telemetry {
        val (partyAddr, enemyAddr) = when (kind) {
            GameKind.FIRERED -> NATIVE_FIRERED_REV1.playerParty to NATIVE_FIRERED_REV1.enemyParty
            GameKind.EMERALD -> NATIVE_EMERALD.playerParty to NATIVE_EMERALD_RETAIL.enemyParty
            else -> return t
        }
        val n = t.partyCount.coerceAtMost(PARTY_SIZE)
        if (n == 0) return t
        val raw = runCatching { reader.readCoreMemory(partyAddr, n * MON_STRUCT_SIZE) }.getOrNull() ?: return t
        val party = t.party.mapIndexed { i, mon ->
            val ram = if (i < n) decodePartyMon(raw, i * MON_STRUCT_SIZE) else null
            if (ram?.exp != null && ram.species == mon.species && ram.level == mon.level) {
                mon.copy(exp = ram.exp, personality = ram.personality, stats = ram.stats)
            } else mon
        }
        val foes = if (t.inBattle) runCatching {
            val e = reader.readCoreMemory(enemyAddr, PARTY_SIZE * MON_STRUCT_SIZE)
            (0 until PARTY_SIZE).mapNotNull { decodePartyMon(e, it * MON_STRUCT_SIZE)?.takeIf { m -> m.exp != null } }
        }.getOrNull().orEmpty() else emptyList()
        val battlers = listOf(t.battleMons[BATTLE_POS_OPPONENT_LEFT], t.battleMons[BATTLE_POS_OPPONENT_RIGHT])
            .filter { it.species != 0 }
        val foesOk = battlers.isNotEmpty() && battlers.all { b -> foes.any { it.species == b.species && it.level == b.level } }
        return t.copy(party = party, battleFoes = if (foesOk) foes else emptyList())
    }

    /** Money isn't in the QoL struct either - see [findStructMoney]. */
    private fun withMoney(reader: MemoryReader, t: Telemetry): Telemetry {
        moneyCfg?.let { return t.copy(money = readMoney(reader, it)) }
        val (cfg, money) = findStructMoney(reader, kind, t.mapGroup, t.mapNum) ?: return t
        moneyCfg = cfg
        return t.copy(money = money)
    }
    private var moneyCfg: NativeConfig? = null

    /**
     * The map's size / type and the player's gender, for the region map's
     * player icon - not in the QoL struct either. gMapHeader sits at the
     * retail address in both QoL builds; it's trusted only while it names
     * the struct's own map section. The gender comes from the save blocks
     * [withMoney] found.
     */
    private fun withMapExtras(reader: MemoryReader, t: Telemetry): Telemetry {
        val header = when (kind) {
            GameKind.FIRERED -> NATIVE_FIRERED_REV1.mapHeader
            GameKind.EMERALD -> NATIVE_EMERALD.mapHeader
            else -> return t
        }
        val mapsec = runCatching { reader.readCoreMemory(header + 0x14, 1)[0].toInt() and 0xFF }.getOrNull()
        val shaped = if (mapsec == t.regionMapSectionId) t.withMapShape(readMapShape(reader, header)) else t
        return moneyCfg?.let { shaped.copy(playerGender = readPlayerGender(reader, it), trainerCard = readTrainerCard(reader, it)) } ?: shaped
    }

    /** The latest snapshot, tagged with the game it came from (null while still detecting). */
    fun sample(reader: MemoryReader): SnapshotView {
        val s = sampleUntagged(reader)
        // An unsupported ROM has no game look to borrow - the app's own backdrop.
        return s.copy(game = if (s.unsupported) null else kind ?: if (s.connected) activeGame else null)
    }

    private fun sampleUntagged(reader: MemoryReader): SnapshotView {
        try {
            if (kind == null) detect(reader)
            activeGame = kind ?: GameKind.FIRERED
            soulGoldV12 = nativeCfg === NATIVE_SOULGOLD_V1_2 || nativeCfg === NATIVE_SOULGOLD_V1_2B
            amethystV141 = nativeCfg === NATIVE_AMETHYST_V1_4_1
            romLanguage = nativeCfg?.language ?: 'E'
            romGameCode = nativeCfg?.gameCode.orEmpty()

            val telemetry = when (kind) {
                GameKind.UNBOUND -> readNativeTelemetry(reader, NATIVE_UNBOUND_WITH_DEX)
                GameKind.YELLOW -> readGen1Telemetry(reader, gen1Cfg ?: GEN1_YELLOW)
                else -> {
                    unsupportedHackLabel?.let { return SnapshotView(connected = false, error = it, unsupported = true) }
                    val cfg = nativeCfg
                    if (cfg != null) {
                        readNativeTelemetry(reader, cfg)
                    } else {
                        if (structAddr < 0 || !magicOk(reader, structAddr)) {
                            structAddr = MgbaCore.pkFindMagic(QOLT_MAGIC)
                        }
                        if (structAddr < 0) {
                            return SnapshotView(
                                connected = false,
                                error = if (isEmeraldStruct || kind == GameKind.FIRERED)
                                    "waiting for gQolTelemetry (QoL ROM) — vanilla ${romLabel()} native read not wired yet"
                                else "no telemetry source",
                            )
                        }
                        withMapExtras(reader, withMoney(reader, withPartyExp(reader, decodeTelemetry(reader.readCoreMemory(structAddr, TELEMETRY_SIZE)))))
                    }
                }
            }
            lastError = null
            return buildSnapshotView(telemetry)
        } catch (e: TelemetryDecodeException) {
            lastError = e.message
            return SnapshotView(connected = false, error = e.message)
        } catch (e: Exception) {
            return SnapshotView(connected = false, error = e.message ?: e.toString())
        }
    }

    private fun detect(reader: MemoryReader) {
        if (runCatching { MgbaCore.pkPlatform() }.getOrDefault(0) == 1) return detectGameBoy()
        val code = runCatching { MgbaCore.pkRomCode() }.getOrNull().orEmpty()
        val size = runCatching { MgbaCore.pkRomSize() }.getOrDefault(0L)

        // LeafGreen / Ruby / Sapphire / the other Emeralds: retail only (no
        // QoL build to probe for), read natively straight away. LeafGreen looks
        // like FireRed, the rest like Emerald (same species / item / map-section ids).
        if (code in OTHER_RETAIL_CODES) {
            val rev = runCatching { reader.readCoreMemory(0x080000BCL, 1)[0].toInt() and 0xFF }.getOrDefault(-1)
            val cfg = otherRetailConfig(code, rev)
            kind = if (code.startsWith("BPR") || code.startsWith("BPG")) GameKind.FIRERED else GameKind.EMERALD
            when {
                size > 0x1000000L -> unsupportedHackLabel =
                    "unrecognized $code-based ROM hack (${size / (1024 * 1024)} MB) - no known RAM addresses for this build"
                cfg == null -> unsupportedHackLabel = "${gameLabel(code)} revision $rev isn't supported yet"
                else -> nativeCfg = withCheckedTables(reader, cfg)
            }
            return
        }

        // Not FireRed/Emerald-based at all (another GBA game): nothing here
        // knows its RAM - say so rather than read it as FireRed.
        // [CompanionSupport] makes the same call from the file at import.
        if (code.length == 4 && code != "BPRE" && code != "BPEE") {
            kind = GameKind.FIRERED
            unsupportedHackLabel = "game code $code isn't a supported Pokémon game"
            return
        }

        // A big (>16 MB) BPRE ROM isn't unique to Unbound - other FireRed-based
        // hacks (Gaia, Radical Red, ...) share the same game code and also
        // expand past the vanilla 16 MB size for their added content. Size and
        // code alone can't tell them apart, and assuming Unbound would apply
        // its (wrong) RAM addresses to a different hack's memory layout,
        // producing silently wrong/garbage telemetry instead of an honest
        // "not supported" - so confirm with an exact whole-ROM hash instead of
        // guessing. One-time cost per launch (a few hundred ms at most to read
        // and hash up to 32 MB), not worth caching more cleverly than "only
        // runs once, since detect() itself is only called while kind == null".
        if (code == "BPRE" && size > 0x1000000L) {
            // Chunked, not one big readCoreMemory(size) call: the native
            // pkReadBytes bridge caps a single read at 16 MB (len > 1<<24 ->
            // null), so a whole-ROM read here would silently fail for any
            // ROM past that (i.e. exactly the large hacks this check exists
            // for) and the hash would always come back null.
            val hash = runCatching { sha1HexChunked(reader, 0x08000000L, size) }.getOrNull()
            when (hash) {
                UNBOUND_V2_1_1_1_SHA1 -> kind = GameKind.UNBOUND
                GAIA_V3_2_SHA1 -> { kind = GameKind.GAIA; nativeCfg = NATIVE_GAIA_V3_2 }
                RADICAL_RED_V4_1_SHA1 -> { kind = GameKind.RADICAL_RED; nativeCfg = NATIVE_RADICAL_RED_V4_1 }
                ODYSSEY_V4_1_1_SHA1 -> { kind = GameKind.ODYSSEY; nativeCfg = NATIVE_ODYSSEY }
                AMETHYST_V1_3_0_SHA1 -> { kind = GameKind.AMETHYST; nativeCfg = NATIVE_AMETHYST }
                AMETHYST_V1_4_1_SHA1 -> { kind = GameKind.AMETHYST; nativeCfg = NATIVE_AMETHYST_V1_4_1 }
                CELIA_V1_1_4_SHA1 -> { kind = GameKind.CELIA; nativeCfg = NATIVE_CELIA }
                else -> {
                    kind = GameKind.FIRERED
                    unsupportedHackLabel = "unrecognized FireRed-based ROM hack " +
                        "(BPRE, ${size / (1024 * 1024)} MB, sha1 ${hash?.take(12) ?: "unknown"}…) " +
                        "- no known RAM addresses for this build"
                    // Full hash, not just the 12-char prefix in the UI-facing
                    // message above - needed to add a new *_SHA1 constant.
                    // This is the LIVE-BUS hash (see RADICAL_RED_V4_1_SHA1's
                    // comment on why it can differ from a plain `shasum` on
                    // the file - RTC/GPIO registers mapped into the ROM's
                    // address space), so don't substitute a file-hash here.
                    android.util.Log.i("pokedaisy", "unrecognized BPRE hack: full sha1=$hash size=$size")
                }
            }
            return
        }

        // Same idea for Emerald-based hacks (BPEE) - see the BPRE block above
        // for why a hash, not a guess. Without this, ANY large BPEE ROM fell
        // through to the vanilla-Emerald native path below unconditionally
        // (no struct to probe for, no disambiguation at all) - silently wrong
        // addresses for a real hack, exactly the failure mode the BPRE check
        // was already written to avoid.
        if (code == "BPEE" && size > 0x1000000L) {
            val hash = runCatching { sha1HexChunked(reader, 0x08000000L, size) }.getOrNull()
            when (hash) {
                HEART_AND_SOUL_V2_0_6_SHA1 -> { kind = GameKind.HEART_AND_SOUL; nativeCfg = NATIVE_HEART_AND_SOUL }
                LAZARUS_V2_0_SHA1 -> { kind = GameKind.LAZARUS; nativeCfg = NATIVE_LAZARUS }
                ROWE_V2_1_9_1_SHA1 -> { kind = GameKind.ROWE; nativeCfg = NATIVE_ROWE }
                EMERALD_ROGUE_V2_2_1_EX_SHA1 -> { kind = GameKind.EMERALD_ROGUE; nativeCfg = NATIVE_EMERALD_ROGUE }
                TMT2_V1_5_2_SHA1 -> { kind = GameKind.TMT2; nativeCfg = NATIVE_TMT2 }
                SOULGOLD_V1_1_4_SHA1 -> { kind = GameKind.SOULGOLD; nativeCfg = NATIVE_SOULGOLD }
                SOULGOLD_V1_2_SHA1 -> { kind = GameKind.SOULGOLD; nativeCfg = NATIVE_SOULGOLD_V1_2 }
                SOULGOLD_V1_2B_SHA1 -> { kind = GameKind.SOULGOLD; nativeCfg = NATIVE_SOULGOLD_V1_2B }
                GLAZED_V9_2_0_SHA1 -> { kind = GameKind.GLAZED; nativeCfg = NATIVE_GLAZED }
                IMPERIUM_V1_3_1_SHA1 -> { kind = GameKind.IMPERIUM; nativeCfg = NATIVE_IMPERIUM }
                QUETZAL_V9_0_ALPHA_SHA1 -> { kind = GameKind.QUETZAL; nativeCfg = NATIVE_QUETZAL }
                else -> {
                    kind = GameKind.EMERALD
                    unsupportedHackLabel = "unrecognized Emerald-based ROM hack " +
                        "(BPEE, ${size / (1024 * 1024)} MB, sha1 ${hash?.take(12) ?: "unknown"}…) " +
                        "- no known RAM addresses for this build"
                    android.util.Log.i("pokedaisy", "unrecognized BPEE hack: full sha1=$hash size=$size")
                }
            }
            return
        }

        // Not every BPEE hack expands past 16 MB (Emerald Seaglass is exactly
        // 16 MB, same as retail) - hash those too, once, before the QoL/
        // vanilla probe below. An unknown hash here is NOT an unsupported
        // hack: retail Emerald and emerald-qol are 16 MB as well, so fall
        // through unchanged.
        if (code == "BPEE" && !smallBpeeHashChecked) {
            smallBpeeHashChecked = true
            val hash = runCatching { sha1HexChunked(reader, 0x08000000L, size) }.getOrNull()
            if (hash == EMERALD_SEAGLASS_V3_0_SHA1) {
                kind = GameKind.EMERALD_SEAGLASS
                nativeCfg = NATIVE_EMERALD_SEAGLASS
                return
            }
        }

        // FireRed / Emerald: the QoL struct's "QOLT" magic isn't written until
        // ~1 s of frames have run, so a -1 here early doesn't mean "no struct".
        // Probe a few times before falling back to native-RAM reads.
        val emerald = code == "BPEE"
        val addr = MgbaCore.pkFindMagic(QOLT_MAGIC)
        if (addr >= 0) {
            kind = if (emerald) GameKind.EMERALD else GameKind.FIRERED
            isEmeraldStruct = emerald
            structAddr = addr
            return
        }
        if (++magicProbes < 6) return   // stay undecided; retry next tick

        // Gave up on the struct — read native RAM.
        isEmeraldStruct = emerald
        kind = if (emerald) GameKind.EMERALD else GameKind.FIRERED
        val rev = runCatching { reader.readCoreMemory(0x080000BCL, 1)[0].toInt() and 0xFF }.getOrDefault(1)
        val cfg = when {
            emerald -> NATIVE_EMERALD_RETAIL
            rev >= 1 -> NATIVE_FIRERED_REV1
            // NATIVE_FIRERED_REV0 is the hacks' base too: only retail gets the card.
            else -> NATIVE_FIRERED_REV0.copy(trainerCard = TRAINER_CARD_FIRERED)
        }
        nativeCfg = withCheckedTables(reader, cfg)
    }

    /** An unrecognised same-size hack lands on the retail configs too: no DEX
     * tab, and no HERE / NEXT BOSS guide pages, unless the retail tables are
     * really where the config expects them. */
    private fun withCheckedTables(reader: MemoryReader, cfg: NativeConfig): NativeConfig {
        val checked = cfg.pokedex?.takeIf { !pokedexMatchesRom(reader, it) }?.let { cfg.copy(pokedex = null) } ?: cfg
        return checked.guideTables?.takeIf { !guideTablesMatchRom(reader, it) }
            ?.let { checked.copy(guideTables = null, enemyParty = 0) } ?: checked
    }

    private fun magicOk(reader: MemoryReader, addr: Long): Boolean = runCatching {
        String(reader.readCoreMemory(addr, 4), Charsets.US_ASCII) == "QOLT"
    }.getOrDefault(false)

    /**
     * Fast, 2-byte peek at battleActiveBattler/battleInputState — unlike
     * [sample], not throttled to ~1 Hz, for driving the bottom-screen touch
     * battle control (see PLAN.md Phase 5). Returns null before
     * [sample] has resolved which game/path is active, or on a pre-v3 ROM
     * (structSize too small to have these fields at all).
     */
    /** The detected game's verified gMain and its inBattle offset (its native config), or null
     * (not detected yet, or the QoL struct path) - for SMART FF's menu watch ([FfMenuWatch]). */
    val knownGMain: Pair<Long, Long>?
        get() = (if (kind == GameKind.UNBOUND) NATIVE_UNBOUND else nativeCfg)?.let { it.gMain to it.inBattleOff }

    /** A Game Boy / Color cart, by the whole ROM's SHA1 (read from the cart: it's bank-switched). */
    private fun detectGameBoy() {
        val size = runCatching { MgbaCore.pkRomSize() }.getOrDefault(0L)
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        var off = 0L
        while (off < size) {
            val len = minOf(1L shl 20, size - off).toInt()
            digest.update(MgbaCore.pkRomRead(off, len) ?: break)
            off += len
        }
        val hash = if (off == size && size > 0) digest.digest().joinToString("") { "%02x".format(it) } else null
        when {
            hash == YELLOW_SHA1 && GAME_BOY_SUPPORT -> { kind = GameKind.YELLOW; gen1Cfg = GEN1_YELLOW }
            else -> {
                kind = GameKind.FIRERED
                unsupportedHackLabel = "this Game Boy game isn't supported yet (sha1 ${hash?.take(12) ?: "unknown"}…)"
            }
        }
    }

    /** The detected game's gPartyMenu + gPlayerParty when its config has them (SoulGold), or null. */
    val knownPartyMenu: Pair<Long, Long>?
        get() = nativeCfg?.takeIf { it.partyMenu != 0L }?.let { it.partyMenu to it.playerParty }

    /** The open battle menu's cursor from the last fast sample, for a Gen 1 game ([readGen1BattleInput]); -1 otherwise. */
    @Volatile var battleMenuCursor = -1
        private set

    fun sampleBattleInputFast(reader: MemoryReader): Pair<Int, Int>? {
        if (kind == GameKind.YELLOW) {
            val (state, cursor) = runCatching { readGen1BattleInput(reader, gen1Cfg ?: GEN1_YELLOW) }.getOrNull() ?: return null
            battleMenuCursor = cursor
            return 0 to state
        }
        // Unbound's `kind` is decided directly in detect() without ever
        // populating nativeCfg (see the GameKind.UNBOUND branch there) -
        // sample() has its own matching special case for the same reason.
        if (kind == GameKind.UNBOUND) return readNativeBattleInputFast(reader, NATIVE_UNBOUND)
        val cfg = nativeCfg
        if (cfg != null) return readNativeBattleInputFast(reader, cfg) // Tier B
        val addr = structAddr
        if (addr < 0) return null
        return runCatching {
            val raw = reader.readCoreMemory(addr + BATTLE_INPUT_STATE_OFFSET, 2)
            (raw[0].toInt() and 0xFF) to (raw[1].toInt() and 0xFF)
        }.getOrNull()
    }

    private fun romLabel() = if (isEmeraldStruct) "Emerald" else "FireRed"

    companion object {
        private val QOLT_MAGIC = "QOLT".toByteArray(Charsets.US_ASCII)
        // Fixed byte offset of battleActiveBattler within QolTelemetryData
        // (right after the v2 icon-table block) — stable across future
        // struct versions since new fields are always appended at the end.
        private const val BATTLE_INPUT_STATE_OFFSET = 460L
        // Pokémon Unbound v2.1.1.1 - the only version this project's Unbound
        // support (native-RAM addresses, gen3mon decode, icon fetch, etc.) has
        // ever been built or verified against. A different Unbound version
        // would need re-verifying the same way, same as any other hack.
        //
        // Equals a plain `shasum` on the file (b4776b82a4c7915d0fadeaa27e01
        // 3523f99dfd94, also what CLAUDE.md's project notes pin) now that
        // sha1HexChunked masks the RTC/GPIO hole (see its own comment) -
        // wasn't always true, see git history if this needs re-deriving on an
        // older checkout.
        /** Retail games read natively with no QoL build: LeafGreen, Ruby, Sapphire. */
        val OTHER_RETAIL_CODES = setOf("BPGE", "AXVE", "AXPE") + EMERALD_LOCALIZED_CODES +
            RETAIL_PORTS.keys.map { it.dropLast(1) }

        /** The config for [code] at header revision [rev], or null if that revision isn't mapped. */
        fun otherRetailConfig(code: String, rev: Int): NativeConfig? = RETAIL_PORTS["$code$rev"]?.invoke() ?: when (code) {
            "BPGE" -> when (rev) { 0 -> NATIVE_LEAFGREEN_REV0; 1 -> NATIVE_LEAFGREEN_REV1; else -> null }
            // Revs 1 and 2 share every address (both built from pokeruby); rev 0 wasn't checked.
            "AXVE" -> if (rev == 1 || rev == 2) NATIVE_RUBY else null
            "AXPE" -> if (rev == 1 || rev == 2) NATIVE_SAPPHIRE else null
            // Each European Emerald had one release (rev 0).
            "BPES" -> if (rev == 0) NATIVE_EMERALD_ES else null
            "BPED" -> if (rev == 0) NATIVE_EMERALD_DE else null
            "BPEF" -> if (rev == 0) NATIVE_EMERALD_FR else null
            "BPEI" -> if (rev == 0) NATIVE_EMERALD_IT else null
            "BPEJ" -> if (rev == 0) NATIVE_EMERALD_JA else null
            else -> null
        }

        fun gameLabel(code: String) = when (code) {
            "BPGE" -> "LeafGreen"
            "AXVE" -> "Ruby"
            "AXPE" -> "Sapphire"
            "BPES", "BPED", "BPEF", "BPEI", "BPEJ" -> "Emerald (${code.last()})"
            else -> RETAIL_PORT_CODE_TITLES[code] ?: code
        }

        const val UNBOUND_V2_1_1_1_SHA1 = "b4776b82a4c7915d0fadeaa27e013523f99dfd94"
        // Pokémon Gaia v3.2 - see NATIVE_GAIA_V3_2 in NativeReader.kt for the
        // verification this address reuse is based on.
        const val GAIA_V3_2_SHA1 = "d5b1e77975fcda831e0e9a7b527906bf3f40ecd0"
        // Pokémon Radical Red v4.1 - equals a plain `shasum` on the file now
        // that sha1HexChunked masks the RTC/GPIO hole (see its own comment) -
        // this hack maps a real-time-clock feature into the classic
        // 0x080000C4-C9 GPIO hole a few official carts and several modern
        // hacks use, and reading through it unmasked returns live,
        // NOT-necessarily-stable-across-a-session hardware state instead of
        // static ROM data (confirmed 2026-09-22 via Heart and Soul: matched a
        // file hash on a truly fresh boot, came out completely different
        // after resuming a session with real elapsed play time).
        const val RADICAL_RED_V4_1_SHA1 = "964f951a0fdaf209e4ea1344883ef0d557bb3a80"
        // Pokemon Odyssey (English) v4.1.1 - confirmed live on-device 2026-09-22.
        const val ODYSSEY_V4_1_1_SHA1 = "8745ddbdbfadf6abaf66de4e9055923b62eb4668"
        // Emerald-based (BPEE) hacks - confirmed live on-device 2026-09-22 via
        // the full-hash debug log in the BPEE `else` branch above.
        const val HEART_AND_SOUL_V2_0_6_SHA1 = "79ee6df0869c1773c8c6a5f764afc1f8d833d8bb"
        const val LAZARUS_V2_0_SHA1 = "7dcdc7e280bc4631487e13dd37e6e0cea04adea6"
        const val ROWE_V2_1_9_1_SHA1 = "81bd0f4bfa1c04ab2c6faab1bddd10e8a390ea77"
        // Emerald Rogue v2.2.1-EX - confirmed live on-device 2026-09-22; equals
        // a plain `shasum` on the file (7600af1fe08444c850c3c1227fd7dfd81336ae8e).
        const val EMERALD_ROGUE_V2_2_1_EX_SHA1 = "7600af1fe08444c850c3c1227fd7dfd81336ae8e"
        // Pokemon Amethyst v1.3.0 - a BPRE (FireRed-based) hack, unlike the
        // other two above - confirmed live on-device 2026-09-22; equals a
        // plain `shasum` on the file (00e70c0384a5f1698588034201fd5b849d3542e2).
        const val AMETHYST_V1_3_0_SHA1 = "00e70c0384a5f1698588034201fd5b849d3542e2"
        // Pokemon Amethyst v1.4.1 - host-side masked hash (GPIO bytes zero, so a plain `shasum`).
        const val AMETHYST_V1_4_1_SHA1 = "91291aade04b4b111cd03ae7b6e2ff460e1edd8a"
        // Emerald Seaglass v3.0 - GPIO-hole-masked hash computed host-side from
        // the file (its 0x080000C4-C9 bytes are zero, so it also equals a plain
        // `shasum`). NOT yet confirmed via the on-device live-bus log.
        const val EMERALD_SEAGLASS_V3_0_SHA1 = "b9f4d332d30fc88c379f9e037f9eae3b2755ead4"
        // Pokemon Celia's Stupid Romhack v1.1.4 - same host-side masked hash as
        // Seaglass (GPIO bytes are zero, so it equals a plain `shasum`).
        const val CELIA_V1_1_4_SHA1 = "3cbd5a2e72ce60cfae1e0c28d83e01f855c51537"
        // Pokemon Too Many Types 2 v1.5.2 - host-side masked hash (GPIO bytes zero).
        const val TMT2_V1_5_2_SHA1 = "3fa8e61ec1727cc51540c54677623f736a446e1b"
        // Pokémon SoulGold v1.1.4 (BPEE, 32 MB) - host-side masked hash (GPIO bytes zero).
        const val SOULGOLD_V1_1_4_SHA1 = "ea5d369cc8a31cbf1cfacb7c9470ea670f08957b"
        // Pokémon SoulGold v1.2 - host-side masked hash (GPIO bytes zero).
        const val SOULGOLD_V1_2_SHA1 = "805d880ee229fb6dc3ce03d7b03baf48f0d759d0"
        // A second build released as SoulGold v1.2 (its title screen says v1.2 too) - masked hash
        // (GPIO bytes zero).
        const val SOULGOLD_V1_2B_SHA1 = "5d6a036260fdbde96f85b5c1b92d0256d3aebafb"
        // Pokémon Glazed 9.2.0 (BPEE, 32 MB) - host-side masked hash (GPIO bytes zero).
        const val GLAZED_V9_2_0_SHA1 = "e10105d8544469c6a11ca2cf510289981df3c3b0"
        // Pokémon Emerald Imperium v1.3.1 (BPEE, 32 MB) - host-side masked hash (GPIO bytes zero).
        const val IMPERIUM_V1_3_1_SHA1 = "1d20091c4d936f5eb122db8780554dd0829ffb63"
        // Pokémon Quetzal English Alpha 9 v0 (BPEE, 32 MB) - host-side masked hash (GPIO bytes zero).
        const val QUETZAL_V9_0_ALPHA_SHA1 = "d0658315da1e8827f66f15c3d3a000fe747e163e"

        // Pokémon Yellow (USA, Europe) - the Game Boy cart (pret/pokeyellow builds it byte for byte).
        const val YELLOW_SHA1 = "cc7d03262ebfaf2f06772c1a480c7d9d5f4a38e1"

        /** The Game Boy / Color carts the companion reads ([CompanionSupport] checks imports against it). */
        val SUPPORTED_GB_SHA1S = setOf(YELLOW_SHA1)

        /**
         * Game Boy / Color (Pokémon Yellow): the library, folder scan, frontend launch and
         * archives take .gb / .gbc (RomArchive.ROM_EXTENSIONS) and a Yellow cart is read by the
         * Gen 1 reader. False turns all of it off again (then drop the manifest's .gb / .gbc
         * VIEW patterns too): a cart that arrives anyway gets the "not supported" notice.
         */
        const val GAME_BOY_SUPPORT = true

        /** Every hack detect() has RAM addresses for - the >16 MB ones it
         * hashes, plus Seaglass (16 MB). [CompanionSupport] checks imports against it. */
        val SUPPORTED_HACK_SHA1S = setOf(
            UNBOUND_V2_1_1_1_SHA1, GAIA_V3_2_SHA1, RADICAL_RED_V4_1_SHA1, ODYSSEY_V4_1_1_SHA1,
            AMETHYST_V1_3_0_SHA1, AMETHYST_V1_4_1_SHA1, CELIA_V1_1_4_SHA1, HEART_AND_SOUL_V2_0_6_SHA1, LAZARUS_V2_0_SHA1,
            ROWE_V2_1_9_1_SHA1, EMERALD_ROGUE_V2_2_1_EX_SHA1, TMT2_V1_5_2_SHA1, EMERALD_SEAGLASS_V3_0_SHA1,
            SOULGOLD_V1_1_4_SHA1, SOULGOLD_V1_2_SHA1, SOULGOLD_V1_2B_SHA1, GLAZED_V9_2_0_SHA1, IMPERIUM_V1_3_1_SHA1,
            QUETZAL_V9_0_ALPHA_SHA1,
        )
    }
}

// The classic GBA cart RTC/rumble GPIO hole: 3 memory-mapped u16 registers
// (data/direction/control) a handful of official carts and several modern
// hacks map into what's otherwise plain ROM address space. Reading through
// it returns LIVE hardware state, not static ROM bytes - and unlike what the
// RADICAL_RED_V4_1_SHA1/UNBOUND_V2_1_1_1_SHA1 comments originally assumed,
// that state is NOT necessarily stable across a session (confirmed
// 2026-09-22: Heart and Soul's live-bus hash matched a plain file hash on a
// truly fresh boot, then came out completely different after resuming a
// session with real elapsed play time - almost certainly the RTC clock
// itself having ticked forward). Masking these 6 bytes out before hashing
// makes detection stable regardless of how long the game's been running,
// instead of only working on the very first boot.
private val GPIO_HOLE_START = 0x080000C4L
private const val GPIO_HOLE_SIZE = 6

private fun sha1HexChunked(reader: MemoryReader, baseAddr: Long, size: Long): String {
    val chunkSize = 1 shl 20 // 1 MB - comfortably under pkReadBytes' 16 MB cap
    val digest = java.security.MessageDigest.getInstance("SHA-1")
    var off = 0L
    while (off < size) {
        val len = minOf(chunkSize.toLong(), size - off).toInt()
        val chunk = reader.readCoreMemory(baseAddr + off, len)
        val holeStart = GPIO_HOLE_START - (baseAddr + off)
        if (holeStart in 0 until len) {
            val holeEnd = minOf(len.toLong(), holeStart + GPIO_HOLE_SIZE)
            for (i in holeStart until holeEnd) chunk[i.toInt()] = 0
        }
        digest.update(chunk)
        off += len
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * A QoL ROM's money, read from its save blocks, with the config that found
 * it. The FireRed QoL build moves gSaveBlock1Ptr/2Ptr (0x03005018 in dist's
 * build vs retail's 0x03005008) and a rebuild can move them again, so each
 * candidate must hold the struct's own map ([mapGroup]/[mapNum], i.e.
 * SaveBlock1.location) before its money is trusted.
 */
internal fun findStructMoney(reader: MemoryReader, game: GameKind?, mapGroup: Int, mapNum: Int): Pair<NativeConfig, Long>? {
    val candidates = when (game) {
        GameKind.FIRERED -> listOf(
            NATIVE_FIRERED_REV1.copy(saveBlock1Ptr = 0x03005018L, saveBlock2Ptr = 0x0300501CL),
            NATIVE_FIRERED_REV1,
        )
        GameKind.EMERALD -> listOf(NATIVE_EMERALD.copy(trainerCard = TRAINER_CARD_EMERALD))
        else -> return null
    }
    for (cfg in candidates) {
        val loc = runCatching {
            val sb1 = saveBlock1(reader, cfg)
            if (sb1 in 0x02000000L until 0x04000000L) reader.readCoreMemory(sb1 + cfg.saveBlock1PosOff + 4, 2) else null
        }.getOrNull() ?: continue
        if ((loc[0].toInt() and 0xFF) != mapGroup || (loc[1].toInt() and 0xFF) != mapNum) continue
        return cfg to (readMoney(reader, cfg) ?: continue)
    }
    return null
}
