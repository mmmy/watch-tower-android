package com.watchtower.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTowerUiStateTest {

    @Test
    fun defaultConfigStartsUnconfiguredWithPrdDefaults() {
        val config = WatchTowerConfig.default()

        assertEquals("", config.baseUrl)
        assertEquals("", config.apiKey)
        assertEquals(60, config.pollIntervalSecs)
        assertEquals(100, config.pageSize)
        assertTrue(config.notificationsEnabled)
        assertFalse(config.soundEnabled)
        assertFalse(config.isComplete)
    }

    @Test
    fun configIsCompleteWhenApiConnectionFieldsAreSet() {
        val config = WatchTowerConfig.default().copy(
            baseUrl = "https://example.com",
            apiKey = "secret"
        )

        assertTrue(config.isComplete)
    }

    @Test
    fun configPageCanReturnOnlyAfterRequiredApiFieldsAreSet() {
        val missingConfig = WatchTowerConfig.default()
        val completeConfig = missingConfig.copy(
            baseUrl = "https://example.com",
            apiKey = "secret"
        )

        assertFalse(missingConfig.canLeaveConfigPage)
        assertTrue(completeConfig.canLeaveConfigPage)
    }

    @Test
    fun statusBarSummarizesHealthyPollState() {
        val status = MonitorStatus(
            unreadCount = 3,
            lastFetchText = "12:30",
            lastSuccessText = "12:30",
            connectionState = ConnectionState.Success
        )

        assertEquals("未读 3 | 刚刚更新 12:30 | 成功", status.summaryText)
    }

    @Test
    fun statusBarSummarizesMissingConfigBeforePolling() {
        val status = MonitorStatus(
            unreadCount = 0,
            lastFetchText = null,
            lastSuccessText = null,
            connectionState = ConnectionState.NotConfigured
        )

        assertEquals("未读 0 | 尚未刷新 | 需要配置", status.summaryText)
    }

    @Test
    fun manualRefreshIsAvailableOnlyWhenConfiguredAndIdle() {
        val configured = WatchTowerConfig.default().copy(
            baseUrl = "https://example.com",
            apiKey = "secret"
        )

        assertTrue(configured.canManualRefresh(isRefreshing = false))
        assertFalse(configured.canManualRefresh(isRefreshing = true))
        assertFalse(WatchTowerConfig.default().canManualRefresh(isRefreshing = false))
    }
}
