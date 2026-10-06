package com.pokedaisey.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pokedaisey.app.companion.ui.GbaText
import com.pokedaisey.app.companion.ui.GbaTextMetrics
import com.pokedaisey.app.companion.ui.OptionButton
import com.pokedaisey.app.companion.ui.OptionColors
import com.pokedaisey.app.companion.ui.OptionListWindow
import com.pokedaisey.app.companion.ui.OptionOverlay
import com.pokedaisey.app.companion.ui.OptionTitleWindow
import com.pokedaisey.app.companion.ui.drawRowDivider
import androidx.compose.ui.draw.drawBehind
import java.io.File
import java.text.DateFormat
import java.util.Date

/** The library menu's INFO: everything known about a game, its ROM and its saves, by section. */
class GameInfo(val title: String, val sections: List<Section>) {
    class Section(val title: String, val rows: List<Pair<String, String>>)

    companion object {
        /** Blocking (reads headers; with [hashes], the whole ROM - CRC32, SHA1, the
         * companion check) - off the UI thread. Without [hashes] it's quick, for a
         * first look while those run. */
        fun read(context: Context, prefs: Prefs, rom: File, hashes: Boolean): GameInfo {
            val pending = "…"
            val name = prefs.romDisplayName(rom) ?: rom.nameWithoutExtension
            val code = RomIdentity.gameCode(rom)
            val sha1 = if (hashes) RomIdentity.sha1(rom) else null
            val crc = if (hashes) runCatching { SaveStates.crc32(rom) }.getOrNull() else null
            val game = sha1?.let { SteamGridDbGames.BY_SHA1[it]?.displayName } ?: baseGame(code)
            val linked = RomFolder.isLinked(prefs, rom)

            val gameRows = buildList {
                add("NAME" to name)
                add("GAME" to (game ?: "Unknown"))
                add("SECOND SCREEN" to if (!hashes) pending else if (CompanionSupport.isSupported(rom)) "Supported" else "Not supported")
                if (rom.absolutePath in prefs.recentRomPaths()) add("LAST PLAYED" to "#${prefs.recentRomPaths().indexOf(rom.absolutePath) + 1} in recent games")
                add("COVER" to when {
                    prefs.romCoverManual(rom) -> "Picked by you"
                    coverFile(context, rom).isFile -> "From SteamGridDB"
                    else -> "None"
                })
            }

            val romRows = buildList {
                add("FILE" to rom.name)
                add("FOLDER" to (rom.parent ?: "?"))
                add("FROM" to if (linked) "Your ROMs folder (played in place)" else prefs.romSourcePath(rom)?.let { "Imported from $it" } ?: "Imported")
                add("SIZE" to sizeLabel(rom.length()))
                add("MODIFIED" to dateLabel(rom.lastModified()))
                RomIdentity.headerTitle(rom)?.let { add("HEADER TITLE" to it) }
                add("GAME CODE" to (code ?: "?"))
                RomIdentity.revision(rom)?.let { add("REVISION" to it.toString()) }
                add("CRC32" to (crc ?: pending))
                add("SHA1" to (sha1 ?: pending))
            }

            val savesDir = SavesLocation.dir(context, prefs)
            val save = SavesLocation.resolve(savesDir, rom.nameWithoutExtension)
            val backups = GameSaves.backups(savesDir, rom.nameWithoutExtension)
            val saveRows = buildList {
                if (save.isFile) {
                    add("FILE" to save.absolutePath)
                    add("SIZE" to (sizeLabel(save.length()) + (GameSaves.kind(save.length())?.let { " · $it" } ?: "")))
                    add("MODIFIED" to dateLabel(save.lastModified()))
                } else {
                    add("FILE" to "None yet - made by the game's first save")
                    add("WILL BE" to save.absolutePath)
                }
                add("BACKUPS" to if (backups.isEmpty()) "None" else "${backups.size} · newest ${backups.first().name}")
            }

            val stateRows = buildList {
                if (crc == null) {
                    add("SLOTS" to pending)
                } else {
                    val st = SaveStates(context.getExternalFilesDir(null) ?: context.filesDir, crc)
                    val slots = st.allSlots().filter { it.present }
                    add("SLOTS" to "${slots.size} of ${SaveStates.SLOT_MAX - SaveStates.SLOT_MIN + 1} used")
                    slots.maxByOrNull { it.savedAt }?.let { add("NEWEST" to "Slot ${it.slot} · ${dateLabel(it.savedAt)}") }
                    add("RESUME" to when {
                        st.freshBootFile.exists() -> "Starts fresh from the save next time"
                        st.resumeFile.isFile -> "Picks up where you left off (${dateLabel(st.resumeFile.lastModified())})"
                        else -> "None"
                    })
                }
            }

            return GameInfo(
                name,
                listOf(
                    Section("GAME", gameRows),
                    Section("ROM", romRows),
                    Section("SAVE FILE", saveRows),
                    Section("SAVE STATES", stateRows),
                ),
            )
        }

        private fun baseGame(code: String?): String? = when (code) {
            "BPRE" -> "Pokémon FireRed"
            "BPGE" -> "Pokémon LeafGreen"
            "BPEE" -> "Pokémon Emerald"
            "AXVE" -> "Pokémon Ruby"
            "AXPE" -> "Pokémon Sapphire"
            else -> null
        }

        private fun coverFile(context: Context, rom: File) =
            File(File(context.getExternalFilesDir(null) ?: context.filesDir, "covers"), "${rom.name}.png")

        fun sizeLabel(bytes: Long): String = when {
            bytes >= 1 shl 20 -> "%.1f MB".format(bytes / 1048576.0)
            bytes >= 1 shl 10 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }

        fun dateLabel(ms: Long): String =
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))
    }
}

/** [info] over the library: a title window, its sections in one scrolling list window, CLOSE. */
@Composable
fun GameInfoDialog(info: GameInfo, m: GbaTextMetrics, small: GbaTextMetrics, onDismiss: () -> Unit) {
    OptionOverlay(onDismiss, Modifier.widthIn(max = 1100.dp).fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OptionTitleWindow("INFO", m, trailing = info.title, onBack = onDismiss)
            Spacer(Modifier.height(m.u * 4))
            OptionListWindow(m, Modifier.fillMaxWidth().weight(1f)) {
                // Two columns (GAME, ROM | SAVE FILE, SAVE STATES): the landscape screen shows it all at once.
                Row(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = m.u * 4),
                    horizontalArrangement = Arrangement.spacedBy(m.u * 12),
                ) {
                    val half = (info.sections.size + 1) / 2
                    listOf(info.sections.take(half), info.sections.drop(half)).forEach { column ->
                        Column(Modifier.weight(1f)) {
                            column.forEachIndexed { i, section -> InfoSection(section, i == 0, m, small) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(m.u * 4))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                OptionButton("CLOSE", m, emphasis = true, modifier = Modifier.widthIn(min = 160.dp), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun InfoSection(section: GameInfo.Section, first: Boolean, m: GbaTextMetrics, small: GbaTextMetrics) {
    GbaText(
        section.title, OptionColors.value, OptionColors.valueShadow, m,
        Modifier.padding(top = if (first) m.u * 2 else m.u * 8, bottom = m.u * 2),
    )
    section.rows.forEachIndexed { j, (label, value) ->
        Row(
            Modifier.fillMaxWidth()
                .then(
                    if (j < section.rows.lastIndex) {
                        Modifier.drawBehind { drawRowDivider(OptionColors.divider, m.u.toPx(), 0f) }
                    } else Modifier,
                )
                .padding(vertical = small.u * 3),
            horizontalArrangement = Arrangement.spacedBy(m.u * 6),
        ) {
            GbaText(label, OptionColors.muted, OptionColors.mutedShadow, small, Modifier.weight(0.34f))
            GbaText(value, OptionColors.label, OptionColors.labelShadow, small, Modifier.weight(0.66f), maxLines = 3)
        }
    }
}
