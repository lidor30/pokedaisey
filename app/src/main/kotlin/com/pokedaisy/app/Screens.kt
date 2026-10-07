package com.pokedaisy.app

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display

/** The device's screens: is there a second one, and which. */
object Screens {
    /**
     * The second screen, or null with one. The Thor's bottom screen is a
     * presentation display. Other dual-screen handhelds (Retroid Pocket Duo /
     * Duo Lite) may expose theirs as a plain secondary display without that
     * flag, so fall back to any valid public display other than the built-in
     * main one.
     */
    fun second(context: Context): Display? {
        val displays = context.getSystemService(DisplayManager::class.java) ?: return null
        return displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull { it.isValid }
            ?: displays.displays.firstOrNull {
                it.isValid && it.displayId != Display.DEFAULT_DISPLAY && (it.flags and Display.FLAG_PRIVATE) == 0
            }
    }

    /** SWAP SCREENS only means something with two. */
    fun hasSecond(context: Context) = second(context) != null

    /** The second screen's width / height, or null with one. */
    fun secondAspect(context: Context): Float? {
        val size = Point().also { p -> second(context)?.let { @Suppress("DEPRECATION") it.getRealSize(p) } ?: return null }
        return if (size.x > 0 && size.y > 0) size.x.toFloat() / size.y else null
    }
}
