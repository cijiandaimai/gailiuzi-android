package com.gailiuzi.app.ai

import com.gailiuzi.app.model.Platform

enum class AiApiProtocol {
    RESPONSES,
    CHAT_COMPLETIONS,
}

enum class AiProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val protocol: AiApiProtocol,
    val routeKey: String,
    val recommended: Boolean = false,
    val supportsMultimodal: Boolean = true,
    val mainlandFriendly: Boolean = false,
) {
    OPENAI(
        displayName = "GPT / OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-5-mini",
        protocol = AiApiProtocol.RESPONSES,
        routeKey = "openai",
        recommended = true,
    ),
    DOUBAO(
        displayName = "豆包 / 火山方舟",
        defaultBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        defaultModel = "doubao-seed-2-0-lite-260215",
        protocol = AiApiProtocol.RESPONSES,
        routeKey = "doubao",
        recommended = true,
        mainlandFriendly = true,
    ),
    QWEN(
        displayName = "千问 / 阿里云百炼",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultModel = "qwen3.7-plus",
        protocol = AiApiProtocol.CHAT_COMPLETIONS,
        routeKey = "qwen",
        mainlandFriendly = true,
    ),
    GEMINI(
        displayName = "Gemini / Google AI",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        defaultModel = "gemini-3.5-flash",
        protocol = AiApiProtocol.CHAT_COMPLETIONS,
        routeKey = "gemini",
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-v4-flash",
        protocol = AiApiProtocol.CHAT_COMPLETIONS,
        routeKey = "deepseek",
        supportsMultimodal = false,
        mainlandFriendly = true,
    ),
    CUSTOM_OPENAI(
        displayName = "自定义 OpenAI 兼容",
        defaultBaseUrl = "https://example.com/v1",
        defaultModel = "your-model",
        protocol = AiApiProtocol.CHAT_COMPLETIONS,
        routeKey = "custom",
    ),
}

data class AiProviderConfig(
    val provider: AiProvider,
    val enabled: Boolean = false,
    val baseUrl: String = provider.defaultBaseUrl,
    val model: String = provider.defaultModel,
    val apiKeyConfigured: Boolean = false,
)

enum class MerchantRegion(val displayName: String) {
    AUTO("自动识别"),
    MAINLAND_CHINA("中国大陆"),
    HONG_KONG_MACAU("中国香港/澳门"),
    TAIWAN("中国台湾"),
    OVERSEAS("其他海外地区"),
}

enum class NetworkRouteMode(val displayName: String, val description: String) {
    SMART("智能路由", "国内模型直连；受区域限制的模型按配置切换企业API网关"),
    DIRECT("全部直连", "直接连接各模型官方Base URL"),
    MERCHANT_GATEWAY("企业网关", "全部请求经商户自建或已签约的HTTPS API网关"),
}

data class NetworkRouteSettings(
    val greenChannelEnabled: Boolean = false,
    val region: MerchantRegion = MerchantRegion.AUTO,
    val routeMode: NetworkRouteMode = NetworkRouteMode.SMART,
    val gatewayBaseUrl: String = "",
    val gatewayTokenConfigured: Boolean = false,
    val allowDirectFallback: Boolean = true,
    val connectTimeoutSeconds: Int = 20,
)

data class ResolvedAiRoute(
    val baseUrl: String,
    val viaGateway: Boolean,
    val gatewayToken: String = "",
)

enum class PersonalityPreset(val displayName: String, val instruction: String) {
    PROFESSIONAL_WARM("专业温和", "专业、温和、耐心，先解决问题再考虑转化"),
    FRIENDLY_NATURAL("亲切自然", "像熟悉本地生活的朋友，表达自然，不使用生硬营销话术"),
    CONCISE_RELIABLE("简洁可靠", "简洁、准确、克制，不夸大、不绕弯"),
    ENTHUSIASTIC("热情活泼", "积极、有活力，但不过度使用感叹号和表情"),
    CRISIS_CALM("公关沉稳", "沉稳、克制、尊重事实，避免争辩和未经核实的结论"),
}

data class AssistantProfile(
    val enabled: Boolean = true,
    val identityName: String = "小改",
    val role: String = "服务行业商家客服与口碑运营助手",
    val personality: PersonalityPreset = PersonalityPreset.PROFESSIONAL_WARM,
    val tone: String = "真诚、简洁、有分寸",
    val customInstructions: String = "",
)

data class PhraseTemplate(
    val id: String,
    val title: String,
    val intent: String,
    val keywords: List<String>,
    val content: String,
    val enabled: Boolean = true,
    val priority: Int = 50,
    val platform: Platform? = null,
)

data class OfficialPlatformConfig(
    val platform: Platform,
    val enabled: Boolean = false,
    val baseUrl: String,
    val clientId: String = "",
    val clientSecretConfigured: Boolean = false,
    val scopes: String = "",
    val capabilitySummary: String = "",
    val documentationUrl: String = "",
)

data class AiSettingsState(
    val providers: List<AiProviderConfig>,
    val primaryProvider: AiProvider,
    val assistantProfile: AssistantProfile,
    val phraseLibraryEnabled: Boolean,
    val phrases: List<PhraseTemplate>,
    val officialPlatforms: List<OfficialPlatformConfig>,
    val networkRoute: NetworkRouteSettings,
)

enum class AiErrorType {
    NONE,
    INVALID_CONFIGURATION,
    AUTHENTICATION,
    MODEL_NOT_FOUND,
    RATE_LIMIT,
    QUOTA_EXCEEDED,
    CONTENT_REJECTED,
    TIMEOUT,
    NETWORK,
    SERVER,
    GATEWAY,
    INVALID_RESPONSE,
    CANCELLED,
    UNKNOWN,
}

data class AiCallResult(
    val success: Boolean,
    val text: String = "",
    val error: String = "",
    val errorType: AiErrorType = AiErrorType.NONE,
    val statusCode: Int? = null,
    val retryable: Boolean = false,
    val attempts: Int = 1,
    val provider: AiProvider? = null,
    val preferredPhraseId: String? = null,
    val riskFlags: List<String> = emptyList(),
    val requiresApproval: Boolean = false,
)
