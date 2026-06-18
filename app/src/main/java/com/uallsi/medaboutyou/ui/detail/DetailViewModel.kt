// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.data.local.ActionCatalog
import com.uallsi.medaboutyou.data.remote.PosologyService
import com.uallsi.medaboutyou.model.Medicine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** In-app posology (SmPC §4.2) load state for the detail screen. */
sealed interface PosologyUi {
    data object Idle : PosologyUi
    data object Loading : PosologyUi
    data class Loaded(val text: String) : PosologyUi
    data object Unavailable : PosologyUi
    data class Error(val message: String) : PosologyUi
}

data class DetailState(
    val medicine: Medicine? = null,
    val stock: Int = 0,
    val imageUrl: String? = null,
    val imageLoading: Boolean = false,
    val posology: PosologyUi = PosologyUi.Idle,
)

/** Backing state for the medicine record screen (Android port of `MedicineDetail`). */
class DetailViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    fun load(medicine: Medicine) {
        _state.value = DetailState(medicine = medicine, imageLoading = true)
        viewModelScope.launch {
            // Cache live (AIFA) records so schedules/stock can reference them later.
            withContext(Dispatchers.IO) { container.medicines.upsertAll(listOf(medicine)) }
            refreshStock()
            val url = withContext(Dispatchers.IO) { container.images.fetchImageUrl(medicine) }
            _state.value = _state.value.copy(imageUrl = url, imageLoading = false)
        }
    }

    fun setStock(count: Int) {
        val med = _state.value.medicine ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.medicines.setDoses(med.source, med.extId, med.name, count)
            }
            container.actionLog.log(
                ActionCatalog.STOCK_SET,
                container.appContext.getString(R.string.action_txt_stock_set, med.name, count),
            )
            refreshStock()
        }
    }

    fun supplyStock(amount: Int) {
        val med = _state.value.medicine ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.medicines.adjustDoses(med.source, med.extId, med.name, amount)
            }
            container.actionLog.log(
                ActionCatalog.STOCK_ADDED,
                container.appContext.getString(R.string.action_txt_stock_added, amount, med.name),
            )
            refreshStock()
        }
    }

    /** Fetch and extract the SmPC §4.2 posology text in-app (EMA records). */
    fun loadPosology() {
        val med = _state.value.medicine ?: return
        if (_state.value.posology == PosologyUi.Loading) return
        _state.value = _state.value.copy(posology = PosologyUi.Loading)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { container.posology.fetch(med) }
            _state.value = _state.value.copy(
                posology = when (result) {
                    is PosologyService.Result.Text -> PosologyUi.Loaded(result.section)
                    PosologyService.Result.Unavailable -> PosologyUi.Unavailable
                    is PosologyService.Result.Error -> PosologyUi.Error(result.message)
                },
            )
        }
    }

    private suspend fun refreshStock() {
        val med = _state.value.medicine ?: return
        val stock = withContext(Dispatchers.IO) {
            container.medicines.availableDoses(med.source, med.extId, med.name)
        }
        _state.value = _state.value.copy(stock = stock)
    }
}
