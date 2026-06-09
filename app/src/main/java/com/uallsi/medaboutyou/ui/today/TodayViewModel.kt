// SPDX-License-Identifier: AGPL-3.0-or-later
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

/**
 * A dose on today's timeline. [checkable] is true once the dose's intake window
 * has opened (scheduled time − window), so it may be marked taken from then on.
 */
data class TodayDose(val occ: Occurrence, val stock: Int, val isPast: Boolean, val checkable: Boolean)

data class TodayState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val doses: List<TodayDose> = emptyList(),
    val takenToday: Int = 0,
    val totalToday: Int = 0,
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
                    checkable = ScheduleEngine.isWithinTakeWindow(
                        occ.year, occ.month, occ.day, occ.hour, occ.minute, occ.windowMinutes,
                    ),
                )
            }
            // The ring tracks progress over ALL of today's doses (including ones
            // still upcoming), so a dose you've yet to take is reflected.
            val takenToday = todays.count { it.status == "taken" }
            val totalToday = todays.size
            val streak = Insights.currentStreak(snapshot, now)

            val refills = withContext(Dispatchers.IO) {
                Insights.refillForecast(snapshot, dosesAvailable(), now)
            }
            // Only surface the home banner when a run-out is genuinely near; the
            // full, urgency-sorted list lives in Insights → Next refill.
            val today = java.time.LocalDate.of(now.year, now.month, now.day)
            val soonest = refills.firstOrNull()?.takeIf { r ->
                val runOut = java.time.LocalDate.of(r.year, r.month, r.day)
                java.time.temporal.ChronoUnit.DAYS.between(today, runOut) <= REFILL_SOON_DAYS
            }

            _state.value = TodayState(
                loading = false,
                refreshing = false,
                doses = doses,
                takenToday = takenToday,
                totalToday = totalToday,
                streak = streak,
                nextRefill = soonest,
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

    private companion object {
        // A run-out within this many days counts as "refill soon" on the home banner.
        const val REFILL_SOON_DAYS = 10L
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
