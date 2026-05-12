package com.watchtower.android

import android.content.Context

interface ConfigStorage {
    fun read(): String?

    fun write(value: String)
}

class WatchTowerConfigStore(
    private val storage: ConfigStorage
) {
    fun load(): WatchTowerConfig {
        val savedConfig = storage.read() ?: return WatchTowerConfig.default()
        return runCatching { ConfigYamlParser.parse(savedConfig) }
            .getOrDefault(WatchTowerConfig.default())
    }

    fun save(config: WatchTowerConfig) {
        storage.write(ConfigYamlParser.dump(config))
    }
}

class SharedPreferencesConfigStorage(context: Context) : ConfigStorage {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(KEY_CONFIG_YAML, null)

    override fun write(value: String) {
        preferences.edit()
            .putString(KEY_CONFIG_YAML, value)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "watch_tower_config"
        const val KEY_CONFIG_YAML = "config_yaml"
    }
}
