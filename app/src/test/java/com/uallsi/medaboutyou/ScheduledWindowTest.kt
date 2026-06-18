// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import com.uallsi.medaboutyou.model.ScheduleEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Guards `isWithinScheduledWindow` — the "taken outside its window" check. */
class ScheduledWindowTest {

    private fun within(dt: LocalDateTime, window: Int) =
        ScheduleEngine.isWithinScheduledWindow(dt.year, dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute, window)

    @Test
    fun `a dose scheduled now is within its window`() {
        assertTrue(within(LocalDateTime.now(), 30))
    }

    @Test
    fun `a dose slightly late but inside the window is within`() {
        assertTrue(within(LocalDateTime.now().minusMinutes(5), 30))
    }

    @Test
    fun `a dose past the end of its window is outside`() {
        assertFalse(within(LocalDateTime.now().minusMinutes(45), 30))
    }

    @Test
    fun `a dose hours in the past is outside its window`() {
        assertFalse(within(LocalDateTime.now().minusHours(3), 30))
    }
}
