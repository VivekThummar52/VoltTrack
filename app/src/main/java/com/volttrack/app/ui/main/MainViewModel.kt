package com.volttrack.app.ui.main

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.volttrack.app.data.ActiveChargingSession
import com.volttrack.app.data.ChargingSession
import com.volttrack.app.data.ChargingSessionRecorder
import com.volttrack.app.data.preferences.PowerUnit
import com.volttrack.app.data.preferences.PreferencesRepository
import com.volttrack.app.data.preferences.UserPreferences
import com.volttrack.app.data.repository.SessionRepository
import com.volttrack.app.logic.BatteryMonitor
import com.volttrack.app.notification.NotificationHelper
import com.volttrack.app.service.ChargingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class MainUiState(
    val batteryPercent: Double = 0.0,
    val displayWatts: Double = 0.0,
    val powerUnit: PowerUnit = PowerUnit.WATTS,
    val activeSession: ActiveChargingSession? = null,
    val sessions: List<ChargingSession> = emptyList(),
    val collapsedDayKeys: Set<Long> = emptySet()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionRepository = SessionRepository(application)
    private val batteryMonitor = BatteryMonitor(application.applicationContext)
    private val preferencesRepository = PreferencesRepository.get(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var wattSmoothed: Double = -1.0
    private val refreshIntervalMs = AtomicLong(2000L)
    private var prefsSnapshot: UserPreferences = UserPreferences()
    private var goalNotifiedThisSession: Boolean = false

    init {
        observeSessions()
        observePreferences()
        startBatteryAndChargingLoop()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesRepository.userPreferences.collect { prefs ->
                prefsSnapshot = prefs
                refreshIntervalMs.set(
                    prefs.refreshIntervalMs.coerceIn(
                        PreferencesRepository.REFRESH_MIN,
                        PreferencesRepository.REFRESH_MAX
                    )
                )
                _uiState.update { it.copy(powerUnit = prefs.powerUnit) }
            }
        }
    }

    private fun observeSessions() {
        viewModelScope.launch {
            sessionRepository.observeSessions().collect { sessions ->
                _uiState.update { it.copy(sessions = sessions) }
            }
        }
    }

    private fun startBatteryAndChargingLoop() {
        viewModelScope.launch {
            delay(750)
            var previousPlugged: Boolean? = null
            while (true) {
                val app = getApplication<Application>()

                // Fetch the intent once to use efficiently across all battery checks in this tick
                val batteryIntent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

                val plugged = batteryMonitor.isExternalPowerConnected(batteryIntent)
                if (previousPlugged != null) {
                    if (!previousPlugged && plugged) {
                        goalNotifiedThisSession = false
                        ChargingSessionRecorder.beginChargingSession(app)
                        tryStartChargingService(app)
                    }
                    if (previousPlugged && !plugged) {
                        goalNotifiedThisSession = false
                        ChargingSessionRecorder.finalizeSession(app)
                    }
                } else if (plugged) {
                    ChargingSessionRecorder.ensureSessionStartedIfCharging(app)
                }
                previousPlugged = plugged

                if (plugged && batteryMonitor.isBatteryChargingComplete(batteryIntent)) {
                    ChargingSessionRecorder.noteChargeCompletedIfUnset(app)
                }

                val pct = batteryMonitor.getPrecisionLevel(batteryIntent)
                val sample = batteryMonitor.getCurrentWatts(batteryIntent)
                if (plugged) {
                    ChargingSessionRecorder.considerWattSample(app, sample)
                }

                // THE FIX: Snap instantly to the first real reading
                wattSmoothed = when {
                    sample <= 0.0 -> 0.0
                    wattSmoothed <= 0.0 && sample > 0.0 -> sample // Instantly bypass smoothing on the first positive read
                    else -> wattSmoothed * 0.55 + sample * 0.45
                }
                val nowMs = System.currentTimeMillis()
                val activeSession: ActiveChargingSession? =
                    if (plugged) ChargingSessionRecorder.readActiveSessionOrNull(app, pct, nowMs) else null

                maybeNotifyGoal(app, plugged, pct)

                _uiState.update {
                    it.copy(
                        batteryPercent = pct,
                        displayWatts = wattSmoothed,
                        activeSession = activeSession
                    )
                }
                delay(refreshIntervalMs.get())
            }
        }
    }

    private fun maybeNotifyGoal(app: Application, plugged: Boolean, pct: Double) {
        val prefs = prefsSnapshot
        if (!plugged || !prefs.goalEnabled || goalNotifiedThisSession) return
        if (pct + 1e-6 >= prefs.goalBatteryPercent) {
            goalNotifiedThisSession = true
            NotificationHelper.showGoalReached(app, pct)
        }
    }

    private fun tryStartChargingService(app: Application) {
        try {
            val intent = Intent(app, ChargingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(app, intent)
            } else {
                app.startService(intent)
            }
        } catch (_: Exception) {
        }
    }

    fun onForegroundChargingCheck() {
        val app = getApplication<Application>()
        val bm = app.getSystemService(android.content.Context.BATTERY_SERVICE) as BatteryManager
        if (bm.isCharging) {
            ChargingSessionRecorder.ensureSessionStartedIfCharging(app)
            tryStartChargingService(app)
        }
    }

    fun toggleDaySection(daySortKey: Long) {
        _uiState.update { state ->
            val keys = state.collapsedDayKeys
            val next = if (daySortKey in keys) keys - daySortKey else keys + daySortKey
            state.copy(collapsedDayKeys = next)
        }
    }
}