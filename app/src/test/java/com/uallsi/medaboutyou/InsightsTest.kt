// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import com.uallsi.medaboutyou.domain.Insights
import com.uallsi.medaboutyou.domain.Now
import com.uallsi.medaboutyou.domain.ScheduleQuery
import com.uallsi.medaboutyou.model.DoseTime
import com.uallsi.medaboutyou.model.EndMode
import com.uallsi.medaboutyou.model.Occurrence
import com.uallsi.medaboutyou.model.PeriodUnit
import com.uallsi.medaboutyou.model.Schedule
import com.uallsi.medaboutyou.model.ScheduleEngine
import com.uallsi.medaboutyou.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the pure domain ports (`ScheduleEngine`, `Insights`) against the
 * behaviour documented for the C++ originals. Mirrors `tests/test_insights.cpp`.
 */
class InsightsTest {

    private fun dailySchedule() = Schedule(
        id = 1,
        medName = "Metformin",
        startDate = "2026-06-01",
        endMode = EndMode.NEVER,
        periodUnit = PeriodUnit.DAYS,
        periodN = 1,
        times = listOf(DoseTime(hour = 8, minute = 0)),
    )

    /** In-memory query backed by precomputed occurrences for a date map. */
    private class FakeQuery(
        val schedules: List<Schedule>,
        val occByDate: Map<Triple<Int, Int, Int>, List<Occurrence>>,
    ) : ScheduleQuery {
        override fun list(includeCancelled: Boolean) = schedules
        override fun occurrencesOn(year: Int, month: Int, day: Int) =
            occByDate[Triple(year, month, day)].orEmpty()
    }

    @Test
    fun daily_schedule_yields_one_dose_per_day() {
        val sch = dailySchedule()
        assertEquals(1, ScheduleEngine.occurrencesOn(sch, 2026, 6, 1).size)
        assertEquals(1, ScheduleEngine.occurrencesOn(sch, 2026, 6, 2).size)
        // Nothing before the start date.
        assertTrue(ScheduleEngine.occurrencesOn(sch, 2026, 5, 31).isEmpty())
    }

    @Test
    fun hours_schedule_yields_multiple_doses_per_day() {
        // HOURS steps every N hours from the start date-time (the entry's
        // hour:minute) — here anchored at midnight, so 00:00, 06:00, 12:00, 18:00.
        val sch = dailySchedule().copy(
            periodUnit = PeriodUnit.HOURS, periodN = 6,
            times = listOf(DoseTime(hour = 0, minute = 0)),
        )
        val day1 = ScheduleEngine.occurrencesOn(sch, 2026, 6, 1)
        assertEquals(listOf(0, 6, 12, 18), day1.map { it.hour })
    }

    @Test
    fun days_schedule_with_several_times_yields_one_per_entry() {
        val sch = dailySchedule().copy(
            times = listOf(DoseTime(hour = 8, minute = 0), DoseTime(hour = 20, minute = 30)),
        )
        val day = ScheduleEngine.occurrencesOn(sch, 2026, 6, 1)
        assertEquals(listOf(8 to 0, 20 to 30), day.map { it.hour to it.minute })
    }

    @Test
    fun months_schedule_clamps_day_to_last_existing_day() {
        // Day 31 every month -> 30 Sep, 28 Feb (no Feb 29 here: 2026 is not a leap year).
        val sch = dailySchedule().copy(
            startDate = "2026-01-31",
            periodUnit = PeriodUnit.MONTHS, periodN = 1,
            times = listOf(DoseTime(dayOfMonth = 31, hour = 8)),
        )
        assertEquals(1, ScheduleEngine.occurrencesOn(sch, 2026, 1, 31).size)
        assertEquals(1, ScheduleEngine.occurrencesOn(sch, 2026, 9, 30).size)   // clamped from 31
        assertTrue(ScheduleEngine.occurrencesOn(sch, 2026, 9, 29).isEmpty())
        assertEquals(1, ScheduleEngine.occurrencesOn(sch, 2026, 2, 28).size)   // clamped from 31
    }

    @Test
    fun years_schedule_lands_on_the_anniversary() {
        val sch = dailySchedule().copy(
            startDate = "2026-03-10",
            periodUnit = PeriodUnit.YEARS, periodN = 1,
            times = listOf(DoseTime(month = 3, dayOfMonth = 10, hour = 9)),
        )
        assertEquals(1, ScheduleEngine.occurrencesOn(sch, 2026, 3, 10).size)
        assertEquals(1, ScheduleEngine.occurrencesOn(sch, 2027, 3, 10).size)
        assertTrue(ScheduleEngine.occurrencesOn(sch, 2027, 3, 11).isEmpty())
    }

    @Test
    fun once_schedule_fires_only_on_its_exact_date() {
        val sch = dailySchedule().copy(
            periodUnit = PeriodUnit.ONCE,
            times = listOf(DoseTime(year = 2026, month = 12, dayOfMonth = 25, hour = 9)),
        )
        assertEquals(1, ScheduleEngine.occurrencesOn(sch, 2026, 12, 25).size)
        assertTrue(ScheduleEngine.occurrencesOn(sch, 2026, 12, 26).isEmpty())
    }

    @Test
    fun weeks_schedule_lands_only_on_interval_dates() {
        val sch = dailySchedule().copy(periodUnit = PeriodUnit.WEEKS, periodN = 1)
        assertEquals(1, ScheduleEngine.occurrencesOn(sch, 2026, 6, 1).size)
        assertTrue(ScheduleEngine.occurrencesOn(sch, 2026, 6, 2).isEmpty())
        assertEquals(1, ScheduleEngine.occurrencesOn(sch, 2026, 6, 8).size)
    }

    @Test
    fun count_end_mode_stops_after_n_doses() {
        val sch = dailySchedule().copy(endMode = EndMode.COUNT, doseCount = 3)
        assertEquals(1, ScheduleEngine.occurrencesOn(sch, 2026, 6, 3).size)  // index 2
        assertTrue(ScheduleEngine.occurrencesOn(sch, 2026, 6, 4).isEmpty())  // index 3 -> stop
    }

    @Test
    fun dose_is_due_compares_against_now() {
        val occ = Occurrence(year = 2026, month = 6, day = 1, hour = 8, minute = 0)
        assertTrue(Insights.doseIsDue(occ, Now(2026, 6, 1, 8, 0)))
        assertTrue(Insights.doseIsDue(occ, Now(2026, 6, 1, 9, 0)))
        assertFalse(Insights.doseIsDue(occ, Now(2026, 6, 1, 7, 59)))
    }

    @Test
    fun adherence_counts_only_due_doses() {
        val now = Now(2026, 6, 3, 12, 0)
        val occ = { d: Int, status: String ->
            Occurrence(scheduleId = 1, medName = "Metformin", year = 2026, month = 6, day = d, hour = 8, status = status)
        }
        val q = FakeQuery(
            schedules = listOf(dailySchedule()),
            occByDate = mapOf(
                Triple(2026, 6, 1) to listOf(occ(1, "taken")),
                Triple(2026, 6, 2) to listOf(occ(2, "untaken")),
                Triple(2026, 6, 3) to listOf(occ(3, "taken")),
            ),
        )
        val stats = Insights.adherence(q, 7, now)
        assertEquals(3, stats.scheduled)
        assertEquals(2, stats.taken)
        assertEquals(1, stats.missed)
    }

    @Test
    fun streak_breaks_on_a_missed_dose() {
        val now = Now(2026, 6, 3, 12, 0)
        val occ = { d: Int, status: String ->
            Occurrence(scheduleId = 1, medName = "Metformin", year = 2026, month = 6, day = d, hour = 8, status = status)
        }
        val q = FakeQuery(
            schedules = listOf(dailySchedule()),
            occByDate = mapOf(
                Triple(2026, 6, 3) to listOf(occ(3, "taken")),
                Triple(2026, 6, 2) to listOf(occ(2, "taken")),
                Triple(2026, 6, 1) to listOf(occ(1, "untaken")),
            ),
        )
        assertEquals(2, Insights.currentStreak(q, now))
    }

    @Test
    fun forecast_runout_flags_when_stock_cannot_cover() {
        val now = Now(2026, 6, 1, 0, 0)  // before the 08:00 dose, so day 1 is "future"
        val occ = { d: Int ->
            Occurrence(scheduleId = 1, medSource = Source.EMA, medName = "Metformin", year = 2026, month = 6, day = d, hour = 8)
        }
        val occByDate = (1..10).associate { d -> Triple(2026, 6, d) to listOf(occ(d)) }
        val q = FakeQuery(listOf(dailySchedule()), occByDate)
        // Stock of 2 covers days 1 and 2; runs out on day 3.
        val runout = Insights.forecastRunouts(q, { _, _, _ -> 2 }, now)
        val key = Insights.medKey(Source.EMA, "", "Metformin")
        assertEquals(3, runout[key]?.day)
    }
}
