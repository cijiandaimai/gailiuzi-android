package com.shangbaobao.app.core

import kotlin.math.round

data class ProspectSignals(
    val explicitIntent: Double,
    val localFit: Double,
    val serviceFit: Double,
    val timeUrgency: Double,
    val conversationOpenness: Double,
    val publicSignalConsistency: Double,
    val brandSafety: Double,
    val penalty: Double = 0.0,
) {
    init {
        listOf(
            explicitIntent,
            localFit,
            serviceFit,
            timeUrgency,
            conversationOpenness,
            publicSignalConsistency,
            brandSafety,
            penalty,
        ).forEach { require(it in 0.0..1.0) { "Signal values must be between 0 and 1" } }
    }
}

enum class ProspectTier {
    HIGH,
    MEDIUM,
    LOW,
}

data class ProspectScore(
    val value: Double,
    val tier: ProspectTier,
)

object ProspectScorer {
    fun score(signals: ProspectSignals): ProspectScore {
        val raw =
            0.28 * signals.explicitIntent +
                0.18 * signals.localFit +
                0.15 * signals.serviceFit +
                0.12 * signals.timeUrgency +
                0.10 * signals.conversationOpenness +
                0.07 * signals.publicSignalConsistency +
                0.10 * signals.brandSafety -
                signals.penalty
        val normalized = round(raw.coerceIn(0.0, 1.0) * 100) / 100
        val tier = when {
            normalized >= 0.75 -> ProspectTier.HIGH
            normalized >= 0.50 -> ProspectTier.MEDIUM
            else -> ProspectTier.LOW
        }
        return ProspectScore(normalized, tier)
    }
}

