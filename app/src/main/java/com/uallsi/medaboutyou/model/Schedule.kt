package com.uallsi.medaboutyou.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

/** How a schedule repeats. Extends the C++ `PeriodUnit` enum with MONTHS. */
enum class PeriodUnit { HOURS, DAYS, WEEKS, MONTHS }

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
 * A prescription: one medicine taken on a recurring schedule.
 *
 * Faithful port of the C++ `Schedule` struct.
 */
data class Schedule(
    val id: Long = 0,
    // Denormalised medicine reference (so the calendar is self-contained).
    val medSource: Source = Source.EMA,
    val medExtId: String = "",
    val medName: String = "",
    val startDate: String = "",               // "YYYY-MM-DD"
    val endMode: EndMode = EndMode.DATE,
    val endDate: String = "",                  // "YYYY-MM-DD" (EndMode.DATE)
    val doseCount: Int = 0,                    // total doses (EndMode.COUNT)
    val periodUnit: PeriodUnit = PeriodUnit.DAYS,
    val periodN: Int = 1,                      // repeat every n units (>= 1)
    val hour: Int = 8,
    val minute: Int = 0,
    val windowMinutes: Int = 30,
    val notes: String = "",
    val active: Boolean = true,
)

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
    val index: Int = 0,           // 0-based dose number within the schedule
    val status: String = "",      // "" | "taken" | "untaken"
    val takenAt: String = "",     // timestamp the dose was marked (logged_at)
    val keyIso: String = "",      // stable identity (original time) for logs/overrides
) {
    /** Canonical "YYYY-MM-DDTHH:MM" key for this occurrence's current time. */
    fun iso(): String = "%04d-%02d-%02dT%02d:%02d".format(year, month, day, hour, minute)

    /** "HH:MM" display time. */
    fun timeLabel(): String = "%02d:%02d".format(hour, minute)
}

/**
 * Schedule timing model — faithful port of `models/schedule.cpp`.
 *
 * Pure and free of any Android dependency, so it is unit-testable.
 */
object ScheduleEngine {

    private fun parseDate(text: String): LocalDate? = try {
        // Accept "YYYY-M-D" the way the C++ sscanf("%d-%d-%d") does.
        val parts = text.split("-")
        if (parts.size != 3) null
        else LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    } catch (_: NumberFormatException) {
        null
    } catch (_: DateTimeParseException) {
        null
    } catch (_: java.time.DateTimeException) {
        null
    }

    /** Generate the occurrences of [schedule] that fall on the given date. */
    fun occurrencesOn(schedule: Schedule, year: Int, month: Int, day: Int): List<Occurrence> {
        val result = mutableListOf<Occurrence>()

        val startDay = parseDate(schedule.startDate) ?: return result
        val targetDay = LocalDate.of(year, month, day)
        if (targetDay.isBefore(startDay)) return result

        var endDay: LocalDate? = null
        if (schedule.endMode == EndMode.DATE) {
            endDay = parseDate(schedule.endDate)
        }

        fun emit(idx: Int, whenTime: LocalDateTime) {
            result.add(
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
            )
        }

        if (schedule.periodUnit == PeriodUnit.HOURS) {
            val base = startDay.atTime(LocalTime.of(schedule.hour, schedule.minute))
            val stepHours = (if (schedule.periodN > 0) schedule.periodN else 1).toLong()
            val dayBegin = targetDay.atStartOfDay()
            val dayEnd = targetDay.plusDays(1).atStartOfDay()

            var idx = 0
            while (true) {
                val whenTime = base.plusHours(idx * stepHours)
                if (!whenTime.isBefore(dayEnd)) break
                if (schedule.endMode == EndMode.COUNT && idx >= schedule.doseCount) break
                if (endDay != null && whenTime.toLocalDate().isAfter(endDay)) break
                if (!whenTime.isBefore(dayBegin)) emit(idx, whenTime)
                idx++
            }
            return result
        }

        // Months: at most one dose per month, on the same day-of-month as the
        // start date, every period_n months. Months that lack that day are skipped.
        if (schedule.periodUnit == PeriodUnit.MONTHS) {
            val step = if (schedule.periodN > 0) schedule.periodN else 1
            if (targetDay.dayOfMonth != startDay.dayOfMonth) return result
            val months = java.time.temporal.ChronoUnit.MONTHS.between(
                startDay.withDayOfMonth(1), targetDay.withDayOfMonth(1),
            )
            if (months < 0 || months % step != 0L) return result
            val idx = (months / step).toInt()
            if (schedule.endMode == EndMode.COUNT && idx >= schedule.doseCount) return result
            if (endDay != null && targetDay.isAfter(endDay)) return result
            emit(idx, targetDay.atTime(LocalTime.of(schedule.hour, schedule.minute)))
            return result
        }

        // Days / Weeks: at most one dose on the target day.
        val unitDays = if (schedule.periodUnit == PeriodUnit.WEEKS) 7 else 1
        val stepDays = unitDays * (if (schedule.periodN > 0) schedule.periodN else 1)
        val diff = java.time.temporal.ChronoUnit.DAYS.between(startDay, targetDay)
        if (diff % stepDays != 0L) return result
        val idx = (diff / stepDays).toInt()
        if (schedule.endMode == EndMode.COUNT && idx >= schedule.doseCount) return result
        if (endDay != null && targetDay.isAfter(endDay)) return result
        emit(idx, targetDay.atTime(LocalTime.of(schedule.hour, schedule.minute)))
        return result
    }

    /** True if the given local date/time is at or before now. */
    fun isPastDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): Boolean {
        val whenTime = LocalDateTime.of(year, month, day, hour, minute)
        return !whenTime.isAfter(LocalDateTime.now())
    }
}
