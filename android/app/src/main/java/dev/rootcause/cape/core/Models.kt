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
    val writeSettings: Boolean
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
    val blockedByPermission: List<String>,
    val explanation: String,
    val commutePlan: CommutePlan? = null,
    val reasoningNote: String? = null
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
