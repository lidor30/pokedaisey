package com.pokedaisy.app.companion.data

/**
 * A Game Boy Pokémon game's music, for STEADY FF music (the GB twin of [FfMusicKey] /
 * [M4aSongs]): Gen 1 plays a song through its audio engine's four music channels
 * (wChannelSoundIDs CHAN1-4; CHAN5-8 are sound effects and cries), from one of
 * several audio banks (wAudioROMBank), so a song is its (bank, id) - song ids repeat
 * across banks. The render core starts one by calling the game's own PlayMusic
 * (a = id, c = bank) from a parked main loop: the VBlank handler runs the sound
 * engine every frame by itself.
 *
 * Addresses are pret/pokeyellow's symbols (its build is byte-identical to retail).
 */
class Gen1MusicTables(
    val sha1: String,
    /** PlayMusic, in bank 0. */
    val playMusic: Int,
    /** A `jr @` (18 FE) in bank 0 that nothing jumps to, for the render core's parked main loop. */
    val spin: Int,
    val audioRomBank: Long,       // wAudioROMBank
    val channelSoundIds: Long,    // wChannelSoundIDs: 8 bytes, CHAN1 first
    /** The music channels' state Gen1LoopWatch compares frame to frame: [start, start + length) ranges. */
    val channelState: List<Pair<Long, Int>>,
    /** Every song, as (bank, id): the background pass renders each once. */
    val songs: List<Pair<Int, Int>>,
)

val GEN1_MUSIC_YELLOW = Gen1MusicTables(
    sha1 = TelemetrySampler.YELLOW_SHA1,
    playMusic = 0x2211,
    spin = 0x1757,                // inside PlaceNextChar's `jp z, $18E3`: its operand's 18 FE
    audioRomBank = 0xC0EFL,
    channelSoundIds = 0xC026L,
    channelState = listOf(
        0xC006L to 8,             // wChannelCommandPointers, CHAN1-4
        0xC016L to 8,             // wChannelReturnAddresses
        0xC026L to 4,             // wChannelSoundIDs
        0xC0B6L to 4,             // wChannelNoteDelayCounters
        0xC0BEL to 4,             // wChannelLoopCounters
    ),
    // constants/music_constants.asm: (address - SFX_Headers_N) / 3 in each song's bank.
    songs = listOf(
        2 to 186, 2 to 189, 2 to 192, 2 to 195, 2 to 199, 2 to 202, 2 to 205, 2 to 208, 2 to 212, 2 to 216,
        2 to 219, 2 to 222, 2 to 225, 2 to 229, 2 to 232, 2 to 235, 2 to 239, 2 to 243, 2 to 247, 2 to 251,
        8 to 234, 8 to 237, 8 to 240, 8 to 243, 8 to 246, 8 to 249, 8 to 252,
        0x1F to 195, 0x1F to 199, 0x1F to 202, 0x1F to 205, 0x1F to 208, 0x1F to 210, 0x1F to 214, 0x1F to 217,
        0x1F to 220, 0x1F to 223, 0x1F to 227, 0x1F to 231, 0x1F to 235, 0x1F to 239, 0x1F to 242, 0x1F to 245,
        0x1F to 248, 0x1F to 251,
        0x20 to 153, 0x20 to 156, 0x20 to 159, 0x20 to 163,
    ),
)

object Gen1Music {
    private val GAMES = listOf(GEN1_MUSIC_YELLOW)

    fun forSha1(sha1: String): Gen1MusicTables? = GAMES.firstOrNull { it.sha1 == sha1 }

    /** The cache key for a song: its bank and id. */
    fun key(bank: Int, id: Int) = "gb_%02x_%02x".format(bank, id)

    /** The (bank, id) a [key] names, or null. */
    fun parse(key: String): Pair<Int, Int>? {
        val m = Regex("gb_([0-9a-f]{2})_([0-9a-f]{2})").matchEntire(key) ?: return null
        return m.groupValues[1].toInt(16) to m.groupValues[2].toInt(16)
    }

    /** What [r]'s music channels are playing, or null (silence / unreadable). */
    fun current(r: MemoryReader, t: Gen1MusicTables): String? = runCatching {
        val ids = r.readCoreMemory(t.channelSoundIds, 4)
        val id = ids.map { it.toInt() and 0xFF }.firstOrNull { it != 0 } ?: return null
        key(r.readCoreMemory(t.audioRomBank, 1)[0].toInt() and 0xFF, id)
    }.getOrNull()
}
