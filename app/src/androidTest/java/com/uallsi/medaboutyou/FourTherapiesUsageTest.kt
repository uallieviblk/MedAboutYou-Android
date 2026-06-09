// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uallsi.medaboutyou.data.local.MedDatabase
import com.uallsi.medaboutyou.data.local.MedicineStore
import com.uallsi.medaboutyou.data.local.ScheduleRepository
import com.uallsi.medaboutyou.domain.AdherenceStats
import com.uallsi.medaboutyou.domain.DosesAvailable
import com.uallsi.medaboutyou.domain.Insights
import com.uallsi.medaboutyou.domain.Now
import com.uallsi.medaboutyou.model.DoseTime
import com.uallsi.medaboutyou.model.EndMode
import com.uallsi.medaboutyou.model.Occurrence
import com.uallsi.medaboutyou.model.PeriodUnit
import com.uallsi.medaboutyou.model.Schedule
import com.uallsi.medaboutyou.model.Source
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Real-world, end-to-end simulation against a real (in-memory) Room database on a
 * device/emulator. A single patient is on **four concurrent therapies** that
 * together exercise the whole scheduling matrix and every cross-cutting feature
 * (occurrence generation, dose logging, stock debit/restore with the ≥0 clamp,
 * adherence overall + per-medicine, streak, refill forecast, plus append-only
 * edit-from-now and pause/resume):
 *
 *  1. **Metformin 500 mg** — chronic type-2 diabetes, twice daily 08:00 + 20:00,
 *     [PeriodUnit.DAYS] / [EndMode.NEVER]. The realistic "I forgot one evening"
 *     case: every dose taken except both doses on one day (drives adherence < 100%
 *     and breaks the streak at a known point).
 *  2. **Amoxicillin 875 mg** — a finite 7-day antibiotic course, every 8 hours,
 *     [PeriodUnit.HOURS] / [EndMode.COUNT] (21 doses). Whole course completed.
 *  3. **Methotrexate** — weekly rheumatoid-arthritis dose, Mondays 18:00,
 *     [PeriodUnit.WEEKS] / [EndMode.NEVER].
 *  4. **Cholecalciferol (Vit D)** — monthly supplement on the 15th, fixed-term
 *     [PeriodUnit.MONTHS] / [EndMode.DATE] ending 2026-12-15.
 *
 * Everything is anchored to a fixed reference clock so the assertions are exact
 * and independent of the wall clock: occurrence generation does not consult "now"
 * for non-paused schedules, and the analytics take an explicit [Now].
 */
@RunWith(AndroidJUnit4::class)
class FourTherapiesUsageTest {

    private lateinit var db: MedDatabase
    private lateinit var schedules: ScheduleRepository
    private lateinit var medicines: MedicineStore

    /** Reference "now": late on 2026-06-09 so every dose scheduled today is due. */
    private val now = Now(2026, 6, 9, 23, 0)
    private val ref = LocalDate.of(2026, 6, 9)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MedDatabase::class.java,
        ).allowMainThreadQueries().build()
        schedules = ScheduleRepository(db)
        medicines = MedicineStore(db)
    }

    @After
    fun tearDown() = db.close()

    // --- The canonical "take/untake a dose" the way the Today/Calendar VMs do. ---

    private suspend fun take(occ: Occurrence) {
        schedules.logDose(occ.scheduleId, occ.keyIso, "taken")
        medicines.adjustDoses(occ.medSource, occ.medExtId, occ.medName, -1)
    }

    private suspend fun untake(occ: Occurrence) {
        schedules.logDose(occ.scheduleId, occ.keyIso, "untaken")
        medicines.adjustDoses(occ.medSource, occ.medExtId, occ.medName, 1)
    }

    /** Every due occurrence of [scheduleId] from [from]..[to], in date order. */
    private suspend fun dueOccurrences(scheduleId: Long, from: LocalDate, to: LocalDate): List<Occurrence> {
        val snap = schedules.snapshot()
        val out = ArrayList<Occurrence>()
        var d = from
        while (!d.isAfter(to)) {
            snap.occurrencesOn(d.year, d.monthValue, d.dayOfMonth)
                .filter { it.scheduleId == scheduleId && Insights.doseIsDue(it, now) }
                .forEach { out.add(it) }
            d = d.plusDays(1)
        }
        return out
    }

    /** A pure stock lookup over the *current* persisted inventory, for Insights. */
    private suspend fun dosesAvailable(): DosesAvailable {
        val stock = HashMap<String, Int>()
        for (sch in schedules.list(false)) {
            val key = Insights.medKey(sch.medSource, sch.medExtId, sch.medName)
            if (key !in stock) {
                stock[key] = medicines.availableDoses(sch.medSource, sch.medExtId, sch.medName)
            }
        }
        return { s, e, n -> stock[Insights.medKey(s, e, n)] ?: 0 }
    }

    @Test
    fun four_therapies_real_world_simulation() = runBlocking {
        // ---------------------------------------------------------------------
        // 1. The patient's four prescriptions, each with a starting stock.
        // ---------------------------------------------------------------------
        val metId = schedules.create(
            Schedule(
                medSource = Source.EMA, medName = "Metformin",
                startDate = "2026-05-10", endMode = EndMode.NEVER,
                periodUnit = PeriodUnit.DAYS, periodN = 1,
                times = listOf(DoseTime(hour = 8, minute = 0), DoseTime(hour = 20, minute = 0)),
                windowMinutes = 30,
            ),
        )
        medicines.setDoses(Source.EMA, "", "Metformin", 100)

        val amoxId = schedules.create(
            Schedule(
                medSource = Source.EMA, medName = "Amoxicillin",
                startDate = "2026-06-02", endMode = EndMode.COUNT, doseCount = 21,
                periodUnit = PeriodUnit.HOURS, periodN = 8,
                times = listOf(DoseTime(hour = 9, minute = 0)),
                windowMinutes = 60,
            ),
        )
        medicines.setDoses(Source.EMA, "", "Amoxicillin", 21)

        val mtxId = schedules.create(
            Schedule(
                medSource = Source.EMA, medName = "Methotrexate",
                startDate = "2026-05-11", endMode = EndMode.NEVER, // a Monday
                periodUnit = PeriodUnit.WEEKS, periodN = 1,
                times = listOf(DoseTime(weekday = 1, hour = 18, minute = 0)), // Mon 18:00
                windowMinutes = 120,
            ),
        )
        medicines.setDoses(Source.EMA, "", "Methotrexate", 12)

        val vitId = schedules.create(
            Schedule(
                medSource = Source.EMA, medName = "Cholecalciferol",
                startDate = "2026-04-15", endMode = EndMode.DATE, endDate = "2026-12-15",
                periodUnit = PeriodUnit.MONTHS, periodN = 1,
                times = listOf(DoseTime(dayOfMonth = 15, hour = 9, minute = 0)),
                windowMinutes = 60,
            ),
        )
        medicines.setDoses(Source.EMA, "", "Cholecalciferol", 9)

        // ---------------------------------------------------------------------
        // 2. The engine should generate exactly the doses a human would expect.
        // ---------------------------------------------------------------------
        // Metformin: 2026-05-10..06-09 inclusive = 31 days × 2 = 62 due doses.
        val metDue = dueOccurrences(metId, LocalDate.of(2026, 5, 10), ref)
        assertEquals(62, metDue.size)
        // Amoxicillin: COUNT-capped 7-day course = 21 doses, all already due.
        val amoxDue = dueOccurrences(amoxId, LocalDate.of(2026, 6, 2), ref)
        assertEquals(21, amoxDue.size)
        // Methotrexate: Mondays 05-11, 05-18, 05-25, 06-01, 06-08 = 5 due doses.
        val mtxDue = dueOccurrences(mtxId, LocalDate.of(2026, 5, 11), ref)
        assertEquals(5, mtxDue.size)
        // Cholecalciferol: the 15th of 04 and 05 are due; 06-15 is still in the future.
        val vitDue = dueOccurrences(vitId, LocalDate.of(2026, 4, 15), ref)
        assertEquals(2, vitDue.size)

        // ---------------------------------------------------------------------
        // 3. The patient takes their doses. Stock debits one unit per dose.
        //    Realistic slip: both Metformin doses on 2026-05-20 are later marked
        //    "not actually taken" (untaken) — so adherence dips and the streak
        //    breaks on exactly that day.
        // ---------------------------------------------------------------------
        (metDue + amoxDue + mtxDue + vitDue).forEach { take(it) }

        val missedDay = LocalDate.of(2026, 5, 20)
        val missed = metDue.filter {
            it.year == missedDay.year && it.month == missedDay.monthValue && it.day == missedDay.dayOfMonth
        }
        assertEquals(2, missed.size) // 08:00 + 20:00 that Wednesday
        missed.forEach { untake(it) }

        // ---------------------------------------------------------------------
        // 4. Stock: starting amount minus net doses taken, clamped at ≥ 0.
        // ---------------------------------------------------------------------
        assertEquals(40, medicines.availableDoses(Source.EMA, "", "Metformin"))      // 100 − 62 + 2
        assertEquals(0, medicines.availableDoses(Source.EMA, "", "Amoxicillin"))     // 21 − 21 (course done)
        assertEquals(7, medicines.availableDoses(Source.EMA, "", "Methotrexate"))    // 12 − 5
        assertEquals(7, medicines.availableDoses(Source.EMA, "", "Cholecalciferol")) // 9 − 2

        // ---------------------------------------------------------------------
        // 5. Adherence — overall and per medicine — over a window that spans all
        //    therapies. 90 scheduled, 88 taken, 2 missed.
        // ---------------------------------------------------------------------
        val snap = schedules.snapshot()
        val overall = Insights.adherence(snap, windowDays = 120, now = now)
        assertEquals(90, overall.scheduled)
        assertEquals(88, overall.taken)
        assertEquals(2, overall.missed)
        assertEquals(88.0 / 90.0, overall.rate, 1e-9)

        val byMed = Insights.adherenceByMedicine(snap, windowDays = 120, now = now)
        assertEquals(
            listOf(
                "Amoxicillin" to AdherenceStats(21, 21, 0, 1.0),
                "Cholecalciferol" to AdherenceStats(2, 2, 0, 1.0),
                "Metformin" to AdherenceStats(62, 60, 2, 60.0 / 62.0), // "Metf" < "Meth"
                "Methotrexate" to AdherenceStats(5, 5, 0, 1.0),
            ),
            byMed,
        )

        // ---------------------------------------------------------------------
        // 6. Streak: perfect days counting back from today stop at 2026-05-20.
        //    2026-05-21..06-09 inclusive = 20 consecutive perfect days.
        // ---------------------------------------------------------------------
        assertEquals(20, Insights.currentStreak(snap, now))

        // ---------------------------------------------------------------------
        // 7. Refill forecast: only the two open-ended therapies whose stock runs
        //    out before their (non-existent) end are flagged.
        //      • Metformin: 40 left, 2/day → runs out 2026-06-30.
        //      • Methotrexate: 7 left, 1/week → runs out 2026-08-03.
        //    Amoxicillin (course finished, no future doses) and Cholecalciferol
        //    (stock covers every remaining monthly dose to its end) are absent.
        // ---------------------------------------------------------------------
        val refills = Insights.refillForecast(snap, dosesAvailable(), now)
        assertEquals(2, refills.size)
        // Sorted by run-out date: Metformin (06-30) before Methotrexate (08-03).
        val first = refills[0]
        assertEquals("Metformin", first.name)
        assertEquals(Triple(2026, 6, 30), Triple(first.year, first.month, first.day))
        val second = refills[1]
        assertEquals("Methotrexate", second.name)
        assertEquals(Triple(2026, 8, 3), Triple(second.year, second.month, second.day))
        assertTrue(refills.none { it.name == "Amoxicillin" || it.name == "Cholecalciferol" })

        // ---------------------------------------------------------------------
        // 8. All four therapies are listed as active prescriptions.
        // ---------------------------------------------------------------------
        assertEquals(
            setOf("Metformin", "Amoxicillin", "Methotrexate", "Cholecalciferol"),
            schedules.list(includeCancelled = false).map { it.medName }.toSet(),
        )
    }

    @Test
    fun edit_from_now_ends_original_yesterday_and_preserves_history() = runBlocking {
        val today = LocalDate.now()
        val start = today.minusDays(10)
        val id = schedules.create(
            Schedule(
                medSource = Source.EMA, medName = "Lisinopril",
                startDate = start.toString(), endMode = EndMode.NEVER,
                periodUnit = PeriodUnit.DAYS, periodN = 1,
                times = listOf(DoseTime(hour = 8, minute = 0)),
                windowMinutes = 30,
            ),
        )

        // Before the edit today fires once, at the original 08:00.
        val before = schedules.snapshot()
            .occurrencesOn(today.year, today.monthValue, today.dayOfMonth)
            .filter { it.scheduleId == id }
        assertEquals(1, before.size)
        assertEquals(8, before.first().hour)

        // Record a taken dose three days ago to prove history survives the edit.
        val pastDay = today.minusDays(3)
        val pastOcc = schedules.snapshot()
            .occurrencesOn(pastDay.year, pastDay.monthValue, pastDay.dayOfMonth)
            .first { it.scheduleId == id }
        schedules.logDose(id, pastOcc.keyIso, "taken")

        // Move the dose to 09:00 from today on.
        val original = schedules.get(id)!!
        schedules.editFromNow(original.copy(times = listOf(DoseTime(hour = 9, minute = 0))))

        // Append-only: the original row is capped to end yesterday, never deleted.
        val orig = schedules.get(id)!!
        assertEquals(EndMode.DATE, orig.endMode)
        assertEquals(today.minusDays(1).toString(), orig.endDate)
        assertTrue(schedules.list(includeCancelled = false).any { it.id == id })

        // A new active row starts today at the new time.
        val newRow = schedules.list(includeCancelled = false).first { it.id != id }
        assertEquals(today.toString(), newRow.startDate)
        assertEquals(9, newRow.times.first().hour)

        // Today now fires exactly once, at 09:00 (the original no longer fires today).
        val todayOccs = schedules.snapshot()
            .occurrencesOn(today.year, today.monthValue, today.dayOfMonth)
        assertEquals(1, todayOccs.size)
        assertEquals(9, todayOccs.first().hour)

        // The past is untouched: still one 08:00 dose, still marked taken.
        val pastOccs = schedules.snapshot()
            .occurrencesOn(pastDay.year, pastDay.monthValue, pastDay.dayOfMonth)
        assertEquals(1, pastOccs.size)
        assertEquals(8, pastOccs.first().hour)
        assertEquals("taken", pastOccs.first().status)
    }

    @Test
    fun pausing_hides_future_doses_but_keeps_history() = runBlocking {
        val today = LocalDate.now()
        val id = schedules.create(
            Schedule(
                medSource = Source.EMA, medName = "Warfarin",
                startDate = today.minusDays(5).toString(), endMode = EndMode.NEVER,
                periodUnit = PeriodUnit.DAYS, periodN = 1,
                times = listOf(DoseTime(hour = 18, minute = 0)),
                windowMinutes = 30,
            ),
        )

        suspend fun occCount(d: LocalDate): Int = schedules.snapshot()
            .occurrencesOn(d.year, d.monthValue, d.dayOfMonth)
            .count { it.scheduleId == id }

        val tomorrow = today.plusDays(1)
        val past = today.minusDays(3)
        assertEquals(1, occCount(tomorrow))

        // Indefinite pause hides the future but never the past.
        schedules.setPause(id, suspended = true, until = "")
        assertEquals(0, occCount(tomorrow))
        assertEquals(1, occCount(past))

        // Resume restores future doses.
        schedules.setPause(id, suspended = false, until = "")
        assertEquals(1, occCount(tomorrow))

        // Timed pause hides up to (but not including) its resume date.
        val until = today.plusDays(4)
        schedules.setPause(id, suspended = false, until = until.toString())
        assertEquals(0, occCount(tomorrow)) // before `until`
        assertEquals(1, occCount(until))    // on `until`, doses are back
    }
}
