package com.pokedaisey.app.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pokedaisey.app.companion.data.GameKind
import com.pokedaisey.app.companion.data.ItemView
import com.pokedaisey.app.companion.data.POCKET_POKE_BALLS
import com.pokedaisey.app.companion.data.POCKET_TM_HM
import com.pokedaisey.app.companion.data.activeGame
import com.pokedaisey.app.companion.data.itemDescriptionsEmeraldGame
import com.pokedaisey.app.companion.data.itemDescriptionsFireRedGame
import com.pokedaisey.app.companion.data.itemNamesEmeraldGame
import com.pokedaisey.app.companion.data.itemNamesFireRedGame
import com.pokedaisey.app.companion.data.pocketLabel
import com.pokedaisey.app.companion.data.pocketOrder
import com.pokedaisey.app.companion.ui.theme.pixelFontFamily

/** DEFAULT keeps the bag's own slot order, as the game lists it. */
private enum class ItemSort(val label: String) { DEFAULT("Default"), NAME("Name"), TYPE("Type"), COUNT("Count") }

/**
 * FireRed and Emerald print item names and pocket titles in ALL CAPS
 * ("POKé BALL", "PARLYZ HEAL") and write "POKéMON" in descriptions, so the bag
 * screen uses each game's own strings (ItemTextGame.kt) rather than the
 * app-wide Title Case tables. Games whose item table was extracted from their
 * own ROM already carry the game's casing.
 */
private class GameItemText(val names: Map<Int, String>, val descriptions: Map<Int, String>)

private fun gameItemText(game: GameKind): GameItemText? = when (game) {
    GameKind.FIRERED -> GameItemText(itemNamesFireRedGame, itemDescriptionsFireRedGame)
    GameKind.EMERALD -> GameItemText(itemNamesEmeraldGame, itemDescriptionsEmeraldGame)
    else -> null
}

/** Games whose bag prints pocket titles in caps; their item tables already are. */
private fun capsBagLabels(game: GameKind): Boolean =
    gameItemText(game) != null || game == GameKind.HEART_AND_SOUL

private fun capsPocketLabel(pocket: Int?): String = when (pocket) {
    null -> "ALL"
    POCKET_POKE_BALLS -> "POKé BALLS"
    POCKET_TM_HM -> "TMs & HMs"
    else -> pocketLabel(pocket).uppercase()
}

/**
 * One game's bag-screen look. The Items tab is drawn entirely from plain
 * Compose shapes + text in these colors (no per-game image assets, unlike the
 * party slots), so a new game only needs a new palette. Layer widths are in u.
 */
private data class BagPalette(
    /**
     * The list window's frame, outermost first. The folder tabs reuse its
     * first [tabLayerCount] layers as their own border, filled with the next
     * layer's color - the band the selected tab opens onto.
     */
    val panelLayers: List<Pair<Color, Int>>,
    val tabLayerCount: Int,
    val fill: Color,
    /** Line under each row; null = none (Emerald). */
    val divider: Color?,
    val text: Color,
    val textShadow: Color,
    val tabText: Color,
    val tabTextShadow: Color,
    /** Under the selected tab's label, like FireRed's pocket-title bar. */
    val tabUnderline: Color,
    /** Description box (and sort button) frame, outermost first. */
    val descLayers: List<Pair<Color, Int>>,
    val descFill: Color,
    val descText: Color,
    val descTextShadow: Color,
    val iconBox: Color,
    val iconBoxEdge: Color,
    /** The bag screen's own 2u-striped backdrop, where it differs from the party one. */
    val backdrop: Pair<Color, Color>? = null,
    /** [divider]'s dash, on/off in u (the vanilla bag's 6/2); null = solid. */
    val dividerDash: Pair<Int, Int>? = 6 to 2,
    /** [divider]'s width in u, and an optional 1u shadow line under it. */
    val dividerWidth: Int = 2,
    val dividerShadow: Color? = null,
) {
    val tabLayers get() = panelLayers.take(tabLayerCount)
    val tabFill get() = panelLayers[tabLayerCount].first
    /** How far the selected tab's fill reaches over the window's frame: onto its [tabFill] band. */
    val selectedFillReach get() = tabLayers.sumOf { it.second }
    /** ...and its outer border: only over the window's own outer border, for a clean corner. */
    val selectedBorderReach get() = panelLayers[0].second
    val panelFrame get() = panelLayers.sumOf { it.second }
}

/** Sampled 1:1 from a FireRed bag screenshot. */
private val FireRedBag = BagPalette(
    panelLayers = listOf(Color(0xFF686868) to 2, Color(0xFFE8E0A8) to 1, Color(0xFFF0C870) to 2, Color(0xFFD0B050) to 2),
    tabLayerCount = 2,
    fill = Color(0xFFF8F8C8), divider = Color(0xFFE8E0A8),
    text = Color(0xFF606060), textShadow = Color(0xFFD0D0C8),
    tabText = Color(0xFFF8F8F8), tabTextShadow = Color(0xFF606060), tabUnderline = Color(0xFFD88848),
    descLayers = listOf(Color(0xFF005070) to 2, Color(0xFF10A8D8) to 1), descFill = Color(0xFF0078C0),
    descText = Color(0xFFF8F8F8), descTextShadow = Color(0xFF606060),
    iconBox = Color(0xFFF8F8F8), iconBoxEdge = Color(0xFFB8D0D0),
)

/**
 * Sampled 1:1 from an Emerald bag screen (mGBA): navy/slate/grey window frame
 * around a gold band, no row lines, black text, a white description box, and
 * purple/blue striped backdrop. The underline borrows the pocket arrows' red.
 */
private val EmeraldBag = BagPalette(
    panelLayers = listOf(Color(0xFF335170) to 2, Color(0xFF2C3941) to 1, Color(0xFF636372) to 1, Color(0xFFFBE898) to 2),
    tabLayerCount = 3,
    fill = Color(0xFFFFFFD3), divider = null,
    text = Color(0xFF000000), textShadow = Color(0xFFD6D6CF),
    tabText = Color(0xFF000000), tabTextShadow = Color(0xFFB89D63), tabUnderline = Color(0xFFEC5F2A),
    descLayers = listOf(Color(0xFF7B7B7B) to 1), descFill = Color(0xFFFFFFFF),
    descText = Color(0xFF000000), descTextShadow = Color(0xFFD6D6CF),
    iconBox = Color(0xFFFFFFFF), iconBoxEdge = Color(0xFF7B7B7B),
    backdrop = Color(0xFFC871F7) to Color(0xFF5882F7),
)

/**
 * Sampled 1:1 from Heart and Soul's bag screen (headless mGBA): Emerald's bag
 * layout re-skinned - a cream list window in a grey/charcoal/slate frame
 * around a gold band, black text, the gold pocket banner with its orange
 * arrows (the underline), a white description box and icon box, and grey
 * 2px-striped backdrop.
 */
private val HeartAndSoulBag = BagPalette(
    panelLayers = listOf(Color(0xFF8C8C8C) to 2, Color(0xFF393139) to 1, Color(0xFF636373) to 1, Color(0xFFFFE78C) to 2),
    tabLayerCount = 3,
    fill = Color(0xFFFFFFCE), divider = null,
    text = Color(0xFF000000), textShadow = Color(0xFFD6D6CE),
    tabText = Color(0xFF000000), tabTextShadow = Color(0xFFBD9C5A), tabUnderline = Color(0xFFFF5200),
    descLayers = listOf(Color(0xFF7B7B7B) to 1), descFill = Color(0xFFFFFFFF),
    descText = Color(0xFF000000), descTextShadow = Color(0xFFD6D6CE),
    iconBox = Color(0xFFFFFFFF), iconBoxEdge = Color(0xFF7B7B7B),
    backdrop = Color(0xFFC6C6C6) to Color(0xFFADADAD),
)

/**
 * Sampled 1:1 from Unbound's cube (bag) screen: slate windows with a
 * dark/light green double line, white text with a grey shadow, solid 1px row
 * lines, a green-edged white icon box, on the screen's flat light grey.
 */
private val UnboundBag = BagPalette(
    panelLayers = listOf(Color(0xFF3A3949) to 1, Color(0xFF4EA06F) to 1, Color(0xFF62C791) to 1, Color(0xFF3A3949) to 1),
    tabLayerCount = 3,
    fill = Color(0xFF3A3949), divider = Color(0xFF4A4951),
    text = Color(0xFFFEFBFF), textShadow = Color(0xFF636163),
    tabText = Color(0xFFFEFBFF), tabTextShadow = Color(0xFF636163), tabUnderline = Color(0xFF62C791),
    descLayers = listOf(Color(0xFF3A3949) to 1, Color(0xFF62C791) to 1), descFill = Color(0xFF3A3949),
    descText = Color(0xFFFEFBFF), descTextShadow = Color(0xFF636163),
    iconBox = Color(0xFFFEFBFF), iconBoxEdge = Color(0xFF4EA06F),
    backdrop = Color(0xFFE5E3E6) to Color(0xFFE5E3E6),
    dividerDash = null, dividerWidth = 1,
)

/**
 * Sampled from Amethyst's bag screen: a light-blue list in a dark-navy frame
 * with dashed blue row lines and the vanilla grey text, the pocket title's
 * navy box with a blue edge (the tabs), the black description bar with its
 * grey-to-black top edge, a light icon box, the orange pocket arrows (the
 * underline), and the screen's navy backdrop.
 */
private val AmethystBag = BagPalette(
    panelLayers = listOf(Color(0xFF061937) to 1, Color(0xFF4C8AF7) to 1, Color(0xFF061937) to 2),
    tabLayerCount = 2,
    fill = Color(0xFFBBD5FB), divider = Color(0xFF4C8AF7),
    text = Color(0xFF636363), textShadow = Color(0xFFD6D6CF),
    tabText = Color(0xFFFFFFFF), tabTextShadow = Color(0xFF636363), tabUnderline = Color(0xFFEC5F2A),
    descLayers = listOf(Color(0xFF525252) to 1, Color(0xFF424242) to 1, Color(0xFF313131) to 1, Color(0xFF212121) to 1),
    descFill = Color(0xFF080808),
    descText = Color(0xFFFFFFFF), descTextShadow = Color(0xFF636363),
    iconBox = Color(0xFFEFEFEF), iconBoxEdge = Color(0xFF848484),
    backdrop = Color(0xFF19418F) to Color(0xFF183877),
)

/**
 * Sampled from Radical Red's bag screen: a white list with dotted grey row
 * lines (1px dots with a light shadow) and the vanilla grey text, framed
 * black / white / black around a charcoal band like its "Items" title box
 * (the tabs: charcoal, white text), the charcoal description box in the same
 * edges, a black-edged white icon box, the orange pocket arrows (underline)
 * and the navy backdrop behind the list.
 */
private val RadicalRedBag = BagPalette(
    panelLayers = listOf(Color(0xFF000000) to 1, Color(0xFFFFFFFF) to 1, Color(0xFF000000) to 1, Color(0xFF313131) to 2),
    tabLayerCount = 3,
    fill = Color(0xFFFFFFFF), divider = Color(0xFF949494),
    text = Color(0xFF636363), textShadow = Color(0xFFD6D6CF),
    tabText = Color(0xFFFFFFFF), tabTextShadow = Color(0xFF636363), tabUnderline = Color(0xFFEC5F2A),
    descLayers = listOf(Color(0xFF000000) to 2, Color(0xFFFFFFFF) to 1, Color(0xFF000000) to 1),
    descFill = Color(0xFF313131),
    descText = Color(0xFFFFFFFF), descTextShadow = Color(0xFF636363),
    iconBox = Color(0xFFFFFFFF), iconBoxEdge = Color(0xFF000000),
    backdrop = Color(0xFF19418F) to Color(0xFF183877),
    dividerDash = 1 to 3, dividerWidth = 1, dividerShadow = Color(0xFFCECECE),
)

/**
 * Sampled from Odyssey's bag screen: light grey-cream rows split by solid
 * gold lines on a gold ground, the vanilla grey text, the dark pocket-title
 * box (the tabs: dark with white text, gold edge), the dark description bar,
 * a gold-edged light icon box, the orange pocket arrows (underline).
 */
private val OdysseyBag = BagPalette(
    panelLayers = listOf(Color(0xFFDDA839) to 2, Color(0xFF292929) to 2),
    tabLayerCount = 1,
    fill = Color(0xFFDEDED7), divider = Color(0xFFE6B743),
    text = Color(0xFF636363), textShadow = Color(0xFFD6D6CF),
    tabText = Color(0xFFFFFFFF), tabTextShadow = Color(0xFF636363), tabUnderline = Color(0xFFEC5F2A),
    descLayers = listOf(Color(0xFF292929) to 2), descFill = Color(0xFF393939),
    descText = Color(0xFFFFFFFF), descTextShadow = Color(0xFF636363),
    iconBox = Color(0xFFDEDED7), iconBoxEdge = Color(0xFFDDA839),
    backdrop = Color(0xFFE6B743) to Color(0xFFDDA839),
    dividerDash = null, dividerWidth = 2,
)

/**
 * Sampled from Emerald Rogue's bag (headless mGBA): Emerald's layout with a
 * navy / purple-grey frame around an amber band, a beige list, the amber
 * pocket banner, Emerald's white description and icon boxes, and navy stripes.
 */
private val RogueBag = BagPalette(
    panelLayers = listOf(Color(0xFF100042) to 2, Color(0xFF39315A) to 1, Color(0xFF636373) to 1, Color(0xFFEFB552) to 2),
    tabLayerCount = 3,
    fill = Color(0xFFEFDEBD), divider = null,
    text = Color(0xFF000000), textShadow = Color(0xFFD6D6CE),
    tabText = Color(0xFF000000), tabTextShadow = Color(0xFFBD9C5A), tabUnderline = Color(0xFFEC5F2A),
    descLayers = listOf(Color(0xFF7B7B7B) to 1), descFill = Color(0xFFFFFFFF),
    descText = Color(0xFF000000), descTextShadow = Color(0xFFD6D6CE),
    iconBox = Color(0xFFFFFFFF), iconBoxEdge = Color(0xFF7B7B7B),
    backdrop = Color(0xFF21087B) to Color(0xFF100042),
)

/**
 * Lazarus' and Seaglass' bag (both expansion's plain look): white windows in
 * a thin dark line, black text with no shadow, on flat cream.
 */
private val PlainExpansionBag = BagPalette(
    panelLayers = listOf(Color(0xFF293942) to 1, Color(0xFFFFFFFF) to 1),
    tabLayerCount = 1,
    fill = Color(0xFFFFFFFF), divider = null,
    text = Color(0xFF000000), textShadow = Color.Transparent,
    tabText = Color(0xFF000000), tabTextShadow = Color.Transparent, tabUnderline = Color(0xFFEC5F2A),
    descLayers = listOf(Color(0xFF293942) to 1), descFill = Color(0xFFFFFFFF),
    descText = Color(0xFF000000), descTextShadow = Color.Transparent,
    iconBox = Color(0xFFFFFFFF), iconBoxEdge = Color(0xFF293942),
    backdrop = Color(0xFFFFFFCE) to Color(0xFFFFFFCE),
)

/** R.O.W.E.'s bag: light grey windows in charcoal / grey lines, slate text with a grey shadow, on charcoal. */
private val RoweBag = BagPalette(
    panelLayers = listOf(Color(0xFF181818) to 1, Color(0xFFBDBDBD) to 1, Color(0xFFD6D6D6) to 1),
    tabLayerCount = 2,
    fill = Color(0xFFEFEFEF), divider = null,
    text = Color(0xFF39394A), textShadow = Color(0xFFA5A5A5),
    tabText = Color(0xFF39394A), tabTextShadow = Color(0xFFA5A5A5), tabUnderline = Color(0xFFEC5F2A),
    descLayers = listOf(Color(0xFF181818) to 1, Color(0xFFBDBDBD) to 1), descFill = Color(0xFFEFEFEF),
    descText = Color(0xFF39394A), descTextShadow = Color(0xFFA5A5A5),
    iconBox = Color(0xFFEFEFEF), iconBoxEdge = Color(0xFF181818),
    backdrop = Color(0xFF292929) to Color(0xFF292929),
)

/** Whether [ItemsBackdrop] replaces the party backdrop behind this game's Items tab. */
fun hasItemsBackdrop(game: GameKind): Boolean = bagPaletteFor(game).backdrop != null

/**
 * The game's own bag-screen backdrop - 2u horizontal stripes in two colors,
 * drawn natively - for games whose bag doesn't share the party menu's
 * backdrop (Emerald's purple/blue vs its olive party screen).
 */
@Composable
fun ItemsBackdrop(game: GameKind, modifier: Modifier = Modifier) {
    val (a, b) = bagPaletteFor(game).backdrop ?: return
    val m = rememberGbaTextMetrics()
    Canvas(modifier) {
        val stripe = 2 * m.px.toFloat()
        drawRect(b)
        var y = 0f
        while (y < size.height) {
            drawRect(a, Offset(0f, y), Size(size.width, stripe))
            y += 2 * stripe
        }
    }
}

/** Same grouping as the companion backdrop (rememberGameBackground). */
private fun bagPaletteFor(game: GameKind): BagPalette = when (game) {
    GameKind.EMERALD -> EmeraldBag
    GameKind.HEART_AND_SOUL -> HeartAndSoulBag
    GameKind.UNBOUND -> UnboundBag
    GameKind.AMETHYST -> AmethystBag
    GameKind.RADICAL_RED -> RadicalRedBag
    GameKind.ODYSSEY -> OdysseyBag
    GameKind.EMERALD_ROGUE -> RogueBag
    GameKind.LAZARUS, GameKind.EMERALD_SEAGLASS -> PlainExpansionBag
    GameKind.ROWE -> RoweBag
    // Too Many Types 2's bag is Emerald's; Gaia's and Celia's are FireRed's.
    GameKind.TMT2 -> EmeraldBag
    else -> FireRedBag
}

@Composable
fun ItemsScreen(items: List<ItemView>, modifier: Modifier = Modifier) {
    var selectedCategory by remember { mutableStateOf<Int?>(null) } // null = All
    var sort by remember { mutableStateOf(ItemSort.DEFAULT) }
    var selectedItem by remember { mutableStateOf<ItemView?>(null) }
    val pal = bagPaletteFor(activeGame)
    val caps = gameItemText(activeGame)
    val shown = if (caps == null) items else items.map {
        it.copy(
            name = caps.names[it.itemId] ?: it.name,
            description = caps.descriptions[it.itemId] ?: it.description,
        )
    }
    val m = rememberGbaTextMetrics()
    val u = m.u

    // Only categories the bag actually has something in - an empty pocket
    // (e.g. no Key Items exported by a pre-v4 ROM) shouldn't show as a dead
    // tab with nothing behind it.
    val categoriesPresent = pocketOrder.filter { p -> items.any { it.pocket == p } }
    val tabs = listOf<Int?>(null) + categoriesPresent
    val capsLabels = capsBagLabels(activeGame)
    val tabLabels = tabs.map { if (capsLabels) capsPocketLabel(it) else it?.let(::pocketLabel) ?: "All" }
    val sortLabel = { s: ItemSort -> if (capsLabels) s.label.uppercase() else s.label }

    val filtered = if (selectedCategory == null) shown else shown.filter { it.pocket == selectedCategory }
    val sorted = when (sort) {
        ItemSort.DEFAULT -> filtered
        ItemSort.NAME -> filtered.sortedBy { it.name }
        ItemSort.COUNT -> filtered.sortedWith(compareByDescending<ItemView> { it.quantity }.thenBy { it.name })
        ItemSort.TYPE -> filtered.sortedWith(
            compareBy<ItemView> { pocketOrder.indexOf(it.pocket).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                .thenBy { it.name },
        )
    }

    // Tab column is as wide as its widest label (incl. the sort button's,
    // which also carries its icon).
    val font = pixelFontFamily()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val tabWidth = remember(tabLabels, capsLabels, m, font) {
        val style = gbaTextStyle(font, m)
        val widestTab = tabLabels.maxOf { measurer.measure(it, style).size.width }
        val widestSort = ItemSort.entries.maxOf { measurer.measure(sortLabel(it), style).size.width }
        with(density) {
            maxOf(
                widestTab.toDp() + u * 20, // FolderTab's 10u side padding (frame included)
                widestSort.toDp() + u * (17 + SORT_ICON_U + 4),
            )
        }
    }
    val panelFrame = u * pal.panelFrame

    // Keep the tapped row on screen once the window shrinks for the
    // description box.
    val listState = rememberLazyListState()
    // The description box overlays the bottom of the whole tab; the window
    // shrinks by its (measured) height so no row hides behind it.
    var descHeight by remember { mutableStateOf(0.dp) }
    LaunchedEffect(selectedItem) {
        val index = sorted.indexOfFirst { it.itemId == selectedItem?.itemId }
        if (index < 0) return@LaunchedEffect
        // Two frames: the box measures itself, then the window shrinks to fit.
        withFrameNanos { }
        withFrameNanos { }
        val visible = listState.layoutInfo.visibleItemsInfo
        val viewportEnd = listState.layoutInfo.viewportEndOffset
        if (visible.none { it.index == index && it.offset + it.size <= viewportEnd }) {
            listState.animateScrollToItem(index)
        }
    }
    // A new order starts from the top of the list.
    LaunchedEffect(sort) { listState.scrollToItem(0) }

    Box(modifier = modifier) {
        // List window, starting where the folder tabs end.
        Column(Modifier.fillMaxSize().padding(start = tabWidth)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .drawBehind { drawLayeredBox(pal.panelLayers.inPx(u.toPx()), pal.fill, radius = 4 * u.toPx()) }
                    .padding(panelFrame + u * 3),
            ) {
                if (items.isEmpty() || sorted.isEmpty()) {
                    GbaText(
                        if (items.isEmpty()) "No items" else "No items in this pocket",
                        pal.text, pal.textShadow, m,
                        modifier = Modifier.padding(start = u * 8, top = u * 2),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(sorted, key = { it.itemId }) { item ->
                            ItemRow(item, pal, m, selected = item.itemId == selectedItem?.itemId) {
                                selectedItem = if (selectedItem?.itemId == item.itemId) null else item
                            }
                        }
                    }
                }
            }

            if (selectedItem != null) Spacer(Modifier.height(descHeight + u * 2))
        }

        // Folder tabs (the first flush with the window's top edge), with the
        // sort control pinned apart at the bottom - scrolls only on a screen too
        // short for all of them.
        BoxWithConstraints(Modifier.width(tabWidth + panelFrame).fillMaxHeight()) {
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
            ) {
                Column {
                    tabs.forEachIndexed { i, pocket ->
                        FolderTab(
                            tabLabels[i], pal, m,
                            selected = selectedCategory == pocket,
                            first = i == 0,
                            tabWidth = tabWidth, panelFrame = panelFrame,
                        ) {
                            selectedCategory = pocket
                            selectedItem = null
                        }
                        Spacer(Modifier.height(u * 2))
                    }
                }
                SortButton(sortLabel(sort), pal, m, width = tabWidth - u * 4) {
                    sort = ItemSort.entries[(sort.ordinal + 1) % ItemSort.entries.size]
                }
            }
        }

        // Full width of the tab, over the bottom of the folder tabs / sort
        // button when it has to be.
        selectedItem?.let { item ->
            CompanionBackHandler { selectedItem = null }
            ItemDescriptionBar(
                item, pal, m,
                onDismiss = { selectedItem = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { descHeight = with(density) { it.height.toDp() } },
            )
        }
    }
}

@Composable
private fun FolderTab(
    label: String,
    pal: BagPalette,
    m: GbaTextMetrics,
    selected: Boolean,
    first: Boolean,
    tabWidth: Dp,
    panelFrame: Dp,
    onClick: () -> Unit,
) {
    val u = m.u
    val noRipple = remember { MutableInteractionSource() }
    // Both are open on the right: an unselected tab ends flush against the
    // window's dark outline (tucked behind it). The selected one reaches onto
    // the window's gold frame so it reads as attached - its fill over the
    // window's outline + light line, its own dark border only over the
    // window's outline, so the two borders meet in a clean corner - and it
    // carries the orange underline the game draws under its pocket title.
    val reach = if (selected) pal.selectedFillReach else 0
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .width(tabWidth + u * reach)
            .drawBehind {
                val px = u.toPx()
                val radius = 4 * px
                val layers = pal.tabLayers.inPx(px)
                if (!selected) {
                    drawLayeredBox(layers, pal.tabFill, radius, openRight = true)
                    return@drawBehind
                }
                val windowEdge = tabWidth.toPx()
                val (border, borderWidth) = layers.first()
                val borderEnd = windowEdge + pal.selectedBorderReach * px
                clipRect(right = borderEnd) { drawLayeredBox(emptyList(), border, radius, openRight = true) }
                inset(left = borderWidth, top = borderWidth, right = 0f, bottom = borderWidth) {
                    drawLayeredBox(layers.drop(1), pal.tabFill, radius - borderWidth, openRight = true)
                }
                if (first) {
                    // Level with the window's top edge: run each border layer
                    // on past the window's rounded (4u) corner so each reads as
                    // one line, then square off the band's own leftover corner.
                    val cornerEnd = windowEdge + radius + px
                    var y = 0f
                    layers.forEachIndexed { i, (color, width) ->
                        val x = if (i == 0) borderEnd else size.width
                        drawRect(color, Offset(x, y), Size(cornerEnd - x, width))
                        y += width
                    }
                    val leftover = radius - y
                    if (leftover > 0f) drawRect(pal.tabFill, Offset(size.width, y), Size(leftover, leftover))
                }
            }
            .soundClickable(interactionSource = noRipple, indication = null, onClick = onClick)
            .padding(start = u * 10, end = u * (10 + reach), top = u * 6, bottom = u * 6),
    ) {
        // Tab-wide underline, drawn behind the label with its top half under
        // the glyphs' bottom edge (row 14 of the 16px em: baseline + shadow).
        GbaText(
            label, pal.tabText, pal.tabTextShadow, m,
            modifier = Modifier.fillMaxWidth().drawBehind {
                if (!selected) return@drawBehind
                val h = 4 * u.toPx()
                drawPixelRoundRect(
                    pal.tabUnderline, Offset(0f, 14 * m.fontPixel - h / 2), Size(size.width, h),
                    radius = h / 2,
                )
            },
        )
    }
}

/**
 * Deliberately not tab-shaped: a free-standing rounded button in the
 * description bar's colors, with a drawn up/down sort icon, pinned apart from
 * the tabs at the bottom of the column. Each tap moves to the next order.
 */
@Composable
private fun SortButton(label: String, pal: BagPalette, m: GbaTextMetrics, width: Dp, onClick: () -> Unit) {
    val u = m.u
    val noRipple = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .padding(start = u * 2)
            .width(width)
            .drawBehind {
                drawLayeredBox(pal.descLayers.inPx(u.toPx()), pal.descFill, radius = 6 * u.toPx())
            }
            .soundClickable(interactionSource = noRipple, indication = null, onClick = onClick)
            .padding(horizontal = u * 6, vertical = u * 6),
    ) {
        SortIcon(pal, m)
        Spacer(Modifier.width(u * 4))
        GbaText(label, pal.descText, pal.descTextShadow, m)
    }
}

/** ▲ over ▼, in font pixels so it matches the text beside it. */
@Composable
private fun SortIcon(pal: BagPalette, m: GbaTextMetrics) {
    Canvas(Modifier.size(m.u * SORT_ICON_U, m.lineHeight)) {
        val fp = m.fontPixel
        val cx = size.width / 2
        fun tri(dx: Float, dy: Float, up: Boolean) = Path().apply {
            val (tip, base) = if (up) 2f to 7f else 15f to 10f
            moveTo(cx - 4 * fp + dx, base * fp + dy)
            lineTo(cx + 4 * fp + dx, base * fp + dy)
            lineTo(cx + dx, tip * fp + dy)
            close()
        }
        for (up in listOf(true, false)) {
            drawPath(tri(fp, fp, up), pal.descTextShadow)
            drawPath(tri(0f, 0f, up), pal.descText)
        }
    }
}

private const val SORT_ICON_U = 10


/** Cursor slot + name + "× qty" on the window's dashed row line, like the vanilla bag list. */
@Composable
private fun ItemRow(item: ItemView, pal: BagPalette, m: GbaTextMetrics, selected: Boolean, onClick: () -> Unit) {
    val u = m.u
    val noRipple = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(m.rowHeight)
            .drawBehind {
                val divider = pal.divider ?: return@drawBehind
                val px = u.toPx()
                val w = pal.dividerWidth * px
                val shadow = pal.dividerShadow
                val y = size.height - w / 2 - (if (shadow != null) px else 0f)
                val dash = pal.dividerDash?.let { (on, off) -> PathEffect.dashPathEffect(floatArrayOf(on * px, off * px)) }
                if (shadow != null) {
                    drawLine(shadow, Offset(8 * px, y + (w + px) / 2), Offset(size.width, y + (w + px) / 2), strokeWidth = px, pathEffect = dash)
                }
                drawLine(divider, Offset(8 * px, y), Offset(size.width, y), strokeWidth = w, pathEffect = dash)
            }
            .soundClickable(interactionSource = noRipple, indication = null, onClick = onClick),
    ) {
        Box(Modifier.width(u * 8)) { if (selected) BagCursor(pal, m) }
        GbaText(
            item.name, pal.text, pal.textShadow, m,
            modifier = Modifier.weight(1f),
        )
        GbaText("× ${item.quantity}", pal.text, pal.textShadow, m, modifier = Modifier.padding(end = u * 4))
    }
}

/** The vanilla ▶ list cursor (Pixel Operator has no ▶ glyph, so it's drawn). */
@Composable
private fun BagCursor(pal: BagPalette, m: GbaTextMetrics) {
    val u = m.u
    Canvas(Modifier.size(u * 8, m.lineHeight)) {
        val px = m.fontPixel // in the font's pixels so it matches the caps (rows 4..13 of the 16px em)
        fun tri(dx: Float, dy: Float) = Path().apply {
            moveTo(dx, 4 * px + dy)
            lineTo(dx + 5 * px, 8.5f * px + dy)
            lineTo(dx, 13 * px + dy)
            close()
        }
        drawPath(tri(px, px), pal.textShadow)
        drawPath(tri(0f, 0f), pal.text)
    }
}

/**
 * The vanilla bottom description bar - a two-tone edge, the item's icon in a
 * white box and white outlined text (never truncated) - as a fully framed
 * box across the whole tab.
 * Tapping it (like tapping the row again) dismisses it.
 */
@Composable
private fun ItemDescriptionBar(item: ItemView, pal: BagPalette, m: GbaTextMetrics, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val u = m.u
    val noRipple = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLayeredBox(pal.descLayers.inPx(u.toPx()), pal.descFill, radius = 4 * u.toPx())
            }
            .soundClickable(interactionSource = noRipple, indication = null, onClick = onDismiss)
            .padding(start = u * 7, end = u * 7, top = u * 7, bottom = u * 7),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(u * 28)
                .drawBehind {
                    val px = u.toPx()
                    drawPixelRoundRect(pal.iconBoxEdge, radius = 3 * px)
                    drawPixelRoundRect(
                        pal.iconBox, Offset(px, px), Size(size.width - 2 * px, size.height - 2 * px),
                        radius = 2 * px,
                    )
                },
        ) {
            ItemIcon(item.iconAsset, size = u * 24)
        }
        GbaText(
            item.description.ifEmpty { item.name },
            pal.descText, pal.descTextShadow, m,
            maxLines = Int.MAX_VALUE,
            modifier = Modifier.padding(start = u * 8).weight(1f),
        )
    }
}
