package dev.rootcause.cape.core

class DecisionOrchestrator(
    private val stressScorer: StressScorer = StressScorer()
) {
    fun decide(context: ContextSnapshot): CapeDecision {
        val stress = stressScorer.score(context)
        val pack = selectPack(context, stress)
        val blocked = requiredPermissions(pack.actions).filter { permission ->
            when (permission) {
                "notificationPolicyAccess" -> !context.notificationPolicyAccess
                "writeSettings" -> !context.writeSettings
                "notifications" -> !context.notifications
                else -> false
            }
        }

        val shouldSuggest = blocked.isEmpty() && pack.actions.isNotEmpty() && pack.confidence < 0.78
        val applyActions = when {
            blocked.isNotEmpty() -> emptyList()
            shouldSuggest -> emptyList()
            else -> pack.actions
        }
        val suggestedActions = if (shouldSuggest) pack.actions else emptyList()

        return CapeDecision(
            type = when {
                blocked.isNotEmpty() -> "REQUEST_PERMISSION"
                shouldSuggest -> "SUGGEST_PACK"
                pack.actions.isNotEmpty() -> "APPLY_PACK"
                else -> "OBSERVE"
            },
            packId = pack.id,
            stress = stress,
            actions = applyActions,
            suggestedActions = suggestedActions,
            blockedByPermission = blocked,
            explanation = explanation(pack.id, stress, blocked),
            confidence = pack.confidence,
            commutePlan = null,
            reasoningNote = null,
            openclaw = null
        )
    }

    private fun selectPack(context: ContextSnapshot, stress: StressResult): Pack {
        if (context.locationState == "commuting") {
            return Pack("commute_alert", 0.92, listOf("DND_OFF", "RINGER_NORMAL", "BRIGHTNESS_65", "WALLPAPER_COMMUTE", "SEND_DEPARTURE_ALERT"))
        }

        if (context.locationState == "office" || context.locationState == "college" || (context.nextMeetingMinutes ?: Int.MAX_VALUE) <= 10) {
            return Pack("office_focus_high_stress", 0.90, listOf("DND_ON", "RINGER_VIBRATE", "BRIGHTNESS_40", "WALLPAPER_FOCUS"))
        }

        if (stress.score >= 60 && stress.reasons.contains("sleep_debt")) {
            return Pack("recovery_mode", 0.72, listOf("DND_OFF", "RINGER_VIBRATE", "SOFT_NOTIFICATIONS", "BREAK_REMINDER", "BRIGHTNESS_50", "WALLPAPER_RELAX"))
        }

        if (context.locationState == "home" || context.locationState == "relaxing") {
            return Pack("home_evening", 0.88, listOf("DND_OFF", "RINGER_NORMAL", "BRIGHTNESS_AUTO", "WALLPAPER_RELAX"))
        }

        if (context.appSwitchCountLast30Min >= 15) {
            return Pack("recovery_mode", 0.83, listOf("DND_OFF", "RINGER_VIBRATE", "SOFT_NOTIFICATIONS", "BREAK_REMINDER", "BRIGHTNESS_50", "WALLPAPER_RELAX"))
        }

        if (context.appSwitchCountLast30Min <= 3 && context.screenUnlockCountLast30Min <= 2 && context.screenTimeLast2hMinutes >= 45) {
            return Pack("office_focus_high_stress", 0.82, listOf("DND_ON", "BRIGHTNESS_50", "WALLPAPER_FOCUS"))
        }

        return Pack("observe_only", 0.65, emptyList())
    }

    private fun requiredPermissions(actions: List<String>): List<String> {
        return buildSet {
            if (actions.any { it == "DND_ON" || it == "DND_OFF" || it == "SOFT_NOTIFICATIONS" }) add("notificationPolicyAccess")
            if (actions.any { it.startsWith("BRIGHTNESS") }) add("writeSettings")
            if (actions.any { it == "SEND_DEPARTURE_ALERT" || it == "SOFT_NOTIFICATIONS" || it == "BREAK_REMINDER" }) add("notifications")
        }.toList()
    }

    private fun explanation(packId: String, stress: StressResult, blocked: List<String>): String {
        if (blocked.isNotEmpty()) {
            return "CAPE needs ${blocked.joinToString()} before applying $packId."
        }

        return when (packId) {
            "office_focus_high_stress" -> "Focus location detected with ${stress.level} stress (${stress.score}/100); CAPE should reduce interruptions."
            "commute_alert" -> "Commuting context detected; CAPE should apply commute mode and send route alerts."
            "recovery_mode" -> "Sleep debt is raising stress to ${stress.score}/100; CAPE should use low-intrusion recovery behavior."
            "home_evening" -> "Home or relaxing context detected; CAPE should restore calmer phone behavior."
            else -> "No confident automation needed; CAPE should continue observing."
        }
    }

    private data class Pack(
        val id: String,
        val confidence: Double,
        val actions: List<String>
    )
}
