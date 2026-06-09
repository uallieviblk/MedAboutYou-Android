// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.model.Medicine
import com.uallsi.medaboutyou.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchState(
    val source: Source = Source.EMA,
    val query: String = "",
    val vetIncluded: Boolean = false,
    val results: List<Medicine> = emptyList(),
    val loading: Boolean = false,
    val statusLine: String = "",
    val datasetTimestamp: String = "",
    val needsDownload: Boolean = false,
)

/**
 * Search half of the app — Android port of `MedWindow`'s sidebar. Replaces the
 * jthread + generation-counter pattern with coroutine cancellation
 * (collectLatest-style: each keystroke cancels the prior search job).
 */
class SearchViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val source = container.settings.sourceFlow.first()
            val vet = container.settings.vetIncludedFlow.first()
            val count = container.medicines.count(Source.EMA)
            _state.value = _state.value.copy(
                source = source,
                vetIncluded = vet,
                needsDownload = source == Source.EMA && count == 0,
                datasetTimestamp = container.medicines.getMeta("ema_timestamp") ?: "",
            )
            runSearch()
        }
    }

    fun setSource(source: Source) {
        viewModelScope.launch { container.settings.setSource(source) }
        _state.value = _state.value.copy(source = source)
        runSearch()
    }

    fun setVetIncluded(included: Boolean) {
        viewModelScope.launch { container.settings.setVetIncluded(included) }
        _state.value = _state.value.copy(vetIncluded = included)
        runSearch()
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
        runSearch()
    }

    private fun runSearch() {
        searchJob?.cancel()
        val s = _state.value
        searchJob = viewModelScope.launch {
            if (s.source == Source.EMA) {
                val results = withContext(Dispatchers.IO) {
                    container.medicines.search(Source.EMA, s.query, !s.vetIncluded, 250)
                }
                _state.value = _state.value.copy(
                    results = results,
                    loading = false,
                    statusLine = countLine(results.size, s.datasetTimestamp),
                )
            } else {
                if (s.query.trim().length < 2) {
                    _state.value = _state.value.copy(
                        results = emptyList(),
                        loading = false,
                        statusLine = ctx.getString(R.string.aifa_min_chars),
                    )
                    return@launch
                }
                _state.value = _state.value.copy(loading = true, statusLine = ctx.getString(R.string.searching_aifa))
                delay(250) // debounce keystrokes
                val results = withContext(Dispatchers.IO) { container.aifa.search(s.query) }
                _state.value = _state.value.copy(
                    results = results,
                    loading = false,
                    statusLine = if (results.isEmpty() && container.aifa.lastError.isNotEmpty())
                        container.aifa.lastError else countLine(results.size, ""),
                )
            }
        }
    }

    /** Re-download the EMA dataset and cache it locally. */
    fun refreshEma(onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, statusLine = ctx.getString(R.string.downloading_ema))
            val result = withContext(Dispatchers.IO) { container.ema.refresh() }
            if (result == null) {
                _state.value = _state.value.copy(loading = false, statusLine = container.ema.lastError)
                onDone(false)
                return@launch
            }
            withContext(Dispatchers.IO) {
                container.medicines.upsertAll(result.medicines)
                container.medicines.setMeta("ema_timestamp", result.timestamp)
            }
            _state.value = _state.value.copy(
                datasetTimestamp = result.timestamp,
                needsDownload = false,
                loading = false,
            )
            runSearch()
            onDone(true)
        }
    }

    private val ctx get() = container.appContext

    private fun countLine(count: Int, timestamp: String): String {
        val base = ctx.resources.getQuantityString(R.plurals.medicines_count, count, count)
        return if (timestamp.isNotEmpty()) ctx.getString(R.string.count_with_date, base, timestamp) else base
    }
}
