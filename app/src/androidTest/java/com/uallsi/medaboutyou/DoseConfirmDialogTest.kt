// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * Verifies the dose-toggle confirmation dialog: tapping a dose's checkbox no
 * longer marks it immediately — a confirmation must be accepted. Cancelling
 * leaves the dose (and stock) untouched; confirming records it and debits stock.
 */
@RunWith(AndroidJUnit4::class)
class DoseConfirmDialogTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MedApp>()

    @Before
    fun setUp() {
        grantNotificationPermission()
        app.clearUserData()
    }

    private fun seedDueDose() = runBlocking {
        // Dose at 00:00 today is in the past and inside its take window, so its
        // checkbox is enabled.
        app.container.schedules.create(
            Schedule(
                medSource = Source.EMA,
                medExtId = "",
                medName = "Confirmtest",
                startDate = LocalDate.now().toString(),
                endMode = EndMode.NEVER,
                periodUnit = PeriodUnit.DAYS,
                periodN = 1,
                times = listOf(DoseTime(hour = 0, minute = 0)),
                windowMinutes = 30,
            ),
        )
        app.container.medicines.setDoses(Source.EMA, "", "Confirmtest", 10)
    }

    @Test
    fun cancelling_the_confirmation_keeps_the_dose_untaken() {
        seedDueDose()
        ActivityScenario.launch(MainActivity::class.java)
        composeRule.awaitText("Confirmtest", substring = true)
        composeRule.onNodeWithText("0/1").assertIsDisplayed()

        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true)
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.awaitText("Mark taken") // the confirmation dialog is up
        composeRule.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        // Nothing changed: ring still 0/1, stock still 10.
        composeRule.onNodeWithText("0/1").assertIsDisplayed()
        runBlocking {
            assertEquals(10, app.container.medicines.availableDoses(Source.EMA, "", "Confirmtest"))
        }
    }

    @Test
    fun confirming_marks_the_dose_taken_and_debits_stock() {
        seedDueDose()
        ActivityScenario.launch(MainActivity::class.java)
        composeRule.awaitText("Confirmtest", substring = true)

        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true)
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.awaitText("Mark taken")
        composeRule.onNodeWithText("Mark taken").performSemanticsAction(SemanticsActions.OnClick)

        composeRule.awaitText("1/1")
        runBlocking {
            assertEquals(9, app.container.medicines.availableDoses(Source.EMA, "", "Confirmtest"))
        }
    }
}
