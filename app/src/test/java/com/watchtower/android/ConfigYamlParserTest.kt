package com.watchtower.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigYamlParserTest {

    @Test
    fun parsesAppConfigFromYaml() {
        val yaml = """
            api:
              base_url: http://139.180.203.107:5008
              api_key: gouge2014
            poll:
              interval_secs: 60
              page_size: 100
            ui:
              notifications: true
              sound: true
              widget_group_id: group-2
            groups:
            - id: group-1
              name: BTC Main
              symbol: BTCUSDT
              timeline_bars: 120
              view:
                show_active_only: true
                row_sort_mode: progress_recent_first
              periods:
              - 10D
              - W
              - '720'
              signal_types:
              - tdMd
              enabled: true
            - id: group-2
              name: Btc 2
              symbol: BTCUSDT
              periods:
              - D
              - '60'
              signal_types:
              - vegas
              enabled: false
        """.trimIndent()

        val config = ConfigYamlParser.parse(yaml)

        assertEquals("http://139.180.203.107:5008", config.baseUrl)
        assertEquals("gouge2014", config.apiKey)
        assertEquals(60, config.pollIntervalSecs)
        assertEquals(100, config.pageSize)
        assertTrue(config.notificationsEnabled)
        assertTrue(config.soundEnabled)
        assertEquals("group-2", config.widgetGroupId)
        assertEquals(2, config.groups.size)
        assertEquals("BTC Main", config.groups[0].name)
        assertEquals(120, config.groups[0].timelineBars)
        assertEquals(listOf("10D", "W", "720"), config.groups[0].periods)
        assertEquals(listOf("tdMd"), config.groups[0].signalTypes)
        assertTrue(config.groups[0].view.showActiveOnly)
        assertEquals(WatchGroupRowSortMode.ProgressRecentFirst, config.groups[0].view.rowSortMode)
        assertTrue(config.groups[0].enabled)
        assertEquals("vegas", config.groups[1].signalTypes.single())
        assertEquals(60, config.groups[1].timelineBars)
        assertFalse(config.groups[1].view.showActiveOnly)
        assertEquals(WatchGroupRowSortMode.ConfigOrder, config.groups[1].view.rowSortMode)
        assertFalse(config.groups[1].enabled)
    }

    @Test
    fun dumpsGroupViewSettingsToYaml() {
        val config = WatchTowerConfig.default().copy(
            baseUrl = "https://example.com",
            apiKey = "secret",
            widgetGroupId = "btc",
            groups = listOf(
                WatchGroup(
                    id = "btc",
                    name = "BTC Main",
                    symbol = "BTCUSDT",
                    periods = listOf("60"),
                    signalTypes = listOf("vegas"),
                    enabled = true,
                    timelineBars = 120,
                    view = WatchGroupView(
                        showActiveOnly = true,
                        rowSortMode = WatchGroupRowSortMode.ProgressRecentFirst
                    )
                )
            )
        )

        val reparsed = ConfigYamlParser.parse(ConfigYamlParser.dump(config))

        assertEquals(120, reparsed.groups.single().timelineBars)
        assertEquals("btc", reparsed.widgetGroupId)
        assertTrue(reparsed.groups.single().view.showActiveOnly)
        assertEquals(WatchGroupRowSortMode.ProgressRecentFirst, reparsed.groups.single().view.rowSortMode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsConfigWithoutApiKey() {
        ConfigYamlParser.parse(
            """
                api:
                  base_url: http://localhost:5008
            """.trimIndent()
        )
    }
}
