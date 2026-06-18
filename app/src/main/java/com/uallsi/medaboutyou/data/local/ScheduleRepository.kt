// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.data.local

import androidx.room.withTransaction
import com.uallsi.medaboutyou.domain.ScheduleQuery
import com.uallsi.medaboutyou.model.EndMode
import com.uallsi.medaboutyou.model.Occurrence
import com.uallsi.medaboutyou.model.Schedule
import com.uallsi.medaboutyou.model.ScheduleEngine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Persistent storage for schedules, the dose log and single-dose overrides —
 * the Android port of the C++ `ScheduleRepository`. Removals are soft
 * (cancel sets `active = 0`); rows are never deleted.
 */
class ScheduleRepository(private val db: MedDatabase) {
    private val scheduleDao = db.scheduleDao()
    private val doseLogDao = db.doseLogDao()
    private val overrideDao = db.occOverrideDao()
    private val doseAlertDao = db.doseAlertDao()
    private val pausePeriodDao = db.pausePeriodDao()

    private fun nowIso(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

    private fun parseDate(text: String): LocalDate? = runCatching {
        val p = text.split("-")
        LocalDate.of(p[0].toInt(), p[1].toInt(), p[2].toInt())
    }.getOrNull()

    /**
     * Copy the original schedule's dose-log and override rows whose occurrence
     * key falls at/after [from] onto the schedule [toId]. Used by the split/edit
     * flows so a same-day edit can't erase a dose already taken this morning:
     * entries whose time didn't change keep their history under the new row.
     */
    private suspend fun carryOverHistory(fromId: Long, toId: Long, from: LocalDate?) {
        val fromKey = (from?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: "0000-01-01") + "T00:00"
        doseLogDao.forScheduleFrom(fromId, fromKey).forEach {
            doseLogDao.upsert(it.copy(id = 0, scheduleId = toId))
        }
        overrideDao.forScheduleFrom(fromId, fromKey).forEach {
            overrideDao.upsert(it.copy(id = 0, scheduleId = toId))
        }
    }

    suspend fun create(schedule: Schedule): Long {
        val now = nowIso()
        return scheduleDao.insert(schedule.copy(id = 0).toEntity(now, now))
    }

    suspend fun cancel(scheduleId: Long) = scheduleDao.cancel(scheduleId, nowIso())

    /**
     * Pause/resume a schedule. [suspended] = indefinite pause; [until] =
     * "YYYY-MM-DD" timed pause (auto-resumes on that date); both clear = active.
     *
     * Every pause is also recorded as an append-only [PausePeriodEntity] window
     * so the paused days stay hidden once they slip into the past — otherwise a
     * vacation pause would retroactively turn into a run of "missed" doses.
     */
    suspend fun setPause(scheduleId: Long, suspended: Boolean, until: String) {
        db.withTransaction {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            // Close any open/future window first (resume, or re-pause with new
            // terms); a same-day pause+resume leaves no window behind.
            pausePeriodDao.closeAt(scheduleId, today)
            pausePeriodDao.pruneEmpty(scheduleId)
            if (suspended || until > today) {
                pausePeriodDao.insert(
                    PausePeriodEntity(
                        scheduleId = scheduleId,
                        startDate = today,
                        endDate = if (suspended) "" else until,
                    )
                )
            }
            scheduleDao.setPause(scheduleId, suspended, until, nowIso())
        }
    }

    /**
     * Apply an edit to a schedule **from now on**, leaving the past untouched:
     * the original is frozen at its already-elapsed doses (so its history and
     * adherence stay intact) and a new schedule with the [edited] parameters
     * starts today. A one-shot is retired whole; a schedule that hasn't started
     * yet is retired and the replacement keeps its planned start date.
     */
    suspend fun editFromNow(edited: Schedule) {
        db.withTransaction {
            val original = scheduleDao.get(edited.id)?.toModel() ?: return@withTransaction
            val today = LocalDate.now()
            val todayIso = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val start = parseDate(original.startDate)
            val once = original.periodUnit == com.uallsi.medaboutyou.model.PeriodUnit.ONCE
            val started = !once && start != null && start.isBefore(today)

            if (!started) {
                // No past recurring days to preserve → retire the whole original.
                cancel(original.id)
            } else if (original.endMode == EndMode.COUNT) {
                // Freeze the original at exactly the doses that already happened,
                // keeping COUNT semantics — converting to a DATE end would make
                // every interval day up to yesterday regenerate, fabricating
                // "missed" doses for a course that may have finished long ago.
                val consumed = ScheduleEngine.countOccurrencesBefore(original, today)
                if (consumed <= 0) {
                    cancel(original.id)
                } else {
                    updateEnd(original.id, EndMode.COUNT, original.endDate, consumed)
                }
            } else {
                // Keep every day up to yesterday — but never *extend* a schedule
                // whose DATE end has already passed.
                val yesterday = today.minusDays(1)
                val existingEnd = if (original.endMode == EndMode.DATE) parseDate(original.endDate) else null
                val newEnd = if (existingEnd != null && existingEnd.isBefore(yesterday)) existingEnd else yesterday
                updateEnd(
                    original.id,
                    EndMode.DATE,
                    newEnd.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    original.doseCount
                )
            }

            // The edited schedule takes effect today; one-shots keep their own
            // dates and a not-yet-started schedule keeps its planned start.
            val effectiveStart = when {
                once || edited.periodUnit == com.uallsi.medaboutyou.model.PeriodUnit.ONCE -> edited.startDate
                start != null && start.isAfter(today) -> original.startDate
                else -> todayIso
            }
            // If the user left a COUNT course's total untouched, carry over the
            // *remaining* doses instead of restarting the full prescription
            // (an already-finished course carries 0 — nothing left to take).
            val doseCount = if (
                started && original.endMode == EndMode.COUNT && edited.endMode == EndMode.COUNT &&
                edited.doseCount == original.doseCount
            ) {
                maxOf(0, original.doseCount - ScheduleEngine.countOccurrencesBefore(original, today))
            } else {
                edited.doseCount
            }

            val newId = create(edited.copy(id = 0, startDate = effectiveStart, doseCount = doseCount, active = true))
            // A same-day edit must not erase doses already logged today against
            // the original (one-shots: keep all of their history).
            carryOverHistory(original.id, newId, if (once) null else parseDate(effectiveStart) ?: today)
        }
    }

    suspend fun updateEnd(scheduleId: Long, endMode: EndMode, endDate: String, doseCount: Int) =
        scheduleDao.updateEnd(scheduleId, endMode.name.lowercase(), endDate, doseCount, nowIso())

    suspend fun get(scheduleId: Long): Schedule? = scheduleDao.get(scheduleId)?.toModel()

    suspend fun list(includeCancelled: Boolean): List<Schedule> =
        scheduleDao.list(if (includeCancelled) 1 else 0).map { it.toModel() }

    /** "This dose only": store/replace an override keyed by the original time. */
    suspend fun editSingle(
        scheduleId: Long,
        keyIso: String,
        hour: Int,
        minute: Int,
        windowMinutes: Int,
        cancelled: Boolean,
    ) {
        overrideDao.upsert(
            OccOverrideEntity(
                scheduleId = scheduleId,
                scheduledAt = keyIso,
                hour = hour,
                minute = minute,
                windowMinutes = windowMinutes,
                cancelled = cancelled,
                updatedAt = nowIso(),
            )
        )
    }

    /**
     * "This and following": truncate the original to end before this date, then
     * (unless [cancelled]) start a new schedule from this date with the edited
     * entry — identified by its original [fromHour]:[fromMinute] — moved to the
     * new (hour, minute). Other dose-time entries keep their times, so editing
     * the evening dose of a morning+evening therapy doesn't collapse it to a
     * single daily dose. Carries over the remaining count for count-limited
     * schedules and any history already logged on/after the split date.
     */
    suspend fun splitFrom(
        scheduleId: Long,
        year: Int,
        month: Int,
        day: Int,
        fromHour: Int,
        fromMinute: Int,
        hour: Int,
        minute: Int,
        windowMinutes: Int,
        cancelled: Boolean,
    ) {
        db.withTransaction {
            val original = get(scheduleId) ?: return@withTransaction
            val thisDate = LocalDate.of(year, month, day)
            val startDate = parseDate(original.startDate)
            // Doses of a COUNT course that happened strictly before the split day.
            val consumed =
                if (original.endMode == EndMode.COUNT) {
                    ScheduleEngine.countOccurrencesBefore(original, thisDate)
                } else {
                    0
                }

            // Special case: editing the very first dose retires the whole series.
            if (startDate != null && !thisDate.isAfter(startDate)) {
                cancel(scheduleId)
            } else if (original.endMode == EndMode.COUNT) {
                // Freeze at the doses before the split (see editFromNow).
                if (consumed <= 0) {
                    cancel(scheduleId)
                } else {
                    updateEnd(scheduleId, EndMode.COUNT, original.endDate, consumed)
                }
            } else {
                val dayBefore = thisDate.minusDays(1)
                val existingEnd = if (original.endMode == EndMode.DATE) parseDate(original.endDate) else null
                val newEnd = if (existingEnd != null && existingEnd.isBefore(dayBefore)) existingEnd else dayBefore
                updateEnd(scheduleId, EndMode.DATE, newEnd.format(DateTimeFormatter.ISO_LOCAL_DATE), original.doseCount)
            }

            if (cancelled) return@withTransaction

            // The replacement regenerates the split day from its first entry, so
            // its budget is everything the original didn't already consume.
            val carriedCount =
                if (original.endMode == EndMode.COUNT) {
                    maxOf(1, original.doseCount - consumed)
                } else {
                    original.doseCount
                }
            val unit = original.periodUnit
            fun matches(t: com.uallsi.medaboutyou.model.DoseTime): Boolean =
                t.hour == fromHour && t.minute == fromMinute &&
                    (unit != com.uallsi.medaboutyou.model.PeriodUnit.WEEKS || t.weekday == thisDate.dayOfWeek.value)
            val newTimes = when {
                // HOURS entries are anchors, not literal times — re-anchor the
                // whole series at the new time on the split date.
                unit == com.uallsi.medaboutyou.model.PeriodUnit.HOURS ->
                    original.times.map { it.copy(hour = hour, minute = minute) }.distinct()
                original.times.any(::matches) ->
                    original.times.map { if (matches(it)) it.copy(hour = hour, minute = minute) else it }
                else -> original.times.map { it.copy(hour = hour, minute = minute) }
            }.ifEmpty { listOf(com.uallsi.medaboutyou.model.DoseTime(hour = hour, minute = minute)) }

            val newId = create(
                original.copy(
                    id = 0,
                    startDate = thisDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    times = newTimes,
                    windowMinutes = windowMinutes,
                    doseCount = carriedCount,
                    active = true,
                )
            )
            // A mid-day split must not erase the day's earlier (possibly taken)
            // doses: entries that kept their time keep their history.
            carryOverHistory(scheduleId, newId, thisDate)
        }
    }

    /** Timestamp ("yyyy-MM-dd HH:mm:ss") of the last [kind] alert, or null. */
    suspend fun alertLastSent(scheduleId: Long, keyIso: String, kind: String): String? =
        doseAlertDao.lastSentAt(scheduleId, keyIso, kind)

    /** Record (or refresh) that a [kind] alert just fired for this occurrence. */
    suspend fun recordAlert(scheduleId: Long, keyIso: String, kind: String) {
        doseAlertDao.upsert(
            DoseAlertEntity(scheduleId = scheduleId, scheduledAt = keyIso, kind = kind, sentAt = nowIso()),
        )
    }

    /**
     * Upserts the dose log and returns the stock delta implied by the **status
     * transition**: −1 when the dose becomes taken, +1 when a take is undone,
     * 0 otherwise (re-logging the same status, or skipping a never-taken dose,
     * must not move stock). Runs in a transaction so concurrent calls — e.g. a
     * double-tap, or in-app take racing a notification action — serialize and
     * only one of them reports the transition.
     */
    suspend fun logDose(scheduleId: Long, iso: String, status: String): Int =
        db.withTransaction {
            val prior = doseLogDao.statusFor(scheduleId, iso)
            doseLogDao.upsert(
                DoseLogEntity(
                    scheduleId = scheduleId,
                    scheduledAt = iso,
                    status = status,
                    loggedAt = nowIso(),
                )
            )
            when {
                status == "taken" && prior != "taken" -> -1
                status != "taken" && prior == "taken" -> +1
                else -> 0
            }
        }

    /** Build an immutable, synchronous snapshot for the calendar and analytics. */
    suspend fun snapshot(): ScheduleSnapshot {
        // Three whole-table reads grouped in memory — not one pair of queries
        // per schedule, which scales badly with an append-only schedules table.
        val all = scheduleDao.list(1).map { it.toModel() }
        val overrides = overrideDao.all().groupBy { it.scheduleId }
            .mapValues { (_, rows) -> rows.associateBy { it.scheduledAt } }
        val logs = doseLogDao.all().groupBy { it.scheduleId }
            .mapValues { (_, rows) -> rows.associateBy { it.scheduledAt } }
        val pauses = pausePeriodDao.all().groupBy { it.scheduleId }
        return ScheduleSnapshot(all, overrides, logs, pauses)
    }
}

/**
 * In-memory, synchronous view of all schedules with their overrides and logs.
 * Implements [ScheduleQuery] so the pure analytics can run tight loops without
 * suspending on the database, exactly like the C++ `ScheduleRepository`.
 */
class ScheduleSnapshot(
    private val all: List<Schedule>,
    private val overrides: Map<Long, Map<String, OccOverrideEntity>>,
    private val logs: Map<Long, Map<String, DoseLogEntity>>,
    private val pauses: Map<Long, List<PausePeriodEntity>> = emptyMap(),
) : ScheduleQuery {

    override fun list(includeCancelled: Boolean): List<Schedule> =
        if (includeCancelled) all else all.filter { it.active }

    override fun occurrencesOn(year: Int, month: Int, day: Int): List<Occurrence> {
        val result = mutableListOf<Occurrence>()
        val target = runCatching { LocalDate.of(year, month, day) }.getOrNull()
        val today = LocalDate.now()
        for (sch in all) {
            if (!sch.active) continue
            if (target != null && isPausedOn(sch, target, today)) continue
            val ov = overrides[sch.id].orEmpty()
            val log = logs[sch.id].orEmpty()
            for (occ in ScheduleEngine.occurrencesOn(sch, year, month, day)) {
                var current = occ
                ov[occ.keyIso]?.let { o ->
                    if (o.cancelled) return@let // handled below by skipping
                    current = current.copy(
                        hour = o.hour,
                        minute = o.minute,
                        windowMinutes = o.windowMinutes,
                    )
                }
                if (ov[occ.keyIso]?.cancelled == true) continue
                log[occ.keyIso]?.let { l ->
                    current = current.copy(status = l.status, takenAt = l.loggedAt)
                }
                result.add(current)
            }
        }
        result.sortWith(compareBy({ it.hour }, { it.minute }))
        return result
    }

    /**
     * True if [sch] is paused on [target]. Past dates (< [today]) consult the
     * recorded pause windows, so a pause never retroactively turns into a run
     * of "missed" doses once its days slip into the past. From today on, the
     * live columns rule: an indefinite suspend hides everything and a timed
     * pause hides up to (but not including) its auto-resume date.
     */
    private fun isPausedOn(sch: Schedule, target: LocalDate, today: LocalDate): Boolean {
        if (target.isBefore(today)) {
            val t = target.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            return pauses[sch.id].orEmpty().any { w ->
                w.startDate <= t && (w.endDate.isEmpty() || t < w.endDate)
            }
        }
        if (sch.suspended) return true
        val until = runCatching {
            val p = sch.suspendedUntil.split("-")
            LocalDate.of(p[0].toInt(), p[1].toInt(), p[2].toInt())
        }.getOrNull() ?: return false
        return target.isBefore(until)
    }
}
