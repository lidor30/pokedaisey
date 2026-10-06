package com.pokedaisey.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pokedaisey.app.companion.ui.AppBackdrop
import com.pokedaisey.app.companion.ui.GbaText
import com.pokedaisey.app.companion.ui.GbaTextMetrics
import com.pokedaisey.app.companion.ui.OptionButton
import com.pokedaisey.app.companion.ui.OptionColors
import com.pokedaisey.app.companion.ui.OptionLine
import com.pokedaisey.app.companion.ui.OptionListWindow
import com.pokedaisey.app.companion.ui.OptionTextField
import com.pokedaisey.app.companion.ui.OptionTitleWindow

/**
 * First-time setup, on the library's screen: link a ROMs folder, pick where saves
 * go, add a SteamGridDB key - each one skippable, each one also in Settings.
 * [LibraryActivity] owns the folder pickers and background work and feeds this.
 */
internal class SetupState {
    enum class Step { ROMS, SAVES, COVERS }

    var step by mutableStateOf(Step.ROMS)
    /** Opened by Settings' RUN SETUP: BACK on the first step returns there. */
    var fromSettings by mutableStateOf(false)
    /** Whether BACK has somewhere to go: an earlier step, or Settings. */
    val canGoBack get() = step.ordinal > 0 || fromSettings
    /** False until All files access is granted - picking a folder asks for it first. */
    var hasAccess by mutableStateOf(true)
    /** The folder pick waiting on Android's All files access page, resumed on return. */
    var awaitingAccess: Step? = null

    var romsFolder by mutableStateOf<String?>(null)
    var scanning by mutableStateOf(false)
    var checked by mutableIntStateOf(0)
    var toCheck by mutableIntStateOf(0)
    /** Names of the supported games in [romsFolder]; null until a scan finishes. */
    var found by mutableStateOf<List<String>?>(null)

    /** Null = the app's own saves folder. */
    var savesDir by mutableStateOf<String?>(null)
    var suggestions by mutableStateOf<List<SavesLocation.Suggestion>>(emptyList())

    var apiKey by mutableStateOf("")
    var keySaved by mutableStateOf(false)
    val covers = CoverSync()
}

@Composable
internal fun SetupScreen(
    s: SetupState,
    m: GbaTextMetrics,
    small: GbaTextMetrics,
    onChooseRoms: () -> Unit,
    onChooseSaves: () -> Unit,
    onUseSaves: (String?) -> Unit,
    onOpenSteamGridDb: () -> Unit,
    onSaveKey: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val steps = SetupState.Step.entries
    Box(Modifier.fillMaxSize()) {
        AppBackdrop()
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            OptionTitleWindow(
                "WELCOME TO POKéDAISEY", m,
                trailing = "${s.step.ordinal + 1} / ${steps.size}",
                onBack = if (s.canGoBack) onBack else null,
            )
            Spacer(Modifier.height(m.u * 4))
            OptionListWindow(m, Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = m.u * 4)) {
                    when (s.step) {
                        SetupState.Step.ROMS -> RomsStep(s, m, small, onChooseRoms)
                        SetupState.Step.SAVES -> SavesStep(s, m, small, onChooseSaves, onUseSaves)
                        SetupState.Step.COVERS -> CoversStep(s, m, small, onOpenSteamGridDb, onSaveKey)
                    }
                }
            }
            Spacer(Modifier.height(m.u * 4))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (s.canGoBack) {
                    OptionButton("BACK", m, enabled = !s.scanning, modifier = Modifier.widthIn(min = 160.dp), onClick = onBack)
                }
                Spacer(Modifier.weight(1f))
                val last = s.step == steps.last()
                val skipping = when (s.step) {
                    SetupState.Step.ROMS -> s.romsFolder == null
                    SetupState.Step.SAVES -> false
                    SetupState.Step.COVERS -> !s.keySaved
                }
                OptionButton(
                    when {
                        skipping -> "SKIP"
                        last -> "DONE"
                        else -> "NEXT"
                    },
                    m, emphasis = !skipping, enabled = !s.scanning,
                    modifier = Modifier.widthIn(min = 160.dp),
                    onClick = onNext,
                )
            }
        }
    }
}

@Composable
private fun Heading(text: String, m: GbaTextMetrics) {
    GbaText(text, OptionColors.label, OptionColors.labelShadow, m, Modifier.padding(top = m.u * 2, bottom = m.u * 2))
}

@Composable
private fun Para(text: String, small: GbaTextMetrics, muted: Boolean = false) {
    GbaText(
        text,
        if (muted) OptionColors.muted else OptionColors.label,
        if (muted) OptionColors.mutedShadow else OptionColors.labelShadow,
        small, maxLines = Int.MAX_VALUE, modifier = Modifier.padding(bottom = small.u * 4),
    )
}

@Composable
private fun RomsStep(s: SetupState, m: GbaTextMetrics, small: GbaTextMetrics, onChoose: () -> Unit) {
    Heading("YOUR ROMS FOLDER", m)
    Para(
        "Pick the folder you keep your games in. PokéDaisey adds every game from it that the second " +
            "screen supports, plays them right where they are (nothing is copied), and looks for new " +
            "ones each time you open the app.",
        small,
    )
    Para(
        "This is optional: skip it and add games one at a time with + in the library. You can link " +
            "a folder later in SETTINGS > FOLDERS.",
        small, muted = true,
    )
    if (!s.hasAccess) {
        Para(
            "Android asks you to allow All files access first, so PokéDaisey can open games and saves " +
                "outside its own folder. Turn it on and come back here.",
            small, muted = true,
        )
    }
    OptionButton(if (s.romsFolder == null) "CHOOSE FOLDER" else "CHANGE FOLDER", m, emphasis = s.romsFolder == null, enabled = !s.scanning, onClick = onChoose)
    val folder = s.romsFolder ?: return
    Spacer(Modifier.height(m.u * 4))
    GbaText(folder, OptionColors.muted, OptionColors.mutedShadow, small, maxLines = 3)
    Spacer(Modifier.height(m.u * 3))
    val found = s.found
    when {
        s.scanning -> {
            GbaText("LOOKING FOR GAMES… ${s.checked} / ${s.toCheck}", OptionColors.label, OptionColors.labelShadow, m)
            Spacer(Modifier.height(m.u * 3))
            SyncProgressBar(if (s.toCheck > 0) s.checked.toFloat() / s.toCheck else 0f, m)
        }
        found == null -> {}
        found.isEmpty() -> Para(
            "No supported games in there. Pick another folder, or skip and add games with + later.", small,
        )
        else -> {
            GbaText("FOUND ${found.size} GAME${if (found.size == 1) "" else "S"}", OptionColors.value, OptionColors.valueShadow, m)
            Spacer(Modifier.height(m.u * 2))
            // Two columns: the landscape screen fits more games without scrolling.
            found.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(vertical = small.u), horizontalArrangement = Arrangement.spacedBy(m.u * 8)) {
                    pair.forEach { GbaText(it, OptionColors.label, OptionColors.labelShadow, small, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SavesStep(
    s: SetupState,
    m: GbaTextMetrics,
    small: GbaTextMetrics,
    onChoose: () -> Unit,
    onUse: (String?) -> Unit,
) {
    Heading("GAME SAVES", m)
    Para(
        "Where should your saves go? If you already play these games in another emulator, pick its " +
            "save folder (RetroArch's, for one) and carry on with the same saves: a game finds its save " +
            "by the ROM's file name (.sav or .srm).",
        small,
    )
    val listed = s.suggestions.map { it.dir.absolutePath }
    OptionLine("KEEP THEM IN POKéDAISEY", "DEFAULT", selected = s.savesDir == null, m, labelWeight = 0.75f, divider = true) { onUse(null) }
    s.suggestions.forEach { sug ->
        OptionLine(
            shortPath(sug.dir.absolutePath), "${sug.saves} SAVE${if (sug.saves == 1) "" else "S"}",
            selected = s.savesDir == sug.dir.absolutePath, m, labelWeight = 0.75f, divider = true,
        ) { onUse(sug.dir.absolutePath) }
    }
    s.savesDir?.takeIf { it !in listed }?.let { dir ->
        OptionLine(shortPath(dir), null, selected = true, m, labelWeight = 0.75f, divider = true) { }
    }
    OptionLine("CHOOSE ANOTHER FOLDER…", null, selected = false, m, onClick = onChoose)
    Spacer(Modifier.height(m.u * 4))
    if (!s.hasAccess) {
        Para("Choosing a folder asks for All files access first, as games and saves are opened by path.", small, muted = true)
    }
    Para("You can change this later in SETTINGS > FOLDERS.", small, muted = true)
}

@Composable
private fun CoversStep(
    s: SetupState,
    m: GbaTextMetrics,
    small: GbaTextMetrics,
    onOpenSteamGridDb: () -> Unit,
    onSaveKey: () -> Unit,
) {
    Heading("COVER ART (OPTIONAL)", m)
    Para(
        "Library covers come from SteamGridDB and need a free API key: sign in at steamgriddb.com, open " +
            "Preferences > API, copy the key and paste it below.",
        small,
    )
    Para("Skip this if you like - you can add a key any time in SETTINGS > COVER ART.", small, muted = true)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.u * 4)) {
        OptionButton("OPEN STEAMGRIDDB", m, onClick = onOpenSteamGridDb)
        OptionTextField(s.apiKey, { s.apiKey = it }, m, Modifier.weight(1f), placeholder = "paste key here")
        OptionButton(
            if (s.covers.running) "FETCHING…" else "SAVE", m, emphasis = true,
            enabled = !s.covers.running && s.apiKey.isNotBlank(), onClick = onSaveKey,
        )
    }
    if (s.covers.running || s.covers.log.isNotEmpty()) {
        Spacer(Modifier.height(m.u * 6))
        CoverSyncStatus(s.covers, m)
    }
}

/** A folder as the player knows it: internal storage's prefix dropped, an SD card named as one. */
private fun shortPath(path: String): String = when {
    path.startsWith("/storage/emulated/0/") -> path.removePrefix("/storage/emulated/0/")
    path.startsWith("/storage/") -> "SD CARD/" + path.removePrefix("/storage/").substringAfter('/')
    else -> path
}
