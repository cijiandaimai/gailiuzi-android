package com.shangbaobao.app.ai

import com.shangbaobao.app.model.Platform

enum class AiProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
) {
    OPENAI(
        displayName = "GPT / OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-5-mini",
    ),
    DOUBAO(
        displayName = "豆包 / 火山方舟",
        defaultBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        defaultModel = "doubao-seed-2-0-lite-260215",
    ),
}

data class AiProviderConfig(
    val provider: AiProvider,
    val enabled: Boolean = false,
    val baseUrl: String = provider.defaultBaseUrl,
    val model: String = provider.defaultModel,
    val apiKeyConfigured: Boolean = false,
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
    val role: String = "商家客服与口碑运营助手",
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
)

data class AiSettingsState(
    val providers: List<AiProviderConfig>,
    val primaryProvider: AiProvider,
    val assistantProfile: AssistantProfile,
    val phraseLibraryEnabled: Boolean,
    val phrases: List<PhraseTemplate>,
    val officialPlatforms: List<OfficialPlatformConfig>,
)

data class AiCallResult(
    val success: Boolean,
    val text: String = "",
    val error: String = "",
    val provider: AiProvider? = null,
    val preferredPhraseId: String? = null,
)
