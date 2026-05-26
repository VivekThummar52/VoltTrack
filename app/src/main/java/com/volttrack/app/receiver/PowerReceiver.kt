package com.volttrack.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.volttrack.app.data.ChargingSessionRecorder
import com.volttrack.app.service.ChargingService

class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pendingResult = goAsync()
        Thread {
            try {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        // Do not start a foreground service from a broadcast: Android 12+ often blocks it
                        // (ForegroundServiceStartNotAllowedException). Session + peak watts use prefs / UI instead.
                        ChargingSessionRecorder.beginChargingSession(app)
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        ChargingSessionRecorder.finalizeSessionBlocking(app)
                        app.stopService(Intent(app, ChargingService::class.java))
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
