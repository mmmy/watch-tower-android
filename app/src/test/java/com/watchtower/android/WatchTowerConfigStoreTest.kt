package com.watchtower.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WatchTowerConfigStoreTest {

    @Test
    fun loadsDefaultConfigWhenNothingWasSaved() {
        val store = WatchTowerConfigStore(FakeConfigStorage())

        val config = store.load()

        assertEquals(WatchTowerConfig.default(), config)
    }

    @Test
    fun savesAndLoadsConfigWithGroups() {
        val storage = FakeConfigStorage()
        val store = WatchTowerConfigStore(storage)
        val config = WatchTowerConfig(
            baseUrl = "http://localhost:5008",
            apiKey = "secret",
            pollIntervalSecs = 30,
            pageSize = 80,
            notificationsEnabled = false,
            soundEnabled = true,
            widgetGroupId = "group-1",
            groups = listOf(
                WatchGroup(
                    id = "group-1",
                    name = "BTC Main",
                    symbol = "BTCUSDT",
                    periods = listOf("10D", "W", "720"),
                    signalTypes = listOf("tdMd"),
                    enabled = true
                )
            )
        )

        store.save(config)

        assertEquals(config, store.load())
    }

    @Test
    fun fallsBackToDefaultWhenSavedConfigIsInvalid() {
        val store = WatchTowerConfigStore(FakeConfigStorage("not: [valid"))

        val config = store.load()

        assertFalse(config.isComplete)
        assertEquals(WatchTowerConfig.default(), config)
    }

    private class FakeConfigStorage(
        private var value: String? = null
    ) : ConfigStorage {
        override fun read(): String? = value

        override fun write(value: String) {
            this.value = value
        }
    }
}
