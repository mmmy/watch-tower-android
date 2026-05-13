package com.watchtower.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SignalAlert(
    val symbol: String,
    val period: String,
    val signalType: String,
    val side: SignalSide,
    val triggerTimeMillis: Long,
    val read: Boolean
)

data class SignalRequest(
    val symbols: String,
    val periods: String,
    val signalTypes: String,
    val page: Int,
    val pageSize: Int
) {
    fun toJson(): String = JSONObject()
        .put("symbols", symbols)
        .put("periods", periods)
        .put("signalTypes", signalTypes)
        .put("page", page)
        .put("pageSize", pageSize)
        .toString()

    companion object {
        fun fromConfig(config: WatchTowerConfig): SignalRequest {
            val enabledGroups = config.groups.filter { it.enabled }
            return SignalRequest(
                symbols = enabledGroups.map { it.symbol }.distinct().joinToString(","),
                periods = enabledGroups.flatMap { it.periods }.distinct().joinToString(","),
                signalTypes = enabledGroups.flatMap { it.signalTypes }.distinct().joinToString(","),
                page = 1,
                pageSize = config.pageSize
            )
        }
    }
}

data class SignalReadStatusItem(
    val symbol: String,
    val period: String,
    val signalType: String,
    val read: Boolean
) {
    fun toJsonObject(): JSONObject = JSONObject()
        .put("symbol", symbol)
        .put("period", period)
        .put("signalType", signalType)
        .put("read", read)

    companion object {
        fun fromAlert(alert: SignalAlert, read: Boolean): SignalReadStatusItem =
            SignalReadStatusItem(
                symbol = alert.symbol,
                period = alert.period,
                signalType = alert.signalType,
                read = read
            )
    }
}

data class SignalReadStatusBatchRequest(
    val items: List<SignalReadStatusItem>
) {
    fun toJson(): String = JSONObject()
        .put("items", JSONArray(items.map { it.toJsonObject() }))
        .toString()

    companion object {
        fun fromAlerts(alerts: List<SignalAlert>, read: Boolean): SignalReadStatusBatchRequest =
            SignalReadStatusBatchRequest(
                items = alerts.map { alert -> SignalReadStatusItem.fromAlert(alert, read = read) }
            )
    }
}

data class SignalReadStatusBatchResult(
    val symbol: String,
    val period: String,
    val signalType: String,
    val read: Boolean,
    val success: Boolean,
    val reason: String?
)

data class SignalReadStatusBatchResponse(
    val success: Int,
    val failed: Int,
    val results: List<SignalReadStatusBatchResult>
)

object SignalResponseParser {
    fun parse(response: String): List<SignalAlert> {
        val root = JSONObject(response)
        val data = root.optJSONArray("data") ?: return emptyList()
        val alerts = mutableListOf<SignalAlert>()

        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            val symbol = item.optString("symbol")
            val period = item.optString("period")
            val signals = item.optJSONObject("signals") ?: continue
            val signalTypes = signals.keys()

            while (signalTypes.hasNext()) {
                val signalType = signalTypes.next()
                val signal = signals.optJSONObject(signalType) ?: continue
                val side = when (signal.optInt("sd", 0)) {
                    1 -> SignalSide.Bullish
                    -1 -> SignalSide.Bearish
                    else -> continue
                }
                alerts += SignalAlert(
                    symbol = symbol,
                    period = period,
                    signalType = signalType,
                    side = side,
                    triggerTimeMillis = signal.optLong("t", item.optLong("t", 0L)),
                    read = signal.optBoolean("read", true)
                )
            }
        }

        return alerts
    }
}

object SignalReadStatusBatchResponseParser {
    fun parse(response: String): SignalReadStatusBatchResponse {
        val root = JSONObject(response)
        val resultsJson = root.optJSONArray("results") ?: JSONArray()
        val results = mutableListOf<SignalReadStatusBatchResult>()

        for (index in 0 until resultsJson.length()) {
            val item = resultsJson.optJSONObject(index) ?: continue
            results += SignalReadStatusBatchResult(
                symbol = item.optString("symbol"),
                period = item.optString("period"),
                signalType = item.optString("signalType"),
                read = item.optBoolean("read"),
                success = item.optBoolean("success"),
                reason = item.optString("reason").takeIf { it.isNotBlank() }
            )
        }

        return SignalReadStatusBatchResponse(
            success = root.optInt("success"),
            failed = root.optInt("failed"),
            results = results
        )
    }
}

class SignalApiClient {
    suspend fun fetchSignals(config: WatchTowerConfig): List<SignalAlert> = withContext(Dispatchers.IO) {
        val request = SignalRequest.fromConfig(config)
        if (request.symbols.isBlank()) return@withContext emptyList()

        val endpoint = "${config.baseUrl.trimEnd('/')}/api/open/watch-list/symbol-signals"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-api-key", config.apiKey)
        }

        try {
            connection.outputStream.use { output ->
                output.write(request.toJson().toByteArray(Charsets.UTF_8))
            }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode")
            }
            SignalResponseParser.parse(body)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun setReadStatuses(
        config: WatchTowerConfig,
        alerts: List<SignalAlert>,
        read: Boolean
    ): SignalReadStatusBatchResponse =
        withContext(Dispatchers.IO) {
            val request = SignalReadStatusBatchRequest.fromAlerts(alerts, read = read)
            val endpoint = "${config.baseUrl.trimEnd('/')}/api/open/watch-list/symbol-alert/read-status/batch"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("x-api-key", config.apiKey)
            }

            try {
                connection.outputStream.use { output ->
                    output.write(request.toJson().toByteArray(Charsets.UTF_8))
                }
                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (responseCode !in 200..299) {
                    throw IllegalStateException("HTTP $responseCode")
                }
                SignalReadStatusBatchResponseParser.parse(body)
            } finally {
                connection.disconnect()
            }
        }
}
