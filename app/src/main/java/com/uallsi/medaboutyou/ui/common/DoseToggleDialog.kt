// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uallsi.medaboutyou.R

/**
 * Confirmation shown before a dose-status toggle.
 *
 * - Marking a dose **taken** ([taken] = true): a confirmation with the full
 *   description whose **default (emphasised) button confirms** the action.
 * - **Un-marking** a taken dose ([taken] = false): a warning with the full
 *   explanation whose **default (emphasised) button cancels**; the destructive
 *   "unmark" is the secondary, error-coloured action.
 */
@Composable
fun DoseToggleConfirmDialog(
    medName: String,
    time: String,
    taken: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    outsideWindow: Boolean = false,
) {
    if (taken) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.confirm_take_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.confirm_take_message, medName, time))
                    if (outsideWindow) {
                        // Taken late / outside [scheduled ± window] — flag it.
                        Text(
                            stringResource(R.string.confirm_take_outside_window),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm_take_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.untake_warn_title)) },
            text = { Text(stringResource(R.string.untake_warn_message, medName, time)) },
            // Default action is Cancel: the emphasised confirm slot keeps the dose taken.
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
            dismissButton = {
                TextButton(onClick = onConfirm) {
                    Text(
                        stringResource(R.string.untake_warn_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }
}
