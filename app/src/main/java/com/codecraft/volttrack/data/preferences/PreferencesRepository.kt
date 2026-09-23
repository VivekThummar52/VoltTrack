package com.codecraft.volttrack.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "volttrack_settings")

class PreferencesRepository(private val context: Context) {

    private val appContext = context.applicationContext

    val userPreferences: Flow<UserPreferences> = appContext.dataStore.data.map { p ->
        UserPreferences(
            onboardingComplete = p[KEY_ONBOARDING_DONE] ?: false,
            theme = p[KEY_THEME]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                ?: ThemePreference.SYSTEM,
            themeColor = p[KEY_THEME_COLOR]?.let { runCatching { AppThemeColor.valueOf(it) }.getOrNull() }
                ?: AppThemeColor.DYNAMIC,
            powerUnit = p[KEY_POWER_UNIT]?.let { runCatching { PowerUnit.valueOf(it) }.getOrNull() }
                ?: PowerUnit.WATTS,
            refreshIntervalMs = p[KEY_REFRESH_MS]?.coerceIn(REFRESH_MIN, REFRESH_MAX) ?: 2000L,
            goalEnabled = p[KEY_GOAL_ENABLED] ?: false,
            goalBatteryPercent = (p[KEY_GOAL_PCT] ?: 80).coerceIn(50, 100),
            alertOverheat = p[KEY_ALERT_OVERHEAT] ?: true,
            alertSlowCharging = p[KEY_ALERT_SLOW] ?: false,
            alertOverheatThreshold = p[KEY_OVERHEAT_LIMIT] ?: 40.0,
            alertSlowChargingThreshold = p[KEY_SLOW_LIMIT] ?: 2.0,
            isCustomSlowThreshold = p[KEY_IS_CUSTOM_SLOW] ?: false
        )
    }

    suspend fun setOnboardingComplete(done: Boolean) {
        appContext.dataStore.edit { it[KEY_ONBOARDING_DONE] = done }
    }

    suspend fun setTheme(theme: ThemePreference) {
        appContext.dataStore.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun setThemeColor(color: AppThemeColor) {
        appContext.dataStore.edit { it[KEY_THEME_COLOR] = color.name }
    }

    suspend fun setPowerUnit(unit: PowerUnit) {
        appContext.dataStore.edit { it[KEY_POWER_UNIT] = unit.name }
    }

    suspend fun setRefreshIntervalMs(ms: Long) {
        appContext.dataStore.edit { it[KEY_REFRESH_MS] = ms.coerceIn(REFRESH_MIN, REFRESH_MAX) }
    }

    suspend fun setGoalEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_GOAL_ENABLED] = enabled }
    }

    suspend fun setGoalBatteryPercent(percent: Int) {
        appContext.dataStore.edit { it[KEY_GOAL_PCT] = percent.coerceIn(50, 100) }
    }

    suspend fun setAlertOverheat(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_ALERT_OVERHEAT] = enabled }
    }

    suspend fun setAlertSlowCharging(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_ALERT_SLOW] = enabled }
    }

    suspend fun setOverheatThreshold(limit: Double) {
        appContext.dataStore.edit { it[KEY_OVERHEAT_LIMIT] = limit }
    }
    suspend fun setSlowChargingThreshold(limit: Double) {
        appContext.dataStore.edit { it[KEY_SLOW_LIMIT] = limit }
    }

    suspend fun setSlowChargingThreshold(limit: Double, isCustom: Boolean) {
        appContext.dataStore.edit {
            it[KEY_SLOW_LIMIT] = limit
            it[KEY_IS_CUSTOM_SLOW] = isCustom
        }
    }

    companion object {
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_complete")
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_THEME_COLOR = stringPreferencesKey("theme_color")
        private val KEY_POWER_UNIT = stringPreferencesKey("power_unit")
        private val KEY_REFRESH_MS = longPreferencesKey("refresh_ms")
        private val KEY_GOAL_ENABLED = booleanPreferencesKey("goal_enabled")
        private val KEY_GOAL_PCT = intPreferencesKey("goal_pct")
        private val KEY_ALERT_OVERHEAT = booleanPreferencesKey("alert_overheat")
        private val KEY_ALERT_SLOW = booleanPreferencesKey("alert_slow")

        private val KEY_OVERHEAT_LIMIT = doublePreferencesKey("overheat_limit")
        private val KEY_SLOW_LIMIT = doublePreferencesKey("slow_limit")
        private val KEY_IS_CUSTOM_SLOW = booleanPreferencesKey("is_custom_slow")

        const val REFRESH_MIN = 1000L
        const val REFRESH_MAX = 10_000L

        @Volatile
        private var instance: PreferencesRepository? = null

        fun get(context: Context): PreferencesRepository =
            instance ?: synchronized(this) {
                instance ?: PreferencesRepository(context.applicationContext).also { instance = it }
            }
    }
}
