package com.volttrack.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
            powerUnit = p[KEY_POWER_UNIT]?.let { runCatching { PowerUnit.valueOf(it) }.getOrNull() }
                ?: PowerUnit.WATTS,
            refreshIntervalMs = p[KEY_REFRESH_MS]?.coerceIn(REFRESH_MIN, REFRESH_MAX) ?: 2000L,
            goalEnabled = p[KEY_GOAL_ENABLED] ?: false,
            goalBatteryPercent = (p[KEY_GOAL_PCT] ?: 80).coerceIn(50, 100)
        )
    }

    suspend fun setOnboardingComplete(done: Boolean) {
        appContext.dataStore.edit { it[KEY_ONBOARDING_DONE] = done }
    }

    suspend fun setTheme(theme: ThemePreference) {
        appContext.dataStore.edit { it[KEY_THEME] = theme.name }
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

    companion object {
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_complete")
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_POWER_UNIT = stringPreferencesKey("power_unit")
        private val KEY_REFRESH_MS = longPreferencesKey("refresh_ms")
        private val KEY_GOAL_ENABLED = booleanPreferencesKey("goal_enabled")
        private val KEY_GOAL_PCT = intPreferencesKey("goal_pct")

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
