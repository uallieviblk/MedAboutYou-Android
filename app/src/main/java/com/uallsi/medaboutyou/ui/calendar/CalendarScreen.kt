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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uallsi.medaboutyou.model.Source
import com.uallsi.medaboutyou.ui.AppViewModelFactory
import com.uallsi.medaboutyou.ui.theme.MedColors
import java.time.LocalDate

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    prefillName: String? = null,
    onConsumePrefill: () -> Unit = {},
) {
    val vm: CalendarViewModel = viewModel(factory = AppViewModelFactory)
    val state by vm.state.collectAsStateWithLifecycle()
    var showNew by remember { mutableStateOf(false) }
    var prefill by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(prefillName) {
        if (prefillName != null) { prefill = prefillName; showNew = true; onConsumePrefill() }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("New schedule") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { prefill = null; showNew = true },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { MonthHeader(state.year, state.month, onPrev = { vm.shiftMonth(-1) }, onNext = { vm.shiftMonth(1) }) }
            item { MonthGrid(state, onSelect = vm::selectDay) }
            item {
                Text(
                    "Doses on %04d-%02d-%02d".format(state.year, state.month, state.selectedDay),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.agenda.isEmpty()) {
                item {
                    Text(
                        "No doses scheduled. Tap + to add one, or pick another day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.agenda, key = { it.occ.scheduleId.toString() + it.occ.keyIso }) { item ->
                    AgendaRow(item, onToggle = { vm.toggleDose(item, it) })
                }
            }
            if (state.schedules.isNotEmpty()) {
                item {
                    Text(
                        "Active schedules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                items(state.schedules, key = { it.id }) { sch ->
                    ScheduleRow(sch.medName, sch.startDate, onCancel = { vm.cancelSchedule(sch.id) })
                }
            }
        }
    }

    if (showNew) {
        NewScheduleDialog(
            prefillName = prefill ?: "",
            prefillSource = Source.EMA,
            prefillExtId = "",
            onCreate = { vm.createSchedule(it); showNew = false },
            onDismiss = { showNew = false },
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
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "Previous month") }
        val name = java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        Text("$name $year", style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "Next month") }
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
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                Text(
                    it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
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
private fun AgendaRow(item: AgendaItem, onToggle: (Boolean) -> Unit) {
    val occ = item.occ
    val statusText = when {
        occ.status == "taken" -> "taken"
        item.isPast -> "missed"
        else -> "upcoming"
    }
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
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = occ.status == "taken",
                enabled = item.isPast,
                onCheckedChange = { onToggle(it) },
            )
            Column(Modifier.weight(1f)) {
                Text("${occ.timeLabel()} — ${occ.medName}", style = MaterialTheme.typography.titleSmall)
                val warn = if (item.stock <= 0) "  ⚠ no stock" else ""
                Text(
                    "±${occ.windowMinutes} min · $statusText · ${item.stock} in stock$warn",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScheduleRow(name: String, startDate: String, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text("from $startDate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Delete, contentDescription = "Cancel prescription", tint = MedColors.error)
        }
    }
}
