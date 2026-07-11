package com.gailiuzi.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ModelTraining
import androidx.compose.material.icons.outlined.UploadFile
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
import androidx.compose.ui.unit.dp
import com.gailiuzi.app.ai.AiGateway
import com.gailiuzi.app.ai.AiSettingsRepository
import com.gailiuzi.app.business.BusinessRepository
import com.gailiuzi.app.business.OrgUnit
import com.gailiuzi.app.knowledge.KnowledgeBaseRepository
import com.gailiuzi.app.knowledge.KnowledgeItem
import com.gailiuzi.app.knowledge.KnowledgeLearningService
import com.gailiuzi.app.knowledge.KnowledgeSettings
import com.gailiuzi.app.knowledge.KnowledgeSourceType
import com.gailiuzi.app.knowledge.KnowledgeStatus
import com.gailiuzi.app.knowledge.TrainingSample
import kotlinx.coroutines.launch

@Composable
fun KnowledgeBaseScreen(
    modifier: Modifier,
    repository: KnowledgeBaseRepository,
    businessRepository: BusinessRepository,
    aiRepository: AiSettingsRepository,
    gateway: AiGateway,
) {
    val state by repository.state.collectAsState()
    val business by businessRepository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val learningService = remember(repository, aiRepository, gateway) {
        KnowledgeLearningService(repository, aiRepository, gateway)
    }
    var editingItem by remember { mutableStateOf<KnowledgeItem?>(null) }
    var showItemDialog by remember { mutableStateOf(false) }
    var editingSample by remember { mutableStateOf<TrainingSample?>(null) }
    var showSampleDialog by remember { mutableStateOf(false) }
    var operationMessage by remember { mutableStateOf("") }

    val documentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var imported = 0
            var failed = 0
            uris.forEach { uri ->
                runCatching {
                    val item = repository.importTextDocument(uri, business.currentScopeId)
                    imported++
                    if (state.settings.autoLearnWithAi) learningService.learn(item.id)
                }.onFailure { failed++ }
            }
            operationMessage = "已导入 $imported 个文件" + if (failed > 0) "，失败 $failed 个" else ""
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(9.dp))
                        Text("商家超级智能体", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        "上传商家资料后自动建立检索索引；接通主控模型后可自动提炼，回复时只检索总部、当前区域和当前门店有权限的知识。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "当前范围：${business.currentScope?.name ?: "全局"}",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item { KnowledgeSettingsCard(state.settings, repository::saveSettings) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { documentLauncher.launch(arrayOf("text/*", "application/json")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.UploadFile, null)
                    Spacer(Modifier.size(5.dp))
                    Text("上传资料")
                }
                OutlinedButton(
                    onClick = {
                        editingItem = null
                        showItemDialog = true
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.size(5.dp))
                    Text("手工知识")
                }
            }
        }
        if (operationMessage.isNotBlank()) {
            item {
                Text(operationMessage, color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            KnowledgeHeader(
                icon = Icons.AutoMirrored.Outlined.LibraryBooks,
                title = "专属知识库",
                subtitle = "支持TXT、Markdown、CSV和JSON文本资料，单文件不超过2MB。",
            )
        }
        if (state.items.isEmpty()) {
            item { KnowledgeEmpty("还没有知识资料", "上传门店手册、项目说明、价目规则、预约和售后政策。") }
        } else {
            items(state.items, key = KnowledgeItem::id) { item ->
                KnowledgeItemCard(
                    item = item,
                    scopeName = item.scopeId?.let { id -> business.units.firstOrNull { it.id == id }?.name } ?: "总部通用",
                    onToggle = { repository.upsertItem(item.copy(enabled = it)) },
                    onEdit = {
                        editingItem = item
                        showItemDialog = true
                    },
                    onLearn = {
                        scope.launch {
                            val result = learningService.learn(item.id)
                            operationMessage = if (result.success) "《${item.title}》学习完成" else result.error
                        }
                    },
                    onApprove = { repository.approveLearning(item.id) },
                    onDelete = { repository.deleteItem(item.id) },
                )
            }
        }
        item {
            KnowledgeHeader(
                icon = Icons.Outlined.ModelTraining,
                title = "人工训练与纠错",
                subtitle = "保存已审核的问答范例，智能体会优先学习处理方式；模型级SFT可直接填写供应商生成的微调模型ID。",
            )
        }
        item {
            Button(
                onClick = {
                    editingSample = null
                    showSampleDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.size(6.dp))
                Text("新增训练样本")
            }
        }
        if (state.trainingSamples.isEmpty()) {
            item { KnowledgeEmpty("暂无训练样本", "把优质人工回复或纠错结果保存为“问题—标准答案”。") }
        } else {
            items(state.trainingSamples, key = TrainingSample::id) { sample ->
                TrainingSampleCard(
                    sample = sample,
                    scopeName = sample.scopeId?.let { id -> business.units.firstOrNull { it.id == id }?.name } ?: "总部通用",
                    onToggle = { repository.upsertTrainingSample(sample.copy(approved = it)) },
                    onEdit = {
                        editingSample = sample
                        showSampleDialog = true
                    },
                    onDelete = { repository.deleteTrainingSample(sample.id) },
                )
            }
        }
    }

    if (showItemDialog) {
        KnowledgeItemDialog(
            initial = editingItem,
            units = business.units,
            defaultScopeId = business.currentScopeId,
            onDismiss = { showItemDialog = false },
            onSave = {
                val saved = repository.upsertItem(it)
                showItemDialog = false
                if (state.settings.autoLearnWithAi) {
                    scope.launch {
                        val result = learningService.learn(saved.id)
                        operationMessage = if (result.success) "《${saved.title}》学习完成" else result.error
                    }
                }
            },
        )
    }
    if (showSampleDialog) {
        TrainingSampleDialog(
            initial = editingSample,
            units = business.units,
            defaultScopeId = business.currentScopeId,
            onDismiss = { showSampleDialog = false },
            onSave = {
                repository.upsertTrainingSample(it)
                showSampleDialog = false
            },
        )
    }
}

@Composable
private fun KnowledgeSettingsCard(settings: KnowledgeSettings, onSave: (KnowledgeSettings) -> Unit) {
    var value by remember(settings) { mutableStateOf(settings) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("学习策略", style = MaterialTheme.typography.titleMedium)
            KnowledgeSwitch("启用专属知识库", value.enabled) { value = value.copy(enabled = it) }
            KnowledgeSwitch("上传/修改后自动调用主控模型学习", value.autoLearnWithAi) {
                value = value.copy(autoLearnWithAi = it)
            }
            KnowledgeSwitch("AI提炼后必须人工复核", value.requireReviewAfterLearning) {
                value = value.copy(requireReviewAfterLearning = it)
            }
            Text("每次回复检索 ${value.retrievalTopK} 条知识")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (2..6).forEach { count ->
                    FilterChip(
                        selected = value.retrievalTopK == count,
                        onClick = { value = value.copy(retrievalTopK = count) },
                        label = { Text(count.toString()) },
                    )
                }
            }
            Button(onClick = { onSave(value) }, modifier = Modifier.fillMaxWidth()) { Text("保存学习策略") }
        }
    }
}

@Composable
private fun KnowledgeSwitch(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun KnowledgeItemCard(
    item: KnowledgeItem,
    scopeName: String,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onLearn: () -> Unit,
    onApprove: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${item.sourceType.displayName} · $scopeName · ${item.status.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (item.status) {
                            KnowledgeStatus.FAILED -> MaterialTheme.colorScheme.error
                            KnowledgeStatus.NEEDS_REVIEW -> Color(0xFFB54708)
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                }
                Switch(checked = item.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "编辑") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "删除") }
            }
            Text(item.content, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
            if (item.aiSummary.isNotBlank()) {
                HorizontalDivider()
                Text("AI学习摘要", fontWeight = FontWeight.SemiBold)
                Text(item.aiSummary, maxLines = 4, style = MaterialTheme.typography.bodyMedium)
            }
            if (item.learningError.isNotBlank()) {
                Text(item.learningError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onLearn, modifier = Modifier.weight(1f), enabled = item.status != KnowledgeStatus.LEARNING) {
                    Text(if (item.status == KnowledgeStatus.LEARNING) "学习中" else "重新学习")
                }
                if (item.status == KnowledgeStatus.NEEDS_REVIEW) {
                    Button(onClick = onApprove, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.CheckCircle, null)
                        Spacer(Modifier.size(4.dp))
                        Text("审核通过")
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingSampleCard(
    sample: TrainingSample,
    scopeName: String,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(scopeName, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Switch(checked = sample.approved, onCheckedChange = onToggle)
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "编辑") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "删除") }
            }
            Text("问：${sample.question}", fontWeight = FontWeight.SemiBold)
            Text("标准答案：${sample.preferredAnswer}")
        }
    }
}

@Composable
private fun KnowledgeItemDialog(
    initial: KnowledgeItem?,
    units: List<OrgUnit>,
    defaultScopeId: String,
    onDismiss: () -> Unit,
    onSave: (KnowledgeItem) -> Unit,
) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var content by remember(initial) { mutableStateOf(initial?.content.orEmpty()) }
    var summary by remember(initial) { mutableStateOf(initial?.aiSummary.orEmpty()) }
    var scopeId by remember(initial) { mutableStateOf<String?>(initial?.scopeId ?: defaultScopeId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增手工知识" else "编辑知识") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth()) }
                item {
                    OutlinedTextField(
                        content,
                        { content = it },
                        label = { Text("知识正文") },
                        minLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (initial != null) {
                    item {
                        OutlinedTextField(
                            summary,
                            { summary = it },
                            label = { Text("AI摘要（可人工修订）") },
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    ScopeChoices(scopeId, units) { scopeId = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        KnowledgeItem(
                            id = initial?.id.orEmpty(),
                            title = title.trim(),
                            content = content.trim(),
                            sourceType = initial?.sourceType ?: KnowledgeSourceType.MANUAL,
                            sourceName = initial?.sourceName.orEmpty(),
                            scopeId = scopeId,
                            enabled = initial?.enabled ?: true,
                            status = if (summary.isNotBlank()) KnowledgeStatus.READY else KnowledgeStatus.INDEXED,
                            aiSummary = summary.trim(),
                        ),
                    )
                },
                enabled = title.isNotBlank() && content.isNotBlank(),
            ) { Text("保存并学习") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun TrainingSampleDialog(
    initial: TrainingSample?,
    units: List<OrgUnit>,
    defaultScopeId: String,
    onDismiss: () -> Unit,
    onSave: (TrainingSample) -> Unit,
) {
    var question by remember(initial) { mutableStateOf(initial?.question.orEmpty()) }
    var answer by remember(initial) { mutableStateOf(initial?.preferredAnswer.orEmpty()) }
    var scopeId by remember(initial) { mutableStateOf<String?>(initial?.scopeId ?: defaultScopeId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增训练样本" else "编辑训练样本") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                item {
                    OutlinedTextField(
                        question,
                        { question = it },
                        label = { Text("典型问题/评论") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        answer,
                        { answer = it },
                        label = { Text("审核后的标准答案") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { ScopeChoices(scopeId, units) { scopeId = it } }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        TrainingSample(
                            id = initial?.id.orEmpty(),
                            question = question.trim(),
                            preferredAnswer = answer.trim(),
                            scopeId = scopeId,
                            approved = initial?.approved ?: true,
                        ),
                    )
                },
                enabled = question.isNotBlank() && answer.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ScopeChoices(selected: String?, units: List<OrgUnit>, onSelected: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("知识适用范围")
        FilterChip(
            selected = selected == null,
            onClick = { onSelected(null) },
            label = { Text("总部通用（所有下级可用）") },
            modifier = Modifier.fillMaxWidth(),
        )
        units.forEach { unit ->
            FilterChip(
                selected = selected == unit.id,
                onClick = { onSelected(unit.id) },
                label = { Text("${unit.level.displayName} · ${unit.name}") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun KnowledgeHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(5.dp))
        HorizontalDivider()
    }
}

@Composable
private fun KnowledgeEmpty(title: String, text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
