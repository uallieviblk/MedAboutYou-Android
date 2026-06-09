// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.model.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the "Mark taken" / "Skip" notification buttons — the Android port of
 * `ReminderScheduler::log_from_action`. Logs the dose, adjusts stock (taken =
 * −1), and withdraws the notification.
 */
class DoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appCtx0 = context.applicationContext

        // Refill alert: "Add to shopping list".
        if (intent.action == Notifications.ACTION_SHOPPING) {
            val medKey = intent.getStringExtra(Notifications.EXTRA_MED_KEY) ?: return
            val medName = intent.getStringExtra(Notifications.EXTRA_MED_NAME) ?: return
            val pendingShop = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AppContainer(appCtx0).shopping.add(medKey, medName)
                    Notifications.withdrawRefill(appCtx0, medKey)
                } finally {
                    pendingShop.finish()
                }
            }
            return
        }

        val payload = intent.getStringExtra(Notifications.EXTRA_PAYLOAD) ?: return
        val parts = payload.split('\u001F')
        if (parts.size != 2) return
        val scheduleId = parts[0].toLongOrNull() ?: return
        val keyIso = parts[1]
        val status = if (intent.action == Notifications.ACTION_TAKE) "taken" else "untaken"

        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = AppContainer(appContext)
                val schedule = container.schedules.get(scheduleId)
                container.schedules.logDose(scheduleId, keyIso, status)
                if (schedule != null) {
                    val delta = if (status == "taken") -1 else 1
                    container.medicines.adjustDoses(
                        schedule.medSource, schedule.medExtId, schedule.medName, delta,
                    )
                }
                Notifications.withdraw(appContext, keyIso)
                // Re-evaluate so a taken dose stops repeating immediately.
                DoseAlarms.kickNow(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
