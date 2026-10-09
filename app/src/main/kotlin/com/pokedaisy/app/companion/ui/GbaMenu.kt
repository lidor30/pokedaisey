package com.pokedaisy.app.companion.ui

import com.pokedaisy.app.companion.ui.theme.isDaisyTheme
import com.pokedaisy.app.companion.i18n.tk
import com.pokedaisy.app.companion.i18n.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pokedaisy.app.companion.data.GameKind
import com.pokedaisy.app.companion.data.activeGame
import com.pokedaisy.app.companion.ui.theme.QolColors
import com.pokedaisy.app.companion.ui.theme.gbaFocusRing
import com.pokedaisy.app.companion.ui.theme.pixelFontFamily
import com.pokedaisy.app.companion.ui.theme.rememberGameBackground
import kotlin.math.max

/**
 * FireRed's OPTION screen, sampled 1:1 from the game (headless mGBA): a white
 * title window, a grey list window whose current row is white, grey labels
 * and red values. Fixed for every game and theme. The building blocks below
 * (title window, list window, `LABEL  VALUE` rows, buttons, overlays) are the
 * app's shared menu look - the companion's SETTINGS and STATES tabs and the
 * top-screen Library/Settings are all built from them.
 */
object OptionColors {
    // A Game Boy game gets its own windows instead (Gen 1: black text on white, no
    // shadows, the text box's double line - sampled headless from Yellow's menus).
    // Read through activeGame like the rest of the per-game look (CompanionScreen is
    // keyed on the game, so a switch redraws) - only while a game is open ([inGame]):
    // the top-screen Library / Settings keep the app's own look after Yellow closes.
    private val gen1 get() = inGame && activeGame == GameKind.YELLOW

    /** A game's screens are up (PokeDaisyActivity resumed); Compose state, so the look follows at once. */
    var inGame: Boolean
        get() = inGameState.value
        set(v) { inGameState.value = v }
    private val inGameState = androidx.compose.runtime.mutableStateOf(false)
    private val G1_BLACK = Color(0xFF181818)
    private val G1_WHITE = Color(0xFFFFFFFF)
    private val G1_FRAME = listOf(G1_WHITE to 2, G1_BLACK to 1, G1_WHITE to 1, G1_BLACK to 1, G1_WHITE to 1)

    // The PokéDaisy theme's light backdrop on the top screen (Library, Settings, setup): text drawn straight
    // on it takes the windows' dark grey instead of white. Never inside a game (the game's own backdrops).
    private val daisy get() = !inGame && isDaisyTheme

    val hintBar get() = if (gen1) G1_BLACK else Color(0xFF007BC6)
    val hintText get() = Color(0xFFFFFFFF)
    val hintShadow get() = if (gen1) Color.Transparent else Color(0xFF636363)
    val titleLayers get() = if (gen1) G1_FRAME else listOf(Color(0xFF63737B) to 2, Color(0xFFCED6D6) to 1)
    val titleFill get() = Color(0xFFFFFFFF)
    val titleText get() = if (gen1) G1_BLACK else Color(0xFF636363)
    val titleShadow get() = if (gen1) Color.Transparent else Color(0xFFD6D6CE)
    val listLayers get() = if (gen1) G1_FRAME else listOf(
        Color(0xFF293131) to 1, Color(0xFF8C8CCE) to 1, Color(0xFF736B84) to 2,
        Color(0xFFDED6DE) to 1, Color(0xFFFFFFFF) to 2,
    )
    val listFill get() = if (gen1) G1_WHITE else Color(0xFFE0DFDF)
    /** The cursor row (Gen 1 marks it with its ▶; here a light grey band). */
    val rowSelected get() = if (gen1) Color(0xFFDCDCDC) else Color(0xFFFFFFFF)
    val label get() = if (gen1) G1_BLACK else Color(0xFF575656)
    val labelShadow get() = if (gen1) Color.Transparent else Color(0xFFBCBBB4)
    val value get() = if (gen1) G1_BLACK else Color(0xFFCB0707)
    val valueShadow get() = if (gen1) Color.Transparent else Color(0xFFE0A564)
    /** Secondary text (paths, timestamps, hints) - lighter than [label]. */
    val muted get() = if (gen1) Color(0xFF787878) else Color(0xFF8C8C94)
    val mutedShadow get() = if (gen1) Color.Transparent else Color(0xFFD6D6D6)
    /** The dark outer line of [listLayers], for frames drawn around images. */
    val frameDark get() = if (gen1) G1_BLACK else Color(0xFF293131)
    val frameLight get() = if (gen1) G1_WHITE else Color(0xFF8C8CCE)
    /** Text straight on a game backdrop: white with the hint bar's grey shadow (black on Gen 1's white,
     * the windows' grey on PokéDaisy's light backdrop). */
    val onBackdrop get() = if (gen1) G1_BLACK else if (daisy) label else Color(0xFFFFFFFF)
    val onBackdropShadow get() = if (gen1) Color.Transparent else if (daisy) labelShadow else Color(0xFF404850)
    /** A tab chip's frame: the title window's, or for Gen 1 just its double line (black, white, black) - its
     * full text-box frame is six pixels a side, which left a narrow chip no room for CHEEVOS. */
    val chipLayers get() = if (gen1) listOf(G1_BLACK to 1, G1_WHITE to 1, G1_BLACK to 1) else titleLayers
    /** The tab bar's chips: the open one white over grey idle ones (Gen 1: the cursor row's grey over white). */
    val tabSelectedFill get() = if (gen1) rowSelected else titleFill
    val tabIdleFill get() = if (gen1) G1_WHITE else listFill
    /** The dashed line between list rows (the bag list's 6/2 dash). */
    /** Settings group titles: Gen 1's value colour is the rows' black, so its titles get a black band (white text). */
    val groupTitleFill: Color? get() = if (gen1) G1_BLACK else null
    val groupTitleText get() = if (gen1) G1_WHITE else value
    val groupTitleShadow get() = if (gen1) Color.Transparent else valueShadow
    val divider get() = if (gen1) Color(0xFFB0B0B0) else Color(0xFFC6C5C5)
}

/** A dashed row divider along the bottom edge, from [start] px in - the bag
 * list's 6-on/2-off dash, 2 GBA px thick. [u] is one GBA pixel in px. */
fun DrawScope.drawRowDivider(color: Color, u: Float, start: Float) {
    val y = size.height - u
    drawLine(
        color, Offset(start, y), Offset(size.width - start, y), strokeWidth = 2 * u,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6 * u, 2 * u)),
    )
}

/**
 * Nested rounded-rect *strokes*, outermost first - [drawLayeredBox]'s frame
 * without the fill, for framing something that draws its own content (a map,
 * a screenshot).
 */
fun DrawScope.drawLayeredFrame(layers: List<Pair<Color, Float>>, radius: Float) {
    var inset = 0f
    var r = radius
    for ((color, width) in layers) {
        drawPixelRoundFrame(
            color, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset),
            r.coerceAtLeast(0f), width,
        )
        inset += width
        r -= width
    }
}

/**
 * The white title window. [onBack] adds a back arrow before the title;
 * [trailing] is right-aligned extra text (e.g. the game's name) and
 * [actions] right-aligned buttons after it.
 */
@Composable
fun OptionTitleWindow(
    title: String,
    m: GbaTextMetrics,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onBack: (() -> Unit)? = null,
    /** Drawn before the title (after the back arrow): the Library's logo. */
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val u = m.u
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawLayeredBox(OptionColors.titleLayers.inPx(u.toPx()), OptionColors.titleFill, radius = 2 * u.toPx()) }
            .padding(horizontal = u * 12, vertical = u * 6),
    ) {
        leading?.let { it(); Spacer(Modifier.width(u * 6)) }
        if (onBack != null) {
            CompanionBackHandler(onBack = onBack)
            Box(Modifier.soundClickable(onClick = onBack).padding(end = u * 6)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back"), tint = OptionColors.titleText, modifier = Modifier.size(m.lineHeight))
            }
        }
        if (trailing == null) {
            GbaText(tr(title), OptionColors.titleText, OptionColors.titleShadow, m, modifier = Modifier.weight(1f))
        } else {
            // The title whole; the trailing (the game's name) right-aligned after a gap, and the one
            // that shortens when both don't fit (a wide game font: "OPTI…" read badly).
            GbaText(tr(title), OptionColors.titleText, OptionColors.titleShadow, m)
            Box(Modifier.weight(1f).padding(start = u * 8), contentAlignment = Alignment.CenterEnd) {
                GbaText(trailing, OptionColors.titleText, OptionColors.titleShadow, m)
            }
        }
        actions()
    }
}

/** The grey list window: its multi-line frame around [content]. */
@Composable
fun OptionListWindow(
    m: GbaTextMetrics,
    modifier: Modifier = Modifier,
    fill: Color = OptionColors.listFill,
    content: @Composable () -> Unit,
) {
    val u = m.u
    val frame = OptionColors.listLayers.sumOf { it.second }
    Box(
        modifier = modifier
            .drawBehind { drawLayeredBox(OptionColors.listLayers.inPx(u.toPx()), fill, radius = 2 * u.toPx()) }
            .padding(u * (frame + 2)),
    ) { content() }
}

/**
 * One `LABEL      VALUE` row, the value column starting ~58% across like the
 * game's. Selected (or D-pad-focused) rows turn white, the game's cursor.
 * [value] null = a plain command row; [valueColor] overrides the red.
 */
@Composable
fun OptionLine(
    label: String,
    value: String?,
    selected: Boolean,
    m: GbaTextMetrics,
    modifier: Modifier = Modifier,
    height: Dp = m.rowHeight,
    labelWeight: Float = 0.58f,
    /** A dashed line under the row, between it and the next. */
    divider: Boolean = false,
    /** An [OptionBadge] after the label ("ALPHA"). */
    labelBadge: String? = null,
    /** Pixel art before the label (RetroAchievements' trophy), a line high. */
    labelIcon: (@Composable () -> Unit)? = null,
    /** An [OptionBadge] after the value. */
    valueBadge: String? = null,
    /** False greys the row out and ignores taps (a setting another one has turned off). */
    enabled: Boolean = true,
    /** A smaller grey line under the label: what the row is for. */
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(if (selected || focused) OptionColors.rowSelected else Color.Transparent)
            .then(if (divider) Modifier.drawBehind { drawRowDivider(OptionColors.divider, m.u.toPx(), 4 * m.u.toPx()) } else Modifier)
            .then(if (enabled) Modifier.soundClickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = m.u * 8),
    ) {
        Column(Modifier.weight(labelWeight)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                labelIcon?.let { Box(Modifier.size(m.lineHeight)) { it() }; Spacer(Modifier.width(m.u * 4)) }
                if (enabled) GbaText(tr(label), OptionColors.label, OptionColors.labelShadow, m)
                else GbaText(tr(label), OptionColors.muted, OptionColors.mutedShadow, m)
                labelBadge?.let { OptionBadge(it, m, Modifier.padding(start = m.u * 6)) }
            }
            subtitle?.let {
                val small = rememberGbaTextMetrics(0.8f * m.fontPixel / m.px)
                GbaText(tr(it), OptionColors.muted, OptionColors.mutedShadow, small)
            }
        }
        if (value != null) {
            Row(Modifier.weight(1f - labelWeight), verticalAlignment = Alignment.CenterVertically) {
                if (enabled) GbaText(tr(value), OptionColors.value, OptionColors.valueShadow, m)
                else GbaText(tr(value), OptionColors.muted, OptionColors.mutedShadow, m)
                valueBadge?.let { OptionBadge(it, m, Modifier.padding(start = m.u * 6)) }
            }
        }
    }
}

/** A small tag on a row ("ALPHA" on a mode that isn't finished): white on the value red, pill-shaped. */
@Composable
fun OptionBadge(text: String, m: GbaTextMetrics, modifier: Modifier = Modifier, fill: Color = OptionColors.value) {
    // ~2/3 of the row's own text: 0.85 next to the main lists' 1.25, smaller beside denser text.
    val small = rememberGbaTextMetrics(0.68f * m.fontPixel / m.px)
    Box(
        modifier
            .background(fill, PixelPillShape)
            .padding(horizontal = m.u * 5, vertical = m.u),
    ) {
        GbaText(tr(text), Color.White, OptionColors.valueShadow.copy(alpha = 0.6f), small)
    }
}

/**
 * [OptionLine] rows that share out the available height (big touch targets)
 * between [minRow] and [maxRow] - past the floor the rows scroll instead.
 * [rows]: label, value (null = command row), onClick. [selected]: the row
 * index drawn as the white cursor, if any.
 */
@Composable
fun OptionRows(
    rows: List<Triple<String, String?, () -> Unit>>,
    m: GbaTextMetrics,
    modifier: Modifier = Modifier,
    selected: Int = -1,
    minRow: Dp = m.rowHeight * 1.2f,
    maxRow: Dp = m.lineHeight * 2.4f,
    labelWeight: Float = 0.58f,
    /** Dashed lines between the rows, like the bag list. */
    dividers: Boolean = true,
    /** An [OptionBadge] after row i's value. */
    valueBadge: (Int) -> String? = { null },
    /** False greys row i out ([OptionLine]'s enabled). */
    enabled: (Int) -> Boolean = { true },
) {
    BoxWithConstraints(modifier) {
        val rowHeight = if (rows.isEmpty() || maxHeight == Dp.Infinity) minRow
        else (maxHeight / rows.size).coerceIn(minRow, maxRow.coerceAtLeast(minRow))
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            rows.forEachIndexed { i, (label, value, onClick) ->
                OptionLine(
                    label, value, selected = i == selected, m, height = rowHeight, labelWeight = labelWeight,
                    divider = dividers && i < rows.lastIndex, valueBadge = valueBadge(i), enabled = enabled(i), onClick = onClick,
                )
            }
        }
    }
}

/**
 * A free-standing button in the title window's look (white, grey frame) -
 * for actions that aren't a row of a list (Save/Load, Undo, header icons).
 * [emphasis] draws the label in the value red; [enabled] false greys it out.
 */
@Composable
fun OptionButton(
    label: String?,
    m: GbaTextMetrics,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emphasis: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String? = label,
) {
    val u = m.u
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fg = when {
        !enabled -> OptionColors.muted
        emphasis -> OptionColors.value
        else -> OptionColors.label
    }
    val shadow = when {
        !enabled -> OptionColors.mutedShadow
        emphasis -> OptionColors.valueShadow
        else -> OptionColors.labelShadow
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .gbaFocusRing(focused, PixelRoundedShape(u * 4))
            .heightIn(min = m.rowHeight)
            .drawBehind {
                drawLayeredBox(
                    OptionColors.titleLayers.inPx(u.toPx()),
                    if (focused) OptionColors.listFill else OptionColors.titleFill,
                    radius = 3 * u.toPx(),
                )
            }
            .soundClickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = u * 8, vertical = u * 4),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription, tint = fg, modifier = Modifier.size(m.lineHeight))
        }
        if (icon != null && label != null) Spacer(Modifier.width(u * 4))
        if (label != null) GbaText(tr(label), fg, shadow, m, bold = gameBoldLabels())
    }
}

/** Text drawn straight on a game backdrop (section headers): white, shadowed. */
@Composable
fun BackdropText(text: String, m: GbaTextMetrics, modifier: Modifier = Modifier) {
    GbaText(text, OptionColors.onBackdrop, OptionColors.onBackdropShadow, m, modifier = modifier)
}

/**
 * Black-out overlay for pickers/confirmations. An in-composition overlay, not
 * a Compose `Dialog` - those open a new window, which throws inside the
 * Screen-2 Presentation.
 */
@Composable
fun OptionOverlay(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    CompanionBackHandler(onBack = onDismiss)
    val noRipple = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .soundClickable(interactionSource = noRipple, indication = null, onClick = onDismiss)
            .padding(16.dp),
    ) {
        Box(modifier.clickable(interactionSource = noRipple, indication = null, onClick = {})) { content() }
    }
}

/**
 * A pick-one list over the screen: tapping a setting's row opens this instead
 * of stepping through every value. The current choice is the white row and
 * carries a red ◀ marker; tapping any row picks it and closes.
 */
@Composable
fun <T> OptionSelector(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    m: GbaTextMetrics,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    /** An [OptionBadge] after an option's label. */
    badge: (T) -> String? = { null },
) {
    OptionOverlay(onDismiss, Modifier.widthIn(max = 480.dp)) {
        BoxWithConstraints {
            // Fit every option + CANCEL on screen when there's room (title
            // window, gap and list frame take ~40u), else scroll.
            val u = m.u
            val room = if (maxHeight == Dp.Infinity) m.rowHeight * 20 else maxHeight - u * 40 - m.lineHeight
            val rowHeight = (room / (options.size + 1)).coerceIn(m.rowHeight, m.rowHeight * 1.3f)
            Column {
                OptionTitleWindow(title, m, onBack = onDismiss)
                Spacer(Modifier.height(u * 4))
                OptionListWindow(m, Modifier.fillMaxWidth()) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        options.forEach { opt ->
                            val on = opt == selected
                            OptionLine(
                                label(opt), if (on) "◀" else null, selected = on, m,
                                height = rowHeight, labelWeight = 0.85f, divider = true, labelBadge = badge(opt),
                            ) { onPick(opt) }
                        }
                        OptionLine(tk("CANCEL"), null, selected = false, m, height = rowHeight, onClick = onDismiss)
                    }
                }
            }
        }
    }
}

/** OPTION-styled yes/no: title window + message + [confirmLabel] / CANCEL rows. */
@Composable
fun OptionConfirm(
    title: String,
    message: String,
    confirmLabel: String,
    m: GbaTextMetrics,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** Null: [confirmLabel] alone, for a notice there's nothing to cancel on. */
    cancelLabel: String? = tk("CANCEL"),
) {
    OptionOverlay(onDismiss, Modifier.widthIn(max = 480.dp)) {
        Column {
            OptionTitleWindow(title, m)
            Spacer(Modifier.height(m.u * 4))
            OptionListWindow(m, Modifier.fillMaxWidth()) {
                Column {
                    GbaText(
                        message, OptionColors.label, OptionColors.labelShadow, m, maxLines = Int.MAX_VALUE,
                        modifier = Modifier.padding(horizontal = m.u * 8, vertical = m.u * 4),
                    )
                    // Side by side, each a framed button.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(m.u * 4),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = m.u * 4, vertical = m.u * 4),
                    ) {
                        cancelLabel?.let { OptionButton(it, m, onClick = onDismiss, modifier = Modifier.weight(1f).height(m.rowHeight * 1.4f)) }
                        OptionButton(confirmLabel, m, onClick = onConfirm, emphasis = true, modifier = Modifier.weight(1f).height(m.rowHeight * 1.4f))
                    }
                }
            }
        }
    }
}

/** A game's party-menu background, pixel-scaled to fill (see [rememberGameBackground]). */
@Composable
fun GameBackdrop(game: GameKind, modifier: Modifier = Modifier.fillMaxSize()) {
    val bg = rememberGameBackground(game) ?: return
    if (bg.tiled) {
        // Whole-number scale of a 240px-wide GBA screen - on the
        // Thor the same scale the party slots land on, so the grid
        // matches their pixels like it does in the game.
        Canvas(modifier = modifier) {
            val k = max(1, (size.width / 240f).toInt())
            val step = bg.image.width * k
            for (y in 0 until size.height.toInt() step step) {
                for (x in 0 until size.width.toInt() step step) {
                    drawImage(
                        bg.image, dstOffset = IntOffset(x, y), dstSize = IntSize(step, bg.image.height * k),
                        filterQuality = FilterQuality.None,
                    )
                }
            }
        }
    } else {
        Image(
            bitmap = bg.image,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.None,
        )
    }
}

/**
 * The top-screen (Library / Settings) backdrop: the 1-GBA-pixel horizontal
 * stripes of FireRed's party-menu screen - the backdrop the companion's tabs
 * sit on - drawn natively so it fills any aspect ratio (the party screen
 * image itself has a dark cancel-button column that looks wrong stretched
 * to 16:9). The FireRed theme uses the game's exact two teals; other themes
 * stripe their own slot colors, so a theme still reskins the app. The
 * PokéDaisy theme (the default) draws the website's backdrop instead ([DaisyBackdrop]).
 */
@Composable
fun AppBackdrop(
    /** The PokéDaisy theme's floating logos (the Library). */
    logos: Boolean = false,
) {
    // PokéDaisy: the website's light backdrop - outside a game only, whose windows keep the game's look.
    if (isDaisyTheme && !OptionColors.inGame) {
        DaisyBackdrop(logos)
        return
    }
    val m = rememberGbaTextMetrics()
    val (a, b) = if (QolColors.currentThemeId == 0) {
        Color(0xFF4AADA5) to Color(0xFF398C8C)
    } else {
        QolColors.slotFill to QolColors.slotFillDark
    }
    Canvas(Modifier.fillMaxSize()) {
        val stripe = m.px.toFloat()
        drawRect(b)
        var y = 0f
        while (y < size.height) {
            drawRect(a, Offset(0f, y), Size(size.width, stripe))
            y += 2 * stripe
        }
    }
}

/** A text input in the title window's look (white, grey frame), pixel font: one line, or
 * [minLines] and more. [password] hides what's typed and keeps the keyboard from learning
 * it; [code] asks for capitals with no suggestions (cheat codes). */
@Composable
fun OptionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    m: GbaTextMetrics,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    password: Boolean = false,
    minLines: Int = 1,
    code: Boolean = false,
) {
    val u = m.u
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = minLines == 1,
        minLines = minLines,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = when {
            password -> KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Password)
            code -> KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrect = false, keyboardType = KeyboardType.Ascii)
            else -> KeyboardOptions.Default
        },
        textStyle = gbaTextStyle(pixelFontFamily(), m).copy(color = OptionColors.label),
        cursorBrush = SolidColor(OptionColors.value),
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawLayeredBox(OptionColors.titleLayers.inPx(u.toPx()), OptionColors.titleFill, radius = 2 * u.toPx()) }
            .padding(horizontal = u * 6, vertical = u * 5),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty() && placeholder != null) GbaText(placeholder, OptionColors.muted, OptionColors.mutedShadow, m)
                inner()
            }
        },
    )
}
