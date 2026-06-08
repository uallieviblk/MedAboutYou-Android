package com.uallsi.medaboutyou.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.model.Medicine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DetailState(
    val medicine: Medicine? = null,
    val stock: Int = 0,
    val imageUrl: String? = null,
    val imageLoading: Boolean = false,
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
            refreshStock()
        }
    }

    fun supplyStock(amount: Int) {
        val med = _state.value.medicine ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.medicines.adjustDoses(med.source, med.extId, med.name, amount)
            }
            refreshStock()
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
