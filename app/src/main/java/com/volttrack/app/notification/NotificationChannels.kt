package com.volttrack.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {

    const val CHARGING_SERVICE = "volt_ch"
    const val SESSION_EVENTS = "volt_session"
    const val GOALS = "volt_goals"

    fun ensureAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHARGING_SERVICE,
                "Charging monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Foreground status while VoltTrack monitors charging." }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                SESSION_EVENTS,
                "Session saved",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Short alerts when a charging session is saved." }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                GOALS,
                "Battery goals",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "When your battery reaches the goal you set." }
        )
    }
}
