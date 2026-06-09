// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.uallsi.medaboutyou.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-arms the reminder scan after a reboot when "Start at boot" is on — the
 * Android analogue of the desktop "Start at login" autostart entry.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = AppContainer(appContext)
                val startAtBoot = container.settings.startAtBootFlow.first()
                val remindersOn = container.settings.remindersEnabledFlow.first()
                if (startAtBoot && remindersOn) Reminders.enable(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
