package com.volttrack.app.ui.charts

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.volttrack.app.data.ChargingSession
import com.volttrack.app.ui.SessionUiFormatting
import com.volttrack.app.ui.main.MainViewModel
import com.volttrack.app.ui.theme.VoltTrackTheme
import java.util.Locale

@Composable
fun ChartsScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChartsScreenContent(sessions = uiState.sessions)
}

@Composable
fun ChartsScreenContent(
    sessions: List<ChargingSession>,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    val bars = remember(sessions, locale) {
        aggregateChargeTimeByDay(sessions, locale, dayCount = 7)
    }
    val maxDuration = bars.maxOfOrNull { it.totalDurationMs }?.coerceAtLeast(1L) ?: 1L

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Charts", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Time on charger by day (last 7 days). Bar height is the sum of session durations that started on that calendar day.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Weekly overview", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                val scheme = MaterialTheme.colorScheme
                val trackFill = scheme.onSurface.copy(alpha = 0.11f)
                val trackOutline = scheme.outline.copy(alpha = 0.65f)
                val axisColor = scheme.onSurfaceVariant.copy(alpha = 0.9f)
                WeeklyBarChart(
                    bars = bars,
                    maxDurationMs = maxDuration,
                    barColor = scheme.primary,
                    trackFillColor = trackFill,
                    trackOutlineColor = trackOutline,
                    axisLineColor = axisColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    bars.forEach { bar ->
                        Text(
                            text = bar.labelShort,
                            modifier = Modifier
                                .weight(1f)
                                .widthIn(max = 120.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
                bars.forEach { bar ->
                    Text(
                        "${bar.labelShort}: ${SessionUiFormatting.formatDurationWithSeconds(bar.totalDurationMs)} " +
                            "(${bar.sessionCount} sessions)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyBarChart(
    bars: List<DayChartBar>,
    maxDurationMs: Long,
    barColor: Color,
    trackFillColor: Color,
    trackOutlineColor: Color,
    axisLineColor: Color,
    modifier: Modifier = Modifier
) {
    val chartHeight = 180.dp
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight)
    ) {
        if (bars.isEmpty()) return@Canvas
        val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        val strokeWidth = 1.5.dp.toPx()
        val n = bars.size
        val gap = size.width * 0.02f
        val barWidth = (size.width - gap * (n + 1)) / n
        // Baseline near canvas bottom so little dead space sits above the date labels
        val bottom = size.height * 0.96f
        val maxH = bottom - size.height * 0.06f
        bars.forEachIndexed { i, bar ->
            val h = if (maxDurationMs <= 0L) 0f
            else (bar.totalDurationMs.toFloat() / maxDurationMs.toFloat()) * maxH
            val left = gap + i * (barWidth + gap)
            val trackTop = bottom - maxH
            drawRoundRect(
                color = trackFillColor,
                topLeft = Offset(left, trackTop),
                size = Size(barWidth, maxH),
                cornerRadius = corner
            )
            if (h > 0f) {
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, bottom - h),
                    size = Size(barWidth, h),
                    cornerRadius = corner
                )
            }
        }
    }
}

private fun previewSampleSessions(): List<ChargingSession> {
    val now = System.currentTimeMillis()
    val h = 3_600_000L
    return listOf(
        ChargingSession(1, now - 5 * h, now - 4 * h, 20.0, 32.0, 12.0, null),
        ChargingSession(2, now - 4 * h, now - 3 * h + 600_000L, 32.0, 48.0, 15.0, null),
        ChargingSession(3, now - 2 * h, now - h, 50.0, 72.0, 18.0, null)
    )
}

@Preview(name = "Charts · Light", showBackground = true)
@Composable
private fun ChartsScreenPreviewLight() {
    VoltTrackTheme(darkTheme = false) {
        ChartsScreenContent(sessions = previewSampleSessions())
    }
}

@Preview(name = "Charts · Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChartsScreenPreviewDark() {
    VoltTrackTheme(darkTheme = true) {
        ChartsScreenContent(sessions = previewSampleSessions())
    }
}
