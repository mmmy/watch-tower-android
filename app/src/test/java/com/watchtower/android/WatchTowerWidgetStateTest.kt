package com.watchtower.android

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchTowerWidgetStateTest {

    @Test
    fun buildsRowsFromConfiguredWidgetGroupForThirtyMinuteAndAbovePeriods() {
        val hourMillis = 60L * 60 * 1000
        val nowMillis = 100L * hourMillis
        val config = WatchTowerConfig.default().copy(
            widgetGroupId = "eth",
            groups = listOf(
                WatchGroup(
                    id = "btc",
                    name = "BTC Main",
                    symbol = "BTCUSDT",
                    periods = listOf("60"),
                    signalTypes = listOf("tdMd"),
                    enabled = true
                ),
                WatchGroup(
                    id = "eth",
                    name = "ETH Main",
                    symbol = "ETHUSDT",
                    periods = listOf("60", "30", "20", "15S"),
                    signalTypes = listOf("tdMd", "vegas"),
                    enabled = true
                )
            )
        )
        val alerts = listOf(
            SignalAlert("BTCUSDT", "60", "tdMd", SignalSide.Bullish, nowMillis, read = false),
            SignalAlert("ETHUSDT", "20", "tdMd", SignalSide.Bullish, nowMillis, read = false),
            SignalAlert("ETHUSDT", "15S", "tdMd", SignalSide.Bullish, nowMillis, read = false),
            SignalAlert("ETHUSDT", "60", "tdMd", SignalSide.Bearish, nowMillis - 2L * hourMillis, read = true),
            SignalAlert("ETHUSDT", "30", "vegas", SignalSide.Bullish, nowMillis, read = false)
        )

        val state = config.toWidgetState(alerts = alerts, nowMillis = nowMillis)

        assertEquals("ETH Main", state.groupName)
        assertEquals("ETHUSDT", state.symbol)
        assertEquals(1, state.unreadCount)
        assertEquals(
            listOf(
                WidgetAlertRow("30", "vegas", SignalSide.Bullish, barsAgo = 0, unread = true),
                WidgetAlertRow("60", "tdMd", SignalSide.Bearish, barsAgo = 2, unread = false)
            ),
            state.rows
        )
    }

    @Test
    fun includesThirtyMinuteAndAbovePeriodsBeyondTimelineWindow() {
        val periodMillis = 240L * 60 * 1000
        val nowMillis = 200L * periodMillis
        val config = WatchTowerConfig.default().copy(
            widgetGroupId = "btc",
            groups = listOf(
                WatchGroup(
                    id = "btc",
                    name = "BTC Main",
                    symbol = "BTCUSDT",
                    periods = listOf("240"),
                    signalTypes = listOf("vegas"),
                    enabled = true,
                    timelineBars = 60
                )
            )
        )
        val alerts = listOf(
            SignalAlert(
                symbol = "BTCUSDT",
                period = "240",
                signalType = "vegas",
                side = SignalSide.Bullish,
                triggerTimeMillis = nowMillis - 171L * periodMillis,
                read = false
            )
        )

        val state = config.toWidgetState(alerts = alerts, nowMillis = nowMillis)

        assertEquals(
            listOf(WidgetAlertRow("240", "vegas", SignalSide.Bullish, barsAgo = 171, unread = true)),
            state.rows
        )
    }

    @Test
    fun fallsBackToFirstEnabledGroupWhenWidgetGroupIsMissing() {
        val config = WatchTowerConfig.default().copy(
            widgetGroupId = "missing",
            groups = listOf(
                WatchGroup("disabled", "Disabled", "SOLUSDT", listOf("60"), listOf("tdMd"), enabled = false),
                WatchGroup("btc", "BTC Main", "BTCUSDT", listOf("60"), listOf("tdMd"), enabled = true)
            )
        )

        val state = config.toWidgetState(alerts = emptyList(), nowMillis = 0L)

        assertEquals("BTC Main", state.groupName)
        assertEquals("BTCUSDT", state.symbol)
        assertEquals(emptyList<WidgetAlertRow>(), state.rows)
    }

    @Test
    fun formatsCompactWidgetTextWithoutRepeatedGroupDetails() {
        val state = WatchTowerWidgetState(
            groupName = "BTC Main",
            symbol = "BTCUSDT",
            unreadCount = 1,
            rows = listOf(
                WidgetAlertRow("30", "vegas", SignalSide.Bullish, barsAgo = 0, unread = true),
                WidgetAlertRow("60", "tdMd", SignalSide.Bearish, barsAgo = 2, unread = false)
            )
        )

        assertEquals("BTC Main", state.titleText)
        assertEquals("同步 12:30", state.syncText("12:30"))
        assertEquals("30 0K  60 2K", state.flowText)
    }

    @Test
    fun formatsUpToTwentyWidgetRows() {
        val state = WatchTowerWidgetState(
            groupName = "BTC Main",
            symbol = "BTCUSDT",
            unreadCount = 21,
            rows = (0..20).map { index ->
                WidgetAlertRow("60", "vegas", SignalSide.Bullish, barsAgo = index, unread = true)
            }
        )

        val expectedText = (0..19).joinToString("  ") { index -> "60 ${index}K" }

        assertEquals(expectedText, state.flowText)
    }
}
