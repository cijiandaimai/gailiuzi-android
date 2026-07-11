package com.shangbaobao.app.knowledge

import com.shangbaobao.app.ai.AiCallResult
import com.shangbaobao.app.ai.AiGateway
import com.shangbaobao.app.ai.AiProvider
import com.shangbaobao.app.ai.AiSettingsRepository
import com.shangbaobao.app.ai.ReplyPrompt
import com.shangbaobao.app.ai.ResolvedAiRoute

class KnowledgeLearningService(
    private val knowledgeRepository: KnowledgeBaseRepository,
    private val aiRepository: AiSettingsRepository,
    private val gateway: AiGateway,
) {
    suspend fun learn(itemId: String): AiCallResult {
        val item = knowledgeRepository.getItem(itemId)
            ?: return AiCallResult(false, error = "知识条目不存在")
        val settings = aiRepository.state.value
        val providers = settings.providers
            .filter { it.enabled && it.apiKeyConfigured }
            .sortedBy { if (it.provider == settings.primaryProvider) 0 else 1 }
        if (providers.isEmpty()) return AiCallResult(false, error = "请先配置并启用一个主控模型")

        knowledgeRepository.markLearning(itemId)
        val prompt = ReplyPrompt(
            instructions = "你是商家知识整理助手。只从原文提取可核实事实，不补充常识，不创造价格、功效或承诺。",
            input = """
                请把以下资料整理成供客服智能体检索的知识摘要：
                1. 保留门店、项目、价格、时间、预约、售后、禁忌和合规边界等明确事实。
                2. 有冲突或不确定内容时标记“待商家确认”。
                3. 使用简短分点，不超过800字。

                标题：${item.title}
                原文：${item.content.take(12_000)}
            """.trimIndent(),
        )
        val errors = mutableListOf<String>()
        providers.forEach { config ->
            val apiKey = aiRepository.getProviderApiKey(config.provider).orEmpty()
            val route = aiRepository.resolveRoute(config)
            var result = gateway.generate(
                config = config,
                apiKey = apiKey,
                prompt = prompt,
                maxOutputTokens = 1_200,
                route = route,
                connectTimeoutSeconds = settings.networkRoute.connectTimeoutSeconds,
            )
            if (!result.success && route.viaGateway && settings.networkRoute.allowDirectFallback) {
                result = gateway.generate(
                    config = config,
                    apiKey = apiKey,
                    prompt = prompt,
                    maxOutputTokens = 1_200,
                    route = ResolvedAiRoute(config.baseUrl, false),
                    connectTimeoutSeconds = settings.networkRoute.connectTimeoutSeconds,
                )
            }
            if (result.success) {
                knowledgeRepository.saveLearningResult(
                    itemId,
                    result.text,
                    knowledgeRepository.state.value.settings.requireReviewAfterLearning,
                )
                return result.copy(provider = config.provider)
            }
            errors += "${config.provider.displayName}: ${result.error}"
        }
        val error = errors.joinToString("；")
        knowledgeRepository.saveLearningFailure(itemId, error)
        return AiCallResult(false, error = error)
    }
}
