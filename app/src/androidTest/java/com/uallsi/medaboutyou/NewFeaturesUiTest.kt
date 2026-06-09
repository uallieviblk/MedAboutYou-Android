// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import android.Manifest
import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * End-to-end UI test for the features added in the navigation/cleanup pass:
 *   - schedule notes are surfaced on the Schedules page;
 *   - the calendar agenda exposes a per-dose edit dialog (retime / window /
 *     scope) that can skip a single occurrence via `editSingle`.
 *
 * Runs against the real persistent DB; run with clean state
 * (`adb shell pm clear com.uallsi.medaboutyou`).
 */
@RunWith(AndroidJUnit4::class)
class NewFeaturesUiTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MedApp>()

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
    fun notes_shown_and_single_dose_skip_via_edit_dialog() {
        val today = LocalDate.now().toString()
        // A daily Aspirin therapy with a note; the 00:00 dose today is in the past.
        runBlocking {
            app.container.schedules.create(
                Schedule(
                    medSource = Source.EMA,
                    medExtId = "",
                    medName = "Aspirin",
                    startDate = today,
                    endMode = EndMode.NEVER,
                    periodUnit = PeriodUnit.DAYS,
                    periodN = 1,
                    times = listOf(DoseTime(hour = 0, minute = 0)),
                    windowMinutes = 30,
                    notes = "Take with food",
                ),
            )
            app.container.medicines.setDoses(Source.EMA, "", "Aspirin", 20)
        }

        ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()

        // --- Schedules page surfaces the note (previously write-only). ---
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Schedules").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Schedules").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Take with food").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Take with food").assertIsDisplayed()

        // --- Calendar agenda → per-dose edit dialog. ---
        composeRule.onNodeWithText("Calendar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithContentDescription("Edit dose").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithContentDescription("Edit dose").onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        // The dialog offers a scope choice and a skip toggle.
        composeRule.onNodeWithText("This and following").assertIsDisplayed()
        composeRule.onNodeWithText("Skip this dose").assertIsDisplayed()

        // Skip this dose, save → today's only occurrence disappears from the agenda.
        composeRule.onNodeWithText("Skip this dose").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithContentDescription("Edit dose").fetchSemanticsNodes().isEmpty()
        }
    }
}
