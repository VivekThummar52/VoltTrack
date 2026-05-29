package com.volttrack.app.data

import android.content.Context
import android.os.BatteryManager
import com.volttrack.app.logic.BatteryMonitor
import com.volttrack.app.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ActiveChargingSession(
    val startTime: Long,
    val startPct: Double,
    val currentPct: Double,
    val maxWatts: Double,
    val chargeCompletedAtMs: Long?,
    val nowMs: Long
)

object ChargingSessionRecorder {
    private val finalizeMutex = Mutex()
    private val wattLock = Any() // Thread lock for max watts check-then-act

    private const val PREFS = "charging_session_active"
    private const val KEY_START_AT = "start_at_ms"
    private const val KEY_START_PCT = "start_pct"
    private const val KEY_MAX_W = "max_w"
    private const val KEY_CHARGE_COMPLETED_AT = "charge_completed_at_ms"

    // Heartbeat tracking for process-death recovery
    private const val KEY_LAST_UPDATE_TIME = "last_update_time_ms"
    private const val KEY_LAST_UPDATE_PCT = "last_update_pct"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun beginChargingSession(context: Context) {
        val app = context.applicationContext
        val p = prefs(app)

        // If a session is already active but we are starting a new one, finalize it first
        if (p.getLong(KEY_START_AT, 0L) > 0L) {
            finalizeSessionBlocking(app, isOrphaned = true)
        }

        val pct = BatteryMonitor(app).getPrecisionLevel()
        val now = System.currentTimeMillis()
        p.edit()
            .putLong(KEY_START_AT, now)
            .putString(KEY_START_PCT, pct.toString())
            .putString(KEY_MAX_W, "0.0")
            .putLong(KEY_LAST_UPDATE_TIME, now)
            .putString(KEY_LAST_UPDATE_PCT, pct.toString())
            .remove(KEY_CHARGE_COMPLETED_AT)
            .commit()
    }

    fun ensureSessionStartedIfCharging(context: Context) {
        val app = context.applicationContext
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        if (!bm.isCharging) return
        if (prefs(app).getLong(KEY_START_AT, 0L) > 0L) return
        beginChargingSession(app)
    }

    fun recoverOrphanedSessionIfUnplugged(context: Context) {
        val app = context.applicationContext
        val p = prefs(app)
        if (p.getLong(KEY_START_AT, 0L) > 0L) {
            val bm = BatteryMonitor(app)
            val isPlugged = bm.isExternalPowerConnected()
            val lastPct = p.getString(KEY_LAST_UPDATE_PCT, "0")!!.toDouble()
            val currentPct = bm.getPrecisionLevel()

            // Finalize if currently unplugged OR if battery dropped significantly
            // (meaning they unplugged, discharged, and plugged back in while app was dead)
            if (!isPlugged || currentPct < lastPct - 1.0) {
                finalizeSessionBlocking(app, isOrphaned = true)
            }
        }
    }

    fun updateSessionProgress(context: Context, pct: Double, nowMs: Long) {
        prefs(context.applicationContext).edit()
            .putLong(KEY_LAST_UPDATE_TIME, nowMs)
            .putString(KEY_LAST_UPDATE_PCT, pct.toString())
            .apply()
    }

    fun updateMaxWatts(context: Context, maxWatts: Double) {
        synchronized(wattLock) {
            prefs(context.applicationContext).edit()
                .putString(KEY_MAX_W, maxWatts.toString())
                .apply()
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
        return ActiveChargingSession(
            startTime = startAt,
            startPct = startPct,
            currentPct = currentPct,
            maxWatts = maxW,
            chargeCompletedAtMs = completedAt,
            nowMs = nowMs
        )
    }

    fun considerWattSample(context: Context, watts: Double) {
        if (watts <= 0.0) return
        val app = context.applicationContext
        synchronized(wattLock) {
            if (prefs(app).getLong(KEY_START_AT, 0L) == 0L) return
            val cur = prefs(app).getString(KEY_MAX_W, "0")!!.toDouble()
            if (watts > cur) {
                prefs(app).edit().putString(KEY_MAX_W, watts.toString()).apply()
            }
        }
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

            val endAt: Long
            val endPct: Double

            if (isOrphaned) {
                // We missed the disconnect broadcast. Rely on the last heartbeat.
                endAt = p.getLong(KEY_LAST_UPDATE_TIME, startAt).coerceAtLeast(startAt)
                endPct = p.getString(KEY_LAST_UPDATE_PCT, startPct.toString())!!.toDouble()
            } else {
                endAt = System.currentTimeMillis()
                endPct = BatteryMonitor(app).getPrecisionLevel()
            }

            val session = ChargingSession(
                startTime = startAt,
                endTime = endAt,
                startPct = startPct,
                endPct = endPct,
                maxWatts = maxW,
                chargeCompletedAtMs = completedAt
            )
            val rowId = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(app).sessionDao().insert(session)
            }
            val withId = session.copy(id = rowId.toInt())
            NotificationHelper.showSessionSaved(app, withId)
            p.edit().clear().commit()
        }
    }

    fun finalizeSessionBlocking(context: Context, isOrphaned: Boolean = false) {
        runBlocking { finalizeSession(context, isOrphaned) }
    }
}