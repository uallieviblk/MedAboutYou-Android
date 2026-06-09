// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.data.local.ShoppingItemEntity
import com.uallsi.medaboutyou.domain.AdherenceStats
import com.uallsi.medaboutyou.domain.DayAdherence
import com.uallsi.medaboutyou.domain.Insights
import com.uallsi.medaboutyou.domain.Now
import com.uallsi.medaboutyou.domain.RefillForecast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InsightsState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val rate7: Double = 0.0,
    val rate30: Double = 0.0,
    val rate90: Double = 0.0,
    val streak: Int = 0,
    val missed30: Int = 0,
    val heatmap: List<DayAdherence> = emptyList(),
    val byMedicine: List<Pair<String, AdherenceStats>> = emptyList(),
    val refills: List<RefillForecast> = emptyList(),
    val shoppingList: List<ShoppingItemEntity> = emptyList(),
)

/** Adherence & refills dashboard state — Android port of `DashboardView`. */
class InsightsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(InsightsState())
    val state: StateFlow<InsightsState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true)
            val now = Now.local()
            val snapshot = withContext(Dispatchers.IO) { container.schedules.snapshot() }
            val doses = withContext(Dispatchers.IO) { dosesAvailable() }
            val shopping = withContext(Dispatchers.IO) { container.shopping.all() }
            withContext(Dispatchers.Default) {
                val s = InsightsState(
                    loading = false,
                    refreshing = false,
                    rate7 = Insights.adherence(snapshot, 7, now).rate,
                    rate30 = Insights.adherence(snapshot, 30, now).let { it.rate },
                    rate90 = Insights.adherence(snapshot, 90, now).rate,
                    streak = Insights.currentStreak(snapshot, now),
                    missed30 = Insights.adherence(snapshot, 30, now).missed,
                    heatmap = Insights.dailyAdherence(snapshot, 30, now),
                    byMedicine = Insights.adherenceByMedicine(snapshot, 30, now),
                    refills = Insights.refillForecast(snapshot, doses, now),
                    shoppingList = shopping,
                )
                _state.value = s
            }
        }
    }

    fun removeFromShopping(medKey: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.shopping.remove(medKey) }
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
