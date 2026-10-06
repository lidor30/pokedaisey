package com.pokedaisey.app.companion.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.pokedaisey.app.GbaControls
import com.pokedaisey.app.Hotkeys
import com.pokedaisey.app.companion.CompanionSettings
import com.pokedaisey.app.companion.StateSlots
import com.pokedaisey.app.companion.data.GENDER_SYMBOL_FEMALE
import com.pokedaisey.app.companion.data.GENDER_SYMBOL_MALE
import com.pokedaisey.app.companion.data.GameKind
import com.pokedaisey.app.companion.data.ItemView
import com.pokedaisey.app.companion.data.LocationView
import com.pokedaisey.app.companion.data.MonView
import com.pokedaisey.app.companion.data.MoveView
import com.pokedaisey.app.companion.data.POCKET_ITEMS
import com.pokedaisey.app.companion.data.POCKET_KEY_ITEMS
import com.pokedaisey.app.companion.data.POCKET_POKE_BALLS
import com.pokedaisey.app.companion.data.SnapshotView
import com.pokedaisey.app.companion.data.TypeMatchup
import com.pokedaisey.app.companion.data.activeGame
import com.pokedaisey.app.companion.data.DecompIconSource
import com.pokedaisey.app.companion.data.EvolutionSource
import com.pokedaisey.app.companion.data.GUIDE_TABLES_FIRERED_REV1
import com.pokedaisey.app.companion.data.GuideRomSource
import com.pokedaisey.app.companion.data.SaveProgress
import com.pokedaisey.app.companion.data.spriteAsset
import com.pokedaisey.app.companion.data.withMovesVs
import com.pokedaisey.app.companion.data.NATIVE_EMERALD
import com.pokedaisey.app.companion.data.NATIVE_FIRERED_REV1
import com.pokedaisey.app.companion.data.POKEDEX_EMERALD
import com.pokedaisey.app.companion.data.POKEDEX_UNBOUND
import com.pokedaisey.app.companion.data.UnboundIconSource
import com.pokedaisey.app.companion.data.POKEDEX_FIRERED_REV1
import com.pokedaisey.app.companion.data.PokedexSource
import com.pokedaisey.app.companion.data.NATIVE_UNBOUND_WITH_DEX
import com.pokedaisey.app.companion.data.guideId
import com.pokedaisey.app.companion.data.gameGuide
import com.pokedaisey.app.companion.data.NATIVE_SAPPHIRE
import com.pokedaisey.app.companion.data.readPokedexState
import com.pokedaisey.app.companion.data.NATIVE_TMT2
import com.pokedaisey.app.companion.data.NATIVE_EMERALD_SEAGLASS
import com.pokedaisey.app.companion.data.NATIVE_LAZARUS
import com.pokedaisey.app.companion.data.NATIVE_HEART_AND_SOUL
import com.pokedaisey.app.companion.data.NATIVE_CELIA
import com.pokedaisey.app.companion.data.NATIVE_GAIA_V3_2
import com.pokedaisey.app.companion.data.NATIVE_AMETHYST
import com.pokedaisey.app.companion.data.NATIVE_RADICAL_RED_V4_1
import com.pokedaisey.app.companion.data.NATIVE_ODYSSEY
import com.pokedaisey.app.companion.data.PokedexState
import com.pokedaisey.app.companion.data.RomArt
import com.pokedaisey.app.companion.data.CardStyle
import com.pokedaisey.app.companion.data.TrainerCardArt
import com.pokedaisey.app.companion.data.readTrainerCard
import com.pokedaisey.app.companion.data.RomFileReader
import com.pokedaisey.app.companion.data.FixtureMemoryReader
import com.pokedaisey.app.companion.data.NativeConfig
import com.pokedaisey.app.companion.data.NATIVE_LEAFGREEN_REV1
import com.pokedaisey.app.companion.data.NATIVE_RUBY
import com.pokedaisey.app.companion.data.RETAIL_ROM_DIR
import com.pokedaisey.app.companion.data.buildSnapshotView
import com.pokedaisey.app.companion.data.readNativeTelemetry
import com.pokedaisey.app.companion.data.resetNativeBagCache
import com.pokedaisey.app.companion.ui.theme.QolTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Paparazzi screenshots of the bottom-screen companion, one per tab, plus the
 * shared OPTION-screen pieces. Not a regression gate (the golden PNGs aren't
 * committed) - it's how to *look* at a UI change without a device:
 *
 *     ./gradlew :app:recordPaparazziDebug --tests '*ScreenshotTest'
 *
 * then open app/src/test/snapshots/images/. Sized like the AYN Thor's bottom
 * screen (1240x1080, landscape). See CLAUDE.md's "UI work" section.
 */
class CompanionScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1240, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE,
        ),
        theme = "android:Theme.Material.NoActionBar",
    )

    @Before
    fun game() {
        activeGame = GameKind.FIRERED
        // The party menu art, backdrops and region maps come from the ROM too (RomArt).
        val art = java.io.File("build/rom-art-paparazzi").also { RomArt.dirOverride = it }
        for (path in listOf(RomFileReader.FIRERED_REV1_PATH, RomFileReader.EMERALD_PATH)) {
            java.io.File(path).takeIf { it.isFile }?.let { RomArt.extractTo(it.readBytes(), art) }
        }
        // The CARD tab draws from these (normally loaded in the background).
        TrainerCardArt.load(art, CardStyle.KANTO)
        TrainerCardArt.load(art, CardStyle.HOENN)
        // The DEX tab's sprites and entries (and every mon icon) come from the
        // ROM: read the retail one when it's on this machine, else they stay blank.
        RomFileReader.load(RomFileReader.FIRERED_REV1_PATH)?.let { rom ->
            PokedexSource.reader = rom
            DecompIconSource.reader = rom
            DecompIconSource.tables = NATIVE_FIRERED_REV1.iconTables
            // Paparazzi draws one frame: load what the shots show up front
            // (the screens start from these caches instead of loading async).
            val t = POKEDEX_FIRERED_REV1
            for (n in 1..12) {
                val species = PokedexSource.speciesFor(t, n)
                PokedexSource.entry(t, n); PokedexSource.frontSprite(t, species); PokedexSource.footprint(t, species)
                DecompIconSource.get(species)
            }
            SampleCompanion.snapshot.party.forEach { DecompIconSource.get(it.species) }
            EvolutionSource.table(t)
            val g = GUIDE_TABLES_FIRERED_REV1
            GuideRomSource.encounters(g, 3, 43)
            PokedexSource.nationalOf(t, 1)
            (350..420).forEach { GuideRomSource.party(g, it) }
            GuideRomSource.party(g, 438)
        }
    }

    private fun tab(name: String) = paparazzi.snapshot {
        CompanionScreen(SampleCompanion.snapshot, SampleCompanion.Slots, SampleCompanion.Settings(), initialTab = name)
    }

    @Test fun party() = tab("PARTY")

    /** The TRAINER CARD, front and back, from the retail ROM and the FireRed fixture save. */
    @Test fun card() = cardShot(back = false)

    @Test fun cardBack() = cardShot(back = true)

    private fun cardShot(back: Boolean) = paparazzi.snapshot {
        val card = readTrainerCard(FixtureMemoryReader.load("firered_vanilla"), NATIVE_FIRERED_REV1)
        CompanionScreen(
            SampleCompanion.snapshot.copy(trainerCard = card), SampleCompanion.Slots, SampleCompanion.Settings(),
            initialTab = "CARD", initialCardBack = back,
        )
    }

    @Test
    fun partyDetail() = paparazzi.snapshot {
        CompanionScreen(SampleCompanion.snapshot, SampleCompanion.Slots, SampleCompanion.Settings(), initialMonIndex = 0)
    }
    @Test
    fun battleInfo() = paparazzi.snapshot {
        QolTheme {
            val party = SampleCompanion.snapshot.party
            BattleInfoScreen(listOf(party[0].withMovesVs(party[1])), listOf(party[1]), isDouble = false, showHints = true, onBack = {}, onShowSuggestions = {})
        }
    }

    /** A trainer battle: move verdicts against an Onix, the FOE TEAM strip, an out-of-PP move. */
    @Test
    fun battleInfoTrainer() = paparazzi.snapshot {
        QolTheme {
            fun foe(species: Int, level: Int, hp: Int, maxHp: Int): MonView {
                val mu = com.pokedaisey.app.companion.data.typeMatchups(
                    com.pokedaisey.app.companion.data.typeIdOf("Rock"),
                    com.pokedaisey.app.companion.data.typeIdOf("Ground"),
                )
                return MonView(
                    species, if (species == 95) "ONIX" else "GEODUDE", level, hp, maxHp, "", listOf("Rock", "Ground"),
                    spriteAsset("pokemon", species), GENDER_SYMBOL_MALE,
                    weaknesses = mu.weaknesses, resistances = mu.resistances, immunities = mu.immunities,
                )
            }
            val team = listOf(foe(74, 12, 0, 33), foe(95, 14, 21, 40), foe(74, 12, 33, 33))
            val me = SampleCompanion.snapshot.party[0].withMovesVs(team[1])
                .let { it.copy(moves = it.moves.mapIndexed { i, mv -> if (i == 1) mv.copy(pp = 0) else mv }) }
            BattleInfoScreen(
                listOf(me), listOf(team[1]), isDouble = false, showHints = true, onBack = {}, onShowSuggestions = {},
                foeTeam = team, seenFoes = setOf(0, 1), shownFoe = 1,
            )
        }
    }

    @Test
    fun battleSuggestions() = paparazzi.snapshot {
        QolTheme {
            val party = SampleCompanion.snapshot.party
            SuggestionsScreen(party, foe = party[1], onBack = {}, onShowInfo = {}, active = listOf(party[0]))
        }
    }

    @Test
    fun unsupported() = paparazzi.snapshot {
        CompanionScreen(
            SnapshotView(connected = false, error = "game code AMTE isn't a supported Pokémon game", unsupported = true),
            SampleCompanion.Slots, SampleCompanion.Settings(),
        )
    }
    @Test fun dex() = tab("DEX")

    @Test
    fun dexEntry() = paparazzi.snapshot {
        QolTheme {
            val dex = SampleCompanion.snapshot.pokedex!!
            val ui = DexUiState(androidx.compose.foundation.lazy.rememberLazyListState())
            ui.open = 6
            PokedexEntryScreen(dex, ui, 6)
        }
    }

    /** Emerald's Hoenn dex (TREECKO = No.001), read from the retail Emerald ROM. */
    @Test
    fun dexEmerald() {
        val shot = emeraldDex() ?: return
        paparazzi.snapshot { CompanionScreen(shot, SampleCompanion.Slots, SampleCompanion.Settings(), initialTab = "DEX") }
    }

    @Test
    fun dexEntryEmerald() {
        val shot = emeraldDex() ?: return
        paparazzi.snapshot {
            QolTheme {
                val ui = DexUiState(androidx.compose.foundation.lazy.rememberLazyListState())
                ui.open = 258 // MUDKIP
                PokedexEntryScreen(shot.pokedex!!, ui, 258)
            }
        }
    }

    /**
     * Emerald's GUIDE on NEXT BOSS, live: the retail ROM's trainer tables and the
     * emerald_vanilla save fixture (six badges -> TATE AND LIZA).
     */
    @Test
    fun guideEmerald() {
        emeraldDex() ?: return
        val live = com.pokedaisey.app.companion.data.buildSnapshotView(
            com.pokedaisey.app.companion.data.decodeNative(
                "emerald_vanilla", com.pokedaisey.app.companion.data.NATIVE_EMERALD_RETAIL,
            ),
        )
        val g = com.pokedaisey.app.companion.data.GUIDE_TABLES_EMERALD
        listOf(271, 272).forEach { GuideRomSource.party(g, it) }
        GuideRomSource.encounters(g, live.mapGroup, live.mapNum)
        paparazzi.snapshot {
            QolTheme {
                val source = rememberGuide(GameKind.EMERALD, live.pokedex, live.guideTables)!!
                GuideScreen(source, GuideUiState().apply { page = "NEXT BOSS" }, live)
            }
        }
    }

    /** Ruby's own GUIDE (not Emerald's) on HERE: the real save in SLATEPORT CITY, the ROM's wild table. */
    @Test fun rubyGuideHere() = retailGuide("ruby_rev1", NATIVE_RUBY, RUBY_ROM, "HERE")

    /** Sapphire's STUCK?: its own story (KYOGRE in the CAVE OF ORIGIN), that answer open. */
    @Test fun sapphireGuideStuck() =
        retailGuide("sapphire_rev1", NATIVE_SAPPHIRE, SAPPHIRE_ROM, "STUCK?", "STUCK?/LATE GAME/SOOTOPOLIS CITY's gym is closed/null" to 2)

    // Heart and Soul's GUIDE from the real save (NEW BARK TOWN, no badges): its
    // area data, FALKNER next, and the WHERE IS generated from the area data.
    @Test fun hnsGuideHere() = retailGuide("heart_and_soul", NATIVE_HEART_AND_SOUL, HNS_ROM, "HERE", kind = GameKind.HEART_AND_SOUL)
    @Test fun hnsGuideNextBoss() =
        retailGuide("heart_and_soul", NATIVE_HEART_AND_SOUL, HNS_ROM, "NEXT BOSS", "NEXT BOSS/NEXT: LEADER FALKNER/POKéMON 1/LV 8" to 2, GameKind.HEART_AND_SOUL)
    @Test fun hnsGuideWhereIs() =
        retailGuide("heart_and_soul", NATIVE_HEART_AND_SOUL, HNS_ROM, "WHERE IS", "WHERE IS/HMs/HM01/null" to 1, GameKind.HEART_AND_SOUL)

    // Unbound: each gym leader's four difficulty teams, and the ROM-read WHERE IS.
    @Test fun unboundGuideNextBoss() =
        retailGuide("unbound", NATIVE_UNBOUND_WITH_DEX, "Pokemon - Unbound (v2.1.1.1).gba", "NEXT BOSS", kind = GameKind.UNBOUND)
    @Test fun unboundGuideWhereIs() =
        retailGuide("unbound", NATIVE_UNBOUND_WITH_DEX, "Pokemon - Unbound (v2.1.1.1).gba", "WHERE IS", "WHERE IS/KEY ITEMS/Go-Goggles/null" to 2, GameKind.UNBOUND)

    // Radical Red: gyms 1-7 and the ROM-read WHERE IS (its HMs).
    @Test fun radicalRedGuideNextBoss() =
        retailGuide("radical_red", NATIVE_RADICAL_RED_V4_1, "Pokemon - Radical Red (v4.1).gba", "NEXT BOSS", kind = GameKind.RADICAL_RED)
    @Test fun radicalRedGuideWhereIs() =
        retailGuide("radical_red", NATIVE_RADICAL_RED_V4_1, "Pokemon - Radical Red (v4.1).gba", "WHERE IS", "WHERE IS/HMs/Hm01/null" to 2, GameKind.RADICAL_RED)

    // Odyssey / Gaia / Amethyst / Celia: NEXT BOSS (NORMAL / HARD, four difficulty tables, a gauntlet) and a ROM-read WHERE IS.
    @Test fun odysseyGuideNextBoss() =
        retailGuide("odyssey", NATIVE_ODYSSEY, "Pokémon Odyssey (English) (v4.1.1).gba", "NEXT BOSS", kind = GameKind.ODYSSEY)
    @Test fun gaiaGuideNextBoss() =
        retailGuide("gaia", NATIVE_GAIA_V3_2, "Pokemon - Gaia (v3.2).gba", "NEXT BOSS", kind = GameKind.GAIA)
    @Test fun amethystGuideNextBoss() =
        retailGuide("amethyst", NATIVE_AMETHYST, "Pokemon Amethyst (v1.3.0).gba", "NEXT BOSS", kind = GameKind.AMETHYST)
    @Test fun celiaGuideNextBoss() =
        retailGuide("celia", NATIVE_CELIA, "Pokemon Celia's Stupid Romhack (v1.1.4).gba", "NEXT BOSS", kind = GameKind.CELIA)
    @Test fun gaiaGuideWhereIs() =
        retailGuide("gaia", NATIVE_GAIA_V3_2, "Pokemon - Gaia (v3.2).gba", "WHERE IS", kind = GameKind.GAIA)

    private fun retailGuide(
        fixture: String, cfg: NativeConfig, romFile: String, page: String, open: Pair<String, Int>? = null,
        kind: GameKind = GameKind.EMERALD,
    ) {
        val rom = RomFileReader.load(RETAIL_ROM_DIR + romFile) ?: return
        activeGame = kind
        resetNativeBagCache()
        val v = buildSnapshotView(readNativeTelemetry(rom.withRam(FixtureMemoryReader.load(fixture)), cfg)).copy(game = kind)
        PokedexSource.reader = rom
        DecompIconSource.reader = rom
        DecompIconSource.tables = cfg.iconTables
        val live = v.guideTables!!
        GuideRomSource.encounters(live, v.mapGroup, v.mapNum)?.let { e ->
            (e.grass + e.water + e.oldRod + e.goodRod + e.superRod).forEach { DecompIconSource.get(it.species) }
        }
        PokedexSource.nationalOf(v.pokedex!!.tables, 1)
        v.progress?.let { p -> gameGuide(guideId(kind, live))?.bosses?.forEach { b -> b.trainers(p).forEach { GuideRomSource.party(live, it) } } }
        paparazzi.snapshot {
            QolTheme {
                val ui = androidx.compose.runtime.remember {
                    GuideUiState().apply { this.page = page; open?.let { this.open = it.first; level = it.second } }
                }
                GuideScreen(rememberGuide(v.game, v.pokedex, v.guideTables)!!, ui, v)
            }
        }
    }

    /** Switches the ROM readers to retail Emerald; null (skip) if it isn't on this machine. */
    private fun emeraldDex(): SnapshotView? {
        val rom = RomFileReader.load(RETAIL_ROM_DIR + "Pokemon - Emerald Version (USA, Europe).gba")
            ?: return null
        activeGame = GameKind.EMERALD
        PokedexSource.reader = rom
        DecompIconSource.reader = rom
        DecompIconSource.tables = NATIVE_EMERALD.iconTables
        val t = POKEDEX_EMERALD
        val hoenn = PokedexSource.regionalOrder(t)!!
        hoenn.take(12).forEach { n ->
            val species = PokedexSource.speciesFor(t, n)
            PokedexSource.entry(t, n); PokedexSource.frontSprite(t, species); PokedexSource.footprint(t, species)
            DecompIconSource.get(species)
        }
        return SampleCompanion.snapshot.copy(
            game = GameKind.EMERALD, party = emptyList(),
            location = LocationView("ROUTE 104", null, 0, 0, 0, 0),
            pokedex = PokedexState(t, seen = hoenn.take(9).toSet(), caught = setOf(252, 255, 258, 261), national = false),
        )
    }

    /** Unbound's National dex (Gen 1-8) and a Gen 8 entry, read from the Unbound ROM. */
    @Test
    fun dexUnbound() {
        val shot = unboundDex() ?: return
        paparazzi.snapshot { CompanionScreen(shot, SampleCompanion.Slots, SampleCompanion.Settings(), initialTab = "DEX") }
    }

    @Test
    fun dexEntryUnbound() {
        val shot = unboundDex() ?: return
        paparazzi.snapshot {
            QolTheme {
                val ui = DexUiState(androidx.compose.foundation.lazy.rememberLazyListState())
                ui.open = 443 // GIBLE
                PokedexEntryScreen(shot.pokedex!!, ui, 443)
            }
        }
    }

    private fun unboundDex(): SnapshotView? {
        val rom = RomFileReader.load(RETAIL_ROM_DIR + "Pokemon - Unbound (v2.1.1.1).gba")
            ?: return null
        activeGame = GameKind.UNBOUND
        PokedexSource.reader = rom
        UnboundIconSource.reader = rom
        val t = POKEDEX_UNBOUND
        (listOf(443) + (1..12)).forEach { n ->
            val species = PokedexSource.speciesFor(t, n)
            PokedexSource.entry(t, n); PokedexSource.frontSprite(t, species)
            UnboundIconSource.get(species)
        }
        return SampleCompanion.snapshot.copy(
            game = GameKind.UNBOUND, party = emptyList(),
            location = LocationView("Frozen Heights", null, 0, 0, 0, 0),
            pokedex = PokedexState(t, seen = setOf(1, 4, 7, 246, 443, 451, 686), caught = setOf(4, 443), national = true),
        )
    }

    @Test fun guide() = tab("GUIDE")

    /** The first-open notice (AI-written, may be wrong). */
    @Test
    fun guideNotice() = paparazzi.snapshot {
        CompanionScreen(SampleCompanion.snapshot, SampleCompanion.Slots, SampleCompanion.Settings().apply { noticeAccepted = false }, initialTab = "GUIDE")
    }

    /** STUCK? with one entry open at its hint. */
    @Test
    fun guideRevealed() = guideShot("STUCK?", "STUCK?/EARLY GAME/An old man blocks the road north of VIRIDIAN/null" to 1)

    /** WHERE IS with the answer open, and Poké Balls on what the save has. */
    @Test
    fun guideWhereIs() = guideShot("WHERE IS", "WHERE IS/KEY ITEMS/BICYCLE/null" to 2)

    /** HERE on ROUTE 24, from the retail ROM: seen species named, one unseen revealed. */
    @Test
    fun guideHere() = guideShot("HERE", "HERE/SUPER ROD/???/LV 15-25  40%" to 1)

    /** NEXT BOSS with BROCK beaten: MISTY next, her first Pokémon's hint open. */
    @Test
    fun guideNextBoss() = guideShot("NEXT BOSS", "NEXT BOSS/NEXT: LEADER MISTY/POKéMON 1/LV 18" to 1)

    /** EVOLUTIONS from the retail ROM: the party, then the special ones. */
    @Test
    fun guideEvolutions() = guideShot("EVOLUTIONS", "EVOLUTIONS/YOUR PARTY/PIDGEY/null" to 2)

    private fun guideShot(page: String, open: Pair<String, Int>) = paparazzi.snapshot {
        QolTheme {
            val ui = androidx.compose.runtime.remember {
                GuideUiState().apply { this.page = page; this.open = open.first; level = open.second }
            }
            val snap = SampleCompanion.snapshot
            GuideScreen(rememberGuide(snap.game, snap.pokedex, snap.guideTables)!!, ui, snap)
        }
    }

    /** The trainer's party for the FOE TEAM shots: GEODUDE fainted, ONIX out, two more unseen. */
    private val foes = listOf(
        MonView(74, "GEODUDE", 12, 0, 30, "", listOf("Rock", "Ground"), spriteAsset("pokemon", 74)),
        MonView(95, "ONIX", 14, 0, 35, "", listOf("Rock", "Ground"), spriteAsset("pokemon", 95)),
        MonView(
            111, "RHYHORN", 15, 40, 40, "", listOf("Ground", "Rock"), spriteAsset("pokemon", 111),
            weaknesses = listOf(TypeMatchup("Water", "4x", 400), TypeMatchup("Grass", "4x", 400)),
            immunities = listOf(TypeMatchup("Electric", "0x", 0)),
        ),
        MonView(142, "AERODACTYL", 16, 40, 40, "", listOf("Rock", "Flying"), spriteAsset("pokemon", 142)),
    )

    /** ONIX fainted; the trainer is about to use RHYHORN ("Will you change POKéMON?"): INFO shows it, your moves rated against it. */
    @Test
    fun battleFoeNext() = paparazzi.snapshot {
        QolTheme {
            val you = SampleCompanion.snapshot.party[0]
            BattleInfoScreen(
                listOf(you.withMovesVs(foes[2])), listOf(foes[2]), isDouble = false, showHints = true, onBack = {}, onShowSuggestions = {},
                foeTeam = foes, seenFoes = setOf(0, 1, 2), shownFoe = 2, foeHeading = "FOE · COMING IN NEXT",
            )
        }
    }

    /** A tapped slot the trainer hasn't sent out yet. */
    @Test
    fun battleFoeTapped() = paparazzi.snapshot {
        QolTheme {
            val you = SampleCompanion.snapshot.party[0]
            BattleInfoScreen(
                listOf(you.withMovesVs(foes[3])), listOf(foes[3]), isDouble = false, showHints = true, onBack = {}, onShowSuggestions = {},
                foeTeam = foes, seenFoes = setOf(0, 1), shownFoe = 3, foeHeading = "FOE · NOT SENT OUT YET",
            )
        }
    }

    // LeafGreen / Ruby: the real decode of each game's headless capture (see
    // LeafGreenDecodeTest / RubySapphireDecodeTest), through the whole UI.
    @Test fun leafGreenParty() = retail("leafgreen_rev1", NATIVE_LEAFGREEN_REV1, LG_ROM, GameKind.FIRERED, "PARTY")
    @Test fun leafGreenItems() = retail("leafgreen_rev1", NATIVE_LEAFGREEN_REV1, LG_ROM, GameKind.FIRERED, "ITEMS")
    @Test fun leafGreenDex() = retail("leafgreen_rev1", NATIVE_LEAFGREEN_REV1, LG_ROM, GameKind.FIRERED, "DEX")
    @Test fun rubyParty() = retail("ruby_rev1", NATIVE_RUBY, RUBY_ROM, GameKind.EMERALD, "PARTY")
    @Test fun rubyItems() = retail("ruby_rev1", NATIVE_RUBY, RUBY_ROM, GameKind.EMERALD, "ITEMS")
    @Test fun rubyDex() = retail("ruby_rev1", NATIVE_RUBY, RUBY_ROM, GameKind.EMERALD, "DEX")
    @Test fun rubyMap() = retail("ruby_rev1", NATIVE_RUBY, RUBY_ROM, GameKind.EMERALD, "MAP")
    @Test fun rubyDexEntry() = retail("ruby_rev1", NATIVE_RUBY, RUBY_ROM, GameKind.EMERALD, "DEX", entry = 384)

    private fun retail(fixture: String, cfg: NativeConfig, romFile: String, kind: GameKind, tab: String, entry: Int? = null) {
        val rom = RomFileReader.load(RETAIL_ROM_DIR + romFile) ?: return
        activeGame = kind
        resetNativeBagCache()
        val t = readNativeTelemetry(rom.withRam(FixtureMemoryReader.load(fixture)), cfg)
        val v = buildSnapshotView(t).copy(game = kind)
        PokedexSource.reader = rom
        DecompIconSource.reader = rom
        DecompIconSource.tables = cfg.iconTables
        val dex = v.pokedex!!
        (v.party.map { it.species } + (1..10).map { PokedexSource.speciesFor(dex.tables, it) } +
            PokedexSource.regionalOrder(dex.tables)!!.take(10).map { PokedexSource.speciesFor(dex.tables, it) })
            .forEach { DecompIconSource.get(it) }
        v.items.forEach { DecompIconSource.getItem(it.itemId) }
        if (entry != null) {
            val species = PokedexSource.speciesFor(dex.tables, entry)
            PokedexSource.entry(dex.tables, entry); PokedexSource.frontSprite(dex.tables, species); PokedexSource.footprint(dex.tables, species)
        }
        paparazzi.snapshot {
            if (entry == null) CompanionScreen(v, SampleCompanion.Slots, SampleCompanion.Settings(), initialTab = tab)
            else QolTheme {
                val ui = DexUiState(androidx.compose.foundation.lazy.rememberLazyListState())
                ui.open = entry
                PokedexEntryScreen(dex, ui, entry)
            }
        }
    }

    // The ROM hacks' DEX: each `<game>_dex` fixture's real flags (see
    // HackPokedexTest) with the hack's own ROM tables - the list, and one entry.
    @Test fun odysseyDexEntry() = hackDex("odyssey_dex", NATIVE_ODYSSEY, "Pokémon Odyssey (English) (v4.1.1).gba", GameKind.ODYSSEY, 387)
    @Test fun radicalRedDexEntry() = hackDex("radical_red_dex", NATIVE_RADICAL_RED_V4_1, "Pokemon - Radical Red (v4.1).gba", GameKind.RADICAL_RED, 35)
    @Test fun amethystDex() = hackDex("amethyst_dex", NATIVE_AMETHYST, "Pokemon Amethyst (v1.3.0).gba", GameKind.AMETHYST)
    @Test fun amethystDexEntry() = hackDex("amethyst_dex", NATIVE_AMETHYST, "Pokemon Amethyst (v1.3.0).gba", GameKind.AMETHYST, 340)
    @Test fun gaiaDex() = hackDex("gaia_dex", NATIVE_GAIA_V3_2, "Pokemon - Gaia (v3.2).gba", GameKind.GAIA)
    @Test fun gaiaDexEntry() = hackDex("gaia_dex", NATIVE_GAIA_V3_2, "Pokemon - Gaia (v3.2).gba", GameKind.GAIA, 390)
    @Test fun celiaDex() = hackDex("celia_dex", NATIVE_CELIA, "Pokemon Celia's Stupid Romhack (v1.1.4).gba", GameKind.CELIA)
    @Test fun celiaDexEntry() = hackDex("celia_dex", NATIVE_CELIA, "Pokemon Celia's Stupid Romhack (v1.1.4).gba", GameKind.CELIA, 2)
    @Test fun heartAndSoulDex() = hackDex("heart_and_soul_dex", NATIVE_HEART_AND_SOUL, "Pokémon Heart and Soul (v2.0.6).gba", GameKind.HEART_AND_SOUL)
    @Test fun heartAndSoulDexEntry() = hackDex("heart_and_soul_dex", NATIVE_HEART_AND_SOUL, "Pokémon Heart and Soul (v2.0.6).gba", GameKind.HEART_AND_SOUL, 155)
    @Test fun lazarusDexEntry() = hackDex("lazarus_dex", NATIVE_LAZARUS, "Pokemon Lazarus (v2.0).gba", GameKind.LAZARUS, 653)
    @Test fun seaglassDex() = hackDex("emerald_seaglass_dex", NATIVE_EMERALD_SEAGLASS, "Pokemon Emerald Seaglass (v3.0).gba", GameKind.EMERALD_SEAGLASS)
    @Test fun seaglassDexEntry() = hackDex("emerald_seaglass_dex", NATIVE_EMERALD_SEAGLASS, "Pokemon Emerald Seaglass (v3.0).gba", GameKind.EMERALD_SEAGLASS, 4)
    @Test fun tmt2Dex() = hackDex("tmt2_dex", NATIVE_TMT2, "Pokemon Too Many Types 2 (v1.5.2).gba", GameKind.TMT2)
    @Test fun tmt2DexEntry() = hackDex("tmt2_dex", NATIVE_TMT2, "Pokemon Too Many Types 2 (v1.5.2).gba", GameKind.TMT2, 397)

    private fun hackDex(fixture: String, cfg: NativeConfig, romFile: String, kind: GameKind, entry: Int? = null) {
        val rom = RomFileReader.load(RETAIL_ROM_DIR + romFile) ?: return
        activeGame = kind
        val t = cfg.pokedex!!
        val dex = readPokedexState(rom.withRam(FixtureMemoryReader.load(fixture)), cfg, t)!!
        PokedexSource.reader = rom
        DecompIconSource.reader = rom
        DecompIconSource.tables = cfg.iconTables
        val shown = if (!t.hasRegional || dex.national) (1..t.nationalCount).toList() else PokedexSource.regionalOrder(t)!!.toList()
        (shown.take(12) + listOfNotNull(entry)).forEach { n ->
            val species = PokedexSource.speciesFor(t, n)
            PokedexSource.entry(t, n); PokedexSource.frontSprite(t, species); PokedexSource.footprint(t, species)
            DecompIconSource.get(species)
        }
        val v = SampleCompanion.snapshot.copy(game = kind, party = emptyList(), pokedex = dex)
        paparazzi.snapshot {
            if (entry == null) CompanionScreen(v, SampleCompanion.Slots, SampleCompanion.Settings(), initialTab = "DEX")
            else QolTheme {
                val ui = DexUiState(androidx.compose.foundation.lazy.rememberLazyListState())
                ui.open = entry
                PokedexEntryScreen(dex, ui, entry)
            }
        }
    }

    @Test fun map() = tab("MAP")
    @Test fun items() = tab("ITEMS")
    @Test fun states() = tab("STATES")
    @Test fun settings() = tab("SETTINGS")

    @Test
    fun optionSelector() = paparazzi.snapshot {
        QolTheme {
            val m = rememberGbaTextMetrics()
            OptionSelector("FF SPEED", listOf("INFINITE", "2×", "3×", "4×", "6×"), "4×", { it }, m, onPick = {}, onDismiss = {})
        }
    }

    @Test
    fun optionConfirm() = paparazzi.snapshot {
        QolTheme {
            val m = rememberGbaTextMetrics()
            OptionConfirm("CLOSE GAME?", "Returns to the ROM list. Progress is saved automatically.", "CLOSE", m, onConfirm = {}, onDismiss = {})
        }
    }
}

/** Fake companion inputs for screenshots / previews. */
private const val LG_ROM = "Pokemon - LeafGreen Version (USA, Europe) (Rev 1).gba"
private const val RUBY_ROM = "Pokemon - Ruby Version (USA, Europe) (Rev 1).gba"
private const val SAPPHIRE_ROM = "Pokemon - Sapphire Version (USA, Europe) (Rev 1).gba"
private const val HNS_ROM = "Pokémon Heart and Soul (v2.0.6).gba"

object SampleCompanion {
    val snapshot = SnapshotView(
        connected = true, facingDirection = "down", x = 12, y = 7, game = GameKind.FIRERED,
        location = LocationView("PALLET TOWN", "kanto", 64, 88, 8, 8),
        party = listOf(
            MonView(
                4, "CHARMANDER", 12, 30, 35, "", listOf("Fire"), null, GENDER_SYMBOL_MALE,
                moves = listOf(
                    MoveView("Scratch", "Normal", 35, 40), MoveView("Growl", "Normal", 40, 0),
                    MoveView("Ember", "Fire", 25, 40), MoveView("Metal Claw", "Steel", 35, 50),
                ),
                weaknesses = listOf(TypeMatchup("Water", "2x", 200), TypeMatchup("Ground", "2x", 200), TypeMatchup("Rock", "2x", 200)),
                resistances = listOf(
                    TypeMatchup("Fire", "½x", 50), TypeMatchup("Grass", "½x", 50), TypeMatchup("Ice", "½x", 50),
                    TypeMatchup("Bug", "½x", 50), TypeMatchup("Steel", "½x", 50),
                ),
            ),
            MonView(
                16, "PIDGEY", 9, 5, 26, "PSN", listOf("Normal", "Flying"), null, GENDER_SYMBOL_FEMALE,
                moves = listOf(MoveView("Tackle", "Normal", 35, 35), MoveView("Sand-Attack", "Ground", 15, 0), MoveView("Gust", "Flying", 35, 40)),
                weaknesses = listOf(TypeMatchup("Electric", "2x", 200), TypeMatchup("Ice", "2x", 200), TypeMatchup("Rock", "2x", 200)),
                resistances = listOf(TypeMatchup("Grass", "½x", 50), TypeMatchup("Bug", "½x", 50)),
                immunities = listOf(TypeMatchup("Ground", "0x", 0), TypeMatchup("Ghost", "0x", 0)),
            ),
        ),
        // ROUTE 24 (MAP_ROUTE24 = 3.43), BROCK's badge, the BICYCLE and OLD ROD in the bag.
        mapGroup = 3, mapNum = 43,
        guideTables = GUIDE_TABLES_FIRERED_REV1,
        progress = SaveProgress(ByteArray(0x120).also { it[0x820 / 8] = 1 }, ByteArray(0x200)),
        items = listOf(
            ItemView(13, "Potion", 3, null, POCKET_ITEMS, "Restores the HP of a POKéMON by 20 points."),
            ItemView(4, "Poké Ball", 10, null, POCKET_POKE_BALLS),
            ItemView(349, "Oak's Parcel", 1, null, POCKET_KEY_ITEMS),
            ItemView(360, "Bicycle", 1, null, POCKET_KEY_ITEMS),
            ItemView(262, "Old Rod", 1, null, POCKET_KEY_ITEMS),
        ),
        pokedex = PokedexState(
            POKEDEX_FIRERED_REV1,
            seen = (1..25).toSet() + setOf(129, 133, 147, 152),
            caught = setOf(1, 4, 6, 7, 10, 16, 19, 25, 129),
            national = true,
        ),
    )

    object Slots : StateSlots {
        override fun list() = (0..9).map { StateSlots.Slot(it, false, 0L, null) }
        override val currentIndex = 1
        override fun requestSave(index: Int) {}
        override fun requestLoad(index: Int) {}
        override fun requestUndoSave() {}
        override fun requestUndoLoad() {}
    }

    class Settings(
        private var tabs: List<String> = com.pokedaisey.app.companion.DEFAULT_COMPANION_TABS,
    ) : CompanionSettings {
        override val companionTabs get() = tabs
        override fun setCompanionTabs(tabs: List<String>) { this.tabs = tabs }
        var noticeAccepted = true
        override fun guideNoticeAccepted(game: String) = noticeAccepted
        override fun acceptGuideNotice(game: String) { noticeAccepted = true }
        override val ffMaxSpeed = 4f
        override fun setFfMaxSpeed(v: Float) {}
        override val ffToggled = false
        override fun setFfToggled(on: Boolean) {}
        override val ffMusicMode = com.pokedaisey.app.companion.FfMusicMode.STEADY
        override fun setFfMusicMode(mode: com.pokedaisey.app.companion.FfMusicMode) {}
        override val ffMode = com.pokedaisey.app.companion.FfMode.SMART
        override fun setFfMode(mode: com.pokedaisey.app.companion.FfMode) {}
        override val touchControlsMode = 0
        override fun setTouchControlsMode(v: Int) {}
        override fun gbaControlBindings() = GbaControls.Btn.entries.associateWith { listOf("BUTTON_${it.name}") }
        override fun setGbaControlBinding(btn: GbaControls.Btn, keyName: String) {}
        override fun hotkeyBindings() = Hotkeys.Action.entries.associateWith { listOf("BUTTON_Y") }
        override fun setHotkeyBinding(action: Hotkeys.Action, keyName: String) {}
        override fun restartGame() {}
        override fun closeGame() {}
        override val showHints = true
        override fun setShowHints(on: Boolean) {}
        override val clickSound = true
        override fun setClickSound(on: Boolean) {}
        override val statusBar = false
        override fun setStatusBar(on: Boolean) {}
        override val gameName = "Pokémon FireRed"
        override val romFileName = "firered-qol.gba"
    }
}
