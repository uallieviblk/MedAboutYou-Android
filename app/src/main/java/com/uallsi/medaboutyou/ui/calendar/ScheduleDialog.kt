package com.uallsi.medaboutyou.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uallsi.medaboutyou.model.EndMode
import com.uallsi.medaboutyou.model.PeriodUnit
import com.uallsi.medaboutyou.model.Schedule
import com.uallsi.medaboutyou.model.Source
import com.uallsi.medaboutyou.ui.common.DateField
import com.uallsi.medaboutyou.ui.common.Stepper
import com.uallsi.medaboutyou.ui.common.TimeField
import java.time.LocalDate

/** New-schedule form (Android port of the calendar's "New schedule" dialog). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewScheduleDialog(
    prefillName: String,
    prefillSource: Source,
    prefillExtId: String,
    onCreate: (Schedule) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now().toString()
    var name by remember { mutableStateOf(prefillName) }
    var startDate by remember { mutableStateOf(today) }
    var unit by remember { mutableStateOf(PeriodUnit.DAYS) }
    var intervalN by remember { mutableIntStateOf(1) }
    var hour by remember { mutableIntStateOf(8) }
    var minute by remember { mutableIntStateOf(0) }
    var window by remember { mutableIntStateOf(30) }
    var endMode by remember { mutableStateOf(EndMode.NEVER) }
    var endDate by remember { mutableStateOf(today) }
    var doseCount by remember { mutableIntStateOf(10) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New schedule") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Medicine name") }, singleLine = true,
                    isError = name.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField("Start date", startDate) { startDate = it }

                Text("Repeat every", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PeriodUnit.entries.forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { unit = u },
                            label = { Text(u.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                Stepper("Interval (every N ${unit.name.lowercase()})", intervalN, 1, 99) { intervalN = it }
                TimeField("Time of dose", hour, minute) { h, m -> hour = h; minute = m }
                Stepper("Allowed window (± minutes)", window, 0, 360, step = 5) { window = it }

                Text("Ends", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EndMode.entries.forEach { m ->
                        val label = when (m) {
                            EndMode.NEVER -> "Ongoing"
                            EndMode.DATE -> "On date"
                            EndMode.COUNT -> "After N"
                        }
                        FilterChip(selected = endMode == m, onClick = { endMode = m }, label = { Text(label) })
                    }
                }
                when (endMode) {
                    EndMode.DATE -> DateField("End date", endDate) { endDate = it }
                    EndMode.COUNT -> Stepper("Number of doses", doseCount, 1, 999) { doseCount = it }
                    EndMode.NEVER -> {}
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onCreate(
                        Schedule(
                            medSource = prefillSource,
                            medExtId = prefillExtId,
                            medName = name.trim(),
                            startDate = startDate,
                            endMode = endMode,
                            endDate = endDate,
                            doseCount = doseCount,
                            periodUnit = unit,
                            periodN = intervalN,
                            hour = hour,
                            minute = minute,
                            windowMinutes = window,
                            notes = notes.trim(),
                            active = true,
                        )
                    )
                },
            ) { Text("Add to calendar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
