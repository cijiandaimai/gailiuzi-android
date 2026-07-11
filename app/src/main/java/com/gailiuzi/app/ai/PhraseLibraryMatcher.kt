package com.gailiuzi.app.ai

import com.gailiuzi.app.model.Platform

data class PhraseMatch(
    val phrase: PhraseTemplate,
    val score: Int,
)

object PhraseLibraryMatcher {
    fun select(
        input: String,
        phrases: List<PhraseTemplate>,
        platform: Platform? = null,
        intentHint: String? = null,
    ): PhraseMatch? {
        val normalizedInput = input.lowercase()
        return phrases.asSequence()
            .filter { it.enabled }
            .filter { it.platform == null || it.platform == platform }
            .map { phrase ->
                val keywordHits = phrase.keywords.count { keyword ->
                    keyword.isNotBlank() && normalizedInput.contains(keyword.lowercase())
                }
                val intentBonus = if (!intentHint.isNullOrBlank() &&
                    phrase.intent.equals(intentHint, ignoreCase = true)
                ) 120 else 0
                val platformBonus = if (phrase.platform != null && phrase.platform == platform) 15 else 0
                PhraseMatch(
                    phrase = phrase,
                    score = keywordHits * 30 + intentBonus + platformBonus + phrase.priority,
                )
            }
            .filter { it.score >= MINIMUM_SCORE }
            .maxWithOrNull(compareBy<PhraseMatch> { it.score }.thenBy { it.phrase.priority })
    }

    private const val MINIMUM_SCORE = 70
}

data class ReplyPrompt(
    val instructions: String,
    val input: String,
    val preferredPhraseId: String? = null,
)

object ReplyPromptComposer {
    fun compose(
        profile: AssistantProfile,
        phraseLibraryEnabled: Boolean,
        phrases: List<PhraseTemplate>,
        userContent: String,
        platform: Platform,
        intentHint: String? = null,
        context: String = "",
    ): ReplyPrompt {
        val match = if (phraseLibraryEnabled) {
            PhraseLibraryMatcher.select(userContent, phrases, platform, intentHint)
        } else {
            null
        }
        val instructions = buildString {
            if (profile.enabled) {
                appendLine("你是${profile.identityName}，身份是${profile.role}。")
                appendLine("性格要求：${profile.personality.instruction}。")
                appendLine("表达语气：${profile.tone}。")
            } else {
                appendLine("你是商家的评论回复助手，表达应专业、真实、简洁。")
            }
            appendLine("回复必须基于已提供信息，不编造价格、效果、调查结论或承诺。")
            appendLine("只输出可直接使用的中文回复，不解释生成过程。")
            if (platform == Platform.XIAOHONGSHU) {
                appendLine("小红书社区约束：不伪装普通消费者，不编造体验，不引导站外联系，不使用无关广告话术。")
                appendLine("草稿必须基于笔记、目标评论和父级对话上下文，最终发布由人工确认。")
            }
            if (profile.enabled && profile.customInstructions.isNotBlank()) {
                appendLine("补充要求：${profile.customInstructions.trim()}")
            }
            match?.let {
                appendLine("预置话术库已开启。若话术与问题匹配，必须优先以此话术为答案基础，只对称谓和上下文做必要调整：")
                appendLine("【${it.phrase.title}】${it.phrase.content}")
            }
        }
        val input = buildString {
            appendLine("平台：${platform.displayName}")
            if (!intentHint.isNullOrBlank()) appendLine("意图：$intentHint")
            if (context.isNotBlank()) appendLine("上下文：${context.trim()}")
            append("需要回复的内容：${userContent.trim()}")
        }
        return ReplyPrompt(
            instructions = instructions,
            input = input,
            preferredPhraseId = match?.phrase?.id,
        )
    }
}
