package com.volttrack.app.ui.charts

import com.volttrack.app.data.ChargingSession
import java.util.Calendar
import java.util.Locale

data class DayChartBar(
    /** Start of calendar day in local timezone (ms). */
    val dayStartMillis: Long,
    val labelShort: String,
    /** Sum of session wall durations with start time on this day. */
    val totalDurationMs: Long,
    val sessionCount: Int
)

/**
 * Last [dayCount] calendar days (including today), oldest first, for bar charts.
 */
fun aggregateChargeTimeByDay(
    sessions: List<ChargingSession>,
    locale: Locale = Locale.getDefault(),
    dayCount: Int = 7
): List<DayChartBar> {
    if (dayCount <= 0) return emptyList()
    val cal = Calendar.getInstance(locale)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val todayStart = cal.timeInMillis
    val dayStarts = LongArray(dayCount)
    val labels = Array(dayCount) { "" }
    for (idx in 0 until dayCount) {
        cal.timeInMillis = todayStart
        cal.add(Calendar.DAY_OF_YEAR, -(dayCount - 1 - idx))
        val start = cal.timeInMillis
        dayStarts[idx] = start
        val dayNum = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, locale) ?: ""
        labels[idx] = "$month $dayNum"
    }
    val durationByDay = LongArray(dayCount) { 0L }
    val countByDay = IntArray(dayCount) { 0 }
    for (s in sessions) {
        val startDay = dayStartMillis(s.startTime, locale)
        val idx = dayStarts.indexOf(startDay)
        if (idx >= 0) {
            durationByDay[idx] += (s.endTime - s.startTime).coerceAtLeast(0L)
            countByDay[idx] += 1
        }
    }
    return dayStarts.indices.map { i ->
        DayChartBar(
            dayStartMillis = dayStarts[i],
            labelShort = labels[i],
            totalDurationMs = durationByDay[i],
            sessionCount = countByDay[i]
        )
    }
}

private fun dayStartMillis(timeMs: Long, locale: Locale): Long {
    val c = Calendar.getInstance(locale).apply { timeInMillis = timeMs }
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}
