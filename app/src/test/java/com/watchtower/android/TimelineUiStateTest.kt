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

    @Test
    fun activeOnlyViewShowsRowsWithVisibleMarkersOnly() {
        val nowMillis = 60L * 60 * 1000
        val group = WatchGroup(
            id = "btc",
            name = "BTC",
            symbol = "BTCUSDT",
            periods = listOf("60", "15", "5"),
            signalTypes = listOf("tdMd"),
            enabled = true,
            view = WatchGroupView(showActiveOnly = true)
        )
        val alerts = listOf(
            SignalAlert("BTCUSDT", "60", "tdMd", SignalSide.Bullish, nowMillis, read = false),
            SignalAlert("BTCUSDT", "15", "tdMd", SignalSide.Bearish, nowMillis - 61L * 15 * 60 * 1000, read = false),
            SignalAlert("ETHUSDT", "5", "tdMd", SignalSide.Bullish, nowMillis, read = false)
        )

        val rows = group.toVisibleTimelineRows(alerts, nowMillis)

        assertEquals(listOf("60"), rows.map { it.period })
    }

    @Test
    fun activeOnlyViewKeepsConfiguredRowsWhenDisabled() {
        val group = WatchGroup(
            id = "btc",
            name = "BTC",
            symbol = "BTCUSDT",
            periods = listOf("60", "15"),
            signalTypes = listOf("tdMd"),
            enabled = true,
            view = WatchGroupView(showActiveOnly = false)
        )

        val rows = group.toVisibleTimelineRows(alerts = emptyList(), nowMillis = 0L)

        assertEquals(listOf("60", "15"), rows.map { it.period })
    }

    @Test
    fun progressRecentFirstSortsRowsByRightmostVisibleMarker() {
        val group = WatchGroup(
            id = "btc",
            name = "BTC",
            symbol = "BTCUSDT",
            periods = listOf("60", "15", "5", "1"),
            signalTypes = listOf("tdMd"),
            enabled = true,
            view = WatchGroupView(rowSortMode = WatchGroupRowSortMode.ProgressRecentFirst)
        )
        val rows = group.sortTimelineRows(
            listOf(
                PeriodTimelineRow("60", markers = listOf(TimelineMarker(slot = 58, side = SignalSide.Bullish))),
                PeriodTimelineRow("15", markers = listOf(TimelineMarker(slot = 52, side = SignalSide.Bearish))),
                PeriodTimelineRow("5", markers = emptyList()),
                PeriodTimelineRow("1", markers = listOf(TimelineMarker(slot = 59, side = SignalSide.Bullish)))
            )
        )

        assertEquals(listOf("1", "60", "15", "5"), rows.map { it.period })
    }

    @Test
    fun progressRecentFirstKeepsConfiguredOrderForTiesAndEmptyRows() {
        val group = WatchGroup(
            id = "btc",
            name = "BTC",
            symbol = "BTCUSDT",
            periods = listOf("60", "15", "5"),
            signalTypes = listOf("tdMd"),
            enabled = true,
            view = WatchGroupView(rowSortMode = WatchGroupRowSortMode.ProgressRecentFirst)
        )
        val rows = group.sortTimelineRows(
            listOf(
                PeriodTimelineRow("60", markers = listOf(TimelineMarker(slot = 40, side = SignalSide.Bullish))),
                PeriodTimelineRow("15", markers = listOf(TimelineMarker(slot = 40, side = SignalSide.Bearish))),
                PeriodTimelineRow("5", markers = emptyList())
            )
        )

        assertEquals(listOf("60", "15", "5"), rows.map { it.period })
    }
}
