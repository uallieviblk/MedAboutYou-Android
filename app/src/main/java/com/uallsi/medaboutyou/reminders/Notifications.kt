// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.uallsi.medaboutyou.MainActivity
import com.uallsi.medaboutyou.R

/** Dose-reminder notifications — Android port of the desktop notification flow. */
object Notifications {
    const val CHANNEL_ID = "dose_reminders"
    const val EXTRA_PAYLOAD = "payload"     // "<scheduleId><keyIso>"
    const val ACTION_TAKE = "com.uallsi.medaboutyou.TAKE"
    const val ACTION_SKIP = "com.uallsi.medaboutyou.SKIP"
    const val ACTION_SHOPPING = "com.uallsi.medaboutyou.ADD_SHOPPING"
    const val REFILL_CHANNEL_ID = "refill_reminders"
    const val EXTRA_MED_KEY = "med_key"
    const val EXTRA_MED_NAME = "med_name"

    fun ensureChannel(context: Context) {
        // minSdk 26, so notification channels are always available.
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_desc) }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun notificationId(keyIso: String): Int = ("dose:$keyIso").hashCode()

    fun show(context: Context, scheduleId: Long, keyIso: String, medName: String, time: String) {
        ensureChannel(context)
        val payload = "$scheduleId$keyIso"

        fun action(act: String, label: String, requestBase: Int): NotificationCompat.Action {
            val intent = Intent(context, DoseActionReceiver::class.java).apply {
                action = act
                putExtra(EXTRA_PAYLOAD, payload)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                requestBase + keyIso.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Action(0, label, pi)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("open", "calendar")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            context, keyIso.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_title, medName))
            .setContentText(context.getString(R.string.notif_text, time))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .addAction(action(ACTION_TAKE, context.getString(R.string.mark_taken), 1000))
            .addAction(action(ACTION_SKIP, context.getString(R.string.skip), 2000))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId(keyIso), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — silently skip.
        }
    }

    fun withdraw(context: Context, keyIso: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(keyIso))
    }

    // --- Refill reminders (separate, lower-priority channel) ---

    fun ensureRefillChannel(context: Context) {
        val channel = NotificationChannel(
            REFILL_CHANNEL_ID,
            context.getString(R.string.channel_refill_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_refill_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun refillNotificationId(medKey: String): Int = ("refill:$medKey").hashCode()

    /** Notify that [medName] is running low, with an "Add to shopping list" action. */
    fun showRefill(context: Context, medKey: String, medName: String, runOutDate: String) {
        ensureRefillChannel(context)
        val shopIntent = Intent(context, DoseActionReceiver::class.java).apply {
            action = ACTION_SHOPPING
            putExtra(EXTRA_MED_KEY, medKey)
            putExtra(EXTRA_MED_NAME, medName)
        }
        val shopPi = PendingIntent.getBroadcast(
            context, 3000 + medKey.hashCode(), shopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openPi = PendingIntent.getActivity(
            context, 4000 + medKey.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, REFILL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.refill_notif_title, medName))
            .setContentText(context.getString(R.string.refill_notif_text, runOutDate))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .addAction(NotificationCompat.Action(0, context.getString(R.string.add_to_shopping), shopPi))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(refillNotificationId(medKey), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — silently skip.
        }
    }

    fun withdrawRefill(context: Context, medKey: String) {
        NotificationManagerCompat.from(context).cancel(refillNotificationId(medKey))
    }
}
