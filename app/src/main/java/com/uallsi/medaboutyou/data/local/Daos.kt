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

    @Query("SELECT * FROM medicines WHERE source = :source AND ext_id = :extId LIMIT 1")
    suspend fun find(source: String, extId: String): MedicineEntity?
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
interface ImageCacheDao {
    @Query("SELECT * FROM image_cache WHERE med_key = :key LIMIT 1")
    suspend fun get(key: String): ImageCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(row: ImageCacheEntity)
}
