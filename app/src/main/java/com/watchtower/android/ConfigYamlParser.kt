package com.watchtower.android

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

object ConfigYamlParser {

    fun parse(content: String): WatchTowerConfig {
        val root = parseRoot(content)
        val api = root.mapValue("api")
        val poll = root.mapValue("poll")
        val ui = root.mapValue("ui")

        val baseUrl = api.stringValue("base_url")
        val apiKey = api.stringValue("api_key")
        require(baseUrl.isNotBlank()) { "api.base_url 不能为空" }
        require(apiKey.isNotBlank()) { "api.api_key 不能为空" }

        return WatchTowerConfig(
            baseUrl = baseUrl,
            apiKey = apiKey,
            pollIntervalSecs = poll.intValue("interval_secs", defaultValue = 60).coerceAtLeast(1),
            pageSize = poll.intValue("page_size", defaultValue = 100).coerceIn(1, 100),
            notificationsEnabled = ui.booleanValue("notifications", defaultValue = true),
            soundEnabled = ui.booleanValue("sound", defaultValue = false),
            groups = root.groupsValue()
        )
    }

    fun dump(config: WatchTowerConfig): String {
        val root = mapOf(
            "api" to mapOf(
                "base_url" to config.baseUrl,
                "api_key" to config.apiKey
            ),
            "poll" to mapOf(
                "interval_secs" to config.pollIntervalSecs,
                "page_size" to config.pageSize
            ),
            "ui" to mapOf(
                "notifications" to config.notificationsEnabled,
                "sound" to config.soundEnabled
            ),
            "groups" to config.groups.map { group ->
                mapOf(
                    "id" to group.id,
                    "name" to group.name,
                    "symbol" to group.symbol,
                    "periods" to group.periods,
                    "signal_types" to group.signalTypes,
                    "view" to mapOf(
                        "show_active_only" to group.view.showActiveOnly
                    ),
                    "enabled" to group.enabled
                )
            }
        )
        return Yaml().dump(root)
    }

    private fun parseRoot(content: String): Map<String, Any?> {
        val yaml = Yaml(SafeConstructor(LoaderOptions()))
        val loaded = yaml.load<Any?>(content)
        require(loaded is Map<*, *>) { "配置文件必须是 YAML 对象" }
        return loaded.stringKeyMap()
    }

    private fun Map<String, Any?>.groupsValue(): List<WatchGroup> {
        val rawGroups = this["groups"] as? List<*> ?: return emptyList()
        return rawGroups.mapIndexed { index, rawGroup ->
            val group = (rawGroup as? Map<*, *>)?.stringKeyMap()
                ?: throw IllegalArgumentException("groups[$index] 必须是对象")
            WatchGroup(
                id = group.stringValue("id", defaultValue = "group-${index + 1}"),
                name = group.stringValue("name", defaultValue = "Group ${index + 1}"),
                symbol = group.stringValue("symbol"),
                periods = group.stringListValue("periods"),
                signalTypes = group.stringListValue("signal_types"),
                enabled = group.booleanValue("enabled", defaultValue = true),
                view = WatchGroupView(
                    showActiveOnly = group.mapValue("view")
                        .booleanValue("show_active_only", defaultValue = false)
                )
            )
        }
    }

    private fun Map<String, Any?>.mapValue(key: String): Map<String, Any?> {
        val value = this[key] ?: return emptyMap()
        require(value is Map<*, *>) { "$key 必须是对象" }
        return value.stringKeyMap()
    }

    private fun Map<String, Any?>.stringValue(
        key: String,
        defaultValue: String = ""
    ): String = this[key]?.toString()?.trim() ?: defaultValue

    private fun Map<String, Any?>.intValue(
        key: String,
        defaultValue: Int
    ): Int = when (val value = this[key]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: defaultValue
        else -> defaultValue
    }

    private fun Map<String, Any?>.booleanValue(
        key: String,
        defaultValue: Boolean
    ): Boolean = when (val value = this[key]) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true)
        else -> defaultValue
    }

    private fun Map<String, Any?>.stringListValue(key: String): List<String> {
        val value = this[key] as? List<*> ?: return emptyList()
        return value.map { it.toString() }
    }

    private fun Map<*, *>.stringKeyMap(): Map<String, Any?> =
        entries.associate { (key, value) -> key.toString() to value }
}
