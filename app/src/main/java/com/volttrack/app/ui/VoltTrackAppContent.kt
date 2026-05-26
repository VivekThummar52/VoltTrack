package com.volttrack.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.volttrack.app.data.preferences.PreferencesRepository
import com.volttrack.app.data.preferences.ThemePreference
import com.volttrack.app.data.preferences.UserPreferences
import com.volttrack.app.ui.main.MainViewModel
import com.volttrack.app.ui.settings.SettingsViewModel
import com.volttrack.app.ui.theme.VoltTrackTheme

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
        LightSystemBarsForContent()
        VoltTrackNavHost(
            mainViewModel = mainViewModel,
            settingsViewModel = settingsViewModel
        )
    }
}
