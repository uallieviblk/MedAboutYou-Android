package com.uallsi.medaboutyou.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    version = 1,
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

        fun get(context: Context): MedDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MedDatabase::class.java,
                    "medicines.db",
                ).build().also { instance = it }
            }
    }
}
