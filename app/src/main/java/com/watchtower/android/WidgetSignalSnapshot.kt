package com.watchtower.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WidgetSignalSnapshot(
    val alerts: List<SignalAlert>,
    val updatedAtMillis: Long
) {
    companion object {
        fun empty() = WidgetSignalSnapshot(alerts = emptyList(), updatedAtMillis = 0L)
    }
}

object WidgetSignalSnapshotJson {
    fun encode(snapshot: WidgetSignalSnapshot): String = JSONObject()
        .put("updatedAtMillis", snapshot.updatedAtMillis)
        .put(
            "alerts",
            JSONArray().apply {
                snapshot.alerts.forEach { alert ->
                    put(
                        JSONObject()
                            .put("symbol", alert.symbol)
                            .put("period", alert.period)
                            .put("signalType", alert.signalType)
                            .put("side", alert.side.name)
                            .put("triggerTimeMillis", alert.triggerTimeMillis)
                            .put("read", alert.read)
                    )
                }
            }
        )
        .toString()

    fun decode(value: String?): WidgetSignalSnapshot =
        runCatching {
            val root = JSONObject(value.orEmpty())
            val alerts = root.optJSONArray("alerts") ?: JSONArray()
            WidgetSignalSnapshot(
                alerts = (0 until alerts.length()).mapNotNull { index ->
                    val alert = alerts.optJSONObject(index) ?: return@mapNotNull null
                    val side = runCatching {
                        SignalSide.valueOf(alert.optString("side"))
                    }.getOrNull() ?: return@mapNotNull null

                    SignalAlert(
                        symbol = alert.optString("symbol"),
                        period = alert.optString("period"),
                        signalType = alert.optString("signalType"),
                        side = side,
                        triggerTimeMillis = alert.optLong("triggerTimeMillis"),
                        read = alert.optBoolean("read", true)
                    )
                },
                updatedAtMillis = root.optLong("updatedAtMillis", 0L)
            )
        }.getOrDefault(WidgetSignalSnapshot.empty())
}

class WidgetSignalSnapshotStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): WidgetSignalSnapshot =
        WidgetSignalSnapshotJson.decode(preferences.getString(KEY_SNAPSHOT, null))

    fun save(snapshot: WidgetSignalSnapshot) {
        preferences.edit()
            .putString(KEY_SNAPSHOT, WidgetSignalSnapshotJson.encode(snapshot))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "watch_tower_widget"
        const val KEY_SNAPSHOT = "signal_snapshot"
    }
}
