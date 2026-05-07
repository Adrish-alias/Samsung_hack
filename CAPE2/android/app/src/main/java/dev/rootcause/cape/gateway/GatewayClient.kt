package dev.rootcause.cape.gateway

import dev.rootcause.cape.core.AgentTraceItem
import dev.rootcause.cape.core.CapeDecision
import dev.rootcause.cape.core.CommutePlan
import dev.rootcause.cape.core.ContextSnapshot
import dev.rootcause.cape.core.FeedbackAck
import dev.rootcause.cape.core.OpenClawState
import dev.rootcause.cape.core.RouteStep
import dev.rootcause.cape.core.SafetyState
import dev.rootcause.cape.core.SavedPlace
import dev.rootcause.cape.core.StressResult
import dev.rootcause.cape.core.TravelModePlan
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GatewayClient(private val baseUrl: String = "http://127.0.0.1:8787") {
    fun requestDecision(snapshot: ContextSnapshot): CapeDecision {
        val url = URL("$baseUrl/v1/context/decision")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 45000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(snapshot.toJson().toString())
        }

        val responseCode = connection.responseCode
        val body = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        connection.disconnect()

        if (responseCode !in 200..299) {
            error("Gateway HTTP $responseCode: $body")
        }

        return parseDecision(JSONObject(body))
    }

    fun sendFeedback(packId: String, signal: String, note: String): FeedbackAck {
        val url = URL("$baseUrl/v1/feedback")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        val payload = JSONObject()
            .put("packId", packId)
            .put("signal", signal)
            .put("note", note)
            .put("source", "android-apk")

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(payload.toString())
        }

        val responseCode = connection.responseCode
        val body = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        connection.disconnect()
        if (responseCode !in 200..299) error("Gateway HTTP $responseCode: $body")

        val json = JSONObject(body)
        val learned = json.optJSONObject("learning")
            ?.optJSONObject("updated")
            ?.optJSONArray("learned")
            ?.toStringList()
            .orEmpty()
        return FeedbackAck(
            message = json.optString("message", "feedback_recorded"),
            learned = learned
        )
    }

    fun sendDecisionApproval(packId: String, signal: String, note: String, actions: List<String>, confidence: Double): FeedbackAck {
        return sendFeedbackPayload(
            JSONObject()
                .put("type", "decision_approval")
                .put("packId", packId)
                .put("signal", signal)
                .put("note", note)
                .put("actions", JSONArray(actions))
                .put("confidence", confidence)
                .put("timestamp", java.time.OffsetDateTime.now().toString())
                .put("source", "android-apk")
        )
    }

    fun sendTodoUpdate(pending: Int, urgent: Int, overdue: Int, note: String, timestamp: String): FeedbackAck {
        return sendFeedbackPayload(
            JSONObject()
                .put("type", "todo_update")
                .put("packId", "day_todo")
                .put("signal", "neutral")
                .put("pending", pending)
                .put("urgent", urgent)
                .put("overdue", overdue)
                .put("note", note)
                .put("timestamp", timestamp)
                .put("source", "android-apk")
        )
    }

    fun sendDailyReflection(tags: List<String>, note: String, timestamp: String): FeedbackAck {
        return sendFeedbackPayload(
            JSONObject()
                .put("type", "daily_reflection")
                .put("tags", JSONArray(tags))
                .put("note", note)
                .put("timestamp", timestamp)
                .put("source", "android-apk")
        )
    }

    private fun sendFeedbackPayload(payload: JSONObject): FeedbackAck {
        val url = URL("$baseUrl/v1/feedback")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(payload.toString())
        }

        val responseCode = connection.responseCode
        val body = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        connection.disconnect()
        if (responseCode !in 200..299) error("Gateway HTTP $responseCode: $body")

        val json = JSONObject(body)
        val learned = json.optJSONObject("learning")
            ?.optJSONObject("updated")
            ?.optJSONArray("learned")
            ?.toStringList()
            .orEmpty()
        return FeedbackAck(
            message = json.optString("message", "feedback_recorded"),
            learned = learned
        )
    }


    private fun ContextSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("source", "android-apk")
            .put("locationState", locationState)
            .put("sleepDebtMinutes", sleepDebtMinutes)
            .put("meetingLoadToday", meetingLoadToday)
            .put("commuteDelayMinutes", commuteDelayMinutes)
            .put("screenTimeLast2hMinutes", screenTimeLast2hMinutes)
            .put("appSwitchCountLast30Min", appSwitchCountLast30Min)
            .put("screenUnlockCountLast30Min", screenUnlockCountLast30Min)
            .put("notificationCountLast30Min", notificationCountLast30Min)
            .put("foregroundAppCategory", foregroundAppCategory)
            .put("timeAtLocationMinutes", timeAtLocationMinutes)
            .put("implicitWorkload", implicitWorkload)
            .put("nextMeetingMinutes", nextMeetingMinutes)
            .put("nextMeetingStartEpochMs", nextMeetingStartEpochMs)
            .put("nextMeetingLocation", resolvedMeetingLocation())
            .put("nextMeetingTitle", nextMeetingTitle)
            .put("currentTimeIso", currentTimeIso)
            .put("dayOfWeek", dayOfWeek)
            .put("hourOfDay", hourOfDay)
            .put("timezone", timezone)
            .put("todoPendingCount", todoPendingCount)
            .put("todoUrgentCount", todoUrgentCount)
            .put("todoOverdueCount", todoOverdueCount)
            .put("todoPressureScore", todoPressureScore)
            .put("learnedTodoUpdateHours", JSONArray(learnedTodoUpdateHours))
            .put(
                "savedPlaces",
                JSONArray(savedPlaces.map { place ->
                    JSONObject()
                        .put("kind", place.kind)
                        .put("label", place.label)
                        .put("query", place.query)
                        .put("lat", place.latitude)
                        .put("lng", place.longitude)
                        .put("radiusMeters", place.radiusMeters)
                })
            )
            .put(
                "currentLocation",
                if (currentLatitude != null && currentLongitude != null) {
                    JSONObject()
                        .put("lat", currentLatitude)
                        .put("lng", currentLongitude)
                        .put("speedMps", currentSpeedMps)
                } else {
                    null
                }
            )
            .put(
                "permissions",
                JSONObject()
                    .put("notificationPolicyAccess", notificationPolicyAccess)
                    .put("writeSettings", writeSettings)
                    .put("notifications", notifications)
                    .put("calendar", calendarPermission)
                    .put("location", locationPermission)
                    .put("usageStats", usageStatsPermission)
            )
    }

    private fun ContextSnapshot.resolvedMeetingLocation(): String? {
        val raw = nextMeetingLocation?.takeIf { it.isNotBlank() }
        val match = savedPlaces.firstOrNull { place ->
            raw != null && (
                place.label.equals(raw, ignoreCase = true) ||
                    place.query.equals(raw, ignoreCase = true) ||
                    raw.contains(place.label, ignoreCase = true) ||
                    raw.contains(place.query, ignoreCase = true)
                )
        }
        if (match?.latitude != null && match.longitude != null) {
            return "${match.latitude},${match.longitude}"
        }
        return raw
    }

    private fun parseDecision(root: JSONObject): CapeDecision {
        val json = root.getJSONObject("decision")
        val stressJson = json.getJSONObject("stress")
        val actionsJson = json.getJSONArray("actions")
        val suggestedJson = json.optJSONArray("suggestedActions") ?: JSONArray()
        val blockedJson = json.getJSONArray("blockedByPermission")
        val reasonsJson = stressJson.getJSONArray("reasons")
        val traceJson = root.optJSONArray("agentTrace") ?: JSONArray()
        val safetyJson = json.optJSONObject("safety")
        val openclawJson = root.optJSONObject("openclaw")

        return CapeDecision(
            type = json.getString("type"),
            packId = json.getString("packId"),
            stress = StressResult(
                score = stressJson.getInt("score"),
                level = stressJson.getString("level"),
                reasons = List(reasonsJson.length()) { reasonsJson.getString(it) }
            ),
            actions = List(actionsJson.length()) { actionsJson.getString(it) },
            suggestedActions = List(suggestedJson.length()) { suggestedJson.getString(it) },
            blockedByPermission = List(blockedJson.length()) { blockedJson.getString(it) },
            explanation = json.getString("explanation"),
            confidence = json.optDouble("confidence", 0.0),
            commutePlan = json.optJSONObject("commutePlan")?.let { plan ->
                CommutePlan(
                    source = plan.optString("source"),
                    etaMinutes = plan.optInt("etaMinutes"),
                    bufferMinutes = plan.optInt("bufferMinutes"),
                    leaveInMinutes = plan.optInt("leaveInMinutes"),
                    leaveByLocal = plan.optString("leaveByLocal"),
                    shouldAlert = plan.optBoolean("shouldAlert"),
                    reason = plan.optString("reason"),
                    destination = plan.optString("destination").takeIf { it.isNotBlank() && it != "null" },
                    mapsUrl = plan.optString("mapsUrl").takeIf { it.isNotBlank() && it != "null" },
                    polyline = plan.optString("polyline").takeIf { it.isNotBlank() && it != "null" },
                    modes = plan.optJSONArray("modes")?.let { modes ->
                        List(modes.length()) { index ->
                            val item = modes.getJSONObject(index)
                            TravelModePlan(
                                id = item.optString("id"),
                                label = item.optString("label"),
                                durationText = item.optString("durationText"),
                                distanceText = item.optString("distanceText"),
                                leaveByLocal = item.optString("leaveByLocal"),
                                arrivalByLocal = item.optString("arrivalByLocal")
                            )
                        }
                    }.orEmpty(),
                    directions = plan.optJSONArray("directions")?.let { steps ->
                        List(steps.length()) { index ->
                            val item = steps.getJSONObject(index)
                            RouteStep(
                                instruction = item.optString("instruction"),
                                distanceText = item.optString("distanceText"),
                                durationText = item.optString("durationText"),
                                travelMode = item.optString("travelMode")
                            )
                        }
                    }.orEmpty()
                )
            },
            reasoningNote = json.optString("reasoningNote").takeIf { it.isNotBlank() && it != "null" },
            safety = safetyJson?.let {
                SafetyState(
                    status = it.optString("status"),
                    blockers = it.optJSONArray("blockers")?.toStringList().orEmpty(),
                    suggested = it.optBoolean("suggested")
                )
            },
            agentTrace = List(traceJson.length()) { index ->
                val item = traceJson.getJSONObject(index)
                AgentTraceItem(
                    agent = item.optString("agent"),
                    status = item.optString("status"),
                    output = item.optString("output")
                )
            },
            openclaw = openclawJson?.let {
                OpenClawState(
                    runtime = it.optString("runtime", "unknown"),
                    orchestrator = it.optString("orchestrator", "openclaw"),
                    sessionId = it.optString("sessionId").takeIf { value -> value.isNotBlank() && value != "null" },
                    fallbackReason = it.optString("fallbackReason").takeIf { value -> value.isNotBlank() && value != "null" },
                    attemptedRemote = it.optBoolean("attemptedRemote"),
                    baseUrl = it.optString("baseUrl").takeIf { value -> value.isNotBlank() && value != "null" },
                    agentId = it.optString("agentId").takeIf { value -> value.isNotBlank() && value != "null" },
                    recommendedModel = it.optString("recommendedModel").takeIf { value -> value.isNotBlank() && value != "null" }
                )
            }
        )
    }

    private fun JSONArray.toStringList(): List<String> {
        return List(length()) { getString(it) }
    }

    fun geocodePlace(query: String): SavedPlace {
        val url = URL("$baseUrl/v1/maps/geocode")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(JSONObject().put("query", query).toString())
        }
        val responseCode = connection.responseCode
        val body = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        connection.disconnect()
        if (responseCode !in 200..299) error("Gateway HTTP $responseCode: $body")
        val place = JSONObject(body).getJSONObject("place")
        return SavedPlace(
            kind = "",
            label = place.optString("label"),
            query = query,
            latitude = place.optDouble("lat"),
            longitude = place.optDouble("lng"),
            radiusMeters = 250
        )
    }

    fun searchPlaces(query: String): List<SavedPlace> {
        val url = URL("$baseUrl/v1/maps/search")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(JSONObject().put("query", query).toString())
        }
        val responseCode = connection.responseCode
        val body = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        connection.disconnect()
        if (responseCode !in 200..299) error("Gateway HTTP $responseCode: $body")
        val places = JSONObject(body).optJSONArray("places") ?: JSONArray()
        return List(places.length()) { index ->
            val place = places.getJSONObject(index)
            SavedPlace(
                kind = "",
                label = place.optString("label"),
                query = query,
                latitude = place.optDouble("lat"),
                longitude = place.optDouble("lng"),
                radiusMeters = 250
            )
        }
    }
}
