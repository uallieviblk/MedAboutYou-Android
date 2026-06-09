package com.uallsi.medaboutyou.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The single, fully local SQLite database — the Android equivalent of the
 * desktop app's `~/.local/share/medaboutyou/medicines.db`. Nothing about the
 * user's medications ever leaves the device.
 */
@Database(
    entities = [
        MedicineEntity::class,
        MetaEntity::class,
        InventoryEntity::class,
        ScheduleEntity::class,
        DoseLogEntity::class,
        OccOverrideEntity::class,
        DoseAlertEntity::class,
        ImageCacheEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class MedDatabase : RoomDatabase() {
    abstract fun medicineDao(): MedicineDao
    abstract fun metaDao(): MetaDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun occOverrideDao(): OccOverrideDao
    abstract fun doseAlertDao(): DoseAlertDao
    abstract fun imageCacheDao(): ImageCacheDao

    companion object {
        @Volatile
        private var instance: MedDatabase? = null

        /**
         * v1 → v2: add the `times` column holding the serialised list of
         * [com.uallsi.medaboutyou.model.DoseTime]s. Existing rows keep an empty
         * string and fall back to their legacy (hour, minute) on read.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN times TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v2 → v3: add the per-schedule reminder-repeat and caregiver-alert
         * timeouts, plus a dose_alert table that paces the repeating local
         * reminder and fires the caregiver SMS once (keyed by alert kind).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN caregiver_alert_min INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE schedules ADD COLUMN alert_refresh_min INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS dose_alert (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "schedule_id INTEGER NOT NULL, scheduled_at TEXT NOT NULL, " +
                        "kind TEXT NOT NULL, sent_at TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_dose_alert_schedule_id_scheduled_at_kind " +
                        "ON dose_alert (schedule_id, scheduled_at, kind)",
                )
            }
        }

        /** v3 → v4: add the per-schedule "suspended" (paused) flag. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN suspended INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): MedDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MedDatabase::class.java,
                    "medicines.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
            }
    }
}
