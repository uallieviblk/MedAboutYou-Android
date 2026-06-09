// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import android.text.format.DateFormat
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.uallsi.medaboutyou.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Read-only field that opens a Material 3 date picker. Avoids error-prone
 * free-text date entry (current Compose guidance). Value is "YYYY-MM-DD".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    minIso: String? = null,
    onChange: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val date = remember(value) { runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now()) }
    val minDate = remember(minIso) { minIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }

    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            androidx.compose.material3.IconButton(onClick = { open = true }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.pick_date))
            }
        },
        modifier = modifier.fillMaxWidth(),
    )

    if (open) {
        val initialMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val selectable = remember(minDate) {
            object : SelectableDates {
                private val minMillis = minDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
                override fun isSelectableDate(utcTimeMillis: Long) =
                    minMillis == null || utcTimeMillis >= minMillis
                override fun isSelectableYear(year: Int) =
                    minDate == null || year >= minDate.year
            }
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis, selectableDates = selectable)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        onChange(picked.toString())
                    }
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
}

/**
 * Read-only field that opens a Material 3 time picker. Value is the (hour,
 * minute) pair; the field shows "HH:MM".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String,
    hour: Int,
    minute: Int,
    modifier: Modifier = Modifier,
    onChange: (Int, Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val display = remember(hour, minute, is24Hour) {
        val t = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        if (is24Hour) "%02d:%02d".format(hour, minute)
        else t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    }

    OutlinedTextField(
        value = display,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            androidx.compose.material3.IconButton(onClick = { open = true }) {
                Icon(Icons.Default.Schedule, contentDescription = stringResource(R.string.pick_time))
            }
        },
        modifier = modifier.fillMaxWidth(),
    )

    if (open) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = is24Hour)
        Dialog(onDismissRequest = { open = false }) {
            Surface(
                shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                modifier = Modifier.wrapContentHeight(),
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 16.dp))
                    TimePicker(state = state)
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) }
                        TextButton(onClick = { onChange(state.hour, state.minute); open = false }) { Text(stringResource(R.string.action_ok)) }
                    }
                }
            }
        }
    }
}
