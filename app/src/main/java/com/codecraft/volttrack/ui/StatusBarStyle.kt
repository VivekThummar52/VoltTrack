package com.codecraft.volttrack.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Chooses light or dark status/nav bar icons to match the current theme background.
 */
@Composable
fun SystemBarsForContent(darkTheme: Boolean) {
    val useDarkIcons = !darkTheme
    val view = LocalView.current
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect

        // Match the status bar color to the current app theme background
        window.statusBarColor = backgroundColor

        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = useDarkIcons
            isAppearanceLightNavigationBars = useDarkIcons
        }
    }
}