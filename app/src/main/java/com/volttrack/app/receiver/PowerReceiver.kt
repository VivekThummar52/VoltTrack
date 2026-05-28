package com.volttrack.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.volttrack.app.data.ChargingSessionRecorder
import com.volttrack.app.service.ChargingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PowerReceiver : BroadcastReceiver() {
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        ChargingSessionRecorder.beginChargingSession(app)

                        // Attempt to start the foreground service.
                        // On Android 12+, this may throw ForegroundServiceStartNotAllowedException
                        // if strictly in the background, but we try anyway to catch graceful windows.
                        try {
                            val serviceIntent = Intent(app, ChargingService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                ContextCompat.startForegroundService(app, serviceIntent)
                            } else {
                                app.startService(serviceIntent)
                            }
                        } catch (e: Exception) {
                            // Safely ignored if the system blocks it
                        }
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        // Safely using the suspending function instead of blocking the thread
                        ChargingSessionRecorder.finalizeSession(app)
                        app.stopService(Intent(app, ChargingService::class.java))
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}