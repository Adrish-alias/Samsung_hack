package dev.rootcause.cape.core

import kotlin.math.roundToInt

class StressScorer {
    fun score(context: ContextSnapshot): StressResult {
        val sleep = normalize(context.sleepDebtMinutes, 120)
        val meetings = normalize(context.meetingLoadToday, 8)
        val commute = normalize(context.commuteDelayMinutes, 45)
        val usage = normalize(context.screenTimeLast2hMinutes, 120)
        val score = ((0.4 * sleep) + (0.3 * meetings) + (0.2 * commute) + (0.1 * usage))
            .times(100)
            .roundToInt()

        val reasons = buildList {
            if (sleep >= 0.5) add("sleep_debt")
            if (meetings >= 0.5) add("heavy_meeting_load")
            if (commute >= 0.45) add("commute_pressure")
            if (usage >= 0.65) add("high_usage_intensity")
        }

        return StressResult(score = score, level = levelFor(score), reasons = reasons)
    }

    private fun normalize(value: Int, max: Int): Double {
        return (value.toDouble() / max.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun levelFor(score: Int): String {
        return when {
            score >= 75 -> "critical"
            score >= 60 -> "high"
            score >= 35 -> "medium"
            else -> "low"
        }
    }
}
