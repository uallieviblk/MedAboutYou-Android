// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uallsi.medaboutyou.data.local.MedDatabase
import com.uallsi.medaboutyou.data.local.ScheduleRepository
import com.uallsi.medaboutyou.model.DoseTime
import com.uallsi.medaboutyou.model.EndMode
import com.uallsi.medaboutyou.model.PeriodUnit
import com.uallsi.medaboutyou.model.Schedule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [ScheduleRepository] against a real (in-memory) Room database on a
 * device/emulator: create → occurrences → log → override → cancel.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleRepositoryInstrumentedTest {

    private lateinit var db: MedDatabase
    private lateinit var repo: ScheduleRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MedDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = ScheduleRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun dailyAt8() = Schedule(
        medName = "Metformin",
        startDate = "2026-06-01",
        endMode = EndMode.NEVER,
        periodUnit = PeriodUnit.DAYS,
        periodN = 1,
        times = listOf(DoseTime(hour = 8, minute = 0)),
    )

    @Test
    fun create_then_snapshot_yields_daily_occurrence() = runBlocking {
        repo.create(dailyAt8())
        val snap = repo.snapshot()
        val occs = snap.occurrencesOn(2026, 6, 2)
        assertEquals(1, occs.size)
        assertEquals(8, occs.first().hour)
    }

    @Test
    fun logging_a_dose_attaches_status() = runBlocking {
        val id = repo.create(dailyAt8())
        val key = repo.snapshot().occurrencesOn(2026, 6, 1).first().keyIso
        repo.logDose(id, key, "taken")
        val occ = repo.snapshot().occurrencesOn(2026, 6, 1).first()
        assertEquals("taken", occ.status)
    }

    @Test
    fun cancelling_an_occurrence_removes_it() = runBlocking {
        val id = repo.create(dailyAt8())
        val key = repo.snapshot().occurrencesOn(2026, 6, 3).first().keyIso
        repo.editSingle(id, key, 8, 0, 30, cancelled = true)
        assertTrue(repo.snapshot().occurrencesOn(2026, 6, 3).isEmpty())
    }

    @Test
    fun cancelling_schedule_is_soft_and_stops_occurrences() = runBlocking {
        val id = repo.create(dailyAt8())
        repo.cancel(id)
        assertTrue(repo.snapshot().occurrencesOn(2026, 6, 5).isEmpty())
        // Soft delete: row still present in the include-cancelled listing.
        assertTrue(repo.list(includeCancelled = true).any { it.id == id })
        assertFalse(repo.list(includeCancelled = false).any { it.id == id })
    }

    // ---- Fix-regression tests ----

    @Test
    fun log_dose_reports_each_status_transition_exactly_once() = runBlocking {
        val id = repo.create(dailyAt8())
        val key = repo.snapshot().occurrencesOn(2026, 6, 1).first().keyIso
        assertEquals(-1, repo.logDose(id, key, "taken")) // newly taken: consume a unit
        assertEquals(0, repo.logDose(id, key, "taken")) // double-tap: no stock move
        assertEquals(1, repo.logDose(id, key, "untaken")) // undo: return the unit
        assertEquals(0, repo.logDose(id, key, "untaken")) // still untaken: no move
        // Skipping a never-taken dose must not move stock either.
        val key2 = repo.snapshot().occurrencesOn(2026, 6, 2).first().keyIso
        assertEquals(0, repo.logDose(id, key2, "untaken"))
    }

    @Test
    fun split_from_preserves_other_dose_times_and_same_day_history() = runBlocking {
        val id = repo.create(dailyAt8().copy(times = listOf(DoseTime(hour = 8), DoseTime(hour = 20))))
        // Take the morning dose of the split day…
        val morning = repo.snapshot().occurrencesOn(2026, 6, 10).first { it.hour == 8 }
        repo.logDose(id, morning.keyIso, "taken")
        // …then retime that day's 20:00 dose "this and following" to 21:00.
        repo.splitFrom(
            id, 2026, 6, 10,
            fromHour = 20, fromMinute = 0, hour = 21, minute = 0,
            windowMinutes = 30, cancelled = false,
        )
        val snap = repo.snapshot()
        // Later days keep BOTH entries, with only the evening one retimed.
        assertEquals(
            listOf(8 to 0, 21 to 0),
            snap.occurrencesOn(2026, 6, 12).map { it.hour to it.minute },
        )
        // The split day still shows the morning dose as taken (carried over).
        assertEquals("taken", snap.occurrencesOn(2026, 6, 10).first { it.hour == 8 }.status)
    }

    @Test
    fun edit_from_now_freezes_a_finished_count_course() = runBlocking {
        // A 3-dose course that finished long before today (relative dates keep
        // the test stable whenever it runs).
        val today = java.time.LocalDate.now()
        val start = today.minusDays(10)
        val id = repo.create(
            dailyAt8().copy(startDate = start.toString(), endMode = EndMode.COUNT, doseCount = 3),
        )
        repo.editFromNow(repo.get(id)!!.copy(notes = "edited"))
        val snap = repo.snapshot()
        // The three elapsed doses stay intact…
        for (i in 0L..2L) {
            val d = start.plusDays(i)
            assertEquals(1, snap.occurrencesOn(d.year, d.monthValue, d.dayOfMonth).size)
        }
        // …and no phantom "missed" doses appear between the course end and today.
        for (i in 3L..9L) {
            val d = start.plusDays(i)
            assertTrue(
                "unexpected dose on $d",
                snap.occurrencesOn(d.year, d.monthValue, d.dayOfMonth).isEmpty(),
            )
        }
    }
}
