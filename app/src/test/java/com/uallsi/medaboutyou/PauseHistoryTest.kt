// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import com.uallsi.medaboutyou.data.local.PausePeriodEntity
import com.uallsi.medaboutyou.data.local.ScheduleSnapshot
import com.uallsi.medaboutyou.model.DoseTime
import com.uallsi.medaboutyou.model.EndMode
import com.uallsi.medaboutyou.model.PeriodUnit
import com.uallsi.medaboutyou.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pause windows must keep past paused days hidden: a vacation pause may never
 * retroactively turn into a run of "missed" doses once its days slip into the
 * past (all dates here are far in the past relative to any test run).
 */
class PauseHistoryTest {

    private val schedule = Schedule(
        id = 1,
        medName = "Metformin",
        startDate = "2020-01-01",
        endMode = EndMode.NEVER,
        periodUnit = PeriodUnit.DAYS,
        periodN = 1,
        times = listOf(DoseTime(hour = 8, minute = 0)),
    )

    private fun snapshot(vararg pauses: PausePeriodEntity) = ScheduleSnapshot(
        listOf(schedule),
        emptyMap(),
        emptyMap(),
        pauses.toList().groupBy { it.scheduleId },
    )

    @Test
    fun past_days_inside_a_closed_window_stay_hidden() {
        val snap = snapshot(
            PausePeriodEntity(scheduleId = 1, startDate = "2020-02-01", endDate = "2020-02-08"),
        )
        // Day before the window, first day, last paused day, resume day.
        assertEquals(1, snap.occurrencesOn(2020, 1, 31).size)
        assertTrue(snap.occurrencesOn(2020, 2, 1).isEmpty())
        assertTrue(snap.occurrencesOn(2020, 2, 7).isEmpty())
        assertEquals(1, snap.occurrencesOn(2020, 2, 8).size) // end is exclusive
    }

    @Test
    fun past_days_inside_an_open_window_stay_hidden() {
        val snap = snapshot(
            PausePeriodEntity(scheduleId = 1, startDate = "2020-02-01", endDate = ""),
        )
        assertEquals(1, snap.occurrencesOn(2020, 1, 31).size)
        assertTrue(snap.occurrencesOn(2020, 2, 1).isEmpty())
        assertTrue(snap.occurrencesOn(2021, 6, 15).isEmpty())
    }

    @Test
    fun without_windows_past_days_are_not_paused() {
        val snap = snapshot()
        assertEquals(1, snap.occurrencesOn(2020, 2, 1).size)
    }

    @Test
    fun windows_only_affect_their_own_schedule() {
        val snap = snapshot(
            PausePeriodEntity(scheduleId = 99, startDate = "2020-02-01", endDate = "2020-02-08"),
        )
        assertEquals(1, snap.occurrencesOn(2020, 2, 3).size)
    }
}
