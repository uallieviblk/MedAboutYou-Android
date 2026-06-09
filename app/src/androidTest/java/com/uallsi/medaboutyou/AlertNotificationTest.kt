// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uallsi.medaboutyou.model.DoseTime
import com.uallsi.medaboutyou.model.EndMode
import com.uallsi.medaboutyou.model.PeriodUnit
import com.uallsi.medaboutyou.model.Schedule
import com.uallsi.medaboutyou.model.Source
import com.uallsi.medaboutyou.reminders.AlertEngine
import com.uallsi.medaboutyou.reminders.DoseActionReceiver
import com.uallsi.medaboutyou.reminders.Notifications
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Verifies the alert → notification pipeline end-to-end on a device:
 *   - a due, unlogged dose makes [AlertEngine] post a dose reminder;
 *   - a low-stock medicine makes it post a refill reminder;
 *   - the "Take" notification action logs the dose and withdraws the reminder.
 *
 * Runs against the real DB and the real NotificationManager; run with clean
 * state (`adb shell pm clear com.uallsi.medaboutyou`).
 */
@RunWith(AndroidJUnit4::class)
class AlertNotificationTest {

    private val app get() = ApplicationProvider.getApplicationContext<MedApp>()
    private val nm get() = app.getSystemService(NotificationManager::class.java)

    @Before
    fun reset() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val inst = InstrumentationRegistry.getInstrumentation()
            inst.uiAutomation.grantRuntimePermission(
                inst.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        // Wipe user data so each test is isolated (real persistent DB).
        runBlocking {
            val dao = app.container.db.backupDao()
            dao.clearDoseAlerts(); dao.clearOverrides(); dao.clearDoseLogs()
            dao.clearInventory(); dao.clearSchedules()
            app.container.shopping.all().forEach { app.container.shopping.remove(it.medKey) }
            app.container.medicines.setMeta("refill_scan_date", "") // reset the daily refill-scan guard
        }
        nm.cancelAll()
    }

    private fun activeTitles(): List<String> =
        nm.activeNotifications.mapNotNull { it.notification.extras.getString(Notification.EXTRA_TITLE) }

    private fun waitForTitle(timeoutMs: Long = 8_000, match: (String) -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (activeTitles().any(match)) return true
            Thread.sleep(150)
        }
        return false
    }

    @Test
    fun due_dose_posts_a_reminder_and_take_action_logs_it() {
        val now = java.time.LocalDateTime.now()
        val today = now.toLocalDate().toString()
        val id = runBlocking {
            val id = app.container.schedules.create(
                Schedule(
                    medSource = Source.EMA, medExtId = "", medName = "Notiftest",
                    startDate = today, endMode = EndMode.NEVER,
                    periodUnit = PeriodUnit.DAYS, periodN = 1,
                    // Due right now and within its 30-min window (a dose well past
                    // its window is "missed" and intentionally posts no reminder).
                    times = listOf(DoseTime(hour = now.hour, minute = now.minute)),
                    windowMinutes = 30,
                ),
            )
            app.container.medicines.setDoses(Source.EMA, "", "Notiftest", 10)
            id
        }

        runBlocking { AlertEngine.runOnce(app) }

        // A dose reminder ("Time for Notiftest") must be posted.
        assertTrue(
            "expected a dose-reminder notification; active=${activeTitles()}",
            waitForTitle { it.contains("Notiftest") },
        )

        // Tap "Take": broadcast the action and confirm it logs the dose and clears the notification.
        val keyIso = runBlocking {
            app.container.schedules.snapshot()
                .occurrencesOn(LocalDate.now().year, LocalDate.now().monthValue, LocalDate.now().dayOfMonth)
                .first().keyIso
        }
        app.sendBroadcast(
            Intent(app, DoseActionReceiver::class.java).apply {
                action = Notifications.ACTION_TAKE
                putExtra(Notifications.EXTRA_PAYLOAD, "$id$keyIso")
            },
        )

        val taken = run {
            val deadline = System.currentTimeMillis() + 8_000
            var ok = false
            while (System.currentTimeMillis() < deadline && !ok) {
                ok = runBlocking {
                    app.container.schedules.snapshot()
                        .occurrencesOn(LocalDate.now().year, LocalDate.now().monthValue, LocalDate.now().dayOfMonth)
                        .firstOrNull()?.status == "taken"
                }
                if (!ok) Thread.sleep(150)
            }
            ok
        }
        assertTrue("Take action should log the dose as taken", taken)
        // …and the reminder should be withdrawn.
        assertTrue(
            "reminder should be withdrawn after Take",
            run {
                val deadline = System.currentTimeMillis() + 5_000
                while (System.currentTimeMillis() < deadline) {
                    if (activeTitles().none { it.contains("Notiftest") }) return@run true
                    Thread.sleep(150)
                }
                false
            },
        )
    }

    @Test
    fun low_stock_posts_a_refill_reminder() {
        val today = LocalDate.now().toString()
        runBlocking {
            app.container.schedules.create(
                Schedule(
                    medSource = Source.EMA, medExtId = "", medName = "Refilltest",
                    startDate = today, endMode = EndMode.NEVER,
                    periodUnit = PeriodUnit.DAYS, periodN = 1,
                    times = listOf(DoseTime(hour = 23, minute = 59)), // future today → no dose reminder now
                    windowMinutes = 30,
                ),
            )
            app.container.medicines.setDoses(Source.EMA, "", "Refilltest", 2) // runs out in ~2 days
        }

        runBlocking { AlertEngine.runOnce(app) }

        assertTrue(
            "expected a refill notification; active=${activeTitles()}",
            waitForTitle { it.contains("Refilltest") },
        )
    }

    @Test
    fun hourly_schedule_does_not_flood_notifications() {
        // An hourly schedule has ~24 doses/day; only the one currently inside its
        // window should ever have a live reminder — never one per past hour.
        runBlocking {
            app.container.schedules.create(
                Schedule(
                    medSource = Source.EMA, medExtId = "", medName = "Hourlytest",
                    startDate = LocalDate.now().toString(), endMode = EndMode.NEVER,
                    periodUnit = PeriodUnit.HOURS, periodN = 1,
                    times = listOf(DoseTime(minute = 0)), // every hour at :00
                    windowMinutes = 30,
                ),
            )
            app.container.medicines.setDoses(Source.EMA, "", "Hourlytest", 50)
        }

        runBlocking { AlertEngine.runOnce(app) }
        Thread.sleep(1500) // let any notification post

        // Count only dose reminders ("Time for …"); a refill alert ("Running low: …")
        // for the same medicine is a separate, legitimate notification.
        val doseReminders = activeTitles().count { it.startsWith("Time for") && it.contains("Hourlytest") }
        assertTrue(
            "an hourly schedule must post at most one dose reminder, got $doseReminders (active=${activeTitles()})",
            doseReminders <= 1,
        )
    }

    @Test
    fun every_scheduling_type_posts_a_due_reminder() {
        val now = java.time.LocalDateTime.now()
        val today = now.toLocalDate().toString()

        // One schedule per PeriodUnit, each with a dose landing today at "now"
        // (so it's inside its 30-min window) and huge stock (no refill noise).
        data class Case(val name: String, val unit: PeriodUnit, val times: List<DoseTime>)
        val cases = listOf(
            Case("OnceT", PeriodUnit.ONCE, listOf(DoseTime(year = now.year, month = now.monthValue, dayOfMonth = now.dayOfMonth, hour = now.hour, minute = now.minute))),
            Case("HoursT", PeriodUnit.HOURS, listOf(DoseTime(minute = now.minute))),
            Case("DaysT", PeriodUnit.DAYS, listOf(DoseTime(hour = now.hour, minute = now.minute))),
            Case("WeeksT", PeriodUnit.WEEKS, listOf(DoseTime(weekday = now.dayOfWeek.value, hour = now.hour, minute = now.minute))),
            Case("MonthsT", PeriodUnit.MONTHS, listOf(DoseTime(dayOfMonth = now.dayOfMonth, hour = now.hour, minute = now.minute))),
            Case("YearsT", PeriodUnit.YEARS, listOf(DoseTime(month = now.monthValue, dayOfMonth = now.dayOfMonth, hour = now.hour, minute = now.minute))),
        )
        runBlocking {
            cases.forEach { c ->
                app.container.schedules.create(
                    Schedule(
                        medSource = Source.EMA, medExtId = "", medName = c.name,
                        startDate = today, endMode = EndMode.NEVER,
                        periodUnit = c.unit, periodN = 1, times = c.times, windowMinutes = 30,
                    ),
                )
                app.container.medicines.setDoses(Source.EMA, "", c.name, 9999)
            }
        }

        runBlocking { AlertEngine.runOnce(app) }
        Thread.sleep(2000)

        val titles = activeTitles()
        cases.forEach { c ->
            assertTrue(
                "expected a due reminder for ${c.unit} (${c.name}); active=$titles",
                titles.any { it == "Time for ${c.name}" },
            )
        }
        // Exactly one dose reminder per schedule — no high-frequency flooding.
        assertEquals("one dose reminder per scheduling type", cases.size, titles.count { it.startsWith("Time for") })
    }
}
