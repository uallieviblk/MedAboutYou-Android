package com.uallsi.medaboutyou

import android.app.Application
import com.uallsi.medaboutyou.reminders.Notifications

/** Application entry point; owns the singleton [AppContainer]. */
class MedApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannel(this)
    }
}
