// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import com.uallsi.medaboutyou.model.DoseTime
import com.uallsi.medaboutyou.model.EndMode
import com.uallsi.medaboutyou.model.PeriodUnit
import com.uallsi.medaboutyou.model.Schedule
import com.uallsi.medaboutyou.model.ScheduleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Exhaustive timing verification of [ScheduleEngine.occurrencesOn] for every
 * [PeriodUnit] and end mode — including interval stepping (periodN > 1),
 * multi-time, multi-weekday, month-end clamping and the leap-day edge.
 */
class ScheduleEngineTest {

    private fun sched(
        start: String,
        unit: PeriodUnit,
        n: Int = 1,
        times: List<DoseTime>,
        endMode: EndMode = EndMode.NEVER,
        endDate: String = "",
        doseCount: Int = 0,
    ) = Schedule(
        medName = "Test", startDate = start, endMode = endMode, endDate = endDate,
        doseCount = doseCount, periodUnit = unit, periodN = n, times = times,
    )

    private fun hoursOn(s: Schedule, y: Int, m: Int, d: Int) =
        ScheduleEngine.occurrencesOn(s, y, m, d).map { it.hour }

    private fun hmOn(s: Schedule, y: Int, m: Int, d: Int) =
        ScheduleEngine.occurrencesOn(s, y, m, d).map { it.hour to it.minute }

    // ---- ONCE ----
    @Test fun once_fires_each_entry_on_its_own_date_only() {
        val s = sched(
            "2026-06-01", PeriodUnit.ONCE,
            times = listOf(
                DoseTime(year = 2026, month = 6, dayOfMonth = 10, hour = 9),
                DoseTime(year = 2026, month = 6, dayOfMonth = 20, hour = 21, minute = 30),
            ),
        )
        assertEquals(listOf(9 to 0), hmOn(s, 2026, 6, 10))
        assertEquals(listOf(21 to 30), hmOn(s, 2026, 6, 20))
        assertTrue(ScheduleEngine.occurrencesOn(s, 2026, 6, 15).isEmpty())
    }

    // ---- HOURS ----
    // The series anchors at the start date's time-of-day (each entry's
    // hour:minute), then steps every N hours — nothing fires before its anchor.
    @Test fun hours_step_from_anchor_and_carry_across_days() {
        // Every 5h from 2026-06-01 00:00: day1 = 0,5,10,15,20; day2 carries to 1,6,11,16,21.
        val s = sched("2026-06-01", PeriodUnit.HOURS, n = 5, times = listOf(DoseTime(hour = 0, minute = 0)))
        assertEquals(listOf(0, 5, 10, 15, 20), hoursOn(s, 2026, 6, 1))
        assertEquals(listOf(1, 6, 11, 16, 21), hoursOn(s, 2026, 6, 2))
        assertTrue(ScheduleEngine.occurrencesOn(s, 2026, 5, 31).isEmpty()) // before start
    }

    @Test fun hours_anchor_at_start_time_with_no_backfill_before_it() {
        // Every 6h from 2026-06-01 08:30 -> 08:30, 14:30, 20:30 that day (nothing
        // earlier), then 02:30, 08:30, 14:30, 20:30 the next day.
        val s = sched("2026-06-01", PeriodUnit.HOURS, n = 6, times = listOf(DoseTime(hour = 8, minute = 30)))
        assertEquals(listOf(8 to 30, 14 to 30, 20 to 30), hmOn(s, 2026, 6, 1))
        assertEquals(listOf(2 to 30, 8 to 30, 14 to 30, 20 to 30), hmOn(s, 2026, 6, 2))
    }

    @Test fun hours_honour_multiple_anchor_times() {
        // Two anchors, each stepping every 12h: 06:00 and 09:00.
        val s = sched(
            "2026-06-01", PeriodUnit.HOURS, n = 12,
            times = listOf(DoseTime(hour = 6, minute = 0), DoseTime(hour = 9, minute = 0)),
        )
        assertEquals(listOf(6 to 0, 9 to 0, 18 to 0, 21 to 0), hmOn(s, 2026, 6, 1))
    }

    // ---- DAYS ----
    @Test fun days_respect_interval_and_sort_times() {
        val s = sched(
            "2026-06-01", PeriodUnit.DAYS, n = 3,
            times = listOf(DoseTime(hour = 20, minute = 30), DoseTime(hour = 8, minute = 0)),
        )
        assertEquals(listOf(8 to 0, 20 to 30), hmOn(s, 2026, 6, 1)) // sorted
        assertTrue(ScheduleEngine.occurrencesOn(s, 2026, 6, 2).isEmpty()) // +1 day
        assertEquals(2, ScheduleEngine.occurrencesOn(s, 2026, 6, 4).size) // +3 days
        assertEquals(2, ScheduleEngine.occurrencesOn(s, 2026, 6, 7).size) // +6 days
    }

    // ---- WEEKS ----
    @Test fun weeks_fire_only_on_selected_weekdays() {
        val s = sched(
            "2026-06-01", PeriodUnit.WEEKS, n = 1,
            times = listOf(
                DoseTime(weekday = DayOfWeek.MONDAY.value, hour = 8),
                DoseTime(weekday = DayOfWeek.THURSDAY.value, hour = 20),
            ),
        )
        for (d in 1..14) {
            val date = LocalDate.of(2026, 6, d)
            val expectFire = date.dayOfWeek == DayOfWeek.MONDAY || date.dayOfWeek == DayOfWeek.THURSDAY
            assertEquals(
                "2026-06-$d (${date.dayOfWeek})",
                if (expectFire) 1 else 0,
                ScheduleEngine.occurrencesOn(s, 2026, 6, d).size,
            )
        }
    }

    @Test fun weeks_every_2_skips_the_in_between_week() {
        val start = LocalDate.of(2026, 6, 1)
        val s = sched(
            start.toString(), PeriodUnit.WEEKS, n = 2,
            times = listOf(DoseTime(weekday = start.dayOfWeek.value, hour = 8)),
        )
        fun fires(date: LocalDate) = ScheduleEngine.occurrencesOn(s, date.year, date.monthValue, date.dayOfMonth).isNotEmpty()
        assertTrue(fires(start))                 // week 0
        assertTrue(!fires(start.plusWeeks(1)))   // week 1 — skipped
        assertTrue(fires(start.plusWeeks(2)))    // week 2
    }

    // ---- MONTHS ----
    @Test fun months_clamp_day_to_month_end() {
        val s = sched(
            "2026-01-31", PeriodUnit.MONTHS, n = 1,
            times = listOf(DoseTime(dayOfMonth = 31, hour = 8)),
        )
        assertEquals(1, ScheduleEngine.occurrencesOn(s, 2026, 1, 31).size)
        assertEquals(1, ScheduleEngine.occurrencesOn(s, 2026, 2, 28).size) // clamped (2026 not leap)
        assertEquals(1, ScheduleEngine.occurrencesOn(s, 2026, 4, 30).size) // clamped from 31
        assertTrue(ScheduleEngine.occurrencesOn(s, 2026, 4, 29).isEmpty())
    }

    @Test fun months_every_2_skips_alternate_months() {
        val s = sched(
            "2026-06-15", PeriodUnit.MONTHS, n = 2,
            times = listOf(DoseTime(dayOfMonth = 15, hour = 8)),
        )
        assertEquals(1, ScheduleEngine.occurrencesOn(s, 2026, 6, 15).size)
        assertTrue(ScheduleEngine.occurrencesOn(s, 2026, 7, 15).isEmpty())
        assertEquals(1, ScheduleEngine.occurrencesOn(s, 2026, 8, 15).size)
    }

    // ---- YEARS ----
    @Test fun years_fire_on_the_anniversary_with_interval() {
        val s = sched(
            "2026-06-09", PeriodUnit.YEARS, n = 2,
            times = listOf(DoseTime(month = 6, dayOfMonth = 9, hour = 9)),
        )
        assertEquals(1, ScheduleEngine.occurrencesOn(s, 2026, 6, 9).size)
        assertTrue(ScheduleEngine.occurrencesOn(s, 2027, 6, 9).isEmpty()) // +1y skipped
        assertEquals(1, ScheduleEngine.occurrencesOn(s, 2028, 6, 9).size) // +2y
    }

    @Test fun years_leap_day_clamps_to_feb_28_in_common_years() {
        val s = sched(
            "2024-02-29", PeriodUnit.YEARS, n = 1,
            times = listOf(DoseTime(month = 2, dayOfMonth = 29, hour = 9)),
        )
        assertEquals(1, ScheduleEngine.occurrencesOn(s, 2024, 2, 29).size) // leap year
        assertEquals(1, ScheduleEngine.occurrencesOn(s, 2025, 2, 28).size) // clamped
        assertTrue(ScheduleEngine.occurrencesOn(s, 2025, 2, 29).isEmpty()) // not a real date
    }

    // ---- End modes ----
    @Test fun end_on_date_is_inclusive_then_stops() {
        val s = sched(
            "2026-06-01", PeriodUnit.DAYS, n = 1, times = listOf(DoseTime(hour = 8)),
            endMode = EndMode.DATE, endDate = "2026-06-05",
        )
        assertEquals(1, ScheduleEngine.occurrencesOn(s, 2026, 6, 5).size)
        assertTrue(ScheduleEngine.occurrencesOn(s, 2026, 6, 6).isEmpty())
    }

    @Test fun end_after_count_stops_after_n_doses() {
        val s = sched(
            "2026-06-01", PeriodUnit.DAYS, n = 1, times = listOf(DoseTime(hour = 8)),
            endMode = EndMode.COUNT, doseCount = 3,
        )
        assertEquals(1, ScheduleEngine.occurrencesOn(s, 2026, 6, 3).size) // 3rd dose
        assertTrue(ScheduleEngine.occurrencesOn(s, 2026, 6, 4).isEmpty()) // 4th -> stop
    }

    // ---- firstOccurrence (the "first dose must be in the future" guard) ----

    // An "every N hours" series anchors at the start date's time-of-day, so its
    // first dose is exactly that start date-time — not midnight, not the "next
    // hour". Picking a future start time is therefore what keeps the first dose
    // in the future.
    @Test fun first_occurrence_hourly_is_the_anchor_date_time() {
        val s = sched("2026-06-01", PeriodUnit.HOURS, n = 1, times = listOf(DoseTime(hour = 9, minute = 0)))
        assertEquals(LocalDate.of(2026, 6, 1).atTime(9, 0), ScheduleEngine.firstOccurrence(s))

        val s30 = sched("2026-06-01", PeriodUnit.HOURS, n = 6, times = listOf(DoseTime(hour = 7, minute = 30)))
        assertEquals(LocalDate.of(2026, 6, 1).atTime(7, 30), ScheduleEngine.firstOccurrence(s30))
    }

    @Test fun first_occurrence_days_is_earliest_time_on_start_date() {
        val s = sched(
            "2026-06-01", PeriodUnit.DAYS,
            times = listOf(DoseTime(hour = 20), DoseTime(hour = 8, minute = 30)),
        )
        assertEquals(LocalDate.of(2026, 6, 1).atTime(8, 30), ScheduleEngine.firstOccurrence(s))
    }

    @Test fun first_occurrence_weeks_is_earliest_selected_weekday() {
        // Start Mon 2026-06-01, fires Wed & Fri -> first is that Wednesday.
        val s = sched(
            "2026-06-01", PeriodUnit.WEEKS,
            times = listOf(DoseTime(weekday = 5, hour = 9), DoseTime(weekday = 3, hour = 9)),
        )
        assertEquals(LocalDate.of(2026, 6, 3).atTime(9, 0), ScheduleEngine.firstOccurrence(s))
    }

    @Test fun first_occurrence_once_is_earliest_entry() {
        val s = sched(
            "2026-06-01", PeriodUnit.ONCE,
            times = listOf(
                DoseTime(year = 2026, month = 6, dayOfMonth = 20, hour = 9),
                DoseTime(year = 2026, month = 6, dayOfMonth = 10, hour = 21, minute = 15),
            ),
        )
        assertEquals(LocalDate.of(2026, 6, 10).atTime(21, 15), ScheduleEngine.firstOccurrence(s))
    }

    @Test fun first_occurrence_null_when_no_times() {
        assertNull(ScheduleEngine.firstOccurrence(sched("2026-06-01", PeriodUnit.DAYS, times = emptyList())))
    }
}
