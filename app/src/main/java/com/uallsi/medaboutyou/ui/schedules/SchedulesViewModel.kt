// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.schedules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.data.local.ActionCatalog
import com.uallsi.medaboutyou.model.EndMode
import com.uallsi.medaboutyou.model.Schedule
import com.uallsi.medaboutyou.reminders.DoseAlarms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Backs the dedicated "Schedules" page: lists the active prescriptions and
 * cancels them (soft cancel, like the calendar did). Append-only — rows are
 * never deleted, only deactivated.
 */
class SchedulesViewModel(private val container: AppContainer) : ViewModel() {

    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            _schedules.value = withContext(Dispatchers.IO) { container.schedules.list(false) }
                .filter { isCurrent(it, today) }
        }
    }

    /**
     * Hide schedules that have already ended (e.g. the original half of an
     * edit, which is kept active+ended so its past stays in the adherence
     * history but should no longer appear as a running schedule). A COUNT
     * course is ended once its last dose date is behind us.
     */
    private fun isCurrent(s: Schedule, today: LocalDate): Boolean {
        if (s.endMode == EndMode.COUNT) {
            val last = com.uallsi.medaboutyou.model.ScheduleEngine.lastCountOccurrence(s)
            return last == null || !last.toLocalDate().isBefore(today)
        }
        if (s.endMode != EndMode.DATE) return true
        val end = runCatching {
            val p = s.endDate.split("-")
            LocalDate.of(p[0].toInt(), p[1].toInt(), p[2].toInt())
        }.getOrNull()
        return end == null || !end.isBefore(today)
    }

    fun cancel(id: Long) {
        viewModelScope.launch {
            val name = _schedules.value.find { it.id == id }?.medName.orEmpty()
            withContext(Dispatchers.IO) { container.schedules.cancel(id) }
            container.actionLog.log(
                ActionCatalog.SCHEDULE_CANCELLED,
                container.appContext.getString(R.string.action_txt_schedule_cancelled, name),
            )
            refresh()
            DoseAlarms.kickNow(container.appContext)
        }
    }

    /**
     * Pause a therapy: indefinitely ([suspended] = true) or until a date
     * ([until] = "YYYY-MM-DD"). Resume by passing false / "".
     */
    fun setPause(id: Long, suspended: Boolean, until: String) {
        viewModelScope.launch {
            val name = _schedules.value.find { it.id == id }?.medName.orEmpty()
            withContext(Dispatchers.IO) { container.schedules.setPause(id, suspended, until) }
            val (action, text) = when {
                suspended ->
                    ActionCatalog.SCHEDULE_PAUSED to
                        container.appContext.getString(R.string.action_txt_schedule_paused, name)
                until.isNotBlank() ->
                    ActionCatalog.SCHEDULE_PAUSED to
                        container.appContext.getString(R.string.action_txt_schedule_paused_until, name, until)
                else ->
                    ActionCatalog.SCHEDULE_RESUMED to
                        container.appContext.getString(R.string.action_txt_schedule_resumed, name)
            }
            container.actionLog.log(action, text)
            refresh()
            DoseAlarms.kickNow(container.appContext)
        }
    }

    /** Save edits to a schedule, effective from today (past is preserved). */
    fun applyEdit(schedule: Schedule) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.schedules.editFromNow(schedule) }
            container.actionLog.log(
                ActionCatalog.SCHEDULE_EDITED,
                container.appContext.getString(R.string.action_txt_schedule_edited, schedule.medName),
            )
            refresh()
            DoseAlarms.kickNow(container.appContext)
        }
    }
}
