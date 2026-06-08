package com.uallsi.medaboutyou

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uallsi.medaboutyou.data.local.MedDatabase
import com.uallsi.medaboutyou.data.local.ScheduleRepository
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
        hour = 8,
        minute = 0,
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
}
