package com.codecraft.volttrack.data.preferences

enum class ThemePreference {
    LIGHT,
    DARK,
    SYSTEM
}

enum class PowerUnit {
    WATTS,
    MILLIWATTS
}

data class UserPreferences(
    val onboardingComplete: Boolean = false,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val powerUnit: PowerUnit = PowerUnit.WATTS,
    /** UI / monitor poll interval (ms). */
    val refreshIntervalMs: Long = 2000L,
    val goalEnabled: Boolean = false,
    /** Target battery % for optional notification while charging (50–100). */
    val goalBatteryPercent: Int = 80,
    val alertOverheat: Boolean = true,
    val alertSlowCharging: Boolean = false,
    val alertOverheatThreshold: Double = 40.0,
    val alertSlowChargingThreshold: Double = 2.0,
    val isCustomSlowThreshold: Boolean = false
)
