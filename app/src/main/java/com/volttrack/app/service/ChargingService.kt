package com.volttrack.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.NotificationCompat
import com.volttrack.app.R
import com.volttrack.app.data.ChargingSessionRecorder
import com.volttrack.app.data.preferences.PreferencesRepository
import com.volttrack.app.data.preferences.UserPreferences
import com.volttrack.app.logic.BatteryMonitor
import com.volttrack.app.notification.NotificationChannels
import com.volttrack.app.ui.PowerDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class ChargingService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var monitor: BatteryMonitor
    private var wattJob: Job? = null
    private val prefsRepo by lazy { PreferencesRepository.get(this) }

    override fun onCreate() {
        super.onCreate()
        monitor = BatteryMonitor(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = buildNotification(UserPreferences(), 0.0, 0.0)
        try {
            startForeground(1, notification)
        } catch (e: RuntimeException) {
            if (e is SecurityException) {
                stopSelf()
                return START_NOT_STICKY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException"
            ) {
                stopSelf()
                return START_NOT_STICKY
            }
            throw e
        }

        wattJob?.cancel()
        wattJob = serviceScope.launch {
            var maxWatts = 0.0
            while (isActive) {
                val prefs: UserPreferences = prefsRepo.userPreferences.first()
                val interval = prefs.refreshIntervalMs.coerceIn(
                    PreferencesRepository.REFRESH_MIN,
                    PreferencesRepository.REFRESH_MAX
                )

                // Fetch the intent once to use efficiently across checks
                val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

                val pct = monitor.getPrecisionLevel(batteryIntent)
                val currentWatts = monitor.getCurrentWatts(batteryIntent)
                if (currentWatts > maxWatts) {
                    maxWatts = currentWatts
                    ChargingSessionRecorder.updateMaxWatts(this@ChargingService, maxWatts)
                }

                // Write heartbeat data
                ChargingSessionRecorder.updateSessionProgress(this@ChargingService, pct, System.currentTimeMillis())

                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(1, buildNotification(prefs, pct, currentWatts))
                delay(interval)
            }
        }
        return START_STICKY
    }

    private fun buildNotification(prefs: UserPreferences, pct: Double, watts: Double): android.app.Notification {
        val locale = Locale.getDefault()
        val powerLine = PowerDisplay.formatWatts(watts, prefs.powerUnit, locale)
        val text = "${String.format(locale, "%.2f", pct)}% · $powerLine"
        return NotificationCompat.Builder(this, NotificationChannels.CHARGING_SERVICE)
            .setContentTitle("VoltTrack active")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_lock_idle_low_battery)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        wattJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NotificationChannels.CHARGING_SERVICE,
                "Charging monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?) = null
}