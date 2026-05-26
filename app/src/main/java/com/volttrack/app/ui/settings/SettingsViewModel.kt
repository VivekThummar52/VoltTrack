package com.volttrack.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.volttrack.app.data.preferences.PowerUnit
import com.volttrack.app.data.preferences.PreferencesRepository
import com.volttrack.app.data.preferences.ThemePreference
import com.volttrack.app.data.preferences.UserPreferences
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
}
