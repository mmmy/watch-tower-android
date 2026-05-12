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

    @Test
    fun countsUnreadAlertsForGroupOnly() {
        val group = WatchGroup(
            id = "btc",
            name = "BTC",
            symbol = "BTCUSDT",
            periods = listOf("60", "15"),
            signalTypes = listOf("tdMd"),
            enabled = true
        )
        val alerts = listOf(
            SignalAlert("BTCUSDT", "60", "tdMd", SignalSide.Bullish, 1L, read = false),
            SignalAlert("BTCUSDT", "15", "tdMd", SignalSide.Bearish, 2L, read = true),
            SignalAlert("BTCUSDT", "5", "tdMd", SignalSide.Bullish, 3L, read = false),
            SignalAlert("ETHUSDT", "60", "tdMd", SignalSide.Bullish, 4L, read = false),
            SignalAlert("BTCUSDT", "60", "vegas", SignalSide.Bearish, 5L, read = false)
        )

        assertEquals(1, group.unreadCount(alerts))
    }
}
