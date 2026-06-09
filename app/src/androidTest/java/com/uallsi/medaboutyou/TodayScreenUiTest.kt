// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uallsi.medaboutyou.ui.today.TodayScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke UI test: the Today screen composes and shows its at-a-glance header.
 * Runs the real ViewModel against the on-device database.
 */
@RunWith(AndroidJUnit4::class)
class TodayScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MedApp>()

    @Before
    fun setUp() {
        // Start from a clean slate so the result is independent of run order.
        app.clearUserData()
    }

    @Test
    fun today_screen_shows_doses_header() {
        composeRule.setContent { TodayScreen() }
        // The header only renders once the ViewModel's async load completes
        // (until then the screen is just a spinner), so poll rather than asserting
        // straight after waitForIdle(), which doesn't await Dispatchers.IO work.
        composeRule.awaitText("Today's doses")
        composeRule.onNodeWithText("Today's doses").assertExists()
    }
}
