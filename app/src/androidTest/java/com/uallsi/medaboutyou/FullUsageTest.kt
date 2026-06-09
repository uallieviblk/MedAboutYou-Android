package com.uallsi.medaboutyou

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import android.Manifest
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uallsi.medaboutyou.model.DoseTime
import com.uallsi.medaboutyou.model.EndMode
import com.uallsi.medaboutyou.model.PeriodUnit
import com.uallsi.medaboutyou.model.Schedule
import com.uallsi.medaboutyou.model.Source
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * End-to-end test simulating real usage against the real (persistent) Room
 * database and the full navigation graph:
 *   seed a prescription → see today's dose → mark it taken (stock debits,
 *   adherence ring fills) → check Insights → check the Schedules page lists it.
 *
 * Run with a clean app state (`adb shell pm clear com.uallsi.medaboutyou`).
 */
@RunWith(AndroidJUnit4::class)
class FullUsageTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MedApp>()

    /**
     * Pre-grant the notification permission so the startup runtime-permission
     * dialog doesn't cover MainActivity and break the Compose hierarchy.
     */
    @Before
    fun grantNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val inst = InstrumentationRegistry.getInstrumentation()
            inst.uiAutomation.grantRuntimePermission(
                inst.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    @Test
    fun real_usage_flow() {
        val today = LocalDate.now().toString()

        // --- A user already has a daily Metformin prescription with 10 doses. ---
        // Dose at 00:00 today is in the past, so it is immediately loggable.
        runBlocking {
            app.container.schedules.create(
                Schedule(
                    medSource = Source.EMA,
                    medExtId = "",
                    medName = "Metformin",
                    startDate = today,
                    endMode = EndMode.NEVER,
                    periodUnit = PeriodUnit.DAYS,
                    periodN = 1,
                    times = listOf(DoseTime(hour = 0, minute = 0)),
                    windowMinutes = 30,
                ),
            )
            app.container.medicines.setDoses(Source.EMA, "", "Metformin", 10)
        }

        ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()

        // --- Today shows the due dose and an empty adherence ring (0 of 1). ---
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Metformin", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("0/1").assertIsDisplayed()

        // --- Mark the dose taken via its checkbox (unmerged tree → the Checkbox node). ---
        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true)
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)

        // Adherence ring fills to 1 of 1; stock debited 10 -> 9.
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("1/1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("1/1").assertIsDisplayed()
        runBlocking {
            assertEquals(9, app.container.medicines.availableDoses(Source.EMA, "", "Metformin"))
        }

        // --- Insights reflects the activity. ---
        composeRule.onNodeWithText("Insights").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Next refill").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Last 30 days").assertIsDisplayed()

        // --- The Schedules page lists the active prescription. ---
        composeRule.onNodeWithText("Schedules").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Active schedules").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Metformin", substring = true)
            .fetchSemanticsNodes().isNotEmpty().let { require(it) }
        runBlocking {
            val names = app.container.schedules.list(false).map { it.medName }.toSet()
            assertEquals(setOf("Metformin"), names)
        }
    }
}
