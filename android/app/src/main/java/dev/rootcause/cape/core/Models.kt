package dev.rootcause.cape.core

data class ContextSnapshot(
    val locationState: String,
    val sleepDebtMinutes: Int,
    val meetingLoadToday: Int,
    val commuteDelayMinutes: Int,
    val screenTimeLast2hMinutes: Int,
    val nextMeetingMinutes: Int?,
    val nextMeetingLocation: String?,
    val currentLatitude: Double?,
    val currentLongitude: Double?,
    val notificationPolicyAccess: Boolean,
    val writeSettings: Boolean,
    val nextMeetingTitle: String? = null,
    val currentTimeIso: String? = null,
    val dayOfWeek: String? = null,
    val hourOfDay: Int? = null,
    val timezone: String? = null
)

data class StressResult(
    val score: Int,
    val level: String,
    val reasons: List<String>
)

data class CapeDecision(
    val type: String,
    val packId: String,
    val stress: StressResult,
    val actions: List<String>,
    val suggestedActions: List<String> = emptyList(),
    val blockedByPermission: List<String>,
    val explanation: String,
    val confidence: Double = 0.0,
    val commutePlan: CommutePlan? = null,
    val reasoningNote: String? = null,
    val safety: SafetyState? = null,
    val agentTrace: List<AgentTraceItem> = emptyList()
)

data class CommutePlan(
    val source: String,
    val etaMinutes: Int,
    val bufferMinutes: Int,
    val leaveInMinutes: Int,
    val leaveByLocal: String,
    val shouldAlert: Boolean,
    val reason: String
)

data class ContextCollectionResult(
    val snapshot: ContextSnapshot,
    val notes: List<String>
)

data class SafetyState(
    val status: String,
    val blockers: List<String> = emptyList(),
    val suggested: Boolean = false
)

data class AgentTraceItem(
    val agent: String,
    val status: String,
    val output: String
)

data class FeedbackAck(
    val message: String,
    val learned: List<String> = emptyList()
)
