package dev.rootcause.cape.gateway

import dev.rootcause.cape.core.AgentTraceItem
import dev.rootcause.cape.core.CapeDecision
import dev.rootcause.cape.core.CommutePlan
import dev.rootcause.cape.core.ContextSnapshot
import dev.rootcause.cape.core.FeedbackAck
import dev.rootcause.cape.core.SafetyState
import dev.rootcause.cape.core.StressResult
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
            connectTimeout = 3000
            readTimeout = 5000
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


    private fun ContextSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("source", "android-apk")
            .put("locationState", locationState)
            .put("sleepDebtMinutes", sleepDebtMinutes)
            .put("meetingLoadToday", meetingLoadToday)
            .put("commuteDelayMinutes", commuteDelayMinutes)
            .put("screenTimeLast2hMinutes", screenTimeLast2hMinutes)
            .put("nextMeetingMinutes", nextMeetingMinutes)
            .put("nextMeetingLocation", nextMeetingLocation)
            .put("nextMeetingTitle", nextMeetingTitle)
            .put("currentTimeIso", currentTimeIso)
            .put("dayOfWeek", dayOfWeek)
            .put("hourOfDay", hourOfDay)
            .put("timezone", timezone)
            .put(
                "currentLocation",
                if (currentLatitude != null && currentLongitude != null) {
                    JSONObject().put("lat", currentLatitude).put("lng", currentLongitude)
                } else {
                    null
                }
            )
            .put(
                "permissions",
                JSONObject()
                    .put("notificationPolicyAccess", notificationPolicyAccess)
                    .put("writeSettings", writeSettings)
            )
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
                    reason = plan.optString("reason")
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
            }
        )
    }

    private fun JSONArray.toStringList(): List<String> {
        return List(length()) { getString(it) }
    }
}
