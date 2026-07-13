package com.gailiuzi.app.core

import com.gailiuzi.app.model.AutomationMode
import com.gailiuzi.app.model.Platform

enum class OutboundAction {
    OBSERVE_CONTEXT,
    GENERATE_DRAFT,
    PUBLISH_REPLY,
    LIKE,
    FOLLOW,
    DIRECT_MESSAGE,
}

enum class ExecutionRoute {
    OFFICIAL_API,
    ACCESSIBILITY_UI,
    HUMAN_HANDOFF,
}

enum class PlatformSafetySignal {
    LOGIN_REQUIRED,
    CAPTCHA_OR_VERIFICATION,
    ACCOUNT_RISK_PROMPT,
    PLATFORM_RATE_LIMIT,
    AUTH_SCOPE_MISSING,
    UNKNOWN_PAGE,
    CLIENT_VERSION_UNVALIDATED,
    DUPLICATE_CONTENT,
    MERCHANT_BUDGET_EXHAUSTED,
}

enum class ActionSafetyDecision {
    ALLOW_OBSERVE,
    ALLOW_DRAFT,
    ALLOW_APPROVED_OFFICIAL_API,
    REQUIRE_HUMAN_HANDOFF,
    PAUSE_PLATFORM,
    BLOCK,
}

data class PlatformActionRequest(
    val platform: Platform,
    val mode: AutomationMode,
    val action: OutboundAction,
    val route: ExecutionRoute,
    val ownedAccountContext: Boolean,
    val officialCapabilityVerified: Boolean,
    val humanApprovalRecorded: Boolean,
    val topicRelevant: Boolean,
    val hasCompleteContext: Boolean,
    val signals: Set<PlatformSafetySignal> = emptySet(),
)

data class ActionSafetyResult(
    val decision: ActionSafetyDecision,
    val reasonCodes: Set<String>,
)

/**
 * Final outbound-action gate. It reduces accidental account violations through least privilege,
 * official interfaces and explicit approval; it never implements stealth or risk-control evasion.
 */
object PlatformActionSafetyPolicy {
    private val pauseSignals = setOf(
        PlatformSafetySignal.LOGIN_REQUIRED,
        PlatformSafetySignal.CAPTCHA_OR_VERIFICATION,
        PlatformSafetySignal.ACCOUNT_RISK_PROMPT,
        PlatformSafetySignal.PLATFORM_RATE_LIMIT,
    )

    fun decide(request: PlatformActionRequest): ActionSafetyResult {
        if (request.signals.any { it in pauseSignals }) {
            return ActionSafetyResult(
                ActionSafetyDecision.PAUSE_PLATFORM,
                setOf("PLATFORM_OR_ACCOUNT_SAFETY_SIGNAL"),
            )
        }
        if (request.action == OutboundAction.OBSERVE_CONTEXT) {
            return if (request.signals.any {
                    it == PlatformSafetySignal.UNKNOWN_PAGE ||
                        it == PlatformSafetySignal.CLIENT_VERSION_UNVALIDATED
                }
            ) {
                ActionSafetyResult(ActionSafetyDecision.BLOCK, setOf("UNKNOWN_PAGE_READ_DISABLED"))
            } else {
                ActionSafetyResult(ActionSafetyDecision.ALLOW_OBSERVE, setOf("READ_ONLY_CONTEXT"))
            }
        }
        if (!request.topicRelevant || !request.hasCompleteContext) {
            return ActionSafetyResult(
                ActionSafetyDecision.BLOCK,
                setOf("IRRELEVANT_OR_INCOMPLETE_CONTEXT"),
            )
        }
        if (request.action == OutboundAction.GENERATE_DRAFT) {
            return ActionSafetyResult(ActionSafetyDecision.ALLOW_DRAFT, setOf("DRAFT_ONLY"))
        }
        if (request.action in setOf(
                OutboundAction.LIKE,
                OutboundAction.FOLLOW,
                OutboundAction.DIRECT_MESSAGE,
            )
        ) {
            return ActionSafetyResult(
                ActionSafetyDecision.BLOCK,
                setOf("UNATTENDED_SOCIAL_ACTION_DISABLED"),
            )
        }
        if (PlatformSafetySignal.UNKNOWN_PAGE in request.signals ||
            PlatformSafetySignal.CLIENT_VERSION_UNVALIDATED in request.signals ||
            PlatformSafetySignal.DUPLICATE_CONTENT in request.signals ||
            PlatformSafetySignal.MERCHANT_BUDGET_EXHAUSTED in request.signals
        ) {
            return ActionSafetyResult(
                ActionSafetyDecision.REQUIRE_HUMAN_HANDOFF,
                setOf("WRITE_SAFETY_REVIEW_REQUIRED"),
            )
        }
        if (request.route == ExecutionRoute.ACCESSIBILITY_UI) {
            return ActionSafetyResult(
                ActionSafetyDecision.REQUIRE_HUMAN_HANDOFF,
                setOf("NO_ACCESSIBILITY_UI_PUBLISH"),
            )
        }
        if (request.route == ExecutionRoute.HUMAN_HANDOFF) {
            return ActionSafetyResult(
                ActionSafetyDecision.REQUIRE_HUMAN_HANDOFF,
                setOf("HUMAN_EXECUTION_REQUIRED"),
            )
        }
        if (request.platform in setOf(Platform.KUAISHOU, Platform.XIAOHONGSHU)) {
            return ActionSafetyResult(
                ActionSafetyDecision.REQUIRE_HUMAN_HANDOFF,
                setOf("PLATFORM_COMMUNITY_WRITE_NOT_CONFIRMED"),
            )
        }
        if (request.mode != AutomationMode.FRONT_DESK || !request.ownedAccountContext) {
            return ActionSafetyResult(
                ActionSafetyDecision.REQUIRE_HUMAN_HANDOFF,
                setOf("DISCOVERY_OR_NON_OWNED_WRITE_DISABLED"),
            )
        }
        if (!request.officialCapabilityVerified ||
            PlatformSafetySignal.AUTH_SCOPE_MISSING in request.signals
        ) {
            return ActionSafetyResult(
                ActionSafetyDecision.BLOCK,
                setOf("OFFICIAL_WRITE_CAPABILITY_NOT_VERIFIED"),
            )
        }
        if (!request.humanApprovalRecorded) {
            return ActionSafetyResult(
                ActionSafetyDecision.REQUIRE_HUMAN_HANDOFF,
                setOf("EXPLICIT_APPROVAL_REQUIRED"),
            )
        }
        return ActionSafetyResult(
            ActionSafetyDecision.ALLOW_APPROVED_OFFICIAL_API,
            setOf("OWNED_APPROVED_OFFICIAL_API_WRITE"),
        )
    }
}

object GenericPlatformRiskDetector {
    private val riskActivityNames = setOf(
        "LoginActivity",
        "VerifyActivity",
        "CaptchaActivity",
        "RiskControlActivity",
        "AccountRiskActivity",
        "BannedActivity",
    )

    fun isRiskPage(className: String?): Boolean =
        className.orEmpty().substringAfterLast('.') in riskActivityNames
}

data class ActionBudgetSnapshot(
    val approvedHourlyLimit: Int,
    val approvedDailyLimit: Int,
    val usedThisHour: Int,
    val usedToday: Int,
) {
    init {
        require(approvedHourlyLimit > 0)
        require(approvedDailyLimit > 0)
        require(usedThisHour >= 0)
        require(usedToday >= 0)
    }
}

object ActionBudgetEvaluator {
    fun safetySignal(snapshot: ActionBudgetSnapshot): PlatformSafetySignal? =
        PlatformSafetySignal.MERCHANT_BUDGET_EXHAUSTED.takeIf {
            snapshot.usedThisHour >= snapshot.approvedHourlyLimit ||
                snapshot.usedToday >= snapshot.approvedDailyLimit
        }
}

object DuplicateContentGuard {
    fun isDuplicate(
        candidate: String,
        recentApprovedReplies: List<String>,
        similarityThreshold: Double = 0.86,
    ): Boolean {
        require(similarityThreshold in 0.0..1.0)
        val normalizedCandidate = normalize(candidate)
        if (normalizedCandidate.isBlank()) return true
        return recentApprovedReplies.any { previous ->
            val normalizedPrevious = normalize(previous)
            normalizedPrevious == normalizedCandidate ||
                jaccardBigrams(normalizedCandidate, normalizedPrevious) >= similarityThreshold
        }
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .filter { it.isLetterOrDigit() }

    private fun jaccardBigrams(left: String, right: String): Double {
        val leftTokens = left.bigrams()
        val rightTokens = right.bigrams()
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0.0
        val union = leftTokens union rightTokens
        return (leftTokens intersect rightTokens).size.toDouble() / union.size
    }

    private fun String.bigrams(): Set<String> =
        if (length < 2) setOf(this).filter(String::isNotBlank).toSet()
        else windowed(size = 2, step = 1).toSet()
}
