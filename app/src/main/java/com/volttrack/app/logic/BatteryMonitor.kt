package com.volttrack.app.logic

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.BatteryManager
import kotlin.math.abs
import kotlin.math.max

class BatteryMonitor(private val context: Context) {
    private val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    companion object {
        /** Same id as [BatteryManager.BATTERY_PROPERTY_CHARGE_FULL] (API 34+). */
        private const val PROPERTY_CHARGE_FULL = 6
        private const val PREFS = "volttrack_battery"
        private const val KEY_FULL_MICRO_AH = "full_charge_micro_ah"
    }

    private fun batteryPrefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadPersistedFullMicroAh(): Long {
        val v = batteryPrefs().getLong(KEY_FULL_MICRO_AH, 0L)
        return if (v >= 100_000L) v else 0L
    }

    private fun persistFullMicroAh(fullMicroAh: Long) {
        if (fullMicroAh == Long.MIN_VALUE || fullMicroAh < 100_000L) return
        val cur = loadPersistedFullMicroAh()
        val merged = max(cur, fullMicroAh)
        batteryPrefs().edit().putLong(KEY_FULL_MICRO_AH, merged).apply()
    }

    private fun percentageFromChargeAndFull(chargeCounter: Long, fullMicroAh: Long): Double? {
        if (fullMicroAh == Long.MIN_VALUE || fullMicroAh < 100_000L) return null
        if (chargeCounter == Long.MIN_VALUE || chargeCounter <= 0) return null
        if (chargeCounter > fullMicroAh * 115 / 100) return null
        val pct = (chargeCounter.toDouble() / fullMicroAh.toDouble()) * 100.0
        return pct.coerceIn(0.0, 100.0)
    }

    fun getPrecisionLevel(batteryIntent: Intent? = null): Double {
        val intent = batteryIntent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        // 1. Get the exact integer percentage the OS is showing in the status bar
        val systemPct = if (level >= 0 && scale > 0) {
            (level.toDouble() / scale.toDouble()) * 100.0
        } else {
            -1.0
        }

        val chargeCounter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)

        if (level >= 0 && scale > 0 && level >= scale &&
            chargeCounter > 0 && chargeCounter != Long.MIN_VALUE
        ) {
            persistFullMicroAh(chargeCounter)
        }

        var precisePct: Double? = null

        if (Build.VERSION.SDK_INT >= 34) {
            val chargeFull = bm.getLongProperty(PROPERTY_CHARGE_FULL)
            precisePct = percentageFromChargeAndFull(chargeCounter, chargeFull)
            if (precisePct != null) {
                persistFullMicroAh(chargeFull)
            }
        }

        if (precisePct == null) {
            val capacity = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val unsupported = chargeCounter == Long.MIN_VALUE || capacity == Long.MIN_VALUE
            val microAhPlausible = !unsupported &&
                    chargeCounter > 0 &&
                    capacity >= 100_000L &&
                    chargeCounter <= capacity * 115 / 100

            if (microAhPlausible) {
                val pct = (chargeCounter.toDouble() / capacity.toDouble()) * 100.0
                if (pct in 0.0..100.0) {
                    persistFullMicroAh(capacity)
                    precisePct = pct
                }
            }
        }

        if (precisePct == null) {
            loadPersistedFullMicroAh().takeIf { it > 0L }?.let { ref ->
                precisePct = percentageFromChargeAndFull(chargeCounter, ref)
            }
        }

        // --- DISCREPANCY FIX (OEM ANCHORING) ---
        if (systemPct >= 0.0 && precisePct == null) {
            return systemPct
        }

        return precisePct ?: 0.0
    }

    fun getCurrentWatts(batteryIntent: Intent? = null): Double {
        val intent = batteryIntent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return 0.0

        if (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0) return 0.0

        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        if (voltageMv <= 0) return 0.0

        var raw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (raw == Long.MIN_VALUE || raw == 0L) {
            raw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        }
        if (raw == Long.MIN_VALUE) return 0.0

        // Use absolute value to bypass OEM sign-flipping.
        // We already know the device is plugged in via EXTRA_PLUGGED,
        // so any measurable current is charging current.
        val microAmps = abs(raw).toDouble()

        val wattsFromMicro = voltageMv * microAmps / 1_000_000_000.0
        val wattsFromMilli = voltageMv * microAmps / 1_000_000.0

        val w = if (microAmps in 1.0..50_000.0 && wattsFromMicro < 0.05 && wattsFromMilli >= 0.05) {
            wattsFromMilli
        } else {
            wattsFromMicro
        }
        
        return w.coerceIn(0.0, 120.0)
    }

    fun isExternalPowerConnected(batteryIntent: Intent? = null): Boolean {
        val intent = batteryIntent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return false
        return intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }

    fun isBatteryChargingComplete(batteryIntent: Intent? = null): Boolean {
        val intent = batteryIntent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (status == BatteryManager.BATTERY_STATUS_FULL) return true
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return level >= 0 && scale > 0 && level >= scale
    }
}