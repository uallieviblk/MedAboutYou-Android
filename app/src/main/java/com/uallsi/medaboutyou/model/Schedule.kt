// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * How a schedule repeats.
 *
 * Extends the C++ `PeriodUnit` enum (HOURS/DAYS/WEEKS) with MONTHS, YEARS and a
 * one-shot ONCE — an Android-side extension so a single schedule can carry
 * several distinct dose times (see [DoseTime]). The ordinal order here is the
 * order the unit chips appear in the New-schedule dialog.
 */
enum class PeriodUnit { ONCE, HOURS, DAYS, WEEKS, MONTHS, YEARS }

/** How a schedule ends. Mirrors the C++ `EndMode` enum. */
enum class EndMode {
    /** Ongoing — no end (e.g. a chronic-therapy prescription). */
    NEVER,

    /** Ends on [Schedule.endDate] (inclusive). */
    DATE,

    /** Ends after [Schedule.doseCount] administrations. */
    COUNT,
}

/**
 * One dose-time entry within a [Schedule]. Which fields are meaningful depends
 * on the schedule's [PeriodUnit]:
 *
 * | Unit   | Fields used                          |
 * |--------|--------------------------------------|
 * | HOURS  | [hour], [minute] (anchor time on the start date) |
 * | DAYS   | [hour], [minute]                     |
 * | WEEKS  | [weekday] (1=Mon..7=Sun), [hour], [minute] |
 * | MONTHS | [dayOfMonth] (1..31, clamped), [hour], [minute] |
 * | YEARS  | [month] (1..12), [dayOfMonth], [hour], [minute] |
 * | ONCE   | [year], [month], [dayOfMonth], [hour], [minute] |
 *
 * Unused fields keep their defaults. For MONTHS/YEARS a [dayOfMonth] beyond the
 * length of a given month is clamped to that month's last existing day (e.g.
 * day 31 → 30 in September), matching the behaviour requested for the calendar.
 */
data class DoseTime(
    val year: Int = LocalDate.now().year,
    val month: Int = 1, // 1..12
    val dayOfMonth: Int = 1, // 1..31
    val weekday: Int = 1, // 1=Mon .. 7=Sun
    val hour: Int = 8, // 0..23
    val minute: Int = 0, // 0..59
)

/**
 * A prescription: one medicine taken on a recurring schedule.
 *
 * Faithful port of the C++ `Schedule` struct, extended with a list of
 * [DoseTime]s so a single schedule can fire at several times.
 */
data class Schedule(
    val id: Long = 0,
    // Denormalised medicine reference (so the calendar is self-contained).
    val medSource: Source = Source.EMA,
    val medExtId: String = "",
    val medName: String = "",
    val startDate: String = "", // "YYYY-MM-DD"
    val endMode: EndMode = EndMode.DATE,
    val endDate: String = "", // "YYYY-MM-DD" (EndMode.DATE)
    val doseCount: Int = 0, // total doses (EndMode.COUNT)
    val periodUnit: PeriodUnit = PeriodUnit.DAYS,
    val periodN: Int = 1, // repeat every n units (>= 1)
    val times: List<DoseTime> = listOf(DoseTime(hour = 8, minute = 0)),
    val windowMinutes: Int = 30,
    // Temporarily paused: kept in the list but generates no doses/reminders
    // until resumed. Distinct from [active] (cancel/retire).
    // [suspended] = indefinite pause; [suspendedUntil] = "YYYY-MM-DD" auto-resume
    // date (paused while today < it). Past dates are never hidden.
    val suspended: Boolean = false,
    val suspendedUntil: String = "",
    // Minutes after the scheduled time at which an untaken dose alerts the
    // caregiver (0 = off). Must be < [windowMinutes] (the intake deadline) so
    // the caregiver is notified before the dose can no longer be taken on time.
    val caregiverAlertMin: Int = 0,
    // How often (minutes) the caregiver alert re-sends while the dose stays
    // untaken (0 = send once). Also < [windowMinutes]; alerts stop once the
    // window closes.
    val alertRefreshMin: Int = 0,
    val notes: String = "",
    val active: Boolean = true,
) {
    /** First dose-time's hour — convenience/back-compat for single-time readers. */
    val hour: Int get() = times.firstOrNull()?.hour ?: 8

    /** First dose-time's minute — convenience/back-compat for single-time readers. */
    val minute: Int get() = times.firstOrNull()?.minute ?: 0
}

/**
 * One concrete scheduled administration.
 *
 * Faithful port of the C++ `Occurrence` struct.
 */
data class Occurrence(
    val scheduleId: Long = 0,
    val medSource: Source = Source.EMA,
    val medExtId: String = "",
    val medName: String = "",
    val year: Int = 0,
    val month: Int = 0,
    val day: Int = 0,
    val hour: Int = 0,
    val minute: Int = 0,
    val windowMinutes: Int = 0,
    val index: Int = 0, // 0-based dose number within the schedule
    val status: String = "", // "" | "taken" | "untaken"
    val takenAt: String = "", // timestamp the dose was marked (logged_at)
    val keyIso: String = "", // stable identity (original time) for logs/overrides
) {
    /** Canonical "YYYY-MM-DDTHH:MM" key for this occurrence's current time. */
    fun iso(): String = "%04d-%02d-%02dT%02d:%02d".format(year, month, day, hour, minute)

    /** "HH:MM" display time. */
    fun timeLabel(): String = "%02d:%02d".format(hour, minute)
}

/**
 * Schedule timing model — port of `models/schedule.cpp`, extended for the
 * Android multi-time model ([DoseTime]) and the MONTHS/YEARS/ONCE units.
 *
 * Pure and free of any Android dependency, so it is unit-testable.
 *
 * Semantics by unit (see [DoseTime]): HOURS anchors each entry at the **start
 * date at the entry's hour:minute** and steps every N hours from there (nothing
 * fires before its anchor); DAYS/WEEKS/MONTHS/YEARS fire on the exact-interval
 * dates at each entry's time; ONCE fires on each entry's own full date.
 * MONTHS/YEARS day-of-month is clamped to the month's last day.
 */
object ScheduleEngine {

    private fun parseDate(text: String): LocalDate? = try {
        // Accept "YYYY-M-D" the way the C++ sscanf("%d-%d-%d") does.
        val parts = text.split("-")
        if (parts.size != 3) {
            null
        } else {
            LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        }
    } catch (_: NumberFormatException) {
        null
    } catch (_: DateTimeParseException) {
        null
    } catch (_: java.time.DateTimeException) {
        null
    }

    /** Day-of-month clamped to the last existing day of [year]/[month]. */
    private fun clampDay(year: Int, month: Int, day: Int): Int =
        day.coerceIn(1, YearMonth.of(year, month).lengthOfMonth())

    /** Generate the occurrences of [schedule] that fall on the given date. */
    fun occurrencesOn(schedule: Schedule, year: Int, month: Int, day: Int): List<Occurrence> {
        val target = try {
            LocalDate.of(year, month, day)
        } catch (_: java.time.DateTimeException) {
            return emptyList()
        }
        if (schedule.times.isEmpty()) return emptyList()

        // ONCE ignores start/interval; it fires on each entry's own date.
        if (schedule.periodUnit == PeriodUnit.ONCE) {
            return onceOccurrences(schedule, target)
        }

        val start = parseDate(schedule.startDate) ?: return emptyList()
        if (target.isBefore(start)) return emptyList()

        // COUNT is the only mode that needs a stable global dose index, so we
        // generate the schedule's doses in order (capped at doseCount) and pick
        // the ones that land on the target day. (COUNT and DATE are mutually
        // exclusive end modes, so there is never an end-date to honour here.)
        if (schedule.endMode == EndMode.COUNT) {
            val limit = schedule.doseCount.coerceAtLeast(0)
            val ordered = generateOrdered(schedule, start, limit)
            val result = mutableListOf<Occurrence>()
            ordered.forEachIndexed { idx, dt ->
                if (dt.toLocalDate() == target) result.add(emit(schedule, idx, dt))
            }
            return dedupe(result)
        }

        // NEVER / DATE: compute the day's firings directly; index is the
        // within-day rank (not consumed beyond display for these modes).
        val endDay = if (schedule.endMode == EndMode.DATE) parseDate(schedule.endDate) else null
        if (endDay != null && target.isAfter(endDay)) return emptyList()

        val firings = firingsOn(schedule, start, target).sorted()
        val result = firings.mapIndexed { idx, dt -> emit(schedule, idx, dt) }
        return dedupe(result)
    }

    /** ONCE: emit any entry whose full date matches [target]. */
    private fun onceOccurrences(schedule: Schedule, target: LocalDate): List<Occurrence> {
        val firings = schedule.times
            .filter { it.year == target.year && it.month == target.monthValue && it.dayOfMonth == target.dayOfMonth }
            .map { target.atTime(LocalTime.of(it.hour.coerceIn(0, 23), it.minute.coerceIn(0, 59))) }
            .sorted()
        return dedupe(firings.mapIndexed { idx, dt -> emit(schedule, idx, dt) })
    }

    /** Drop occurrences that share a key (e.g. two clamped MONTHS days collide). */
    private fun dedupe(list: List<Occurrence>): List<Occurrence> {
        if (list.size <= 1) return list
        val seen = HashSet<String>()
        return list.filter { seen.add(it.keyIso) }
    }

    private fun emit(schedule: Schedule, idx: Int, whenTime: LocalDateTime): Occurrence =
        Occurrence(
            scheduleId = schedule.id,
            medSource = schedule.medSource,
            medExtId = schedule.medExtId,
            medName = schedule.medName,
            windowMinutes = schedule.windowMinutes,
            index = idx,
            year = whenTime.year,
            month = whenTime.monthValue,
            day = whenTime.dayOfMonth,
            hour = whenTime.hour,
            minute = whenTime.minute,
        ).let { it.copy(keyIso = it.iso()) }

    /** The dose datetimes of [schedule] that fall on [target] (ignores end). */
    private fun firingsOn(schedule: Schedule, start: LocalDate, target: LocalDate): List<LocalDateTime> {
        val n = schedule.periodN.coerceAtLeast(1)
        val out = mutableListOf<LocalDateTime>()
        when (schedule.periodUnit) {
            PeriodUnit.ONCE -> {} // handled by onceOccurrences

            PeriodUnit.HOURS -> {
                // Each entry is an independent anchor: the start date at the
                // entry's time-of-day, then every N hours. Nothing fires before
                // its anchor, so an hourly schedule never back-fills the start
                // day before its start time.
                val dayBegin = target.atStartOfDay()
                val dayEnd = target.plusDays(1).atStartOfDay()
                val stepMin = n.toLong() * 60
                for (t in schedule.times) {
                    val anchor = start.atTime(LocalTime.of(t.hour.coerceIn(0, 23), t.minute.coerceIn(0, 59)))
                    val gapMin = ChronoUnit.MINUTES.between(anchor, dayBegin)
                    var k = if (gapMin <= 0) 0L else (gapMin + stepMin - 1) / stepMin
                    while (true) {
                        val dt = anchor.plusHours(k * n)
                        if (!dt.isBefore(dayEnd)) break
                        if (!dt.isBefore(dayBegin)) out.add(dt)
                        k++
                    }
                }
            }

            PeriodUnit.DAYS -> {
                val diff = ChronoUnit.DAYS.between(start, target)
                if (diff >= 0 && diff % n == 0L) addDayTimes(schedule, target, out)
            }

            PeriodUnit.WEEKS -> {
                val stepDays = 7L * n
                val diff = ChronoUnit.DAYS.between(start, target)
                for (t in schedule.times) {
                    val offset = ((t.weekday.coerceIn(1, 7) - start.dayOfWeek.value + 7) % 7).toLong()
                    val rem = diff - offset
                    if (rem >= 0 && rem % stepDays == 0L) {
                        out.add(target.atTime(LocalTime.of(t.hour.coerceIn(0, 23), t.minute.coerceIn(0, 59))))
                    }
                }
            }

            PeriodUnit.MONTHS -> {
                val mi = ChronoUnit.MONTHS.between(start.withDayOfMonth(1), target.withDayOfMonth(1))
                if (mi >= 0 && mi % n == 0L) {
                    for (t in schedule.times) {
                        val effDay = clampDay(target.year, target.monthValue, t.dayOfMonth)
                        if (target.dayOfMonth == effDay) {
                            out.add(target.atTime(LocalTime.of(t.hour.coerceIn(0, 23), t.minute.coerceIn(0, 59))))
                        }
                    }
                }
            }

            PeriodUnit.YEARS -> {
                val yi = (target.year - start.year).toLong()
                if (yi >= 0 && yi % n == 0L) {
                    for (t in schedule.times) {
                        if (target.monthValue != t.month.coerceIn(1, 12)) continue
                        val effDay = clampDay(target.year, target.monthValue, t.dayOfMonth)
                        if (target.dayOfMonth == effDay) {
                            out.add(target.atTime(LocalTime.of(t.hour.coerceIn(0, 23), t.minute.coerceIn(0, 59))))
                        }
                    }
                }
            }
        }
        return out
    }

    private fun addDayTimes(schedule: Schedule, target: LocalDate, out: MutableList<LocalDateTime>) {
        for (t in schedule.times) {
            out.add(target.atTime(LocalTime.of(t.hour.coerceIn(0, 23), t.minute.coerceIn(0, 59))))
        }
    }

    /**
     * The first [limit] dose datetimes of [schedule] in chronological order
     * (used only for COUNT end-mode, so the cap keeps it bounded). Respects the
     * start date; ONCE is handled separately.
     */
    private fun generateOrdered(schedule: Schedule, start: LocalDate, limit: Int): List<LocalDateTime> {
        if (limit <= 0) return emptyList()
        val n = schedule.periodN.coerceAtLeast(1)
        val out = mutableListOf<LocalDateTime>()
        // Hard backstop so an all-filtered first period can never spin forever.
        val maxPeriods = limit + 24

        when (schedule.periodUnit) {
            PeriodUnit.ONCE -> {
                schedule.times
                    .map {
                        LocalDate.of(
                            it.year,
                            it.month.coerceIn(1, 12),
                            clampDay(it.year, it.month.coerceIn(1, 12), it.dayOfMonth)
                        )
                            .atTime(LocalTime.of(it.hour.coerceIn(0, 23), it.minute.coerceIn(0, 59)))
                    }
                    .sorted()
                    .distinct() // duplicate entries are one dose, and consume one count
                    .take(limit)
                    .forEach { out.add(it) }
            }

            PeriodUnit.HOURS -> {
                // Each entry anchors at the start date's time-of-day; step every
                // n hours. The per-anchor series are merged smallest-first (a
                // k-way merge): with anchors further apart than the period, a
                // plain "one of each per round, then sort" would let a late
                // anchor's early doses displace the true chronological first
                // [limit] doses.
                val anchors = schedule.times
                    .map { start.atTime(LocalTime.of(it.hour.coerceIn(0, 23), it.minute.coerceIn(0, 59))) }
                    .distinct()
                val ks = LongArray(anchors.size)
                val guard = limit.toLong() * anchors.size + 1
                var produced = 0L
                while (out.size < limit && produced < guard) {
                    var best = 0
                    var bestDt = anchors[0].plusHours(ks[0] * n)
                    for (i in 1 until anchors.size) {
                        val dt = anchors[i].plusHours(ks[i] * n)
                        if (dt.isBefore(bestDt)) {
                            best = i;
                            bestDt = dt
                        }
                    }
                    // The merge emits in order, so cross-anchor duplicates are adjacent.
                    if (out.isEmpty() || bestDt != out.last()) out.add(bestDt)
                    ks[best]++
                    produced++
                }
            }

            PeriodUnit.DAYS -> {
                var k = 0L
                while (out.size < limit && k < maxPeriods.toLong()) {
                    val date = start.plusDays(k * n)
                    schedule.times
                        .map { date.atTime(LocalTime.of(it.hour.coerceIn(0, 23), it.minute.coerceIn(0, 59))) }
                        .sorted()
                        .distinct() // duplicate entries are one dose, and consume one count
                        .forEach { if (out.size < limit) out.add(it) }
                    k++
                }
            }

            PeriodUnit.WEEKS -> {
                var k = 0L
                while (out.size < limit && k < maxPeriods.toLong()) {
                    val period = mutableListOf<LocalDateTime>()
                    for (t in schedule.times) {
                        val offset = ((t.weekday.coerceIn(1, 7) - start.dayOfWeek.value + 7) % 7).toLong()
                        val date = start.plusDays(7L * n * k + offset)
                        period.add(date.atTime(LocalTime.of(t.hour.coerceIn(0, 23), t.minute.coerceIn(0, 59))))
                    }
                    // distinct(): duplicate entries are one dose, and consume one count
                    period.sorted().distinct().forEach { if (out.size < limit) out.add(it) }
                    k++
                }
            }

            PeriodUnit.MONTHS -> {
                var k = 0L
                while (out.size < limit && k < maxPeriods.toLong()) {
                    val base = start.withDayOfMonth(1).plusMonths(k * n)
                    val period = mutableListOf<LocalDateTime>()
                    for (t in schedule.times) {
                        val effDay = clampDay(base.year, base.monthValue, t.dayOfMonth)
                        val date = base.withDayOfMonth(effDay)
                        if (!date.isBefore(start)) {
                            period.add(date.atTime(LocalTime.of(t.hour.coerceIn(0, 23), t.minute.coerceIn(0, 59))))
                        }
                    }
                    // distinct(): duplicate entries are one dose, and consume one count
                    period.sorted().distinct().forEach { if (out.size < limit) out.add(it) }
                    k++
                }
            }

            PeriodUnit.YEARS -> {
                var k = 0L
                while (out.size < limit && k < maxPeriods.toLong()) {
                    val year = start.year + (k * n).toInt()
                    val period = mutableListOf<LocalDateTime>()
                    for (t in schedule.times) {
                        val mo = t.month.coerceIn(1, 12)
                        val effDay = clampDay(year, mo, t.dayOfMonth)
                        val date = LocalDate.of(year, mo, effDay)
                        if (!date.isBefore(start)) {
                            period.add(date.atTime(LocalTime.of(t.hour.coerceIn(0, 23), t.minute.coerceIn(0, 59))))
                        }
                    }
                    // distinct(): duplicate entries are one dose, and consume one count
                    period.sorted().distinct().forEach { if (out.size < limit) out.add(it) }
                    k++
                }
            }
        }
        return out.take(limit)
    }

    /**
     * The earliest dose date-time this schedule would ever generate, or null if
     * it produces none (no dose times, or an unparseable start date for a
     * recurring unit).
     *
     * Used to enforce that a newly-created schedule's first dose lies in the
     * future. For [PeriodUnit.HOURS] the series anchors at the start date's
     * time-of-day (each entry's hour:minute), so the first occurrence is that
     * start date-time — letting an hourly schedule begin at a chosen moment and
     * step forward from there, with no back-filled doses before it.
     */
    fun firstOccurrence(schedule: Schedule): LocalDateTime? {
        if (schedule.times.isEmpty()) return null
        if (schedule.periodUnit == PeriodUnit.ONCE) {
            return generateOrdered(schedule, LocalDate.MIN, 1).firstOrNull()
        }
        val start = parseDate(schedule.startDate) ?: return null
        return generateOrdered(schedule, start, 1).firstOrNull()
    }

    /**
     * Number of doses of a COUNT-ended [schedule] that fall strictly before
     * [date] (capped at [Schedule.doseCount]). Used by edit-from-now to freeze
     * the original schedule at exactly the doses that already happened.
     */
    fun countOccurrencesBefore(schedule: Schedule, date: LocalDate): Int {
        if (schedule.times.isEmpty()) return 0
        val start = parseDate(schedule.startDate) ?: return 0
        val limit = schedule.doseCount.coerceAtLeast(0)
        return generateOrdered(schedule, start, limit).count { it.toLocalDate().isBefore(date) }
    }

    /**
     * The date-time of the last dose a COUNT-ended schedule will ever generate,
     * or null when it isn't COUNT-ended or generates nothing. Lets the Schedules
     * list hide finished courses.
     */
    fun lastCountOccurrence(schedule: Schedule): LocalDateTime? {
        if (schedule.endMode != EndMode.COUNT || schedule.times.isEmpty()) return null
        val start = parseDate(schedule.startDate) ?: return null
        return generateOrdered(schedule, start, schedule.doseCount.coerceAtLeast(0)).lastOrNull()
    }

    /** True if the given local date/time is at or before now. */
    fun isPastDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): Boolean {
        val whenTime = LocalDateTime.of(year, month, day, hour, minute)
        return !whenTime.isAfter(LocalDateTime.now())
    }

    /**
     * True if a dose at the given local date/time may be checked off now — i.e.
     * the current time has reached the start of its intake window
     * (scheduled time − [windowMinutes]). Lets a dose be marked taken from the
     * opening of its window, not only once the exact scheduled minute is reached.
     */
    fun isWithinTakeWindow(year: Int, month: Int, day: Int, hour: Int, minute: Int, windowMinutes: Int): Boolean {
        val opensAt = LocalDateTime.of(
            year,
            month,
            day,
            hour,
            minute
        ).minusMinutes(windowMinutes.coerceAtLeast(0).toLong())
        return !opensAt.isAfter(LocalDateTime.now())
    }

    /**
     * True if **now** falls inside a dose's intake window — i.e. within
     * `[scheduled − windowMinutes, scheduled + windowMinutes]`. Marking a dose
     * taken while this is false means it's being taken outside its scheduled
     * window (in practice late, since the checkbox is locked before the window
     * opens), which the take-confirmation surfaces as a warning.
     */
    fun isWithinScheduledWindow(year: Int, month: Int, day: Int, hour: Int, minute: Int, windowMinutes: Int): Boolean {
        val scheduled = LocalDateTime.of(year, month, day, hour, minute)
        val w = windowMinutes.coerceAtLeast(0).toLong()
        val now = LocalDateTime.now()
        return !now.isBefore(scheduled.minusMinutes(w)) && !now.isAfter(scheduled.plusMinutes(w))
    }
}
