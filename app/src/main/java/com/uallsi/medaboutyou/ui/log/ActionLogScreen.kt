// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.log

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.data.local.ActionCatalog
import com.uallsi.medaboutyou.data.local.ActionLogEntity
import com.uallsi.medaboutyou.ui.AppViewModelFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** The local activity log: every user action with its timestamp, category, id and clear text. */
@Composable
fun ActionLogScreen(modifier: Modifier = Modifier) {
    val vm: ActionLogViewModel = viewModel(factory = AppViewModelFactory)
    val records by vm.records.collectAsStateWithLifecycle(initialValue = emptyList())

    Box(modifier.fillMaxSize()) {
        if (records.isEmpty()) {
            Text(
                stringResource(R.string.activity_log_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(records, key = { it.id }) { rec ->
                    ActionLogRow(rec)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ActionLogRow(rec: ActionLogEntity) {
    ListItem(
        overlineContent = { Text(formatTimestamp(rec.timestamp)) },
        headlineContent = { Text(rec.text) },
        supportingContent = {
            // Category and id are shown as BOTH the number and the localized label.
            Text(
                stringResource(
                    R.string.activity_log_meta,
                    rec.category,
                    stringResource(categoryLabel(rec.category)),
                    rec.actionId,
                    stringResource(actionLabel(rec.actionId)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

private val TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatTimestamp(iso: String): String =
    runCatching { LocalDateTime.parse(iso).format(TS_FORMAT) }.getOrDefault(iso)

private fun categoryLabel(category: Int): Int = when (category) {
    ActionCatalog.CAT_MEDICATION -> R.string.action_cat_medication
    ActionCatalog.CAT_SCHEDULE -> R.string.action_cat_schedule
    ActionCatalog.CAT_INVENTORY -> R.string.action_cat_inventory
    ActionCatalog.CAT_DATA -> R.string.action_cat_data
    else -> R.string.action_cat_settings
}

private fun actionLabel(actionId: Int): Int = when (actionId) {
    ActionCatalog.DOSE_TAKEN -> R.string.action_act_dose_taken
    ActionCatalog.DOSE_UNTAKEN -> R.string.action_act_dose_untaken
    ActionCatalog.DOSES_TAKEN_ALL -> R.string.action_act_doses_all
    ActionCatalog.SCHEDULE_CREATED -> R.string.action_act_schedule_created
    ActionCatalog.SCHEDULE_EDITED -> R.string.action_act_schedule_edited
    ActionCatalog.DOSE_EDITED -> R.string.action_act_dose_edited
    ActionCatalog.SCHEDULE_CANCELLED -> R.string.action_act_schedule_cancelled
    ActionCatalog.SCHEDULE_PAUSED -> R.string.action_act_schedule_paused
    ActionCatalog.SCHEDULE_RESUMED -> R.string.action_act_schedule_resumed
    ActionCatalog.STOCK_SET -> R.string.action_act_stock_set
    ActionCatalog.STOCK_ADDED -> R.string.action_act_stock_added
    ActionCatalog.SHOPPING_REMOVED -> R.string.action_act_shopping_removed
    ActionCatalog.BACKUP_EXPORTED -> R.string.action_act_backup_exported
    ActionCatalog.BACKUP_RESTORED -> R.string.action_act_backup_restored
    ActionCatalog.REMINDERS_TOGGLED -> R.string.action_act_reminders
    ActionCatalog.BOOT_TOGGLED -> R.string.action_act_boot
    ActionCatalog.CAREGIVER_ADDED -> R.string.action_act_caregiver_added
    ActionCatalog.CAREGIVER_REMOVED -> R.string.action_act_caregiver_removed
    else -> R.string.action_act_unknown
}
