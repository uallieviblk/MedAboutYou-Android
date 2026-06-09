package com.uallsi.medaboutyou.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.data.local.Caregiver
import com.uallsi.medaboutyou.reminders.Reminders
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val remindersEnabled: Boolean = true,
    val startAtBoot: Boolean = false,
    val caregivers: List<Caregiver> = emptyList(),
)

/** Preferences screen state — Android port of `AppSettings` + the Preferences dialog. */
class SettingsViewModel(
    private val container: AppContainer,
    private val app: Application,
) : ViewModel() {

    val state: StateFlow<SettingsState> =
        combine(
            container.settings.remindersEnabledFlow,
            container.settings.startAtBootFlow,
            container.settings.caregiversFlow,
        ) { reminders, boot, caregivers -> SettingsState(reminders, boot, caregivers) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setRemindersEnabled(enabled)
            if (enabled) Reminders.enable(app) else Reminders.disable(app)
        }
    }

    fun setStartAtBoot(enabled: Boolean) {
        viewModelScope.launch { container.settings.setStartAtBoot(enabled) }
    }

    fun setCaregivers(list: List<Caregiver>) {
        viewModelScope.launch { container.settings.setCaregivers(list) }
    }
}
