// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.model.ScheduleEngine
import com.uallsi.medaboutyou.ui.AppViewModelFactory
import com.uallsi.medaboutyou.ui.common.DoseToggleConfirmDialog
import com.uallsi.medaboutyou.ui.theme.MedColors

private val blockLabels = listOf(
    R.string.block_morning,
    R.string.block_afternoon,
    R.string.block_evening,
    R.string.block_night,
)

private fun blockOf(hour: Int): Int = when (hour) {
    in 5..11 -> 0
    in 12..16 -> 1
    in 17..20 -> 2
    else -> 3
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    val vm: TodayViewModel = viewModel(factory = AppViewModelFactory)
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refresh() }

    // Toggling a dose is confirmed first: marking taken defaults to confirm,
    // un-marking a taken dose defaults to cancel (see DoseToggleConfirmDialog).
    var pendingToggle by remember { mutableStateOf<Pair<TodayDose, Boolean>?>(null) }
    pendingToggle?.let { (dose, taken) ->
        DoseToggleConfirmDialog(
            medName = dose.occ.medName,
            time = dose.occ.timeLabel(),
            taken = taken,
            outsideWindow = taken && !ScheduleEngine.isWithinScheduledWindow(
                dose.occ.year, dose.occ.month, dose.occ.day, dose.occ.hour, dose.occ.minute, dose.occ.windowMinutes,
            ),
            onConfirm = {
                if (taken) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.toggle(dose, taken)
                pendingToggle = null
            },
            onDismiss = { pendingToggle = null },
        )
    }

    Box(modifier.fillMaxSize()) {
        if (state.loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
            return@Box
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { AdherenceCard(state.takenToday, state.totalToday, state.streak) }
                state.nextRefill?.let { refill ->
                    item { RefillBanner(refill.name, refill.year, refill.month, refill.day, refill.dosesLeft) }
                }

                // Time-of-day grouped timeline.
                val groups = state.doses.groupBy { blockOf(it.occ.hour) }
                for (block in 0..3) {
                    val items = groups[block].orEmpty().sortedWith(compareBy({ it.occ.hour }, { it.occ.minute }))
                    if (items.isEmpty()) continue
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(blockLabels[block]),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (items.any { it.isPast && it.occ.status.isEmpty() }) {
                                TextButton(onClick = { vm.takeAll(items) }) { Text(stringResource(R.string.take_all)) }
                            }
                        }
                    }
                    items(items, key = { it.occ.scheduleId.toString() + it.occ.keyIso }) { dose ->
                        DoseRow(dose, onToggle = { taken -> pendingToggle = dose to taken })
                    }
                }

                if (state.doses.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.today_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdherenceCard(taken: Int, total: Int, streak: Int) {
    val progress = if (total > 0) taken.toFloat() / total else 0f
    val allDone = total > 0 && taken == total
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(96.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round,
                )
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(96.dp),
                    color = if (allDone) MedColors.success else MedColors.warning,
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round,
                )
                Text("$taken/$total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(stringResource(R.string.todays_doses), style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        total == 0 -> stringResource(R.string.nothing_scheduled_today)
                        allDone -> stringResource(R.string.all_taken, total)
                        else -> stringResource(R.string.taken_progress, taken, total, total - taken)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = MedColors.warning)
                    Text(
                        "  " + pluralStringResource(R.plurals.day_streak, streak, streak),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun RefillBanner(name: String, year: Int, month: Int, day: Int, left: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MedColors.shortageContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MedColors.shortage)
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    stringResource(R.string.refill_soon, name),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(
                        R.string.runs_out,
                        "%04d-%02d-%02d".format(year, month, day),
                        pluralStringResource(R.plurals.doses_left, left, left),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DoseRow(dose: TodayDose, onToggle: (Boolean) -> Unit) {
    val occ = dose.occ
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = occ.status == "taken", enabled = dose.checkable, onCheckedChange = onToggle)
            Column(Modifier.weight(1f)) {
                Text("${occ.timeLabel()} — ${occ.medName}", style = MaterialTheme.typography.titleSmall)
                val statusRes = when {
                    occ.status == "taken" -> R.string.status_taken
                    dose.isPast -> R.string.status_due_now
                    else -> R.string.status_upcoming
                }
                Text(
                    stringResource(statusRes) + " · " +
                        pluralStringResource(R.plurals.in_stock, dose.stock, dose.stock),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (dose.stock <= 0) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = stringResource(R.string.no_stock),
                    tint = MedColors.shortage,
                )
            }
        }
    }
}
