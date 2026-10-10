package com.pokedaisy.app.companion.ui

import com.pokedaisy.app.companion.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokedaisy.app.companion.data.TypeMatchup
import com.pokedaisy.app.companion.ui.theme.QolColors
import com.pokedaisy.app.companion.ui.theme.pixelFontFamily

@Composable
fun TypeBadge(type: String, modifier: Modifier = Modifier, fontSize: TextUnit = 12.sp) {
    val color = QolColors.typeColors[type] ?: QolColors.muted
    Box(
        modifier = modifier
            .clip(PixelCornerShape(3.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(com.pokedaisy.app.companion.data.typeLabel(type), color = Color.White, fontSize = fontSize, fontFamily = pixelFontFamily())
    }
}

@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    if (status.isEmpty()) return
    Box(
        modifier = modifier
            .clip(PixelRoundedShape(4.dp))
            .background(QolColors.statusColor(status))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(status, color = Color.White, fontSize = 10.sp, fontFamily = pixelFontFamily())
    }
}

@Composable
fun MultiplierChip(label: String, pct: Int, modifier: Modifier = Modifier, fontSize: TextUnit = 10.sp) {
    val bg = QolColors.multiplierColor(pct)
    val fg = if (pct == 0) Color(0xFFF2B8BD) else Color.White
    Box(
        modifier = modifier
            .clip(PixelRoundedShape(4.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(label, color = fg, fontSize = fontSize, fontFamily = pixelFontFamily())
    }
}

/** "35" for a damaging move, "—" for a status move (power 0 in the ROM's own
 * move data, same convention every mainline Pokémon game uses on its own move
 * info screen). */
fun powerLabel(power: Int): String = if (power > 0) power.toString() else "—"

@Composable
fun HpBar(hp: Int, maxHp: Int, modifier: Modifier = Modifier) {
    val pct = if (maxHp == 0) 0f else (hp.toFloat() / maxHp).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(tr("HP"), color = QolColors.hpHigh, fontSize = 10.sp, fontFamily = pixelFontFamily())
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .weight(1f)
                .height(8.dp)
                .clip(PixelRoundedShape(2.dp))
                .background(Color(0xFF2C2418))
                .border(1.dp, QolColors.borderOuter, PixelRoundedShape(2.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pct)
                    .background(QolColors.hpColor(hp, maxHp)),
            )
        }
        Text("$hp/$maxHp", color = QolColors.muted, fontSize = 12.sp, fontFamily = pixelFontFamily())
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MatchupChipRow(heading: String, list: List<TypeMatchup>) {
    if (list.isEmpty()) return
    Text(heading, color = QolColors.muted, fontSize = 10.sp, fontFamily = pixelFontFamily(), modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
    androidx.compose.foundation.layout.FlowRow {
        list.forEach { m ->
            Box(modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)) {
                TypeBadgeWithLabel(m)
            }
        }
    }
}

/** A type badge with its multiplier ("FIRE 2x"). */
@Composable
fun TypeBadgeWithLabel(m: TypeMatchup, modifier: Modifier = Modifier) {
    val color = QolColors.typeColors[m.type] ?: QolColors.muted
    Box(
        modifier = modifier
            .clip(PixelCornerShape(3.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text("${com.pokedaisy.app.companion.data.typeLabel(m.type)} ${m.label}", color = Color.White, fontSize = 12.sp, fontFamily = pixelFontFamily())
    }
}

@Composable
fun PanelBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    com.pokedaisy.app.companion.ui.theme.GbaWindow(
        modifier = modifier,
        padding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) { content() }
}

/** A rounded rect with its corners cut to a single diagonal step instead of a
 * smooth arc - reads as a chunky pixel-art corner at typical button/badge
 * sizes. Shared by [TypeBadge] and [PlatinumButton] (in BattleControlsScreen)
 * so every pixel-corner element in the app uses the exact same shape. */
class PixelCornerShape(private val step: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { step.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(s, 0f)
            lineTo(w - s, 0f)
            lineTo(w - s, s)
            lineTo(w, s)
            lineTo(w, h - s)
            lineTo(w - s, h - s)
            lineTo(w - s, h)
            lineTo(s, h)
            lineTo(s, h - s)
            lineTo(0f, h - s)
            lineTo(0f, s)
            lineTo(s, s)
            close()
        }
        return Outline.Generic(path)
    }
}
