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
                else -> false
            }
        }

        return CapeDecision(
            type = when {
                blocked.isNotEmpty() -> "REQUEST_PERMISSION"
                pack.actions.isNotEmpty() -> "APPLY_PACK"
                else -> "OBSERVE"
            },
            packId = pack.id,
            stress = stress,
            actions = if (blocked.isEmpty()) pack.actions else emptyList(),
            blockedByPermission = blocked,
            explanation = explanation(pack.id, stress, blocked),
            commutePlan = null,
            reasoningNote = null
        )
    }

    private fun selectPack(context: ContextSnapshot, stress: StressResult): Pack {
        if (context.locationState == "commuting" && (context.nextMeetingMinutes ?: Int.MAX_VALUE) <= 120) {
            return Pack("commute_alert", listOf("SEND_DEPARTURE_ALERT"))
        }

        if (context.locationState == "office" && stress.score >= 60) {
            return Pack("office_focus_high_stress", listOf("DND_ON", "RINGER_VIBRATE", "BRIGHTNESS_40"))
        }

        if (stress.score >= 60 && stress.reasons.contains("sleep_debt")) {
            return Pack("recovery_mode", listOf("SOFT_NOTIFICATIONS", "BREAK_REMINDER", "BRIGHTNESS_65"))
        }

        if (context.locationState == "home") {
            return Pack("home_evening", listOf("DND_OFF", "RINGER_NORMAL", "BRIGHTNESS_AUTO"))
        }

        return Pack("observe_only", emptyList())
    }

    private fun requiredPermissions(actions: List<String>): List<String> {
        return buildSet {
            if (actions.any { it == "DND_ON" || it == "SOFT_NOTIFICATIONS" }) add("notificationPolicyAccess")
            if (actions.any { it.startsWith("BRIGHTNESS") }) add("writeSettings")
        }.toList()
    }

    private fun explanation(packId: String, stress: StressResult, blocked: List<String>): String {
        if (blocked.isNotEmpty()) {
            return "CAPE needs ${blocked.joinToString()} before applying $packId."
        }

        return when (packId) {
            "office_focus_high_stress" -> "Office context with ${stress.level} stress (${stress.score}/100); CAPE should reduce interruptions."
            "commute_alert" -> "Upcoming meeting and commute pressure detected; CAPE should send a departure alert."
            "recovery_mode" -> "Sleep debt is raising stress to ${stress.score}/100; CAPE should use low-intrusion recovery behavior."
            "home_evening" -> "Home context detected; CAPE should restore normal phone behavior."
            else -> "No confident automation needed; CAPE should continue observing."
        }
    }

    private data class Pack(
        val id: String,
        val actions: List<String>
    )
}
