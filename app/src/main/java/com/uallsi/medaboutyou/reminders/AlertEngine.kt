// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.reminders

import android.content.Context
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.domain.DosesAvailable
import com.uallsi.medaboutyou.domain.Insights
import com.uallsi.medaboutyou.domain.Now
import com.uallsi.medaboutyou.widget.TodayWidgetProvider
import kotlinx.coroutines.flow.first
import java.time.LocalDate
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
    private const val REFILL_SOON_DAYS = 7L
    private const val META_REFILL_SCAN = "refill_scan_date"

    // A live dose reminder is kept only while the dose is within its intake
    // window; this is the floor for that window (so tiny/zero windows still fire
    // the on-time reminder, and high-frequency schedules don't back-fill).
    private const val MIN_REMINDER_MIN = 10L
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

            // Past dose, still unlogged. Keep a live reminder only while it's
            // within its intake window; once the window closes it's "missed", so
            // withdraw any stale reminder instead of leaving it. This stops
            // high-frequency schedules (e.g. hourly) from posting a separate
            // notification for every past dose since midnight.
            val windowEnd = scheduled.plusMinutes(maxOf(sch.windowMinutes.toLong(), MIN_REMINDER_MIN))
            if (now.isAfter(windowEnd)) {
                Notifications.withdraw(ctx, occ.scheduleId, occ.keyIso)
            } else {
                // Local reminder: when due, then every alertRefreshMin (within the window) until taken.
                val lastLocal = parse(container.schedules.alertLastSent(occ.scheduleId, occ.keyIso, KIND_LOCAL))
                val localDue = lastLocal == null ||
                    (sch.alertRefreshMin > 0 && ChronoUnit.MINUTES.between(lastLocal, now) >= sch.alertRefreshMin)
                if (localDue) {
                    Notifications.show(ctx, occ.scheduleId, occ.keyIso, occ.medName, occ.timeLabel())
                    container.schedules.recordAlert(occ.scheduleId, occ.keyIso, KIND_LOCAL)
                }
                if (sch.alertRefreshMin > 0) {
                    val next = (if (localDue) now else lastLocal!!).plusMinutes(sch.alertRefreshMin.toLong())
                    if (!next.isAfter(windowEnd)) candidates += next
                }
            }

            // Caregiver escalation once the dose stays untaken past caregiverAlertMin
            // — but only while it's still within the window (no late/duplicate SMS).
            if (sch.caregiverAlertMin > 0) {
                val escalateAt = scheduled.plusMinutes(sch.caregiverAlertMin.toLong())
                if (now.isBefore(escalateAt)) {
                    candidates += escalateAt
                } else if (!now.isAfter(windowEnd) && canSms &&
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

        // Refill reminders: once a day, notify for medicines running out soon
        // (skipping any already on the shopping list). Deduped via a meta date.
        val todayIso = now.toLocalDate().toString()
        if (container.medicines.getMeta(META_REFILL_SCAN) != todayIso) {
            val refills = Insights.refillForecast(snapshot, stockLookup(container), Now.local())
            val onList = container.shopping.all().map { it.medKey }.toSet()
            for (r in refills) {
                val days = ChronoUnit.DAYS.between(now.toLocalDate(), LocalDate.of(r.year, r.month, r.day))
                if (days > REFILL_SOON_DAYS) continue
                val key = Insights.medKey(r.source, r.ext, r.name)
                if (key !in onList) {
                    Notifications.showRefill(ctx, key, r.name, "%04d-%02d-%02d".format(r.year, r.month, r.day))
                }
            }
            container.medicines.setMeta(META_REFILL_SCAN, todayIso)
        }

        val nextMillis = candidates.filter { it.isAfter(now) }.min()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Never schedule in the past / too tight a loop.
        return maxOf(nextMillis, System.currentTimeMillis() + 5_000)
    }

    private suspend fun stockLookup(container: AppContainer): DosesAvailable {
        val schedules = container.schedules.list(true)
        val map = HashMap<String, Int>()
        for (sch in schedules) {
            val k = Insights.medKey(sch.medSource, sch.medExtId, sch.medName)
            if (k !in map) map[k] = container.medicines.availableDoses(sch.medSource, sch.medExtId, sch.medName)
        }
        return { s, e, n -> map[Insights.medKey(s, e, n)] ?: 0 }
    }
}
