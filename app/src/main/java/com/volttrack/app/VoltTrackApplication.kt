package com.volttrack.app

import android.app.Application
import com.volttrack.app.notification.NotificationChannels

class VoltTrackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureAll(this)
    }
}
