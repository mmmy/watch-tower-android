package com.watchtower.android

data class WidgetAlertRow(
    val period: String,
    val signalType: String,
    val side: SignalSide,
    val barsAgo: Int,
    val unread: Boolean
) {
    val periodText: String
        get() = period

    val barsAgoText: String
        get() = "${barsAgo}K"

    val displayText: String
        get() = "$periodText $barsAgoText"
}

data class WatchTowerWidgetState(
    val groupName: String,
    val symbol: String,
    val unreadCount: Int,
    val rows: List<WidgetAlertRow>
) {
    val titleText: String
        get() = if (groupName.isNotBlank()) groupName else symbol

    fun syncText(lastSyncText: String): String =
        "同步 $lastSyncText"

    val rowsText: String
        get() = rows.take(WIDGET_MAX_ROWS)
            .joinToString("\n") { row -> row.displayText }
            .ifBlank { "暂无警报" }

    val flowText: String
        get() = rows.take(WIDGET_MAX_ROWS)
            .joinToString("  ") { row -> row.displayText }
            .ifBlank { "暂无警报" }
}

fun WatchTowerConfig.toWidgetState(
    alerts: List<SignalAlert>,
    nowMillis: Long = System.currentTimeMillis()
): WatchTowerWidgetState {
    val group = widgetGroup()
    if (group == null) {
        return WatchTowerWidgetState(
            groupName = "Watch Tower",
            symbol = "未配置分组",
            unreadCount = 0,
            rows = emptyList()
        )
    }

    val rows = alerts.mapNotNull { alert ->
        if (!group.matches(alert)) return@mapNotNull null
        val periodMillis = periodDurationMillis(alert.period) ?: return@mapNotNull null
        if (periodMillis < WIDGET_MIN_PERIOD_MILLIS) return@mapNotNull null
        val barsAgo = barsAgoForPeriod(alert.period, alert.triggerTimeMillis, nowMillis)
            ?: return@mapNotNull null
        if (barsAgo >= coerceTimelineBars(group.timelineBars)) return@mapNotNull null

        WidgetAlertRow(
            period = alert.period,
            signalType = alert.signalType,
            side = alert.side,
            barsAgo = barsAgo,
            unread = !alert.read
        )
    }.sortedWith(
        compareBy<WidgetAlertRow> { it.barsAgo }
            .thenBy { group.periods.indexOf(it.period).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
            .thenBy { it.signalType }
    )

    return WatchTowerWidgetState(
        groupName = group.name,
        symbol = group.symbol,
        unreadCount = rows.count { it.unread },
        rows = rows
    )
}

private fun WatchTowerConfig.widgetGroup(): WatchGroup? =
    groups.firstOrNull { it.id == resolvedWidgetGroupId() }
        ?: groups.firstOrNull()

fun WatchTowerConfig.resolvedWidgetGroupId(): String? =
    groups.firstOrNull { it.id == widgetGroupId && it.enabled }?.id
        ?: groups.firstOrNull { it.enabled }?.id

private fun WatchGroup.matches(alert: SignalAlert): Boolean =
    alert.symbol == symbol &&
        alert.period in periods &&
        alert.signalType in signalTypes

const val WIDGET_MAX_ROWS = 6
private const val WIDGET_MIN_PERIOD_MILLIS = 30L * 60 * 1000
