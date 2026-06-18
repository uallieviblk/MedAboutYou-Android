// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.model.Occurrence
import com.uallsi.medaboutyou.model.ScheduleEngine
import com.uallsi.medaboutyou.model.Source
import com.uallsi.medaboutyou.ui.AppViewModelFactory
import com.uallsi.medaboutyou.ui.common.DoseToggleConfirmDialog
import com.uallsi.medaboutyou.ui.theme.MedColors
import java.time.LocalDate

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    prefillName: String? = null,
    prefillSource: Source = Source.EMA,
    prefillExtId: String = "",
    onConsumePrefill: () -> Unit = {},
) {
    val vm: CalendarViewModel = viewModel(factory = AppViewModelFactory)
    val state by vm.state.collectAsStateWithLifecycle()
    var showNew by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var editingOcc by remember { mutableStateOf<Occurrence?>(null) }
    var prefill by remember { mutableStateOf<String?>(null) }
    // Identity to attach to a prefilled schedule, so its stock is the same row
    // the medicine record edits (key "source:extId").
    var prefillMedSource by remember { mutableStateOf(Source.EMA) }
    var prefillMedExt by remember { mutableStateOf("") }
    var pendingToggle by remember { mutableStateOf<Pair<AgendaItem, Boolean>?>(null) }

    androidx.compose.runtime.LaunchedEffect(prefillName) {
        if (prefillName != null) {
            prefill = prefillName
            prefillMedSource = prefillSource
            prefillMedExt = prefillExtId
            showNew = true
            onConsumePrefill()
        }
    }

    // Refresh whenever the tab is (re-)shown: a dose taken on Today, from the
    // widget or from a notification must be reflected here without requiring a
    // manual day tap (the retained VM otherwise keeps the stale snapshot).
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refresh() }

    // Schedules are created from a medicine record (Search → medicine → "Add to
    // my medication schedule"), so the schedule always carries the medicine's
    // identity for stock tracking. There is no free-text "New schedule" button.
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { MonthHeader(state.year, state.month, onPrev = { vm.shiftMonth(-1) }, onNext = { vm.shiftMonth(1) }) }
        item { MonthGrid(state, onSelect = vm::selectDay) }
        item {
            Text(
                stringResource(
                    R.string.doses_on,
                    "%04d-%02d-%02d".format(state.year, state.month, state.selectedDay)
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (state.agenda.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.calendar_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.agenda, key = { it.occ.scheduleId.toString() + it.occ.keyIso }) { item ->
                AgendaRow(
                    item,
                    onToggle = { pendingToggle = item to it },
                    onEdit = { editingOcc = item.occ },
                )
            }
        }
    }

    if (showNew) {
        ScheduleEditorDialog(
            prefillName = prefill ?: "",
            prefillSource = prefillMedSource,
            prefillExtId = prefillMedExt,
            onCreate = {
                vm.createSchedule(it);
                showNew = false
            },
            onDismiss = { showNew = false },
        )
    }

    editingOcc?.let { occ ->
        EditOccurrenceDialog(
            occurrence = occ,
            onSave = { hour, minute, window, cancelled, applyToFollowing ->
                if (applyToFollowing) {
                    vm.splitFrom(occ, hour, minute, window, cancelled)
                } else {
                    vm.editSingle(occ, hour, minute, window, cancelled)
                }
                editingOcc = null
            },
            onDismiss = { editingOcc = null },
            allowFollowing = occ.scheduleId !in state.onceScheduleIds,
        )
    }

    pendingToggle?.let { (item, taken) ->
        DoseToggleConfirmDialog(
            medName = item.occ.medName,
            time = item.occ.timeLabel(),
            taken = taken,
            outsideWindow = taken && !ScheduleEngine.isWithinScheduledWindow(
                item.occ.year, item.occ.month, item.occ.day, item.occ.hour, item.occ.minute, item.occ.windowMinutes,
            ),
            onConfirm = {
                vm.toggleDose(item, taken);
                pendingToggle = null
            },
            onDismiss = { pendingToggle = null },
        )
    }
}

@Composable
private fun MonthHeader(year: Int, month: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.previous_month)) }
        val name = java.time.Month.of(
            month
        ).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        Text("$name $year", style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, stringResource(R.string.next_month)) }
    }
}

@Composable
private fun MonthGrid(state: CalendarState, onSelect: (Int) -> Unit) {
    val first = LocalDate.of(state.year, state.month, 1)
    val lastDay = first.lengthOfMonth()
    // Monday-first offset (ISO).
    val leading = (first.dayOfWeek.value + 6) % 7
    val today = LocalDate.now()

    Column {
        Row(Modifier.fillMaxWidth()) {
            (0..6).map {
                java.time.DayOfWeek.MONDAY.plus(
                    it.toLong()
                ).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            }.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        val cells = leading + lastDay
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val cellIndex = r * 7 + c
                    val day = cellIndex - leading + 1
                    if (day in 1..lastDay) {
                        val isToday = today.year == state.year && today.monthValue == state.month && today.dayOfMonth == day
                        DayCell(
                            day = day,
                            stateColor = stateColor(state.dayStates[day]),
                            selected = day == state.selectedDay,
                            isToday = isToday,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(day) },
                        )
                    } else {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    stateColor: Color?,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .background(stateColor ?: Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isToday) {
                Box(
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                        .padding(8.dp),
                ) { Text("$day", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
            } else {
                Text("$day", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun stateColor(state: DayState?): Color? = when (state) {
    DayState.MISSED -> MedColors.errorContainer
    DayState.SHORTAGE -> MedColors.shortageContainer
    DayState.TAKEN -> MedColors.successContainer
    DayState.FUTURE -> MedColors.futureContainer
    else -> null
}

@Composable
private fun AgendaRow(item: AgendaItem, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    val occ = item.occ
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val statusText = stringResource(
        when {
            occ.status == "taken" -> R.string.agenda_status_taken
            item.isPast -> R.string.agenda_status_missed
            else -> R.string.agenda_status_upcoming
        }
    )
    val rowColor = when {
        occ.status == "taken" -> MedColors.successContainer
        item.isPast -> MedColors.errorContainer
        item.stock <= 0 && !item.isPast -> MedColors.shortageContainer
        else -> MedColors.futureContainer
    }
    Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = rowColor.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = occ.status == "taken",
                enabled = item.checkable,
                onCheckedChange = { taken ->
                    // Same dose-taken haptic as the Today screen.
                    if (taken) {
                        haptics.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                        )
                    }
                    onToggle(taken)
                },
            )
            Column(Modifier.weight(1f)) {
                Text("${occ.timeLabel()} — ${occ.medName}", style = MaterialTheme.typography.titleSmall)
                val inStock = pluralStringResource(R.plurals.in_stock, item.stock, item.stock)
                val warn = if (item.stock <= 0) "  " + stringResource(R.string.agenda_warn_no_stock) else ""
                Text(
                    stringResource(R.string.agenda_subtitle, occ.windowMinutes, statusText, inStock) + warn,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_dose))
            }
        }
    }
}
