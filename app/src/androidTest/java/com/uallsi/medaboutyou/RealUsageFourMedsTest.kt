// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uallsi.medaboutyou.data.local.Caregiver
import com.uallsi.medaboutyou.data.local.MqttConfig
import com.uallsi.medaboutyou.domain.Insights
import com.uallsi.medaboutyou.domain.Now
import com.uallsi.medaboutyou.model.DoseTime
import com.uallsi.medaboutyou.model.EndMode
import com.uallsi.medaboutyou.model.PeriodUnit
import com.uallsi.medaboutyou.model.Schedule
import com.uallsi.medaboutyou.model.Source
import com.uallsi.medaboutyou.reminders.AlertEngine
import com.uallsi.medaboutyou.reminders.MqttAlerts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Real-user simulation against the real (persistent) DB + the live [AlertEngine]:
 * a patient on **four once-daily medicines**, each with a **30-minute take
 * window** and a caregiver **"carer"** alerted over MQTT.
 *
 * "Accelerated time": a **7-day** course is replayed in one run — the prior 7
 * days are all taken (no waiting), and today's doses are scheduled 20 minutes ago
 * so they're overdue, still inside their window, and past the 15-minute caregiver
 * escalation — which makes [AlertEngine] enqueue one MQTT caregiver alert per med
 * to `medaboutyou/caregiver/carer`.
 *
 * Run with clean state (`adb shell pm clear com.uallsi.medaboutyou`).
 */
@RunWith(AndroidJUnit4::class)
class RealUsageFourMedsTest {

    private val app get() = ApplicationProvider.getApplicationContext<MedApp>()

    private val meds = listOf("Metformina", "Amlodipina", "Atorvastatina", "Levotiroxina")

    @Before
    fun setUp() {
        grantNotificationPermission()
        app.clearUserData()
        app.getSystemService(NotificationManager::class.java).cancelAll()
        // clearUserData() doesn't touch the transient mqtt_outbox — wipe it so the
        // caregiver-alert count is this test's alone.
        runBlocking {
            val dao = app.container.db.mqttOutboxDao()
            dao.pending().forEach { dao.delete(it.id) }
        }
    }

    @Test
    fun four_meds_once_daily_30min_window_caregiver_carer_7_days_accelerated() = runBlocking {
        val schedules = app.container.schedules
        val medicines = app.container.medicines
        val settings = app.container.settings

        // Caregiver "carer" + MQTT alerts (broker config defaults; enabled).
        settings.setUserName("Patient")
        settings.setCaregivers(listOf(Caregiver(name = "Carer", username = "carer")))
        settings.setRemindersEnabled(true)
        settings.setMqttConfig(MqttConfig()) // defaults: enabled, medaboutyou/caregiver

        // Today's dose is scheduled 20 min ago: overdue, still inside its 30-min
        // window, and past the 15-min caregiver escalation.
        val sched = LocalDateTime.now().minusMinutes(20)
        val start = LocalDate.now().minusDays(DAYS.toLong())
        meds.forEach { name ->
            schedules.create(
                Schedule(
                    medSource = Source.EMA,
                    medExtId = "",
                    medName = name,
                    startDate = start.toString(),
                    endMode = EndMode.NEVER,
                    periodUnit = PeriodUnit.DAYS,
                    periodN = 1,
                    times = listOf(DoseTime(hour = sched.hour, minute = sched.minute)),
                    windowMinutes = 30,
                    caregiverAlertMin = 15,
                ),
            )
            medicines.setDoses(Source.EMA, "", name, 30)
        }

        // Each med fires exactly one dose per day.
        val sampleDay = start.plusDays(2)
        assertEquals(
            "one administration per med on a sample day",
            meds.toSet(),
            schedules.snapshot()
                .occurrencesOn(sampleDay.year, sampleDay.monthValue, sampleDay.dayOfMonth)
                .map { it.medName }.toSet(),
        )

        // --- Accelerated time: replay the previous 7 days, taking every dose. ---
        for (back in DAYS downTo 1) {
            val d = LocalDate.now().minusDays(back.toLong())
            schedules.snapshot().occurrencesOn(d.year, d.monthValue, d.dayOfMonth)
                .filter { it.status.isEmpty() }
                .forEach { occ ->
                    schedules.logDose(occ.scheduleId, occ.keyIso, "taken")
                    medicines.adjustDoses(occ.medSource, occ.medExtId, occ.medName, -1)
                }
        }

        // Stock debited one unit per taken dose: 30 − 7.
        meds.forEach { name ->
            assertEquals("stock for $name", 30 - DAYS, medicines.availableDoses(Source.EMA, "", name))
        }

        // --- Adherence/streak over the replayed week (as of end of yesterday, so
        //     today's still-pending doses aren't counted yet). ---
        val snap = schedules.snapshot()
        val yest = LocalDate.now().minusDays(1)
        val asOfYesterday = Now(yest.year, yest.monthValue, yest.dayOfMonth, 23, 59)
        val adherence = Insights.adherence(snap, windowDays = DAYS, now = asOfYesterday)
        assertEquals(4 * DAYS, adherence.taken)
        assertEquals(0, adherence.missed)
        assertEquals(1.0, adherence.rate, 1e-9)
        assertEquals(DAYS, Insights.currentStreak(snap, asOfYesterday))

        // --- Caregiver alerts: today's 4 overdue doses each enqueue one MQTT
        //     alert to carer's topic. ---
        AlertEngine.runOnce(app)

        val outbox = app.container.db.mqttOutboxDao().pending()
        assertEquals("one caregiver alert per overdue med", meds.size, outbox.size)
        assertTrue(
            "all alerts target carer's topic; got ${outbox.map { it.topic }}",
            outbox.all { it.topic == "medaboutyou/caregiver/carer" },
        )
        assertTrue(
            "all alerts use the medication-overdue category",
            outbox.all { it.category == MqttAlerts.CATEGORY_MEDICATION_OVERDUE },
        )
        assertTrue("all alerts carry the patient name", outbox.all { it.user == "Patient" })
        assertEquals(
            "each med produced one alert",
            meds.toSet(),
            outbox.map { row -> meds.first { row.text.contains(it) } }.toSet(),
        )
    }

    private companion object {
        const val DAYS = 7
    }
}
