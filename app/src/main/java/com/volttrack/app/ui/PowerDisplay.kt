package com.volttrack.app.ui

import com.volttrack.app.data.preferences.PowerUnit
import java.util.Locale

object PowerDisplay {
    fun formatWatts(watts: Double, unit: PowerUnit, locale: Locale = Locale.getDefault()): String =
        when (unit) {
            PowerUnit.WATTS -> "${String.format(locale, "%.1f", watts)} W"
            PowerUnit.MILLIWATTS -> "${String.format(locale, "%.0f", watts * 1000.0)} mW"
        }
}
