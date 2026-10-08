package com.pokedaisy.app

import android.os.Build
import android.util.Log
import android.view.KeyEvent
import java.io.File
import java.util.Properties

/**
 * Remappable mapping of physical keys/buttons to GBA buttons, from
 * `files/controls.properties`. One or more key names per GBA button
 * (comma-separated; any of them triggers it). The D-pad and analog stick stay
 * fixed — only the face/shoulder/menu buttons are here.
 */
object GbaControls {

    /** START2 / SELECT2: a second button for START / SELECT, X / Y by default, like the
     * DS games' X (menu) and Y (registered item). */
    enum class Btn(val bit: Int, val label: String) {
        A(MgbaCore.Key.A, "A"), B(MgbaCore.Key.B, "B"),
        L(MgbaCore.Key.L, "L"), R(MgbaCore.Key.R, "R"),
        START(MgbaCore.Key.START, "START"), SELECT(MgbaCore.Key.SELECT, "SELECT"),
        START2(MgbaCore.Key.START, "START 2"), SELECT2(MgbaCore.Key.SELECT, "SELECT 2");

        val prop get() = name.lowercase()
    }

    /**
     * AYN (Thor) and Retroid handhelds print A on the right face button and B on
     * the bottom one, like the GBA, but Android reports them by Xbox position:
     * the bottom button is BUTTON_A. A and B are not swapped for that by default
     * (GBA A is BUTTON_A everywhere; GAME BUTTONS remaps them); X / Y still are.
     */
    internal val swapFaceButtons: Boolean = run {
        val ids = listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL).map { it.orEmpty().lowercase() }
        ids[0] == "ayn" || ids[1] == "ayn" || ids.any { "retroid" in it }
    }

    private val DEFAULTS = linkedMapOf(
        "a" to "BUTTON_A, Z",
        "b" to "BUTTON_B, X",
        "l" to "BUTTON_L1, A",
        "r" to "BUTTON_R1, S",
        "start" to "BUTTON_START, ENTER",
        "select" to "BUTTON_SELECT, BACKSLASH, SHIFT_RIGHT",
        // The buttons printed X (top) and Y (left): Android names them by Xbox position,
        // so on the Nintendo-labelled handhelds the top one is BUTTON_Y, like A/B above.
        "start2" to if (swapFaceButtons) "BUTTON_Y" else "BUTTON_X",
        "select2" to if (swapFaceButtons) "BUTTON_X" else "BUTTON_Y",
    )

    private fun file(dir: File) = File(dir, "controls.properties")

    private fun readProps(dir: File): Properties {
        val p = Properties()
        val f = file(dir)
        if (f.isFile) runCatching { f.inputStream().use(p::load) }
        var changed = false
        for ((k, v) in DEFAULTS) if (!p.containsKey(k)) { p.setProperty(k, v); changed = true }
        if (changed) runCatching {
            f.outputStream().use { p.store(it, "PokeDaisy GBA button map — Android key names, comma-separated") }
        }
        return p
    }

    /** keycode -> GBA key bit. */
    fun load(dir: File): Map<Int, Int> {
        val props = readProps(dir)
        val out = HashMap<Int, Int>()
        for (btn in Btn.entries) {
            props.getProperty(btn.prop).orEmpty().split(',').forEach { n ->
                resolveKey(n.trim())?.let { out[it] = btn.bit }
            }
        }
        return out
    }

    fun rawBindings(dir: File): Map<Btn, List<String>> {
        val props = readProps(dir)
        return Btn.entries.associateWith {
            props.getProperty(it.prop).orEmpty().split(',').map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
        }
    }

    fun setBinding(dir: File, btn: Btn, keyCode: Int) {
        val props = readProps(dir)
        props.setProperty(btn.prop, Hotkeys.keyName(keyCode))
        runCatching { file(dir).outputStream().use { props.store(it, "PokeDaisy GBA button map") } }
    }

    private fun resolveKey(name: String): Int? {
        if (name.isEmpty()) return null
        return runCatching { KeyEvent::class.java.getField("KEYCODE_$name").getInt(null) }
            .onFailure { Log.w("pokedaisy", "unknown control key: $name") }.getOrNull()
    }
}
