package com.codecraft.volttrack.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.codecraft.volttrack.R
import com.codecraft.volttrack.data.ChargingSessionRecorder
import com.codecraft.volttrack.data.preferences.PreferencesRepository
import com.codecraft.volttrack.data.preferences.UserPreferences
import com.codecraft.volttrack.logic.BatteryMonitor
import com.codecraft.volttrack.notification.NotificationChannels
import com.codecraft.volttrack.ui.PowerDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.codecraft.volttrack.notification.AlertManager

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
                        Log.d("VoltTrack", "Disconnected, scheduling finalization")
                        scheduleFinalization() // Hands off finalization to the OS
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(powerReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        if (monitor.isExternalPowerConnected()) {
            serviceScope.launch {
                // Force check: If we started but charger is NOT connected,
                // clean up anything left over from the last crash/kill
                if (!monitor.isExternalPowerConnected()) {
                    ChargingSessionRecorder.finalizeSession(this@ChargingService, isOrphaned = true)
                }
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
            var maxTemp = 0.0
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
                    scheduleFinalization() // Hands off finalization to the OS
                    stopTrackingLoop()
                    break
                }

                val pct = monitor.getPrecisionLevel(batteryIntent)
                val currentWatts = monitor.getCurrentWatts(batteryIntent)
                val currentTemp = monitor.getTemperature(batteryIntent)
                
                // Track "Charging Completed" time even when app is closed
                if (monitor.isBatteryChargingComplete(batteryIntent)) {
                    ChargingSessionRecorder.noteChargeCompletedIfUnset(this@ChargingService)
                }

                AlertManager.checkAndNotify(this@ChargingService, prefs, currentTemp, currentWatts)

                if (currentWatts > maxWatts || currentTemp > maxTemp) {
                    if (currentWatts > maxWatts) maxWatts = currentWatts
                    if (currentTemp > maxTemp) maxTemp = currentTemp
                    ChargingSessionRecorder.updateMaxStats(this@ChargingService, maxWatts, maxTemp)
                }

                ChargingSessionRecorder.updateActiveSessionInDb(
                    context = this@ChargingService,
                    pct = pct,
                    watts = currentWatts,
                    temp = currentTemp
                )

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

    private fun scheduleFinalization() {
        val workRequest = OneTimeWorkRequestBuilder<FinalizeSessionWorker>().build()
        WorkManager.getInstance(this).enqueue(workRequest)
    }

    override fun onBind(intent: Intent?) = null
}