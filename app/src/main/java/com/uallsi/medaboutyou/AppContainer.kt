package com.uallsi.medaboutyou

import android.content.Context
import com.uallsi.medaboutyou.data.local.MedDatabase
import com.uallsi.medaboutyou.data.local.MedicineStore
import com.uallsi.medaboutyou.data.local.ScheduleRepository
import com.uallsi.medaboutyou.data.local.Settings
import com.uallsi.medaboutyou.data.remote.AifaSource
import com.uallsi.medaboutyou.data.remote.EmaDataset
import com.uallsi.medaboutyou.data.remote.ImageService

/**
 * Hand-rolled service locator (no DI framework needed). Holds the long-lived,
 * fully local data layer — the Android equivalent of the services `MedWindow`
 * owns as members.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val db: MedDatabase by lazy { MedDatabase.get(appContext) }
    val medicines: MedicineStore by lazy { MedicineStore(db) }
    val schedules: ScheduleRepository by lazy { ScheduleRepository(db) }
    val settings: Settings by lazy { Settings(appContext) }
    val ema: EmaDataset by lazy { EmaDataset() }
    val aifa: AifaSource by lazy { AifaSource() }
    val images: ImageService by lazy { ImageService() }
}
