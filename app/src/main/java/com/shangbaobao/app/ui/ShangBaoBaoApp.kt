package com.shangbaobao.app.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shangbaobao.app.agent.AgentEventStore
import com.shangbaobao.app.ai.AiGateway
import com.shangbaobao.app.ai.AiSettingsRepository
import com.shangbaobao.app.business.BusinessRepository
import com.shangbaobao.app.data.ModeSettingsRepository
import com.shangbaobao.app.knowledge.KnowledgeBaseRepository
import com.shangbaobao.app.model.AgentEvent
import com.shangbaobao.app.model.AgentEventType
import com.shangbaobao.app.model.AutomationLevel
import com.shangbaobao.app.model.AutomationMode
import com.shangbaobao.app.model.PermissionSnapshot
import com.shangbaobao.app.model.Platform
import com.shangbaobao.app.platform.PermissionInspector
import java.util.Date

private enum class AppTab(val title: String, val icon: ImageVector) {
    WORKBENCH("经营", Icons.Outlined.Home),
    TASKS("动态", Icons.Outlined.TaskAlt),
    KNOWLEDGE("知识", Icons.AutoMirrored.Outlined.LibraryBooks),
    AI_SETTINGS("模型", Icons.Outlined.AutoAwesome),
    SETTINGS("设置", Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShangBaoBaoApp(
    modeSettingsRepository: ModeSettingsRepository,
    aiSettingsRepository: AiSettingsRepository,
    aiGateway: AiGateway,
    businessRepository: BusinessRepository,
    knowledgeRepository: KnowledgeBaseRepository,
    openAccessibilitySettings: () -> Unit,
    openNotificationSettings: () -> Unit,
    requestNotificationPermission: () -> Unit,
    startAgent: () -> Unit,
    stopAgent: () -> Unit,
    refreshPermissions: () -> Unit,
) {
    val modeSettings by modeSettingsRepository.state.collectAsState()
    val permissions by PermissionInspector.state.collectAsState()
    val running by AgentEventStore.running.collectAsState()
    val events by AgentEventStore.events.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("改流子", fontWeight = FontWeight.Bold)
                        Text(
                            "服务行业超级口碑智能体",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    StatusDot(active = running)
                    Spacer(Modifier.size(16.dp))
                },
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { padding ->
        when (AppTab.entries[selectedTab]) {
            AppTab.WORKBENCH -> WorkbenchScreen(
                modifier = Modifier.padding(padding),
                businessRepository = businessRepository,
                enabledModes = modeSettings.enabledModes,
                permissions = permissions,
                running = running,
                onModeChanged = modeSettingsRepository::setModeEnabled,
                onStart = startAgent,
                onStop = stopAgent,
                openAccessibilitySettings = openAccessibilitySettings,
            )

            AppTab.TASKS -> EventScreen(
                modifier = Modifier.padding(padding),
                events = events,
                onClear = AgentEventStore::clear,
            )

            AppTab.KNOWLEDGE -> KnowledgeBaseScreen(
                modifier = Modifier.padding(padding),
                repository = knowledgeRepository,
                businessRepository = businessRepository,
                aiRepository = aiSettingsRepository,
                gateway = aiGateway,
            )

            AppTab.AI_SETTINGS -> AiSettingsScreen(
                modifier = Modifier.padding(padding),
                repository = aiSettingsRepository,
                gateway = aiGateway,
                knowledgeRepository = knowledgeRepository,
                businessRepository = businessRepository,
            )

            AppTab.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(padding),
                permissions = permissions,
                level = modeSettings.automationLevel,
                onLevelChanged = modeSettingsRepository::setAutomationLevel,
                openAccessibilitySettings = openAccessibilitySettings,
                openNotificationSettings = openNotificationSettings,
                requestNotificationPermission = requestNotificationPermission,
                refreshPermissions = refreshPermissions,
            )
        }
    }
}

@Composable
private fun WorkbenchScreen(
    modifier: Modifier,
    businessRepository: BusinessRepository,
    enabledModes: Set<AutomationMode>,
    permissions: PermissionSnapshot,
    running: Boolean,
    onModeChanged: (AutomationMode, Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    openAccessibilitySettings: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BusinessDashboard(businessRepository) }
        item {
            AgentStatusCard(
                running = running,
                accessibilityEnabled = permissions.accessibilityEnabled,
                onStart = onStart,
                onStop = onStop,
                openAccessibilitySettings = openAccessibilitySettings,
            )
        }
        item { SectionTitle("平台") }
        item { PlatformRow() }
        item { SectionTitle("工作模式") }
        items(AutomationMode.entries) { mode ->
            ModeCard(
                mode = mode,
                enabled = mode in enabledModes,
                onEnabledChanged = { onModeChanged(mode, it) },
            )
        }
        item {
            InfoCard(
                title = "当前运行边界",
                text = "当前版本完成权限、页面观察、通知发现、模式配置和安全策略底座，不会自动发布评论。",
            )
        }
    }
}

@Composable
private fun AgentStatusCard(
    running: Boolean,
    accessibilityEnabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    openAccessibilitySettings: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (running) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (running) "辅助服务运行中" else "辅助服务未启动",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        if (running) "正在等待平台页面和任务" else "启用权限后开始观察平台动态",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!accessibilityEnabled) {
                Button(onClick = openAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.AccessibilityNew, null)
                    Spacer(Modifier.size(8.dp))
                    Text("启用操作辅助")
                }
            } else if (running) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Outlined.StopCircle, null)
                    Spacer(Modifier.size(8.dp))
                    Text("停止全部设备任务")
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.PlayArrow, null)
                    Spacer(Modifier.size(8.dp))
                    Text("启动辅助服务")
                }
            }
        }
    }
}

@Composable
private fun PlatformRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Platform.entries.forEach { platform ->
            AssistChip(
                onClick = {},
                label = { Text(platform.displayName) },
                leadingIcon = {
                    Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(18.dp))
                },
                colors = AssistChipDefaults.assistChipColors(
                    leadingIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun ModeCard(
    mode: AutomationMode,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
) {
    val icon = when (mode) {
        AutomationMode.FRONT_DESK -> Icons.Outlined.Storefront
        AutomationMode.SHOPPING -> Icons.Outlined.TravelExplore
        AutomationMode.PUBLIC_RELATIONS -> Icons.Outlined.Campaign
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(13.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(mode.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    mode.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    mode.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChanged)
        }
    }
}

@Composable
private fun EventScreen(
    modifier: Modifier,
    events: List<AgentEvent>,
    onClear: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("设备动态", Modifier.weight(1f))
                if (events.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "清空")
                    }
                }
            }
        }
        if (events.isEmpty()) {
            item {
                EmptyState(
                    title = "暂无动态",
                    text = "启动辅助服务并打开受支持的平台后，这里会显示页面和通知事件。",
                )
            }
        } else {
            items(events, key = { it.id }) { event -> EventCard(event) }
        }
    }
}

@Composable
private fun EventCard(event: AgentEvent) {
    val icon = when (event.type) {
        AgentEventType.SYSTEM -> Icons.Outlined.Info
        AgentEventType.PAGE_OBSERVED -> Icons.Outlined.AccessibilityNew
        AgentEventType.NOTIFICATION_DISCOVERED -> Icons.Outlined.NotificationsActive
        AgentEventType.SAFETY -> Icons.Outlined.Security
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(event.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        DateFormat.format("HH:mm", Date(event.timestamp)).toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    event.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    permissions: PermissionSnapshot,
    level: AutomationLevel,
    onLevelChanged: (AutomationLevel) -> Unit,
    openAccessibilitySettings: () -> Unit,
    openNotificationSettings: () -> Unit,
    requestNotificationPermission: () -> Unit,
    refreshPermissions: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("权限中心") }
        item {
            PermissionCard(
                icon = Icons.Outlined.AccessibilityNew,
                title = "操作辅助",
                description = "识别支持平台的可见页面并执行经过批准的动作",
                granted = permissions.accessibilityEnabled,
                action = openAccessibilitySettings,
            )
        }
        item {
            PermissionCard(
                icon = Icons.Outlined.NotificationsActive,
                title = "通知发现",
                description = "发现新评论和平台提醒，不在动态日志保存正文",
                granted = permissions.notificationListenerEnabled,
                action = openNotificationSettings,
            )
        }
        item {
            PermissionCard(
                icon = Icons.Outlined.NotificationsActive,
                title = "运行状态通知",
                description = "显示辅助服务运行状态、暂停和告警",
                granted = permissions.notificationPermissionGranted,
                action = requestNotificationPermission,
            )
        }
        item {
            Button(onClick = refreshPermissions, modifier = Modifier.fillMaxWidth()) {
                Text("刷新权限状态")
            }
        }
        item { SectionTitle("自动化等级") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AutomationLevel.entries.forEach { option ->
                    FilterChip(
                        selected = option == level,
                        onClick = { onLevelChanged(option) },
                        label = {
                            Column(Modifier.padding(vertical = 3.dp)) {
                                Text(option.title)
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            InfoCard(
                title = "数据保护",
                text = "平台账号仍由用户在官方 App 内登录。改流子不收集平台密码，不建立跨平台个人画像。",
                icon = Icons.Outlined.Lock,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    action: () -> Unit,
) {
    Card(
        onClick = action,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (granted) "已启用" else "去设置",
                color = if (granted) Color(0xFF067647) else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    text: String,
    icon: ImageVector = Icons.Outlined.Info,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.TaskAlt,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun StatusDot(active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (active) Color(0xFF12B76A) else Color(0xFF98A2B3), CircleShape),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            if (active) "运行中" else "已停止",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
