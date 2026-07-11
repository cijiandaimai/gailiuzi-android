package com.shangbaobao.app.business

data class ComplianceEvaluation(
    val flags: List<String>,
    val requiresApproval: Boolean,
)

object ComplianceEngine {
    private val medicalClaims = listOf(
        "根治", "治愈", "治疗", "药到病除", "无副作用", "绝对安全", "永久有效",
    )
    private val absoluteClaims = listOf(
        "100%", "百分百", "全网最低", "最便宜", "保证有效", "一定有效", "零风险",
    )
    private val personalDataPatterns = listOf(
        Regex("1[3-9]\\d{9}"),
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
    )

    fun evaluate(
        text: String,
        vertical: ServiceVertical,
        settings: ComplianceSettings,
    ): ComplianceEvaluation {
        val flags = buildList {
            if (settings.medicalClaimsGuard &&
                vertical in setOf(
                    ServiceVertical.BEAUTY,
                    ServiceVertical.MASSAGE,
                    ServiceVertical.SPA,
                    ServiceVertical.MEDICAL_BEAUTY,
                )
            ) {
                medicalClaims.firstOrNull(text::contains)?.let { add("医疗功效宣称：$it") }
            }
            if (settings.priceAccuracyGuard) {
                absoluteClaims.firstOrNull(text::contains)?.let { add("绝对化或价格承诺：$it") }
            }
            if (settings.personalDataGuard && personalDataPatterns.any { it.containsMatchIn(text) }) {
                add("可能包含手机号或邮箱")
            }
            settings.customForbiddenTerms.firstOrNull { it.isNotBlank() && text.contains(it) }
                ?.let { add("命中商家禁用词：$it") }
        }
        return ComplianceEvaluation(
            flags = flags,
            requiresApproval = flags.isNotEmpty() && settings.requireApprovalForRisk,
        )
    }

    fun promptGuardrails(vertical: ServiceVertical, settings: ComplianceSettings): String = buildString {
        appendLine("行业：${vertical.displayName}。")
        if (settings.medicalClaimsGuard) appendLine("不得宣称治疗、治愈、根治、永久有效或无副作用。")
        if (settings.priceAccuracyGuard) appendLine("不得编造价格、折扣、最低价或效果保证，以门店官方页面和核实信息为准。")
        if (settings.personalDataGuard) appendLine("公开回复不得包含客户手机号、邮箱、订单号等个人信息。")
        if (settings.negativeReviewEscalation) appendLine("负面评价先共情并引导到官方订单/客服通道，不公开争辩或泄露核查细节。")
        if (settings.customForbiddenTerms.isNotEmpty()) {
            appendLine("商家禁用词：${settings.customForbiddenTerms.joinToString("、")}。")
        }
    }.trim()
}
