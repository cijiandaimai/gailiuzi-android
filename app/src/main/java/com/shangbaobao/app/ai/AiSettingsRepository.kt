package com.shangbaobao.app.ai

import android.content.Context
import androidx.core.content.edit
import com.shangbaobao.app.model.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AiSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val secretStore = SecureSecretStore(context)
    private val mutableState = MutableStateFlow(loadState())
    val state: StateFlow<AiSettingsState> = mutableState.asStateFlow()

    fun saveProvider(config: AiProviderConfig, newApiKey: String? = null) {
        if (!newApiKey.isNullOrBlank()) secretStore.put(providerSecretKey(config.provider), newApiKey.trim())
        preferences.edit {
            putBoolean("provider_${config.provider.name}_enabled", config.enabled)
            putString("provider_${config.provider.name}_base_url", config.baseUrl.trim())
            putString("provider_${config.provider.name}_model", config.model.trim())
        }
        reload()
    }

    fun clearProviderApiKey(provider: AiProvider) {
        secretStore.remove(providerSecretKey(provider))
        reload()
    }

    fun getProviderApiKey(provider: AiProvider): String? = secretStore.get(providerSecretKey(provider))

    fun setPrimaryProvider(provider: AiProvider) {
        preferences.edit { putString(KEY_PRIMARY_PROVIDER, provider.name) }
        reload()
    }

    fun saveNetworkRoute(settings: NetworkRouteSettings, newGatewayToken: String? = null) {
        if (!newGatewayToken.isNullOrBlank()) {
            secretStore.put(KEY_GATEWAY_TOKEN, newGatewayToken.trim())
        }
        preferences.edit {
            putBoolean(KEY_GREEN_CHANNEL_ENABLED, settings.greenChannelEnabled)
            putString(KEY_MERCHANT_REGION, settings.region.name)
            putString(KEY_NETWORK_ROUTE_MODE, settings.routeMode.name)
            putString(KEY_GATEWAY_BASE_URL, settings.gatewayBaseUrl.trim())
            putBoolean(KEY_ALLOW_DIRECT_FALLBACK, settings.allowDirectFallback)
            putInt(KEY_CONNECT_TIMEOUT_SECONDS, settings.connectTimeoutSeconds.coerceIn(5, 60))
        }
        reload()
    }

    fun clearGatewayToken() {
        secretStore.remove(KEY_GATEWAY_TOKEN)
        reload()
    }

    fun getGatewayToken(): String? = secretStore.get(KEY_GATEWAY_TOKEN)

    fun resolveRoute(config: AiProviderConfig): ResolvedAiRoute {
        val settings = mutableState.value.networkRoute
        if (!settings.greenChannelEnabled || settings.routeMode == NetworkRouteMode.DIRECT) {
            return ResolvedAiRoute(config.baseUrl, viaGateway = false)
        }
        val shouldUseGateway = when (settings.routeMode) {
            NetworkRouteMode.MERCHANT_GATEWAY -> true
            NetworkRouteMode.DIRECT -> false
            NetworkRouteMode.SMART -> !config.provider.mainlandFriendly &&
                settings.region in setOf(
                    MerchantRegion.AUTO,
                    MerchantRegion.MAINLAND_CHINA,
                    MerchantRegion.HONG_KONG_MACAU,
                )
        }
        if (!shouldUseGateway || settings.gatewayBaseUrl.isBlank()) {
            return ResolvedAiRoute(config.baseUrl, viaGateway = false)
        }
        return ResolvedAiRoute(
            baseUrl = settings.gatewayBaseUrl.trimEnd('/') + "/providers/${config.provider.routeKey}",
            viaGateway = true,
            gatewayToken = getGatewayToken().orEmpty(),
        )
    }

    fun saveProfile(profile: AssistantProfile) {
        preferences.edit {
            putBoolean(KEY_PROFILE_ENABLED, profile.enabled)
            putString(KEY_PROFILE_NAME, profile.identityName)
            putString(KEY_PROFILE_ROLE, profile.role)
            putString(KEY_PROFILE_PERSONALITY, profile.personality.name)
            putString(KEY_PROFILE_TONE, profile.tone)
            putString(KEY_PROFILE_CUSTOM, profile.customInstructions)
        }
        reload()
    }

    fun setPhraseLibraryEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_PHRASE_LIBRARY_ENABLED, enabled) }
        reload()
    }

    fun upsertPhrase(phrase: PhraseTemplate) {
        val current = mutableState.value.phrases.toMutableList()
        val normalized = if (phrase.id.isBlank()) phrase.copy(id = UUID.randomUUID().toString()) else phrase
        val index = current.indexOfFirst { it.id == normalized.id }
        if (index >= 0) current[index] = normalized else current.add(normalized)
        savePhrases(current)
    }

    fun deletePhrase(id: String) {
        savePhrases(mutableState.value.phrases.filterNot { it.id == id })
    }

    fun resetDefaultPhrases() {
        savePhrases(defaultPhrases())
    }

    fun saveOfficialPlatform(config: OfficialPlatformConfig, newClientSecret: String? = null) {
        if (!newClientSecret.isNullOrBlank()) {
            secretStore.put(platformSecretKey(config.platform), newClientSecret.trim())
        }
        preferences.edit {
            putBoolean("official_${config.platform.name}_enabled", config.enabled)
            putString("official_${config.platform.name}_base_url", config.baseUrl.trim())
            putString("official_${config.platform.name}_client_id", config.clientId.trim())
            putString("official_${config.platform.name}_scopes", config.scopes.trim())
        }
        reload()
    }

    fun clearOfficialPlatformSecret(platform: Platform) {
        secretStore.remove(platformSecretKey(platform))
        reload()
    }

    private fun reload() {
        mutableState.value = loadState()
    }

    private fun loadState(): AiSettingsState = AiSettingsState(
        providers = AiProvider.entries.map { provider ->
            AiProviderConfig(
                provider = provider,
                enabled = preferences.getBoolean("provider_${provider.name}_enabled", false),
                baseUrl = preferences.getString(
                    "provider_${provider.name}_base_url",
                    provider.defaultBaseUrl,
                ).orEmpty().ifBlank { provider.defaultBaseUrl },
                model = preferences.getString(
                    "provider_${provider.name}_model",
                    provider.defaultModel,
                ).orEmpty().ifBlank { provider.defaultModel },
                apiKeyConfigured = secretStore.contains(providerSecretKey(provider)),
            )
        },
        primaryProvider = preferences.getString(KEY_PRIMARY_PROVIDER, AiProvider.OPENAI.name)
            ?.let { saved -> AiProvider.entries.firstOrNull { it.name == saved } }
            ?: AiProvider.OPENAI,
        assistantProfile = AssistantProfile(
            enabled = preferences.getBoolean(KEY_PROFILE_ENABLED, true),
            identityName = preferences.getString(KEY_PROFILE_NAME, "小改").orEmpty(),
            role = preferences.getString(KEY_PROFILE_ROLE, "服务行业商家客服与口碑运营助手").orEmpty(),
            personality = preferences.getString(
                KEY_PROFILE_PERSONALITY,
                PersonalityPreset.PROFESSIONAL_WARM.name,
            )?.let { saved -> PersonalityPreset.entries.firstOrNull { it.name == saved } }
                ?: PersonalityPreset.PROFESSIONAL_WARM,
            tone = preferences.getString(KEY_PROFILE_TONE, "真诚、简洁、有分寸").orEmpty(),
            customInstructions = preferences.getString(KEY_PROFILE_CUSTOM, "").orEmpty(),
        ),
        phraseLibraryEnabled = preferences.getBoolean(KEY_PHRASE_LIBRARY_ENABLED, true),
        phrases = loadPhrases(),
        officialPlatforms = Platform.entries.map(::loadOfficialPlatform),
        networkRoute = loadNetworkRoute(),
    )

    private fun loadNetworkRoute(): NetworkRouteSettings = NetworkRouteSettings(
        greenChannelEnabled = preferences.getBoolean(KEY_GREEN_CHANNEL_ENABLED, false),
        region = preferences.getString(KEY_MERCHANT_REGION, MerchantRegion.AUTO.name)
            ?.let { saved -> MerchantRegion.entries.firstOrNull { it.name == saved } }
            ?: MerchantRegion.AUTO,
        routeMode = preferences.getString(KEY_NETWORK_ROUTE_MODE, NetworkRouteMode.SMART.name)
            ?.let { saved -> NetworkRouteMode.entries.firstOrNull { it.name == saved } }
            ?: NetworkRouteMode.SMART,
        gatewayBaseUrl = preferences.getString(KEY_GATEWAY_BASE_URL, "").orEmpty(),
        gatewayTokenConfigured = secretStore.contains(KEY_GATEWAY_TOKEN),
        allowDirectFallback = preferences.getBoolean(KEY_ALLOW_DIRECT_FALLBACK, true),
        connectTimeoutSeconds = preferences.getInt(KEY_CONNECT_TIMEOUT_SECONDS, 20).coerceIn(5, 60),
    )

    private fun loadOfficialPlatform(platform: Platform): OfficialPlatformConfig {
        val defaults = when (platform) {
            Platform.DOUYIN -> "https://open.douyin.com" to "video.comment,video.search"
            Platform.MEITUAN -> "https://openapi.meituan.com" to ""
            Platform.XIAOHONGSHU -> "https://open.xiaohongshu.com" to ""
        }
        return OfficialPlatformConfig(
            platform = platform,
            enabled = preferences.getBoolean("official_${platform.name}_enabled", false),
            baseUrl = preferences.getString("official_${platform.name}_base_url", defaults.first)
                .orEmpty().ifBlank { defaults.first },
            clientId = preferences.getString("official_${platform.name}_client_id", "").orEmpty(),
            clientSecretConfigured = secretStore.contains(platformSecretKey(platform)),
            scopes = preferences.getString("official_${platform.name}_scopes", defaults.second).orEmpty(),
        )
    }

    private fun loadPhrases(): List<PhraseTemplate> {
        val raw = preferences.getString(KEY_PHRASES_JSON, null) ?: return defaultPhrases()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        PhraseTemplate(
                            id = item.getString("id"),
                            title = item.getString("title"),
                            intent = item.optString("intent"),
                            keywords = item.optJSONArray("keywords")?.let { words ->
                                List(words.length()) { words.getString(it) }
                            }.orEmpty(),
                            content = item.getString("content"),
                            enabled = item.optBoolean("enabled", true),
                            priority = item.optInt("priority", 50),
                            platform = item.optString("platform").takeIf { it.isNotBlank() }
                                ?.let { name -> Platform.entries.firstOrNull { it.name == name } },
                        ),
                    )
                }
            }
        }.getOrElse { defaultPhrases() }
    }

    private fun savePhrases(phrases: List<PhraseTemplate>) {
        val array = JSONArray()
        phrases.forEach { phrase ->
            array.put(
                JSONObject()
                    .put("id", phrase.id)
                    .put("title", phrase.title)
                    .put("intent", phrase.intent)
                    .put("keywords", JSONArray(phrase.keywords))
                    .put("content", phrase.content)
                    .put("enabled", phrase.enabled)
                    .put("priority", phrase.priority)
                    .put("platform", phrase.platform?.name.orEmpty()),
            )
        }
        preferences.edit { putString(KEY_PHRASES_JSON, array.toString()) }
        reload()
    }

    private fun providerSecretKey(provider: AiProvider) = "provider_${provider.name}_api_key"
    private fun platformSecretKey(platform: Platform) = "official_${platform.name}_client_secret"

    companion object {
        private const val PREFERENCES_NAME = "ai_settings"
        private const val KEY_PROFILE_ENABLED = "profile_enabled"
        private const val KEY_PRIMARY_PROVIDER = "primary_provider"
        private const val KEY_GREEN_CHANNEL_ENABLED = "green_channel_enabled"
        private const val KEY_MERCHANT_REGION = "merchant_region"
        private const val KEY_NETWORK_ROUTE_MODE = "network_route_mode"
        private const val KEY_GATEWAY_BASE_URL = "gateway_base_url"
        private const val KEY_GATEWAY_TOKEN = "network_gateway_token"
        private const val KEY_ALLOW_DIRECT_FALLBACK = "allow_direct_fallback"
        private const val KEY_CONNECT_TIMEOUT_SECONDS = "connect_timeout_seconds"
        private const val KEY_PROFILE_NAME = "profile_name"
        private const val KEY_PROFILE_ROLE = "profile_role"
        private const val KEY_PROFILE_PERSONALITY = "profile_personality"
        private const val KEY_PROFILE_TONE = "profile_tone"
        private const val KEY_PROFILE_CUSTOM = "profile_custom"
        private const val KEY_PHRASE_LIBRARY_ENABLED = "phrase_library_enabled"
        private const val KEY_PHRASES_JSON = "phrases_json"

        fun defaultPhrases(): List<PhraseTemplate> = listOf(
            PhraseTemplate(
                id = "default_praise",
                title = "感谢认可",
                intent = "PRAISE",
                keywords = listOf("满意", "喜欢", "推荐", "不错", "专业"),
                content = "感谢您的认可和分享，我们会继续认真做好每一次服务，期待再次见到您。",
                priority = 80,
            ),
            PhraseTemplate(
                id = "default_location",
                title = "门店位置咨询",
                intent = "LOCATION_QUERY",
                keywords = listOf("地址", "在哪里", "哪家店", "怎么去", "停车"),
                content = "您好，可以点击平台内的官方门店页查看地址、营业时间和导航信息；告诉我您所在的商圈，也可以帮您匹配更方便的门店。",
                priority = 85,
            ),
            PhraseTemplate(
                id = "default_price",
                title = "价格项目咨询",
                intent = "PRICE_QUERY",
                keywords = listOf("价格", "多少钱", "收费", "套餐", "团购"),
                content = "您好，不同门店和项目的实际价格可能不同，建议以平台内官方门店页当前展示为准；您也可以告诉我想了解的项目和城市。",
                priority = 85,
            ),
            PhraseTemplate(
                id = "default_appointment",
                title = "预约咨询",
                intent = "APPOINTMENT_QUERY",
                keywords = listOf("预约", "有空吗", "排队", "周末", "今天"),
                content = "您好，可以通过平台内官方门店入口查看并提交预约；如果告诉我城市、门店和期望时间，我可以先帮您整理预约信息。",
                priority = 80,
            ),
            PhraseTemplate(
                id = "default_complaint",
                title = "服务体验反馈",
                intent = "SERVICE_COMPLAINT",
                keywords = listOf("投诉", "不满意", "态度", "等太久", "退款"),
                content = "您好，给您带来不好的体验，我们很重视。为了尽快核实处理，请通过平台内订单或官方客服入口联系我们，并提供消费门店和时间，我们会安排负责人跟进。",
                priority = 95,
            ),
        )
    }
}
