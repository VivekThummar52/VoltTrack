package com.codecraft.volttrack.ui.charts

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codecraft.volttrack.data.ChargingSession
import com.codecraft.volttrack.ui.PowerDisplay
import com.codecraft.volttrack.ui.SessionUiFormatting
import com.codecraft.volttrack.ui.main.MainViewModel
import com.codecraft.volttrack.ui.theme.VoltTrackTheme
import java.util.Calendar
import java.util.Locale

enum class TimeRange(val label: String, val dayCount: Int) {
    SEVEN_DAYS("7D", 7),
    FOURTEEN_DAYS("14D", 14),
    THIRTY_DAYS("30D", 30),
    ALL_TIME("All", -1)
}

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
    var selectedRange by remember { mutableStateOf(TimeRange.SEVEN_DAYS) }
    var selectedDayStart by remember { mutableStateOf<Long?>(null) }

    val bars = remember(sessions, locale, selectedRange) {
        aggregateChargeTimeByDay(sessions, locale, dayCount = selectedRange.dayCount)
    }
    val timeOfDay = remember(sessions, locale) {
        aggregateTimeOfDay(sessions, locale)
    }

    // Filter sessions based on selected time range for KPIs
    val kpiSessions = remember(sessions, locale, selectedRange) {
        if (selectedRange == TimeRange.ALL_TIME) {
            sessions
        } else {
            val cal = Calendar.getInstance(locale)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.DAY_OF_YEAR, -(selectedRange.dayCount - 1))
            val rangeStart = cal.timeInMillis
            sessions.filter { it.startTime >= rangeStart }
        }
    }

    val selectedDaySessions = remember(sessions, selectedDayStart, locale) {
        if (selectedDayStart == null) emptyList()
        else sessions.filter { 
            val sessionDayStart = Calendar.getInstance(locale).apply { timeInMillis = it.startTime }
                .apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            sessionDayStart == selectedDayStart
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Charts", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Insights and charging patterns.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Time Range Selector
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            TimeRange.entries.forEachIndexed { index, range ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TimeRange.entries.size),
                    onClick = { 
                        selectedRange = range 
                        selectedDayStart = null // Reset selection when range changes
                    },
                    selected = selectedRange == range
                ) {
                    Text(range.label)
                }
            }
        }

        QuickKpiRow(sessions = kpiSessions, locale = locale)

        // 0. Weekly Overview (Duration Sum)
        ChartCard(
            title = "Usage Overview",
            subtitle = "Total time spent on charger per day"
        ) {
            val maxDur = (bars.maxOfOrNull { it.totalDurationMs } ?: 1L).toFloat()
            val scheme = MaterialTheme.colorScheme
            val trackFill = scheme.onSurface.copy(alpha = 0.11f)
            val barColor = scheme.primary
            
            Column {
                InteractiveBarChart(
                    bars = bars,
                    maxVal = maxDur,
                    selectedDayStart = selectedDayStart,
                    onBarClick = { selectedDayStart = if (selectedDayStart == it) null else it },
                    barColor = barColor,
                    selectedBarColor = barColor,
                    useOldStyleTrack = true,
                    trackColor = trackFill
                ) { bar -> bar.totalDurationMs.toFloat() }
                
                XAxisLabels(bars, selectedRange)
            }
        }

        // Detail Card for Selected Day
        if (selectedDayStart != null) {
            val barData = bars.find { it.dayStartMillis == selectedDayStart }
            if (barData != null) {
                DayDetailCard(
                    barData = barData,
                    sessions = selectedDaySessions,
                    locale = locale,
                    onClose = { selectedDayStart = null }
                )
            }
        }

        // A. Peak Charging Power Trend
        ChartCard(
            title = "Peak Power Trend",
            subtitle = "Daily maximum wattage (W)"
        ) {
            val maxWatts = (bars.maxOfOrNull { it.maxWatts } ?: 0.0).coerceAtLeast(15.0).toFloat()
            val thresholdColor = MaterialTheme.colorScheme.outlineVariant
            Column {
                InteractiveBarChart(
                    bars = bars,
                    maxVal = maxWatts,
                    selectedDayStart = selectedDayStart,
                    onBarClick = { selectedDayStart = if (selectedDayStart == it) null else it },
                    barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    selectedBarColor = MaterialTheme.colorScheme.primary,
                    drawThresholds = { size, bottom, maxH ->
                        val thresholds = listOf(15f, 30f)
                        thresholds.forEach { t ->
                            if (t < maxWatts) {
                                val y = bottom - (t / maxWatts) * maxH
                                drawLine(
                                    color = thresholdColor,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                                )
                            }
                        }
                    }
                ) { bar -> bar.maxWatts.toFloat() }
                XAxisLabels(bars, selectedRange)
            }
        }

        // B. Daily Peak Temperature Heat Map
        ChartCard(
            title = "Peak Temperature",
            subtitle = "Daily max. Color shows safety zone"
        ) {
            val maxT = (bars.maxOfOrNull { it.maxTemp } ?: 0.0).coerceAtLeast(45.0).toFloat()
            val normal = Color(0xFF4CAF50)
            val warm = Color(0xFFFFC107)
            val hot = Color(0xFFF44336)

            Column {
                InteractiveBarChart(
                    bars = bars,
                    maxVal = maxT,
                    selectedDayStart = selectedDayStart,
                    onBarClick = { selectedDayStart = if (selectedDayStart == it) null else it },
                    barColor = null, // Dynamic color based on value
                    selectedBarColor = null,
                    getBarColor = { val t = it.maxTemp.toFloat()
                        when {
                            t < 37f -> normal
                            t < 42f -> warm
                            else -> hot
                        }
                    }
                ) { bar -> bar.maxTemp.toFloat() }
                XAxisLabels(bars, selectedRange)
            }
        }

        // C. Battery Level Gain vs Time Spent
        ChartCard(
            title = "Gain vs Time",
            subtitle = "Inner bar is % gained, outer is time"
        ) {
            val maxDur = (bars.maxOfOrNull { it.totalDurationMs } ?: 1L).toFloat()
            val maxGain = (bars.maxOfOrNull { it.totalGainPct } ?: 1.0).toFloat()
            val durColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
            val gainColor = MaterialTheme.colorScheme.primary

            Column {
                InteractiveBarChart(
                    bars = bars,
                    maxVal = maxDur,
                    selectedDayStart = selectedDayStart,
                    onBarClick = { selectedDayStart = if (selectedDayStart == it) null else it },
                    barColor = durColor,
                    selectedBarColor = durColor.copy(alpha = 0.6f),
                    drawOverlays = { bar, left, barWidth, bottom, maxH ->
                        val gainH = (bar.totalGainPct.toFloat() / maxGain) * (maxH * (bar.totalDurationMs.toFloat() / maxDur))
                        val innerGap = barWidth * 0.2f
                        drawRoundRect(
                            color = if (selectedDayStart == bar.dayStartMillis) gainColor else gainColor.copy(alpha = 0.7f),
                            topLeft = Offset(left + innerGap, bottom - gainH),
                            size = Size(barWidth - innerGap * 2, gainH),
                            cornerRadius = CornerRadius(2.dp.toPx())
                        )
                    }
                ) { bar -> bar.totalDurationMs.toFloat() }
                XAxisLabels(bars, selectedRange)
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendItem("Time", durColor.copy(alpha = 0.8f))
                    LegendItem("Gain %", gainColor)
                }
            }
        }

        // D. Time-of-Day Charging Distribution
        ChartCard(
            title = "Charging Habits",
            subtitle = "Distribution of session start times"
        ) {
            TimeOfDayDonutChart(timeOfDay)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun InteractiveBarChart(
    bars: List<DayChartBar>,
    maxVal: Float,
    selectedDayStart: Long?,
    onBarClick: (Long) -> Unit,
    barColor: Color?,
    selectedBarColor: Color?,
    useOldStyleTrack: Boolean = false,
    trackColor: Color = Color.Transparent,
    getBarColor: ((DayChartBar) -> Color)? = null,
    drawThresholds: (androidx.compose.ui.graphics.drawscope.DrawScope.(Size, Float, Float) -> Unit)? = null,
    drawOverlays: (androidx.compose.ui.graphics.drawscope.DrawScope.(DayChartBar, Float, Float, Float, Float) -> Unit)? = null,
    valueProvider: (DayChartBar) -> Float
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(bars, selectedDayStart) {
                detectTapGestures { offset ->
                    val n = bars.size
                    val gap = size.width * 0.02f
                    val barWidth = (size.width - gap * (n + 1)) / n
                    
                    bars.forEachIndexed { i, bar ->
                        val left = gap + i * (barWidth + gap)
                        if (offset.x in left..(left + barWidth)) {
                            onBarClick(bar.dayStartMillis)
                        }
                    }
                }
            }
    ) {
        val n = bars.size
        val gap = size.width * 0.02f
        val barWidth = (size.width - gap * (n + 1)) / n
        val bottom = size.height
        val maxH = size.height * 0.85f

        drawThresholds?.invoke(this, size, bottom, maxH)

        bars.forEachIndexed { i, bar ->
            val v = valueProvider(bar)
            val h = (v / maxVal) * maxH
            val left = gap + i * (barWidth + gap)
            val isSelected = selectedDayStart == bar.dayStartMillis
            
            if (useOldStyleTrack) {
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, bottom - maxH),
                    size = Size(barWidth, maxH),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }

            val color = when {
                isSelected && selectedBarColor != null -> selectedBarColor
                isSelected && getBarColor != null -> getBarColor(bar)
                getBarColor != null -> getBarColor(bar).copy(alpha = 0.6f)
                else -> barColor ?: Color.Gray
            }

            if (h > 0f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, bottom - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }
            
            drawOverlays?.invoke(this, bar, left, barWidth, bottom, maxH)
        }
    }
}

@Composable
private fun DayDetailCard(
    barData: DayChartBar,
    sessions: List<ChargingSession>,
    locale: Locale,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        barData.labelShort,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${barData.sessionCount} sessions",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            sessions.forEachIndexed { index, session ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            SessionUiFormatting.formatTimeAmPm(session.startTime, locale),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "+${String.format(locale, "%.1f", session.endPct - session.startPct)}%",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            SessionUiFormatting.formatDurationWithSeconds(session.endTime - session.startTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            "Peak: ${String.format(locale, "%.2f", session.maxWatts)}W · ${session.maxTemp}°C",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                if (index < sessions.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            content()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QuickKpiRow(sessions: List<ChargingSession>, locale: Locale) {
    val kpis = remember(sessions, locale) {
        val count = sessions.size.coerceAtLeast(1)
        val totalDuration = sessions.sumOf { (it.endTime - it.startTime).coerceAtLeast(0L) }
        val totalGained = sessions.sumOf { it.endPct - it.startPct }
        val avgPeakPower = if (sessions.isEmpty()) 0.0 else sessions.sumOf { it.maxWatts } / count
        val peakTemp = sessions.maxOfOrNull { it.maxTemp } ?: 0.0

        listOf(
            KpiData(
                label = "Avg. Duration",
                value = if (sessions.isEmpty()) "--" else SessionUiFormatting.formatDurationWithSeconds(totalDuration / count),
                icon = Icons.Default.AvTimer
            ),
            KpiData(
                label = "Total Gained",
                value = if (sessions.isEmpty()) "0%" else "+${String.format(locale, "%.0f", totalGained)}%",
                icon = Icons.Default.ShowChart
            ),
            KpiData(
                label = "Avg. Peak Power",
                value = if (sessions.isEmpty()) "--" else PowerDisplay.formatWatts(avgPeakPower, com.codecraft.volttrack.data.preferences.PowerUnit.WATTS, locale),
                icon = Icons.Default.Bolt
            ),
            KpiData(
                label = "Peak Temp",
                value = if (sessions.isEmpty()) "--" else "${String.format(locale, "%.1f", peakTemp)}°C",
                icon = Icons.Default.Thermostat
            )
        )
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(kpis) { kpi ->
            KpiCard(kpi)
        }
    }
}

private data class KpiData(val label: String, val value: String, val icon: ImageVector)

@Composable
private fun KpiCard(data: KpiData) {
    Surface(
        modifier = Modifier.widthIn(min = 130.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = data.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = data.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = data.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TimeOfDayDonutChart(dist: TimeOfDayDistribution) {
    val total = (dist.overnightCount + dist.morningCount + dist.afternoonCount + dist.eveningCount).coerceAtLeast(1).toFloat()
    
    val colors = listOf(
        Color(0xFF3F51B5), // Overnight
        Color(0xFF03A9F4), // Morning
        Color(0xFFFF9800), // Afternoon
        Color(0xFF607D8B)  // Evening
    )
    val labels = listOf("Overnight", "Morning", "Afternoon", "Evening")
    val values = listOf(dist.overnightCount, dist.morningCount, dist.afternoonCount, dist.eveningCount)

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -90f
                values.forEachIndexed { i, count ->
                    val sweep = (count.toFloat() / total) * 360f
                    if (sweep > 0) {
                        drawArc(
                            color = colors[i],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${total.toInt()}", style = MaterialTheme.typography.titleLarge)
                Text("Total", style = MaterialTheme.typography.labelSmall)
            }
        }
        
        Spacer(modifier = Modifier.width(32.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEachIndexed { i, count ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(colors[i], RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${labels[i]}: $count",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun XAxisLabels(bars: List<DayChartBar>, range: TimeRange) {
    // For large ranges, only show a few labels to avoid overlap
    val labelsToShow = if (range == TimeRange.ALL_TIME || range == TimeRange.THIRTY_DAYS) {
        val indices = listOf(0, bars.size / 2, bars.size - 1)
        bars.filterIndexed { index, _ -> index in indices }
    } else {
        bars
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (range == TimeRange.ALL_TIME || range == TimeRange.THIRTY_DAYS) {
            labelsToShow.forEachIndexed { idx, bar ->
                Text(
                    text = bar.labelShort,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = when(idx) {
                        0 -> TextAlign.Start
                        1 -> TextAlign.Center
                        else -> TextAlign.End
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            bars.forEach { bar ->
                Text(
                    text = bar.labelShort,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

private fun previewSampleSessions(): List<ChargingSession> {
    val now = System.currentTimeMillis()
    val h = 3_600_000L
    val day = 24 * h
    return listOf(
        ChargingSession(1, now - 5 * day, now - 5 * day + h, 20.0, 32.0, 12.0, null, 35.0),
        ChargingSession(2, now - 4 * day, now - 4 * day + 2 * h, 32.0, 78.0, 25.0, null, 38.0),
        ChargingSession(3, now - 3 * day, now - 3 * day + h, 50.0, 72.0, 18.0, null, 43.0),
        ChargingSession(4, now - 2 * day, now - 2 * day + 3 * h, 10.0, 95.0, 32.0, null, 32.0),
        ChargingSession(5, now - day, now - day + h, 20.0, 40.0, 15.0, null, 36.0),
        ChargingSession(6, now - 2 * h, now - h, 60.0, 85.0, 20.0, null, 37.5)
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
