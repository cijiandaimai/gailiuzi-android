package com.gailiuzi.app.core

import com.gailiuzi.app.model.AutomationLevel
import com.gailiuzi.app.model.AutomationMode
import com.gailiuzi.app.model.Platform

enum class RiskLevel {
    P3,
    P2,
    P1,
    P0,
}

enum class InteractionDecision {
    IGNORE,
    SAVE_AS_OPPORTUNITY,
    DRAFT_FOR_APPROVAL,
    ALLOW_DETERMINISTIC_ACTION,
    ESCALATE_INCIDENT,
}

data class InteractionRequest(
    val platform: Platform,
    val mode: AutomationMode,
    val automationLevel: AutomationLevel,
    val prospectTier: ProspectTier,
    val riskLevel: RiskLevel,
    val topicRelevant: Boolean,
    val hasRequiredContext: Boolean,
    val ownedAccountContext: Boolean,
    val officialWriteCapability: Boolean,
    val humanApprovalRecorded: Boolean,
)

data class PolicyDecision(
    val decision: InteractionDecision,
    val reasonCodes: Set<String>,
)

object InteractionPolicy {
    fun decide(request: InteractionRequest): PolicyDecision {
        if (request.riskLevel == RiskLevel.P0 || request.riskLevel == RiskLevel.P1) {
            return PolicyDecision(
                InteractionDecision.ESCALATE_INCIDENT,
                setOf("HIGH_RISK_CONTENT"),
            )
        }
        if (!request.topicRelevant) {
            return PolicyDecision(InteractionDecision.IGNORE, setOf("LOW_TOPIC_RELEVANCE"))
        }
        if (!request.hasRequiredContext) {
            return PolicyDecision(
                InteractionDecision.SAVE_AS_OPPORTUNITY,
                setOf("CONTEXT_INCOMPLETE"),
            )
        }
        if (request.prospectTier == ProspectTier.LOW) {
            return PolicyDecision(InteractionDecision.IGNORE, setOf("LOW_PROSPECT_SCORE"))
        }
        if (request.automationLevel == AutomationLevel.MONITOR) {
            return PolicyDecision(
                InteractionDecision.SAVE_AS_OPPORTUNITY,
                setOf("MONITOR_ONLY"),
            )
        }
        if (request.platform == Platform.XIAOHONGSHU) {
            return PolicyDecision(
                InteractionDecision.DRAFT_FOR_APPROVAL,
                setOf(
                    "XHS_COMMUNITY_MANUAL_SEND_ONLY",
                    "XHS_NO_CONFIRMED_OFFICIAL_COMMUNITY_WRITE_API",
                ),
            )
        }
        if (request.platform == Platform.KUAISHOU) {
            return PolicyDecision(
                InteractionDecision.DRAFT_FOR_APPROVAL,
                setOf(
                    "KUAISHOU_COMMUNITY_MANUAL_SEND_ONLY",
                    "KUAISHOU_NO_CONFIRMED_COMMENT_WRITE_API",
                ),
            )
        }
        if (request.platform == Platform.DOUYIN && !request.officialWriteCapability) {
            return PolicyDecision(
                InteractionDecision.DRAFT_FOR_APPROVAL,
                setOf("DOUYIN_UI_MANUAL_CONFIRMATION_REQUIRED"),
            )
        }
        if (request.platform == Platform.DOUYIN && request.mode != AutomationMode.FRONT_DESK) {
            return PolicyDecision(
                InteractionDecision.DRAFT_FOR_APPROVAL,
                setOf("DOUYIN_DISCOVERY_APPROVAL_REQUIRED"),
            )
        }
        if (!request.ownedAccountContext) {
            return PolicyDecision(
                InteractionDecision.DRAFT_FOR_APPROVAL,
                setOf("NON_OWNED_CONTEXT_MANUAL_ONLY"),
            )
        }
        if (!request.officialWriteCapability) {
            return PolicyDecision(
                InteractionDecision.DRAFT_FOR_APPROVAL,
                setOf("HUMAN_CONFIRMATION_REQUIRED"),
            )
        }
        if (request.automationLevel == AutomationLevel.ASSISTED &&
            request.mode == AutomationMode.FRONT_DESK &&
            request.riskLevel == RiskLevel.P3 &&
            request.humanApprovalRecorded
        ) {
            return PolicyDecision(
                InteractionDecision.ALLOW_DETERMINISTIC_ACTION,
                setOf("OWNED_LOW_RISK_CONTEXT"),
            )
        }
        return PolicyDecision(
            InteractionDecision.DRAFT_FOR_APPROVAL,
            setOf(
                if (request.humanApprovalRecorded) {
                    "DEFAULT_APPROVAL_POLICY"
                } else {
                    "EXPLICIT_APPROVAL_REQUIRED"
                },
            ),
        )
    }
}
