// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fired by the exact alarm: runs one [AlertEngine] pass and schedules the next
 * exact alarm at the time the engine reports (the next dose / repeat / caregiver
 * escalation). This is what gives on-the-minute reminders.
 */
class DoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val next = AlertEngine.runOnce(appContext)
                if (next != null) DoseAlarms.scheduleAt(appContext, next)
            } finally {
                pending.finish()
            }
        }
    }
}
