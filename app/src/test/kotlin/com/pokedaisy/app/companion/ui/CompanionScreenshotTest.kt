package com.pokedaisy.app.companion.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.pokedaisy.app.GbaControls
import com.pokedaisy.app.Hotkeys
import com.pokedaisy.app.companion.CompanionSettings
import com.pokedaisy.app.companion.StateSlots
import com.pokedaisy.app.companion.data.GENDER_SYMBOL_FEMALE
import com.pokedaisy.app.companion.data.GENDER_SYMBOL_MALE
import com.pokedaisy.app.companion.data.GameKind
import com.pokedaisy.app.companion.data.ItemView
import com.pokedaisy.app.companion.data.LocationView
import com.pokedaisy.app.companion.data.MonView
import com.pokedaisy.app.companion.data.MoveView
import com.pokedaisy.app.companion.data.POCKET_ITEMS
import com.pokedaisy.app.companion.data.POCKET_KEY_ITEMS
import com.pokedaisy.app.companion.data.POCKET_POKE_BALLS
import com.pokedaisy.app.companion.data.SnapshotView
import com.pokedaisy.app.companion.data.TypeMatchup
import com.pokedaisy.app.companion.data.activeGame
import com.pokedaisy.app.companion.data.DecompIconSource
import com.pokedaisy.app.companion.data.EvolutionSource
import com.pokedaisy.app.companion.data.GUIDE_TABLES_FIRERED_REV1
import com.pokedaisy.app.companion.data.GuideRomSource
import com.pokedaisy.app.companion.data.SaveProgress
import com.pokedaisy.app.companion.data.spriteAsset
import com.pokedaisy.app.companion.data.withMovesVs
import com.pokedaisy.app.companion.data.NATIVE_EMERALD
import com.pokedaisy.app.companion.data.NATIVE_FIRERED_REV1
import com.pokedaisy.app.companion.data.POKEDEX_EMERALD
import com.pokedaisy.app.companion.data.POKEDEX_UNBOUND
import com.pokedaisy.app.companion.data.UnboundIconSource
import com.pokedaisy.app.companion.data.POKEDEX_FIRERED_REV1
import com.pokedaisy.app.companion.data.PokedexSource
import com.pokedaisy.app.companion.data.NATIVE_UNBOUND_WITH_DEX
import com.pokedaisy.app.companion.data.guideId
import com.pokedaisy.app.companion.data.gameGuide
import com.pokedaisy.app.companion.data.NATIVE_SAPPHIRE
import com.pokedaisy.app.companion.data.readPokedexState
import com.pokedaisy.app.companion.data.NATIVE_TMT2
import com.pokedaisy.app.companion.data.NATIVE_GLAZED
import com.pokedaisy.app.companion.data.NATIVE_EMERALD_ROGUE
import com.pokedaisy.app.companion.data.NATIVE_IMPERIUM
import com.pokedaisy.app.companion.data.NATIVE_QUETZAL
import com.pokedaisy.app.companion.data.NATIVE_ROWE
import com.pokedaisy.app.companion.data.NATIVE_EMERALD_SEAGLASS
import com.pokedaisy.app.companion.data.NATIVE_LAZARUS
import com.pokedaisy.app.companion.data.NATIVE_SOULGOLD
import com.pokedaisy.app.companion.data.NATIVE_SOULGOLD_V1_2
import com.pokedaisy.app.companion.data.NATIVE_SOULGOLD_V1_2B
import com.pokedaisy.app.companion.data.NATIVE_HEART_AND_SOUL
import com.pokedaisy.app.companion.data.NATIVE_CELIA
import com.pokedaisy.app.companion.data.NATIVE_GAIA_V3_2
import com.pokedaisy.app.companion.data.NATIVE_AMETHYST
import com.pokedaisy.app.companion.data.NATIVE_AMETHYST_V1_4_1
import com.pokedaisy.app.companion.data.amethystV141
import com.pokedaisy.app.companion.data.romGameCode
import com.pokedaisy.app.companion.data.romLanguage
import com.pokedaisy.app.companion.data.NATIVE_EMERALD_DE
import com.pokedaisy.app.companion.data.NATIVE_EMERALD_ES
import com.pokedaisy.app.companion.data.NATIVE_EMERALD_FR
import com.pokedaisy.app.companion.data.NATIVE_EMERALD_IT
import com.pokedaisy.app.companion.data.NATIVE_EMERALD_JA
import com.pokedaisy.app.companion.data.NATIVE_RADICAL_RED_V4_1
import com.pokedaisy.app.companion.data.NATIVE_ODYSSEY
import com.pokedaisy.app.companion.data.PokedexState
import com.pokedaisy.app.companion.data.RomArt
import com.pokedaisy.app.companion.data.CardStyle
import com.pokedaisy.app.companion.data.TrainerCardArt
import com.pokedaisy.app.companion.data.readTrainerCard
import com.pokedaisy.app.companion.data.RomFileReader
import com.pokedaisy.app.companion.data.FixtureMemoryReader
import com.pokedaisy.app.companion.data.NativeConfig
import com.pokedaisy.app.companion.data.NATIVE_LEAFGREEN_REV1
import com.pokedaisy.app.companion.data.NATIVE_RUBY
import com.pokedaisy.app.companion.data.RETAIL_ROM_DIR
import com.pokedaisy.app.companion.data.buildSnapshotView
import com.pokedaisy.app.companion.data.readNativeTelemetry
import com.pokedaisy.app.companion.data.resetNativeBagCache
import com.pokedaisy.app.companion.ui.theme.QolTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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
open class CompanionScreenshotTest {
    @get:Rule
    open val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1240, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE,
        ),
        theme = "android:Theme.Material.NoActionBar",
    )

    @Before
    fun game() {
        activeGame = GameKind.FIRERED
        OptionColors.inGame = true
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

    /** SETTINGS > STATUS BAR > COMPANION: the game's status bar over the tabs. */
    @Test fun partyStatusBar() {
        CompanionStatusBar.shown = true
        try {
            paparazzi.snapshot {
                CompanionScreen(SampleCompanion.snapshot, SampleCompanion.Slots, SampleCompanion.Settings(), initialTab = "PARTY", statusBar = {
                    GameStatusBar("Pokémon FireRed", "PALLET TOWN", 3000, "12:34")
                })
            }
        } finally { CompanionStatusBar.shown = false }
    }

    @Test fun yellowSettingsStatusBar() {
        CompanionStatusBar.shown = true
        try { yellow("yellow", "SETTINGS", statusBar = true) } finally { CompanionStatusBar.shown = false }
    }

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

    /** RetroAchievements: the set (on the tab bar), signed out, a ROM with no set, and an unlock popup. */
    @Test fun achievements() = achievementsShot(SampleCompanion.achievementsState)

    /** A cheat on: the set's tally stays, with the PAUSED badge by the title. */
    @Test fun achievementsCheatsPaused() = achievementsShot(SampleCompanion.achievementsState.copy(cheatsPaused = true))

    @Test fun achievementsSignedOut() = achievementsShot(com.pokedaisy.app.companion.AchievementsState())

    @Test fun achievementsNoSet() = achievementsShot(
        com.pokedaisy.app.companion.AchievementsState(
            user = "Lidor",
            game = com.pokedaisy.app.companion.AchievementGame(0, null, "5d14746c8e95c0979a374e80ec123096", 0, 0, 0, 0),
        ),
    )

    private fun achievementsShot(state: com.pokedaisy.app.companion.AchievementsState) = paparazzi.snapshot {
        CompanionScreen(
            SampleCompanion.snapshot, SampleCompanion.Slots,
            SampleCompanion.Settings(listOf("PARTY", "DEX", "MAP", "ITEMS", "ACHIEVEMENTS")),
            initialTab = "ACHIEVEMENTS", achievements = SampleCompanion.Achievements(state),
        )
    }

    @Test
    fun achievementPopup() = paparazzi.snapshot {
        CompanionScreen(
            SampleCompanion.snapshot, SampleCompanion.Slots, SampleCompanion.Settings(),
            achievements = SampleCompanion.Achievements(SampleCompanion.achievementsState),
            initialPopup = com.pokedaisy.app.companion.AchievementPopup(
                com.pokedaisy.app.companion.AchievementPopup.Kind.UNLOCKED, "Boulder Badge",
                "Defeat Brock in Pewter City", points = 5,
            ),
        )
    }

    /** The CHEEVOS tab's leaderboards: the list, and one board's page (the player 17th, below the top 5 shown). */
    @Test fun leaderboards() = leaderboardShot(board = null)

    @Test fun leaderboardPage() = leaderboardShot(board = 11)

    private fun leaderboardShot(board: Int?) = paparazzi.snapshot {
        QolTheme {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                GameBackdrop(GameKind.FIRERED)
                AchievementsScreen(
                    SampleCompanion.Achievements(SampleCompanion.achievementsState, SampleCompanion.leaderboardPage),
                    androidx.compose.ui.Modifier.padding(12.dp),
                    initialLeaderboards = true, initialBoard = board,
                )
            }
        }
    }

    /** A running leaderboard attempt's timer and two challenge badges, over PARTY. */
    @Test
    fun achievementIndicators() = paparazzi.snapshot {
        CompanionScreen(
            SampleCompanion.snapshot, SampleCompanion.Slots, SampleCompanion.Settings(),
            achievements = SampleCompanion.Achievements(
                SampleCompanion.achievementsState.copy(
                    trackers = listOf(com.pokedaisy.app.companion.LeaderboardTracker(1, "1:23.45")),
                    challenges = listOf(
                        com.pokedaisy.app.companion.ChallengeIndicator(2, "No Healing", null),
                        com.pokedaisy.app.companion.ChallengeIndicator(3, "Solo Run", null),
                    ),
                ),
            ),
        )
    }

    @Test
    fun partyDetail() = paparazzi.snapshot {
        CompanionScreen(SampleCompanion.snapshot, SampleCompanion.Slots, SampleCompanion.Settings(), initialMonIndex = 0)
    }
    /** The summary's STATS page: IVs / EVs / nature / Hidden Power instead of the moves. */
    @Test
    fun partyDetailStats() = paparazzi.snapshot {
        QolTheme { MonDetailScreen(SampleCompanion.snapshot.party, 0, {}, {}, initialShowStats = true) }
    }

    /** Battle INFO's STATS page (FOE IVS on): your battler beside the foe. */
    @Test
    fun battleStats() = paparazzi.snapshot {
        QolTheme {
            val party = SampleCompanion.snapshot.party
            BattleStatsScreen(listOf(party[0]), listOf(party[1]), "FOE", onBack = {})
        }
    }

    /** INFO with FOE IVS on in a trainer battle: STATS beside SUGGESTIONS, the FOE TEAM strip still fits. */
    @Test
    fun battleInfoStatsButton() = paparazzi.snapshot {
        QolTheme {
            val you = SampleCompanion.snapshot.party[0]
            BattleInfoScreen(
                listOf(you.withMovesVs(foes[1])), listOf(foes[1]), isDouble = false, showHints = true,
                onBack = {}, onShowSuggestions = {}, onShowStats = {},
                foeTeam = foes, seenFoes = setOf(0, 1), shownFoe = 1,
            )
        }
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
                val mu = com.pokedaisy.app.companion.data.typeMatchups(
                    com.pokedaisy.app.companion.data.typeIdOf("Rock"),
                    com.pokedaisy.app.companion.data.typeIdOf("Ground"),
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
        val live = com.pokedaisy.app.companion.data.buildSnapshotView(
            com.pokedaisy.app.companion.data.decodeNative(
                "emerald_vanilla", com.pokedaisy.app.companion.data.NATIVE_EMERALD_RETAIL,
            ),
        )
        val g = com.pokedaisy.app.companion.data.GUIDE_TABLES_EMERALD
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
    @Test fun amethystV141GuideNextBoss() =
        retailGuide("amethyst_v141", NATIVE_AMETHYST_V1_4_1, AM141_ROM, "NEXT BOSS", kind = GameKind.AMETHYST)
    @Test fun amethystV141GuideHere() =
        retailGuide("amethyst_v141", NATIVE_AMETHYST_V1_4_1, AM141_ROM, "HERE", kind = GameKind.AMETHYST)
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
        amethystV141 = cfg === NATIVE_AMETHYST_V1_4_1 // the Poller sets these with activeGame
        romLanguage = cfg.language
        romGameCode = cfg.gameCode
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

    // Radical Red's own species / item tables and icons on a later save (radical_red_1636),
    // and Lazarus's own region map, rebuilt from its ROM.
    @Test fun radicalRedParty() = retail("radical_red_1636", NATIVE_RADICAL_RED_V4_1, RR_ROM, GameKind.RADICAL_RED, "PARTY")
    @Test fun radicalRedItems() = retail("radical_red_1636", NATIVE_RADICAL_RED_V4_1, RR_ROM, GameKind.RADICAL_RED, "ITEMS")
    @Test fun lazarusMap() = retail("lazarus_srm", NATIVE_LAZARUS, "Pokemon Lazarus (v2.0).gba", GameKind.LAZARUS, "MAP", art = true)
    @Test fun lazarusBattle() = retail("lazarus_battle", NATIVE_LAZARUS, "Pokemon Lazarus (v2.0).gba", GameKind.LAZARUS, "BATTLE")

    // SoulGold: its own party / bag look (the bag's night sky rebuilt from the ROM), battle and map.
    @Test fun soulGoldParty() = retail("soulgold_battle", NATIVE_SOULGOLD, SG_ROM, GameKind.SOULGOLD, "PARTY", art = true)
    @Test fun soulGoldSixParty() = retail("soulgold_party", NATIVE_SOULGOLD, SG_ROM, GameKind.SOULGOLD, "PARTY", art = true)
    @Test fun soulGoldItems() = retail("soulgold", NATIVE_SOULGOLD, SG_ROM, GameKind.SOULGOLD, "ITEMS", art = true)
    @Test fun soulGoldBattle() = retail("soulgold_battle", NATIVE_SOULGOLD, SG_ROM, GameKind.SOULGOLD, "BATTLE", art = true)
    @Test fun soulGoldMap() = retail("soulgold", NATIVE_SOULGOLD, SG_ROM, GameKind.SOULGOLD, "MAP", art = true)
    // Emerald Seaglass: its own Hoenn art (from its ROM) and the battle pane, now that its battle memory is mapped.
    @Test fun seaglassMap() = retail("emerald_seaglass", NATIVE_EMERALD_SEAGLASS, SEAGLASS_ROM, GameKind.EMERALD_SEAGLASS, "MAP", art = true)
    @Test fun seaglassBattle() =
        retail("emerald_seaglass_battle", NATIVE_EMERALD_SEAGLASS, SEAGLASS_ROM, GameKind.EMERALD_SEAGLASS, "BATTLE", art = true)
    // Pokémon Glazed: retail Emerald's RAM, its own names / types / map sections, Emerald's bag and party slots on olive.
    @Test fun glazedParty() = retail("glazed", NATIVE_GLAZED, GLAZED_ROM, GameKind.GLAZED, "PARTY", art = true)
    @Test fun glazedItems() = retail("glazed", NATIVE_GLAZED, GLAZED_ROM, GameKind.GLAZED, "ITEMS", art = true)
    @Test fun glazedMap() = retail("glazed", NATIVE_GLAZED, GLAZED_ROM, GameKind.GLAZED, "MAP", art = true)
    @Test fun imperiumParty() = retail("imperium", NATIVE_IMPERIUM, IMPERIUM_ROM, GameKind.IMPERIUM, "PARTY", art = true)
    @Test fun imperiumItems() = retail("imperium", NATIVE_IMPERIUM, IMPERIUM_ROM, GameKind.IMPERIUM, "ITEMS", art = true)
    @Test fun imperiumMap() = retail("imperium", NATIVE_IMPERIUM, IMPERIUM_ROM, GameKind.IMPERIUM, "MAP", art = true)
    @Test fun imperiumBattle() = retail("imperium_battle", NATIVE_IMPERIUM, IMPERIUM_ROM, GameKind.IMPERIUM, "BATTLE", art = true)
    @Test fun quetzalParty() = retail("quetzal", NATIVE_QUETZAL, QUETZAL_ROM, GameKind.QUETZAL, "PARTY", art = true)
    @Test fun quetzalItems() = retail("quetzal", NATIVE_QUETZAL, QUETZAL_ROM, GameKind.QUETZAL, "ITEMS", art = true)
    @Test fun quetzalMap() = retail("quetzal", NATIVE_QUETZAL, QUETZAL_ROM, GameKind.QUETZAL, "MAP", art = true)
    @Test fun quetzalBattle() = retail("quetzal_battle", NATIVE_QUETZAL, QUETZAL_ROM, GameKind.QUETZAL, "BATTLE", art = true)
    @Test fun glazedDex() = retail("glazed", NATIVE_GLAZED, GLAZED_ROM, GameKind.GLAZED, "DEX", art = true)
    @Test fun glazedDexEntry() = retail("glazed", NATIVE_GLAZED, GLAZED_ROM, GameKind.GLAZED, "DEX", entry = 322, art = true)
    @Test fun imperiumDex() = retail("imperium", NATIVE_IMPERIUM, IMPERIUM_ROM, GameKind.IMPERIUM, "DEX", art = true)
    @Test fun imperiumDexEntry() = retail("imperium", NATIVE_IMPERIUM, IMPERIUM_ROM, GameKind.IMPERIUM, "DEX", entry = 4, art = true)
    @Test fun quetzalDex() = retail("quetzal", NATIVE_QUETZAL, QUETZAL_ROM, GameKind.QUETZAL, "DEX", art = true)
    @Test fun quetzalDexEntry() = retail("quetzal", NATIVE_QUETZAL, QUETZAL_ROM, GameKind.QUETZAL, "DEX", entry = 4, art = true)
    @Test fun glazedCard() = retail("glazed", NATIVE_GLAZED, GLAZED_ROM, GameKind.GLAZED, "CARD", art = true)
    @Test fun imperiumCard() = retail("imperium", NATIVE_IMPERIUM, IMPERIUM_ROM, GameKind.IMPERIUM, "CARD", art = true)
    @Test fun tmt2Battle() = retail("tmt2_battle", NATIVE_TMT2, "Pokemon Too Many Types 2 (v1.5.2).gba", GameKind.TMT2, "BATTLE", art = true)
    @Test fun amethystBattle() = retail("amethyst_battle", NATIVE_AMETHYST, "Pokemon Amethyst (v1.3.0).gba", GameKind.AMETHYST, "BATTLE", art = true)
    @Test fun heartAndSoulMap() = retail("heart_and_soul", NATIVE_HEART_AND_SOUL, "Pokémon Heart and Soul (v2.0.6).gba", GameKind.HEART_AND_SOUL, "MAP", art = true)
    @Test fun emeraldRogueDex() = hackDex("emerald_rogue", NATIVE_EMERALD_ROGUE, "Pokemon Emerald Rogue (v2.2.1-EX).gba", GameKind.EMERALD_ROGUE)
    @Test fun emeraldRogueDexEntry() = hackDex("emerald_rogue", NATIVE_EMERALD_ROGUE, "Pokemon Emerald Rogue (v2.2.1-EX).gba", GameKind.EMERALD_ROGUE, 744)
    @Test fun imperiumGuideBoss() = retailGuide("imperium", NATIVE_IMPERIUM, IMPERIUM_ROM, "NEXT BOSS", kind = GameKind.IMPERIUM)
    @Test fun quetzalGuideBoss() = retailGuide("quetzal", NATIVE_QUETZAL, QUETZAL_ROM, "NEXT BOSS", kind = GameKind.QUETZAL)
    @Test fun quetzalGuideHere() = retailGuide("quetzal", NATIVE_QUETZAL, QUETZAL_ROM, "HERE", kind = GameKind.QUETZAL)
    @Test fun lazarusGuideBoss() = retailGuide("lazarus", NATIVE_LAZARUS, LAZARUS_ROM, "NEXT BOSS", kind = GameKind.LAZARUS)
    @Test fun seaglassGuideBoss() = retailGuide("emerald_seaglass", NATIVE_EMERALD_SEAGLASS, SEAGLASS_ROM, "NEXT BOSS", kind = GameKind.EMERALD_SEAGLASS)
    @Test fun tmt2GuideBoss() = retailGuide("tmt2", NATIVE_TMT2, TMT2_ROM, "NEXT BOSS", kind = GameKind.TMT2)
    @Test fun tmt2GuideHere() = retailGuide("tmt2", NATIVE_TMT2, TMT2_ROM, "HERE", kind = GameKind.TMT2)
    @Test fun soulGoldGuideBoss() = retailGuide("soulgold", NATIVE_SOULGOLD, SG_ROM, "NEXT BOSS", kind = GameKind.SOULGOLD)
    @Test fun soulGoldGuideHere() = retailGuide("soulgold", NATIVE_SOULGOLD, SG_ROM, "HERE", kind = GameKind.SOULGOLD)
    @Test fun glazedGuideBoss() = retailGuide("glazed", NATIVE_GLAZED, GLAZED_ROM, "NEXT BOSS", kind = GameKind.GLAZED)
    @Test fun roweParty() = retail("rowe", NATIVE_ROWE, ROWE_ROM, GameKind.ROWE, "PARTY", art = true)
    @Test fun roweItems() = retail("rowe", NATIVE_ROWE, ROWE_ROM, GameKind.ROWE, "ITEMS", art = true)
    @Test fun roweMap() = retail("rowe", NATIVE_ROWE, ROWE_ROM, GameKind.ROWE, "MAP", art = true)
    @Test fun roweBattle() = retail("rowe_battle", NATIVE_ROWE, ROWE_ROM, GameKind.ROWE, "BATTLE", art = true)
    @Test fun roweDexEntry() = retail("rowe", NATIVE_ROWE, ROWE_ROM, GameKind.ROWE, "DEX", entry = 884, art = true)
    @Test fun glazedBattle() = retail("glazed_battle", NATIVE_GLAZED, GLAZED_ROM, GameKind.GLAZED, "BATTLE", art = true)
    @Test fun yellowParty() = yellow("yellow", "PARTY")
    @Test fun yellowItems() = yellow("yellow", "ITEMS")
    @Test fun yellowBattle() = yellow("yellow_battle", "BATTLE")
    @Test fun yellowBattleMenu() = yellow("yellow_battle_menu", "BATTLE", controls = true)
    @Test fun yellowBattleMoves() = yellow("yellow_battle_move2", "BATTLE", controls = true)
    @Test fun yellowMap() = yellow("yellow", "MAP")
    @Test fun yellowSettings() = yellow("yellow", "SETTINGS")
    @Test fun yellowDex() = yellow("yellow", "DEX")
    @Test fun yellowStates() = yellow("yellow", "STATES")
    @Test fun yellowTweaks() = yellow("yellow", "SETTINGS", page = "TWEAKS")
    @Test fun yellowDexEntry() = yellow("yellow", "DEX", dexEntry = 25)
    @Test fun soulGoldV12Party() = retail("soulgold_v12_battle", NATIVE_SOULGOLD_V1_2, SG12_ROM, GameKind.SOULGOLD, "PARTY", art = true)
    @Test fun soulGoldV12Items() = retail("soulgold_v12", NATIVE_SOULGOLD_V1_2, SG12_ROM, GameKind.SOULGOLD, "ITEMS", art = true)
    @Test fun soulGoldV12Battle() = retail("soulgold_v12_battle", NATIVE_SOULGOLD_V1_2, SG12_ROM, GameKind.SOULGOLD, "BATTLE", art = true)
    @Test fun soulGoldV12Map() = retail("soulgold_v12", NATIVE_SOULGOLD_V1_2, SG12_ROM, GameKind.SOULGOLD, "MAP", art = true)
    @Test fun soulGoldV12DexEntry() = hackDex("soulgold_v12", NATIVE_SOULGOLD_V1_2, SG12_ROM, GameKind.SOULGOLD, 25)
    @Test fun soulGoldV12bParty() = retail("soulgold_v12b_battle", NATIVE_SOULGOLD_V1_2B, SG12B_ROM, GameKind.SOULGOLD, "PARTY", art = true)
    @Test fun soulGoldV12bItems() = retail("soulgold_v12b", NATIVE_SOULGOLD_V1_2B, SG12B_ROM, GameKind.SOULGOLD, "ITEMS", art = true)
    @Test fun soulGoldV12bDexEntry() = hackDex("soulgold_v12b", NATIVE_SOULGOLD_V1_2B, SG12B_ROM, GameKind.SOULGOLD, 25)
    @Test fun soulGoldDex() = hackDex("soulgold", NATIVE_SOULGOLD, SG_ROM, GameKind.SOULGOLD)
    @Test fun soulGoldDexEntry() = hackDex("soulgold", NATIVE_SOULGOLD, SG_ROM, GameKind.SOULGOLD, 155)

    /** Pokémon Yellow (Game Boy): the Gen 1 reader on a GB fixture (wram.bin). */
    private fun yellow(fixture: String, tab: String, controls: Boolean = false, dexEntry: Int? = null, statusBar: Boolean = false, page: String? = null) {
        activeGame = GameKind.YELLOW
        romLanguage = 'E'   // another test's ROM may have left Japanese names on
        romGameCode = ""
        val rom = java.io.File(System.getProperty("user.home"), "Downloads/gbc/Pokemon-Yellow Version.gbc")
        if (rom.isFile) com.pokedaisy.app.companion.data.Gen1Art.extractTo(rom.readBytes(), RomArt.dirOverride!!)
        com.pokedaisy.app.companion.data.Gen1Dex.rom = if (rom.isFile) rom.readBytes() else null
        val t = com.pokedaisy.app.companion.data.readGen1Telemetry(FixtureMemoryReader.load(fixture), com.pokedaisy.app.companion.data.GEN1_YELLOW)
        val v = buildSnapshotView(t).copy(game = GameKind.YELLOW)
        // The icon sheets load asynchronously in the app; decode them before the snapshot.
        (v.party + v.battlePlayer + v.battleOpponent).forEach { m -> m.iconAsset?.let { GameArt.get(paparazzi.context, it) } }
        // The touch controls (FIGHT / moves) show with a battle input to drive.
        val input = if (!controls) null else object : com.pokedaisy.app.companion.BattleInput {
            override val busy = false
            override fun selectAction(actionIndex: Int) {}
            override fun selectMove(moveIndex: Int) {}
            override fun back() {}
        }
        val dex = v.pokedex
        if (dex != null) (1..12).forEach { PokedexSource.entry(dex.tables, it); PokedexSource.frontSprite(dex.tables, it) }
        if (dexEntry != null && dex != null) {
            PokedexSource.entry(dex.tables, dexEntry); PokedexSource.frontSprite(dex.tables, dexEntry)
            paparazzi.snapshot {
                QolTheme {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.pokedaisy.app.companion.ui.theme.LocalGameFont provides com.pokedaisy.app.companion.ui.theme.rememberGameFont(GameKind.YELLOW),
                        com.pokedaisy.app.companion.ui.theme.LocalGameTextScale provides com.pokedaisy.app.companion.ui.theme.gameTextScale(GameKind.YELLOW),
                        com.pokedaisy.app.companion.ui.theme.LocalGameFontWidth provides com.pokedaisy.app.companion.ui.theme.gameFontWidth(GameKind.YELLOW),
                    ) {
                        val ui = DexUiState(androidx.compose.foundation.lazy.rememberLazyListState())
                        ui.open = dexEntry
                        PokedexEntryScreen(dex, ui, dexEntry)
                    }
                }
            }
            return
        }
        paparazzi.snapshot {
            CompanionScreen(
                v, SampleCompanion.Slots, SampleCompanion.Settings(), initialTab = tab, battleInput = input, initialSettingsPage = page,
                statusBar = if (!statusBar) null else ({ GameStatusBar("Pokémon Yellow", v.location.mapSecName, 3175, "12:34") }),
            )
        }
    }

    private fun retail(
        fixture: String, cfg: NativeConfig, romFile: String, kind: GameKind, tab: String, entry: Int? = null, art: Boolean = false,
    ) {
        val rom = RomFileReader.load(RETAIL_ROM_DIR + romFile) ?: return
        if (art) RomArt.extractTo(java.io.File(RETAIL_ROM_DIR + romFile).readBytes(), RomArt.dirOverride!!)
        activeGame = kind
        amethystV141 = cfg === NATIVE_AMETHYST_V1_4_1 // the Poller sets these with activeGame
        romLanguage = cfg.language
        romGameCode = cfg.gameCode
        resetNativeBagCache()
        val t = readNativeTelemetry(rom.withRam(FixtureMemoryReader.load(fixture)), cfg)
        val v = buildSnapshotView(t).copy(game = kind)
        PokedexSource.reader = rom
        DecompIconSource.reader = rom
        DecompIconSource.tables = cfg.iconTables
        val dex = v.pokedex
        (v.party.map { it.species } + t.battleMons.map { it.species }.filter { it != 0 } +
            (if (dex == null) emptyList() else (1..10).map { PokedexSource.speciesFor(dex.tables, it) } +
                (PokedexSource.regionalOrder(dex.tables) ?: IntArray(0)).take(10).map { PokedexSource.speciesFor(dex.tables, it) }))
            .forEach { DecompIconSource.get(it) }
        v.items.forEach { DecompIconSource.getItem(it.itemId) }
        if (entry != null && dex != null) {
            val species = PokedexSource.speciesFor(dex.tables, entry)
            PokedexSource.entry(dex.tables, entry); PokedexSource.frontSprite(dex.tables, species); PokedexSource.footprint(dex.tables, species)
        }
        paparazzi.snapshot {
            if (entry == null) CompanionScreen(v, SampleCompanion.Slots, SampleCompanion.Settings(), initialTab = tab)
            else QolTheme {
                val ui = DexUiState(androidx.compose.foundation.lazy.rememberLazyListState())
                ui.open = entry
                PokedexEntryScreen(dex!!, ui, entry)
            }
        }
    }

    // The ROM hacks' DEX: each `<game>_dex` fixture's real flags (see
    // HackPokedexTest) with the hack's own ROM tables - the list, and one entry.
    @Test fun odysseyDexEntry() = hackDex("odyssey_dex", NATIVE_ODYSSEY, "Pokémon Odyssey (English) (v4.1.1).gba", GameKind.ODYSSEY, 387)
    @Test fun radicalRedDexEntry() = hackDex("radical_red_dex", NATIVE_RADICAL_RED_V4_1, "Pokemon - Radical Red (v4.1).gba", GameKind.RADICAL_RED, 35)
    @Test fun amethystDex() = hackDex("amethyst_dex", NATIVE_AMETHYST, "Pokemon Amethyst (v1.3.0).gba", GameKind.AMETHYST)
    @Test fun amethystDexEntry() = hackDex("amethyst_dex", NATIVE_AMETHYST, "Pokemon Amethyst (v1.3.0).gba", GameKind.AMETHYST, 340)
    @Test fun amethystParty() = retail("amethyst", NATIVE_AMETHYST, "Pokemon Amethyst (v1.3.0).gba", GameKind.AMETHYST, "PARTY", art = true)
    @Test fun amethystMap() = retail("amethyst", NATIVE_AMETHYST, "Pokemon Amethyst (v1.3.0).gba", GameKind.AMETHYST, "MAP", art = true)
    @Test fun amethystV141Dex() = hackDex("amethyst_v141", NATIVE_AMETHYST_V1_4_1, AM141_ROM, GameKind.AMETHYST)
    @Test fun amethystV141Party() = retail("amethyst_v141", NATIVE_AMETHYST_V1_4_1, AM141_ROM, GameKind.AMETHYST, "PARTY", art = true)
    @Test fun amethystV141Items() = retail("amethyst_v141", NATIVE_AMETHYST_V1_4_1, AM141_ROM, GameKind.AMETHYST, "ITEMS", art = true)
    @Test fun amethystV141Map() = retail("amethyst_v141", NATIVE_AMETHYST_V1_4_1, AM141_ROM, GameKind.AMETHYST, "MAP", art = true)
    @Test fun amethystV141DexEntry() = hackDex("amethyst_v141", NATIVE_AMETHYST_V1_4_1, AM141_ROM, GameKind.AMETHYST, 340)
    // The European Emeralds: English's save (emerald_<lang>) with each game's own names and party art.
    @Test fun emeraldDeParty() = retail("emerald_de", NATIVE_EMERALD_DE, EM_DE_ROM, GameKind.EMERALD, "PARTY", art = true)
    @Test fun emeraldDeBattle() = retail("emerald_de_battle", NATIVE_EMERALD_DE, EM_DE_ROM, GameKind.EMERALD, "BATTLE", art = true)
    @Test fun emeraldDeItems() = retail("emerald_de", NATIVE_EMERALD_DE, EM_DE_ROM, GameKind.EMERALD, "ITEMS", art = true)
    @Test fun emeraldDeMap() = retail("emerald_de", NATIVE_EMERALD_DE, EM_DE_ROM, GameKind.EMERALD, "MAP", art = true)
    @Test fun emeraldDeDexEntry() = retail("emerald_de", NATIVE_EMERALD_DE, EM_DE_ROM, GameKind.EMERALD, "DEX", entry = 255)
    @Test fun emeraldDeGuideHere() =
        retailGuide("emerald_de", NATIVE_EMERALD_DE, EM_DE_ROM, "HERE", kind = GameKind.EMERALD)
    @Test fun emeraldEsParty() = retail("emerald_es", NATIVE_EMERALD_ES, EM_ES_ROM, GameKind.EMERALD, "PARTY", art = true)
    @Test fun emeraldEsDexEntry() = retail("emerald_es", NATIVE_EMERALD_ES, EM_ES_ROM, GameKind.EMERALD, "DEX", entry = 255)
    @Test fun emeraldFrParty() = retail("emerald_fr", NATIVE_EMERALD_FR, EM_FR_ROM, GameKind.EMERALD, "PARTY", art = true)
    @Test fun emeraldFrItems() = retail("emerald_fr", NATIVE_EMERALD_FR, EM_FR_ROM, GameKind.EMERALD, "ITEMS", art = true)
    @Test fun emeraldItParty() = retail("emerald_it", NATIVE_EMERALD_IT, EM_IT_ROM, GameKind.EMERALD, "PARTY", art = true)
    // Japanese Emerald: kana names, the app's own party slot.
    @Test fun emeraldJaParty() = retail("emerald_ja", NATIVE_EMERALD_JA, EM_JA_ROM, GameKind.EMERALD, "PARTY", art = true)
    @Test fun emeraldJaItems() = retail("emerald_ja", NATIVE_EMERALD_JA, EM_JA_ROM, GameKind.EMERALD, "ITEMS", art = true)
    @Test fun emeraldJaMap() = retail("emerald_ja", NATIVE_EMERALD_JA, EM_JA_ROM, GameKind.EMERALD, "MAP", art = true)
    @Test fun emeraldJaBattle() = retail("emerald_ja_battle", NATIVE_EMERALD_JA, EM_JA_ROM, GameKind.EMERALD, "BATTLE", art = true)
    @Test fun emeraldJaDexEntry() = retail("emerald_ja", NATIVE_EMERALD_JA, EM_JA_ROM, GameKind.EMERALD, "DEX", entry = 255)
    @Test fun emeraldJaGuideHere() = retailGuide("emerald_ja", NATIVE_EMERALD_JA, EM_JA_ROM, "HERE", kind = GameKind.EMERALD)
    @Test fun emeraldJaGuideNextBoss() = retailGuide("emerald_ja", NATIVE_EMERALD_JA, EM_JA_ROM, "NEXT BOSS", kind = GameKind.EMERALD)
    // scripts/port_retail.py's ports (RETAIL_PORTS): the port_* fixtures, each game's own names and art.
    @Test fun portFireRedDeParty() = retail("port_bprd0", port("BPRD0"), ML_FR_DE, GameKind.FIRERED, "PARTY", art = true)
    @Test fun portFireRedDeDexEntry() = retail("port_bprd0", port("BPRD0"), ML_FR_DE, GameKind.FIRERED, "DEX", entry = 6)
    @Test fun portLeafGreenEsItems() = retail("port_bpgs0", port("BPGS0"), ML_LG_ES, GameKind.FIRERED, "ITEMS", art = true)
    @Test fun portSapphireFrDexEntry() = retail("port_axpf1", port("AXPF1"), ML_SA_FR, GameKind.EMERALD, "DEX", entry = 321)
    @Test fun portRubyJaParty() = retail("port_axvj1", port("AXVJ1"), ML_RU_JA, GameKind.EMERALD, "PARTY", art = true)
    @Test fun portFireRedJaParty() = retail("port_bprj1", port("BPRJ1"), ML_FR_JA, GameKind.FIRERED, "PARTY", art = true)
    @Test fun portFireRedJaItems() = retail("port_bprj1", port("BPRJ1"), ML_FR_JA, GameKind.FIRERED, "ITEMS", art = true)
    @Test fun portFireRedJaDexEntry() = retail("port_bprj1", port("BPRJ1"), ML_FR_JA, GameKind.FIRERED, "DEX", entry = 6)
    @Test fun portRubyJaMap() = retail("port_axvj1", port("AXVJ1"), ML_RU_JA, GameKind.EMERALD, "MAP", art = true)
    @Test fun portRubyJaDex() = retail("port_axvj1", port("AXVJ1"), ML_RU_JA, GameKind.EMERALD, "DEX")
    @Test fun portRubyEnDex() = retail("port_en_axve1", com.pokedaisy.app.companion.data.NATIVE_RUBY, ML + "Pokemon - Ruby Version (USA, Europe) (Rev 1)/Pokemon - Ruby Version (USA, Europe) (Rev 1).gba", GameKind.EMERALD, "DEX")
    @Test fun portRubyJaBattle() = retail("port_axvj1/battle", port("AXVJ1"), ML_RU_JA, GameKind.EMERALD, "BATTLE", art = true)
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
        amethystV141 = cfg === NATIVE_AMETHYST_V1_4_1 // the Poller sets these with activeGame
        romLanguage = cfg.language
        romGameCode = cfg.gameCode
        val t = cfg.pokedex!!
        PokedexSource.reader = rom // before the flags: Rogue's are by species, read through the ROM
        val dex = readPokedexState(rom.withRam(FixtureMemoryReader.load(fixture)), cfg, t)!!
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
                PokedexEntryScreen(dex!!, ui, entry)
            }
        }
    }

    @Test fun map() = tab("MAP")
    @Test fun items() = tab("ITEMS")

    /** A Repel's description with USE (FieldItems.kt). */
    @Test fun itemsRepelUse() = paparazzi.snapshot {
        activeGame = GameKind.EMERALD
        val use = object : com.pokedaisy.app.companion.ItemUse {
            override fun canUse(itemId: Int) = itemId in com.pokedaisy.app.companion.data.REPEL_ITEMS
            override fun use(itemId: Int, done: (com.pokedaisy.app.companion.data.ItemUseOutcome) -> Unit) {}
        }
        val items = listOf(
            ItemView(86, "Repel", 3, spriteAsset("items", 86), description = "Prevents weak wild POKéMON from appearing for 100 steps."),
            ItemView(13, "Potion", 2, spriteAsset("items", 13)),
        )
        QolTheme { ItemsScreen(items, androidx.compose.ui.Modifier.fillMaxSize(), itemUse = use, initialItemId = 86) }
    }

    /** Two stacks of one item (99 Repels and 5 more): LazyColumn used to throw on the repeated key. */
    @Test fun itemsDuplicateStacks() = paparazzi.snapshot {
        val stacks = listOf(
            ItemView(86, "Repel", 99, null, POCKET_ITEMS), ItemView(86, "Repel", 5, null, POCKET_ITEMS),
            ItemView(4, "Poké Ball", 99, null, POCKET_POKE_BALLS), ItemView(4, "Poké Ball", 23, null, POCKET_POKE_BALLS),
        )
        CompanionScreen(
            SampleCompanion.snapshot.copy(items = stacks + SampleCompanion.snapshot.items),
            SampleCompanion.Slots, SampleCompanion.Settings(), initialTab = "ITEMS",
        )
    }
    @Test fun states() = tab("STATES")
    @Test fun settings() = tab("SETTINGS")

    /** SETTINGS > CHEATS: the master switch and a game's three cheats, then a game with none. */
    @Test fun settingsCheats() = paparazzi.snapshot {
        CompanionScreen(SampleCompanion.snapshot, SampleCompanion.Slots, SampleCompanion.Settings(cheatList = SampleCompanion.cheats), initialTab = "SETTINGS", initialSettingsPage = "CHEATS")
    }

    @Test fun settingsCheatsEmpty() = paparazzi.snapshot {
        CompanionScreen(SampleCompanion.snapshot, SampleCompanion.Slots, SampleCompanion.Settings(), initialTab = "SETTINGS", initialSettingsPage = "CHEATS")
    }

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
private const val RR_ROM = "1636 - Pokemon Radical Red.gba"
private const val SG_ROM = "Pokemon-SoulGold-v1.1.4.gba"
private const val SG12_ROM = "Soulgold (v1.2).gba"
private const val SG12B_ROM = "Pokemon-SoulGold-v1.2.gba"
private const val AM141_ROM = "Pokemon Amethyst (v1.4.1).gba"
private const val EM_ES_ROM = "emerald-multilang/Pokemon - Edicion Esmeralda (Spain).gba"
private const val EM_DE_ROM = "emerald-multilang/Pokemon - Smaragd-Edition (Germany).gba"
private const val EM_FR_ROM = "emerald-multilang/Pokemon - Version Emeraude (France).gba"
private const val EM_IT_ROM = "emerald-multilang/Pokemon - Versione Smeraldo (Italy).gba"
private const val EM_JA_ROM = "emerald-multilang/Pocket Monsters - Emerald (Japan).gba"
// The other-language FireRed / LeafGreen / Ruby / Sapphire ROMs, beside the retail dir.
private const val ML = "../../Pokemon Multi Language/"
private const val ML_FR_DE = ML + "Pokemon - Feuerrote Edition (Germany)/Pokemon - Feuerrote Edition (Germany).gba"
private const val ML_LG_ES = ML + "Pokemon - Edicion Verde Hoja (Spain)/Pokemon - Edicion Verde Hoja (Spain).gba"
private const val ML_SA_FR = ML + "Pokemon - Version Saphir (France) (Rev 1)/Pokemon - Version Saphir (France) (Rev 1).gba"
private const val ML_FR_JA = ML + "Pocket Monsters - FireRed (Japan) (Rev 1)/Pocket Monsters - FireRed (Japan) (Rev 1).gba"
private const val ML_RU_JA = ML + "Pocket Monsters - Ruby (Japan) (Rev 1)/Pocket Monsters - Ruby (Japan) (Rev 1).gba"
private fun port(key: String) = com.pokedaisy.app.companion.data.RETAIL_PORTS.getValue(key)()
private const val SEAGLASS_ROM = "Pokemon Emerald Seaglass (v3.0).gba"
private const val LAZARUS_ROM = "Pokemon Lazarus (v2.0).gba"
private const val TMT2_ROM = "Pokemon Too Many Types 2 (v1.5.2).gba"
private const val GLAZED_ROM = "Glazed (9.2.0).gba"
private const val IMPERIUM_ROM = "Emerald Imperium (v1.3.1).gba"
private const val QUETZAL_ROM = "PokemonQuetzalEnglishAlpha9v0.gba"
private const val ROWE_ROM = "Pokémon R.O.W.E. (v2.1.9.1 Experimental).gba"
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
                // Adamant; the stats are what these IVs / EVs give at Lv 12.
                stats = com.pokedaisy.app.companion.data.MonStats(
                    ivs = listOf(28, 31, 9, 28, 17, 30), evs = listOf(12, 40, 6, 30, 18, 4), nature = 3,
                    stats = listOf(35, 24, 16, 24, 18, 20),
                ),
            ),
            MonView(
                16, "PIDGEY", 9, 5, 26, "PSN", listOf("Normal", "Flying"), null, GENDER_SYMBOL_FEMALE,
                moves = listOf(MoveView("Tackle", "Normal", 35, 35), MoveView("Sand-Attack", "Ground", 15, 0), MoveView("Gust", "Flying", 35, 40)),
                weaknesses = listOf(TypeMatchup("Electric", "2x", 200), TypeMatchup("Ice", "2x", 200), TypeMatchup("Rock", "2x", 200)),
                resistances = listOf(TypeMatchup("Grass", "½x", 50), TypeMatchup("Bug", "½x", 50)),
                immunities = listOf(TypeMatchup("Ground", "0x", 0), TypeMatchup("Ghost", "0x", 0)),
                // Timid, a perfect Speed IV.
                stats = com.pokedaisy.app.companion.data.MonStats(
                    ivs = listOf(5, 12, 20, 31, 3, 18), evs = List(6) { 0 }, nature = 10,
                    stats = listOf(26, 12, 14, 18, 11, 12),
                ),
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

    class Achievements(
        s: com.pokedaisy.app.companion.AchievementsState,
        page: com.pokedaisy.app.companion.LeaderboardPage? = null,
    ) : com.pokedaisy.app.companion.CompanionAchievements {
        override val state = kotlinx.coroutines.flow.MutableStateFlow(s)
        override val leaderboardPage = kotlinx.coroutines.flow.MutableStateFlow(page)
        override val popups = kotlinx.coroutines.flow.emptyFlow<com.pokedaisy.app.companion.AchievementPopup>()
        override fun retry() {}
    }

    val achievementsState = run {
        fun a(id: Int, title: String, desc: String, pts: Int, unlocked: Boolean, section: String, progress: String = "", pct: Float = 0f) =
            com.pokedaisy.app.companion.Achievement(id, title, desc, pts, unlocked, section, null, null, progress, pct)
        val list = listOf(
            a(1, "Boulder Badge", "Defeat Brock in Pewter City", 5, true, "RECENTLY UNLOCKED"),
            a(2, "Gotta Catch 'Em", "Register 50 Pokémon in the Pokédex", 10, false, "ALMOST THERE", "38/50", 76f),
            a(3, "Cascade Badge", "Defeat Misty in Cerulean City", 5, false, "LOCKED"),
            a(4, "Snorlax Awakens", "Wake up a Snorlax with the Poké Flute and catch it", 10, false, "LOCKED"),
            a(5, "The Champion", "Defeat the Pokémon League Champion and enter the Hall of Fame", 25, false, "LOCKED"),
            a(6, "Starter", "Choose your first Pokémon", 1, true, "UNLOCKED"),
            a(7, "Rival Beaten", "Win your first battle against your rival", 3, true, "UNLOCKED"),
        )
        com.pokedaisy.app.companion.AchievementsState(
            user = "Lidor",
            game = com.pokedaisy.app.companion.AchievementGame(515, "Pokemon FireRed Version", "51901a6e40661b3914aa333c802e24e8", 60, 3, 600, 9),
            achievements = list,
            leaderboards = listOf(
                com.pokedaisy.app.companion.Leaderboard(11, "Elite Four Speedrun", "Fastest time from entering the League to the Hall of Fame", "POKEMON FIRERED VERSION", true),
                com.pokedaisy.app.companion.Leaderboard(12, "Fewest Steps to Pewter", "Reach Pewter City in the fewest steps", "POKEMON FIRERED VERSION", true),
                com.pokedaisy.app.companion.Leaderboard(13, "Safari Zone Haul", "Most Pokemon caught in one Safari Zone visit", "BONUS", false),
            ),
        )
    }

    val leaderboardPage = run {
        fun e(rank: Int, user: String, score: String, me: Boolean = false) = com.pokedaisy.app.companion.LeaderboardEntry(rank, user, score, me)
        com.pokedaisy.app.companion.LeaderboardPage(
            id = 11, loading = false, total = 214,
            top = listOf(e(1, "Speedy", "12:04.33"), e(2, "AshK", "12:58.10"), e(3, "MistyFan", "13:21.47"), e(4, "Gary0ak", "14:02.95"), e(5, "Brock", "14:40.12")),
            nearMe = listOf(e(16, "Nurse_Joy", "15:11.68"), e(17, "Lidor", "15:30.02", me = true), e(18, "Oddish", "15:44.90")),
        )
    }

    object Slots : StateSlots {
        override fun list() = (0..9).map { StateSlots.Slot(it, false, 0L, null) }
        override val currentIndex = 1
        override fun requestSave(index: Int) {}
        override fun requestLoad(index: Int) {}
        override fun requestUndoSave() {}
        override fun requestUndoLoad() {}
    }

    /** A typed code and two from a RetroArch .cht. */
    val cheats = listOf(
        com.pokedaisy.app.cheats.Cheat("INFINITE MONEY", listOf("82025838 FFFF"), "", true),
        com.pokedaisy.app.cheats.Cheat("Master Code (must be on)", listOf("000014D1 000A", "1003DBB8 0007"), "", false),
        com.pokedaisy.app.cheats.Cheat("Wild Pokemon are always shiny", listOf("12345678 9ABCDEF0"), "GSAv1", false),
    )

    class Settings(
        private var tabs: List<String> = com.pokedaisy.app.companion.DEFAULT_COMPANION_TABS,
        private var cheatList: List<com.pokedaisy.app.cheats.Cheat> = emptyList(),
    ) : CompanionSettings {
        override val cheats get() = cheatList
        override val cheatsEnabled get() = cheatList.isNotEmpty()
        override val companionTabs get() = tabs
        override fun setCompanionTabs(tabs: List<String>) { this.tabs = tabs }
        var noticeAccepted = true
        override fun guideNoticeAccepted(game: String) = noticeAccepted
        override fun acceptGuideNotice(game: String) { noticeAccepted = true }
        override val ffMaxSpeed = 4f
        override fun setFfMaxSpeed(v: Float) {}
        override val ffToggled = false
        override fun setFfToggled(on: Boolean) {}
        override val ffMusicMode = com.pokedaisy.app.companion.FfMusicMode.STEADY
        override fun setFfMusicMode(mode: com.pokedaisy.app.companion.FfMusicMode) {}
        override val ffMode = com.pokedaisy.app.companion.FfMode.SMART
        override fun setFfMode(mode: com.pokedaisy.app.companion.FfMode) {}
        override val touchControlsMode = 0
        override fun setTouchControlsMode(v: Int) {}
        override fun gbaControlBindings() = GbaControls.Btn.entries.associateWith {
        listOf(when (it) { GbaControls.Btn.START2 -> "BUTTON_X"; GbaControls.Btn.SELECT2 -> "BUTTON_Y"; else -> "BUTTON_${it.name}" })
    }
        override fun setGbaControlBinding(btn: GbaControls.Btn, keyName: String) {}
        override val hotkeysEnabled = true
        override fun setHotkeysEnabled(on: Boolean) {}
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
        override val stretchGame = false
        override fun setStretchGame(on: Boolean) {}
        override val gbaColors = false
        override fun setGbaColors(on: Boolean) {}
        override val screenFilter = com.pokedaisy.app.companion.ScreenFilter.NONE
        override fun setScreenFilter(filter: com.pokedaisy.app.companion.ScreenFilter) {}
        override val gridStrength = com.pokedaisy.app.companion.GridStrength.MEDIUM
        override fun setGridStrength(strength: com.pokedaisy.app.companion.GridStrength) {}
        override val companionShaders = true
        override fun setCompanionShaders(on: Boolean) {}
        override val gameName = "Pokémon FireRed"
        override val romFileName = "firered-qol.gba"
    }
}
