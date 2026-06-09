package com.uallsi.medaboutyou.reminders

import android.content.Context
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.widget.TodayWidgetProvider
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * The single dose-alert pass, shared by the exact-alarm receiver and the
 * WorkManager fallback. It fires the due local reminders and caregiver SMS,
 * then returns the **exact** epoch-millis at which it next needs to run (the
 * earliest upcoming dose time, local-repeat, or caregiver escalation), so the
 * caller can set a precise alarm. Returns null when reminders are disabled.
 */
object AlertEngine {

    private const val KIND_LOCAL = "local"
    private const val KIND_CAREGIVER = "caregiver"
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    suspend fun runOnce(context: Context): Long? {
        val ctx = context.applicationContext
        val container = AppContainer(ctx)
        // Keep the home-screen widget current on every pass (incl. kicks after a
        // dose is taken or a schedule changes), regardless of reminder state.
        TodayWidgetProvider.refresh(ctx)
        if (!container.settings.remindersEnabledFlow.first()) return null

        val now = LocalDateTime.now()
        val snapshot = container.schedules.snapshot()
        val todays = snapshot.occurrencesOn(now.year, now.monthValue, now.dayOfMonth)
        val byId = container.schedules.list(false).associateBy { it.id }
        val userName = container.settings.userNameFlow.first()
        val caregivers = container.settings.caregiversFlow.first()
        val canSms = caregivers.isNotEmpty() && CaregiverAlerts.hasSmsPermission(ctx)

        fun parse(ts: String?) = ts?.let { runCatching { LocalDateTime.parse(it, STAMP) }.getOrNull() }

        // Always re-scan at the start of tomorrow to pick up the next day's doses.
        val candidates = mutableListOf(now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(1))

        for (occ in todays) {
            if (occ.status.isNotEmpty()) continue                 // taken or skipped
            val sch = byId[occ.scheduleId] ?: continue
            val scheduled = LocalDateTime.of(occ.year, occ.month, occ.day, occ.hour, occ.minute)

            if (scheduled.isAfter(now)) {
                candidates += scheduled                            // first reminder, exactly at dose time
                continue
            }

            // Local reminder: first now, then every alertRefreshMin until taken.
            val lastLocal = parse(container.schedules.alertLastSent(occ.scheduleId, occ.keyIso, KIND_LOCAL))
            val localDue = lastLocal == null ||
                (sch.alertRefreshMin > 0 && ChronoUnit.MINUTES.between(lastLocal, now) >= sch.alertRefreshMin)
            if (localDue) {
                Notifications.show(ctx, occ.scheduleId, occ.keyIso, occ.medName, occ.timeLabel())
                container.schedules.recordAlert(occ.scheduleId, occ.keyIso, KIND_LOCAL)
            }
            if (sch.alertRefreshMin > 0) {
                val base = if (localDue) now else lastLocal!!
                candidates += base.plusMinutes(sch.alertRefreshMin.toLong())
            }

            // Caregiver escalation once the dose stays untaken past caregiverAlertMin.
            if (sch.caregiverAlertMin > 0) {
                val escalateAt = scheduled.plusMinutes(sch.caregiverAlertMin.toLong())
                if (now.isBefore(escalateAt)) {
                    candidates += escalateAt
                } else if (canSms &&
                    container.schedules.alertLastSent(occ.scheduleId, occ.keyIso, KIND_CAREGIVER) == null
                ) {
                    var anySent = false
                    for (cg in caregivers) {
                        if (CaregiverAlerts.sendOverdueSms(ctx, cg.phone, userName, occ.medName, occ.timeLabel())) {
                            anySent = true
                        }
                    }
                    if (anySent) container.schedules.recordAlert(occ.scheduleId, occ.keyIso, KIND_CAREGIVER)
                }
            }
        }

        val nextMillis = candidates.filter { it.isAfter(now) }.min()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Never schedule in the past / too tight a loop.
        return maxOf(nextMillis, System.currentTimeMillis() + 5_000)
    }
}
