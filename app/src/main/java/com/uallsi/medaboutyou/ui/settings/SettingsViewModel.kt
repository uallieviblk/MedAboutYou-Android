// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.data.local.Caregiver
import com.uallsi.medaboutyou.reminders.DoseAlarms
import com.uallsi.medaboutyou.reminders.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsState(
    val remindersEnabled: Boolean = true,
    val startAtBoot: Boolean = false,
    val userName: String = "",
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
            container.settings.userNameFlow,
            container.settings.caregiversFlow,
        ) { reminders, boot, userName, caregivers ->
            SettingsState(reminders, boot, userName, caregivers)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setRemindersEnabled(enabled)
            if (enabled) Reminders.enable(app) else Reminders.disable(app)
        }
    }

    fun setStartAtBoot(enabled: Boolean) {
        viewModelScope.launch { container.settings.setStartAtBoot(enabled) }
    }

    fun setUserName(name: String) {
        viewModelScope.launch { container.settings.setUserName(name) }
    }

    fun setCaregivers(list: List<Caregiver>) {
        viewModelScope.launch { container.settings.setCaregivers(list) }
    }

    /** Write an encrypted backup to [uri]; [onDone] reports success or the error. */
    fun exportBackup(uri: Uri, password: String, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val blob = container.backup.export(password.toCharArray())
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { it.write(blob) }
                        ?: error("Couldn't open the destination file.")
                }
            }
            onDone(result)
        }
    }

    /** Restore from the encrypted backup at [uri]; [onDone] reports schedules restored. */
    fun importBackup(uri: Uri, password: String, onDone: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val blob = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Couldn't open the backup file.")
                }
                container.backup.restore(blob, password.toCharArray())
            }
            if (result.isSuccess) DoseAlarms.kickNow(app)
            onDone(result)
        }
    }
}
