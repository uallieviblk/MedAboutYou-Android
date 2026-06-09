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
        val today = LocalDate.now().toString()
        val id = runBlocking {
            val id = app.container.schedules.create(
                Schedule(
                    medSource = Source.EMA, medExtId = "", medName = "Notiftest",
                    startDate = today, endMode = EndMode.NEVER,
                    periodUnit = PeriodUnit.DAYS, periodN = 1,
                    times = listOf(DoseTime(hour = 0, minute = 0)), // 00:00 today → already due
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
}
