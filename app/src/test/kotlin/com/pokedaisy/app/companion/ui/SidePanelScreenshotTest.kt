package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.pokedaisy.app.companion.data.DecompIconSource
import com.pokedaisy.app.companion.data.GameKind
import com.pokedaisy.app.companion.data.NATIVE_FIRERED_REV1
import com.pokedaisy.app.companion.data.RomArt
import com.pokedaisy.app.companion.data.RomFileReader
import com.pokedaisy.app.companion.data.activeGame
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A single-screen device's side panel (SidePanel): the companion beside the
 * game, locked, and over it, with the lock tab ([SidePanelHandle]) on its edge,
 * on a Retroid Pocket 6-sized screen (1920x1080; its 3 px GBA pixel), plus
 * [tab] - the tab's corner close up. The game is a drawn stand-in (Paparazzi
 * can't run the core). See CompanionScreenshotTest.
 *
 *     ./gradlew :app:recordPaparazziDebug --tests '*SidePanelScreenshotTest'
 */
class SidePanelScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1920, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE,
            density = Density.XHIGH, // 2.0: the same 3 px GBA pixel as the RP6 (2.3)
        ),
        theme = "android:Theme.Material.NoActionBar",
    )

    @Before
    fun game() {
        activeGame = GameKind.FIRERED
        // The side panel lives in the game's activity: the companion's in-game look, not the Library's theme.
        OptionColors.inGame = true
        val art = java.io.File("build/rom-art-paparazzi").also { RomArt.dirOverride = it }
        for (path in listOf(RomFileReader.FIRERED_REV1_PATH, RomFileReader.EMERALD_PATH)) {
            java.io.File(path).takeIf { it.isFile }?.let { RomArt.extractTo(it.readBytes(), art) }
        }
        RomFileReader.load(RomFileReader.FIRERED_REV1_PATH)?.let { rom ->
            DecompIconSource.reader = rom
            DecompIconSource.tables = NATIVE_FIRERED_REV1.iconTables
            SampleCompanion.snapshot.party.forEach { DecompIconSource.get(it.species) }
        }
    }


    @Test fun locked() = paparazzi.snapshot { Scene(docked = true) }

    @Test fun over() = paparazzi.snapshot { Scene(docked = false) }

    /** Closed: the game alone, the open tab on the screen's right edge. */
    @Test fun closed() = paparazzi.snapshot {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            FakeGame(Modifier.offset(x = px(150)).size(px(1620), px(1080)))
            Box(Modifier.align(Alignment.BottomEnd).padding(bottom = px(28))) { SidePanelOpenTab {} }
        }
    }

    /** The lock tab's corner of the screen at 1:1, locked and over the game, then the tabs alone at 3x. */
    @Test
    fun tab() = paparazzi.snapshot {
        Row(
            Modifier.fillMaxSize().background(Color(0xFF303030)).padding(px(12)),
            horizontalArrangement = Arrangement.spacedBy(px(12)),
        ) {
            Crop(docked = true)
            Crop(docked = false)
            val tabs: List<@Composable () -> Unit> = listOf(
                { SidePanelHandle(true) {} }, { SidePanelHandle(false) {} }, { SidePanelOpenTab {} }, { SidePanelCloseTab {} },
            )
            for (tab in tabs) {
                Box(Modifier.size(px(CROP_W), px(CROP_H)).background(Color.Black), contentAlignment = Alignment.Center) {
                    Box(Modifier.graphicsLayer(scaleX = 3f, scaleY = 3f)) { tab() }
                }
            }
        }
    }

    @Composable
    private fun Crop(docked: Boolean) {
        Box(Modifier.size(px(CROP_W), px(CROP_H)).clipToBounds()) {
            Box(
                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                    .offset(x = -px(SCREEN_W - PANEL_W - CROP_W + 90))
                    .requiredSize(px(SCREEN_W), px(SCREEN_H)),
            ) { Scene(docked) }
        }
    }

    /** The RP6's screen, laid out like SidePanel does at its default half-and-half. */
    @Composable
    private fun Scene(docked: Boolean) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (docked) {
                // Half the screen: exactly 4x, centred in its half.
                FakeGame(Modifier.offset(y = px(220)).size(px(960), px(640)))
            } else {
                FakeGame(Modifier.offset(x = px(150)).size(px(1620), px(1080)))
            }
            Box(Modifier.align(Alignment.TopEnd).width(px(PANEL_W)).fillMaxHeight()) {
                SidePanelCompanion {
                    CompanionScreen(SampleCompanion.snapshot, SampleCompanion.Slots, SampleCompanion.Settings())
                }
            }
            // SidePanel's HANDLE_TOP_DP / TAB_MARGIN_DP (12 dp on the RP6), and its LOCKED_TAB_ALPHA.
            Box(Modifier.align(Alignment.TopEnd).padding(end = px(PANEL_W), top = px(28))) { SidePanelHandle(docked) {} }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = px(PANEL_W), bottom = px(28))
                    .alpha(if (docked) 0.35f else 1f),
            ) { SidePanelCloseTab {} }
        }
    }

    @Composable
    private fun px(v: Int): Dp = with(LocalDensity.current) { v.toDp() }

    private companion object {
        const val SCREEN_W = 1920
        const val SCREEN_H = 1080
        const val PANEL_W = 960
        const val CROP_W = 290
        const val CROP_H = 300
    }
}

/** A GBA screen's worth of something game-like: sky, striped ground, a text box. */
@Composable
internal fun FakeGame(modifier: Modifier) {
    Canvas(modifier) {
        val k = size.width / 240f
        fun r(x: Int, y: Int, w: Int, h: Int, c: Color) = drawRect(c, Offset(x * k, y * k), Size(w * k, h * k))
        r(0, 0, 240, 104, Color(0xFFE8F0E8))
        for (i in 0 until 7) r(0, 104 + i * 8, 240, 8, if (i % 2 == 0) Color(0xFF5C9C90) else Color(0xFF4C8C80))
        r(100, 30, 40, 70, Color(0xFFB8A8E0)) // someone standing there
        r(108, 18, 24, 16, Color(0xFFE8D8A8))
        r(4, 112, 232, 44, Color(0xFF6888B0))
        r(6, 114, 228, 40, Color.White)
        r(14, 122, 120, 8, Color(0xFF606060))
        r(14, 138, 150, 8, Color(0xFF606060))
    }
}
