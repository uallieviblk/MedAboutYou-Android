// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.data.local

import androidx.room.withTransaction
import com.uallsi.medaboutyou.model.Source
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-protected, **fully local** backup of the user's data — the
 * privacy-respecting alternative to cloud sync. The payload (schedules, dose
 * log, overrides, stock, alert bookkeeping and the relevant settings) is
 * serialised to JSON and encrypted with AES-256-GCM (key derived from the user's
 * password via PBKDF2). The cached medicines catalogue is intentionally excluded
 * — it is re-downloadable.
 *
 * File layout: `MABU` magic · format byte · 16-byte salt · 12-byte IV · ciphertext.
 */
class BackupManager(private val db: MedDatabase, private val settings: Settings) {

    private val dao = db.backupDao()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /** Typed failure, mapped to a localized message by the settings UI. */
    class BackupException(val kind: Kind) : Exception(kind.name) {
        enum class Kind { NOT_A_BACKUP, WRONG_PASSWORD, NEWER_VERSION }
    }

    @Serializable
    data class BackupData(
        val schemaVersion: Int,
        val schedules: List<ScheduleEntity>,
        val doseLogs: List<DoseLogEntity>,
        val overrides: List<OccOverrideEntity>,
        val inventory: List<InventoryEntity>,
        val doseAlerts: List<DoseAlertEntity>,
        val settings: Map<String, String> = emptyMap(),
        // Added later; defaults keep older backups restorable.
        val shoppingItems: List<ShoppingItemEntity> = emptyList(),
        val pausePeriods: List<PausePeriodEntity> = emptyList(),
    )

    /** Collect everything into an encrypted blob ready to write to a file. */
    suspend fun export(password: CharArray): ByteArray {
        // Read all tables in one transaction so the backup is a consistent
        // snapshot (a dose taken mid-export can't appear in one table only).
        val data = db.withTransaction {
            BackupData(
                schemaVersion = SCHEMA_VERSION,
                schedules = dao.schedules(),
                doseLogs = dao.doseLogs(),
                overrides = dao.overrides(),
                inventory = dao.inventory(),
                doseAlerts = dao.doseAlerts(),
                shoppingItems = dao.shoppingItems(),
                pausePeriods = dao.pausePeriods(),
                settings = mapOf(
                    "userName" to settings.userNameFlow.first(),
                    "caregivers" to encodeCaregivers(settings.caregiversFlow.first()),
                    "reminders" to settings.remindersEnabledFlow.first().toString(),
                    "startAtBoot" to settings.startAtBootFlow.first().toString(),
                    "source" to settings.sourceFlow.first().key,
                    "vetIncluded" to settings.vetIncludedFlow.first().toString(),
                ),
            )
        }
        return encrypt(json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8), password)
    }

    /**
     * Replace the local data with the backup. Returns the number of schedules
     * restored, or throws a [BackupException] on a wrong password / unreadable
     * / too-new backup.
     */
    suspend fun restore(blob: ByteArray, password: CharArray): Int {
        val plain = decrypt(blob, password)
        val data = json.decodeFromString(BackupData.serializer(), plain.toString(Charsets.UTF_8))
        if (data.schemaVersion > SCHEMA_VERSION) {
            throw BackupException(BackupException.Kind.NEWER_VERSION)
        }
        db.withTransaction {
            dao.clearDoseAlerts()
            dao.clearOverrides()
            dao.clearDoseLogs()
            dao.clearInventory()
            dao.clearShoppingItems()
            dao.clearPausePeriods()
            dao.clearSchedules()
            dao.putSchedules(data.schedules)
            dao.putDoseLogs(data.doseLogs)
            dao.putOverrides(data.overrides)
            dao.putInventory(data.inventory)
            dao.putDoseAlerts(data.doseAlerts)
            dao.putShoppingItems(data.shoppingItems)
            dao.putPausePeriods(data.pausePeriods)
        }
        data.settings["userName"]?.let { settings.setUserName(it) }
        data.settings["caregivers"]?.let { settings.setCaregivers(decodeCaregivers(it)) }
        data.settings["reminders"]?.let { settings.setRemindersEnabled(it.toBoolean()) }
        data.settings["startAtBoot"]?.let { settings.setStartAtBoot(it.toBoolean()) }
        data.settings["source"]?.let { settings.setSource(Source.fromKey(it)) }
        data.settings["vetIncluded"]?.let { settings.setVetIncluded(it.toBoolean()) }
        return data.schedules.size
    }

    // --- AES-256-GCM with a PBKDF2-derived key ---

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 256)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain)
        return MAGIC + byteArrayOf(FORMAT) + salt + iv + ct
    }

    private fun decrypt(blob: ByteArray, password: CharArray): ByteArray {
        if (blob.size <= MAGIC.size + 1 + 16 + 12 || !blob.copyOf(MAGIC.size).contentEquals(MAGIC)) {
            throw BackupException(BackupException.Kind.NOT_A_BACKUP)
        }
        // A format byte we don't know means a newer key-derivation/layout —
        // refuse cleanly instead of mis-deriving and reporting "wrong password".
        if (blob[MAGIC.size] != FORMAT) {
            throw BackupException(BackupException.Kind.NEWER_VERSION)
        }
        var off = MAGIC.size + 1
        val salt = blob.copyOfRange(off, off + 16)
        off += 16
        val iv = blob.copyOfRange(off, off + 12)
        off += 12
        val ct = blob.copyOfRange(off, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        return try {
            cipher.doFinal(ct)
        } catch (_: javax.crypto.AEADBadTagException) {
            throw BackupException(BackupException.Kind.WRONG_PASSWORD)
        }
    }

    private companion object {
        // Bump in lockstep with MedDatabase.version. The mqtt_outbox (transient)
        // and action_log (local activity log) tables are intentionally not part
        // of the backup. v10 = schema with mqtt_outbox + action_log.
        const val SCHEMA_VERSION = 10
        const val PBKDF2_ITERATIONS = 120_000
        const val FORMAT: Byte = 1
        val MAGIC = "MABU".toByteArray(Charsets.US_ASCII)
    }
}
