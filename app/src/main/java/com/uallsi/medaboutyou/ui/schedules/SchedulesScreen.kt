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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import com.uallsi.medaboutyou.ui.theme.MedColors
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Dedicated page listing the active prescriptions (moved out of the calendar). */
@Composable
fun SchedulesScreen(modifier: Modifier = Modifier) {
    val vm: SchedulesViewModel = viewModel(factory = AppViewModelFactory)
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    var pendingCancel by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var editing by remember { mutableStateOf<Schedule?>(null) }

    if (schedules.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.schedules_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(schedules, key = { it.id }) { sch ->
                ScheduleCard(
                    sch,
                    onEdit = { editing = sch },
                    onCancel = { pendingCancel = sch.id to sch.medName },
                )
            }
        }
    }

    editing?.let { sch ->
        ScheduleEditorDialog(
            prefillName = sch.medName,
            prefillSource = sch.medSource,
            prefillExtId = sch.medExtId,
            existing = sch,
            onCreate = { vm.applyEdit(it); editing = null },
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
                    onClick = { vm.cancel(id); pendingCancel = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MedColors.error),
                ) { Text(stringResource(R.string.cancel_prescription)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancel = null }) { Text(stringResource(R.string.keep)) }
            },
        )
    }
}

@Composable
private fun ScheduleCard(schedule: Schedule, onEdit: () -> Unit, onCancel: () -> Unit) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(schedule.medName, style = MaterialTheme.typography.titleMedium)
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
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cancel_prescription), tint = MedColors.error)
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
        if (is24) "%02d:%02d".format(h, m)
        else LocalTime.of(h.coerceIn(0, 23), m.coerceIn(0, 59)).format(DateTimeFormatter.ofPattern("h:mm a", locale))

    val lastDayLabel = stringResource(R.string.last_day_of_month)
    val head = if (schedule.periodUnit == PeriodUnit.ONCE) periodUnitLabel(schedule.periodUnit)
    else "${stringResource(R.string.repeat_every)} ${schedule.periodN} ${periodUnitLabel(schedule.periodUnit).lowercase(locale)}"

    val detail = when (schedule.periodUnit) {
        PeriodUnit.ONCE -> times.sortedBy { "%04d%02d%02d%02d%02d".format(it.year, it.month, it.dayOfMonth, it.hour, it.minute) }
            .joinToString(", ") { "${it.dayOfMonth} ${mon(it.month)} ${it.year} ${t(it.hour, it.minute)}" }
        PeriodUnit.HOURS -> times.map { it.minute }.distinct().sorted().joinToString(", ") { ":%02d".format(it) }
        PeriodUnit.DAYS -> times.sortedWith(compareBy({ it.hour }, { it.minute })).joinToString(", ") { t(it.hour, it.minute) }
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
