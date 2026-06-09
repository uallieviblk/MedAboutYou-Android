// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uallsi.medaboutyou.model.Medicine
import com.uallsi.medaboutyou.model.Source
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reproduces and verifies the "refill stock" flow end-to-end:
 *   Search → open a medicine record → Set stock → schedule it →
 *   the schedule's agenda shows that same stock.
 *
 * This guards the bug where a scheduled medicine lost its extId, so stock set
 * on the record (key "ema:<extId>") never reached the schedule (key "name:…").
 * Run with a clean app state (`adb shell pm clear com.uallsi.medaboutyou`).
 */
@RunWith(AndroidJUnit4::class)
class RefillFlowTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MedApp>()

    @Before
    fun setUp() {
        grantNotificationPermission()
        // Clear the shared DB (and reset the source to EMA) so Search opens on
        // EMA and the schedule uniqueness check is independent of run order.
        app.clearUserData()
    }

    @Test
    fun refill_stock_reaches_schedule() {
        // A medicine exists in the cached EMA dataset so Search can find it.
        runBlocking {
            app.container.medicines.upsertAll(
                listOf(
                    Medicine(
                        source = Source.EMA,
                        extId = "EMEA/TEST/1",
                        productNumber = "EMEA/TEST/1",
                        category = "Human",
                        name = "Testmedicine",
                        inn = "testsubstance",
                        status = "Authorised",
                    ),
                ),
            )
        }

        ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()
        // Open Search from the Schedules "Add medicine" button (Search is no
        // longer a bottom-nav tab after the redesign).
        composeRule.awaitText("Schedules")
        composeRule.onNodeWithText("Schedules").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.awaitTag("addMedicineFab")
        composeRule.onNodeWithTag("addMedicineFab").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        // Search list → open the seeded record.
        composeRule.awaitText("Testmedicine", substring = true)
        composeRule.onAllNodesWithText("Testmedicine", substring = true).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        // Refill: Set stock to 50.
        composeRule.awaitText("Set stock", substring = true)
        composeRule.onNodeWithText("Set stock…").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        val stockField = composeRule.onAllNodes(hasSetTextAction()).onFirst()
        stockField.performTextClearance()
        stockField.performTextInput("50")
        composeRule.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)

        // The record now shows 50 doses, and the inventory row is keyed by extId.
        composeRule.awaitText("50 doses")
        runBlocking {
            assertEquals(50, app.container.medicines.availableDoses(Source.EMA, "EMEA/TEST/1", "Testmedicine"))
        }

        // Schedule the medicine, then confirm the agenda sees the same stock.
        composeRule.onNodeWithText("Add to my medication schedule", substring = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Add to calendar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        // The schedule carries the extId, so its agenda row reports "50 in stock".
        composeRule.awaitText("50 in stock", substring = true)
        runBlocking {
            val sch = app.container.schedules.list(false).single { it.medName == "Testmedicine" }
            assertEquals("EMEA/TEST/1", sch.medExtId)
        }
    }
}
