package com.uallsi.medaboutyou

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uallsi.medaboutyou.ui.today.TodayScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke UI test: the Today screen composes and shows its at-a-glance header.
 * Runs the real ViewModel against the on-device database (empty by default).
 */
@RunWith(AndroidJUnit4::class)
class TodayScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun today_screen_shows_doses_header() {
        composeRule.setContent { TodayScreen() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Today's doses").assertExists()
    }
}
