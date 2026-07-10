package com.shangbaobao.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProspectScorerTest {
    @Test
    fun `high intent local candidate is ranked high`() {
        val result = ProspectScorer.score(
            ProspectSignals(
                explicitIntent = 1.0,
                localFit = 1.0,
                serviceFit = 0.9,
                timeUrgency = 0.8,
                conversationOpenness = 0.9,
                publicSignalConsistency = 0.8,
                brandSafety = 1.0,
            ),
        )

        assertEquals(ProspectTier.HIGH, result.tier)
        assertTrue(result.value >= 0.75)
    }

    @Test
    fun `penalty lowers otherwise promising candidate`() {
        val result = ProspectScorer.score(
            ProspectSignals(
                explicitIntent = 0.9,
                localFit = 0.9,
                serviceFit = 0.9,
                timeUrgency = 0.7,
                conversationOpenness = 0.7,
                publicSignalConsistency = 0.7,
                brandSafety = 0.4,
                penalty = 0.5,
            ),
        )

        assertEquals(ProspectTier.LOW, result.tier)
    }
}

