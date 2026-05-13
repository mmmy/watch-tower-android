package com.watchtower.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class SignalApiTest {

    @Test
    fun buildsSignalRequestFromEnabledGroups() {
        val config = WatchTowerConfig.default().copy(
            pageSize = 100,
            groups = listOf(
                WatchGroup(
                    id = "btc",
                    name = "BTC",
                    symbol = "BTCUSDT",
                    periods = listOf("D", "60"),
                    signalTypes = listOf("tdMd"),
                    enabled = true
                ),
                WatchGroup(
                    id = "eth",
                    name = "ETH",
                    symbol = "ETHUSDT",
                    periods = listOf("60"),
                    signalTypes = listOf("vegas"),
                    enabled = true
                ),
                WatchGroup(
                    id = "off",
                    name = "Off",
                    symbol = "SOLUSDT",
                    periods = listOf("15"),
                    signalTypes = listOf("tdMd"),
                    enabled = false
                )
            )
        )

        val request = SignalRequest.fromConfig(config)

        assertEquals("BTCUSDT,ETHUSDT", request.symbols)
        assertEquals("D,60", request.periods)
        assertEquals("tdMd,vegas", request.signalTypes)
        assertEquals(100, request.pageSize)
    }

    @Test
    fun parsesSignalResponseData() {
        val response = """
            {
              "total": 1,
              "page": 1,
              "pageSize": 100,
              "data": [
                {
                  "symbol": "BTCUSDT",
                  "period": "60",
                  "t": 1710000000000,
                  "signals": {
                    "tdMd": { "sd": 1, "t": 1710003600000, "read": false },
                    "vegas": { "sd": -1, "t": 1710000000000, "read": true }
                  }
                }
              ]
            }
        """.trimIndent()

        val alerts = SignalResponseParser.parse(response)

        assertEquals(2, alerts.size)
        assertEquals("BTCUSDT", alerts[0].symbol)
        assertEquals("60", alerts[0].period)
        assertEquals("tdMd", alerts[0].signalType)
        assertEquals(SignalSide.Bullish, alerts[0].side)
        assertEquals(1710003600000L, alerts[0].triggerTimeMillis)
        assertFalse(alerts[0].read)
        assertEquals(SignalSide.Bearish, alerts[1].side)
        assertTrue(alerts[1].read)
    }

    @Test
    fun serializesReadStatusBatchRequestFromAlerts() {
        val alert = SignalAlert(
            symbol = "BTCUSDT",
            period = "60",
            signalType = "tdMd",
            side = SignalSide.Bullish,
            triggerTimeMillis = 1710003600000L,
            read = false
        )

        val json = JSONObject(SignalReadStatusBatchRequest.fromAlerts(listOf(alert), read = true).toJson())
        val item = json.getJSONArray("items").getJSONObject(0)

        assertEquals(1, json.getJSONArray("items").length())
        assertEquals("BTCUSDT", item.getString("symbol"))
        assertEquals("60", item.getString("period"))
        assertEquals("tdMd", item.getString("signalType"))
        assertTrue(item.getBoolean("read"))
    }

    @Test
    fun parsesReadStatusBatchResponseResults() {
        val response = """
            {
              "success": 1,
              "failed": 1,
              "results": [
                {
                  "symbol": "BTCUSDT",
                  "period": "60",
                  "signalType": "tdMd",
                  "read": true,
                  "success": true
                },
                {
                  "symbol": "ETHUSDT",
                  "period": "15",
                  "signalType": "vegas",
                  "read": true,
                  "success": false,
                  "reason": "signal_not_found"
                }
              ]
            }
        """.trimIndent()

        val result = SignalReadStatusBatchResponseParser.parse(response)

        assertEquals(1, result.success)
        assertEquals(1, result.failed)
        assertEquals(2, result.results.size)
        assertTrue(result.results[0].success)
        assertEquals("BTCUSDT", result.results[0].symbol)
        assertFalse(result.results[1].success)
        assertEquals("signal_not_found", result.results[1].reason)
    }

    @Test
    fun mapsSignalsToGroupTimelineRows() {
        val now = 1710007200000L
        val group = WatchGroup(
            id = "btc",
            name = "BTC",
            symbol = "BTCUSDT",
            periods = listOf("60", "15"),
            signalTypes = listOf("tdMd"),
            enabled = true
        )
        val alerts = listOf(
            SignalAlert(
                symbol = "BTCUSDT",
                period = "60",
                signalType = "tdMd",
                side = SignalSide.Bullish,
                triggerTimeMillis = now - (2 * 60 * 60 * 1000L),
                read = false
            ),
            SignalAlert(
                symbol = "BTCUSDT",
                period = "15",
                signalType = "tdMd",
                side = SignalSide.Bearish,
                triggerTimeMillis = now - (61 * 15 * 60 * 1000L),
                read = false
            )
        )

        val rows = group.toTimelineRows(alerts, now)

        assertEquals(57, rows[0].markers.single().slot)
        assertEquals(SignalSide.Bullish, rows[0].markers.single().side)
        assertTrue(rows[0].unread)
        assertTrue(rows[1].markers.isEmpty())
        assertFalse(rows[1].unread)
    }
}
