package dev.rootcause.cape.core

data class ContextSnapshot(
    val locationState: String,
    val sleepDebtMinutes: Int,
    val meetingLoadToday: Int,
    val commuteDelayMinutes: Int,
    val screenTimeLast2hMinutes: Int,
    val appSwitchCountLast30Min: Int = 0,
    val screenUnlockCountLast30Min: Int = 0,
    val notificationCountLast30Min: Int = 0,
    val foregroundAppCategory: String = "mixed",
    val timeAtLocationMinutes: Int = 0,
    val implicitWorkload: String = "LOW",
    val nextMeetingMinutes: Int?,
    val nextMeetingStartEpochMs: Long? = null,
    val nextMeetingLocation: String?,
    val currentLatitude: Double?,
    val currentLongitude: Double?,
    val currentSpeedMps: Float? = null,
    val notificationPolicyAccess: Boolean,
    val writeSettings: Boolean,
    val notifications: Boolean,
    val calendarPermission: Boolean = false,
    val locationPermission: Boolean = false,
    val usageStatsPermission: Boolean = false,
    val nextMeetingTitle: String? = null,
    val currentTimeIso: String? = null,
    val dayOfWeek: String? = null,
    val hourOfDay: Int? = null,
    val timezone: String? = null,
    val savedPlaces: List<SavedPlace> = emptyList(),
    val todoPendingCount: Int = 0,
    val todoUrgentCount: Int = 0,
    val todoOverdueCount: Int = 0,
    val todoPressureScore: Int = 0,
    val learnedTodoUpdateHours: List<Int> = emptyList()
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
    val agentTrace: List<AgentTraceItem> = emptyList(),
    val openclaw: OpenClawState? = null
)

data class CommutePlan(
    val source: String,
    val etaMinutes: Int,
    val bufferMinutes: Int,
    val leaveInMinutes: Int,
    val leaveByLocal: String,
    val shouldAlert: Boolean,
    val reason: String,
    val destination: String? = null,
    val modes: List<TravelModePlan> = emptyList(),
    val directions: List<RouteStep> = emptyList(),
    val mapsUrl: String? = null,
    val polyline: String? = null
)

data class TravelModePlan(
    val id: String,
    val label: String,
    val durationText: String,
    val distanceText: String,
    val leaveByLocal: String,
    val arrivalByLocal: String
)

data class RouteStep(
    val instruction: String,
    val distanceText: String,
    val durationText: String,
    val travelMode: String
)

data class SavedPlace(
    val kind: String,
    val label: String,
    val query: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int = 250
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

data class OpenClawState(
    val runtime: String,
    val orchestrator: String,
    val sessionId: String?,
    val fallbackReason: String?,
    val attemptedRemote: Boolean,
    val baseUrl: String?,
    val agentId: String?,
    val recommendedModel: String?
)

data class FeedbackAck(
    val message: String,
    val learned: List<String> = emptyList()
)
