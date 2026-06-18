// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.data.local.ActionCatalog
import com.uallsi.medaboutyou.data.local.BackupManager
import com.uallsi.medaboutyou.data.local.Caregiver
import com.uallsi.medaboutyou.data.local.DEFAULT_ACTION_LOG_LIMIT
import com.uallsi.medaboutyou.data.local.MqttConfig
import com.uallsi.medaboutyou.reminders.DoseAlarms
import com.uallsi.medaboutyou.reminders.MqttOutboxWorker
import com.uallsi.medaboutyou.reminders.MqttPublisher
import com.uallsi.medaboutyou.reminders.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsState(
    val remindersEnabled: Boolean = true,
    val startAtBoot: Boolean = false,
    val userName: String = "",
    val caregivers: List<Caregiver> = emptyList(),
    val mqtt: MqttConfig = MqttConfig(),
    val actionLogLimit: Int = DEFAULT_ACTION_LOG_LIMIT,
)

/** Ephemeral result of the Settings "Test connection" probe (not persisted). */
enum class MqttTestStatus { Idle, Testing, Success, Failure }

data class MqttTestState(val status: MqttTestStatus = MqttTestStatus.Idle, val message: String = "")

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
            container.settings.mqttConfigFlow,
        ) { reminders, boot, userName, caregivers, mqtt ->
            SettingsState(reminders, boot, userName, caregivers, mqtt)
        }.combine(container.settings.actionLogLimitFlow) { s, limit ->
            s.copy(actionLogLimit = limit)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

    private val _mqttTest = MutableStateFlow(MqttTestState())

    /** Result of the most recent broker "Test connection" probe. */
    val mqttTest: StateFlow<MqttTestState> = _mqttTest.asStateFlow()

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setRemindersEnabled(enabled)
            if (enabled) Reminders.enable(app) else Reminders.disable(app)
            container.actionLog.log(
                ActionCatalog.REMINDERS_TOGGLED,
                app.getString(if (enabled) R.string.action_txt_reminders_on else R.string.action_txt_reminders_off),
            )
        }
    }

    fun setStartAtBoot(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setStartAtBoot(enabled)
            container.actionLog.log(
                ActionCatalog.BOOT_TOGGLED,
                app.getString(if (enabled) R.string.action_txt_boot_on else R.string.action_txt_boot_off),
            )
        }
    }

    fun setUserName(name: String) {
        viewModelScope.launch { container.settings.setUserName(name) }
    }

    fun setCaregivers(list: List<Caregiver>) {
        viewModelScope.launch {
            // Log only discrete add/remove (a size change), never per-keystroke edits.
            val before = state.value.caregivers.size
            container.settings.setCaregivers(list)
            when {
                list.size > before -> container.actionLog.log(
                    ActionCatalog.CAREGIVER_ADDED,
                    app.getString(R.string.action_txt_caregiver_added),
                )
                list.size < before -> container.actionLog.log(
                    ActionCatalog.CAREGIVER_REMOVED,
                    app.getString(R.string.action_txt_caregiver_removed),
                )
            }
        }
    }

    fun setMqttConfig(config: MqttConfig) {
        // Editing the broker config invalidates any earlier test result.
        _mqttTest.value = MqttTestState()
        viewModelScope.launch {
            container.settings.setMqttConfig(config)
            // Flush any queued alerts promptly with the new broker settings.
            MqttOutboxWorker.kick(app)
        }
    }

    /**
     * Probe the broker described by [config] (the values currently typed, which need
     * not be saved yet) and surface the outcome in [mqttTest]. Runs an isolated
     * connection that never disturbs the durable delivery session.
     */
    fun testMqttConnection(config: MqttConfig) {
        if (config.host.isBlank()) {
            _mqttTest.value = MqttTestState(MqttTestStatus.Failure, app.getString(R.string.mqtt_test_no_host))
            return
        }
        _mqttTest.value = MqttTestState(MqttTestStatus.Testing)
        viewModelScope.launch {
            val result = MqttPublisher.testConnection(config, container.settings.mqttClientId())
            _mqttTest.value = result.fold(
                onSuccess = { MqttTestState(MqttTestStatus.Success, app.getString(R.string.mqtt_test_ok)) },
                onFailure = { e ->
                    MqttTestState(MqttTestStatus.Failure, app.getString(R.string.mqtt_test_fail, mqttErrorReason(e)))
                },
            )
        }
    }

    /** Boil a Paho exception chain down to a short, human-readable reason. */
    private fun mqttErrorReason(e: Throwable): String =
        (e.cause?.message ?: e.message ?: e.javaClass.simpleName).take(160)

    fun setActionLogLimit(limit: Int) {
        viewModelScope.launch { container.settings.setActionLogLimit(limit) }
    }

    /** Write an encrypted backup to [uri]; [onDone] reports success or the error. */
    fun exportBackup(uri: Uri, password: String, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                // PBKDF2 + AES over the whole dataset is CPU work — keep it off
                // the main thread (an ANR-sized freeze on slow devices).
                val blob = withContext(Dispatchers.Default) { container.backup.export(password.toCharArray()) }
                withContext(Dispatchers.IO) {
                    // "wt": SAF providers (e.g. Drive) don't guarantee "w"
                    // truncates — a smaller re-export would leave stale trailing
                    // bytes and corrupt the file.
                    app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(blob) }
                        ?: error(app.getString(R.string.backup_err_open_dest))
                }
            }.localizeBackupError()
            if (result.isSuccess) {
                container.actionLog.log(
                    ActionCatalog.BACKUP_EXPORTED,
                    app.getString(R.string.action_txt_backup_exported)
                )
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
                        ?: error(app.getString(R.string.backup_err_open_file))
                }
                withContext(Dispatchers.Default) { container.backup.restore(blob, password.toCharArray()) }
            }.localizeBackupError()
            if (result.isSuccess) {
                DoseAlarms.kickNow(app)
                container.actionLog.log(
                    ActionCatalog.BACKUP_RESTORED,
                    app.getString(R.string.action_txt_backup_restored, result.getOrDefault(0)),
                )
            }
            onDone(result)
        }
    }

    /** Map [BackupManager.BackupException] kinds onto localized messages. */
    private fun <T> Result<T>.localizeBackupError(): Result<T> = recoverCatching { e ->
        val kind = (e as? BackupManager.BackupException)?.kind ?: throw e
        throw IllegalArgumentException(
            app.getString(
                when (kind) {
                    BackupManager.BackupException.Kind.NOT_A_BACKUP -> R.string.backup_err_not_backup
                    BackupManager.BackupException.Kind.WRONG_PASSWORD -> R.string.backup_err_wrong_password
                    BackupManager.BackupException.Kind.NEWER_VERSION -> R.string.backup_err_newer
                }
            )
        )
    }
}
