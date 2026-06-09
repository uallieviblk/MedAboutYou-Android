package com.uallsi.medaboutyou.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.text.format.DateFormat
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.model.DoseTime
import com.uallsi.medaboutyou.model.EndMode
import com.uallsi.medaboutyou.model.PeriodUnit
import com.uallsi.medaboutyou.model.Schedule
import com.uallsi.medaboutyou.model.Source
import com.uallsi.medaboutyou.ui.common.DateField
import com.uallsi.medaboutyou.ui.common.Stepper
import com.uallsi.medaboutyou.ui.common.TimeField
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Localised label for a repeat unit. */
@Composable
fun periodUnitLabel(unit: PeriodUnit): String = stringResource(
    when (unit) {
        PeriodUnit.ONCE -> R.string.unit_once
        PeriodUnit.HOURS -> R.string.unit_hours
        PeriodUnit.DAYS -> R.string.unit_days
        PeriodUnit.WEEKS -> R.string.unit_weeks
        PeriodUnit.MONTHS -> R.string.unit_months
        PeriodUnit.YEARS -> R.string.unit_years
    }
)

@Composable
private fun endModeLabel(mode: EndMode): String = stringResource(
    when (mode) {
        EndMode.NEVER -> R.string.end_ongoing
        EndMode.DATE -> R.string.end_on_date
        EndMode.COUNT -> R.string.end_after_n
    }
)

/** A sensible default dose-time entry for a freshly-selected [unit]. */
private fun defaultTime(unit: PeriodUnit, today: LocalDate): DoseTime = when (unit) {
    PeriodUnit.ONCE -> DoseTime(year = today.year, month = today.monthValue, dayOfMonth = today.dayOfMonth, hour = 8)
    PeriodUnit.HOURS -> DoseTime(minute = 0)
    PeriodUnit.DAYS -> DoseTime(hour = 8)
    PeriodUnit.WEEKS -> DoseTime(weekday = today.dayOfWeek.value, hour = 8)
    PeriodUnit.MONTHS -> DoseTime(dayOfMonth = today.dayOfMonth, hour = 8)
    PeriodUnit.YEARS -> DoseTime(month = today.monthValue, dayOfMonth = today.dayOfMonth.coerceAtMost(Month.of(today.monthValue).length(false)), hour = 8)
}

/** "HH:mm" or "h:mm a" per the device's hour-format setting. */
private fun fmtTime(hour: Int, minute: Int, is24: Boolean, locale: Locale): String =
    if (is24) "%02d:%02d".format(hour, minute)
    else LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)).format(DateTimeFormatter.ofPattern("h:mm a", locale))

/**
 * Schedule create/edit form. With [existing] non-null the dialog opens
 * pre-filled and saves changes to that schedule; otherwise it creates a new one.
 * (Android port of the calendar's "New schedule" dialog, extended for editing.)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditorDialog(
    prefillName: String,
    prefillSource: Source,
    prefillExtId: String,
    onCreate: (Schedule) -> Unit,
    onDismiss: () -> Unit,
    existing: Schedule? = null,
) {
    val today = LocalDate.now()
    val todayIso = today.toString()
    val locale = Locale.getDefault()
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val editing = existing != null
    val initUnit = existing?.periodUnit ?: PeriodUnit.DAYS

    var name by remember { mutableStateOf(existing?.medName ?: prefillName) }
    var startDate by remember { mutableStateOf(existing?.startDate?.ifBlank { todayIso } ?: todayIso) }
    var unit by remember { mutableStateOf(initUnit) }
    var intervalN by remember { mutableIntStateOf(existing?.periodN?.coerceAtLeast(1) ?: 1) }
    var times by remember {
        mutableStateOf(
            if (existing != null && initUnit != PeriodUnit.WEEKS && existing.times.isNotEmpty()) existing.times
            else listOf(defaultTime(initUnit, today)),
        )
    }
    // WEEKS uses a day-toggle row + shared dose times (Google/Apple pattern).
    var weekDays by remember {
        mutableStateOf(
            if (initUnit == PeriodUnit.WEEKS && existing != null)
                existing.times.map { it.weekday }.toSet().ifEmpty { setOf(today.dayOfWeek.value) }
            else setOf(today.dayOfWeek.value),
        )
    }
    var weekTimes by remember {
        mutableStateOf(
            if (initUnit == PeriodUnit.WEEKS && existing != null)
                existing.times.map { it.hour to it.minute }.distinct().ifEmpty { listOf(8 to 0) }
            else listOf(8 to 0),
        )
    }
    var window by remember { mutableIntStateOf(existing?.windowMinutes ?: 30) }
    var caregiverAlert by remember { mutableIntStateOf(existing?.caregiverAlertMin ?: 0) }
    var alertRefresh by remember { mutableIntStateOf(existing?.alertRefreshMin ?: 0) }
    var endMode by remember { mutableStateOf(existing?.endMode ?: EndMode.NEVER) }
    var endDate by remember { mutableStateOf(existing?.endDate?.ifBlank { todayIso } ?: todayIso) }
    var doseCount by remember { mutableIntStateOf(existing?.doseCount?.takeIf { it > 0 } ?: 10) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    fun setUnit(u: PeriodUnit) {
        if (u == unit) return
        unit = u
        if (u != PeriodUnit.WEEKS) times = listOf(defaultTime(u, today))
    }

    fun updateTime(i: Int, t: DoseTime) {
        times = times.toMutableList().also { it[i] = t }
    }

    // Build the final dose-time list from the active editor's state.
    fun buildTimes(): List<DoseTime> = when (unit) {
        PeriodUnit.WEEKS -> weekDays.sorted().flatMap { wd ->
            weekTimes.map { (h, m) -> DoseTime(weekday = wd, hour = h, minute = m) }
        }
        else -> times
    }

    val timesValid = if (unit == PeriodUnit.WEEKS) weekDays.isNotEmpty() && weekTimes.isNotEmpty() else times.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (editing) R.string.title_edit else R.string.new_schedule)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.medicine_name)) }, singleLine = true,
                    isError = name.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Edits take effect from today, so the start date isn't editable.
                if (unit != PeriodUnit.ONCE && !editing) {
                    DateField(stringResource(R.string.start_date), startDate) { startDate = it }
                }
                if (editing && unit != PeriodUnit.ONCE) {
                    Text(
                        stringResource(R.string.edit_from_today),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(stringResource(R.string.repeat_every), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PeriodUnit.entries.forEach { u ->
                        FilterChip(selected = unit == u, onClick = { setUnit(u) }, label = { Text(periodUnitLabel(u)) })
                    }
                }
                if (unit != PeriodUnit.ONCE) {
                    Stepper(stringResource(R.string.interval_label, periodUnitLabel(unit).lowercase()), intervalN, 1, 99) { intervalN = it }
                }

                // ---- Dose-time entries (shape depends on the unit) ----
                Text(stringResource(R.string.dose_times), style = MaterialTheme.typography.labelMedium)
                if (unit == PeriodUnit.WEEKS) {
                    WeekdayToggleRow(weekDays) { weekDays = it }
                    weekTimes.forEachIndexed { i, (h, m) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TimeField(
                                stringResource(R.string.time_of_dose), h, m,
                                modifier = Modifier.weight(1f),
                            ) { nh, nm -> weekTimes = weekTimes.toMutableList().also { it[i] = nh to nm } }
                            if (weekTimes.size > 1) {
                                IconButton(onClick = { weekTimes = weekTimes.filterIndexed { j, _ -> j != i } }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_remove))
                                }
                            }
                        }
                    }
                    TextButton(onClick = { weekTimes = weekTimes + (8 to 0) }) { Text(stringResource(R.string.add_time)) }
                } else {
                    times.forEachIndexed { i, t ->
                        DoseTimeRow(
                            unit = unit,
                            time = t,
                            canRemove = times.size > 1,
                            onChange = { updateTime(i, it) },
                            onRemove = { times = times.filterIndexed { j, _ -> j != i } },
                        )
                    }
                    TextButton(onClick = { times = times + defaultTime(unit, today) }) {
                        Text(stringResource(R.string.add_time))
                    }
                }

                // ---- Live recurrence summary ----
                val summary = scheduleSummary(unit, intervalN, buildTimes(), weekDays, is24, locale)
                if (summary.isNotBlank()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Stepper(stringResource(R.string.window_label), window, 0, 360, step = 5) {
                    window = it
                    val cap = (it - 5).coerceAtLeast(0)
                    if (caregiverAlert >= it) caregiverAlert = cap
                    if (alertRefresh >= it) alertRefresh = cap
                }
                // Repeat the local reminder, and escalate to caregivers — both
                // timeouts stay below the intake window.
                Stepper(stringResource(R.string.reminder_repeat_every), alertRefresh, 0, (window - 5).coerceAtLeast(0), step = 5) {
                    alertRefresh = it
                }
                Stepper(stringResource(R.string.caregiver_alert_after), caregiverAlert, 0, (window - 5).coerceAtLeast(0), step = 5) {
                    caregiverAlert = it
                }

                if (unit != PeriodUnit.ONCE) {
                    Text(stringResource(R.string.ends), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EndMode.entries.forEach { mo ->
                            FilterChip(selected = endMode == mo, onClick = { endMode = mo }, label = { Text(endModeLabel(mo)) })
                        }
                    }
                    when (endMode) {
                        // End date can't precede the start date.
                        EndMode.DATE -> DateField(stringResource(R.string.end_date), endDate, minIso = startDate) { endDate = it }
                        EndMode.COUNT -> Stepper(stringResource(R.string.number_of_doses), doseCount, 1, 999) { doseCount = it }
                        EndMode.NEVER -> {}
                    }
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes_optional)) }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && timesValid,
                onClick = {
                    val once = unit == PeriodUnit.ONCE
                    val built = buildTimes()
                    onCreate(
                        Schedule(
                            id = existing?.id ?: 0,
                            medSource = existing?.medSource ?: prefillSource,
                            medExtId = existing?.medExtId ?: prefillExtId,
                            medName = name.trim(),
                            startDate = if (once) (built.minByOrNull { "%04d%02d%02d".format(it.year, it.month, it.dayOfMonth) }
                                ?.let { "%04d-%02d-%02d".format(it.year, it.month, it.dayOfMonth) } ?: startDate) else startDate,
                            endMode = if (once) EndMode.NEVER else endMode,
                            endDate = endDate,
                            doseCount = doseCount,
                            periodUnit = unit,
                            periodN = if (once) 1 else intervalN,
                            times = built,
                            windowMinutes = window,
                            caregiverAlertMin = caregiverAlert,
                            alertRefreshMin = alertRefresh,
                            notes = notes.trim(),
                            active = existing?.active ?: true,
                        )
                    )
                },
            ) { Text(stringResource(if (editing) R.string.action_save else R.string.add_to_calendar)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Human-readable summary of the current recurrence, e.g. "Repeat every 2 weeks · Mon, Thu · 08:00". */
@Composable
private fun scheduleSummary(
    unit: PeriodUnit,
    intervalN: Int,
    times: List<DoseTime>,
    weekDays: Set<Int>,
    is24: Boolean,
    locale: Locale,
): String {
    if (times.isEmpty()) return ""
    fun dow(d: Int) = DayOfWeek.of(d.coerceIn(1, 7)).getDisplayName(TextStyle.SHORT, locale)
    fun mon(m: Int) = Month.of(m.coerceIn(1, 12)).getDisplayName(TextStyle.SHORT, locale)
    fun t(h: Int, m: Int) = fmtTime(h, m, is24, locale)

    val head = if (unit == PeriodUnit.ONCE) periodUnitLabel(unit)
    else "${stringResource(R.string.repeat_every)} $intervalN ${periodUnitLabel(unit).lowercase(locale)}"
    val lastDayLabel = stringResource(R.string.last_day_of_month)

    val detail = when (unit) {
        PeriodUnit.ONCE -> times.sortedBy { "%04d%02d%02d%02d%02d".format(it.year, it.month, it.dayOfMonth, it.hour, it.minute) }
            .joinToString(", ") { "${it.dayOfMonth} ${mon(it.month)} ${it.year} ${t(it.hour, it.minute)}" }
        PeriodUnit.HOURS -> times.map { it.minute }.distinct().sorted().joinToString(", ") { ":%02d".format(it) }
        PeriodUnit.DAYS -> times.sortedWith(compareBy({ it.hour }, { it.minute })).joinToString(", ") { t(it.hour, it.minute) }
        PeriodUnit.WEEKS -> {
            val days = weekDays.sorted().joinToString(", ") { dow(it) }
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

/** One editable dose-time row; the controls shown depend on [unit]. (Not used for WEEKS.) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoseTimeRow(
    unit: PeriodUnit,
    time: DoseTime,
    canRemove: Boolean,
    onChange: (DoseTime) -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (unit) {
                    PeriodUnit.HOURS ->
                        Stepper(stringResource(R.string.label_minute), time.minute, 0, 59) { onChange(time.copy(minute = it)) }

                    PeriodUnit.DAYS ->
                        TimeField(stringResource(R.string.time_of_dose), time.hour, time.minute) { h, m -> onChange(time.copy(hour = h, minute = m)) }

                    PeriodUnit.WEEKS -> {} // handled by the day-toggle editor

                    PeriodUnit.MONTHS -> {
                        val lastDay = time.dayOfMonth >= 31
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = lastDay, onCheckedChange = { onChange(time.copy(dayOfMonth = if (it) 31 else 28)) })
                            Text(stringResource(R.string.last_day_of_month), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (!lastDay) {
                            Stepper(stringResource(R.string.label_day_of_month), time.dayOfMonth, 1, 30) { onChange(time.copy(dayOfMonth = it)) }
                            if (time.dayOfMonth >= 29) {
                                Text(stringResource(R.string.clamp_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TimeField(stringResource(R.string.time_of_dose), time.hour, time.minute) { h, m -> onChange(time.copy(hour = h, minute = m)) }
                    }

                    PeriodUnit.YEARS -> {
                        val maxDay = Month.of(time.month.coerceIn(1, 12)).length(false) // Feb capped at 28 -> no Feb 29
                        MonthDropdown(time.month) { newMonth ->
                            val nm = Month.of(newMonth).length(false)
                            onChange(time.copy(month = newMonth, dayOfMonth = time.dayOfMonth.coerceAtMost(nm)))
                        }
                        Stepper(stringResource(R.string.label_day_of_month), time.dayOfMonth.coerceAtMost(maxDay), 1, maxDay) { onChange(time.copy(dayOfMonth = it)) }
                        TimeField(stringResource(R.string.time_of_dose), time.hour, time.minute) { h, m -> onChange(time.copy(hour = h, minute = m)) }
                    }

                    PeriodUnit.ONCE -> {
                        val iso = "%04d-%02d-%02d".format(time.year, time.month, time.dayOfMonth)
                        DateField(stringResource(R.string.date_of_dose), iso) { picked ->
                            runCatching { LocalDate.parse(picked) }.getOrNull()?.let {
                                onChange(time.copy(year = it.year, month = it.monthValue, dayOfMonth = it.dayOfMonth))
                            }
                        }
                        TimeField(stringResource(R.string.time_of_dose), time.hour, time.minute) { h, m -> onChange(time.copy(hour = h, minute = m)) }
                    }
                }
            }
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_remove))
                }
            }
        }
    }
}

/** Multi-select Mon–Sun toggle row (Google Calendar / Apple Health weekly pattern). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayToggleRow(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    val locale = Locale.getDefault()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { d ->
            val label = DayOfWeek.of(d).getDisplayName(TextStyle.NARROW, locale)
            FilterChip(
                selected = d in selected,
                onClick = {
                    // Keep at least one day selected.
                    val next = if (d in selected) selected - d else selected + d
                    if (next.isNotEmpty()) onChange(next)
                },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthDropdown(month: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val locale = Locale.getDefault()
    fun label(m: Int) = Month.of(m.coerceIn(1, 12)).getDisplayName(TextStyle.FULL, locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label(month), onValueChange = {}, readOnly = true,
            label = { Text(stringResource(R.string.label_month)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..12).forEach { m ->
                DropdownMenuItem(text = { Text(label(m)) }, onClick = { onChange(m); expanded = false })
            }
        }
    }
}
