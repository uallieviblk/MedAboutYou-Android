// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.model.Occurrence
import com.uallsi.medaboutyou.ui.common.Stepper
import com.uallsi.medaboutyou.ui.common.TimeField

/**
 * Edit (retime / re-window) or skip a single calendar occurrence, with a scope
 * selector: "This dose only" stores a one-off override; "This and following"
 * splits the schedule from this date on. Surfaces the existing
 * `ScheduleRepository.editSingle` / `splitFrom` logic.
 *
 * [onSave] receives `(hour, minute, window, cancelled, applyToFollowing)`.
 * [allowFollowing] hides the scope selector for one-shot (ONCE) schedules,
 * where "following" doses don't exist and a split would duplicate the series.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditOccurrenceDialog(
    occurrence: Occurrence,
    onSave: (Int, Int, Int, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
    allowFollowing: Boolean = true,
) {
    var hour by remember { mutableIntStateOf(occurrence.hour) }
    var minute by remember { mutableIntStateOf(occurrence.minute) }
    var window by remember { mutableIntStateOf(occurrence.windowMinutes) }
    var skip by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_dose_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${occurrence.medName} · " +
                        "%04d-%02d-%02d".format(occurrence.year, occurrence.month, occurrence.day),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!skip) {
                    TimeField(stringResource(R.string.time_of_dose), hour, minute) { h, m ->
                        hour = h
                        minute = m
                    }
                    Stepper(stringResource(R.string.window_label), window, 0, 360, step = 5) { window = it }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = skip, role = Role.Checkbox) { skip = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = skip, onCheckedChange = null)
                    Text(stringResource(R.string.skip_this_dose), style = MaterialTheme.typography.bodyMedium)
                }

                if (allowFollowing) {
                    Text(stringResource(R.string.edit_scope), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !following,
                            onClick = { following = false },
                            label = { Text(stringResource(R.string.edit_scope_this)) },
                        )
                        FilterChip(
                            selected = following,
                            onClick = { following = true },
                            label = { Text(stringResource(R.string.edit_scope_following)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(hour, minute, window, skip, following) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
