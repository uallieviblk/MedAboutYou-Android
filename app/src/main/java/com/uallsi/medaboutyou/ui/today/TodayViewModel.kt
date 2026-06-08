package com.uallsi.medaboutyou.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.domain.Insights
import com.uallsi.medaboutyou.domain.Now
import com.uallsi.medaboutyou.domain.RefillForecast
import com.uallsi.medaboutyou.model.Occurrence
import com.uallsi.medaboutyou.model.ScheduleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A dose on today's timeline. */
data class TodayDose(val occ: Occurrence, val stock: Int, val isPast: Boolean)

data class TodayState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val doses: List<TodayDose> = emptyList(),
    val takenToday: Int = 0,
    val dueToday: Int = 0,
    val streak: Int = 0,
    val nextRefill: RefillForecast? = null,
)

/** Home screen state — the adherence ring + today's dose timeline + refill banner. */
class TodayViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(TodayState())
    val state: StateFlow<TodayState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true)
            val now = Now.local()
            val snapshot = withContext(Dispatchers.IO) { container.schedules.snapshot() }

            val todays = snapshot.occurrencesOn(now.year, now.month, now.day)
            val doses = todays.map { occ ->
                val stock = withContext(Dispatchers.IO) {
                    container.medicines.availableDoses(occ.medSource, occ.medExtId, occ.medName)
                }
                TodayDose(
                    occ = occ,
                    stock = stock,
                    isPast = ScheduleEngine.isPastDateTime(occ.year, occ.month, occ.day, occ.hour, occ.minute),
                )
            }
            val due = todays.filter { Insights.doseIsDue(it, now) }
            val taken = due.count { it.status == "taken" }
            val streak = Insights.currentStreak(snapshot, now)

            val refills = withContext(Dispatchers.IO) {
                Insights.refillForecast(snapshot, dosesAvailable(), now)
            }

            _state.value = TodayState(
                loading = false,
                refreshing = false,
                doses = doses,
                takenToday = taken,
                dueToday = due.size,
                streak = streak,
                nextRefill = refills.firstOrNull(),
            )
        }
    }

    fun toggle(dose: TodayDose, taken: Boolean) {
        viewModelScope.launch {
            val occ = dose.occ
            withContext(Dispatchers.IO) {
                container.schedules.logDose(occ.scheduleId, occ.keyIso, if (taken) "taken" else "untaken")
                container.medicines.adjustDoses(occ.medSource, occ.medExtId, occ.medName, if (taken) -1 else 1)
            }
            refresh()
        }
    }

    /** Log every still-due, unlogged dose in a time block as taken ("take all"). */
    fun takeAll(doses: List<TodayDose>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                doses.filter { it.isPast && it.occ.status.isEmpty() }.forEach { d ->
                    container.schedules.logDose(d.occ.scheduleId, d.occ.keyIso, "taken")
                    container.medicines.adjustDoses(d.occ.medSource, d.occ.medExtId, d.occ.medName, -1)
                }
            }
            refresh()
        }
    }

    private suspend fun dosesAvailable(): com.uallsi.medaboutyou.domain.DosesAvailable {
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
}
