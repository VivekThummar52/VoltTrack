package com.codecraft.volttrack.notification

import android.content.Context
import com.codecraft.volttrack.data.preferences.UserPreferences

object AlertManager {
    private var lastAlertTime = 0L

    fun checkAndNotify(context: Context, prefs: UserPreferences, temp: Double, watts: Double) {
        val now = System.currentTimeMillis()
        if (now - lastAlertTime < 300_000L) return

        // Dynamic Overheat check
        if (prefs.alertOverheat && temp >= prefs.alertOverheatThreshold) {
            lastAlertTime = now
            val msg = "Temperature is ${temp}°C (Threshold: ${prefs.alertOverheatThreshold}°C)."
            NotificationHelper.showSimpleAlert(context, "Battery Overheating", msg)
        }

        // Dynamic Slow Charging check
        // Check if watts is greater than 0 but less than the user's limit
        else if (prefs.alertSlowCharging && watts > 0.0 && watts <= prefs.alertSlowChargingThreshold) {
            // If the threshold is 0 or > 300, don't trigger a false alert
            if (prefs.alertSlowChargingThreshold in 1.0..300.0) {
                lastAlertTime = now
                val rounded = Math.round(watts)
                val msg = "Input is ${rounded}W (Limit: ${prefs.alertSlowChargingThreshold.toInt()}W)."
                NotificationHelper.showSimpleAlert(context, "Slow Charging", msg)
            }
        }
    }
}