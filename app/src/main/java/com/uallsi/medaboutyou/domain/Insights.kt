// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.domain

import com.uallsi.medaboutyou.model.Occurrence
import com.uallsi.medaboutyou.model.Schedule
import com.uallsi.medaboutyou.model.Source
import java.time.LocalDate

/**
 * Read-only view over schedules and their occurrences, the way the C++
 * `insights` functions consume `ScheduleRepository`. Keeping it an interface
 * makes the analytics unit-testable without Room.
 */
interface ScheduleQuery {
    fun list(includeCancelled: Boolean): List<Schedule>
    fun occurrencesOn(year: Int, month: Int, day: Int): List<Occurrence>
}

/** Stock lookup injected into the analytics (matches the C++ `DosesAvailable`). */
typealias DosesAvailable = (source: Source, ext: String, name: String) -> Int

/** A fixed "now" so the analytics are deterministic and testable. */
data class Now(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int) {
    companion object {
        fun local(): Now {
            val n = java.time.LocalDateTime.now()
            return Now(n.year, n.monthValue, n.dayOfMonth, n.hour, n.minute)
        }
    }
}

data class RunOut(val year: Int, val month: Int, val day: Int, val keyIso: String)

data class RefillForecast(
    val source: Source,
    val ext: String,
    val name: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val keyIso: String,
    val dosesLeft: Int,
)

data class AdherenceStats(
    val scheduled: Int = 0,
    val taken: Int = 0,
    val missed: Int = 0,
    val rate: Double = 0.0,
)

data class DayAdherence(
    val year: Int,
    val month: Int,
    val day: Int,
    val scheduled: Int,
    val taken: Int,
)

/**
 * Pure, deterministic schedule/dose-log analytics — faithful port of
 * `data/insights.cpp`. No Android dependency.
 */
object Insights {

    /** Stable per-medicine key, identical layout to the C++ `med_key`. */
    fun medKey(source: Source, ext: String, name: String): String =
        "${if (source == Source.AIFA) "aifa" else "ema"}\u001F$ext\u001F$name"

    private fun stamp(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        // Encodes (y,m,d,h,mi) so numeric comparison matches lexicographic order.
        return (((year.toLong() * 13 + month) * 32 + day) * 24 + hour) * 60 + minute
    }

    private fun addDays(year: Int, month: Int, day: Int, offset: Int): Triple<Int, Int, Int> {
        val d = LocalDate.of(year, month, day).plusDays(offset.toLong())
        return Triple(d.year, d.monthValue, d.dayOfMonth)
    }

    fun doseIsDue(occ: Occurrence, now: Now): Boolean =
        stamp(occ.year, occ.month, occ.day, occ.hour, occ.minute) <=
            stamp(now.year, now.month, now.day, now.hour, now.minute)

    private data class MedId(val source: Source, val ext: String, val name: String)

    private fun medicineIds(schedules: List<Schedule>): Map<String, MedId> {
        val ids = LinkedHashMap<String, MedId>()
        for (sch in schedules) {
            val key = medKey(sch.medSource, sch.medExtId, sch.medName)
            ids.getOrPut(key) { MedId(sch.medSource, sch.medExtId, sch.medName) }
        }
        return ids
    }

    fun forecastRunouts(
        repo: ScheduleQuery,
        dosesAvailable: DosesAvailable,
        now: Now,
    ): Map<String, RunOut> {
        val runout = HashMap<String, RunOut>()
        val schedules = repo.list(false)
        if (schedules.isEmpty()) return runout

        val meds = medicineIds(schedules)
        val remaining = HashMap<String, Int>()
        val resolved = HashMap<String, Boolean>()
        for ((key, id) in meds) {
            remaining[key] = dosesAvailable(id.source, id.ext, id.name)
            resolved[key] = false
        }

        val horizonDays = 366 // look ahead at most one year
        var unresolved = meds.size
        var offset = 0
        while (offset <= horizonDays && unresolved > 0) {
            val (yy, mm, dd) = addDays(now.year, now.month, now.day, offset)
            for (occ in repo.occurrencesOn(yy, mm, dd)) {
                val key = medKey(occ.medSource, occ.medExtId, occ.medName)
                val rem = remaining[key]
                if (rem == null || resolved[key] == true) continue
                if (occ.status == "taken") continue // stock already reflects it
                if (doseIsDue(occ, now)) continue // a past dose won't consume future stock
                if (rem <= 0) {
                    runout[key] = RunOut(occ.year, occ.month, occ.day, occ.keyIso)
                    resolved[key] = true
                    unresolved--
                } else {
                    remaining[key] = rem - 1 // this upcoming dose is covered by stock
                }
            }
            offset++
        }
        return runout
    }

    fun refillForecast(
        repo: ScheduleQuery,
        dosesAvailable: DosesAvailable,
        now: Now,
    ): List<RefillForecast> {
        val runout = forecastRunouts(repo, dosesAvailable, now)
        val meds = medicineIds(repo.list(false))
        val out = ArrayList<RefillForecast>(runout.size)
        for ((key, ro) in runout) {
            val id = meds[key] ?: continue
            out.add(
                RefillForecast(
                    id.source,
                    id.ext,
                    id.name,
                    ro.year,
                    ro.month,
                    ro.day,
                    ro.keyIso,
                    dosesAvailable(id.source, id.ext, id.name),
                )
            )
        }
        out.sortBy { stamp(it.year, it.month, it.day, 0, 0) }
        return out
    }

    private fun tallyDay(
        repo: ScheduleQuery,
        year: Int,
        month: Int,
        day: Int,
        now: Now,
        onlyKey: String?,
    ): Pair<Int, Int> {
        var scheduled = 0
        var taken = 0
        for (occ in repo.occurrencesOn(year, month, day)) {
            if (!doseIsDue(occ, now)) continue
            if (onlyKey != null &&
                medKey(occ.medSource, occ.medExtId, occ.medName) != onlyKey
            ) {
                continue
            }
            scheduled++
            if (occ.status == "taken") taken++
        }
        return scheduled to taken
    }

    private fun finalize(scheduled: Int, taken: Int): AdherenceStats =
        AdherenceStats(
            scheduled = scheduled,
            taken = taken,
            missed = scheduled - taken,
            rate = if (scheduled > 0) taken.toDouble() / scheduled else 0.0,
        )

    fun adherence(repo: ScheduleQuery, windowDays: Int, now: Now): AdherenceStats {
        var scheduled = 0
        var taken = 0
        for (offset in 0 until windowDays) {
            val (yy, mm, dd) = addDays(now.year, now.month, now.day, -offset)
            val (s, t) = tallyDay(repo, yy, mm, dd, now, null)
            scheduled += s
            taken += t
        }
        return finalize(scheduled, taken)
    }

    fun adherenceByMedicine(
        repo: ScheduleQuery,
        windowDays: Int,
        now: Now,
    ): List<Pair<String, AdherenceStats>> {
        // med_key -> (display name, scheduled, taken)
        val byMed = sortedMapOf<String, Triple<String, Int, Int>>()
        for (offset in 0 until windowDays) {
            val (yy, mm, dd) = addDays(now.year, now.month, now.day, -offset)
            for (occ in repo.occurrencesOn(yy, mm, dd)) {
                if (!doseIsDue(occ, now)) continue
                val key = medKey(occ.medSource, occ.medExtId, occ.medName)
                val cur = byMed[key] ?: Triple(occ.medName, 0, 0)
                byMed[key] = Triple(
                    occ.medName,
                    cur.second + 1,
                    cur.third + if (occ.status == "taken") 1 else 0,
                )
            }
        }
        return byMed.values
            .map { it.first to finalize(it.second, it.third) }
            .sortedBy { it.first }
    }

    fun dailyAdherence(repo: ScheduleQuery, windowDays: Int, now: Now): List<DayAdherence> {
        val out = ArrayList<DayAdherence>(windowDays)
        for (offset in windowDays - 1 downTo 0) {
            val (yy, mm, dd) = addDays(now.year, now.month, now.day, -offset)
            val (s, t) = tallyDay(repo, yy, mm, dd, now, null)
            out.add(DayAdherence(yy, mm, dd, s, t))
        }
        return out
    }

    fun currentStreak(repo: ScheduleQuery, now: Now): Int {
        val maxLookback = 400
        var streak = 0
        for (offset in 0 until maxLookback) {
            val (yy, mm, dd) = addDays(now.year, now.month, now.day, -offset)
            val (scheduled, taken) = tallyDay(repo, yy, mm, dd, now, null)
            if (scheduled == 0) continue // no doses that day: neutral
            if (taken == scheduled) streak++ else break
        }
        return streak
    }
}
