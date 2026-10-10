package com.pokedaisy.app.companion.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pokedaisy.app.companion.data.GameKind
import com.pokedaisy.app.companion.ui.GameArt
import com.pokedaisy.app.companion.ui.PixelRoundedShape
import com.pokedaisy.app.companion.ui.rememberArtGeneration
import com.pokedaisy.app.companion.ui.soundClickable

/**
 * A named "chrome" palette — one of 9 selectable reskins (PokéDaisy default, then
 * FireRed and 7 more named after mainline Pokémon versions). Only the
 * identity/chrome colors vary per theme; semantic colors (HP/status/type, cream
 * panel, dark text) stay constant across all of them so contrast and meaning
 * never break. Picked by [id] (stored in Prefs.appTheme), not list position.
 */
data class ThemeSpec(
    val id: Int,
    val label: String,
    val bgTop: Color,
    val bgBottom: Color,
    val windowFrame: Color,
    val windowInner: Color,
    val windowGold: Color,
    val accent: Color,
    val slotFill: Color,
    val slotFillDark: Color,
    val slotFrame: Color,
    val slotInner: Color,
)

/** [ThemeSpec.id] of the PokéDaisy theme, the default: the website's look (see [isDaisyTheme]). */
const val DAISY_THEME_ID = 8

/** [ThemeSpec.id] of the FireRed theme (the default before PokéDaisy): FireRed's party-menu stripes. */
const val FIRERED_THEME_ID = 0

/**
 * The PokéDaisy theme draws the top screen (Library, Settings, setup) like the
 * website: light, soft colour, ink-outlined white windows (OptionColors), the
 * drifting backdrop with the logo (AppBackdrop). Its chrome colours here are
 * FireRed's, so what still reads them (the in-game companion's few themed bits)
 * looks as before.
 */
val isDaisyTheme: Boolean get() = QolColors.currentThemeId == DAISY_THEME_ID

/** The theme stored as [id], or the default for an unknown one. */
fun themeById(id: Int): ThemeSpec = APP_THEMES.firstOrNull { it.id == id } ?: APP_THEMES.first()

val APP_THEMES = listOf(
    ThemeSpec(
        DAISY_THEME_ID, "PokéDaisy",
        bgTop = Color(0xFF3AA890), bgBottom = Color(0xFF2C7868),
        windowFrame = Color(0xFF3860A8), windowInner = Color(0xFF98C0F8), windowGold = Color(0xFFF8D060),
        accent = Color(0xFF3860A8),
        slotFill = Color(0xFF48B8A8), slotFillDark = Color(0xFF308878), slotFrame = Color(0xFF206858), slotInner = Color(0xFF90E0D0),
    ),
    ThemeSpec(
        FIRERED_THEME_ID, "FireRed",
        bgTop = Color(0xFF3AA890), bgBottom = Color(0xFF2C7868),
        windowFrame = Color(0xFF3860A8), windowInner = Color(0xFF98C0F8), windowGold = Color(0xFFF8D060),
        accent = Color(0xFF3860A8),
        slotFill = Color(0xFF48B8A8), slotFillDark = Color(0xFF308878), slotFrame = Color(0xFF206858), slotInner = Color(0xFF90E0D0),
    ),
    ThemeSpec(
        1, "LeafGreen",
        bgTop = Color(0xFF4CAA58), bgBottom = Color(0xFF307838),
        windowFrame = Color(0xFF388850), windowInner = Color(0xFFA8E8B0), windowGold = Color(0xFFF8D060),
        accent = Color(0xFF388850),
        slotFill = Color(0xFF68B858), slotFillDark = Color(0xFF488838), slotFrame = Color(0xFF305820), slotInner = Color(0xFFA8E090),
    ),
    ThemeSpec(
        2, "Emerald",
        bgTop = Color(0xFF189878), bgBottom = Color(0xFF106050),
        windowFrame = Color(0xFF189858), windowInner = Color(0xFF90E8B8), windowGold = Color(0xFFF8D060),
        accent = Color(0xFF189858),
        slotFill = Color(0xFF38B888), slotFillDark = Color(0xFF208858), slotFrame = Color(0xFF106040), slotInner = Color(0xFF80E0B0),
    ),
    ThemeSpec(
        3, "Ruby",
        bgTop = Color(0xFFC84850), bgBottom = Color(0xFF983038),
        windowFrame = Color(0xFFC82838), windowInner = Color(0xFFF8A8A8), windowGold = Color(0xFFF8D060),
        accent = Color(0xFFC82838),
        slotFill = Color(0xFFE86858), slotFillDark = Color(0xFFB84838), slotFrame = Color(0xFF802818), slotInner = Color(0xFFF8B098),
    ),
    ThemeSpec(
        4, "Sapphire",
        bgTop = Color(0xFF3868B0), bgBottom = Color(0xFF284878),
        windowFrame = Color(0xFF2858B8), windowInner = Color(0xFFA8C8F8), windowGold = Color(0xFFD8E8F8),
        accent = Color(0xFF2858B8),
        slotFill = Color(0xFF4888D8), slotFillDark = Color(0xFF3068A8), slotFrame = Color(0xFF204878), slotInner = Color(0xFF90B8F0),
    ),
    ThemeSpec(
        5, "Platinum",
        bgTop = Color(0xFF8098B0), bgBottom = Color(0xFF586878),
        windowFrame = Color(0xFF7888A0), windowInner = Color(0xFFD8E0E8), windowGold = Color(0xFFF0F4F8),
        accent = Color(0xFF5878A0),
        slotFill = Color(0xFF98A8C0), slotFillDark = Color(0xFF708098), slotFrame = Color(0xFF506078), slotInner = Color(0xFFC0D0E0),
    ),
    ThemeSpec(
        6, "HeartGold",
        bgTop = Color(0xFFD8A030), bgBottom = Color(0xFF986818),
        windowFrame = Color(0xFFC88820), windowInner = Color(0xFFF8D888), windowGold = Color(0xFFF8E8A0),
        accent = Color(0xFFC87810),
        slotFill = Color(0xFFE8A838), slotFillDark = Color(0xFFB87818), slotFrame = Color(0xFF805008), slotInner = Color(0xFFF8C868),
    ),
    ThemeSpec(
        7, "SoulSilver",
        bgTop = Color(0xFF9888C0), bgBottom = Color(0xFF685890),
        windowFrame = Color(0xFF8878A8), windowInner = Color(0xFFD0C8F0), windowGold = Color(0xFFE0D8F0),
        accent = Color(0xFF7868A0),
        slotFill = Color(0xFFA898D0), slotFillDark = Color(0xFF8078B0), slotFrame = Color(0xFF584878), slotInner = Color(0xFFC8B8F0),
    ),
)

/**
 * FireRed / LeafGreen menu chrome: cream windows with a double frame, teal (or
 * theme-colored) party slots with an orange "selected" border, a beige HP
 * capsule, and a pixel font throughout. Field names kept stable so callers
 * don't churn. The "chrome" fields (bgTop, bgBottom, the window/accent/slot
 * colors) are backed by Compose `State` so picking a theme (see [applyTheme]) recomposes
 * every screen live, app-wide — QolColors is a process-wide singleton, so this
 * reaches the top screen AND the Screen-2 companion (a separate Presentation
 * in the same process) at once.
 */
object QolColors {
    private var theme by mutableStateOf(APP_THEMES[0])

    val bgTop: Color get() = theme.bgTop
    val bgBottom: Color get() = theme.bgBottom
    val windowFrame: Color get() = theme.windowFrame
    val windowInner: Color get() = theme.windowInner
    val windowGold: Color get() = theme.windowGold
    val accent: Color get() = theme.accent
    val slotFill: Color get() = theme.slotFill
    val slotFillDark: Color get() = theme.slotFillDark
    val slotFrame: Color get() = theme.slotFrame
    val slotInner: Color get() = theme.slotInner

    val currentThemeId: Int get() = theme.id
    fun applyTheme(spec: ThemeSpec) { theme = spec }

    // Cream window ("Text box"): fill + double frame. Constant across themes.
    val panel = Color(0xFFF8F8F0)
    val panelAlt = Color(0xFFFFFFF8)
    val borderOuter get() = windowFrame   // legacy alias
    val borderInner get() = windowGold    // legacy alias

    val text = Color(0xFF404850)          // near-black on cream
    val muted = Color(0xFF8890A0)

    // Party slot (see the party-menu screenshot). slotSelected/slotSelectedInner
    // deliberately do NOT vary per theme — "this is the active/chosen item"
    // needs to stay recognizable regardless of the chrome color.
    val slotSelected = Color(0xFFF87838)
    val slotSelectedInner = Color(0xFFFFC098)
    val onSlot = Color(0xFFFCFCFC)
    val onSlotOutline = Color(0xFF404850)

    // HP capsule.
    val hpPill = Color(0xFFE8D8A0)
    val hpPillEdge = Color(0xFF807038)
    val hpTrack = Color(0xFF283830)
    val hpHigh = Color(0xFF60D048)
    val hpMid = Color(0xFFF8C838)
    val hpLow = Color(0xFFF04838)

    // D-pad / keyboard focus ring. Deliberately NOT slotSelected (orange) — that
    // color already means "this is the active/chosen item" (PLAYING row, checked
    // setting, party lead); reusing it for focus made "where's my cursor" and
    // "what's already picked" indistinguishable. Bright cyan reads clearly on
    // both the teal and cream backgrounds and matches nothing else in the app.
    val focusRing = Color(0xFF2EE6FF)

    val statusSlp = Color(0xFF8A8A8A)
    val statusPsn = Color(0xFFA259D9)
    val statusBrn = Color(0xFFE0693E)
    val statusPar = Color(0xFFD9C341)
    val statusFrz = Color(0xFF56C2E6)
    val statusTox = Color(0xFF7A3EA1)

    private val typeColorsTitled = mapOf(
        "Normal" to Color(0xFFA8A878), "Fighting" to Color(0xFFC03028), "Flying" to Color(0xFFA890F0),
        "Poison" to Color(0xFFA040A0), "Ground" to Color(0xFFE0C068), "Rock" to Color(0xFFB8A038),
        "Bug" to Color(0xFFA8B820), "Ghost" to Color(0xFF705898), "Steel" to Color(0xFFB8B8D0),
        "???" to Color(0xFF68A090), "Fire" to Color(0xFFF08030), "Water" to Color(0xFF6890F0),
        "Grass" to Color(0xFF78C850), "Electric" to Color(0xFFF8D030), "Psychic" to Color(0xFFF85888),
        "Ice" to Color(0xFF98D8D8), "Dragon" to Color(0xFF7038F8), "Dark" to Color(0xFF705848),
        "Fairy" to Color(0xFFEE99AC),
    )

    /** By the game's own type name: title-cased ("Fire"), or Gen 1's capitals ("FIRE", and its unused BIRD as Flying). */
    val typeColors = typeColorsTitled + typeColorsTitled.mapKeys { it.key.uppercase() } +
        ("BIRD" to typeColorsTitled.getValue("Flying"))

    fun statusColor(label: String): Color = when (label) {
        "SLP" -> statusSlp; "PSN" -> statusPsn; "BRN" -> statusBrn
        "PAR" -> statusPar; "FRZ" -> statusFrz; "TOX" -> statusTox
        else -> muted
    }

    fun hpColor(hp: Int, maxHp: Int): Color {
        if (maxHp == 0) return hpHigh
        val pct = hp.toFloat() / maxHp
        return when {
            pct > 0.5f -> hpHigh
            pct > 0.2f -> hpMid
            else -> hpLow
        }
    }

    fun multiplierColor(pct: Int): Color = when {
        pct == 0 -> Color(0xFF585048)
        pct > 100 -> hpHigh
        pct < 100 -> hpLow
        else -> muted
    }
}

/**
 * Bundled "Pixel Operator" (CC0, Jayvee Enaguas) - the app's one font. A
 * proportional pixel font close to the GBA games' own text: 16px em with 9px
 * caps, the same proportions as FireRed's menu font. Crisp only at integer
 * multiples of 16px (see [com.pokedaisy.app.companion.ui.GbaTextMetrics]);
 * elsewhere it's slightly soft, which is fine for body text. Its caps are
 * ~0.56em (Press Start 2P's, which it replaced, were ~0.88em), so sizes run
 * ~1.5x what the old font needed for the same visual height.
 */
@Composable
fun pixelFontFamily(): FontFamily {
    LocalGameFont.current?.let { return it }
    val context = LocalContext.current
    // Pixel Operator + its Japanese fallback (PixelTypeface.kt), one per process.
    return com.pokedaisy.app.pixelFontFamily(context)
}

/**
 * The running game's own font, when it has one the app can use instead of Pixel
 * Operator: a Game Boy game's, cut from its ROM ([com.pokedaisy.app.companion.data.Gen1Art]).
 * Provided by CompanionScreen only, so the top-screen Library / Settings keep Pixel Operator.
 */
val LocalGameFont = androidx.compose.runtime.staticCompositionLocalOf<FontFamily?> { null }

/**
 * How much larger than Pixel Operator text [LocalGameFont] is set, so its letters come out
 * as tall: Gen 1's capitals are 7 pixels to Pixel Operator's 9 (rememberGbaTextMetrics still
 * rounds to whole screen pixels per font pixel). 1 with no game font.
 */
val LocalGameTextScale = androidx.compose.runtime.staticCompositionLocalOf { 1f }

/**
 * How wide [LocalGameFont]'s pixels are drawn, relative to their height: Gen 1's letters fill
 * 8px cells, far wider than Pixel Operator's, so they're condensed (GbaText rounds it per size,
 * so a font pixel stays a whole number of screen pixels wide). 1 with no game font.
 */
val LocalGameFontWidth = androidx.compose.runtime.staticCompositionLocalOf { 1f }

/** [LocalGameFontWidth] for [game]'s own font. */
fun gameFontWidth(game: GameKind?): Float = when (game) {
    GameKind.YELLOW -> 0.75f
    else -> 1f
}

/** [LocalGameTextScale] for [game]'s own font. */
fun gameTextScale(game: GameKind?): Float = when (game) {
    GameKind.YELLOW -> 9f / 7f
    else -> 1f
}

/** [game]'s own font from the rom-art cache, once a launch of it has written it; else null. */
@Composable
fun rememberGameFont(game: GameKind?): FontFamily? {
    val context = LocalContext.current
    val gen = com.pokedaisy.app.companion.ui.rememberArtGeneration()
    return remember(game, gen) {
        val path = when (game) {
            GameKind.YELLOW -> com.pokedaisy.app.companion.data.Gen1Art.YELLOW_FONT
            else -> null
        } ?: return@remember null
        com.pokedaisy.app.companion.data.RomArt.file(context.filesDir, path)?.let { com.pokedaisy.app.gameFontFamily(context, it) }
    }
}

@Composable
fun QolTheme(content: @Composable () -> Unit) {
    // Pick up the persisted theme choice ONCE per composition (inside the
    // remember{} block itself, not just the id lookup) — this function reads
    // QolColors.accent/bgBottom/panel/text below to build darkColorScheme(),
    // so it recomposes every time the theme changes, including from a LIVE
    // pick on this exact screen (e.g. SettingsActivity's Theme page calls
    // QolColors.applyTheme() directly for instant feedback). A prior version
    // called applyTheme() unconditionally in the composable body on every
    // recomposition, which re-applied the stale remembered Prefs value and
    // stomped the just-picked theme back to whatever it was on first launch —
    // selecting a theme visibly never stuck. remember{} only runs its block
    // once (until its key changes), so this only "loads from disk" at startup.
    val context = LocalContext.current
    remember(context) {
        // runCatching: previews / screenshot tests may have no real prefs.
        val id = runCatching { com.pokedaisy.app.Prefs(context).appTheme }.getOrDefault(DAISY_THEME_ID)
        QolColors.applyTheme(themeById(id))
        runCatching { DarkMode.load(context) }
    }

    val pixel = pixelFontFamily()
    // lineHeight in em so a Text() that overrides fontSize keeps a
    // proportional line height instead of the style's fixed one.
    fun s(size: Int) = TextStyle(fontFamily = pixel, fontSize = size.sp, lineHeight = 1.2.em)
    val typography = Typography(
        displayLarge = s(30), displayMedium = s(27), displaySmall = s(24),
        headlineLarge = s(24), headlineMedium = s(21), headlineSmall = s(20),
        titleLarge = s(21), titleMedium = s(18), titleSmall = s(17),
        bodyLarge = s(17), bodyMedium = s(15), bodySmall = s(14),
        labelLarge = s(15), labelMedium = s(14), labelSmall = s(12),
    )
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = QolColors.accent,
            onPrimary = Color.White,
            background = QolColors.bgBottom,
            surface = QolColors.panel,
            onSurface = QolColors.text,
            onSurfaceVariant = QolColors.text,
            onBackground = QolColors.text,
        ),
        typography = typography,
        content = content,
    )
}

/**
 * The per-game party-menu background: a whole 240x160 screen (teal for
 * FireRed and most hacks, green for Emerald, grey for Heart and Soul) cropped to fill, or - for the
 * CFRU hacks, whose navy grid repeats every 32px - one grid cell to tile
 * ([GameBackground.tiled]).
 */
class GameBackground(val image: androidx.compose.ui.graphics.ImageBitmap, val tiled: Boolean)

/**
 * FireRed's party backdrop in another game's colors - its three colors (the
 * two stripes, the darker band) mapped to the game's, sampled where only the
 * backdrop shows on each game's party menu (headless mGBA). Those hacks kept
 * FireRed's backdrop layout (Gaia, Too Many Types 2, Emerald Rogue) or draw a
 * flat one (Seaglass, Lazarus, R.O.W.E. - whose grid lines are left out), or
 * stripes of their own (SoulGold).
 */
private val FIRERED_BACKDROP = intArrayOf(0xFF4AADA5.toInt(), 0xFF398C8C.toInt(), 0xFF216B63.toInt())

private fun backdropColors(game: GameKind): IntArray? = when (game) {
    GameKind.EMERALD_ROGUE -> intArrayOf(0xFF080029.toInt(), 0xFF100042.toInt(), 0xFF080029.toInt())
    GameKind.GAIA -> intArrayOf(0xFF63BD7B.toInt(), 0xFF529C6B.toInt(), 0xFF42845A.toInt())
    // Glazed's and Quetzal's party menus have the same olive stripes.
    GameKind.TMT2, GameKind.GLAZED, GameKind.QUETZAL -> intArrayOf(0xFFCED67B.toInt(), 0xFFB5B55A.toInt(), 0xFF8C9C29.toInt())
    GameKind.EMERALD_SEAGLASS -> IntArray(3) { 0xFF4A4A63.toInt() }
    GameKind.LAZARUS -> IntArray(3) { 0xFFFFFFFF.toInt() }
    // Imperium's party grid: grey / near-black rows.
    GameKind.IMPERIUM -> intArrayOf(0xFF424242.toInt(), 0xFF101821.toInt(), 0xFF101821.toInt())
    GameKind.ROWE -> IntArray(3) { 0xFF292929.toInt() }
    // SoulGold's party list: light-blue stripes, a deeper blue edge.
    GameKind.SOULGOLD -> intArrayOf(0xFF84CEEF.toInt(), 0xFF7BC6EF.toInt(), 0xFF429CD6.toInt())
    // Orange Islands' party menu: FireRed's layout in cream stripes (sampled headless).
    GameKind.ORANGE_ISLANDS -> intArrayOf(0xFFFFF7CE.toInt(), 0xFFEFEFB5.toInt(), 0xFFE7DE9C.toInt())
    else -> null
}

/** [bmp] (FireRed's backdrop) with [FIRERED_BACKDROP] swapped for [to]; or, with no FireRed art yet, its stripes alone. */
private fun recolorBackdrop(bmp: android.graphics.Bitmap?, to: IntArray): android.graphics.Bitmap {
    val w = bmp?.width ?: 240
    val h = bmp?.height ?: 160
    val px = IntArray(w * h)
    if (bmp != null) {
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val k = FIRERED_BACKDROP.indexOf(px[i] or (0xFF shl 24))
            if (k >= 0) px[i] = to[k]
        }
    } else {
        for (y in 0 until h) px.fill(to[y and 1], y * w, (y + 1) * w)
    }
    return android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888)
}

@Composable
fun rememberGameBackground(game: GameKind): GameBackground? {
    val context = LocalContext.current
    val gen = rememberArtGeneration()
    return remember(game, gen) {
        // Kept across visits: the ITEMS backdrop crossfade unmounts this one, and
        // rebuilding it recoloured 38k pixels on the main thread each time.
        backgroundCache[game to gen]?.let { return@remember it }
        buildGameBackground(context, game)?.also {
            backgroundCache.keys.removeAll { (_, g) -> g != gen }
            backgroundCache[game to gen] = it
        }
    }
}

/** [rememberGameBackground]'s backdrops by game and art generation (main thread only). */
private val backgroundCache = HashMap<Pair<GameKind, Int>, GameBackground>()

private fun buildGameBackground(context: android.content.Context, game: GameKind): GameBackground? {
    // Gen 1's screens are plain white: one pixel, stretched over the tab in a single draw.
    // (Never tiled - GameBackdrop draws a tiled backdrop tile by tile, and a 1px tile at
    // the Thor's 5x was ~53,000 draws a frame: 77 ms per frame on the RenderThread.)
    if (game == GameKind.YELLOW) {
        return GameBackground(android.graphics.Bitmap.createBitmap(intArrayOf(-1), 1, 1, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap(), false)
    }
    backdropColors(game)?.let { to ->
        return GameBackground(recolorBackdrop(GameArt.get(context, "partybg/firered.png"), to).asImageBitmap(), false)
    }
    val (asset, tiled) = when (game) {
        GameKind.EMERALD -> "partybg/emerald.png" to false
        GameKind.HEART_AND_SOUL -> "partybg/hns.png" to false
        GameKind.UNBOUND, GameKind.RADICAL_RED, GameKind.ODYSSEY, GameKind.AMETHYST -> "partybg/cfru_tile.png" to true
        else -> "partybg/firered.png" to false
    }
    // Every backdrop comes from a ROM the player has run (RomArt): until the
    // game's own is there, FireRed's or Emerald's if one of those is (untiled).
    val bmp = sequenceOf(asset, "partybg/firered.png", "partybg/emerald.png").distinct()
        .firstNotNullOfOrNull { path -> GameArt.get(context, path)?.let { it to (tiled && path == asset) } }
    return bmp?.let { (b, tile) -> GameBackground(b.asImageBitmap(), tile) }
}

/**
 * A thick, high-contrast [QolColors.focusRing] border drawn outside whatever
 * shape the element already uses — the shared D-pad/keyboard "here's your
 * cursor" indicator. Apply first in a modifier chain, before any background.
 * No-op (zero-width transparent border, so layout doesn't jump) when not
 * focused.
 */
fun Modifier.gbaFocusRing(focused: Boolean, shape: androidx.compose.ui.graphics.Shape): Modifier = this
    .border(if (focused) 4.dp else 0.dp, if (focused) QolColors.focusRing else Color.Transparent, shape)
    .padding(if (focused) 3.dp else 0.dp)

/**
 * Translucent cyan wash over an item's own fill when it has D-pad/keyboard
 * focus — paired with [gbaFocusRing] so focus reads as a highlighted
 * background too, not just an outline. Apply last, right before `.clickable`,
 * over the item's innermost (already-drawn) fill color.
 */
fun Modifier.gbaFocusTint(focused: Boolean, shape: androidx.compose.ui.graphics.Shape): Modifier =
    if (focused) this.background(QolColors.focusRing.copy(alpha = 0.30f), shape) else this

/** A FR/LG "window": cream fill, blue outer frame, gold hairline, light inner edge. */
@Composable
fun GbaWindow(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(10.dp),
    fill: Color = QolColors.panel,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(QolColors.windowFrame, PixelRoundedShape(8.dp))
            .padding(2.dp)
            .background(QolColors.windowGold, PixelRoundedShape(6.dp))
            .padding(1.dp)
            .background(QolColors.windowInner, PixelRoundedShape(5.dp))
            .padding(2.dp)
            .background(fill, PixelRoundedShape(4.dp))
            .padding(padding),
    ) { content() }
}

/**
 * FR/LG "menu button": a cream key with the blue double-frame, pressable. Used
 * everywhere in place of Material `Button`/`OutlinedButton`, which vanish on the
 * teal backdrop. [selected] gives it the blue "highlighted option" fill; keyboard
 * / D-pad focus draws a thick cyan [QolColors.focusRing] ring around the whole
 * key, kept a different color from "selected" so the two are never ambiguous.
 * [danger] tints it red for destructive actions (e.g. the confirm button on a
 * delete dialog).
 */
@Composable
fun GbaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val frame = when {
        !enabled -> QolColors.muted
        danger -> QolColors.hpLow
        else -> QolColors.windowFrame
    }
    val fill = when {
        !enabled -> QolColors.panelAlt
        danger -> Color(0xFFFCE4E0)
        selected -> QolColors.windowInner
        else -> QolColors.panel
    }
    val contentColor = when {
        !enabled -> QolColors.muted
        danger -> QolColors.hpLow
        else -> QolColors.text
    }
    Box(
        modifier = modifier
            .gbaFocusRing(focused, PixelRoundedShape(10.dp))
            .background(frame, PixelRoundedShape(7.dp))
            .padding(2.dp)
            .background(if (danger) Color(0xFFF8B8A8) else QolColors.windowGold, PixelRoundedShape(5.dp))
            .padding(1.dp)
            .background(fill, PixelRoundedShape(4.dp))
            .gbaFocusTint(focused, PixelRoundedShape(4.dp))
            .soundClickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor,
        ) { content() }
    }
}

/** White pixel text with a 1px dark outline (as drawn on the party slots). */
@Composable
fun OnSlotText(
    text: String,
    fontSize: Int,
    modifier: Modifier = Modifier,
    color: Color = QolColors.onSlot,
    weight: FontWeight = FontWeight.Normal,
) {
    // NOTE: `style = TextStyle(shadow = ...)` looked right but silently threw
    // away the ambient pixel-font Typography QolTheme sets — Text() merges its
    // own `style` param wholesale over LocalTextStyle.current rather than
    // layering just the one field, so every slot label/HP/Lv on the party
    // screen rendered in the platform default font instead of the pixel font.
    // fontFamily as its own direct Text() param always wins over `style`, so
    // set it there explicitly instead of relying on the merge.
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontWeight = weight,
        fontSize = fontSize.sp,
        fontFamily = pixelFontFamily(),
        style = TextStyle(
            shadow = Shadow(QolColors.onSlotOutline, Offset(1.5f, 1.5f), 0f),
        ),
    )
}

/**
 * App-styled confirmation modal — an in-composition overlay (NOT a Compose
 * `Dialog`/system `AlertDialog`: those default to Material's own colors, which
 * is what made the old delete confirmation read as generic light-gray-on-white,
 * and a real `Dialog` throws "window type mismatch" inside the Screen-2
 * Presentation anyway — see OptionOverlay). Safe to use from any screen,
 * including the companion.
 */
@Composable
fun GbaConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    dismissLabel: String = "Cancel",
    danger: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .soundClickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        GbaWindow(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(24.dp)
                .clickable(interactionSource = noRipple, indication = null, onClick = {}), // swallow taps
            padding = PaddingValues(16.dp),
        ) {
            Column {
                Text(title, color = QolColors.text, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Spacer(Modifier.padding(top = 8.dp))
                Text(message, color = QolColors.muted, fontSize = 16.sp)
                Spacer(Modifier.padding(top = 16.dp))
                Row {
                    GbaButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(dismissLabel) }
                    Spacer(Modifier.padding(start = 8.dp))
                    GbaButton(onClick = onConfirm, danger = danger, modifier = Modifier.weight(1f)) { Text(confirmLabel) }
                }
            }
        }
    }
}
