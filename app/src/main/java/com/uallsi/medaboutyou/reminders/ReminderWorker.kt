package com.uallsi.medaboutyou.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Safety-net for the exact-alarm chain: exact alarms don't survive a reboot or
 * an app-standby kill, so this periodic worker (WorkManager min period 15 min)
 * runs one [AlertEngine] pass and re-arms the next exact alarm. Precise timing
 * comes from [DoseAlarms]; this just guarantees the chain stays alive.
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val next = AlertEngine.runOnce(applicationContext)
        if (next != null) DoseAlarms.scheduleAt(applicationContext, next)
        return Result.success()
    }
}
