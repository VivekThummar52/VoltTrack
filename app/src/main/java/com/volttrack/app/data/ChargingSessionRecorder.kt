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

/**
 * Persists charging session boundaries in [SharedPreferences] because [ChargingService] may not
 * reliably run or reach [android.app.Service.onDestroy] on all devices. Unplug finalizes the row.
 *
 * Critical writes use [android.content.SharedPreferences.Editor.commit] so a fast unplug cannot
 * finalize before [beginChargingSession]'s [android.content.SharedPreferences.apply] has landed.
 */
/** Live charging window tracked in prefs until [finalizeSession] writes to Room. */
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

    private const val PREFS = "charging_session_active"
    private const val KEY_START_AT = "start_at_ms"
    private const val KEY_START_PCT = "start_pct"
    private const val KEY_MAX_W = "max_w"
    private const val KEY_CHARGE_COMPLETED_AT = "charge_completed_at_ms"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** New plug-in event: always starts a fresh session window. */
    fun beginChargingSession(context: Context) {
        val app = context.applicationContext
        val pct = BatteryMonitor(app).getPrecisionLevel()
        prefs(app).edit()
            .putLong(KEY_START_AT, System.currentTimeMillis())
            .putString(KEY_START_PCT, pct.toString())
            .putString(KEY_MAX_W, "0.0")
            .remove(KEY_CHARGE_COMPLETED_AT)
            .commit()
    }

    /**
     * If the device is charging but we have no session (e.g. missed [Intent.ACTION_POWER_CONNECTED]),
     * start tracking so unplug can still save.
     */
    fun ensureSessionStartedIfCharging(context: Context) {
        val app = context.applicationContext
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        if (!bm.isCharging) return
        if (prefs(app).getLong(KEY_START_AT, 0L) > 0L) return
        beginChargingSession(app)
    }

    fun updateMaxWatts(context: Context, maxWatts: Double) {
        prefs(context.applicationContext).edit()
            .putString(KEY_MAX_W, maxWatts.toString())
            .apply()
    }

    /** First instant we observe full/100% during an active session (still plugged). */
    fun noteChargeCompletedIfUnset(context: Context) {
        val app = context.applicationContext
        val p = prefs(app)
        if (p.getLong(KEY_START_AT, 0L) == 0L) return
        if (p.getLong(KEY_CHARGE_COMPLETED_AT, 0L) > 0L) return
        p.edit().putLong(KEY_CHARGE_COMPLETED_AT, System.currentTimeMillis()).commit()
    }

    /**
     * Returns the in-progress session from prefs, or null if nothing is being tracked.
     * [currentPct] and [nowMs] should reflect the latest battery clock for live duration/gain.
     */
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

    /** Raises stored peak for the active session when a new sample is higher (e.g. from the UI poll). */
    fun considerWattSample(context: Context, watts: Double) {
        if (watts <= 0.0) return
        val app = context.applicationContext
        if (prefs(app).getLong(KEY_START_AT, 0L) == 0L) return
        val cur = prefs(app).getString(KEY_MAX_W, "0")!!.toDouble()
        if (watts > cur) updateMaxWatts(app, watts)
    }

    suspend fun finalizeSession(context: Context) {
        finalizeMutex.withLock {
            val app = context.applicationContext
            val p = prefs(app)
            val startAt = p.getLong(KEY_START_AT, 0L)
            if (startAt == 0L) return@withLock

            val startPct = p.getString(KEY_START_PCT, "0")!!.toDouble()
            val maxW = p.getString(KEY_MAX_W, "0")!!.toDouble()
            val endPct = BatteryMonitor(app).getPrecisionLevel()
            val endAt = System.currentTimeMillis()
            val completedAt = p.getLong(KEY_CHARGE_COMPLETED_AT, 0L).takeIf { it > 0L }

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

    /** For [BroadcastReceiver] / non-suspending callers. */
    fun finalizeSessionBlocking(context: Context) {
        runBlocking { finalizeSession(context) }
    }
}
