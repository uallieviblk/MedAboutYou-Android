package com.uallsi.medaboutyou.ui.schedules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.model.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            _schedules.value = withContext(Dispatchers.IO) { container.schedules.list(false) }
        }
    }

    fun cancel(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.schedules.cancel(id) }
            refresh()
        }
    }
}
