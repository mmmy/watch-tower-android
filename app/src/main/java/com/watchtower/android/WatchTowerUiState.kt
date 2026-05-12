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

data class WatchGroup(
    val id: String,
    val name: String,
    val symbol: String,
    val periods: List<String>,
    val signalTypes: List<String>,
    val enabled: Boolean
)

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
    val unread: Boolean = false
) {
    val normalizedMarkers: List<TimelineMarker>
        get() = markers.map { marker ->
            marker.copy(slot = marker.slot.coerceIn(0, TIMELINE_SLOT_COUNT - 1))
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

fun WatchGroup.toTimelineRows(
    alerts: List<SignalAlert>,
    nowMillis: Long = System.currentTimeMillis()
): List<PeriodTimelineRow> =
    periods.map { period ->
        val periodAlerts = alerts.filter { alert ->
            alert.symbol == symbol &&
                alert.period == period &&
                alert.signalType in signalTypes
        }
        val visibleMarkers = periodAlerts.mapNotNull { alert ->
            alert.toTimelineMarker(period = period, nowMillis = nowMillis)
        }
        PeriodTimelineRow(
            period = period,
            markers = visibleMarkers,
            unread = visibleMarkers.isNotEmpty() && periodAlerts.any { !it.read }
        )
    }

const val TIMELINE_SLOT_COUNT = 60

private fun SignalAlert.toTimelineMarker(
    period: String,
    nowMillis: Long
): TimelineMarker? {
    val periodMillis = periodDurationMillis(period) ?: return null
    val barsAgo = ((nowMillis - triggerTimeMillis).coerceAtLeast(0L) / periodMillis).toInt()
    if (barsAgo >= TIMELINE_SLOT_COUNT) return null
    return TimelineMarker(
        slot = TIMELINE_SLOT_COUNT - 1 - barsAgo,
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
            val fetchText = lastFetchText?.let { "刚刚更新 $it" } ?: "尚未刷新"
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
