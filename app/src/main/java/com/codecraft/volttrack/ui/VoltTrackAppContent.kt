package com.codecraft.volttrack.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codecraft.volttrack.data.preferences.PreferencesRepository
import com.codecraft.volttrack.data.preferences.ThemePreference
import com.codecraft.volttrack.data.preferences.UserPreferences
import com.codecraft.volttrack.ui.main.MainViewModel
import com.codecraft.volttrack.ui.settings.SettingsViewModel
import com.codecraft.volttrack.ui.theme.VoltTrackTheme

@Composable
fun VoltTrackAppContent(
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val prefs by PreferencesRepository.get(context).userPreferences.collectAsStateWithLifecycle(
        UserPreferences()
    )
    val darkTheme = when (prefs.theme) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }
    VoltTrackTheme(darkTheme = darkTheme) {
        SystemBarsForContent(darkTheme = darkTheme)
        VoltTrackNavHost(
            mainViewModel = mainViewModel,
            settingsViewModel = settingsViewModel
        )
    }
}
