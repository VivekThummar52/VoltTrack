package com.codecraft.volttrack.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codecraft.volttrack.data.preferences.PowerUnit
import com.codecraft.volttrack.data.preferences.ThemePreference
import com.codecraft.volttrack.data.preferences.UserPreferences
import com.codecraft.volttrack.ui.theme.VoltTrackTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenPrivacy: () -> Unit
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    SettingsScreenContent(
        prefs = prefs,
        onSetTheme = viewModel::setTheme,
        onSetPowerUnit = viewModel::setPowerUnit,
        onSetOverheatThreshold = viewModel::setOverheatThreshold,
        onSetSlowChargingThreshold = viewModel::setSlowChargingThreshold,
        onSetRefreshIntervalMs = viewModel::setRefreshIntervalMs,
        onSetGoalEnabled = viewModel::setGoalEnabled,
        onSetGoalBatteryPercent = viewModel::setGoalBatteryPercent,
        onOpenPrivacy = onOpenPrivacy
    )
}

@Composable
fun SettingsScreenContent(
    prefs: UserPreferences,
    onSetTheme: (ThemePreference) -> Unit,
    onSetPowerUnit: (PowerUnit) -> Unit,
    onSetOverheatThreshold: (Double) -> Unit,
    onSetSlowChargingThreshold: (Double, Boolean) -> Unit,
    onSetRefreshIntervalMs: (Long) -> Unit,
    onSetGoalEnabled: (Boolean) -> Unit,
    onSetGoalBatteryPercent: (Int) -> Unit,
    onOpenPrivacy: () -> Unit
) {
    var textFieldValue by remember(prefs.alertSlowChargingThreshold) {
        mutableStateOf(if (prefs.isCustomSlowThreshold) prefs.alertSlowChargingThreshold.toInt().toString() else "")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Theme",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Column(Modifier.selectableGroup()) {
                    ThemePreference.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = prefs.theme == mode,
                                    onClick = { onSetTheme(mode) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = prefs.theme == mode,
                                onClick = null
                            )
                            Text(
                                when (mode) {
                                    ThemePreference.LIGHT -> "Light"
                                    ThemePreference.DARK -> "Dark"
                                    ThemePreference.SYSTEM -> "System default"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    "Power display",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Column(Modifier.selectableGroup()) {
                    PowerUnit.entries.forEach { unit ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = prefs.powerUnit == unit,
                                    onClick = { onSetPowerUnit(unit) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = prefs.powerUnit == unit,
                                onClick = null
                            )
                            Text(
                                when (unit) {
                                    PowerUnit.WATTS -> "Watts (W)"
                                    PowerUnit.MILLIWATTS -> "Milliwatts (mW)"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- Unified Alerts Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

                // 1. Overheat Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Thermostat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Overheat threshold (°C)", style = MaterialTheme.typography.titleSmall)
                    }
                    SegmentedSelectionRow(listOf(35.0, 40.0, 45.0), prefs.alertOverheatThreshold, "°C") {
                        onSetOverheatThreshold(it)
                    }
                    Text("Receive an alert when battery temperature exceeds this limit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 2. Slow Charging Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Slow charging threshold (W)", style = MaterialTheme.typography.titleSmall)
                    }
                    SegmentedSelectionRow(listOf(1.0, 2.0, 5.0), prefs.alertSlowChargingThreshold, "W") {
                        onSetSlowChargingThreshold(it, false)
                    }

                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                val valInt = input.toIntOrNull() ?: 0
                                if (valInt <= 300) {
                                    textFieldValue = input
                                    if (valInt > 0) {
                                        onSetSlowChargingThreshold(valInt.toDouble(), true)
                                    }
                                }
                            }
                        },
                        label = { Text("Custom threshold (W)") },
                        placeholder = { Text("Max 300W") },
                        isError = textFieldValue.isNotEmpty() && (textFieldValue.toIntOrNull() ?: 0) > 300,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    Text("Receive an alert when charging power falls below this limit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // 3. Info Box inside the same card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Alerts will appear as notifications while charging. You can change these anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Refresh interval", style = MaterialTheme.typography.titleSmall)
                Text(
                    "How often the app polls battery and power while it is open. " +
                        "The foreground charging notification uses the same interval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val options = listOf(1000L, 2000L, 5000L, 10000L)
                Column(Modifier.selectableGroup()) {
                    options.forEach { ms ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = prefs.refreshIntervalMs == ms,
                                    onClick = { onSetRefreshIntervalMs(ms) },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = prefs.refreshIntervalMs == ms,
                                onClick = null
                            )
                            Text(
                                "${ms / 1000}s",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Battery goal alert", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Notify once per charge when level reaches the target (while plugged in).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.goalEnabled,
                        onCheckedChange = { onSetGoalEnabled(it) }
                    )
                }
                if (prefs.goalEnabled) {
                    Text(
                        "Target: ${prefs.goalBatteryPercent}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = prefs.goalBatteryPercent.toFloat(),
                        onValueChange = { onSetGoalBatteryPercent(it.toInt()) },
                        valueRange = 50f..100f,
                        steps = 49
                    )
                }
            }
        }
        HorizontalDivider()
        Text("Privacy", style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenPrivacy() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Privacy & data", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Local storage, permissions, and how estimates work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SegmentedSelectionRow(
    options: List<Double>,
    selected: Double,
    labelSuffix: String,
    onSelect: (Double) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clickable {
                        keyboardController?.hide()
                        onSelect(option)
                    },
                shape = RoundedCornerShape(50),
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = "${option.toInt()}$labelSuffix",
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    VoltTrackTheme {
        SettingsScreenContent(
            prefs = UserPreferences(
                theme = ThemePreference.SYSTEM,
                powerUnit = PowerUnit.WATTS,
                refreshIntervalMs = 2000L,
                goalEnabled = true,
                goalBatteryPercent = 80,
                alertOverheatThreshold = 40.0,
                alertSlowChargingThreshold = 2.0,
                isCustomSlowThreshold = false
            ),
            onSetTheme = {},
            onSetPowerUnit = {},
            onSetOverheatThreshold = {},
            onSetSlowChargingThreshold = { _, _ -> },
            onSetRefreshIntervalMs = {},
            onSetGoalEnabled = {},
            onSetGoalBatteryPercent = {},
            onOpenPrivacy = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SettingsScreenDarkPreview() {
    VoltTrackTheme(darkTheme = true) {
        SettingsScreenContent(
            prefs = UserPreferences(
                theme = ThemePreference.DARK,
                powerUnit = PowerUnit.WATTS,
                refreshIntervalMs = 2000L,
                goalEnabled = true,
                goalBatteryPercent = 85,
                alertOverheatThreshold = 45.0,
                alertSlowChargingThreshold = 5.0,
                isCustomSlowThreshold = true
            ),
            onSetTheme = {},
            onSetPowerUnit = {},
            onSetOverheatThreshold = {},
            onSetSlowChargingThreshold = { _, _ -> },
            onSetRefreshIntervalMs = {},
            onSetGoalEnabled = {},
            onSetGoalBatteryPercent = {},
            onOpenPrivacy = {}
        )
    }
}
