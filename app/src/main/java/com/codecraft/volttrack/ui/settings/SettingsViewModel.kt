package com.codecraft.volttrack.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codecraft.volttrack.data.preferences.AppThemeColor
import com.codecraft.volttrack.data.preferences.PowerUnit
import com.codecraft.volttrack.data.preferences.PreferencesRepository
import com.codecraft.volttrack.data.preferences.ThemePreference
import com.codecraft.volttrack.data.preferences.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PreferencesRepository.get(application)

    val preferences = repository.userPreferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UserPreferences()
    )

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { repository.setTheme(theme) }
    }

    fun setThemeColor(color: AppThemeColor) {
        viewModelScope.launch { repository.setThemeColor(color) }
    }

    fun setPowerUnit(unit: PowerUnit) {
        viewModelScope.launch { repository.setPowerUnit(unit) }
    }

    fun setRefreshIntervalMs(ms: Long) {
        viewModelScope.launch { repository.setRefreshIntervalMs(ms) }
    }

    fun setGoalEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setGoalEnabled(enabled) }
    }

    fun setGoalBatteryPercent(percent: Int) {
        viewModelScope.launch { repository.setGoalBatteryPercent(percent) }
    }

    fun setAlertOverheat(enabled: Boolean) {
        viewModelScope.launch { repository.setAlertOverheat(enabled) }
    }

    fun setAlertSlowCharging(enabled: Boolean) {
        viewModelScope.launch { repository.setAlertSlowCharging(enabled) }
    }

    fun setOverheatThreshold(enabled: Double) {
        viewModelScope.launch { repository.setOverheatThreshold(enabled) }
    }

    fun setSlowChargingThreshold(limit: Double, isCustom: Boolean) {
        viewModelScope.launch {
            repository.setSlowChargingThreshold(limit, isCustom)
        }
    }
}
