package com.codecraft.volttrack.ui.charts

import com.google.common.truth.Truth.assertThat
import com.codecraft.volttrack.data.ChargingSession
import org.junit.Test
import java.util.Locale
import java.util.concurrent.TimeUnit

class ChartAggregationTest {

    private val locale = Locale.US

    @Test
    fun `empty sessions still returns seven day buckets`() {
        val bars = aggregateChargeTimeByDay(emptyList(), locale, dayCount = 7)
        assertThat(bars).hasSize(7)
        assertThat(bars.all { it.totalDurationMs == 0L && it.sessionCount == 0 }).isTrue()
    }

    @Test
    fun `sums duration for sessions on same calendar day`() {
        val cal = java.util.Calendar.getInstance(locale)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 12)
        cal.set(java.util.Calendar.MINUTE, 0)
        val noonToday = cal.timeInMillis
        val oneHour = TimeUnit.HOURS.toMillis(1)
        val sessions = listOf(
            ChargingSession(
                id = 0,
                startTime = noonToday,
                endTime = noonToday + oneHour,
                startPct = 10.0,
                endPct = 20.0,
                maxWatts = 5.0,
                chargeCompletedAtMs = null
            ),
            ChargingSession(
                id = 0,
                startTime = noonToday + oneHour,
                endTime = noonToday + 2 * oneHour,
                startPct = 20.0,
                endPct = 30.0,
                maxWatts = 5.0,
                chargeCompletedAtMs = null
            )
        )
        val bars = aggregateChargeTimeByDay(sessions, locale, dayCount = 7)
        val todayBar = bars.maxByOrNull { it.totalDurationMs }!!
        assertThat(todayBar.totalDurationMs).isEqualTo(2 * oneHour)
        assertThat(todayBar.sessionCount).isEqualTo(2)
    }
}
