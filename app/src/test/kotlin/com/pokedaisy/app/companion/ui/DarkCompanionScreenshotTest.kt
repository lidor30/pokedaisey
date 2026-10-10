package com.pokedaisy.app.companion.ui

import com.pokedaisy.app.companion.ui.theme.DarkMode
import org.junit.After
import org.junit.Before

/**
 * Every [CompanionScreenshotTest] shot again in DARK MODE, same names with this class's prefix:
 *
 *     ./gradlew :app:recordPaparazziDebug --tests '*DarkCompanionScreenshotTest'
 */
class DarkCompanionScreenshotTest : CompanionScreenshotTest() {
    @Before fun darkOn() { DarkMode.override = true }

    @After fun darkOff() { DarkMode.override = null }
}
