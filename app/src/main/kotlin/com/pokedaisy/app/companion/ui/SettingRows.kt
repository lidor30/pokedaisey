package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pokedaisy.app.companion.i18n.tr

/**
 * One line of a settings list: [value] (red, in the value column) or null for a plain
 * command row; [header] = a group title. [badge] goes after the value ("ALPHA"),
 * [labelBadge] after the label ("BETA"); [enabled] false greys the row out.
 */
class SettingRow(
    val label: String, val value: String?, val badge: String? = null, val header: Boolean = false,
    val enabled: Boolean = true, val labelBadge: String? = null,
    val labelIcon: (@Composable () -> Unit)? = null, val onClick: () -> Unit,
)

/** A group's title row in [GroupedRows]. */
fun groupTitle(label: String) = SettingRow(label, null, header = true) {}

/**
 * A settings list in groups, shared by both screens' SETTINGS: group titles (red, like
 * the bag's pocket names) over [OptionLine] rows, one column at the normal text size
 * (big touch targets), scrolling when it outgrows the window, then [footer]. [onClick]
 * gets the row's index in [rows]; [cursor] is the white row (-1: none).
 */
@Composable
fun GroupedRows(
    rows: List<SettingRow>,
    m: GbaTextMetrics,
    cursor: Int,
    onClick: (Int) -> Unit,
    footer: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        rows.forEachIndexed { i, row ->
            if (row.header) {
                GbaText(
                    tr(row.label), OptionColors.value, OptionColors.valueShadow, m,
                    modifier = Modifier.padding(start = m.u * 8, top = m.u * (if (i == 0) 1 else 6), bottom = m.u),
                )
            } else {
                OptionLine(
                    row.label, row.value, selected = i == cursor, m, height = m.rowHeight * 1.2f,
                    divider = rows.getOrNull(i + 1)?.header == false, labelBadge = row.labelBadge, labelIcon = row.labelIcon,
                    valueBadge = row.badge, enabled = row.enabled,
                ) { onClick(i) }
            }
        }
        footer()
    }
}
