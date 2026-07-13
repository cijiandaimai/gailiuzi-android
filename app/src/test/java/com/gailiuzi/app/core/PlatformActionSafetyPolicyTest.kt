package com.gailiuzi.app.core

import com.gailiuzi.app.model.AutomationMode
import com.gailiuzi.app.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformActionSafetyPolicyTest {
    @Test
    fun `account risk signal pauses all platform actions`() {
        val result = PlatformActionSafetyPolicy.decide(
            baseRequest.copy(signals = setOf(PlatformSafetySignal.ACCOUNT_RISK_PROMPT)),
        )

        assertEquals(ActionSafetyDecision.PAUSE_PLATFORM, result.decision)
    }

    @Test
    fun `accessibility route never publishes a reply`() {
        val result = PlatformActionSafetyPolicy.decide(
            baseRequest.copy(route = ExecutionRoute.ACCESSIBILITY_UI),
        )

        assertEquals(ActionSafetyDecision.REQUIRE_HUMAN_HANDOFF, result.decision)
        assertTrue("NO_ACCESSIBILITY_UI_PUBLISH" in result.reasonCodes)
    }

    @Test
    fun `unattended likes follows and messages are blocked`() {
        listOf(OutboundAction.LIKE, OutboundAction.FOLLOW, OutboundAction.DIRECT_MESSAGE).forEach { action ->
            val result = PlatformActionSafetyPolicy.decide(baseRequest.copy(action = action))
            assertEquals(ActionSafetyDecision.BLOCK, result.decision)
        }
    }

    @Test
    fun `shopping and public relations writes require human handoff`() {
        listOf(AutomationMode.SHOPPING, AutomationMode.PUBLIC_RELATIONS).forEach { mode ->
            val result = PlatformActionSafetyPolicy.decide(baseRequest.copy(mode = mode))
            assertEquals(ActionSafetyDecision.REQUIRE_HUMAN_HANDOFF, result.decision)
        }
    }

    @Test
    fun `only owned approved verified official api write is allowed`() {
        val result = PlatformActionSafetyPolicy.decide(baseRequest)

        assertEquals(ActionSafetyDecision.ALLOW_APPROVED_OFFICIAL_API, result.decision)
    }

    @Test
    fun `kuaishou and xiaohongshu community writes remain manual`() {
        listOf(Platform.KUAISHOU, Platform.XIAOHONGSHU).forEach { platform ->
            val result = PlatformActionSafetyPolicy.decide(baseRequest.copy(platform = platform))
            assertEquals(ActionSafetyDecision.REQUIRE_HUMAN_HANDOFF, result.decision)
        }
    }

    @Test
    fun `generic detector only matches explicit safety activity names`() {
        assertTrue(GenericPlatformRiskDetector.isRiskPage("com.example.account.CaptchaActivity"))
        assertTrue(!GenericPlatformRiskDetector.isRiskPage("com.example.account.PostLoginActivity"))
        assertTrue(!GenericPlatformRiskDetector.isRiskPage("android.widget.FrameLayout"))
    }

    @Test
    fun `unvalidated client version prevents outbound write`() {
        val result = PlatformActionSafetyPolicy.decide(
            baseRequest.copy(signals = setOf(PlatformSafetySignal.CLIENT_VERSION_UNVALIDATED)),
        )

        assertEquals(ActionSafetyDecision.REQUIRE_HUMAN_HANDOFF, result.decision)
    }

    @Test
    fun `merchant action budget produces an exhausted signal at either limit`() {
        assertEquals(
            PlatformSafetySignal.MERCHANT_BUDGET_EXHAUSTED,
            ActionBudgetEvaluator.safetySignal(ActionBudgetSnapshot(5, 20, 5, 8)),
        )
        assertEquals(
            PlatformSafetySignal.MERCHANT_BUDGET_EXHAUSTED,
            ActionBudgetEvaluator.safetySignal(ActionBudgetSnapshot(5, 20, 2, 20)),
        )
        assertEquals(null, ActionBudgetEvaluator.safetySignal(ActionBudgetSnapshot(5, 20, 2, 8)))
    }

    @Test
    fun `duplicate content guard catches normalized and highly similar replies`() {
        assertTrue(
            DuplicateContentGuard.isDuplicate(
                "感谢您的认可，期待再次见到您！",
                listOf("感谢您的认可，期待再次见到您。"),
            ),
        )
        assertTrue(
            !DuplicateContentGuard.isDuplicate(
                "您好，可以通过平台内的官方门店入口预约。",
                listOf("感谢您的认可，期待再次见到您。"),
            ),
        )
    }

    private val baseRequest = PlatformActionRequest(
        platform = Platform.DOUYIN,
        mode = AutomationMode.FRONT_DESK,
        action = OutboundAction.PUBLISH_REPLY,
        route = ExecutionRoute.OFFICIAL_API,
        ownedAccountContext = true,
        officialCapabilityVerified = true,
        humanApprovalRecorded = true,
        topicRelevant = true,
        hasCompleteContext = true,
    )
}
