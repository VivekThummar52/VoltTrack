package com.codecraft.volttrack.ui.charts

import com.codecraft.volttrack.data.ChargingSession
import java.util.Calendar
import java.util.Locale

data class DayChartBar(
    /** Start of calendar day in local timezone (ms). */
    val dayStartMillis: Long,
    val labelShort: String,
    /** Sum of session wall durations with start time on this day. */
    val totalDurationMs: Long,
    val totalGainPct: Double,
    val maxWatts: Double,
    val maxTemp: Double,
    val sessionCount: Int
)

data class TimeOfDayDistribution(
    val overnightCount: Int,
    val morningCount: Int,
    val afternoonCount: Int,
    val eveningCount: Int
)

/**
 * Last [dayCount] calendar days (including today), oldest first, for bar charts.
 * If dayCount is -1, it aggregates for all sessions.
 */
fun aggregateChargeTimeByDay(
    sessions: List<ChargingSession>,
    locale: Locale = Locale.getDefault(),
    dayCount: Int = 7
): List<DayChartBar> {
    if (dayCount == 0) return emptyList()
    
    val cal = Calendar.getInstance(locale)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val todayStart = cal.timeInMillis
    
    val actualDayCount = if (dayCount == -1) {
        if (sessions.isEmpty()) 7 else {
            val firstSessionStart = sessions.minOf { it.startTime }
            val firstDayStart = dayStartMillis(firstSessionStart, locale)
            val diff = todayStart - firstDayStart
            ((diff / (24 * 60 * 60 * 1000L)).toInt() + 1).coerceAtLeast(7)
        }
    } else dayCount

    val dayStarts = LongArray(actualDayCount)
    val labels = Array(actualDayCount) { "" }
    for (idx in 0 until actualDayCount) {
        cal.timeInMillis = todayStart
        cal.add(Calendar.DAY_OF_YEAR, -(actualDayCount - 1 - idx))
        val start = cal.timeInMillis
        dayStarts[idx] = start
        val dayNum = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, locale) ?: ""
        labels[idx] = "$month $dayNum"
    }
    val durationByDay = LongArray(dayCount) { 0L }
    val gainByDay = DoubleArray(dayCount) { 0.0 }
    val maxWattsByDay = DoubleArray(dayCount) { 0.0 }
    val maxTempByDay = DoubleArray(dayCount) { 0.0 }
    val countByDay = IntArray(dayCount) { 0 }

    for (s in sessions) {
        val startDay = dayStartMillis(s.startTime, locale)
        val idx = dayStarts.indexOf(startDay)
        if (idx >= 0) {
            durationByDay[idx] += (s.endTime - s.startTime).coerceAtLeast(0L)
            gainByDay[idx] += (s.endPct - s.startPct).coerceAtLeast(0.0)
            if (s.maxWatts > maxWattsByDay[idx]) maxWattsByDay[idx] = s.maxWatts
            if (s.maxTemp > maxTempByDay[idx]) maxTempByDay[idx] = s.maxTemp
            countByDay[idx] += 1
        }
    }
    return dayStarts.indices.map { i ->
        DayChartBar(
            dayStartMillis = dayStarts[i],
            labelShort = labels[i],
            totalDurationMs = durationByDay[i],
            totalGainPct = gainByDay[i],
            maxWatts = maxWattsByDay[i],
            maxTemp = maxTempByDay[i],
            sessionCount = countByDay[i]
        )
    }
}

fun aggregateTimeOfDay(sessions: List<ChargingSession>, locale: Locale = Locale.getDefault()): TimeOfDayDistribution {
    var overnight = 0
    var morning = 0
    var afternoon = 0
    var evening = 0
    val cal = Calendar.getInstance(locale)
    for (s in sessions) {
        cal.timeInMillis = s.startTime
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 0..5 -> overnight++
            in 6..11 -> morning++
            in 12..17 -> afternoon++
            else -> evening++
        }
    }
    return TimeOfDayDistribution(overnight, morning, afternoon, evening)
}

private fun dayStartMillis(timeMs: Long, locale: Locale): Long {
    val c = Calendar.getInstance(locale).apply { timeInMillis = timeMs }
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}
