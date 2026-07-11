package com.shangbaobao.app.business

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplianceEngineTest {
    @Test
    fun `beauty medical claim requires approval`() {
        val result = ComplianceEngine.evaluate(
            text = "这个项目可以根治皮肤问题，而且永久有效。",
            vertical = ServiceVertical.BEAUTY,
            settings = ComplianceSettings(),
        )

        assertTrue(result.flags.any { it.contains("医疗功效") })
        assertTrue(result.requiresApproval)
    }

    @Test
    fun `verified neutral service reply passes`() {
        val result = ComplianceEngine.evaluate(
            text = "您好，具体项目与价格请以门店官方页面当前展示为准。",
            vertical = ServiceVertical.MASSAGE,
            settings = ComplianceSettings(),
        )

        assertTrue(result.flags.isEmpty())
        assertFalse(result.requiresApproval)
    }

    @Test
    fun `personal contact is flagged`() {
        val result = ComplianceEngine.evaluate(
            text = "请联系手机号13812345678，我们会安排处理。",
            vertical = ServiceVertical.HAIR,
            settings = ComplianceSettings(),
        )

        assertTrue(result.flags.any { it.contains("手机号") })
    }

    @Test
    fun `merchant forbidden term is respected`() {
        val result = ComplianceEngine.evaluate(
            text = "这是内部项目名称至尊针。",
            vertical = ServiceVertical.OTHER_SERVICE,
            settings = ComplianceSettings(customForbiddenTerms = listOf("至尊针")),
        )

        assertTrue(result.flags.any { it.contains("商家禁用词") })
    }
}
