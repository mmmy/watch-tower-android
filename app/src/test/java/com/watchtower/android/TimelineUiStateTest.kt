package com.watchtower.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineUiStateTest {

    @Test
    fun createsOneCompactTimelineRowPerConfiguredPeriod() {
        val group = WatchGroup(
            id = "group-1",
            name = "BTC Main",
            symbol = "BTCUSDT",
            periods = listOf("10D", "W", "720"),
            signalTypes = listOf("tdMd"),
            enabled = true
        )

        val rows = group.toTimelineRows()

        assertEquals(listOf("10D", "W", "720"), rows.map { it.period })
        assertTrue(rows.all { it.markers.isEmpty() })
    }

    @Test
    fun clampsTimelineMarkerSlotsToSixtyBarRange() {
        val row = PeriodTimelineRow(
            period = "60",
            markers = listOf(
                TimelineMarker(slot = -3, side = SignalSide.Bullish),
                TimelineMarker(slot = 12, side = SignalSide.Bearish),
                TimelineMarker(slot = 70, side = SignalSide.Bullish)
            )
        )

        assertEquals(listOf(0, 12, 59), row.normalizedMarkers.map { it.slot })
        assertEquals(SignalSide.Bearish, row.normalizedMarkers[1].side)
    }
}
