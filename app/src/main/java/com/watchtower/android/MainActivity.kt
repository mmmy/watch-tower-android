package com.watchtower.android

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.watchtower.android.ui.theme.WatchTowerTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val configStore = WatchTowerConfigStore(SharedPreferencesConfigStorage(this))
        val initialConfig = configStore.load()
        setContent {
            WatchTowerTheme {
                WatchTowerApp(
                    initialConfig = initialConfig,
                    onPersistConfig = configStore::save
                )
            }
        }
    }
}

@Composable
private fun WatchTowerApp(
    initialConfig: WatchTowerConfig = WatchTowerConfig.default(),
    onPersistConfig: (WatchTowerConfig) -> Unit = {},
    signalApiClient: SignalApiClient = remember { SignalApiClient() }
) {
    var config by remember { mutableStateOf(initialConfig) }
    var showConfig by remember { mutableStateOf(!initialConfig.isComplete) }
    var alerts by remember { mutableStateOf(emptyList<SignalAlert>()) }
    var status by remember(config) {
        mutableStateOf(
            MonitorStatus(
                unreadCount = alerts.count { !it.read },
                lastFetchText = null,
                lastSuccessText = null,
                connectionState = if (config.isComplete) ConnectionState.Idle else ConnectionState.NotConfigured
            )
        )
    }

    LaunchedEffect(config) {
        if (!config.isComplete) {
            alerts = emptyList()
            status = MonitorStatus(
                unreadCount = 0,
                lastFetchText = null,
                lastSuccessText = null,
                connectionState = ConnectionState.NotConfigured
            )
            return@LaunchedEffect
        }

        while (true) {
            val fetchTime = currentClockText()
            try {
                val fetchedAlerts = signalApiClient.fetchSignals(config)
                alerts = fetchedAlerts
                status = MonitorStatus(
                    unreadCount = fetchedAlerts.count { !it.read },
                    lastFetchText = fetchTime,
                    lastSuccessText = fetchTime,
                    connectionState = ConnectionState.Success
                )
            } catch (_: Exception) {
                status = MonitorStatus(
                    unreadCount = alerts.count { !it.read },
                    lastFetchText = fetchTime,
                    lastSuccessText = status.lastSuccessText,
                    connectionState = ConnectionState.Failed
                )
            }
            delay(config.pollIntervalSecs.coerceAtLeast(1) * 1000L)
        }
    }

    val displayStatus = if (config.isComplete) {
        status
    } else {
        MonitorStatus(
            unreadCount = 0,
            lastFetchText = null,
            lastSuccessText = null,
            connectionState = ConnectionState.NotConfigured
        )
    }

    BackHandler(enabled = showConfig && config.canLeaveConfigPage) {
        showConfig = false
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            StatusBar(
                status = displayStatus,
                onOpenConfig = { showConfig = true }
            )
            if (showConfig || !config.isComplete) {
                ConfigScreen(
                    config = config,
                    canNavigateBack = config.canLeaveConfigPage,
                    onNavigateBack = { showConfig = false },
                    onConfigSaved = {
                        config = it
                        onPersistConfig(it)
                        showConfig = !it.isComplete
                    }
                )
            } else {
                DashboardScreen(
                    config = config,
                    alerts = alerts
                )
            }
        }
    }
}

@Composable
private fun StatusBar(
    status: MonitorStatus,
    onOpenConfig: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = status.summaryText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onOpenConfig) {
                Text(text = "配置")
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun ConfigScreen(
    config: WatchTowerConfig,
    canNavigateBack: Boolean,
    onNavigateBack: () -> Unit,
    onConfigSaved: (WatchTowerConfig) -> Unit
) {
    val context = LocalContext.current
    var baseUrl by remember(config) { mutableStateOf(config.baseUrl) }
    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var pollIntervalText by remember(config) { mutableStateOf(config.pollIntervalSecs.toString()) }
    var pageSizeText by remember(config) { mutableStateOf(config.pageSize.toString()) }
    var notificationsEnabled by remember(config) { mutableStateOf(config.notificationsEnabled) }
    var soundEnabled by remember(config) { mutableStateOf(config.soundEnabled) }
    var importMessage by remember { mutableStateOf<String?>(null) }

    val yamlPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val importedConfig = ConfigYamlParser.parse(context.readTextFromUri(uri))
            importMessage = "导入成功: ${importedConfig.groups.size} 组"
            onConfigSaved(importedConfig)
        } catch (error: IllegalArgumentException) {
            importMessage = "导入失败: ${error.message ?: "配置格式不正确"}"
        } catch (error: RuntimeException) {
            importMessage = "导入失败: 无法读取文件"
        }
    }

    val canSave = baseUrl.isNotBlank() && apiKey.isNotBlank()
    val nextConfig = WatchTowerConfig(
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        pollIntervalSecs = pollIntervalText.toIntOrNull()?.coerceAtLeast(1) ?: 60,
        pageSize = pageSizeText.toIntOrNull()?.coerceIn(1, 100) ?: 100,
        notificationsEnabled = notificationsEnabled,
        soundEnabled = soundEnabled,
        groups = config.groups
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "配置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (canNavigateBack) {
                    TextButton(onClick = onNavigateBack) {
                        Text("返回")
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        yamlPicker.launch(
                            arrayOf(
                                "application/x-yaml",
                                "text/yaml",
                                "text/x-yaml",
                                "text/plain",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("导入 YAML")
                }
                importMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        item {
            ConfigSection(title = "API") {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Base URL") }
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        }

        item {
            ConfigSection(title = "轮询") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = pollIntervalText,
                        onValueChange = { pollIntervalText = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("间隔秒") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = pageSizeText,
                        onValueChange = { pageSizeText = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Page Size") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        }

        item {
            ConfigSection(title = "提醒") {
                SwitchRow(
                    title = "系统通知",
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it }
                )
                SwitchRow(
                    title = "声音",
                    checked = soundEnabled,
                    onCheckedChange = { soundEnabled = it }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onConfigSaved(nextConfig) },
                    enabled = canSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
                TextButton(
                    onClick = { onConfigSaved(WatchTowerConfig.default()) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("重置")
                }
            }
        }
    }
}

@Composable
private fun ConfigSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun DashboardScreen(
    config: WatchTowerConfig,
    alerts: List<SignalAlert>
) {
    val groups = config.groups.ifEmpty {
        listOf(
            WatchGroup("placeholder-1", "BTC Main", "BTCUSDT", emptyList(), emptyList(), true),
            WatchGroup("placeholder-2", "Btc 2", "BTCUSDT", emptyList(), emptyList(), true)
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(groups) { group ->
            GroupTimelinePanel(
                group = group,
                alerts = alerts
            )
        }
    }
}

@Composable
private fun GroupTimelinePanel(
    group: WatchGroup,
    alerts: List<SignalAlert>
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            GroupHeader(group = group)
            group.toTimelineRows(alerts).forEach { row ->
                PeriodTimelineRowView(row = row)
            }
        }
    }
}

@Composable
private fun GroupHeader(group: WatchGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = group.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = group.symbol,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold
        )
        if (group.signalTypes.isNotEmpty()) {
            Text(
                text = group.signalTypes.joinToString("/"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PeriodTimelineRowView(row: PeriodTimelineRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val unreadColor = Color(0xFFDC2626)
        Text(
            text = row.period,
            modifier = Modifier.width(34.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (row.unread) unreadColor else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (row.unread) FontWeight.Bold else FontWeight.SemiBold
        )
        TimelineRail(
            markers = row.normalizedMarkers,
            modifier = Modifier
                .weight(1f)
                .height(16.dp)
        )
    }
}

@Composable
private fun TimelineRail(
    markers: List<TimelineMarker>,
    modifier: Modifier = Modifier
) {
    val railColor = Color(0xFFE5E7EB)
    val bullishColor = Color(0xFF16A34A)
    val bearishColor = Color(0xFFDC2626)

    Canvas(
        modifier = modifier.background(Color.Transparent)
    ) {
        drawRect(
            color = railColor,
            topLeft = Offset.Zero,
            size = size
        )

        val markerWidth = (size.width / TIMELINE_SLOT_COUNT).coerceAtLeast(4f)
        markers.forEach { marker ->
            val x = if (TIMELINE_SLOT_COUNT <= 1) {
                0f
            } else {
                (marker.slot / (TIMELINE_SLOT_COUNT - 1).toFloat()) * (size.width - markerWidth)
            }
            drawRect(
                color = when (marker.side) {
                    SignalSide.Bullish -> bullishColor
                    SignalSide.Bearish -> bearishColor
                },
                topLeft = Offset(x, 0f),
                size = Size(markerWidth, size.height)
            )
        }
    }
}

private fun Context.readTextFromUri(uri: Uri): String =
    contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ?: throw IllegalArgumentException("无法读取文件")

private fun currentClockText(): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

@Preview(showBackground = true)
@Composable
private fun WatchTowerAppPreview() {
    WatchTowerTheme {
        WatchTowerApp()
    }
}
