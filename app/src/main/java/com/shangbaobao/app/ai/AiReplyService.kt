package com.shangbaobao.app.ai

import com.shangbaobao.app.business.BusinessRepository
import com.shangbaobao.app.business.ComplianceEngine
import com.shangbaobao.app.knowledge.KnowledgeBaseRepository
import com.shangbaobao.app.model.Platform

class AiReplyService(
    private val repository: AiSettingsRepository,
    private val gateway: AiGateway = AiGateway(),
    private val knowledgeRepository: KnowledgeBaseRepository? = null,
    private val businessRepository: BusinessRepository? = null,
) {
    suspend fun generateReply(
        userContent: String,
        platform: Platform,
        intentHint: String? = null,
        context: String = "",
    ): AiCallResult {
        val settings = repository.state.value
        val business = businessRepository?.state?.value
        val scopeIds = business?.let(::scopeLineage).orEmpty()
        val knowledgeContext = knowledgeRepository?.let { knowledge ->
            buildString {
                val snippets = knowledge.retrieve(userContent, scopeIds)
                if (snippets.isNotEmpty()) {
                    appendLine("商家专属知识库（只能依据这些事实，不得补充猜测）：")
                    snippets.forEach { appendLine("- 【${it.title}】${it.content}") }
                }
                val examples = knowledge.matchingTrainingSamples(userContent, scopeIds)
                if (examples.isNotEmpty()) {
                    appendLine("商家已审核训练样本（学习处理方式，不要机械照抄无关内容）：")
                    examples.forEach { appendLine("- 问：${it.question}\n  答：${it.preferredAnswer}") }
                }
            }.trim()
        }.orEmpty()
        val mergedContext = listOf(context.trim(), knowledgeContext)
            .filter(String::isNotBlank)
            .joinToString("\n\n")
        var prompt = ReplyPromptComposer.compose(
            profile = settings.assistantProfile,
            phraseLibraryEnabled = settings.phraseLibraryEnabled,
            phrases = settings.phrases,
            userContent = userContent,
            platform = platform,
            intentHint = intentHint,
            context = mergedContext,
        )
        business?.let {
            prompt = prompt.copy(
                instructions = prompt.instructions + "\n" +
                    ComplianceEngine.promptGuardrails(it.profile.vertical, it.compliance),
            )
        }
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
            val route = repository.resolveRoute(config)
            var result = gateway.generate(
                config = config,
                apiKey = apiKey,
                prompt = prompt,
                route = route,
                connectTimeoutSeconds = settings.networkRoute.connectTimeoutSeconds,
            )
            if (!result.success && route.viaGateway && settings.networkRoute.allowDirectFallback) {
                result = gateway.generate(
                    config = config,
                    apiKey = apiKey,
                    prompt = prompt,
                    route = ResolvedAiRoute(config.baseUrl, false),
                    connectTimeoutSeconds = settings.networkRoute.connectTimeoutSeconds,
                )
            }
            if (result.success) {
                val compliance = business?.let {
                    ComplianceEngine.evaluate(result.text, it.profile.vertical, it.compliance)
                }
                return result.copy(
                    provider = config.provider,
                    preferredPhraseId = prompt.preferredPhraseId,
                    riskFlags = compliance?.flags.orEmpty(),
                    requiresApproval = compliance?.requiresApproval ?: false,
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

    private fun scopeLineage(state: com.shangbaobao.app.business.BusinessState): Set<String> {
        val result = mutableSetOf<String>()
        var current = state.currentScope
        while (current != null && result.add(current.id)) {
            current = current.parentId?.let { parentId -> state.units.firstOrNull { it.id == parentId } }
        }
        return result
    }
}
