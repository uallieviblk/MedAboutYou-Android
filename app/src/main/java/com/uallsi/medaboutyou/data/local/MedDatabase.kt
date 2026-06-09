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
        ImageCacheEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class MedDatabase : RoomDatabase() {
    abstract fun medicineDao(): MedicineDao
    abstract fun metaDao(): MetaDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun occOverrideDao(): OccOverrideDao
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

        fun get(context: Context): MedDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MedDatabase::class.java,
                    "medicines.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
