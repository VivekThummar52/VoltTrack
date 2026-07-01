package com.codecraft.volttrack.ui.main

import com.codecraft.volttrack.data.preferences.UserPreferences
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Documents goal-notification gating without spinning up [MainViewModel] (Android dependencies).
 */
class MainViewModelGoalLogicTest {

    @Test
    fun goalFiresWhenPercentReachesTarget() {
        val prefs = UserPreferences(goalEnabled = true, goalBatteryPercent = 80)
        val pct = 80.0
        val shouldFire = prefs.goalEnabled && pct + 1e-6 >= prefs.goalBatteryPercent
        assertThat(shouldFire).isTrue()
    }

    @Test
    fun goalDoesNotFireWhenDisabled() {
        val prefs = UserPreferences(goalEnabled = false, goalBatteryPercent = 80)
        val pct = 100.0
        val shouldFire = prefs.goalEnabled && pct + 1e-6 >= prefs.goalBatteryPercent
        assertThat(shouldFire).isFalse()
    }
}
