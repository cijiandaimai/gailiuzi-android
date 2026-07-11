package com.gailiuzi.app.knowledge

enum class KnowledgeSourceType(val displayName: String) {
    FILE("文件"),
    MANUAL("手工知识"),
    FAQ("问答"),
    CORRECTION("纠错样本"),
}

enum class KnowledgeStatus(val displayName: String) {
    INDEXED("已建立索引"),
    LEARNING("AI学习中"),
    READY("AI学习完成"),
    NEEDS_REVIEW("待人工复核"),
    FAILED("学习失败"),
}

data class KnowledgeItem(
    val id: String,
    val title: String,
    val content: String,
    val sourceType: KnowledgeSourceType,
    val sourceName: String = "",
    val scopeId: String? = null,
    val enabled: Boolean = true,
    val status: KnowledgeStatus = KnowledgeStatus.INDEXED,
    val aiSummary: String = "",
    val learningError: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

data class TrainingSample(
    val id: String,
    val question: String,
    val preferredAnswer: String,
    val scopeId: String? = null,
    val approved: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class KnowledgeSettings(
    val enabled: Boolean = true,
    val autoLearnWithAi: Boolean = true,
    val requireReviewAfterLearning: Boolean = true,
    val retrievalTopK: Int = 4,
)

data class KnowledgeState(
    val settings: KnowledgeSettings,
    val items: List<KnowledgeItem>,
    val trainingSamples: List<TrainingSample>,
)

data class KnowledgeSnippet(
    val itemId: String,
    val title: String,
    val content: String,
    val score: Int,
)
