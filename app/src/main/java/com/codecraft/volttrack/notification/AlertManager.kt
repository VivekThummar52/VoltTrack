package com.codecraft.volttrack.notification

import android.content.Context
import com.codecraft.volttrack.data.preferences.UserPreferences

object AlertManager {
    private var lastAlertTime = 0L
    private var lastTempNotified = 0.0
    private var lastWattsNotified = 0.0

    fun checkAndNotify(context: Context, prefs: UserPreferences, temp: Double, watts: Double) {
        val now = System.currentTimeMillis()
        
        // 1. Check for Overheat
        if (prefs.alertOverheat && temp >= prefs.alertOverheatThreshold) {
            // Only notify if enough time has passed OR if the temperature has changed significantly
            val shouldNotify = (now - lastAlertTime > 300_000L) || (Math.abs(temp - lastTempNotified) >= 1.0)
            
            if (shouldNotify) {
                lastAlertTime = now
                lastTempNotified = temp
                val msg = "Temperature is ${temp}°C (Threshold: ${prefs.alertOverheatThreshold}°C)."
                NotificationHelper.showSimpleAlert(context, "Battery Overheating", msg)
            }
            return // Skip slow charging check if overheating to avoid notification spam
        }

        // 2. Check for Slow Charging
        if (prefs.alertSlowCharging && watts > 0.0 && watts <= prefs.alertSlowChargingThreshold) {
            if (prefs.alertSlowChargingThreshold in 1.0..300.0) {
                // Only notify if enough time has passed OR if wattage dropped significantly
                val shouldNotify = (now - lastAlertTime > 300_000L) || (lastWattsNotified - watts >= 2.0)
                
                if (shouldNotify) {
                    lastAlertTime = now
                    lastWattsNotified = watts
                    val rounded = Math.round(watts)
                    val msg = "Input is ${rounded}W (Limit: ${prefs.alertSlowChargingThreshold.toInt()}W)."
                    NotificationHelper.showSimpleAlert(context, "Slow Charging", msg)
                }
            }
        }
    }
}