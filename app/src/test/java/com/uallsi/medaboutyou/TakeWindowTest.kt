// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import com.uallsi.medaboutyou.model.ScheduleEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Covers the early-take rule (`ScheduleEngine.isWithinTakeWindow`): a dose is
 * checkable once now ≥ scheduled − windowMinutes, so it can be marked taken from
 * the opening of its intake window rather than only after the exact minute.
 */
class TakeWindowTest {

    private fun checkable(dt: LocalDateTime, window: Int) =
        ScheduleEngine.isWithinTakeWindow(dt.year, dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute, window)

    @Test
    fun dose_inside_the_lead_window_is_checkable() {
        // Scheduled 10 min from now, window 30 → window opened 20 min ago.
        assertTrue(checkable(LocalDateTime.now().plusMinutes(10), window = 30))
    }

    @Test
    fun dose_before_its_window_opens_is_not_checkable() {
        // Scheduled 2 h from now, window 30 → opens in 90 min.
        assertFalse(checkable(LocalDateTime.now().plusMinutes(120), window = 30))
    }

    @Test
    fun past_dose_is_always_checkable() {
        assertTrue(checkable(LocalDateTime.now().minusHours(1), window = 30))
    }

    @Test
    fun zero_window_behaves_like_scheduled_time() {
        assertTrue(checkable(LocalDateTime.now().minusMinutes(1), window = 0))
        assertFalse(checkable(LocalDateTime.now().plusMinutes(5), window = 0))
    }
}
