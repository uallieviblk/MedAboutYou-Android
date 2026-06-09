// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.uallsi.medaboutyou.R

/**
 * Sends an SMS to the configured caregiver when a dose is overdue past its
 * caregiver-alert timeout.
 *
 * NOTE: this is the one place the app sends data off-device. It is opt-in (only
 * fires when a caregiver phone is set in Settings and a schedule enables the
 * alert) and uses the user's own SIM via [SmsManager].
 */
object CaregiverAlerts {

    fun hasSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /** True if the SMS was handed to the telephony stack. */
    fun sendOverdueSms(context: Context, phone: String, userName: String, medName: String, time: String): Boolean {
        if (phone.isBlank() || !hasSmsPermission(context)) return false
        val body = if (userName.isBlank()) {
            context.getString(R.string.sms_caregiver_text, medName, time)
        } else {
            context.getString(R.string.sms_caregiver_text_named, userName, medName, time)
        }
        return try {
            smsManager(context).sendTextMessage(phone, null, body, null, null)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            context.getSystemService(SmsManager::class.java)
        else
            SmsManager.getDefault()
}
