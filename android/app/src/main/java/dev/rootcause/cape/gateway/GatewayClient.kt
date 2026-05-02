package dev.rootcause.cape.gateway

import dev.rootcause.cape.core.CapeDecision
import dev.rootcause.cape.core.CommutePlan
import dev.rootcause.cape.core.ContextSnapshot
import dev.rootcause.cape.core.StressResult
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

        return parseDecision(JSONObject(body).getJSONObject("decision"))
    }

    fun sendFeedback(packId: String, signal: String, note: String) {
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
        connection.disconnect()
        if (responseCode !in 200..299) error("Gateway HTTP $responseCode")
    }


    private fun ContextSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("locationState", locationState)
            .put("sleepDebtMinutes", sleepDebtMinutes)
            .put("meetingLoadToday", meetingLoadToday)
            .put("commuteDelayMinutes", commuteDelayMinutes)
            .put("screenTimeLast2hMinutes", screenTimeLast2hMinutes)
            .put("nextMeetingMinutes", nextMeetingMinutes)
            .put("nextMeetingLocation", nextMeetingLocation)
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

    private fun parseDecision(json: JSONObject): CapeDecision {
        val stressJson = json.getJSONObject("stress")
        val actionsJson = json.getJSONArray("actions")
        val blockedJson = json.getJSONArray("blockedByPermission")
        val reasonsJson = stressJson.getJSONArray("reasons")

        return CapeDecision(
            type = json.getString("type"),
            packId = json.getString("packId"),
            stress = StressResult(
                score = stressJson.getInt("score"),
                level = stressJson.getString("level"),
                reasons = List(reasonsJson.length()) { reasonsJson.getString(it) }
            ),
            actions = List(actionsJson.length()) { actionsJson.getString(it) },
            blockedByPermission = List(blockedJson.length()) { blockedJson.getString(it) },
            explanation = json.getString("explanation"),
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
            reasoningNote = json.optString("reasoningNote").takeIf { it.isNotBlank() && it != "null" }
        )
    }
}
