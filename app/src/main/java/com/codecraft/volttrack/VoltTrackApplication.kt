package com.codecraft.volttrack

import android.app.Application
import com.codecraft.volttrack.notification.NotificationChannels

class VoltTrackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureAll(this)
    }
}
