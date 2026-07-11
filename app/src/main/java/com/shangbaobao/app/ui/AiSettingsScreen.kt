package com.shangbaobao.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.shangbaobao.app.ai.AiGateway
import com.shangbaobao.app.ai.AiProvider
import com.shangbaobao.app.ai.AiProviderConfig
import com.shangbaobao.app.ai.AiReplyService
import com.shangbaobao.app.ai.AiSettingsRepository
import com.shangbaobao.app.ai.AssistantProfile
import com.shangbaobao.app.ai.OfficialPlatformConfig
import com.shangbaobao.app.ai.PersonalityPreset
import com.shangbaobao.app.ai.PhraseTemplate
import com.shangbaobao.app.business.BusinessRepository
import com.shangbaobao.app.knowledge.KnowledgeBaseRepository
import com.shangbaobao.app.model.Platform
import kotlinx.coroutines.launch

@Composable
fun AiSettingsScreen(
    modifier: Modifier,
    repository: AiSettingsRepository,
    gateway: AiGateway,
    knowledgeRepository: KnowledgeBaseRepository,
    businessRepository: BusinessRepository,
) {
    val state by repository.state.collectAsState()
    var showPhraseDialog by remember { mutableStateOf(false) }
    var editingPhrase by remember { mutableStateOf<PhraseTemplate?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AiSectionHeader(
                icon = Icons.Outlined.AutoAwesome,
                title = "模型API",
                subtitle = "商户自主选择主控模型。推荐GPT和豆包，并支持千问、Gemini、DeepSeek及OpenAI兼容接口。",
            )
        }
        item {
            PrimaryProviderSelector(
                selected = state.primaryProvider,
                onSelected = repository::setPrimaryProvider,
            )
        }
        items(state.providers, key = { it.provider.name }) { config ->
            ProviderConfigCard(config, repository, gateway)
        }
        item {
            AiSectionHeader(
                icon = Icons.Outlined.CloudDone,
                title = "网络与绿色通道",
                subtitle = "国内模型优先直连；其他模型可使用商户自有企业API网关，不内置VPN。",
            )
        }
        item { NetworkRouteCard(state.networkRoute, state.providers, repository) }
        item { ReplyTestCard(repository, gateway, knowledgeRepository, businessRepository) }

        item {
            AiSectionHeader(
                icon = Icons.Outlined.Psychology,
                title = "身份与性格",
                subtitle = "身份设置会加入每次AI回复的系统指令。",
            )
        }
        item { AssistantProfileCard(state.assistantProfile, repository::saveProfile) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AiSectionHeader(
                icon = Icons.AutoMirrored.Outlined.LibraryBooks,
                    title = "预置话术库",
                    subtitle = "开启后，AI先匹配话术，再结合上下文做必要调整。",
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.phraseLibraryEnabled,
                    onCheckedChange = repository::setPhraseLibraryEnabled,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        editingPhrase = null
                        showPhraseDialog = true
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.size(6.dp))
                    Text("新增话术")
                }
                OutlinedButton(
                    onClick = repository::resetDefaultPhrases,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("恢复默认")
                }
            }
        }
        items(state.phrases, key = { it.id }) { phrase ->
            PhraseCard(
                phrase = phrase,
                onToggle = { repository.upsertPhrase(phrase.copy(enabled = it)) },
                onEdit = {
                    editingPhrase = phrase
                    showPhraseDialog = true
                },
                onDelete = { repository.deletePhrase(phrase.id) },
            )
        }

        item {
            AiSectionHeader(
                icon = Icons.Outlined.Hub,
                title = "平台官方API",
                subtitle = "配置平台开放能力；获得正式权限后用于评论同步、搜索和回复辅助。",
            )
        }
        items(state.officialPlatforms, key = { it.platform.name }) { config ->
            OfficialPlatformCard(config, repository)
        }
    }

    if (showPhraseDialog) {
        PhraseEditorDialog(
            initial = editingPhrase,
            onDismiss = { showPhraseDialog = false },
            onSave = {
                repository.upsertPhrase(it)
                showPhraseDialog = false
            },
        )
    }
}

@Composable
private fun ReplyTestCard(
    repository: AiSettingsRepository,
    gateway: AiGateway,
    knowledgeRepository: KnowledgeBaseRepository,
    businessRepository: BusinessRepository,
) {
    var platform by remember { mutableStateOf(Platform.DOUYIN) }
    var input by remember { mutableStateOf("请问你们门店在哪里，周末可以预约吗？") }
    var generating by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }
    var meta by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val replyService = remember(repository, gateway, knowledgeRepository, businessRepository) {
        AiReplyService(repository, gateway, knowledgeRepository, businessRepository)
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AI回复测试台", style = MaterialTheme.typography.titleMedium)
            Text(
                "用于验证首选模型、身份性格和话术库优先匹配是否生效。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Platform.entries.forEach { option ->
                FilterChip(
                    selected = platform == option,
                    onClick = { platform = option },
                    label = { Text(option.displayName) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("模拟评论或咨询") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    generating = true
                    output = ""
                    meta = ""
                    scope.launch {
                        val result = replyService.generateReply(input, platform)
                        generating = false
                        output = if (result.success) result.text else result.error
                        meta = buildString {
                            result.provider?.let { append("模型：${it.displayName}") }
                            result.preferredPhraseId?.let {
                                if (isNotEmpty()) append(" · ")
                                append("话术：$it")
                            }
                            if (result.requiresApproval) {
                                if (isNotEmpty()) append(" · ")
                                append("需合规审核")
                            }
                            if (result.riskFlags.isNotEmpty()) {
                                append("\n风险：${result.riskFlags.joinToString("、")}")
                            }
                        }
                    }
                },
                enabled = input.isNotBlank() && !generating,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (generating) "生成中" else "生成测试回复") }
            if (output.isNotBlank()) {
                HorizontalDivider()
                Text(output, style = MaterialTheme.typography.bodyLarge)
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryProviderSelector(
    selected: AiProvider,
    onSelected: (AiProvider) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("首选模型提供商", fontWeight = FontWeight.SemiBold)
            Text(
                "首选调用失败时，会自动尝试另一个已启用且配置完成的提供商。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AiProvider.entries.forEach { provider ->
                FilterChip(
                    selected = selected == provider,
                    onClick = { onSelected(provider) },
                    label = { Text(provider.displayName + if (provider.recommended) "（推荐）" else "") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ProviderConfigCard(
    config: AiProviderConfig,
    repository: AiSettingsRepository,
    gateway: AiGateway,
) {
    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var baseUrl by remember(config) { mutableStateOf(config.baseUrl) }
    var model by remember(config) { mutableStateOf(config.model) }
    var apiKey by remember(config) { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(config.provider.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        buildString {
                            append(if (config.apiKeyConfigured) "API Key已加密保存" else "尚未配置API Key")
                            append(" · ")
                            append(if (config.provider.supportsMultimodal) "多模态" else "文本模型")
                            append(" · ${config.provider.protocol.name}")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (config.apiKeyConfigured) Color(0xFF067647)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("模型名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(if (config.apiKeyConfigured) "API Key（留空则保留）" else "API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        repository.saveProvider(
                            config.copy(enabled = enabled, baseUrl = baseUrl, model = model),
                            apiKey,
                        )
                        apiKey = ""
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
                OutlinedButton(
                    onClick = {
                        val currentKey = apiKey.ifBlank {
                            repository.getProviderApiKey(config.provider).orEmpty()
                        }
                        if (currentKey.isBlank()) {
                            testSuccess = false
                            testMessage = "请先填写API Key"
                        } else {
                            testing = true
                            testMessage = null
                            scope.launch {
                                val testConfig = config.copy(enabled = enabled, baseUrl = baseUrl, model = model)
                                val route = repository.resolveRoute(testConfig)
                                val result = gateway.testConnection(
                                    config = testConfig,
                                    apiKey = currentKey,
                                    route = route,
                                    connectTimeoutSeconds = repository.state.value.networkRoute.connectTimeoutSeconds,
                                )
                                testing = false
                                testSuccess = result.success
                                testMessage = if (result.success) result.text else result.error
                            }
                        }
                    },
                    enabled = !testing,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.CloudDone, null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (testing) "测试中" else "测试连接")
                }
            }
            if (config.apiKeyConfigured) {
                TextButton(onClick = { repository.clearProviderApiKey(config.provider) }) {
                    Text("清除已保存的API Key")
                }
            }
            testMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (testSuccess) Color(0xFF067647) else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AssistantProfileCard(
    profile: AssistantProfile,
    onSave: (AssistantProfile) -> Unit,
) {
    var enabled by remember(profile) { mutableStateOf(profile.enabled) }
    var identityName by remember(profile) { mutableStateOf(profile.identityName) }
    var role by remember(profile) { mutableStateOf(profile.role) }
    var personality by remember(profile) { mutableStateOf(profile.personality) }
    var tone by remember(profile) { mutableStateOf(profile.tone) }
    var custom by remember(profile) { mutableStateOf(profile.customInstructions) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用身份性格", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            OutlinedTextField(
                value = identityName,
                onValueChange = { identityName = it },
                label = { Text("AI称呼") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = role,
                onValueChange = { role = it },
                label = { Text("身份角色") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("性格预设", style = MaterialTheme.typography.labelLarge)
            PersonalityPreset.entries.forEach { preset ->
                FilterChip(
                    selected = personality == preset,
                    onClick = { personality = preset },
                    label = {
                        Column(Modifier.padding(vertical = 2.dp)) {
                            Text(preset.displayName)
                            Text(
                                preset.instruction,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = tone,
                onValueChange = { tone = it },
                label = { Text("表达语气") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it },
                label = { Text("自定义补充要求") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onSave(
                        AssistantProfile(
                            enabled = enabled,
                            identityName = identityName.trim(),
                            role = role.trim(),
                            personality = personality,
                            tone = tone.trim(),
                            customInstructions = custom.trim(),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存身份性格") }
        }
    }
}

@Composable
private fun PhraseCard(
    phrase: PhraseTemplate,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(phrase.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${phrase.intent} · 优先级 ${phrase.priority}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Switch(checked = phrase.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "编辑") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "删除") }
            }
            Text(phrase.content, style = MaterialTheme.typography.bodyMedium)
            if (phrase.keywords.isNotEmpty()) {
                Text(
                    "关键词：${phrase.keywords.joinToString("、")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OfficialPlatformCard(
    config: OfficialPlatformConfig,
    repository: AiSettingsRepository,
) {
    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var baseUrl by remember(config) { mutableStateOf(config.baseUrl) }
    var clientId by remember(config) { mutableStateOf(config.clientId) }
    var clientSecret by remember(config) { mutableStateOf("") }
    var scopes by remember(config) { mutableStateOf(config.scopes) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${config.platform.displayName}开放平台",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("开放平台Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it },
                label = { Text("Client Key / App Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = clientSecret,
                onValueChange = { clientSecret = it },
                label = {
                    Text(if (config.clientSecretConfigured) "Client Secret（留空则保留）" else "Client Secret")
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = scopes,
                onValueChange = { scopes = it },
                label = { Text("Scopes / 权限范围") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    repository.saveOfficialPlatform(
                        config.copy(
                            enabled = enabled,
                            baseUrl = baseUrl,
                            clientId = clientId,
                            scopes = scopes,
                        ),
                        clientSecret,
                    )
                    clientSecret = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存平台配置") }
            if (config.clientSecretConfigured) {
                TextButton(onClick = { repository.clearOfficialPlatformSecret(config.platform) }) {
                    Text("清除已保存的Client Secret")
                }
            }
        }
    }
}

@Composable
private fun PhraseEditorDialog(
    initial: PhraseTemplate?,
    onDismiss: () -> Unit,
    onSave: (PhraseTemplate) -> Unit,
) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var intent by remember(initial) { mutableStateOf(initial?.intent.orEmpty()) }
    var keywords by remember(initial) { mutableStateOf(initial?.keywords?.joinToString("，").orEmpty()) }
    var content by remember(initial) { mutableStateOf(initial?.content.orEmpty()) }
    var priority by remember(initial) { mutableStateOf((initial?.priority ?: 50).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增话术" else "编辑话术") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("话术名称") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = intent,
                        onValueChange = { intent = it },
                        label = { Text("意图标签") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = keywords,
                        onValueChange = { keywords = it },
                        label = { Text("关键词，使用逗号分隔") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("话术内容") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = priority,
                        onValueChange = { priority = it.filter(Char::isDigit).take(3) },
                        label = { Text("优先级 0-100") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        PhraseTemplate(
                            id = initial?.id.orEmpty(),
                            title = title.trim(),
                            intent = intent.trim(),
                            keywords = keywords.split('，', ',')
                                .map(String::trim)
                                .filter(String::isNotBlank),
                            content = content.trim(),
                            enabled = initial?.enabled ?: true,
                            priority = priority.toIntOrNull()?.coerceIn(0, 100) ?: 50,
                            platform = initial?.platform,
                        ),
                    )
                },
                enabled = title.isNotBlank() && content.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AiSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
    }
}
