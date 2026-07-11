package com.shangbaobao.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBusiness
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.SyncAlt
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangbaobao.app.business.BusinessProfile
import com.shangbaobao.app.business.BusinessRepository
import com.shangbaobao.app.business.BusinessRole
import com.shangbaobao.app.business.ComplianceSettings
import com.shangbaobao.app.business.FeedbackSentiment
import com.shangbaobao.app.business.FeedbackSource
import com.shangbaobao.app.business.FeedbackStatus
import com.shangbaobao.app.business.OrgLevel
import com.shangbaobao.app.business.OrgUnit
import com.shangbaobao.app.business.ReputationRecord
import com.shangbaobao.app.business.ServiceIssueCategory
import com.shangbaobao.app.business.ServiceVertical

@Composable
fun BusinessDashboard(repository: BusinessRepository) {
    val state by repository.state.collectAsState()
    var showProfile by remember { mutableStateOf(false) }
    var showUnit by remember { mutableStateOf(false) }
    var editingUnit by remember { mutableStateOf<OrgUnit?>(null) }
    var showFeedback by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Store, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.profile.merchantName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${state.profile.vertical.displayName} · ${state.profile.currentRole.displayName}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showProfile = true }) {
                        Icon(Icons.Outlined.Edit, "编辑商家")
                    }
                }
                Text("当前管理范围", fontWeight = FontWeight.SemiBold)
                state.units.filter(OrgUnit::enabled).forEach { unit ->
                    FilterChip(
                        selected = unit.id == state.currentScopeId,
                        onClick = { repository.setCurrentScope(unit.id) },
                        label = { Text("${unit.level.displayName} · ${unit.name}") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BusinessMetric("待回复", state.snapshot.pendingReplies.toString(), Modifier.weight(1f))
            BusinessMetric("风险事件", state.snapshot.riskCases.toString(), Modifier.weight(1f), Color(0xFFB42318))
            BusinessMetric("门店", state.units.count { it.level == OrgLevel.STORE }.toString(), Modifier.weight(1f))
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.SyncAlt, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text("线上线下一体化口碑", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "统一汇总抖音/美团/小红书线上评价、商品评价与门店服务反馈，按总部—区域—门店下钻。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChannelStatus("线上评价", state.snapshot.onlineReviews, "等待平台授权", Modifier.weight(1f))
                    ChannelStatus("线下反馈", state.snapshot.offlineFeedback, "等待收银/CRM接入", Modifier.weight(1f))
                }
                Button(onClick = { showFeedback = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.RateReview, null)
                    Spacer(Modifier.size(6.dp))
                    Text("录入门店服务反馈")
                }
                if (state.reputationRecords.isEmpty()) {
                    Text(
                        "统一评价收件箱暂无数据；平台授权后线上评价会自动进入，线下反馈可先手工录入。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("统一评价收件箱", fontWeight = FontWeight.SemiBold)
                    state.reputationRecords.take(5).forEach { record ->
                        ReputationRecordRow(
                            record = record,
                            unitName = state.units.firstOrNull { it.id == record.orgUnitId }?.name ?: "未知门店",
                            onStatus = { repository.updateReputationStatus(record.id, it) },
                            onDelete = { repository.deleteReputationRecord(record.id) },
                        )
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AddBusiness, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text("多层级门店管理", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            editingUnit = null
                            showUnit = true
                        },
                    ) { Text("新增") }
                }
                state.units.forEach { unit ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${unit.level.displayName} · ${unit.name}", fontWeight = FontWeight.SemiBold)
                            Text(
                                listOf(unit.city, unit.storeCode, unit.onlineShopNames.joinToString("/"))
                                    .filter(String::isNotBlank).joinToString(" · ").ifBlank { "未补充资料" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = {
                                editingUnit = unit
                                showUnit = true
                            },
                        ) { Icon(Icons.Outlined.Edit, "编辑") }
                        if (unit.level != OrgLevel.HEADQUARTERS) {
                            IconButton(onClick = { repository.deleteUnit(unit.id) }) {
                                Icon(Icons.Outlined.Delete, "删除")
                            }
                        }
                    }
                }
            }
        }

        ComplianceCard(state.compliance, repository::saveCompliance)
    }

    if (showProfile) {
        BusinessProfileDialog(
            initial = state.profile,
            onDismiss = { showProfile = false },
            onSave = {
                repository.saveProfile(it)
                showProfile = false
            },
        )
    }
    if (showUnit) {
        OrgUnitDialog(
            initial = editingUnit,
            units = state.units,
            onDismiss = { showUnit = false },
            onSave = {
                repository.upsertUnit(it)
                showUnit = false
            },
        )
    }
    if (showFeedback) {
        FeedbackDialog(
            units = state.units,
            defaultUnitId = state.currentScopeId,
            onDismiss = { showFeedback = false },
            onSave = {
                repository.upsertReputationRecord(it)
                showFeedback = false
            },
        )
    }
}

@Composable
private fun BusinessMetric(
    label: String,
    value: String,
    modifier: Modifier,
    valueColor: Color = MaterialTheme.colorScheme.primary,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = valueColor, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChannelStatus(title: String, count: Int, status: String, modifier: Modifier) {
    Column(modifier) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text("$count 条", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReputationRecordRow(
    record: ReputationRecord,
    unitName: String,
    onStatus: (FeedbackStatus) -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${record.source.displayName} · $unitName · ${record.sentiment.displayName}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (record.sentiment == FeedbackSentiment.NEGATIVE) Color(0xFFB42318)
                    else MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "删除反馈") }
            }
            Text(record.content, maxLines = 3)
            Text("${record.category.displayName} · ${record.status.displayName}", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FeedbackStatus.entries.forEach { status ->
                    FilterChip(
                        selected = record.status == status,
                        onClick = { onStatus(status) },
                        label = { Text(status.displayName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ComplianceCard(settings: ComplianceSettings, onSave: (ComplianceSettings) -> Unit) {
    var value by remember(settings) { mutableStateOf(settings) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.HealthAndSafety, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text("服务行业合规风控", style = MaterialTheme.typography.titleMedium)
            }
            ComplianceSwitch("医疗功效与绝对化宣称校验", value.medicalClaimsGuard) {
                value = value.copy(medicalClaimsGuard = it)
            }
            ComplianceSwitch("价格、优惠与效果承诺校验", value.priceAccuracyGuard) {
                value = value.copy(priceAccuracyGuard = it)
            }
            ComplianceSwitch("手机号、邮箱等个人信息校验", value.personalDataGuard) {
                value = value.copy(personalDataGuard = it)
            }
            ComplianceSwitch("负面评价自动升级", value.negativeReviewEscalation) {
                value = value.copy(negativeReviewEscalation = it)
            }
            ComplianceSwitch("风险回复必须人工审核", value.requireApprovalForRisk) {
                value = value.copy(requireApprovalForRisk = it)
            }
            OutlinedTextField(
                value = value.customForbiddenTerms.joinToString("，"),
                onValueChange = { input ->
                    value = value.copy(
                        customForbiddenTerms = input.split('，', ',').map(String::trim).filter(String::isNotBlank),
                    )
                },
                label = { Text("商家禁用词，逗号分隔") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onSave(value) }, modifier = Modifier.fillMaxWidth()) { Text("保存风控规则") }
        }
    }
}

@Composable
private fun ComplianceSwitch(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun BusinessProfileDialog(
    initial: BusinessProfile,
    onDismiss: () -> Unit,
    onSave: (BusinessProfile) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial.merchantName) }
    var vertical by remember(initial) { mutableStateOf(initial.vertical) }
    var role by remember(initial) { mutableStateOf(initial.currentRole) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("商家与权限") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    OutlinedTextField(name, { name = it }, label = { Text("品牌名称") }, modifier = Modifier.fillMaxWidth())
                }
                item { Text("行业") }
                items(ServiceVertical.entries) { option ->
                    FilterChip(
                        selected = vertical == option,
                        onClick = { vertical = option },
                        label = { Text(option.displayName) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { Text("当前角色") }
                items(BusinessRole.entries) { option ->
                    FilterChip(
                        selected = role == option,
                        onClick = { role = option },
                        label = { Text("${option.displayName} · ${option.description}") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(BusinessProfile(name.trim(), vertical, role)) },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun OrgUnitDialog(
    initial: OrgUnit?,
    units: List<OrgUnit>,
    onDismiss: () -> Unit,
    onSave: (OrgUnit) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var level by remember(initial) { mutableStateOf(initial?.level ?: OrgLevel.STORE) }
    var parentId by remember(initial) { mutableStateOf(initial?.parentId) }
    var city by remember(initial) { mutableStateOf(initial?.city.orEmpty()) }
    var code by remember(initial) { mutableStateOf(initial?.storeCode.orEmpty()) }
    var shops by remember(initial) { mutableStateOf(initial?.onlineShopNames?.joinToString("，").orEmpty()) }
    val validParents = units.filter { parent ->
        parent.id != initial?.id && when (level) {
            OrgLevel.HEADQUARTERS -> false
            OrgLevel.REGION -> parent.level == OrgLevel.HEADQUARTERS
            OrgLevel.STORE -> parent.level in setOf(OrgLevel.HEADQUARTERS, OrgLevel.REGION)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增组织/门店" else "编辑组织/门店") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                }
                items(OrgLevel.entries) { option ->
                    FilterChip(
                        selected = level == option,
                        onClick = {
                            level = option
                            parentId = null
                        },
                        label = { Text(option.displayName) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = initial?.level != OrgLevel.HEADQUARTERS,
                    )
                }
                if (validParents.isNotEmpty()) {
                    item { Text("上级") }
                    items(validParents) { parent ->
                        FilterChip(
                            selected = parentId == parent.id,
                            onClick = { parentId = parent.id },
                            label = { Text("${parent.level.displayName} · ${parent.name}") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    OutlinedTextField(city, { city = it }, label = { Text("城市") }, modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedTextField(code, { code = it }, label = { Text("门店编码") }, modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedTextField(
                        shops,
                        { shops = it },
                        label = { Text("关联线上店铺，逗号分隔") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        OrgUnit(
                            id = initial?.id.orEmpty(),
                            name = name.trim(),
                            level = level,
                            parentId = if (level == OrgLevel.HEADQUARTERS) null else parentId,
                            city = city.trim(),
                            storeCode = code.trim(),
                            onlineShopNames = shops.split('，', ',').map(String::trim).filter(String::isNotBlank),
                            enabled = initial?.enabled ?: true,
                        ),
                    )
                },
                enabled = name.isNotBlank() && (level == OrgLevel.HEADQUARTERS || parentId != null),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun FeedbackDialog(
    units: List<OrgUnit>,
    defaultUnitId: String,
    onDismiss: () -> Unit,
    onSave: (ReputationRecord) -> Unit,
) {
    var source by remember { mutableStateOf(FeedbackSource.OFFLINE_SURVEY) }
    var unitId by remember { mutableStateOf(defaultUnitId) }
    var author by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf<Int?>(null) }
    var sentiment by remember { mutableStateOf(FeedbackSentiment.NEUTRAL) }
    var category by remember { mutableStateOf(ServiceIssueCategory.OTHER) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("录入线上/线下反馈") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Text("来源")
                    FeedbackSource.entries.forEach { option ->
                        FilterChip(
                            selected = source == option,
                            onClick = { source = option },
                            label = { Text(option.displayName) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    Text("所属门店/组织")
                    units.forEach { unit ->
                        FilterChip(
                            selected = unitId == unit.id,
                            onClick = { unitId = unit.id },
                            label = { Text("${unit.level.displayName} · ${unit.name}") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        author,
                        { author = it },
                        label = { Text("顾客称呼/匿名标识") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        content,
                        { content = it },
                        label = { Text("反馈内容") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text("评分")
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        (1..5).forEach { value ->
                            FilterChip(
                                selected = rating == value,
                                onClick = { rating = value },
                                label = { Text(value.toString()) },
                            )
                        }
                    }
                }
                item {
                    Text("情绪")
                    FeedbackSentiment.entries.forEach { option ->
                        FilterChip(
                            selected = sentiment == option,
                            onClick = { sentiment = option },
                            label = { Text(option.displayName) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    Text("问题分类")
                    ServiceIssueCategory.entries.forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(option.displayName) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        ReputationRecord(
                            id = "",
                            source = source,
                            orgUnitId = unitId,
                            content = content.trim(),
                            authorAlias = author.trim().ifBlank { "匿名顾客" },
                            rating = rating,
                            sentiment = sentiment,
                            category = category,
                        ),
                    )
                },
                enabled = content.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
