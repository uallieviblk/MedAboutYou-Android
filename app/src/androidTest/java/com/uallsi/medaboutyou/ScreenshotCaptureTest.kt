// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uallsi.medaboutyou.data.local.ActionCatalog
import com.uallsi.medaboutyou.data.local.Caregiver
import com.uallsi.medaboutyou.data.local.MqttConfig
import com.uallsi.medaboutyou.model.DoseTime
import com.uallsi.medaboutyou.model.EndMode
import com.uallsi.medaboutyou.model.PeriodUnit
import com.uallsi.medaboutyou.model.Schedule
import com.uallsi.medaboutyou.model.Source
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Utility (not an assertion test): drives the app and writes PNG screenshots to
 * the app's external files dir for the README / website. Run it explicitly:
 *   adb shell am instrument -w -e class com.uallsi.medaboutyou.ScreenshotCaptureTest \
 *     com.uallsi.medaboutyou.test/androidx.test.runner.AndroidJUnitRunner
 *   adb pull /sdcard/Android/data/com.uallsi.medaboutyou/files/shots
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotCaptureTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MedApp>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        grantNotificationPermission()
        app.clearUserData()
    }

    private fun shot(name: String) {
        composeRule.waitForIdle()
        Thread.sleep(600) // let ripples/dialog transitions settle
        val bmp = inst.uiAutomation.takeScreenshot()
        val dir = File(app.getExternalFilesDir(null), "shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    @Test
    fun capture_readme_screenshots() {
        val today = LocalDate.now().toString()
        val now = LocalDateTime.now()
        val morning = (now.hour - 1 + 24) % 24
        runBlocking {
            suspend fun mk(name: String, h: Int, m: Int) {
                app.container.schedules.create(
                    Schedule(
                        medSource = Source.EMA, medExtId = "", medName = name, startDate = today,
                        endMode = EndMode.NEVER, periodUnit = PeriodUnit.DAYS, periodN = 1,
                        times = listOf(DoseTime(hour = h, minute = m)), windowMinutes = 30, caregiverAlertMin = 15,
                    ),
                )
                app.container.medicines.setDoses(Source.EMA, "", name, 24)
            }
            mk("Metformin 500 mg", morning, 0)         // overdue (outside window)
            mk("Atorvastatin 20 mg", morning, 30)      // overdue
            mk("Vitamin D3", (now.hour + 3) % 24, 0)   // upcoming
            app.container.settings.setUserName("Alex")
            app.container.settings.setCaregivers(listOf(Caregiver("Caregiver", "carer")))
            app.container.settings.setMqttConfig(MqttConfig(host = "mqtt.example.com"))
            app.container.actionLog.log(ActionCatalog.SCHEDULE_CREATED, "Created a schedule for Metformin 500 mg")
            app.container.actionLog.log(ActionCatalog.STOCK_ADDED, "Added 24 doses to Atorvastatin 20 mg")
            app.container.actionLog.log(ActionCatalog.DOSE_TAKEN, "Marked Vitamin D3 as taken")
        }

        ActivityScenario.launch(MainActivity::class.java)
        composeRule.awaitText("Metformin", substring = true)

        // Mark-taken confirmation (this dose is overdue, so it also shows the
        // "outside the scheduled window" warning).
        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.awaitText("Mark taken")
        shot("confirm-take")
        composeRule.onNodeWithText("Mark taken").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        // Un-mark warning (tap the now-taken dose).
        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.awaitText("Unmark")
        shot("untake-warning")
        composeRule.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        // Today (one dose taken → ring shows progress).
        shot("today")

        composeRule.onNodeWithText("Calendar").performSemanticsAction(SemanticsActions.OnClick)
        shot("calendar")

        composeRule.onNodeWithText("Schedules").performSemanticsAction(SemanticsActions.OnClick)
        shot("schedules")

        // Activity log (overflow → Activity log).
        composeRule.onNodeWithContentDescription("More options").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.awaitText("Activity log")
        composeRule.onNodeWithText("Activity log").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.awaitText("Created a schedule", substring = true)
        shot("activity-log")
        composeRule.onNodeWithText("Today").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        // Settings → scroll to the MQTT broker section.
        composeRule.onNodeWithContentDescription("More options").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.awaitText("Settings")
        composeRule.onNodeWithText("Settings").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.awaitText("Broker host")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Broker host"))
        shot("settings-mqtt")
    }
}
