package com.uallsi.medaboutyou.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.uallsi.medaboutyou.model.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** A caregiver to alert by SMS when a dose is overdue. */
data class Caregiver(val name: String, val phone: String)

/** Encode caregivers as `name\tphone` lines (tabs/newlines stripped from input). */
fun encodeCaregivers(list: List<Caregiver>): String =
    list.filter { it.phone.isNotBlank() }.joinToString("\n") {
        "${it.name.replace('\t', ' ').replace('\n', ' ')}\t${it.phone.replace('\t', ' ').replace('\n', ' ')}"
    }

fun decodeCaregivers(text: String): List<Caregiver> {
    if (text.isBlank()) return emptyList()
    return text.split("\n").mapNotNull { line ->
        val p = line.split("\t")
        if (p.size != 2 || p[1].isBlank()) null else Caregiver(p[0], p[1])
    }
}

/**
 * App preferences — the Android port of `AppSettings`. Fully local; stored in a
 * Preferences DataStore. Keys mirror the desktop config: source, image API key,
 * reminders on/off, plus "start at boot" (the Android analogue of autostart).
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
    val imageApiKeyFlow: Flow<String> = context.dataStore.data.map {
        it[IMAGE_API_KEY] ?: ""
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

    suspend fun setSource(source: Source) =
        context.dataStore.edit { it[SOURCE] = source.key }.let {}

    suspend fun setRemindersEnabled(enabled: Boolean) =
        context.dataStore.edit { it[REMINDERS] = enabled }.let {}

    suspend fun setStartAtBoot(enabled: Boolean) =
        context.dataStore.edit { it[START_AT_BOOT] = enabled }.let {}

    suspend fun setImageApiKey(key: String) =
        context.dataStore.edit { it[IMAGE_API_KEY] = key }.let {}

    suspend fun setVetIncluded(included: Boolean) =
        context.dataStore.edit { it[VET_INCLUDED] = included }.let {}

    suspend fun setUserName(name: String) =
        context.dataStore.edit { it[USER_NAME] = name }.let {}

    suspend fun setCaregivers(list: List<Caregiver>) =
        context.dataStore.edit { it[CAREGIVERS] = encodeCaregivers(list) }.let {}

    private companion object {
        val SOURCE = stringPreferencesKey("source")
        val REMINDERS = booleanPreferencesKey("reminders_enabled")
        val START_AT_BOOT = booleanPreferencesKey("start_at_boot")
        val IMAGE_API_KEY = stringPreferencesKey("image_api_key")
        val VET_INCLUDED = booleanPreferencesKey("vet_included")
        val USER_NAME = stringPreferencesKey("user_name")
        val CAREGIVERS = stringPreferencesKey("caregivers")
    }
}
