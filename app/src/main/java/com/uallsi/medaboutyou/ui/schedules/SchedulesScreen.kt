// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.schedules

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.model.PeriodUnit
import com.uallsi.medaboutyou.model.Schedule
import com.uallsi.medaboutyou.ui.AppViewModelFactory
import com.uallsi.medaboutyou.ui.calendar.ScheduleEditorDialog
import com.uallsi.medaboutyou.ui.calendar.periodUnitLabel
import com.uallsi.medaboutyou.ui.common.DateField
import com.uallsi.medaboutyou.ui.theme.MedColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Dedicated page listing the active prescriptions (moved out of the calendar). */
@Composable
fun SchedulesScreen(onAddMedicine: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SchedulesViewModel = viewModel(factory = AppViewModelFactory)
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    // Re-query on every (re)entry: the VM is retained across tab switches and only
    // self-refreshes after its own mutations, so a schedule added from the
    // Search → Detail → Add flow would otherwise not appear until a process restart.
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refresh() }
    var pendingCancel by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var editing by remember { mutableStateOf<Schedule?>(null) }
    var pausing by remember { mutableStateOf<Long?>(null) } // schedule id to pause

    Box(modifier.fillMaxSize()) {
        if (schedules.isEmpty()) {
            Text(
                stringResource(R.string.schedules_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 88.dp),
            ) {
                items(schedules, key = { it.id }) { sch ->
                    ScheduleCard(
                        sch,
                        onEdit = { editing = sch },
                        onPauseRequested = { pausing = sch.id },
                        onResume = { vm.setPause(sch.id, false, "") },
                        onCancel = { pendingCancel = sch.id to sch.medName },
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAddMedicine,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.add_medicine)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("addMedicineFab"),
        )
    }

    editing?.let { sch ->
        ScheduleEditorDialog(
            prefillName = sch.medName,
            prefillSource = sch.medSource,
            prefillExtId = sch.medExtId,
            existing = sch,
            onCreate = {
                vm.applyEdit(it);
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    pendingCancel?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { pendingCancel = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MedColors.error) },
            title = { Text(stringResource(R.string.confirm_cancel_title)) },
            text = { Text("$name\n\n" + stringResource(R.string.confirm_cancel_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.cancel(id);
                        pendingCancel = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MedColors.error),
                ) { Text(stringResource(R.string.cancel_prescription)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancel = null }) { Text(stringResource(R.string.keep)) }
            },
        )
    }

    pausing?.let { id ->
        val today = LocalDate.now()
        AlertDialog(
            onDismissRequest = { pausing = null },
            title = { Text(stringResource(R.string.pause_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    fun pauseUntil(d: LocalDate) {
                        vm.setPause(id, false, d.toString());
                        pausing = null
                    }
                    PauseOption(stringResource(R.string.pause_indefinite)) {
                        vm.setPause(id, true, "");
                        pausing = null
                    }
                    PauseOption(stringResource(R.string.pause_1week)) { pauseUntil(today.plusWeeks(1)) }
                    PauseOption(stringResource(R.string.pause_2weeks)) { pauseUntil(today.plusWeeks(2)) }
                    PauseOption(stringResource(R.string.pause_1month)) { pauseUntil(today.plusMonths(1)) }
                    DateField(
                        label = stringResource(R.string.pause_until_date),
                        value = today.plusDays(1).toString(),
                        minIso = today.plusDays(1).toString(),
                    ) { picked ->
                        vm.setPause(id, false, picked);
                        pausing = null
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pausing = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun PauseOption(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ScheduleCard(
    schedule: Schedule,
    onEdit: () -> Unit,
    onPauseRequested: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    val until = runCatching { LocalDate.parse(schedule.suspendedUntil) }.getOrNull()
    val paused = schedule.suspended || (until != null && LocalDate.now().isBefore(until))
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(schedule.medName, style = MaterialTheme.typography.titleMedium)
                val pausedLabel = when {
                    schedule.suspended -> stringResource(R.string.suspended_label)
                    paused && until != null -> stringResource(
                        R.string.paused_until,
                        until.format(
                            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
                        ),
                    )
                    else -> null
                }
                if (pausedLabel != null) {
                    Text(
                        pausedLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    recurrenceSummary(schedule),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.from_date, schedule.startDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (schedule.notes.isNotBlank()) {
                    Text(
                        schedule.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            IconButton(onClick = if (paused) onResume else onPauseRequested) {
                if (paused) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.resume_therapy))
                } else {
                    Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.suspend_therapy))
                }
            }
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cancel_prescription),
                    tint = MedColors.error
                )
            }
        }
    }
}

/** Compact human-readable recurrence, e.g. "Every 2 weeks · Mon, Thu · 08:00". */
@Composable
private fun recurrenceSummary(schedule: Schedule): String {
    val locale = Locale.getDefault()
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val times = schedule.times
    if (times.isEmpty()) return ""

    fun dow(d: Int) = DayOfWeek.of(d.coerceIn(1, 7)).getDisplayName(TextStyle.SHORT, locale)
    fun mon(m: Int) = Month.of(m.coerceIn(1, 12)).getDisplayName(TextStyle.SHORT, locale)
    fun t(h: Int, m: Int) =
        if (is24) {
            "%02d:%02d".format(h, m)
        } else {
            LocalTime.of(h.coerceIn(0, 23), m.coerceIn(0, 59)).format(DateTimeFormatter.ofPattern("h:mm a", locale))
        }

    val lastDayLabel = stringResource(R.string.last_day_of_month)
    val head = if (schedule.periodUnit == PeriodUnit.ONCE) {
        periodUnitLabel(schedule.periodUnit)
    } else {
        "${stringResource(
            R.string.repeat_every
        )} ${schedule.periodN} ${periodUnitLabel(schedule.periodUnit).lowercase(locale)}"
    }

    val detail = when (schedule.periodUnit) {
        PeriodUnit.ONCE -> times.sortedBy {
            "%04d%02d%02d%02d%02d".format(it.year, it.month, it.dayOfMonth, it.hour, it.minute)
        }
            .joinToString(", ") { "${it.dayOfMonth} ${mon(it.month)} ${it.year} ${t(it.hour, it.minute)}" }
        PeriodUnit.HOURS -> times.map { it.hour to it.minute }.distinct()
            .sortedWith(compareBy({ it.first }, { it.second })).joinToString(", ") { t(it.first, it.second) }
        PeriodUnit.DAYS -> times.sortedWith(
            compareBy({ it.hour }, { it.minute })
        ).joinToString(", ") { t(it.hour, it.minute) }
        PeriodUnit.WEEKS -> {
            val days = times.map { it.weekday }.distinct().sorted().joinToString(", ") { dow(it) }
            val ts = times.map { it.hour to it.minute }.distinct().sortedWith(compareBy({ it.first }, { it.second }))
                .joinToString(", ") { t(it.first, it.second) }
            "$days · $ts"
        }
        PeriodUnit.MONTHS -> times.sortedBy { it.dayOfMonth }.joinToString(", ") {
            val day = if (it.dayOfMonth >= 31) lastDayLabel else it.dayOfMonth.toString()
            "$day ${t(it.hour, it.minute)}"
        }
        PeriodUnit.YEARS -> times.sortedWith(compareBy({ it.month }, { it.dayOfMonth })).joinToString(", ") {
            "${it.dayOfMonth} ${mon(it.month)} ${t(it.hour, it.minute)}"
        }
    }
    return "$head · $detail"
}
