package com.volttrack.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
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

    // DYNAMIC RECEIVER: This completely bypasses the Android 8.0 manifest block.
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            serviceScope.launch {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        ChargingSessionRecorder.beginChargingSession(this@ChargingService)
                        startTrackingLoop()
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        ChargingSessionRecorder.finalizeSession(this@ChargingService)
                        stopTrackingLoop()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        monitor = BatteryMonitor(this)

        // Register the dynamic receiver so it listens 24/7
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(powerReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        if (monitor.isExternalPowerConnected()) {
            serviceScope.launch {
                ChargingSessionRecorder.ensureSessionStartedIfCharging(this@ChargingService)
            }
            startTrackingLoop()
        } else {
            // Drop down to a quiet, minimized notification to keep the process alive
            startForeground(1, buildIdleNotification())
        }

        // START_STICKY ensures the OS automatically restarts the service if memory gets low
        return START_STICKY
    }

    private fun startTrackingLoop() {
        wattJob?.cancel()
        wattJob = serviceScope.launch {
            var maxWatts = 0.0
            val prefs: UserPreferences = prefsRepo.userPreferences.first()

            try {
                startForeground(1, buildActiveNotification(prefs, monitor.getPrecisionLevel(), 0.0))
            } catch (e: Exception) {
                // Ignore Android 12+ background start exceptions
            }

            while (isActive) {
                val interval = prefs.refreshIntervalMs.coerceIn(
                    PreferencesRepository.REFRESH_MIN,
                    PreferencesRepository.REFRESH_MAX
                )

                val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

                // Rogue Service Kill-Switch (Keeps your Doze Mode logic safe)
                if (!monitor.isExternalPowerConnected(batteryIntent)) {
                    ChargingSessionRecorder.finalizeSession(this@ChargingService, isOrphaned = true)
                    stopTrackingLoop()
                    break
                }

                val pct = monitor.getPrecisionLevel(batteryIntent)
                val currentWatts = monitor.getCurrentWatts(batteryIntent)
                if (currentWatts > maxWatts) {
                    maxWatts = currentWatts
                    ChargingSessionRecorder.updateMaxWatts(this@ChargingService, maxWatts)
                }

                ChargingSessionRecorder.updateSessionProgress(this@ChargingService, pct, System.currentTimeMillis())

                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(1, buildActiveNotification(prefs, pct, currentWatts))
                delay(interval)
            }
        }
    }

    private fun stopTrackingLoop() {
        wattJob?.cancel()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, buildIdleNotification())
    }

    private fun buildActiveNotification(prefs: UserPreferences, pct: Double, watts: Double): android.app.Notification {
        val locale = Locale.getDefault()
        val powerLine = PowerDisplay.formatWatts(watts, prefs.powerUnit, locale)
        val text = "${String.format(locale, "%.2f", pct)}% · $powerLine"
        return NotificationCompat.Builder(this, NotificationChannels.CHARGING_SERVICE)
            .setContentTitle("VoltTrack Active")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_lock_idle_low_battery)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun buildIdleNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, NotificationChannels.CHARGING_SERVICE)
            .setContentTitle("VoltTrack Monitoring")
            .setContentText("Waiting for charger...")
            .setSmallIcon(R.drawable.ic_lock_idle_low_battery)
            .setPriority(NotificationCompat.PRIORITY_MIN) // Hides the icon from the top status bar
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        unregisterReceiver(powerReceiver)
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