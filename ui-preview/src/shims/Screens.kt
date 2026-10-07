package com.pokedaisy.app

/** Stand-in for the app's Screens (DisplayManager): the Thor, a 1240x1080 second screen. */
object Screens {
    fun hasSecond(context: android.content.Context) = true
    fun secondAspect(context: android.content.Context): Float? = 1240f / 1080f
}
