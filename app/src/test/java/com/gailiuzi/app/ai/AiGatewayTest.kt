package com.gailiuzi.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiGatewayTest {
    private val gateway = AiGateway()

    @Test
    fun `classifies authentication error as non retryable`() {
        val result = gateway.httpError(401, "invalid api key", viaGateway = false)

        assertEquals(AiErrorType.AUTHENTICATION, result.errorType)
        assertFalse(result.retryable)
    }

    @Test
    fun `classifies rate limit as retryable`() {
        val result = gateway.httpError(429, "too many requests", viaGateway = false)

        assertEquals(AiErrorType.RATE_LIMIT, result.errorType)
        assertTrue(result.retryable)
    }

    @Test
    fun `classifies exhausted quota separately`() {
        val result = gateway.httpError(429, "insufficient quota", viaGateway = false)

        assertEquals(AiErrorType.QUOTA_EXCEEDED, result.errorType)
        assertTrue(result.retryable)
    }

    @Test
    fun `uses bounded exponential backoff`() {
        assertEquals(500L, gateway.retryDelayMillis(1))
        assertEquals(1_000L, gateway.retryDelayMillis(2))
        assertEquals(8_000L, gateway.retryDelayMillis(10))
    }
}
