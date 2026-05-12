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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.watchtower.android.ui.theme.WatchTowerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()
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

    suspend fun refreshSignals() {
        if (!config.isComplete || isRefreshing) return

        isRefreshing = true
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
        } finally {
            isRefreshing = false
        }
    }

    LaunchedEffect(config) {
        if (!config.isComplete) {
            alerts = emptyList()
            isRefreshing = false
            status = MonitorStatus(
                unreadCount = 0,
                lastFetchText = null,
                lastSuccessText = null,
                connectionState = ConnectionState.NotConfigured
            )
            return@LaunchedEffect
        }

        while (true) {
            refreshSignals()
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
                canRefresh = config.canManualRefresh(isRefreshing),
                onRefresh = {
                    refreshScope.launch {
                        refreshSignals()
                    }
                },
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
                    alerts = alerts,
                    onConfigChanged = {
                        config = it
                        onPersistConfig(it)
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusBar(
    status: MonitorStatus,
    canRefresh: Boolean,
    onRefresh: () -> Unit,
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
            TextButton(
                onClick = onRefresh,
                enabled = canRefresh
            ) {
                Text(text = "刷新")
            }
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
    alerts: List<SignalAlert>,
    onConfigChanged: (WatchTowerConfig) -> Unit
) {
    var settingsGroupId by remember { mutableStateOf<String?>(null) }
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
                alerts = alerts,
                onOpenSettings = if (config.groups.any { it.id == group.id }) {
                    { settingsGroupId = group.id }
                } else {
                    null
                }
            )
        }
    }

    val settingsGroup = config.groups.firstOrNull { it.id == settingsGroupId }
    if (settingsGroup != null) {
        GroupSettingsSheet(
            group = settingsGroup,
            onDismissRequest = { settingsGroupId = null },
            onGroupChanged = { updatedGroup ->
                onConfigChanged(
                    config.copy(
                        groups = config.groups.map { group ->
                            if (group.id == updatedGroup.id) updatedGroup else group
                        }
                    )
                )
            }
        )
    }
}

@Composable
private fun GroupTimelinePanel(
    group: WatchGroup,
    alerts: List<SignalAlert>,
    onOpenSettings: (() -> Unit)?
) {
    val allRows = group.toTimelineRows(alerts)
    val visibleRows = group.toVisibleTimelineRows(alerts)
    val activePeriodCount = allRows.count { it.markers.isNotEmpty() }

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
            GroupHeader(
                group = group,
                unreadCount = group.unreadCount(alerts),
                activePeriodCount = activePeriodCount,
                totalPeriodCount = group.periods.size,
                onOpenSettings = onOpenSettings
            )
            if (visibleRows.isEmpty() && group.view.showActiveOnly && group.periods.isNotEmpty()) {
                Text(
                    text = "当前无活跃警报",
                    modifier = Modifier.padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            visibleRows.forEach { row ->
                PeriodTimelineRowView(row = row)
            }
        }
    }
}

@Composable
private fun GroupHeader(
    group: WatchGroup,
    unreadCount: Int,
    activePeriodCount: Int,
    totalPeriodCount: Int,
    onOpenSettings: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = group.name,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (unreadCount > 0) {
                Text(
                    text = "未读 $unreadCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (totalPeriodCount > 0) {
                Text(
                    text = "活跃 $activePeriodCount/$totalPeriodCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
        if (onOpenSettings != null) {
            TextButton(
                onClick = onOpenSettings,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupSettingsSheet(
    group: WatchGroup,
    onDismissRequest: () -> Unit,
    onGroupChanged: (WatchGroup) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "${group.name} 设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            SwitchRow(
                title = "只显示有信号级别",
                checked = group.view.showActiveOnly,
                onCheckedChange = { checked ->
                    onGroupChanged(
                        group.copy(
                            view = group.view.copy(showActiveOnly = checked)
                        )
                    )
                }
            )
            Text(
                text = "隐藏当前没有可见警报的级别",
                style = MaterialTheme.typography.bodySmall,
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
