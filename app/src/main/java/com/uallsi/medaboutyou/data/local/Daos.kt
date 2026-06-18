// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MedicineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<MedicineEntity>)

    /**
     * Case-insensitive search over name / inn / active substance, mirroring the
     * C++ `MedicineDatabase::search` (LIKE on the three fields). [query] must
     * already have `\`, `%` and `_` escaped (see `MedicineStore.search`) so user
     * input can't act as a LIKE wildcard.
     */
    @Query(
        """
        SELECT * FROM medicines
        WHERE source = :source
          AND (:humanOnly = 0 OR category = 'Human')
          AND (
            name LIKE '%' || :query || '%' ESCAPE '\'
            OR inn LIKE '%' || :query || '%' ESCAPE '\'
            OR active_substance LIKE '%' || :query || '%' ESCAPE '\'
          )
        ORDER BY name COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun search(source: String, query: String, humanOnly: Int, limit: Int): List<MedicineEntity>

    @Query("SELECT COUNT(*) FROM medicines WHERE source = :source")
    suspend fun count(source: String): Int
}

@Dao
interface MetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(row: MetaEntity)

    @Query("SELECT value FROM meta WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?
}

@Dao
interface InventoryDao {
    @Query("SELECT doses FROM inventory WHERE med_key = :key LIMIT 1")
    suspend fun doses(key: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(row: InventoryEntity)

    /**
     * Atomic stock adjustment (mirrors the C++ `MedicineDatabase::adjust_doses`
     * single-statement upsert) — no read-modify-write race, clamped at ≥ 0.
     */
    @Query(
        "INSERT INTO inventory (med_key, doses) VALUES (:key, MAX(0, :delta)) " +
            "ON CONFLICT(med_key) DO UPDATE SET doses = MAX(0, doses + :delta)"
    )
    suspend fun adjust(key: String, delta: Int)
}

@Dao
interface ScheduleDao {
    @Insert
    suspend fun insert(row: ScheduleEntity): Long

    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): ScheduleEntity?

    @Query("SELECT * FROM schedules WHERE (:includeCancelled = 1 OR active = 1) ORDER BY id DESC")
    suspend fun list(includeCancelled: Int): List<ScheduleEntity>

    @Query("UPDATE schedules SET active = 0, updated_at = :now WHERE id = :id")
    suspend fun cancel(id: Long, now: String)

    @Query("UPDATE schedules SET suspended = :suspended, suspended_until = :until, updated_at = :now WHERE id = :id")
    suspend fun setPause(id: Long, suspended: Boolean, until: String, now: String)

    @Query(
        "UPDATE schedules SET end_mode = :endMode, end_date = :endDate, " +
            "dose_count = :doseCount, updated_at = :now WHERE id = :id"
    )
    suspend fun updateEnd(id: Long, endMode: String, endDate: String, doseCount: Int, now: String)
}

@Dao
interface DoseLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: DoseLogEntity)

    @Query("SELECT * FROM dose_log WHERE schedule_id = :scheduleId")
    suspend fun forSchedule(scheduleId: Long): List<DoseLogEntity>

    @Query("SELECT status FROM dose_log WHERE schedule_id = :scheduleId AND scheduled_at = :scheduledAt LIMIT 1")
    suspend fun statusFor(scheduleId: Long, scheduledAt: String): String?

    @Query("SELECT * FROM dose_log")
    suspend fun all(): List<DoseLogEntity>

    /** Log rows of a schedule at/after the "YYYY-MM-DDTHH:MM" key [fromIso]. */
    @Query("SELECT * FROM dose_log WHERE schedule_id = :scheduleId AND scheduled_at >= :fromIso")
    suspend fun forScheduleFrom(scheduleId: Long, fromIso: String): List<DoseLogEntity>
}

@Dao
interface OccOverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: OccOverrideEntity)

    @Query("SELECT * FROM occ_override WHERE schedule_id = :scheduleId")
    suspend fun forSchedule(scheduleId: Long): List<OccOverrideEntity>

    @Query("SELECT * FROM occ_override")
    suspend fun all(): List<OccOverrideEntity>

    /** Override rows of a schedule at/after the "YYYY-MM-DDTHH:MM" key [fromIso]. */
    @Query("SELECT * FROM occ_override WHERE schedule_id = :scheduleId AND scheduled_at >= :fromIso")
    suspend fun forScheduleFrom(scheduleId: Long, fromIso: String): List<OccOverrideEntity>
}

/** Append-only pause windows per schedule (see [PausePeriodEntity]). */
@Dao
interface PausePeriodDao {
    @Insert
    suspend fun insert(row: PausePeriodEntity)

    @Query("SELECT * FROM pause_period")
    suspend fun all(): List<PausePeriodEntity>

    /**
     * Close every open or future-ending window of a schedule at [today]
     * (exclusive) — called on resume and before recording a new pause, so
     * windows never overlap and an early resume truncates a timed pause.
     */
    @Query(
        "UPDATE pause_period SET end_date = :today WHERE schedule_id = :scheduleId AND (end_date = '' OR end_date > :today)"
    )
    suspend fun closeAt(scheduleId: Long, today: String)

    /** Drop empty windows (paused and resumed the same day). */
    @Query("DELETE FROM pause_period WHERE schedule_id = :scheduleId AND end_date = start_date")
    suspend fun pruneEmpty(scheduleId: Long)
}

@Dao
interface DoseAlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: DoseAlertEntity)

    /** Timestamp of the last [kind] alert for this occurrence, or null if none. */
    @Query(
        "SELECT sent_at FROM dose_alert WHERE schedule_id = :scheduleId AND scheduled_at = :scheduledAt AND kind = :kind LIMIT 1"
    )
    suspend fun lastSentAt(scheduleId: Long, scheduledAt: String, kind: String): String?
}

/** The local activity log of user actions (newest first). */
@Dao
interface ActionLogDao {
    @Insert
    suspend fun insert(row: ActionLogEntity)

    @Query("SELECT * FROM action_log ORDER BY id DESC LIMIT 1000")
    fun recent(): kotlinx.coroutines.flow.Flow<List<ActionLogEntity>>

    /** Keep only the most recent [limit] rows (delete the rest). */
    @Query("DELETE FROM action_log WHERE id NOT IN (SELECT id FROM action_log ORDER BY id DESC LIMIT :limit)")
    suspend fun prune(limit: Int)
}

/** Durable outbox of caregiver MQTT alerts awaiting confirmed delivery. */
@Dao
interface MqttOutboxDao {
    /** Insert a pending alert; the returned row id is the alert's numeric id. */
    @Insert
    suspend fun insert(row: MqttOutboxEntity): Long

    @Query("SELECT * FROM mqtt_outbox ORDER BY id ASC")
    suspend fun pending(): List<MqttOutboxEntity>

    @Query("DELETE FROM mqtt_outbox WHERE id = :id")
    suspend fun delete(id: Long)

    /** Drop alerts older than [cutoffIso] (ISO-8601) — the max-age cap. */
    @Query("DELETE FROM mqtt_outbox WHERE created_at < :cutoffIso")
    suspend fun pruneOlderThan(cutoffIso: String)

    @Query("SELECT COUNT(*) FROM mqtt_outbox")
    suspend fun count(): Int
}

/** Refill shopping list (medicines the user flagged from a refill alert). */
@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_item ORDER BY added_at DESC")
    suspend fun all(): List<ShoppingItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ShoppingItemEntity)

    @Query("DELETE FROM shopping_item WHERE med_key = :key")
    suspend fun remove(key: String)
}

/** Bulk table dump/restore for the local encrypted backup (user data only). */
@Dao
interface BackupDao {
    @Query("SELECT * FROM schedules")
    suspend fun schedules(): List<ScheduleEntity>

    @Query("SELECT * FROM dose_log")
    suspend fun doseLogs(): List<DoseLogEntity>

    @Query("SELECT * FROM occ_override")
    suspend fun overrides(): List<OccOverrideEntity>

    @Query("SELECT * FROM inventory")
    suspend fun inventory(): List<InventoryEntity>

    @Query("SELECT * FROM dose_alert")
    suspend fun doseAlerts(): List<DoseAlertEntity>

    @Query("SELECT * FROM shopping_item")
    suspend fun shoppingItems(): List<ShoppingItemEntity>

    @Query("SELECT * FROM pause_period")
    suspend fun pausePeriods(): List<PausePeriodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSchedules(rows: List<ScheduleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDoseLogs(rows: List<DoseLogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOverrides(rows: List<OccOverrideEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putInventory(rows: List<InventoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDoseAlerts(rows: List<DoseAlertEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putShoppingItems(rows: List<ShoppingItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPausePeriods(rows: List<PausePeriodEntity>)

    @Query("DELETE FROM schedules")
    suspend fun clearSchedules()

    @Query("DELETE FROM dose_log")
    suspend fun clearDoseLogs()

    @Query("DELETE FROM occ_override")
    suspend fun clearOverrides()

    @Query("DELETE FROM inventory")
    suspend fun clearInventory()

    @Query("DELETE FROM dose_alert")
    suspend fun clearDoseAlerts()

    @Query("DELETE FROM shopping_item")
    suspend fun clearShoppingItems()

    @Query("DELETE FROM pause_period")
    suspend fun clearPausePeriods()
}
