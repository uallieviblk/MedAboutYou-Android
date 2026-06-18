// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.uallsi.medaboutyou.model.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * A caregiver to alert over MQTT when a dose is overdue. [username] is the last
 * segment of the publish topic (`<baseTopic>/<username>`), so a caregiver
 * subscribes to their own topic.
 */
data class Caregiver(val name: String, val username: String)

/** Encode caregivers as `name\tusername` lines (tabs/newlines stripped from input). */
fun encodeCaregivers(list: List<Caregiver>): String =
    list.filter { it.username.isNotBlank() }.joinToString("\n") {
        "${it.name.replace('\t', ' ').replace('\n', ' ')}\t${it.username.replace('\t', ' ').replace('\n', ' ')}"
    }

fun decodeCaregivers(text: String): List<Caregiver> {
    if (text.isBlank()) return emptyList()
    return text.split("\n").mapNotNull { line ->
        val p = line.split("\t")
        if (p.size != 2 || p[1].isBlank()) null else Caregiver(p[0], p[1])
    }
}

// No broker is shipped by default — the user sets their MQTT broker host in Settings.
const val DEFAULT_MQTT_HOST = ""
const val DEFAULT_MQTT_PORT = 1883
const val DEFAULT_MQTT_BASE_TOPIC = "medaboutyou/caregiver"

/** How many activity-log entries to keep (older ones are pruned). Configurable. */
const val DEFAULT_ACTION_LOG_LIMIT = 200

/**
 * Persistent MQTT broker configuration for caregiver alerts. Defaults to the
 * project's LAN broker. Broker auth ([username]/[password]) and TLS ([tls] with
 * the optional PEM [caCertPem] for a custom/self-signed broker CA and the
 * [clientCertPem]/[clientKeyPem] pair for mutual TLS) are all optional.
 */
data class MqttConfig(
    val enabled: Boolean = true,
    val host: String = DEFAULT_MQTT_HOST,
    val port: Int = DEFAULT_MQTT_PORT,
    val baseTopic: String = DEFAULT_MQTT_BASE_TOPIC,
    val username: String = "",
    val password: String = "",
    val tls: Boolean = false,
    val caCertPem: String = "",
    val clientCertPem: String = "",
    val clientKeyPem: String = "",
)

/**
 * App preferences — the Android port of `AppSettings`. Fully local; stored in a
 * Preferences DataStore. Keys mirror the desktop config: source, reminders
 * on/off, plus "start at boot" (the Android analogue of autostart) and the
 * persistent MQTT caregiver-alert broker config.
 */
class Settings(private val context: Context) {

    val sourceFlow: Flow<Source> = context.dataStore.data.map {
        Source.fromKey(it[SOURCE] ?: Source.EMA.key)
    }
    val remindersEnabledFlow: Flow<Boolean> = context.dataStore.data.map {
        it[REMINDERS] ?: true
    }
    val startAtBootFlow: Flow<Boolean> = context.dataStore.data.map {
        it[START_AT_BOOT] ?: false
    }
    val vetIncludedFlow: Flow<Boolean> = context.dataStore.data.map {
        it[VET_INCLUDED] ?: false
    }
    val userNameFlow: Flow<String> = context.dataStore.data.map {
        it[USER_NAME] ?: ""
    }
    val caregiversFlow: Flow<List<Caregiver>> = context.dataStore.data.map {
        decodeCaregivers(it[CAREGIVERS] ?: "")
    }
    val actionLogLimitFlow: Flow<Int> = context.dataStore.data.map {
        it[ACTION_LOG_LIMIT] ?: DEFAULT_ACTION_LOG_LIMIT
    }
    val mqttConfigFlow: Flow<MqttConfig> = context.dataStore.data.map { p ->
        MqttConfig(
            enabled = p[MQTT_ENABLED] ?: true,
            host = p[MQTT_HOST] ?: DEFAULT_MQTT_HOST,
            port = p[MQTT_PORT] ?: DEFAULT_MQTT_PORT,
            baseTopic = p[MQTT_BASE_TOPIC] ?: DEFAULT_MQTT_BASE_TOPIC,
            username = p[MQTT_USERNAME] ?: "",
            password = p[MQTT_PASSWORD] ?: "",
            tls = p[MQTT_TLS] ?: false,
            caCertPem = p[MQTT_CA_CERT] ?: "",
            clientCertPem = p[MQTT_CLIENT_CERT] ?: "",
            clientKeyPem = p[MQTT_CLIENT_KEY] ?: "",
        )
    }

    suspend fun setSource(source: Source) =
        context.dataStore.edit { it[SOURCE] = source.key }.let {}

    suspend fun setRemindersEnabled(enabled: Boolean) =
        context.dataStore.edit { it[REMINDERS] = enabled }.let {}

    suspend fun setStartAtBoot(enabled: Boolean) =
        context.dataStore.edit { it[START_AT_BOOT] = enabled }.let {}

    suspend fun setVetIncluded(included: Boolean) =
        context.dataStore.edit { it[VET_INCLUDED] = included }.let {}

    suspend fun setUserName(name: String) =
        context.dataStore.edit { it[USER_NAME] = name }.let {}

    suspend fun setCaregivers(list: List<Caregiver>) =
        context.dataStore.edit { it[CAREGIVERS] = encodeCaregivers(list) }.let {}

    suspend fun setActionLogLimit(limit: Int) =
        context.dataStore.edit { it[ACTION_LOG_LIMIT] = limit }.let {}

    suspend fun setMqttConfig(config: MqttConfig) {
        context.dataStore.edit {
            it[MQTT_ENABLED] = config.enabled
            it[MQTT_HOST] = config.host
            it[MQTT_PORT] = config.port
            it[MQTT_BASE_TOPIC] = config.baseTopic
            it[MQTT_USERNAME] = config.username
            it[MQTT_PASSWORD] = config.password
            it[MQTT_TLS] = config.tls
            it[MQTT_CA_CERT] = config.caCertPem
            it[MQTT_CLIENT_CERT] = config.clientCertPem
            it[MQTT_CLIENT_KEY] = config.clientKeyPem
        }
    }

    /** Stable per-install MQTT client id (generated once) so the broker session resumes. */
    suspend fun mqttClientId(): String {
        context.dataStore.data.first()[MQTT_CLIENT_ID]?.let { return it }
        val id = "medaboutyou-" + UUID.randomUUID()
        context.dataStore.edit { it[MQTT_CLIENT_ID] = id }
        return id
    }

    private companion object {
        val SOURCE = stringPreferencesKey("source")
        val REMINDERS = booleanPreferencesKey("reminders_enabled")
        val START_AT_BOOT = booleanPreferencesKey("start_at_boot")
        val VET_INCLUDED = booleanPreferencesKey("vet_included")
        val USER_NAME = stringPreferencesKey("user_name")
        val CAREGIVERS = stringPreferencesKey("caregivers")
        val ACTION_LOG_LIMIT = intPreferencesKey("action_log_limit")
        val MQTT_ENABLED = booleanPreferencesKey("mqtt_enabled")
        val MQTT_HOST = stringPreferencesKey("mqtt_host")
        val MQTT_PORT = intPreferencesKey("mqtt_port")
        val MQTT_BASE_TOPIC = stringPreferencesKey("mqtt_base_topic")
        val MQTT_USERNAME = stringPreferencesKey("mqtt_username")
        val MQTT_PASSWORD = stringPreferencesKey("mqtt_password")
        val MQTT_TLS = booleanPreferencesKey("mqtt_tls")
        val MQTT_CA_CERT = stringPreferencesKey("mqtt_ca_cert")
        val MQTT_CLIENT_CERT = stringPreferencesKey("mqtt_client_cert")
        val MQTT_CLIENT_KEY = stringPreferencesKey("mqtt_client_key")
        val MQTT_CLIENT_ID = stringPreferencesKey("mqtt_client_id")
    }
}
