package dev.rootcause.cape.core

import kotlin.math.roundToInt

class StressScorer {
    fun score(context: ContextSnapshot): StressResult {
        val sleep = normalize(context.sleepDebtMinutes, 120)
        val appSwitches = normalize(context.appSwitchCountLast30Min, 30)
        val unlocks = normalize(context.screenUnlockCountLast30Min, 12)
        val notifications = normalize(context.notificationCountLast30Min, 20)
        val commute = normalize(context.commuteDelayMinutes, 45)
        val usage = normalize(context.screenTimeLast2hMinutes, 120)
        val meetings = normalize(context.meetingLoadToday, 8)
        val workloadBoost = if (context.implicitWorkload == "HIGH") 10 else 0
        val mixedBoost = if (context.foregroundAppCategory == "mixed") 5 else 0
        val score = (((0.25 * sleep) +
            (0.20 * appSwitches) +
            (0.15 * unlocks) +
            (0.15 * usage) +
            (0.10 * notifications) +
            (0.10 * commute) +
            (0.05 * meetings))
            .times(100)
            .roundToInt() + workloadBoost + mixedBoost).coerceIn(0, 100)

        val reasons = buildList {
            if (sleep >= 0.5) add("sleep_debt")
            if (appSwitches >= 0.5) add("task_fragmentation")
            if (unlocks >= 0.5) add("focus_drops")
            if (notifications >= 0.5) add("notification_pressure")
            if (commute >= 0.45) add("commute_pressure")
            if (usage >= 0.65) add("high_usage_intensity")
            if (meetings >= 0.5) add("meeting_load")
            if (context.implicitWorkload == "HIGH") add("implicit_workload")
            if (context.foregroundAppCategory == "mixed") add("mixed_app_context")
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
