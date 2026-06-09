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
     * C++ `MedicineDatabase::search` (LIKE on the three fields).
     */
    @Query(
        """
        SELECT * FROM medicines
        WHERE source = :source
          AND (:humanOnly = 0 OR category = 'Human')
          AND (
            name LIKE '%' || :query || '%' COLLATE NOCASE
            OR inn LIKE '%' || :query || '%' COLLATE NOCASE
            OR active_substance LIKE '%' || :query || '%' COLLATE NOCASE
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
}

@Dao
interface OccOverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: OccOverrideEntity)

    @Query("SELECT * FROM occ_override WHERE schedule_id = :scheduleId")
    suspend fun forSchedule(scheduleId: Long): List<OccOverrideEntity>
}

@Dao
interface DoseAlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: DoseAlertEntity)

    /** Timestamp of the last [kind] alert for this occurrence, or null if none. */
    @Query("SELECT sent_at FROM dose_alert WHERE schedule_id = :scheduleId AND scheduled_at = :scheduledAt AND kind = :kind LIMIT 1")
    suspend fun lastSentAt(scheduleId: Long, scheduledAt: String, kind: String): String?
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
    @Query("SELECT * FROM schedules") suspend fun schedules(): List<ScheduleEntity>
    @Query("SELECT * FROM dose_log") suspend fun doseLogs(): List<DoseLogEntity>
    @Query("SELECT * FROM occ_override") suspend fun overrides(): List<OccOverrideEntity>
    @Query("SELECT * FROM inventory") suspend fun inventory(): List<InventoryEntity>
    @Query("SELECT * FROM dose_alert") suspend fun doseAlerts(): List<DoseAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSchedules(rows: List<ScheduleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDoseLogs(rows: List<DoseLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putOverrides(rows: List<OccOverrideEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putInventory(rows: List<InventoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDoseAlerts(rows: List<DoseAlertEntity>)

    @Query("DELETE FROM schedules") suspend fun clearSchedules()
    @Query("DELETE FROM dose_log") suspend fun clearDoseLogs()
    @Query("DELETE FROM occ_override") suspend fun clearOverrides()
    @Query("DELETE FROM inventory") suspend fun clearInventory()
    @Query("DELETE FROM dose_alert") suspend fun clearDoseAlerts()
}
