package com.codecraft.volttrack.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.codecraft.volttrack.data.preferences.PreferencesRepository
import com.codecraft.volttrack.ui.charts.ChartsScreen
import com.codecraft.volttrack.ui.components.LoadingScreen
import com.codecraft.volttrack.ui.main.MainScreen
import com.codecraft.volttrack.ui.main.MainViewModel
import com.codecraft.volttrack.ui.onboarding.OnboardingScreen
import com.codecraft.volttrack.ui.settings.PrivacyScreen
import com.codecraft.volttrack.ui.settings.SettingsScreen
import com.codecraft.volttrack.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.first

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CHARTS = "charts"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
}

@Composable
fun VoltTrackNavHost(
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val prefsRepo = remember { PreferencesRepository.get(context) }
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val done = prefsRepo.userPreferences.first().onboardingComplete
        startDestination = if (done) Routes.HOME else Routes.ONBOARDING
    }

    if (startDestination == null) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            LoadingScreen("Setting up VoltTrack...")
        }
        return
    }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in setOf(Routes.HOME, Routes.CHARTS, Routes.SETTINGS)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    listOf(
                        Triple(Routes.HOME, "Home", Icons.Filled.Home),
                        Triple(Routes.CHARTS, "Charts", Icons.Filled.BarChart),
                        Triple(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
                    ).forEach { (route, label, icon) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = navBackStackEntry?.destination?.hierarchy?.any { it.route == route } == true,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination!!,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Routes.HOME) {
                MainScreen(viewModel = mainViewModel)
            }
            composable(Routes.CHARTS) {
                ChartsScreen(viewModel = mainViewModel)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onOpenPrivacy = {
                        navController.navigate(Routes.PRIVACY)
                    }
                )
            }
            composable(Routes.PRIVACY) {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
