package com.watchtower.android

data class WatchTowerConfig(
    val baseUrl: String,
    val apiKey: String,
    val pollIntervalSecs: Int,
    val pageSize: Int,
    val notificationsEnabled: Boolean,
    val soundEnabled: Boolean,
    val groups: List<WatchGroup>
) {
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    val canLeaveConfigPage: Boolean
        get() = isComplete

    companion object {
        fun default() = WatchTowerConfig(
            baseUrl = "",
            apiKey = "",
            pollIntervalSecs = 60,
            pageSize = 100,
            notificationsEnabled = true,
            soundEnabled = false,
            groups = emptyList()
        )
    }
}

fun WatchTowerConfig.canManualRefresh(isRefreshing: Boolean): Boolean =
    isComplete && !isRefreshing

data class WatchGroup(
    val id: String,
    val name: String,
    val symbol: String,
    val periods: List<String>,
    val signalTypes: List<String>,
    val enabled: Boolean,
    val timelineBars: Int = DEFAULT_TIMELINE_BARS,
    val view: WatchGroupView = WatchGroupView()
)

data class WatchGroupView(
    val showActiveOnly: Boolean = false,
    val rowSortMode: WatchGroupRowSortMode = WatchGroupRowSortMode.ConfigOrder
)

enum class WatchGroupRowSortMode(val configValue: String) {
    ConfigOrder("config_order"),
    ProgressRecentFirst("progress_recent_first");

    companion object {
        fun fromConfigValue(value: String): WatchGroupRowSortMode =
            entries.firstOrNull { it.configValue == value.trim() } ?: ConfigOrder
    }
}

enum class SignalSide {
    Bullish,
    Bearish
}

data class TimelineMarker(
    val slot: Int,
    val side: SignalSide
)

data class PeriodTimelineRow(
    val period: String,
    val markers: List<TimelineMarker>,
    val unread: Boolean = false,
    val timelineBars: Int = DEFAULT_TIMELINE_BARS
) {
    val normalizedMarkers: List<TimelineMarker>
        get() = markers.map { marker ->
            marker.copy(slot = marker.slot.coerceIn(0, coerceTimelineBars(timelineBars) - 1))
        }
}

fun WatchGroup.toTimelineRows(): List<PeriodTimelineRow> =
    toTimelineRows(alerts = emptyList())

fun WatchGroup.unreadCount(alerts: List<SignalAlert>): Int =
    alerts.count { alert ->
        alert.symbol == symbol &&
            alert.period in periods &&
            alert.signalType in signalTypes &&
            !alert.read
    }

val WatchGroup.settingsSummaryText: String
    get() {
        val recentSuffix = if (view.rowSortMode == WatchGroupRowSortMode.ProgressRecentFirst) "(近)" else ""
        return "${coerceTimelineBars(timelineBars)}K$recentSuffix"
    }

fun WatchGroup.toTimelineRows(
    alerts: List<SignalAlert>,
    nowMillis: Long = System.currentTimeMillis()
): List<PeriodTimelineRow> {
    val slotCount = coerceTimelineBars(timelineBars)
    return periods.map { period ->
        val periodAlerts = alerts.filter { alert ->
            alert.symbol == symbol &&
                alert.period == period &&
                alert.signalType in signalTypes
        }
        val visibleMarkers = periodAlerts.mapNotNull { alert ->
            alert.toTimelineMarker(period = period, nowMillis = nowMillis, slotCount = slotCount)
        }
        PeriodTimelineRow(
            period = period,
            markers = visibleMarkers,
            unread = visibleMarkers.isNotEmpty() && periodAlerts.any { !it.read },
            timelineBars = slotCount
        )
    }
}

fun WatchGroup.toVisibleTimelineRows(
    alerts: List<SignalAlert>,
    nowMillis: Long = System.currentTimeMillis()
): List<PeriodTimelineRow> {
    val rows = toTimelineRows(alerts = alerts, nowMillis = nowMillis)
    val filteredRows = if (view.showActiveOnly) {
        rows.filter { it.markers.isNotEmpty() }
    } else {
        rows
    }
    return sortTimelineRows(filteredRows)
}

fun WatchGroup.sortTimelineRows(rows: List<PeriodTimelineRow>): List<PeriodTimelineRow> {
    if (view.rowSortMode == WatchGroupRowSortMode.ConfigOrder) return rows

    return rows.withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<PeriodTimelineRow>> { (_, row) ->
                row.normalizedMarkers.maxOfOrNull { marker -> marker.slot } ?: -1
            }.thenBy { it.index }
        )
        .map { it.value }
}

const val DEFAULT_TIMELINE_BARS = 60
const val MIN_TIMELINE_BARS = 1
const val MAX_TIMELINE_BARS = 500

fun coerceTimelineBars(value: Int): Int =
    value.coerceIn(MIN_TIMELINE_BARS, MAX_TIMELINE_BARS)

private fun SignalAlert.toTimelineMarker(
    period: String,
    nowMillis: Long,
    slotCount: Int
): TimelineMarker? {
    val periodMillis = periodDurationMillis(period) ?: return null
    val barsAgo = ((nowMillis - triggerTimeMillis).coerceAtLeast(0L) / periodMillis).toInt()
    if (barsAgo >= slotCount) return null
    return TimelineMarker(
        slot = slotCount - 1 - barsAgo,
        side = side
    )
}

fun periodDurationMillis(period: String): Long? {
    val normalized = period.trim().uppercase()
    if (normalized.isBlank()) return null
    return when {
        normalized == "W" -> 7L * 24 * 60 * 60 * 1000
        normalized == "D" -> 24L * 60 * 60 * 1000
        normalized.endsWith("D") -> normalized.dropLast(1).toLongOrNull()
            ?.let { it * 24 * 60 * 60 * 1000 }
        normalized.endsWith("S") -> normalized.dropLast(1).toLongOrNull()
            ?.let { it * 1000 }
        else -> normalized.toLongOrNull()?.let { it * 60 * 1000 }
    }
}

enum class ConnectionState {
    NotConfigured,
    Idle,
    Success,
    Failed
}

data class MonitorStatus(
    val unreadCount: Int,
    val lastFetchText: String?,
    val lastSuccessText: String?,
    val connectionState: ConnectionState
) {
    val summaryText: String
        get() {
            val fetchText = lastFetchText?.let { "刚刚 $it" } ?: "尚未刷新"
            return "未读 $unreadCount | $fetchText | ${connectionState.label}"
        }
}

private val ConnectionState.label: String
    get() = when (this) {
        ConnectionState.NotConfigured -> "需要配置"
        ConnectionState.Idle -> "待机"
        ConnectionState.Success -> "成功"
        ConnectionState.Failed -> "失败"
    }
