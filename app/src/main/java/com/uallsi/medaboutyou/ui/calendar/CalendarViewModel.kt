package com.uallsi.medaboutyou.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.domain.Insights
import com.uallsi.medaboutyou.domain.Now
import com.uallsi.medaboutyou.model.EndMode
import com.uallsi.medaboutyou.model.Occurrence
import com.uallsi.medaboutyou.model.Schedule
import com.uallsi.medaboutyou.model.ScheduleEngine
import com.uallsi.medaboutyou.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class DayState { NONE, FUTURE, TAKEN, SHORTAGE, MISSED }

/** One agenda row with its current stock (for the ⚠ shortage marker). */
data class AgendaItem(val occ: Occurrence, val stock: Int, val isPast: Boolean)

data class CalendarState(
    val year: Int = 0,
    val month: Int = 0,
    val selectedDay: Int = 0,
    val dayStates: Map<Int, DayState> = emptyMap(),
    val agenda: List<AgendaItem> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
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

    fun refresh() {
        viewModelScope.launch {
            val s = _state.value
            val snapshot = withContext(Dispatchers.IO) { container.schedules.snapshot() }
            val now = Now.local()

            // Shortage days from the shared refill forecast.
            val runout = withContext(Dispatchers.IO) {
                Insights.forecastRunouts(snapshot, dosesAvailable(), now)
            }
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
            val agendaOccs = snapshot.occurrencesOn(s.year, s.month, s.selectedDay)
            val agenda = agendaOccs.map { occ ->
                val stock = withContext(Dispatchers.IO) {
                    container.medicines.availableDoses(occ.medSource, occ.medExtId, occ.medName)
                }
                AgendaItem(
                    occ = occ,
                    stock = stock,
                    isPast = ScheduleEngine.isPastDateTime(occ.year, occ.month, occ.day, occ.hour, occ.minute),
                )
            }

            _state.value = s.copy(
                dayStates = states,
                agenda = agenda,
                schedules = snapshot.list(false),
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
                past && occ.status != "taken" -> return DayState.MISSED  // highest priority
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
            withContext(Dispatchers.IO) {
                container.schedules.logDose(occ.scheduleId, occ.keyIso, if (taken) "taken" else "untaken")
                // Taking consumes one unit of stock; un-taking returns one.
                val delta = if (taken) -1 else 1
                container.medicines.adjustDoses(occ.medSource, occ.medExtId, occ.medName, delta)
            }
            refresh()
        }
    }

    fun createSchedule(schedule: Schedule) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.schedules.create(schedule) }
            refresh()
        }
    }

    fun cancelSchedule(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.schedules.cancel(id) }
            refresh()
        }
    }

    fun prolong(id: Long, endMode: EndMode, endDate: String, doseCount: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.schedules.updateEnd(id, endMode, endDate, doseCount) }
            refresh()
        }
    }

    fun editSingle(occ: Occurrence, hour: Int, minute: Int, window: Int, cancelled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.schedules.editSingle(occ.scheduleId, occ.keyIso, hour, minute, window, cancelled)
            }
            refresh()
        }
    }

    fun splitFrom(occ: Occurrence, hour: Int, minute: Int, window: Int, cancelled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.schedules.splitFrom(
                    occ.scheduleId, occ.year, occ.month, occ.day, occ.index, hour, minute, window, cancelled,
                )
            }
            refresh()
        }
    }
}
