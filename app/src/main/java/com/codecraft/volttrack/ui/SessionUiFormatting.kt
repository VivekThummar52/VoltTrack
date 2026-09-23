package com.codecraft.volttrack.ui

import com.codecraft.volttrack.data.ChargingSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SessionDaySection(
    val label: String,
    val daySortKey: Long,
    val sessions: List<ChargingSession>
)

data class DayChargeSummary(
    val totalDurationMs: Long,
    val totalGainPercent: Double,
    val sessionCount: Int
)

object SessionUiFormatting {

    private val amPmTime: (Locale) -> SimpleDateFormat = { locale ->
        SimpleDateFormat("h:mm a", locale)
    }

    private val amPmDateTime: (Locale) -> SimpleDateFormat = { locale ->
        SimpleDateFormat("MMM d, yyyy • h:mm a", locale)
    }

    fun formatTimeAmPm(timeMs: Long, locale: Locale = Locale.getDefault()): String =
        amPmTime(locale).format(Date(timeMs))

    fun formatSessionStartDateTime(startTimeMs: Long, locale: Locale = Locale.getDefault()): String =
        amPmDateTime(locale).format(Date(startTimeMs))

    fun formatStartLineAmPm(session: ChargingSession, locale: Locale = Locale.getDefault()): String =
        formatSessionStartDateTime(session.startTime, locale)

    fun formatChargeCompletedLine(timeMs: Long, locale: Locale = Locale.getDefault()): String =
        "Charging completed: ${formatTimeAmPm(timeMs, locale)}"

    fun computeDayChargeSummary(sessions: List<ChargingSession>): DayChargeSummary {
        if (sessions.isEmpty()) {
            return DayChargeSummary(0L, 0.0, 0)
        }
        var duration = 0L
        var gain = 0.0
        for (s in sessions) {
            duration += (s.endTime - s.startTime).coerceAtLeast(0L)
            gain += (s.endPct - s.startPct)
        }
        return DayChargeSummary(duration, gain, sessions.size)
    }

    fun formatActiveSessionHeadline(
        startPct: Double,
        currentPct: Double,
        durationMs: Long,
        locale: Locale
    ): String {
        val gain = currentPct - startPct
        val sign = if (gain >= 0) "+" else ""
        return "So far $sign${String.format(locale, "%.2f", gain)}% in ${formatDurationWithSeconds(durationMs)}"
    }

    fun formatDurationWithSeconds(durationMs: Long): String {
        if (durationMs <= 0L) return "0s"
        val totalSec = durationMs / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return buildString {
            if (h > 0) {
                append("${h}h")
                if (m > 0) append(" ${m}m")
            } else {
                if (m > 0) append("${m}m ")
                append("${s}s")
            }
        }.trim()
    }

    fun groupSessionsByDay(sessions: List<ChargingSession>, locale: Locale = Locale.getDefault()): List<SessionDaySection> {
        if (sessions.isEmpty()) return emptyList()
        val sorted = sessions.sortedByDescending { it.startTime }
        val byDay = LinkedHashMap<Long, MutableList<ChargingSession>>()
        for (session in sorted) {
            val key = dayStartMillis(session.startTime, locale)
            byDay.getOrPut(key) { mutableListOf() }.add(session)
        }
        return byDay.map { (dayStart, list) ->
            SessionDaySection(
                label = daySectionLabel(dayStart, locale),
                daySortKey = dayStart,
                sessions = list
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

    private fun daySectionLabel(dayStartMillis: Long, locale: Locale): String {
        val now = Calendar.getInstance(locale)
        val day = Calendar.getInstance(locale).apply { timeInMillis = dayStartMillis }
        val yesterday = Calendar.getInstance(locale).apply {
            timeInMillis = now.timeInMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return when {
            isSameDay(now, day) -> "Today"
            isSameDay(yesterday, day) -> "Yesterday"
            else -> SimpleDateFormat("EEEE, MMMM d, yyyy", locale).format(Date(dayStartMillis))
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    /** First line for collapsed day header: time + cumulative %. */
    fun formatCollapsedSummaryLine1(summary: DayChargeSummary, locale: Locale): String {
        val dur = formatDurationWithSeconds(summary.totalDurationMs)
        val sign = if (summary.totalGainPercent >= 0) "+" else ""
        val pct = String.format(locale, "%.2f", summary.totalGainPercent)
        return "$dur on charger • $sign$pct% cumulative"
    }

    /** Second line: session count only. */
    fun formatCollapsedSummaryLine2(summary: DayChargeSummary): String {
        val n = summary.sessionCount
        val sessionWord = if (n == 1) "session" else "sessions"
        return "$n charging $sessionWord"
    }
}