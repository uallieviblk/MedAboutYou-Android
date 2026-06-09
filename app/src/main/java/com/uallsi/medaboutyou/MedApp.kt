package com.uallsi.medaboutyou

import android.app.Application
import com.uallsi.medaboutyou.reminders.Notifications
import com.uallsi.medaboutyou.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Application entry point; owns the singleton [AppContainer]. */
class MedApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannel(this)
        // Keep the background reminder/caregiver-alert scan armed whenever
        // reminders are enabled (idempotent). BootReceiver re-arms it after a
        // reboot when "Start at boot" is on.
        CoroutineScope(Dispatchers.IO).launch {
            if (container.settings.remindersEnabledFlow.first()) Reminders.enable(this@MedApp)
        }
    }
}
