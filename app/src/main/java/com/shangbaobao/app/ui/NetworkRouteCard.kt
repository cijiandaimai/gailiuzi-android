package com.shangbaobao.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.shangbaobao.app.ai.AiProviderConfig
import com.shangbaobao.app.ai.AiSettingsRepository
import com.shangbaobao.app.ai.MerchantRegion
import com.shangbaobao.app.ai.NetworkDiagnostics
import com.shangbaobao.app.ai.NetworkProbeResult
import com.shangbaobao.app.ai.NetworkRouteMode
import com.shangbaobao.app.ai.NetworkRouteSettings
import kotlinx.coroutines.launch

@Composable
fun NetworkRouteCard(
    initial: NetworkRouteSettings,
    providers: List<AiProviderConfig>,
    repository: AiSettingsRepository,
) {
    var settings by remember(initial) { mutableStateOf(initial) }
    var token by remember(initial.gatewayTokenConfigured) { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var probes by remember { mutableStateOf<List<NetworkProbeResult>>(emptyList()) }
    val context = LocalContext.current
    val diagnostics = remember(context) { NetworkDiagnostics(context) }
    val scope = rememberCoroutineScope()

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Route, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("绿色通道", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "智能选择模型直连或企业API网关",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.greenChannelEnabled,
                    onCheckedChange = { settings = settings.copy(greenChannelEnabled = it) },
                )
            }
            Text(
                "不内置VPN。国内模型优先官方直连；GPT、Gemini等按供应商可用地区和商户自建/签约HTTPS网关路由。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("商户所在地区", fontWeight = FontWeight.SemiBold)
            MerchantRegion.entries.forEach { region ->
                FilterChip(
                    selected = settings.region == region,
                    onClick = { settings = settings.copy(region = region) },
                    label = { Text(region.displayName) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("路由方式", fontWeight = FontWeight.SemiBold)
            NetworkRouteMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.routeMode == mode,
                    onClick = { settings = settings.copy(routeMode = mode) },
                    label = {
                        Column(Modifier.padding(vertical = 2.dp)) {
                            Text(mode.displayName)
                            Text(
                                mode.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (settings.greenChannelEnabled && settings.routeMode != NetworkRouteMode.DIRECT) {
                OutlinedTextField(
                    value = settings.gatewayBaseUrl,
                    onValueChange = { settings = settings.copy(gatewayBaseUrl = it) },
                    label = { Text("企业网关Base URL") },
                    supportingText = { Text("请求路径：/providers/{provider}/responses 或 /chat/completions") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = {
                        Text(if (settings.gatewayTokenConfigured) "网关令牌（留空则保留）" else "网关令牌")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("网关失败后尝试官方直连", modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.allowDirectFallback,
                        onCheckedChange = { settings = settings.copy(allowDirectFallback = it) },
                    )
                }
            }
            Button(
                onClick = {
                    repository.saveNetworkRoute(settings, token)
                    token = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存绿色通道") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        testing = true
                        probes = emptyList()
                        scope.launch {
                            val targets = providers.filter { it.enabled }.ifEmpty { providers.filter { it.provider.recommended } }
                            probes = targets.map { config ->
                                val route = repository.resolveRoute(config)
                                diagnostics.probe(route.baseUrl)
                            }
                            testing = false
                        }
                    },
                    enabled = !testing,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.NetworkCheck, null)
                    Spacer(Modifier.size(5.dp))
                    Text(if (testing) "诊断中" else "网络诊断")
                }
            }
            Text(
                diagnostics.activeNetworkSummary(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            probes.forEach { probe ->
                Text(
                    "${if (probe.reachable) "可达" else "不可达"} · ${probe.target} · ${probe.detail}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (probe.reachable) Color(0xFF067647) else MaterialTheme.colorScheme.error,
                )
            }
            if (settings.gatewayTokenConfigured) {
                TextButton(onClick = repository::clearGatewayToken) { Text("清除已保存的网关令牌") }
            }
            Text(
                "台湾及其他供应商支持地区可选直连；香港、澳门和其他地区以模型供应商实时可用范围及商户账号资格为准。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
