package com.pokedaisy.app.companion.data

import java.io.File

/**
 * BEST EFFORT for a ROM the companion doesn't know (an unrecognised hash): read the running game
 * through every config the app has for its base game and keep the one it makes sense with. Many
 * hacks keep a supported game's RAM (Orange Islands read as FireRed before it had a config of its
 * own), and a supported version re-patched onto another base ROM has a new hash but the same
 * addresses (SoulGold v1.2 builds players reported as "not supported").
 *
 * A config fits when the party it reads is real - 1 to 6 Pokémon, each decoding (encrypted boxes
 * pass their checksum) with a plausible species, level and HP; that needs the player in game with
 * a Pokémon. Its ROM-side parts are then checked one by one ([Part]): one that doesn't hold here is
 * switched off. Every part holding = the ROM is that game, a FULL match (no "not supported" any
 * more); some off = PARTIAL. The result is kept per ROM ([BestEffortStore], by SHA-1), so the
 * companion comes back on the next launch without asking.
 */
object BestEffort {
    /** A config best effort can try: [id] is what [BestEffortStore] keeps (stable, never renamed). */
    class Candidate(val id: String, val kind: GameKind, val cfg: NativeConfig, val families: Set<String>, val title: String)

    /** ROM-side pieces checked against the ROM; the RAM side (party, bag, map, battle) comes with the config. */
    enum class Part { DEX, GUIDE, ITEM_TEXT }

    class Match(val candidate: Candidate, val full: Boolean, val off: Set<Part>, val score: Int) {
        /** The config to read with: [off] parts dropped (a partial match gets no trainer card either). */
        val config: NativeConfig get() {
            if (full) return candidate.cfg
            var c = candidate.cfg
            if (Part.DEX in off) c = c.copy(pokedex = null)
            if (Part.GUIDE in off) c = c.copy(guideTables = null, enemyParty = 0)
            if (Part.ITEM_TEXT in off) c = c.copy(itemDescs = null)
            return c.copy(trainerCard = null)
        }
    }

    private val FIRERED = setOf("BPR", "BPG")
    private val EMERALD = setOf("BPE")
    private val RUBY_SAPPHIRE = setOf("AXV", "AXP")

    /** Every config, by base game - the first three letters of the header's game code. */
    val candidates: List<Candidate> by lazy {
        listOf(
            Candidate("FIRERED_REV1", GameKind.FIRERED, NATIVE_FIRERED_REV1, FIRERED, "Pokémon FireRed"),
            Candidate("FIRERED_REV0", GameKind.FIRERED, NATIVE_FIRERED_REV0, FIRERED, "Pokémon FireRed (rev 0)"),
            Candidate("LEAFGREEN_REV1", GameKind.FIRERED, NATIVE_LEAFGREEN_REV1, FIRERED, "Pokémon LeafGreen"),
            Candidate("LEAFGREEN_REV0", GameKind.FIRERED, NATIVE_LEAFGREEN_REV0, FIRERED, "Pokémon LeafGreen (rev 0)"),
            Candidate("UNBOUND", GameKind.UNBOUND, NATIVE_UNBOUND_WITH_DEX, FIRERED, "Pokémon Unbound"),
            Candidate("UNBOUND_FR", GameKind.UNBOUND, NATIVE_UNBOUND_FR, FIRERED, "Pokémon Unbound (FR)"),
            Candidate("GAIA_V3_2", GameKind.GAIA, NATIVE_GAIA_V3_2, FIRERED, "Pokémon Gaia"),
            Candidate("RADICAL_RED_V4_1", GameKind.RADICAL_RED, NATIVE_RADICAL_RED_V4_1, FIRERED, "Pokémon Radical Red"),
            Candidate("ODYSSEY", GameKind.ODYSSEY, NATIVE_ODYSSEY, FIRERED, "Pokémon Odyssey"),
            Candidate("AMETHYST", GameKind.AMETHYST, NATIVE_AMETHYST, FIRERED, "Pokémon Amethyst v1.3"),
            Candidate("AMETHYST_V1_4_1", GameKind.AMETHYST, NATIVE_AMETHYST_V1_4_1, FIRERED, "Pokémon Amethyst v1.4"),
            Candidate("CELIA", GameKind.CELIA, NATIVE_CELIA, FIRERED, "Celia's Stupid Romhack"),
            Candidate("ORANGE_ISLANDS", GameKind.ORANGE_ISLANDS, NATIVE_ORANGE_ISLANDS, FIRERED, "Pokémon Orange Islands"),
            Candidate("EMERALD", GameKind.EMERALD, NATIVE_EMERALD_RETAIL, EMERALD, "Pokémon Emerald"),
            Candidate("EMERALD_ES", GameKind.EMERALD, NATIVE_EMERALD_ES, EMERALD, "Pokémon Esmeralda"),
            Candidate("EMERALD_DE", GameKind.EMERALD, NATIVE_EMERALD_DE, EMERALD, "Pokémon Smaragd"),
            Candidate("EMERALD_FR", GameKind.EMERALD, NATIVE_EMERALD_FR, EMERALD, "Pokémon Émeraude"),
            Candidate("EMERALD_IT", GameKind.EMERALD, NATIVE_EMERALD_IT, EMERALD, "Pokémon Smeraldo"),
            Candidate("EMERALD_JA", GameKind.EMERALD, NATIVE_EMERALD_JA, EMERALD, "Pocket Monsters Emerald"),
            Candidate("HEART_AND_SOUL", GameKind.HEART_AND_SOUL, NATIVE_HEART_AND_SOUL, EMERALD, "Pokémon Heart and Soul"),
            Candidate("LAZARUS", GameKind.LAZARUS, NATIVE_LAZARUS, EMERALD, "Pokémon Lazarus"),
            Candidate("SOULGOLD", GameKind.SOULGOLD, NATIVE_SOULGOLD, EMERALD, "Pokémon SoulGold v1.1"),
            Candidate("SOULGOLD_V1_2", GameKind.SOULGOLD, NATIVE_SOULGOLD_V1_2, EMERALD, "Pokémon SoulGold v1.2"),
            Candidate("SOULGOLD_V1_2B", GameKind.SOULGOLD, NATIVE_SOULGOLD_V1_2B, EMERALD, "Pokémon SoulGold v1.2"),
            Candidate("ROWE", GameKind.ROWE, NATIVE_ROWE, EMERALD, "Pokémon R.O.W.E."),
            Candidate("EMERALD_ROGUE", GameKind.EMERALD_ROGUE, NATIVE_EMERALD_ROGUE, EMERALD, "Emerald Rogue"),
            Candidate("EMERALD_SEAGLASS", GameKind.EMERALD_SEAGLASS, NATIVE_EMERALD_SEAGLASS, EMERALD, "Emerald Seaglass"),
            Candidate("TMT2", GameKind.TMT2, NATIVE_TMT2, EMERALD, "Too Many Types 2"),
            Candidate("GLAZED", GameKind.GLAZED, NATIVE_GLAZED, EMERALD, "Pokémon Glazed"),
            Candidate("IMPERIUM", GameKind.IMPERIUM, NATIVE_IMPERIUM, EMERALD, "Emerald Imperium"),
            Candidate("QUETZAL", GameKind.QUETZAL, NATIVE_QUETZAL, EMERALD, "Pokémon Quetzal"),
            Candidate("QUETZAL_ES", GameKind.QUETZAL, NATIVE_QUETZAL_ES, EMERALD, "Pokémon Quetzal (ES)"),
            Candidate("RUBY", GameKind.EMERALD, NATIVE_RUBY, RUBY_SAPPHIRE, "Pokémon Ruby"),
            Candidate("SAPPHIRE", GameKind.EMERALD, NATIVE_SAPPHIRE, RUBY_SAPPHIRE, "Pokémon Sapphire"),
        )
    }

    fun candidate(id: String): Candidate? = candidates.firstOrNull { it.id == id }

    /** Whether best effort can try a ROM with this header game code at all (a Gen 3 Pokémon base game). */
    fun canTry(gameCode: String): Boolean = candidates.any { c -> c.families.any { gameCode.startsWith(it) } }

    /** Why [match] found nothing, for the companion's notice. */
    enum class Miss { NO_PARTY, NO_MATCH }

    /**
     * The config [reader]'s game reads best with, or why none. Reads only (party, bag, a few ROM
     * words per config), on the emulator thread like the sampler; the [activeGame] family of
     * globals the decoders consult is set per try and put back after.
     */
    fun match(reader: MemoryReader, gameCode: String): Pair<Match?, Miss?> {
        val tries = candidates.filter { c -> c.families.any { gameCode.startsWith(it) } }
        val saved = Globals.save()
        var sawParty = false
        val fits = try {
            tries.mapNotNull { c ->
                Globals.set(c)
                val r = runCatching { score(reader, c) }.getOrNull()
                if (r != null) sawParty = true
                r
            }
        } finally {
            Globals.restore(saved)
            resetNativeBagCache()
        }
        val best = fits.maxWithOrNull(compareBy<Match>({ it.full }, { it.score }, { -tries.indexOf(it.candidate) }))
        return best to (if (best != null) null else if (sawParty) Miss.NO_MATCH else Miss.NO_PARTY)
    }

    private fun score(reader: MemoryReader, c: Candidate): Match? {
        resetNativeBagCache()
        val t = readNativeTelemetry(reader, c.cfg)
        val n = t.partyCount
        if (n !in 1..PARTY_SIZE || t.party.size < n) return null
        val mons = t.party.take(n)
        if (!mons.all { it.species in 1..MAX_SPECIES && it.level in 1..100 && it.maxHp in 1..999 && it.hp in 0..it.maxHp }) return null
        var score = n * 10
        t.money?.let { if (it in 0..c.cfg.maxMoney) score += 2 }
        if (t.items.isNotEmpty() && t.items.all { it.itemId > 0 && it.quantity in 1..999 }) score += 3

        val off = mutableSetOf<Part>()
        fun part(p: Part, present: Boolean, holds: () -> Boolean) {
            if (!present) return
            if (runCatching(holds).getOrDefault(false)) score += 4 else off += p
        }
        part(Part.DEX, c.cfg.pokedex != null) { dexHolds(reader, c.cfg.pokedex!!) }
        part(Part.GUIDE, c.cfg.guideTables != null) { guideTablesMatchRom(reader, c.cfg.guideTables!!) }
        part(Part.ITEM_TEXT, c.cfg.itemDescs != null) { RomItemText.matchesRom(reader, c.cfg.itemDescs!!) }
        return Match(c, full = off.isEmpty(), off = off, score = score)
    }

    /** [pokedexMatchesRom] for the dex-entry tables; an expansion game's gSpeciesInfo: BULBASAUR's
     * national number, height and weight where [t] says they are. */
    private fun dexHolds(reader: MemoryReader, t: PokedexTables): Boolean {
        val x = t.expansion ?: return pokedexMatchesRom(reader, t)
        val e = reader.readCoreMemory(t.speciesInfo + x.stride + x.natDexOff, 6)
        return u16le(e, 0) == 1 && u16le(e, 2) == 7 && u16le(e, 4) == 69
    }

    /** Above every species id the configs know (SoulGold's egg is 1578). */
    private const val MAX_SPECIES = 2047

    /** The globals the decoders read the running game's tables by ([activeGame] and friends). */
    private object Globals {
        class Saved(val game: GameKind, val lang: Char, val code: String, val sg12: Boolean, val am141: Boolean)

        fun save() = Saved(activeGame, romLanguage, romGameCode, soulGoldV12, amethystV141)
        fun restore(s: Saved) {
            activeGame = s.game; romLanguage = s.lang; romGameCode = s.code; soulGoldV12 = s.sg12; amethystV141 = s.am141
        }
        fun set(c: Candidate) {
            activeGame = c.kind
            romLanguage = c.cfg.language
            romGameCode = c.cfg.gameCode
            soulGoldV12 = c.cfg === NATIVE_SOULGOLD_V1_2 || c.cfg === NATIVE_SOULGOLD_V1_2B
            amethystV141 = c.cfg === NATIVE_AMETHYST_V1_4_1
        }
    }
}

/**
 * A match the player may choose to share (BestEffortShare, after the notice asks): about the ROM
 * only - nothing about the player, the device or the file's name.
 */
data class BestEffortReport(
    val sha1: String,
    val size: Long,
    val gameCode: String,
    val revision: Int,
    /** [BestEffort.Candidate.id]. */
    val matchedAs: String,
    val full: Boolean,
    val off: Set<BestEffort.Part>,
)

/**
 * What best effort found for each ROM the player tried it on, by the ROM's SHA-1 (an archive: the
 * ROM inside): `<sha1>\t<candidate id>\t<FULL|PARTIAL>\t<parts off, comma-separated>` in
 * `filesDir/best-effort.tsv`. [dir] is set by the activities before anything reads it.
 */
object BestEffortStore {
    class Entry(val sha1: String, val id: String, val full: Boolean, val off: Set<BestEffort.Part>)

    @Volatile var dir: File? = null
    /** Called (any thread) after [save] / [forget] with the SHA-1 changed: the library's verdict cache drops it. */
    @Volatile var onChanged: ((String) -> Unit)? = null

    private fun file() = dir?.let { File(it, "best-effort.tsv") }

    @Synchronized
    fun all(): Map<String, Entry> = runCatching {
        file()?.takeIf { it.isFile }?.readLines().orEmpty().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 3 || BestEffort.candidate(p[1]) == null) return@mapNotNull null
            val off = p.getOrNull(3).orEmpty().split(',').mapNotNull { n -> BestEffort.Part.entries.firstOrNull { it.name == n } }.toSet()
            p[0] to Entry(p[0], p[1], p[2] == "FULL", off)
        }.toMap()
    }.getOrDefault(emptyMap())

    fun load(sha1: String?): Entry? = sha1?.let { all()[it.lowercase()] }

    @Synchronized
    fun save(sha1: String, m: BestEffort.Match) {
        write(all() + (sha1.lowercase() to Entry(sha1.lowercase(), m.candidate.id, m.full, m.off)))
        onChanged?.invoke(sha1)
    }

    @Synchronized
    fun forget(sha1: String) {
        write(all() - sha1.lowercase())
        onChanged?.invoke(sha1)
    }

    private fun write(entries: Map<String, Entry>) {
        val f = file() ?: return
        runCatching {
            val tmp = File(f.parentFile, "${f.name}.tmp")
            tmp.writeText(entries.values.joinToString("") { e ->
                "${e.sha1}\t${e.id}\t${if (e.full) "FULL" else "PARTIAL"}\t${e.off.joinToString(",") { it.name }}\n"
            })
            tmp.renameTo(f)
        }
    }

    /** [entry]'s config as a [BestEffort.Match] would read with it. */
    fun matchOf(entry: Entry): BestEffort.Match? =
        BestEffort.candidate(entry.id)?.let { BestEffort.Match(it, entry.full, entry.off, 0) }
}
