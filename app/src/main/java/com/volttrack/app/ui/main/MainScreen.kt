package com.volttrack.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.volttrack.app.ui.PowerDisplay
import com.volttrack.app.ui.SessionUiFormatting
import java.util.Locale
import android.os.BatteryManager
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = Locale.getDefault()
    val groupedSessions = remember(uiState.sessions, locale) {
        SessionUiFormatting.groupSessionsByDay(uiState.sessions, locale)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("VoltTrack Live", style = MaterialTheme.typography.headlineLarge)

            BatteryOverviewCard(uiState, locale)

            uiState.activeSession?.let { active ->
                val durationMs = (active.nowMs - active.startTime).coerceAtLeast(0L)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Current session",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            SessionUiFormatting.formatActiveSessionHeadline(
                                active.startPct,
                                active.currentPct,
                                durationMs,
                                locale
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            SessionUiFormatting.formatSessionStartDateTime(active.startTime, locale) +
                                " • Peak ${PowerDisplay.formatWatts(active.maxWatts, uiState.powerUnit, locale)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        active.chargeCompletedAtMs?.let { completedMs ->
                            Text(
                                SessionUiFormatting.formatChargeCompletedLine(completedMs, locale),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            Text("Recent Sessions", style = MaterialTheme.typography.titleLarge)
            if (uiState.sessions.isEmpty()) {
                Text(
                    if (uiState.activeSession != null) {
                        "No completed sessions in the list yet. The current charge above is saved here when you unplug."
                    } else {
                        "No sessions yet. Plug in to charge (VoltTrack starts monitoring), then unplug to save a session here."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(
                        items = groupedSessions,
                        key = { it.daySortKey }
                    ) { section ->
                        val collapsed = section.daySortKey in uiState.collapsedDayKeys
                        val daySummary = remember(section.sessions) {
                            SessionUiFormatting.computeDayChargeSummary(section.sessions)
                        }
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.toggleDaySection(section.daySortKey)
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = section.label,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        if (collapsed) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = SessionUiFormatting.formatCollapsedSummaryLine1(
                                                    daySummary,
                                                    locale
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = SessionUiFormatting.formatCollapsedSummaryLine2(
                                                    daySummary
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                                        contentDescription = if (collapsed) "Expand" else "Collapse",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                AnimatedVisibility(
                                    visible = !collapsed,
                                    enter = fadeIn(
                                        animationSpec = tween(170, easing = FastOutSlowInEasing)
                                    ) + scaleIn(
                                        initialScale = 0.97f,
                                        animationSpec = tween(170, easing = FastOutSlowInEasing)
                                    ),
                                    exit = fadeOut(
                                        animationSpec = tween(110, easing = LinearOutSlowInEasing)
                                    ) + scaleOut(
                                        targetScale = 0.98f,
                                        animationSpec = tween(110, easing = LinearOutSlowInEasing)
                                    )
                                ) {
                                    Column {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        section.sessions.forEachIndexed { index, session ->
                                            key(session.id) {
                                                val durationMs = session.endTime - session.startTime
                                                val gain = session.endPct - session.startPct
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surface, // This makes the item background white/surface color
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(
                                                                horizontal = 16.dp,
                                                                vertical = 12.dp
                                                            ), // ListItem default padding
                                                        verticalAlignment = Alignment.Top
                                                    ) {
                                                        // LEFT SIDE
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = "Gained ${
                                                                    String.format(
                                                                        locale,
                                                                        "%.2f",
                                                                        gain
                                                                    )
                                                                }% in ${
                                                                    SessionUiFormatting.formatDurationWithSeconds(
                                                                        durationMs
                                                                    )
                                                                }",
                                                                style = MaterialTheme.typography.bodyLarge
                                                            )
                                                            Spacer(modifier = Modifier.height(4.dp)) // Exact gap to match supporting text

                                                            Text(
                                                                text = SessionUiFormatting.formatStartLineAmPm(
                                                                    session,
                                                                    locale
                                                                ),
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )

                                                            session.chargeCompletedAtMs?.let { completedMs ->
                                                                Text(
                                                                    text = SessionUiFormatting.formatChargeCompletedLine(
                                                                        completedMs,
                                                                        locale
                                                                    ),
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    modifier = Modifier.padding(top = 2.dp)
                                                                )
                                                            }
                                                        }

                                                        // RIGHT SIDE
                                                        Column(horizontalAlignment = Alignment.End) {
                                                            Text(
                                                                text = "Peak ${
                                                                    PowerDisplay.formatWatts(
                                                                        session.maxWatts,
                                                                        uiState.powerUnit,
                                                                        locale
                                                                    )
                                                                }",
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Spacer(modifier = Modifier.height(4.dp)) // Matches the left side spacer

                                                            val tempText =
                                                                if (session.maxTemp > 0.0) "${session.maxTemp}°C" else "--"
                                                            Text(
                                                                text = "Peak Temp $tempText",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }

                                                if (index < section.sessions.lastIndex) {
                                                    HorizontalDivider(
                                                        color = MaterialTheme.colorScheme.outlineVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryOverviewCard(uiState: MainUiState, locale: Locale) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Title + Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Battery Overview", style = MaterialTheme.typography.titleMedium)
                HealthBadge(uiState.healthStatus)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Side: Big Percentage & Battery Icon
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${String.format(locale, "%.2f", uiState.batteryPercent)}%",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Icon(
                        imageVector = Icons.Filled.BatteryFull,
                        contentDescription = "Battery Icon",
                        tint = Color(0xFF4CAF50), // Standard green
                        modifier = Modifier.size(48.dp).rotate(90f)
                    )
                }

                // Right Side: Stats List
                Column(modifier = Modifier.weight(1.2f)) {
                    StatRow(
                        icon = Icons.Filled.Bolt,
                        label = "Input Power",
                        value = PowerDisplay.formatWatts(uiState.displayWatts, uiState.powerUnit, locale)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    StatRow(
                        icon = Icons.Filled.Thermostat,
                        label = "Current Temp",
                        value = "${uiState.temperatureC}°C"
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    StatRow(
                        icon = Icons.Filled.FavoriteBorder,
                        label = "Battery Health",
                        value = uiState.healthPercent?.let { "$it%" } ?: "--"
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HealthBadge(healthStatus: Int) {
    val (text, color, icon) = when (healthStatus) {
        BatteryManager.BATTERY_HEALTH_GOOD -> Triple("Healthy", Color(0xFF4CAF50), Icons.Filled.CheckCircleOutline)
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> Triple("Overheat", Color(0xFFE53935), Icons.Filled.Warning)
        BatteryManager.BATTERY_HEALTH_DEAD -> Triple("Dead", Color(0xFFE53935), Icons.Filled.Warning)
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> Triple("Over Voltage", Color(0xFFE53935), Icons.Filled.Warning)
        BatteryManager.BATTERY_HEALTH_COLD -> Triple("Cold", Color(0xFF1E88E5), Icons.Filled.Thermostat)
        else -> Triple("Unknown", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Filled.HelpOutline)
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text, color = color, style = MaterialTheme.typography.labelMedium)
        }
    }
}
