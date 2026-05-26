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

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Battery Level", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${String.format(locale, "%.2f", uiState.batteryPercent)}%",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Input Power: ${PowerDisplay.formatWatts(uiState.displayWatts, uiState.powerUnit, locale)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

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
                                                ListItem(
                                                    headlineContent = {
                                                        Text(
                                                            "Gained ${String.format(locale, "%.2f", gain)}% in ${
                                                                SessionUiFormatting.formatDurationWithSeconds(
                                                                    durationMs
                                                                )
                                                            }"
                                                        )
                                                    },
                                                    supportingContent = {
                                                        Column {
                                                            Text(
                                                                SessionUiFormatting.formatStartLineAmPm(
                                                                    session,
                                                                    locale
                                                                ) +
                                                                        " • Peak ${
                                                                            PowerDisplay.formatWatts(
                                                                                session.maxWatts,
                                                                                uiState.powerUnit,
                                                                                locale
                                                                            )
                                                                        }"
                                                            )
                                                            session.chargeCompletedAtMs?.let { completedMs ->
                                                                Text(
                                                                    SessionUiFormatting.formatChargeCompletedLine(
                                                                        completedMs,
                                                                        locale
                                                                    ),
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    style = MaterialTheme.typography.bodySmall
                                                                )
                                                            }
                                                        }
                                                    }
                                                )
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
