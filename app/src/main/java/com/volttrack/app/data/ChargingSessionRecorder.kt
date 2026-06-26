package com.volttrack.app.data

import android.content.Context
import android.os.BatteryManager
import android.os.SystemClock
import com.volttrack.app.logic.BatteryMonitor
import com.volttrack.app.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ActiveChargingSession(
    val startTime: Long,
    val startPct: Double,
    val currentPct: Double,
    val maxWatts: Double,
    val chargeCompletedAtMs: Long?,
    val nowMs: Long,
    val maxTemp: Double
)

object ChargingSessionRecorder {
    private val finalizeMutex = Mutex()
    private val wattLock = Any()

    private const val PREFS = "charging_session_active"
    private const val KEY_START_AT = "start_at_ms"
    private const val KEY_START_PCT = "start_pct"
    private const val KEY_MAX_W = "max_w"
    private const val KEY_CHARGE_COMPLETED_AT = "charge_completed_at_ms"

    // Heartbeat tracking
    private const val KEY_LAST_UPDATE_TIME = "last_update_time_ms" // Wall-clock time for DB saving
    private const val KEY_LAST_UPDATE_PCT = "last_update_pct"
    private const val KEY_LAST_HEARTBEAT_REALTIME = "last_heartbeat_realtime" // Monotonic clock for Doze/Staleness checks
    private const val KEY_MAX_TEMP = "max_temp"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun beginChargingSession(context: Context) {
        val app = context.applicationContext
        val p = prefs(app)

        val startAt = p.getLong(KEY_START_AT, 0L)
        val now = System.currentTimeMillis()
        val realTime = SystemClock.elapsedRealtime()

        if (startAt > 0L) {
            // Debounce: Ignore concurrent triggers from BroadcastReceiver and ViewModel loop
            if (now - startAt < 2000L) {
                return
            }

            // REBOOT PROTECTION: If the device just booted (uptime < 5 minutes),
            // this is the OS waking up, not a physical plug-in. Do not split the session.
            if (SystemClock.elapsedRealtime() < 300_000L) {
                return
            }

            finalizeSession(app, isOrphaned = true)
        }

        val pct = BatteryMonitor(app).getPrecisionLevel()
        val temp = BatteryMonitor(app).getTemperature()
        p.edit()
            .putLong(KEY_START_AT, now)
            .putString(KEY_START_PCT, pct.toString())
            .putString(KEY_MAX_W, "0.0")
            .putString(KEY_MAX_TEMP, temp.toString())
            .putLong(KEY_LAST_UPDATE_TIME, now)
            .putLong(KEY_LAST_HEARTBEAT_REALTIME, realTime)
            .putString(KEY_LAST_UPDATE_PCT, pct.toString())
            .remove(KEY_CHARGE_COMPLETED_AT)
            .commit()
    }

    suspend fun ensureSessionStartedIfCharging(context: Context) {
        val app = context.applicationContext
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        if (!bm.isCharging) return
        if (prefs(app).getLong(KEY_START_AT, 0L) > 0L) return
        beginChargingSession(app)
    }

    suspend fun recoverOrphanedSessionIfUnplugged(context: Context) {
        val app = context.applicationContext
        val p = prefs(app)
        val startAt = p.getLong(KEY_START_AT, 0L)

        if (startAt > 0L) {
            val bm = BatteryMonitor(app)
            val isPlugged = bm.isExternalPowerConnected()
            val lastPct = p.getString(KEY_LAST_UPDATE_PCT, "0")!!.toDouble()
            val lastRealTime = p.getLong(KEY_LAST_HEARTBEAT_REALTIME, SystemClock.elapsedRealtime())
            val currentPct = bm.getPrecisionLevel()
            val currentRealTime = SystemClock.elapsedRealtime()

            // Time jumps backwards if the device rebooted. Otherwise, check if > 30 minutes of silence
            val timeSinceHeartbeat = currentRealTime - lastRealTime
            val deviceRebooted = timeSinceHeartbeat < 0
            val isSeverelyStale = timeSinceHeartbeat > 1800_000L // 30 minutes

            // Trigger recovery ONLY if:
            // 1. We are currently unplugged.
            // 2. OR The battery dropped by at least 1% (meaning they discharged while app was asleep).
            // 3. OR The heartbeat is severely stale AND we are currently unplugged.
            // 4. OR The device rebooted AND we are currently unplugged.
            if (!isPlugged || currentPct < lastPct - 1.0 || (isSeverelyStale && !isPlugged) || (deviceRebooted && !isPlugged)) {
                finalizeSession(app, isOrphaned = true)
            }
        }
    }

    fun updateSessionProgress(context: Context, pct: Double, nowMs: Long) {
        prefs(context.applicationContext).edit()
            .putLong(KEY_LAST_UPDATE_TIME, nowMs)
            .putLong(KEY_LAST_HEARTBEAT_REALTIME, SystemClock.elapsedRealtime())
            .putString(KEY_LAST_UPDATE_PCT, pct.toString())
            .apply()
    }

    fun updateMaxStats(context: Context, watts: Double, temp: Double) {
        val app = context.applicationContext
        synchronized(wattLock) {
            val p = prefs(app)
            if (p.getLong(KEY_START_AT, 0L) == 0L) return

            val curW = p.getString(KEY_MAX_W, "0")!!.toDouble()
            val curT = p.getString(KEY_MAX_TEMP, "0")!!.toDouble()
            val edit = p.edit()
            var changed = false

            if (watts > curW) {
                edit.putString(KEY_MAX_W, watts.toString())
                changed = true
            }
            if (temp > curT) {
                edit.putString(KEY_MAX_TEMP, temp.toString())
                changed = true
            }
            if (changed) edit.apply()
        }
    }

    fun noteChargeCompletedIfUnset(context: Context) {
        val app = context.applicationContext
        val p = prefs(app)
        if (p.getLong(KEY_START_AT, 0L) == 0L) return
        if (p.getLong(KEY_CHARGE_COMPLETED_AT, 0L) > 0L) return
        p.edit().putLong(KEY_CHARGE_COMPLETED_AT, System.currentTimeMillis()).commit()
    }

    fun readActiveSessionOrNull(
        context: Context,
        currentPct: Double,
        nowMs: Long = System.currentTimeMillis()
    ): ActiveChargingSession? {
        val app = context.applicationContext
        val p = prefs(app)
        val startAt = p.getLong(KEY_START_AT, 0L)
        if (startAt == 0L) return null
        val startPct = p.getString(KEY_START_PCT, "0")!!.toDouble()
        val maxW = p.getString(KEY_MAX_W, "0")!!.toDouble()
        val completedAt = p.getLong(KEY_CHARGE_COMPLETED_AT, 0L).takeIf { it > 0L }
        val maxTemp = p.getString(KEY_MAX_TEMP, "0")!!.toDouble()
        return ActiveChargingSession(
            startTime = startAt,
            startPct = startPct,
            currentPct = currentPct,
            maxWatts = maxW,
            chargeCompletedAtMs = completedAt,
            nowMs = nowMs,
            maxTemp = maxTemp
        )
    }

    suspend fun finalizeSession(context: Context, isOrphaned: Boolean = false) {
        finalizeMutex.withLock {
            val app = context.applicationContext
            val p = prefs(app)
            val startAt = p.getLong(KEY_START_AT, 0L)
            if (startAt == 0L) return@withLock

            val startPct = p.getString(KEY_START_PCT, "0")!!.toDouble()
            val maxW = p.getString(KEY_MAX_W, "0")!!.toDouble()
            val completedAt = p.getLong(KEY_CHARGE_COMPLETED_AT, 0L).takeIf { it > 0L }
            val maxTemp = p.getString(KEY_MAX_TEMP, "0")!!.toDouble()

            val endAt: Long
            val endPct: Double

            if (isOrphaned) {
                // We missed the disconnect broadcast. Rely on the last heartbeat.
                endAt = p.getLong(KEY_LAST_UPDATE_TIME, startAt).coerceAtLeast(startAt)
                endPct = p.getString(KEY_LAST_UPDATE_PCT, startPct.toString())!!.toDouble()
            } else {
                // coerceAtLeast prevents negative durations if the system clock is wrong after reboot
                endAt = System.currentTimeMillis().coerceAtLeast(startAt)
                endPct = BatteryMonitor(app).getPrecisionLevel()
            }

            // MICRO-SESSION FILTER: Discard if < 10 seconds with zero gain
            val durationMs = (endAt - startAt).coerceAtLeast(0L)
            val gain = endPct - startPct
            // Only filter if we aren't being forced to finalize from a worker/kill-event
            if (!isOrphaned && durationMs < 10000L && gain <= 0.0) {
                p.edit().clear().commit()
                return@withLock
            }

            val session = ChargingSession(
                startTime = startAt,
                endTime = endAt,
                startPct = startPct,
                endPct = endPct,
                maxWatts = maxW,
                chargeCompletedAtMs = completedAt,
                maxTemp = maxTemp
            )
            val rowId = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(app).sessionDao().insert(session)
            }
            val withId = session.copy(id = rowId.toInt())
            NotificationHelper.showSessionSaved(app, withId)
            p.edit().clear().commit()
        }
    }

    // Add this new function to ChargingSessionRecorder.kt
    fun updateActiveSessionInDb(context: Context, pct: Double, watts: Double, temp: Double) {
        val app = context.applicationContext
        val p = prefs(app)
        val startAt = p.getLong(KEY_START_AT, 0L)
        if (startAt == 0L) return

        // Run this update in a coroutine scope
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(app).sessionDao().updateActiveSession(
                start = startAt,
                pct = pct,
                watts = watts,
                temp = temp
            )
        }
    }
}