package com.shangbaobao.app.ai

import com.shangbaobao.app.model.Platform

class AiReplyService(
    private val repository: AiSettingsRepository,
    private val gateway: AiGateway = AiGateway(),
) {
    suspend fun generateReply(
        userContent: String,
        platform: Platform,
        intentHint: String? = null,
        context: String = "",
    ): AiCallResult {
        val settings = repository.state.value
        val prompt = ReplyPromptComposer.compose(
            profile = settings.assistantProfile,
            phraseLibraryEnabled = settings.phraseLibraryEnabled,
            phrases = settings.phrases,
            userContent = userContent,
            platform = platform,
            intentHint = intentHint,
            context = context,
        )
        val orderedProviders = settings.providers
            .filter { it.enabled && it.apiKeyConfigured }
            .sortedBy { if (it.provider == settings.primaryProvider) 0 else 1 }

        if (orderedProviders.isEmpty()) {
            return AiCallResult(
                success = false,
                error = "没有已启用且配置完成的AI提供商",
                preferredPhraseId = prompt.preferredPhraseId,
            )
        }

        val errors = mutableListOf<String>()
        orderedProviders.forEach { config ->
            val apiKey = repository.getProviderApiKey(config.provider).orEmpty()
            val result = gateway.generate(config, apiKey, prompt)
            if (result.success) {
                return result.copy(
                    provider = config.provider,
                    preferredPhraseId = prompt.preferredPhraseId,
                )
            }
            errors += "${config.provider.displayName}: ${result.error}"
        }
        return AiCallResult(
            success = false,
            error = errors.joinToString("；"),
            preferredPhraseId = prompt.preferredPhraseId,
        )
    }
}
