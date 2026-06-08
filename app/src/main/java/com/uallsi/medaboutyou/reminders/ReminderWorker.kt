package com.uallsi.medaboutyou.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.domain.Insights
import com.uallsi.medaboutyou.domain.Now
import kotlinx.coroutines.flow.first

/**
 * Periodically scans today's doses and raises a notification for any dose that
 * is **due and not yet logged** — the Android port of the desktop
 * `ReminderScheduler` 60-second scan (WorkManager's minimum period is 15 min).
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer(applicationContext)
        if (!container.settings.remindersEnabledFlow.first()) return Result.success()

        val now = Now.local()
        val snapshot = container.schedules.snapshot()
        val due = snapshot.occurrencesOn(now.year, now.month, now.day)
            .filter { it.status.isEmpty() && Insights.doseIsDue(it, now) }

        for (occ in due) {
            Notifications.show(
                applicationContext,
                occ.scheduleId,
                occ.keyIso,
                occ.medName,
                occ.timeLabel(),
            )
        }
        return Result.success()
    }
}
