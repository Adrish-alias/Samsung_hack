package dev.rootcause.cape

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.rootcause.cape.core.ContextSnapshot
import dev.rootcause.cape.core.CapeDecision
import dev.rootcause.cape.core.DecisionOrchestrator
import dev.rootcause.cape.execution.PackExecutor
import dev.rootcause.cape.gateway.GatewayClient
import dev.rootcause.cape.sensing.ContextCollector

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        setContent { CapeApp() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CapeApp(
                onRequestRuntimePermissions = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    )
                }
            )
        }
    }
}

@Composable
fun CapeApp(onRequestRuntimePermissions: () -> Unit = {}) {
    val context = LocalContext.current
    val collector = remember { ContextCollector(context.applicationContext) }
    var collection by remember { mutableStateOf(collector.collect()) }
    var selectedScenario by remember { mutableStateOf(ScenarioPreset.Live) }
    var decision by remember { mutableStateOf(DecisionOrchestrator().decide(applyScenario(collection.snapshot, selectedScenario))) }
    var feedback by remember { mutableStateOf("No feedback yet") }
    var applyStatus by remember { mutableStateOf("Pack not applied yet") }
    var gatewayStatus by remember { mutableStateOf("Gateway not called yet") }
    var feedbackNote by remember { mutableStateOf("Don't do this during 1:1s on Fridays") }
    var isLoading by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> collector.recordScreenOff()
                    Intent.ACTION_SCREEN_ON -> collector.recordScreenOn()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF7FAF9)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Header()
                ScenarioPanel(
                    selected = selectedScenario,
                    onSelect = { scenario ->
                        selectedScenario = scenario
                        decision = DecisionOrchestrator().decide(applyScenario(collection.snapshot, scenario))
                        gatewayStatus = if (scenario == ScenarioPreset.Live) {
                            "Using live collected context"
                        } else {
                            "Using ${scenario.label} demo scenario"
                        }
                    }
                )
                PermissionPanel(
                    permissions = readPermissionState(context),
                    onRequestRuntimePermissions = onRequestRuntimePermissions,
                    onOpenDnd = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                    onOpenWriteSettings = {
                        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                        intent.data = Uri.parse("package:${context.packageName}")
                        context.startActivity(intent)
                    },
                    onOpenUsageAccess = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                )
                ControlPanel(
                    gatewayStatus = gatewayStatus,
                    isLoading = isLoading,
                    onRefreshLocal = {
                        collection = collector.collect()
                        val effective = applyScenario(collection.snapshot, selectedScenario)
                        decision = DecisionOrchestrator().decide(effective)
                        gatewayStatus = if (selectedScenario == ScenarioPreset.Live) {
                            "Local decision refreshed"
                        } else {
                            "Local decision refreshed with ${selectedScenario.label} scenario"
                        }
                    },
                    onAskGateway = {
                        isLoading = true
                        gatewayStatus = "Calling CAPE gateway..."
                        Thread {
                            val fresh = collector.collect()
                            val effective = applyScenario(fresh.snapshot, selectedScenario)
                            val result = runCatching { GatewayClient().requestDecision(effective) }
                            (context as? ComponentActivity)?.runOnUiThread {
                                collection = fresh
                                decision = result.getOrElse {
                                    gatewayStatus = "Gateway failed: ${it.message}. Local fallback used."
                                    DecisionOrchestrator().decide(effective)
                                }
                                if (result.isSuccess) gatewayStatus = "Decision returned by CAPE gateway/OpenClaw bridge"
                                isLoading = false
                            }
                        }.start()
                    }
                )
                DecisionPanel(
                    decision = decision,
                    applyStatus = applyStatus,
                    onApply = {
                        val executable = if (decision.type == "SUGGEST_PACK" && decision.suggestedActions.isNotEmpty()) {
                            decision.copy(type = "APPLY_PACK", actions = decision.suggestedActions)
                        } else {
                            decision
                        }
                        applyStatus = PackExecutor(context).apply(executable)
                    }
                )
                ContextPanel(applyScenario(collection.snapshot, selectedScenario), collection.notes, selectedScenario)
                FeedbackPanel(
                    feedback = feedback,
                    note = feedbackNote,
                    onNoteChange = { feedbackNote = it }
                ) { signal, note ->
                    feedback = "$signal: sending feedback..."
                    Thread {
                        val result = runCatching {
                            GatewayClient().sendFeedback(decision.packId, signal, note)
                        }
                        (context as? ComponentActivity)?.runOnUiThread {
                            feedback = result.fold(
                                onSuccess = {
                                    buildString {
                                        append("$signal: ${it.message}")
                                        if (it.learned.isNotEmpty()) {
                                            append(" | learned: ${it.learned.joinToString()}")
                                        }
                                    }
                                },
                                onFailure = { "$signal: local only (${it.message})" }
                            )
                        }
                    }.start()
                }
            }
        }
    }
}

@Composable
private fun ScenarioPanel(selected: ScenarioPreset, onSelect: (ScenarioPreset) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Demo Scenarios", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ScenarioPreset.values().forEach { scenario ->
                    FilterChip(
                        selected = selected == scenario,
                        onClick = { onSelect(scenario) },
                        label = { Text(scenario.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("CAPE", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Live context collection + OpenClaw decision bridge", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PermissionPanel(
    permissions: PermissionState,
    onRequestRuntimePermissions: () -> Unit,
    onOpenDnd: () -> Unit,
    onOpenWriteSettings: () -> Unit,
    onOpenUsageAccess: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            PermissionRow("Location", permissions.location)
            PermissionRow("Calendar", permissions.calendar)
            PermissionRow("Notifications", permissions.notifications)
            PermissionRow("DND Access", permissions.notificationPolicyAccess)
            PermissionRow("Write Settings", permissions.writeSettings)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onRequestRuntimePermissions) { Text("Request") }
                TextButton(onClick = onOpenDnd) { Text("DND") }
                TextButton(onClick = onOpenWriteSettings) { Text("Settings") }
                TextButton(onClick = onOpenUsageAccess) { Text("Usage") }
            }
        }
    }
}

@Composable
private fun ControlPanel(
    gatewayStatus: String,
    isLoading: Boolean,
    onRefreshLocal: () -> Unit,
    onAskGateway: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Context Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onRefreshLocal, enabled = !isLoading) { Text("Refresh") }
                Button(onClick = onAskGateway, enabled = !isLoading) { Text("Ask Gateway") }
            }
            Text(gatewayStatus)
        }
    }
}

@Composable
private fun DecisionPanel(decision: CapeDecision, applyStatus: String, onApply: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Decision", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                StatusPill(decision.type)
            }
            MetricRow("Confidence", String.format("%.2f", decision.confidence))
            MetricRow("Stress", "${decision.stress.score}/100 ${decision.stress.level}")
            MetricRow("Pack", decision.packId)
            if (decision.actions.isNotEmpty()) {
                Text("Actions", style = MaterialTheme.typography.labelLarge)
                decision.actions.forEach { ActionItem(it) }
            }
            if (decision.suggestedActions.isNotEmpty()) {
                Text("Suggested actions", style = MaterialTheme.typography.labelLarge)
                decision.suggestedActions.forEach { ActionItem(it) }
            }
            if (decision.blockedByPermission.isNotEmpty()) {
                Text("Blocked by ${decision.blockedByPermission.joinToString()}", color = Color(0xFFB42318))
            }
            decision.safety?.let { safety ->
                MetricRow("Safety", safety.status)
                if (safety.blockers.isNotEmpty()) {
                    Text(safety.blockers.joinToString(), color = Color(0xFFB42318))
                }
            }
            decision.commutePlan?.let { plan ->
                Text("Commute", style = MaterialTheme.typography.labelLarge)
                MetricRow("Leave by", plan.leaveByLocal)
                MetricRow("ETA + buffer", "${plan.etaMinutes} + ${plan.bufferMinutes} min")
                Text(plan.reason, style = MaterialTheme.typography.bodySmall)
            }
            decision.reasoningNote?.let { note ->
                Text("Ollama Reasoning", style = MaterialTheme.typography.labelLarge)
                Text(note, style = MaterialTheme.typography.bodyMedium)
            }
            if (decision.agentTrace.isNotEmpty()) {
                Text("Agent Trace", style = MaterialTheme.typography.labelLarge)
                decision.agentTrace.forEach { item ->
                    Text("${item.agent}: ${item.status} - ${item.output}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(decision.explanation, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onApply, enabled = decision.type == "APPLY_PACK" || decision.type == "SUGGEST_PACK") {
                Text(if (decision.type == "SUGGEST_PACK") "Apply Suggestion" else "Apply Pack")
            }
            Text(applyStatus, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ContextPanel(snapshot: ContextSnapshot, notes: List<String>, scenario: ScenarioPreset) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Live Context Snapshot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            MetricRow("Scenario", scenario.label)
            MetricRow("Location", snapshot.locationState)
            MetricRow("Sleep debt", "${snapshot.sleepDebtMinutes} min")
            MetricRow("Meetings today", snapshot.meetingLoadToday.toString())
            MetricRow("Commute delay", "${snapshot.commuteDelayMinutes} min")
            MetricRow("Screen time 2h", "${snapshot.screenTimeLast2hMinutes} min")
            MetricRow("Next meeting", snapshot.nextMeetingMinutes?.let { "$it min" } ?: "none")
            MetricRow("Meeting title", snapshot.nextMeetingTitle ?: "none")
            MetricRow("Destination", snapshot.nextMeetingLocation ?: "none")
            MetricRow("Day / hour", listOfNotNull(snapshot.dayOfWeek, snapshot.hourOfDay?.toString()).joinToString(" / ").ifBlank { "unknown" })
            MetricRow(
                "Coordinates",
                if (snapshot.currentLatitude != null && snapshot.currentLongitude != null) {
                    "%.4f, %.4f".format(snapshot.currentLatitude, snapshot.currentLongitude)
                } else {
                    "none"
                }
            )
            if (notes.isNotEmpty()) {
                Text("Signals", style = MaterialTheme.typography.labelLarge)
                notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun FeedbackPanel(
    feedback: String,
    note: String,
    onNoteChange: (String) -> Unit,
    onFeedback: (String, String) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Feedback Loop", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(feedback)
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Override or feedback note") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onFeedback("accepted", note.ifBlank { "User marked the pack useful" }) }) { Text("Useful") }
                TextButton(onClick = { onFeedback("rejected", note.ifBlank { "User rejected or delayed the pack" }) }) { Text("Not now") }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        StatusPill(if (granted) "Granted" else "Missing")
    }
}

@Composable
private fun StatusPill(text: String) {
    val color = when (text) {
        "APPLY_PACK", "Granted" -> Color(0xFF0F766E)
        "SUGGEST_PACK" -> Color(0xFFB54708)
        "REQUEST_PERMISSION", "Missing" -> Color(0xFFB42318)
        else -> Color(0xFF475467)
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ActionItem(action: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Box(
            modifier = Modifier
                .height(10.dp)
                .width(10.dp)
                .background(Color(0xFF0F766E), RoundedCornerShape(100.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(action)
    }
}

private fun readPermissionState(context: Context): PermissionState {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return PermissionState(
        location = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        calendar = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED,
        notifications = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        notificationPolicyAccess = notificationManager.isNotificationPolicyAccessGranted,
        writeSettings = Settings.System.canWrite(context)
    )
}

private data class PermissionState(
    val location: Boolean,
    val calendar: Boolean,
    val notifications: Boolean,
    val notificationPolicyAccess: Boolean,
    val writeSettings: Boolean
)

private enum class ScenarioPreset(val label: String) {
    Live("Live"),
    Office("Office"),
    Commute("Commute"),
    Recovery("Recovery"),
    Home("Home")
}

private fun applyScenario(snapshot: ContextSnapshot, scenario: ScenarioPreset): ContextSnapshot {
    return when (scenario) {
        ScenarioPreset.Live -> snapshot
        ScenarioPreset.Office -> snapshot.copy(
            locationState = "office",
            sleepDebtMinutes = maxOf(snapshot.sleepDebtMinutes, 90),
            meetingLoadToday = maxOf(snapshot.meetingLoadToday, 6),
            commuteDelayMinutes = maxOf(snapshot.commuteDelayMinutes, 18),
            screenTimeLast2hMinutes = maxOf(snapshot.screenTimeLast2hMinutes, 70),
            nextMeetingMinutes = 25,
            nextMeetingLocation = snapshot.nextMeetingLocation ?: "Samsung Office",
            nextMeetingTitle = snapshot.nextMeetingTitle ?: "Sprint Review"
        )
        ScenarioPreset.Commute -> snapshot.copy(
            locationState = "commuting",
            sleepDebtMinutes = maxOf(snapshot.sleepDebtMinutes, 45),
            meetingLoadToday = maxOf(snapshot.meetingLoadToday, 3),
            commuteDelayMinutes = maxOf(snapshot.commuteDelayMinutes, 28),
            nextMeetingMinutes = 35,
            nextMeetingLocation = snapshot.nextMeetingLocation ?: "Client Meeting",
            nextMeetingTitle = snapshot.nextMeetingTitle ?: "Client Call"
        )
        ScenarioPreset.Recovery -> snapshot.copy(
            locationState = "home",
            sleepDebtMinutes = maxOf(snapshot.sleepDebtMinutes, 130),
            meetingLoadToday = maxOf(snapshot.meetingLoadToday, 4),
            commuteDelayMinutes = 0,
            screenTimeLast2hMinutes = maxOf(snapshot.screenTimeLast2hMinutes, 20),
            nextMeetingMinutes = snapshot.nextMeetingMinutes ?: 180,
            nextMeetingLocation = snapshot.nextMeetingLocation,
            nextMeetingTitle = snapshot.nextMeetingTitle ?: "Deep Work"
        )
        ScenarioPreset.Home -> snapshot.copy(
            locationState = "home",
            sleepDebtMinutes = minOf(snapshot.sleepDebtMinutes, 40),
            meetingLoadToday = minOf(snapshot.meetingLoadToday, 1),
            commuteDelayMinutes = 0,
            screenTimeLast2hMinutes = minOf(snapshot.screenTimeLast2hMinutes, 35),
            nextMeetingMinutes = null,
            nextMeetingLocation = null,
            nextMeetingTitle = null
        )
    }
}
