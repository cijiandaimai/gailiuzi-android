package com.gailiuzi.app.core

import com.gailiuzi.app.model.AutomationLevel
import com.gailiuzi.app.model.AutomationMode
import com.gailiuzi.app.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertTrue("XHS_COMMUNITY_MANUAL_SEND_ONLY" in decision.reasonCodes)
    }

    @Test
    fun `kuaishou community interaction requires manual send even with official capability`() {
        val decision = InteractionPolicy.decide(
            baseRequest.copy(
                platform = Platform.KUAISHOU,
                mode = AutomationMode.FRONT_DESK,
                officialWriteCapability = true,
            ),
        )

        assertEquals(InteractionDecision.DRAFT_FOR_APPROVAL, decision.decision)
        assertTrue("KUAISHOU_COMMUNITY_MANUAL_SEND_ONLY" in decision.reasonCodes)
        assertTrue("KUAISHOU_NO_CONFIRMED_COMMENT_WRITE_API" in decision.reasonCodes)
    }

    @Test
    fun `missing context cannot produce a write action`() {
        val decision = InteractionPolicy.decide(
            baseRequest.copy(hasRequiredContext = false),
        )
        assertEquals(InteractionDecision.SAVE_AS_OPPORTUNITY, decision.decision)
    }

    @Test
    fun `douyin ui fallback always requires manual confirmation`() {
        val decision = InteractionPolicy.decide(
            baseRequest.copy(officialWriteCapability = false),
        )

        assertEquals(InteractionDecision.DRAFT_FOR_APPROVAL, decision.decision)
        assertTrue("DOUYIN_UI_MANUAL_CONFIRMATION_REQUIRED" in decision.reasonCodes)
    }

    @Test
    fun `douyin discovery interactions require approval even with official capability`() {
        val decision = InteractionPolicy.decide(baseRequest)

        assertEquals(InteractionDecision.DRAFT_FOR_APPROVAL, decision.decision)
        assertTrue("DOUYIN_DISCOVERY_APPROVAL_REQUIRED" in decision.reasonCodes)
    }

    @Test
    fun `owned low risk douyin comment may use approved official write capability`() {
        val decision = InteractionPolicy.decide(
            baseRequest.copy(mode = AutomationMode.FRONT_DESK),
        )

        assertEquals(InteractionDecision.ALLOW_DETERMINISTIC_ACTION, decision.decision)
    }

    @Test
    fun `official write without explicit approval remains a draft`() {
        val decision = InteractionPolicy.decide(
            baseRequest.copy(
                mode = AutomationMode.FRONT_DESK,
                humanApprovalRecorded = false,
            ),
        )

        assertEquals(InteractionDecision.DRAFT_FOR_APPROVAL, decision.decision)
        assertTrue("EXPLICIT_APPROVAL_REQUIRED" in decision.reasonCodes)
    }

    @Test
    fun `non owned context cannot become deterministic action`() {
        val decision = InteractionPolicy.decide(
            baseRequest.copy(
                mode = AutomationMode.FRONT_DESK,
                ownedAccountContext = false,
            ),
        )

        assertEquals(InteractionDecision.DRAFT_FOR_APPROVAL, decision.decision)
        assertTrue("NON_OWNED_CONTEXT_MANUAL_ONLY" in decision.reasonCodes)
    }

    private val baseRequest = InteractionRequest(
        platform = Platform.DOUYIN,
        mode = AutomationMode.SHOPPING,
        automationLevel = AutomationLevel.ASSISTED,
        prospectTier = ProspectTier.HIGH,
        riskLevel = RiskLevel.P3,
        topicRelevant = true,
        hasRequiredContext = true,
        ownedAccountContext = true,
        officialWriteCapability = true,
        humanApprovalRecorded = true,
    )
}
