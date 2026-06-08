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

    fun ensureChannel(context: Context) {
        // minSdk 26, so notification channels are always available.
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Dose reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Reminds you when a scheduled dose is due" }
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
            .setContentTitle("Time for $medName")
            .setContentText("Dose due at $time")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .addAction(action(ACTION_TAKE, "Mark taken", 1000))
            .addAction(action(ACTION_SKIP, "Skip", 2000))
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
}
