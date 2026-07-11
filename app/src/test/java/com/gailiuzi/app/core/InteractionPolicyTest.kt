package com.gailiuzi.app.core

import com.gailiuzi.app.model.AutomationLevel
import com.gailiuzi.app.model.AutomationMode
import com.gailiuzi.app.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class InteractionPolicyTest {
    @Test
    fun `high risk content is always escalated`() {
        val decision = InteractionPolicy.decide(
            baseRequest.copy(riskLevel = RiskLevel.P1),
        )
        assertEquals(InteractionDecision.ESCALATE_INCIDENT, decision.decision)
    }

    @Test
    fun `xiaohongshu interaction requires approval`() {
        val decision = InteractionPolicy.decide(
            baseRequest.copy(platform = Platform.XIAOHONGSHU),
        )
        assertEquals(InteractionDecision.DRAFT_FOR_APPROVAL, decision.decision)
    }

    @Test
    fun `missing context cannot produce a write action`() {
        val decision = InteractionPolicy.decide(
            baseRequest.copy(hasRequiredContext = false),
        )
        assertEquals(InteractionDecision.SAVE_AS_OPPORTUNITY, decision.decision)
    }

    private val baseRequest = InteractionRequest(
        platform = Platform.DOUYIN,
        mode = AutomationMode.SHOPPING,
        automationLevel = AutomationLevel.ASSISTED,
        prospectTier = ProspectTier.HIGH,
        riskLevel = RiskLevel.P3,
        topicRelevant = true,
        hasRequiredContext = true,
        officialWriteCapability = true,
    )
}

