package com.uallsi.medaboutyou.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules a single **exact** alarm that drives [AlertEngine]. The receiver
 * re-arms the next one each time it fires, so dose reminders land on the minute
 * instead of being floored to WorkManager's 15-minute period.
 */
object DoseAlarms {
    const val ACTION_FIRE = "com.uallsi.medaboutyou.ALERT_TICK"
    private const val REQUEST = 7711

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DoseAlarmReceiver::class.java).setAction(ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context, REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Run the engine almost immediately (after a schedule/dose change). */
    fun kickNow(context: Context) = scheduleAt(context, System.currentTimeMillis() + 2_000)

    fun scheduleAt(context: Context, atMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            // No exact-alarm capability granted → best-effort inexact wake.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }
}
