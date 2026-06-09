package com.uallsi.medaboutyou.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.domain.Insights
import com.uallsi.medaboutyou.domain.Now
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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
        val todays = snapshot.occurrencesOn(now.year, now.month, now.day)
        val due = todays.filter { it.status.isEmpty() && Insights.doseIsDue(it, now) }

        for (occ in due) {
            Notifications.show(
                applicationContext,
                occ.scheduleId,
                occ.keyIso,
                occ.medName,
                occ.timeLabel(),
            )
        }

        escalateToCaregiver(container, todays)
        return Result.success()
    }

    /**
     * SMS every configured caregiver about still-untaken doses whose caregiver
     * timeout has elapsed. The first alert fires at [Schedule.caregiverAlertMin];
     * if [Schedule.alertRefreshMin] > 0 it re-sends every that-many minutes while
     * the dose stays untaken, stopping once the intake window closes. Sends are
     * timestamped in the caregiver_alert table to drive the refresh cadence.
     */
    private suspend fun escalateToCaregiver(
        container: AppContainer,
        todays: List<com.uallsi.medaboutyou.model.Occurrence>,
    ) {
        val caregivers = container.settings.caregiversFlow.first()
        if (caregivers.isEmpty() || !CaregiverAlerts.hasSmsPermission(applicationContext)) return

        val byId = container.schedules.list(false).associateBy { it.id }
        val nowDt = LocalDateTime.now()
        val stampFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        for (occ in todays) {
            if (occ.status.isNotEmpty()) continue                 // taken or skipped
            val sch = byId[occ.scheduleId] ?: continue
            val alertMin = sch.caregiverAlertMin
            if (alertMin <= 0) continue

            val scheduled = LocalDateTime.of(occ.year, occ.month, occ.day, occ.hour, occ.minute)
            val elapsed = ChronoUnit.MINUTES.between(scheduled, nowDt)
            // Alert only within [alertMin, window): before the intake deadline.
            if (elapsed < alertMin || elapsed >= sch.windowMinutes) continue

            val lastSent = container.schedules.caregiverAlertLastSent(occ.scheduleId, occ.keyIso)
                ?.let { runCatching { LocalDateTime.parse(it, stampFmt) }.getOrNull() }
            val due = when {
                lastSent == null -> true                                   // first alert
                sch.alertRefreshMin <= 0 -> false                          // single alert only
                else -> ChronoUnit.MINUTES.between(lastSent, nowDt) >= sch.alertRefreshMin
            }
            if (!due) continue

            var anySent = false
            for (cg in caregivers) {
                if (CaregiverAlerts.sendOverdueSms(applicationContext, cg.phone, occ.medName, occ.timeLabel())) {
                    anySent = true
                }
            }
            if (anySent) container.schedules.recordCaregiverAlert(occ.scheduleId, occ.keyIso)
        }
    }
}
