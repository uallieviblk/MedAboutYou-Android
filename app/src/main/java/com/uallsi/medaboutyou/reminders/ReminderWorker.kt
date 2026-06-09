package com.uallsi.medaboutyou.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uallsi.medaboutyou.AppContainer
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
        val byId = container.schedules.list(false).associateBy { it.id }

        val userName = container.settings.userNameFlow.first()
        val caregivers = container.settings.caregiversFlow.first()
        val canSms = caregivers.isNotEmpty() && CaregiverAlerts.hasSmsPermission(applicationContext)

        val nowDt = LocalDateTime.now()
        val stampFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        fun parse(ts: String?) = ts?.let { runCatching { LocalDateTime.parse(it, stampFmt) }.getOrNull() }

        for (occ in todays) {
            if (occ.status.isNotEmpty()) continue                 // already taken or skipped
            val sch = byId[occ.scheduleId] ?: continue
            val scheduled = LocalDateTime.of(occ.year, occ.month, occ.day, occ.hour, occ.minute)
            val elapsed = ChronoUnit.MINUTES.between(scheduled, nowDt)
            if (elapsed < 0) continue                              // dose time not yet reached

            // Local reminder: first at the dose time, then repeated every
            // alertRefreshMin until the dose is taken (0 = remind once).
            val lastLocal = parse(container.schedules.alertLastSent(occ.scheduleId, occ.keyIso, KIND_LOCAL))
            val localDue = lastLocal == null ||
                (sch.alertRefreshMin > 0 && ChronoUnit.MINUTES.between(lastLocal, nowDt) >= sch.alertRefreshMin)
            if (localDue) {
                Notifications.show(applicationContext, occ.scheduleId, occ.keyIso, occ.medName, occ.timeLabel())
                container.schedules.recordAlert(occ.scheduleId, occ.keyIso, KIND_LOCAL)
            }

            // Caregiver escalation: once the dose is still untaken past
            // caregiverAlertMin, SMS every caregiver (once per dose).
            if (canSms && sch.caregiverAlertMin > 0 && elapsed >= sch.caregiverAlertMin) {
                if (container.schedules.alertLastSent(occ.scheduleId, occ.keyIso, KIND_CAREGIVER) == null) {
                    var anySent = false
                    for (cg in caregivers) {
                        if (CaregiverAlerts.sendOverdueSms(applicationContext, cg.phone, userName, occ.medName, occ.timeLabel())) {
                            anySent = true
                        }
                    }
                    if (anySent) container.schedules.recordAlert(occ.scheduleId, occ.keyIso, KIND_CAREGIVER)
                }
            }
        }
        return Result.success()
    }

    private companion object {
        const val KIND_LOCAL = "local"
        const val KIND_CAREGIVER = "caregiver"
    }
}
