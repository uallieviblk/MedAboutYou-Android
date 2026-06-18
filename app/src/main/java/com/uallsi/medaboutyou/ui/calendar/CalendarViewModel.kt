// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.data.local.ActionCatalog
import com.uallsi.medaboutyou.domain.Insights
import com.uallsi.medaboutyou.domain.Now
import com.uallsi.medaboutyou.model.Occurrence
import com.uallsi.medaboutyou.model.Schedule
import com.uallsi.medaboutyou.model.ScheduleEngine
import com.uallsi.medaboutyou.reminders.DoseAlarms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class DayState { NONE, FUTURE, TAKEN, SHORTAGE, MISSED }

/**
 * One agenda row with its current stock (for the ⚠ shortage marker). [checkable]
 * is true once the dose's intake window has opened, so it may be marked taken.
 */
data class AgendaItem(val occ: Occurrence, val stock: Int, val isPast: Boolean, val checkable: Boolean)

data class CalendarState(
    val year: Int = 0,
    val month: Int = 0,
    val selectedDay: Int = 0,
    val dayStates: Map<Int, DayState> = emptyMap(),
    val agenda: List<AgendaItem> = emptyList(),
    // Schedules whose unit is ONCE — their doses have no "following" to split.
    val onceScheduleIds: Set<Long> = emptySet(),
)

/** Android port of `CalendarView` + the calendar half of `ScheduleRepository`. */
class CalendarViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(CalendarState())
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    init {
        val today = LocalDate.now()
        _state.value = CalendarState(today.year, today.monthValue, today.dayOfMonth)
        refresh()
    }

    fun shiftMonth(delta: Int) {
        val s = _state.value
        val base = LocalDate.of(s.year, s.month, 1).plusMonths(delta.toLong())
        val lastDay = base.lengthOfMonth()
        _state.value = s.copy(
            year = base.year,
            month = base.monthValue,
            selectedDay = s.selectedDay.coerceIn(1, lastDay),
        )
        refresh()
    }

    fun selectDay(day: Int) {
        _state.value = _state.value.copy(selectedDay = day)
        refresh()
    }

    private var refreshJob: Job? = null

    fun refresh() {
        // Cancel any in-flight refresh so a stale month/day computation can't
        // overwrite a newer selection when its IO finishes late.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val s = _state.value
            val (statesAgenda, onceIds) = withContext(Dispatchers.IO) {
                val snapshot = container.schedules.snapshot()
                val once = snapshot.list(true)
                    .filter { it.periodUnit == com.uallsi.medaboutyou.model.PeriodUnit.ONCE }
                    .map { it.id }
                    .toSet()
                val now = Now.local()
                // One stock map shared by the forecast and the agenda rows.
                val stockOf = dosesAvailable()

                // Shortage days from the shared refill forecast.
                val runout = Insights.forecastRunouts(snapshot, stockOf, now)
                val shortageDays = runout.values
                    .filter { it.year == s.year && it.month == s.month }
                    .map { it.day }
                    .toSet()

                val states = HashMap<Int, DayState>()
                val lastDay = LocalDate.of(s.year, s.month, 1).lengthOfMonth()
                for (d in 1..lastDay) {
                    val occs = snapshot.occurrencesOn(s.year, s.month, d)
                    states[d] = dayState(occs, d in shortageDays)
                }

                // Agenda for the selected day.
                val agenda = snapshot.occurrencesOn(s.year, s.month, s.selectedDay).map { occ ->
                    AgendaItem(
                        occ = occ,
                        stock = stockOf(occ.medSource, occ.medExtId, occ.medName),
                        isPast = ScheduleEngine.isPastDateTime(occ.year, occ.month, occ.day, occ.hour, occ.minute),
                        checkable = ScheduleEngine.isWithinTakeWindow(
                            occ.year, occ.month, occ.day, occ.hour, occ.minute, occ.windowMinutes,
                        ),
                    )
                }
                (states to agenda) to once
            }

            // Apply onto the *current* state: only the derived fields change, so
            // a month/day switched while we were loading is preserved.
            _state.value = _state.value.copy(
                dayStates = statesAgenda.first,
                agenda = statesAgenda.second,
                onceScheduleIds = onceIds,
            )
        }
    }

    private fun dayState(occs: List<Occurrence>, shortage: Boolean): DayState {
        if (occs.isEmpty()) return DayState.NONE
        var hasTaken = false
        var hasFuture = false
        for (occ in occs) {
            val past = ScheduleEngine.isPastDateTime(occ.year, occ.month, occ.day, occ.hour, occ.minute)
            when {
                past && occ.status != "taken" -> return DayState.MISSED // highest priority
                past && occ.status == "taken" -> hasTaken = true
                !past -> hasFuture = true
            }
        }
        return when {
            shortage -> DayState.SHORTAGE
            hasTaken -> DayState.TAKEN
            hasFuture -> DayState.FUTURE
            else -> DayState.NONE
        }
    }

    private suspend fun dosesAvailable(): com.uallsi.medaboutyou.domain.DosesAvailable {
        // Snapshot stock for every scheduled medicine up-front so the pure
        // forecast can run synchronously.
        val schedules = container.schedules.list(true)
        val map = HashMap<String, Int>()
        for (sch in schedules) {
            val key = Insights.medKey(sch.medSource, sch.medExtId, sch.medName)
            if (key !in map) {
                map[key] = container.medicines.availableDoses(sch.medSource, sch.medExtId, sch.medName)
            }
        }
        return { source, ext, name -> map[Insights.medKey(source, ext, name)] ?: 0 }
    }

    fun toggleDose(item: AgendaItem, taken: Boolean) {
        viewModelScope.launch {
            val occ = item.occ
            val delta = withContext(Dispatchers.IO) {
                // Taking consumes one unit of stock; un-taking returns one. The
                // repository reports the actual status transition, so re-logging
                // the same status (e.g. a double-tap) moves no stock.
                val d = container.schedules.logDose(occ.scheduleId, occ.keyIso, if (taken) "taken" else "untaken")
                if (d != 0) {
                    container.medicines.adjustDoses(occ.medSource, occ.medExtId, occ.medName, d)
                }
                d
            }
            if (delta != 0) {
                val action = if (taken) ActionCatalog.DOSE_TAKEN else ActionCatalog.DOSE_UNTAKEN
                val textRes = if (taken) R.string.action_txt_dose_taken else R.string.action_txt_dose_untaken
                container.actionLog.log(action, container.appContext.getString(textRes, occ.medName))
            }
            refresh()
            DoseAlarms.kickNow(container.appContext)
        }
    }

    fun createSchedule(schedule: Schedule) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.schedules.create(schedule) }
            container.actionLog.log(
                ActionCatalog.SCHEDULE_CREATED,
                container.appContext.getString(R.string.action_txt_schedule_created, schedule.medName),
            )
            refresh()
            DoseAlarms.kickNow(container.appContext)
        }
    }

    fun editSingle(occ: Occurrence, hour: Int, minute: Int, window: Int, cancelled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.schedules.editSingle(occ.scheduleId, occ.keyIso, hour, minute, window, cancelled)
            }
            container.actionLog.log(
                ActionCatalog.DOSE_EDITED,
                container.appContext.getString(R.string.action_txt_dose_edited, occ.medName),
            )
            refresh()
            DoseAlarms.kickNow(container.appContext)
        }
    }

    fun splitFrom(occ: Occurrence, hour: Int, minute: Int, window: Int, cancelled: Boolean) {
        viewModelScope.launch {
            // The entry being retimed is identified by its *original* time — the
            // keyIso tail ("…THH:MM") — not occ.hour/minute, which a prior
            // single-dose override may have changed.
            val fromHour = occ.keyIso.substringAfter('T').substringBefore(':').toIntOrNull() ?: occ.hour
            val fromMinute = occ.keyIso.substringAfterLast(':').toIntOrNull() ?: occ.minute
            withContext(Dispatchers.IO) {
                container.schedules.splitFrom(
                    occ.scheduleId, occ.year, occ.month, occ.day,
                    fromHour, fromMinute, hour, minute, window, cancelled,
                )
            }
            container.actionLog.log(
                ActionCatalog.SCHEDULE_EDITED,
                container.appContext.getString(R.string.action_txt_schedule_edited, occ.medName),
            )
            refresh()
            DoseAlarms.kickNow(container.appContext)
        }
    }
}
