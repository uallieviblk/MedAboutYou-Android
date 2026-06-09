package com.uallsi.medaboutyou.data.local

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
 * (cancel / cancelled flag); rows are never deleted.
 */
class ScheduleRepository(db: MedDatabase) {
    private val scheduleDao = db.scheduleDao()
    private val doseLogDao = db.doseLogDao()
    private val overrideDao = db.occOverrideDao()
    private val doseAlertDao = db.doseAlertDao()

    private fun nowIso(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

    suspend fun create(schedule: Schedule): Long {
        val now = nowIso()
        return scheduleDao.insert(schedule.copy(id = 0).toEntity(now, now))
    }

    suspend fun cancel(scheduleId: Long) = scheduleDao.cancel(scheduleId, nowIso())

    /**
     * Pause/resume a schedule. [suspended] = indefinite pause; [until] =
     * "YYYY-MM-DD" timed pause (auto-resumes on that date); both clear = active.
     */
    suspend fun setPause(scheduleId: Long, suspended: Boolean, until: String) =
        scheduleDao.setPause(scheduleId, suspended, until, nowIso())

    /**
     * Apply an edit to a schedule **from now on**, leaving the past untouched:
     * the original is ended yesterday (so its history and adherence stay intact)
     * and a new schedule with the [edited] parameters starts today. If the
     * original hasn't started yet (or is a one-shot) it is simply retired.
     */
    suspend fun editFromNow(edited: Schedule) {
        val original = scheduleDao.get(edited.id)?.toModel() ?: return
        val today = LocalDate.now()
        val start = runCatching {
            val p = original.startDate.split("-")
            LocalDate.of(p[0].toInt(), p[1].toInt(), p[2].toInt())
        }.getOrNull()

        if (original.periodUnit == com.uallsi.medaboutyou.model.PeriodUnit.ONCE ||
            start == null || !start.isBefore(today)
        ) {
            // No past recurring days to preserve → retire the whole original.
            cancel(original.id)
        } else {
            // Keep every day up to yesterday; stop the original from today on.
            updateEnd(
                original.id,
                EndMode.DATE,
                today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                original.doseCount,
            )
        }

        // The edited schedule takes effect today (one-shots keep their own date).
        val effectiveStart =
            if (edited.periodUnit == com.uallsi.medaboutyou.model.PeriodUnit.ONCE) edited.startDate
            else today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        create(edited.copy(id = 0, startDate = effectiveStart, active = true))
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
     * (unless [cancelled]) start a new schedule from this date with the new time.
     * Carries over the remaining count for count-limited schedules.
     */
    suspend fun splitFrom(
        scheduleId: Long,
        year: Int,
        month: Int,
        day: Int,
        index: Int,
        hour: Int,
        minute: Int,
        windowMinutes: Int,
        cancelled: Boolean,
    ) {
        val original = get(scheduleId) ?: return
        val thisDate = LocalDate.of(year, month, day)
        val startDate = runCatching {
            val p = original.startDate.split("-")
            LocalDate.of(p[0].toInt(), p[1].toInt(), p[2].toInt())
        }.getOrNull()

        // Special case: editing the very first dose retires the whole series.
        if (startDate != null && !thisDate.isAfter(startDate)) {
            cancel(scheduleId)
        } else {
            val dayBefore = thisDate.minusDays(1)
            updateEnd(
                scheduleId,
                EndMode.DATE,
                dayBefore.format(DateTimeFormatter.ISO_LOCAL_DATE),
                original.doseCount,
            )
        }

        if (cancelled) return

        val carriedCount =
            if (original.endMode == EndMode.COUNT) maxOf(1, original.doseCount - index) else original.doseCount
        create(
            original.copy(
                id = 0,
                startDate = thisDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                // Retime every dose-time entry to the new (hour, minute).
                times = original.times.map { it.copy(hour = hour, minute = minute) }
                    .ifEmpty { listOf(com.uallsi.medaboutyou.model.DoseTime(hour = hour, minute = minute)) },
                windowMinutes = windowMinutes,
                doseCount = carriedCount,
                active = true,
            )
        )
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

    suspend fun logDose(scheduleId: Long, iso: String, status: String) {
        doseLogDao.upsert(
            DoseLogEntity(
                scheduleId = scheduleId,
                scheduledAt = iso,
                status = status,
                loggedAt = nowIso(),
            )
        )
    }

    /** Build an immutable, synchronous snapshot for the calendar and analytics. */
    suspend fun snapshot(): ScheduleSnapshot {
        val all = scheduleDao.list(1).map { it.toModel() }
        val overrides = HashMap<Long, Map<String, OccOverrideEntity>>()
        val logs = HashMap<Long, Map<String, DoseLogEntity>>()
        for (sch in all) {
            overrides[sch.id] = overrideDao.forSchedule(sch.id).associateBy { it.scheduledAt }
            logs[sch.id] = doseLogDao.forSchedule(sch.id).associateBy { it.scheduledAt }
        }
        return ScheduleSnapshot(all, overrides, logs)
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
     * True if [sch] is paused on [target]. Past dates (< [today]) are never
     * paused (history is preserved); from today on, an indefinite suspend hides
     * everything and a timed pause hides up to (but not including) its date.
     */
    private fun isPausedOn(sch: Schedule, target: LocalDate, today: LocalDate): Boolean {
        if (target.isBefore(today)) return false
        if (sch.suspended) return true
        val until = runCatching {
            val p = sch.suspendedUntil.split("-")
            LocalDate.of(p[0].toInt(), p[1].toInt(), p[2].toInt())
        }.getOrNull() ?: return false
        return target.isBefore(until)
    }

    /** Days of [year]/[month] that have at least one (non-cancelled) occurrence. */
    fun daysWithDoses(year: Int, month: Int): Set<Int> {
        val days = sortedSetOf<Int>()
        val last = LocalDate.of(year, month, 1).lengthOfMonth()
        for (d in 1..last) {
            if (occurrencesOn(year, month, d).isNotEmpty()) days.add(d)
        }
        return days
    }
}
