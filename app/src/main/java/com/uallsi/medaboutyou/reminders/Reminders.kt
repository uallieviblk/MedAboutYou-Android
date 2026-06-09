// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules/cancels the periodic dose-reminder scan. */
object Reminders {
    private const val WORK_NAME = "dose_reminder_scan"

    fun enable(context: Context) {
        Notifications.ensureChannel(context)
        // Precise, on-the-minute alerts via a self-rescheduling exact alarm…
        DoseAlarms.kickNow(context)
        // …plus a 15-min WorkManager fallback that re-arms the alarm if it's
        // ever dropped (reboot, app standby).
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun disable(context: Context) {
        DoseAlarms.cancel(context)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
