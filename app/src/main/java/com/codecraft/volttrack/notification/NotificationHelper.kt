package com.codecraft.volttrack.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.codecraft.volttrack.MainActivity
import com.codecraft.volttrack.R
import com.codecraft.volttrack.data.ChargingSession
import java.util.Locale

object NotificationHelper {

    private const val ID_SESSION = 2001
    private const val ID_GOAL = 2002

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun mainActivityIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showSessionSaved(context: Context, session: ChargingSession) {
        if (!canPostNotifications(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val locale = Locale.getDefault()
        val gain = session.endPct - session.startPct
        val title = "Session saved"
        val text = "Gained ${String.format(locale, "%.2f", gain)}% • peak ${
            String.format(locale, "%.1f", session.maxWatts)
        } W"

        val n = NotificationCompat.Builder(context, NotificationChannels.SESSION_EVENTS)
            .setSmallIcon(R.drawable.ic_lock_idle_low_battery)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(mainActivityIntent(context, requestCode = 0))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(ID_SESSION, n)
    }

    fun showGoalReached(context: Context, percent: Double) {
        if (!canPostNotifications(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val locale = Locale.getDefault()
        val title = "Battery goal reached"
        val text = "Battery is at ${String.format(locale, "%.1f", percent)}% (your target)."

        val n = NotificationCompat.Builder(context, NotificationChannels.GOALS)
            .setSmallIcon(R.drawable.ic_lock_idle_low_battery)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(mainActivityIntent(context, requestCode = 1))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(ID_GOAL, n)
    }

    // Replace the previous implementation with this flexible version
    fun showSimpleAlert(context: Context, title: String, message: String) {
        if (!canPostNotifications(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val n = NotificationCompat.Builder(context, NotificationChannels.GOALS)
            .setSmallIcon(R.drawable.ic_lock_idle_low_battery)
            .setContentTitle(title)
            .setContentText(message) // Use the passed string directly
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(mainActivityIntent(context, requestCode = 2))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(3001, n)
    }
}