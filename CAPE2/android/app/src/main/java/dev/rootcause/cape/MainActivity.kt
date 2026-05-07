package dev.rootcause.cape

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.provider.CalendarContract
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import dev.rootcause.cape.core.CapeDecision
import dev.rootcause.cape.core.ContextCollectionResult
import dev.rootcause.cape.core.ContextSnapshot
import dev.rootcause.cape.core.DecisionOrchestrator
import dev.rootcause.cape.core.SavedPlace
import dev.rootcause.cape.execution.PackExecutor
import dev.rootcause.cape.gateway.GatewayClient
import dev.rootcause.cape.sensing.ContextCollector
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private val CapeBg = Color(0xFFF4F7FF)
private val Glass = Color(0xFFFFFFFF)
private val GlassStrong = Color(0xFFEDEBFF)
private val CapeText = Color(0xFF111827)
private val CapeMuted = Color(0xFF5B6478)
private val CapeAccent = Color(0xFF5B67F1)
private val CapeBlue = Color(0xFF2F80ED)
private val CapeGreen = Color(0xFF22C55E)
private val CapeOrange = Color(0xFFF97316)
private val CapeWarning = Color(0xFFFBBF24)
private val CapeDanger = Color(0xFFFB7185)

private data class CommuteCacheEntry(
    val meetingKey: String,
    val plan: dev.rootcause.cape.core.CommutePlan,
    val meetingTitle: String?,
    val meetingStartEpochMs: Long?,
    val proposedDepartureEpochMs: Long,
    val originLat: Double?,
    val originLng: Double?,
    val notified: Boolean = false,
    val dismissed: Boolean = false,
    val actualDepartureEpochMs: Long? = null,
    val departureFeedbackSent: Boolean = false,
    val lateNotified: Boolean = false
)

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        renderApp()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        renderApp()
    }

    private fun renderApp() {
        setContent {
            CapeApp(
                onRequestRuntimePermissions = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    )
                }
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            COMMUTE_CHANNEL,
            "Commute alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Departure and route readiness alerts"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

class CapeSyncService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastStressScore = 0
    private val syncRunnable = object : Runnable {
        override fun run() {
            Thread {
                val collector = ContextCollector(applicationContext)
                val snapshot = collector.collect().snapshot
                val decision = runCatching { GatewayClient().requestDecision(snapshot) }
                    .getOrElse { DecisionOrchestrator().decide(snapshot) }
                val plan = decision.commutePlan
                val cache = loadCommuteCache(applicationContext).toMutableMap()
                resolveCommuteCacheEntry(snapshot, cache)
                val key = buildMeetingKey(snapshot)
                val cached = key?.let { cache[it] }
                if (
                    plan != null &&
                    key != null &&
                    plan.modes.isNotEmpty() &&
                    plan.shouldAlert &&
                    cached?.dismissed != true &&
                    cached?.notified != true
                ) {
                    showCommuteNotification(applicationContext, plan.leaveByLocal, plan.destination ?: "your destination", plan.modes.first().durationText)
                    cache[key] = CommuteCacheEntry(
                        meetingKey = key,
                        plan = plan,
                        meetingTitle = snapshot.nextMeetingTitle,
                        meetingStartEpochMs = snapshot.nextMeetingStartEpochMs,
                        proposedDepartureEpochMs = System.currentTimeMillis() + plan.leaveInMinutes * 60_000L,
                        originLat = snapshot.currentLatitude,
                        originLng = snapshot.currentLongitude,
                        notified = true,
                        dismissed = false
                    )
                }
                if (key != null) {
                    val entry = cache[key]
                    if (
                        entry != null &&
                        !entry.dismissed &&
                        !entry.lateNotified &&
                        System.currentTimeMillis() > entry.proposedDepartureEpochMs
                    ) {
                        showLateNotification(applicationContext, entry.plan.destination ?: "your destination")
                        cache[key] = entry.copy(lateNotified = true)
                    }
                }
                saveCommuteCache(applicationContext, cache)
                if (decision.stress.score >= 75 && decision.stress.score - lastStressScore >= 15) {
                    showStressNotification(applicationContext, decision.stress.score, decision.stress.level)
                }
                lastStressScore = decision.stress.score
                maybeShowTodoPromptNotification(applicationContext, snapshot)
            }.start()
            handler.postDelayed(this, SERVICE_SYNC_INTERVAL_MS)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_CAPE_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PAUSE_CAPE_SERVICE) {
            val pauseUntil = System.currentTimeMillis() + 60 * 60_000L
            getSharedPreferences("cape_context", Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_SERVICE_PAUSED_UNTIL, pauseUntil)
                .apply()
            handler.removeCallbacks(syncRunnable)
            startForeground(2001, serviceNotification("Paused for 1 hour"))
            return START_STICKY
        }
        val pausedUntil = getSharedPreferences("cape_context", Context.MODE_PRIVATE)
            .getLong(KEY_SERVICE_PAUSED_UNTIL, 0L)
        if (pausedUntil > System.currentTimeMillis()) {
            startForeground(2001, serviceNotification("Paused for 1 hour"))
            handler.removeCallbacks(syncRunnable)
            handler.postDelayed(syncRunnable, pausedUntil - System.currentTimeMillis())
            return START_STICKY
        }
        startForeground(2001, serviceNotification())
        handler.removeCallbacks(syncRunnable)
        handler.post(syncRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(syncRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun serviceNotification(text: String = "Automatic context and commute sync is active.") = NotificationCompat.Builder(this, COMMUTE_CHANNEL)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("CAPE is monitoring stress")
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .addAction(0, "Pause", PendingIntent.getService(this, 3001, Intent(this, CapeSyncService::class.java).setAction(ACTION_PAUSE_CAPE_SERVICE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .addAction(0, "Stop", PendingIntent.getService(this, 3002, Intent(this, CapeSyncService::class.java).setAction(ACTION_STOP_CAPE_SERVICE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .build()
}

@Composable
fun CapeApp(onRequestRuntimePermissions: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cape_context", Context.MODE_PRIVATE) }
    var isOnboarded by remember { mutableStateOf(prefs.getBoolean("onboarded", false)) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = CapeAccent,
            secondary = CapeBlue,
            background = CapeBg,
            surface = Glass,
            onPrimary = Color.White,
            onBackground = CapeText,
            onSurface = CapeText
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = CapeBg) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFFEFF4FF), Color(0xFFE5E8FF), Color(0xFFF9FAFF))))
                    .padding(16.dp)
            ) {
                if (!isOnboarded) {
                    SignupScreen(
                        onRequestRuntimePermissions = onRequestRuntimePermissions,
                        onComplete = { profile, places ->
                            saveProfile(context, profile, places)
                            startCapeSyncService(context)
                            isOnboarded = true
                        }
                    )
                } else {
                    HomeShell(onRequestRuntimePermissions)
                }
            }
        }
    }
}

@Composable
private fun SignupScreen(
    onRequestRuntimePermissions: () -> Unit,
    onComplete: (UserProfile, List<SavedPlace>) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var home by remember { mutableStateOf("") }
    var work by remember { mutableStateOf("") }
    var college by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("CAPE", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        Text("Set up your stress-aware commute assistant.", color = CapeMuted)
        GlassCard {
            Text("Create Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Name") })
            OutlinedTextField(value = home, onValueChange = { home = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Home location, optional") })
            OutlinedTextField(value = work, onValueChange = { work = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Work location, optional") })
            OutlinedTextField(value = college, onValueChange = { college = it }, modifier = Modifier.fillMaxWidth(), label = { Text("College location, optional") })
            Text("You can skip places now and add them later. Calendar and location permissions help CAPE detect meetings, destination, and commute state.", color = CapeMuted)
            OutlinedButton(onClick = onRequestRuntimePermissions, modifier = Modifier.fillMaxWidth()) {
                Text("Grant Location, Calendar, Notifications")
            }
            Button(
                onClick = {
                    status = "Resolving places..."
                    Thread {
                        val places = listOf("home" to home, "work" to work, "college" to college)
                            .filter { it.second.isNotBlank() }
                            .mapNotNull { (kind, query) ->
                                runCatching {
                                    val place = GatewayClient().geocodePlace(query)
                                    place.copy(kind = kind, label = kind.replaceFirstChar { it.titlecase() }, radiusMeters = fixedPlaceRadius(kind))
                                }.getOrNull()
                            }
                        (context as? ComponentActivity)?.runOnUiThread {
                            onComplete(UserProfile(name.ifBlank { "User" }), places)
                        }
                    }.start()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CapeAccent)
            ) {
                Text("Continue")
            }
            if (status.isNotBlank()) Text(status, color = CapeMuted)
        }
    }
}

@Composable
private fun HomeShell(onRequestRuntimePermissions: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cape_context", Context.MODE_PRIVATE) }
    val collector = remember { ContextCollector(context.applicationContext) }
    var collection by remember { mutableStateOf(collector.collect()) }
    var decision by remember { mutableStateOf(DecisionOrchestrator().decide(collection.snapshot)) }
    var syncStatus by remember { mutableStateOf("Auto sync starting") }
    var selectedSection by remember { mutableStateOf(HomeSection.Dashboard) }
    var lastStressScore by remember { mutableStateOf(decision.stress.score) }
    var feedbackStatus by remember { mutableStateOf("") }
    var executionStatus by remember { mutableStateOf("") }
    var pendingApproval by remember { mutableStateOf<CapeDecision?>(null) }
    var showReflection by remember { mutableStateOf(false) }
    var reflectionStatus by remember { mutableStateOf("") }
    var showTodoPrompt by remember { mutableStateOf(shouldPromptForTodoUpdate(context, collection.snapshot)) }
    var forceTodoEditor by remember { mutableStateOf(false) }

    fun syncOnce() {
        Thread {
            val fresh = collector.collect()
            val result = runCatching { GatewayClient().requestDecision(fresh.snapshot) }
            (context as? ComponentActivity)?.runOnUiThread {
                val previousLocation = collection.snapshot.locationState
                collection = fresh
                val current = result.getOrElse { DecisionOrchestrator().decide(fresh.snapshot) }
                syncStatus = result.fold(
                    onSuccess = { "Synced just now" },
                    onFailure = { error ->
                        Log.e("CAPE", "Gateway sync failed", error)
                        "Gateway unavailable: ${error.javaClass.simpleName}: ${error.message ?: "no details"}"
                    }
                )
                val cache = loadCommuteCache(context).toMutableMap()
                resolveCommuteCacheEntry(fresh.snapshot, cache)
                val activeKey = buildMeetingKey(fresh.snapshot)
                val now = System.currentTimeMillis()
                var updated = current
                if (activeKey != null) {
                    val cached = cache[activeKey]
                    if (current.commutePlan != null) {
                        val previous = cached?.takeIf { !it.dismissed }
                        cache[activeKey] = CommuteCacheEntry(
                            meetingKey = activeKey,
                            plan = current.commutePlan,
                            meetingTitle = fresh.snapshot.nextMeetingTitle,
                            meetingStartEpochMs = fresh.snapshot.nextMeetingStartEpochMs,
                            proposedDepartureEpochMs = now + current.commutePlan.leaveInMinutes * 60_000L,
                            originLat = fresh.snapshot.currentLatitude,
                            originLng = fresh.snapshot.currentLongitude,
                            notified = previous?.notified == true,
                            lateNotified = previous?.lateNotified == true,
                            departureFeedbackSent = previous?.departureFeedbackSent == true
                        )
                    } else if (cached != null && !cached.dismissed) {
                        updated = updated.copy(commutePlan = cached.plan)
                    }
                    val latest = cache[activeKey]
                    // Keep notifications in background service to avoid popups on every app open.
                    val speed = fresh.snapshot.currentSpeedMps ?: 0f
                    if (
                        latest != null &&
                        !latest.dismissed &&
                        latest.actualDepartureEpochMs == null &&
                        latest.originLat != null &&
                        latest.originLng != null &&
                        fresh.snapshot.currentLatitude != null &&
                        fresh.snapshot.currentLongitude != null
                    ) {
                        val movedMeters = distanceBetween(
                            latest.originLat,
                            latest.originLng,
                            fresh.snapshot.currentLatitude,
                            fresh.snapshot.currentLongitude
                        )
                        if (movedMeters > 150f && speed > 2.0f) {
                            val actual = now
                            val delayedByMinutes = ((actual - latest.proposedDepartureEpochMs) / 60_000L).toInt()
                            cache[activeKey] = latest.copy(actualDepartureEpochMs = actual)
                            if (!latest.departureFeedbackSent) {
                                Thread {
                                    runCatching {
                                        GatewayClient().sendFeedback(
                                            packId = "commute_departure_timing",
                                            signal = if (delayedByMinutes > 2) "rejected" else "accepted",
                                            note = "User departed ${if (delayedByMinutes >= 0) delayedByMinutes else -delayedByMinutes} minutes ${if (delayedByMinutes >= 0) "after" else "before"} suggested departure; location_state ${fresh.snapshot.locationState}."
                                        )
                                    }
                                    val refreshed = loadCommuteCache(context).toMutableMap()
                                    refreshed[activeKey]?.let { existing ->
                                        refreshed[activeKey] = existing.copy(departureFeedbackSent = true)
                                        saveCommuteCache(context, refreshed)
                                    }
                                }.start()
                            }
                        }
                    }
                    val newest = cache[activeKey]
                    if (
                        newest != null &&
                        !newest.dismissed &&
                        !newest.lateNotified &&
                        now > newest.proposedDepartureEpochMs
                    ) {
                        showLateNotification(context, newest.plan.destination ?: "your destination")
                        cache[activeKey] = newest.copy(lateNotified = true)
                    }
                }
                saveCommuteCache(context, cache)
                decision = updated
                if (shouldPromptForTodoUpdate(context, fresh.snapshot)) {
                    showTodoPrompt = true
                }
                if (shouldShowReflection(previousLocation, fresh.snapshot, prefs)) {
                    showReflection = true
                }
                if (decision.stress.score >= 75 && decision.stress.score - lastStressScore >= 15) {
                    showStressNotification(context, decision.stress.score, decision.stress.level)
                }
                lastStressScore = decision.stress.score
            }
        }.start()
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> collector.recordScreenOff()
                    Intent.ACTION_SCREEN_ON -> collector.recordScreenOn()
                    Intent.ACTION_USER_PRESENT -> collector.recordUnlock()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(Unit) {
        startCapeSyncService(context)
        while (true) {
            syncOnce()
            delay(60_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Header(syncStatus)
        SectionTabs(selectedSection) { selectedSection = it }
        when (selectedSection) {
            HomeSection.Dashboard -> DashboardSection(
                collection = collection,
                decision = decision,
                onRequestRuntimePermissions = onRequestRuntimePermissions,
                onRequestDndAccess = { openNotificationPolicyAccessSettings(context) },
                onRequestWriteSettings = { openWriteSettingsPanel(context) },
                onRequestUsageAccess = { openUsageAccessSettings(context) },
                onApplyPack = {
                    val executableDecision = if (decision.type == "SUGGEST_PACK") {
                        decision.copy(type = "APPLY_PACK", actions = decision.suggestedActions, suggestedActions = emptyList())
                    } else {
                        decision
                    }
                    pendingApproval = executableDecision
                },
                onApplyDemoWallpaper = { wallpaperAction ->
                    val demoDecision = decision.copy(
                        type = "APPLY_PACK",
                        packId = "manual_wallpaper_demo",
                        actions = listOf(wallpaperAction),
                        suggestedActions = emptyList(),
                        blockedByPermission = emptyList(),
                        explanation = "Manual wallpaper demo action."
                    )
                    pendingApproval = demoDecision
                },
                executionStatus = executionStatus
            )
            HomeSection.Commute -> CommuteSection(
                decision = decision,
                meetingKey = buildMeetingKey(collection.snapshot),
                onCloseRoute = { key ->
                    val cache = loadCommuteCache(context).toMutableMap()
                    cache[key]?.let { entry ->
                        cache[key] = entry.copy(dismissed = true)
                        saveCommuteCache(context, cache)
                        decision = decision.copy(commutePlan = null)
                    }
                },
                onSendFeedback = { signal, note ->
                    Thread {
                        val ack = runCatching {
                            GatewayClient().sendFeedback(
                                packId = decision.packId.ifBlank { "commute_feedback" },
                                signal = signal,
                                note = note
                            )
                        }
                        (context as? ComponentActivity)?.runOnUiThread {
                            feedbackStatus = ack.fold(
                                onSuccess = { "Feedback saved: ${it.message}" },
                                onFailure = { "Feedback failed to send" }
                            )
                        }
                    }.start()
                },
                feedbackStatus = feedbackStatus
            )
            HomeSection.Profile -> ProfileSection(collection.snapshot)
            HomeSection.Plan -> TodoSection(
                snapshot = collection.snapshot,
                forceEditor = forceTodoEditor,
                onEditorConsumed = { forceTodoEditor = false }
            )
        }
    }
    pendingApproval?.let { approvalDecision ->
        ApprovalDialog(
            decision = approvalDecision,
            onDecision = { approved ->
                val outcome = if (approved) "accepted" else "rejected"
                recordDecisionApproval(context, approvalDecision, outcome)
                Thread {
                    runCatching {
                        GatewayClient().sendDecisionApproval(
                            packId = approvalDecision.packId,
                            signal = outcome,
                            note = if (approved) "User approved CAPE decision." else "User rejected CAPE decision.",
                            actions = approvalDecision.actions,
                            confidence = approvalDecision.confidence
                        )
                    }
                }.start()
                executionStatus = if (approved) {
                    PackExecutor(context.applicationContext).apply(approvalDecision)
                } else {
                    "Rejected by user; no device changes applied."
                }
                pendingApproval = null
            }
        )
    }
    if (showTodoPrompt) {
        TodoPromptDialog(
            onDismiss = {
                markTodoPromptSeen(context)
                showTodoPrompt = false
            },
            onOpen = {
                markTodoPromptSeen(context)
                showTodoPrompt = false
                forceTodoEditor = true
                selectedSection = HomeSection.Plan
            }
        )
    }
    if (showReflection) {
        ReflectionBottomSheet(
            status = reflectionStatus,
            onDismiss = { showReflection = false },
            onSubmit = { tags, note ->
                Thread {
                    val timestamp = collection.snapshot.currentTimeIso ?: java.time.OffsetDateTime.now().toString()
                    val ack = runCatching { GatewayClient().sendDailyReflection(tags, note, timestamp) }
                    (context as? ComponentActivity)?.runOnUiThread {
                        reflectionStatus = ack.fold(
                            onSuccess = { "Reflection saved" },
                            onFailure = { "Reflection failed to send" }
                        )
                        if (ack.isSuccess) {
                            markReflectionShown(prefs)
                            showReflection = false
                        }
                    }
                }.start()
            }
        )
    }
}

@Composable
private fun Header(syncStatus: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Good day", style = MaterialTheme.typography.titleMedium, color = CapeMuted)
        Text("Stress Monitor", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(syncStatus, color = CapeAccent, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ApprovalDialog(
    decision: CapeDecision,
    onDecision: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDecision(false) },
        title = { Text("Apply this CAPE decision?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricRow("Pack", decision.packId)
                MetricRow("Confidence", "%.2f".format(decision.confidence))
                Text("Key actions", fontWeight = FontWeight.SemiBold)
                val actions = decision.actions.ifEmpty { decision.suggestedActions }
                Text(actions.take(5).joinToString().ifBlank { "none" }, color = CapeMuted)
            }
        },
        confirmButton = {
            Button(onClick = { onDecision(true) }) { Text("YES") }
        },
        dismissButton = {
            OutlinedButton(onClick = { onDecision(false) }) { Text("NO") }
        },
        containerColor = Glass
    )
}

@Composable
private fun TodoPromptDialog(onDismiss: () -> Unit, onOpen: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Do you want to update today's todo list?") },
        text = { Text("CAPE uses this to understand workload pressure and plan gentler decisions.", color = CapeMuted) },
        confirmButton = {
            Button(onClick = onOpen) { Text("YES") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("NO") }
        },
        containerColor = Glass
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReflectionBottomSheet(
    status: String,
    onDismiss: () -> Unit,
    onSubmit: (List<String>, String) -> Unit
) {
    val options = listOf("Heavy workload", "Assignments", "Meetings", "Exams", "Personal stress", "Chill day")
    var selected by remember { mutableStateOf(setOf<String>()) }
    var note by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Glass) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Quick reflection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Help CAPE learn what today felt like.", color = CapeMuted)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.take(3).forEach { option ->
                    FilterChip(
                        selected = selected.contains(option),
                        onClick = { selected = toggleSelected(selected, option) },
                        label = { Text(option) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.drop(3).forEach { option ->
                    FilterChip(
                        selected = selected.contains(option),
                        onClick = { selected = toggleSelected(selected, option) },
                        label = { Text(option) }
                    )
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Optional note") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Later") }
                Button(
                    onClick = { onSubmit(selected.toList(), note) },
                    modifier = Modifier.weight(1f),
                    enabled = selected.isNotEmpty() || note.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CapeAccent)
                ) { Text("Save") }
            }
            if (status.isNotBlank()) Text(status, color = CapeMuted)
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun toggleSelected(current: Set<String>, value: String): Set<String> =
    if (current.contains(value)) current - value else current + value

@Composable
private fun SectionTabs(selected: HomeSection, onSelect: (HomeSection) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        HomeSection.values().forEach { section ->
            FilterChip(
                selected = selected == section,
                onClick = { onSelect(section) },
                label = { Text(section.label) }
            )
        }
    }
}

@Composable
private fun DashboardSection(
    collection: ContextCollectionResult,
    decision: CapeDecision,
    onRequestRuntimePermissions: () -> Unit,
    onRequestDndAccess: () -> Unit,
    onRequestWriteSettings: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onApplyPack: () -> Unit,
    onApplyDemoWallpaper: (String) -> Unit,
    executionStatus: String
) {
    val snapshot = collection.snapshot
    GlassCard {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StressGauge(score = decision.stress.score, level = decision.stress.level)
        }
        Text(decision.explanation, color = CapeMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        InfoTile("Sleep debt", "${snapshot.sleepDebtMinutes} min", Modifier.weight(1f))
        InfoTile("Meetings", snapshot.meetingLoadToday.toString(), Modifier.weight(1f))
    }
    GlassCard {
        Text("Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        MetricRow("Current location", readableLocation(snapshot))
        MetricRow("Next meeting", snapshot.nextMeetingTitle ?: "No meeting found")
        MetricRow("Destination", snapshot.nextMeetingLocation ?: "No destination")
        MetricRow("Starts in", snapshot.nextMeetingMinutes?.let { "$it min" } ?: "none")
    }
    GlassCard {
        Text("Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val permissions = readPermissionState(LocalContext.current)
        MetricRow("Location", if (permissions.location) "Granted" else "Missing")
        MetricRow("Calendar", if (permissions.calendar) "Granted" else "Missing")
        MetricRow("Calendar write", if (permissions.calendarWrite) "Granted" else "Missing")
        MetricRow("Usage access", if (permissions.usageStats) "Granted" else "Open settings")
        MetricRow("Notifications", if (permissions.notifications) "Granted" else "Missing")
        MetricRow("DND access", if (permissions.notificationPolicyAccess) "Granted" else "Open settings")
        MetricRow("Brightness control", if (permissions.writeSettings) "Granted" else "Open settings")
        Button(onClick = onRequestRuntimePermissions, modifier = Modifier.fillMaxWidth()) { Text("Update Permissions") }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onRequestDndAccess, modifier = Modifier.weight(1f)) { Text("DND Access") }
            OutlinedButton(onClick = onRequestWriteSettings, modifier = Modifier.weight(1f)) { Text("Brightness Access") }
        }
        OutlinedButton(onClick = onRequestUsageAccess, modifier = Modifier.fillMaxWidth()) {
            Text("Usage Access")
        }
    }
    GlassCard {
        Text("Pack Execution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        MetricRow("Decision", decision.type)
        MetricRow("Pack", decision.packId)
        MetricRow("Confidence", "%.2f".format(decision.confidence))
        MetricRow("Actions", (decision.actions.ifEmpty { decision.suggestedActions }).joinToString().ifBlank { "none" })
        Text("CAPE will ask before any device setting changes. Background sync can suggest decisions and commute alerts, but pack execution needs your YES.", color = CapeMuted)
        Button(
            onClick = onApplyPack,
            enabled = decision.type == "APPLY_PACK" || decision.type == "SUGGEST_PACK",
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CapeAccent)
        ) {
            Text(if (decision.type == "SUGGEST_PACK") "Apply Suggested Pack" else "Apply Pack")
        }
        if (executionStatus.isNotBlank()) Text(executionStatus, color = CapeMuted)
    }
    OpenClawSection(decision)
    GlassCard {
        Text("Wallpaper Demo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onApplyDemoWallpaper("WALLPAPER_FOCUS") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = CapeAccent)
            ) { Text("Focus") }
            Button(
                onClick = { onApplyDemoWallpaper("WALLPAPER_RELAX") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = CapeGreen)
            ) { Text("Relax") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onApplyDemoWallpaper("WALLPAPER_COMMUTE") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = CapeBlue)
            ) { Text("Commute") }
            OutlinedButton(
                onClick = { onApplyDemoWallpaper("WALLPAPER_RESET") },
                modifier = Modifier.weight(1f)
            ) { Text("Reset") }
        }
    }
}

@Composable
private fun CommuteSection(
    decision: CapeDecision,
    meetingKey: String?,
    onCloseRoute: (String) -> Unit,
    onSendFeedback: (String, String) -> Unit,
    feedbackStatus: String
) {
    val plan = decision.commutePlan
    val hasPlan = plan != null
    val hasMapRoute = plan != null && plan.source.contains("google_routes_api") && plan.polyline != null
    var feedbackNote by remember { mutableStateOf("") }
    GlassCard {
        Text("Commute Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (!hasPlan) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = CapeAccent,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(20.dp)
                )
                Text("Loading live commute details...", color = CapeMuted)
            }
        } else {
            MetricRow("Destination", plan.destination ?: "Calendar destination")
            MetricRow("Recommended departure", plan.leaveByLocal)
            MetricRow("Data source", plan.source)
            MetricRow("ETA", if (plan.etaMinutes > 0) "${plan.etaMinutes} min" else "unknown")
            if (plan.leaveInMinutes <= 0) {
                Text("You are running late. Leave as soon as possible.", color = CapeDanger, fontWeight = FontWeight.Bold)
            } else {
                Text(plan.reason, color = CapeMuted)
            }
            if (meetingKey != null) {
                OutlinedButton(onClick = { onCloseRoute(meetingKey) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Close this route")
                }
            }
            plan.mapsUrl?.let { url ->
                val context = LocalContext.current
                Button(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CapeBlue)
                ) {
                    Text("Open Fastest Route in Google Maps")
                }
            }
        }
    }
    if (hasMapRoute && plan?.polyline != null) {
        RouteMap(plan.polyline, plan.destination)
    }
    if (hasPlan) plan?.modes?.forEach { mode ->
        GlassCard(container = GlassStrong) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            painter = painterResource(id = modeIcon(mode.id)),
                            contentDescription = mode.label,
                            tint = CapeBlue
                        )
                        Text(mode.label, fontWeight = FontWeight.Bold)
                    }
                    Text(mode.distanceText.ifBlank { "Route available" }, color = CapeMuted)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(mode.durationText, color = CapeAccent, fontWeight = FontWeight.Bold)
                    Text("Leave ${mode.leaveByLocal}", color = CapeMuted)
                }
            }
        }
    }
    if (hasPlan && plan?.directions?.isNotEmpty() == true) {
        GlassCard {
            Text("Route Guidance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            plan.directions.forEachIndexed { index, step ->
                Text("${modeEmoji(step.travelMode)} ${index + 1}. ${step.instruction}", color = CapeText)
                Text("${step.distanceText} · ${step.durationText} · ${step.travelMode}", color = CapeMuted)
            }
            plan.mapsUrl?.let { url ->
                val context = LocalContext.current
                Button(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open in Google Maps")
                }
            }
        }
    }
    if (hasPlan) GlassCard {
        Text("Feedback for CAPE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Tell CAPE and OpenClaw what worked so future departure estimates can adapt to your routine.", color = CapeMuted)
        OutlinedTextField(
            value = feedbackNote,
            onValueChange = { feedbackNote = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("What happened today?") }
        )
        Button(
            onClick = {
                onSendFeedback("neutral", feedbackNote.ifBlank { "Commute feedback submitted without explicit rating." })
                feedbackNote = ""
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CapeAccent)
        ) { Text("Send feedback to OpenClaw") }
        if (feedbackStatus.isNotBlank()) {
            Text(feedbackStatus, color = CapeMuted)
        }
    }
}

@Composable
private fun ProfileSection(snapshot: ContextSnapshot) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cape_context", Context.MODE_PRIVATE) }
    val savedByKind = snapshot.savedPlaces.associateBy { normalizedPlaceKind(it.kind) }
    var home by remember(snapshot.savedPlaces) { mutableStateOf(savedByKind["home"]?.query.orEmpty()) }
    var work by remember(snapshot.savedPlaces) { mutableStateOf((savedByKind["work"] ?: savedByKind["office"])?.query.orEmpty()) }
    var college by remember(snapshot.savedPlaces) { mutableStateOf(savedByKind["college"]?.query.orEmpty()) }
    var selectedHome by remember(snapshot.savedPlaces) { mutableStateOf(savedByKind["home"]) }
    var selectedWork by remember(snapshot.savedPlaces) { mutableStateOf(savedByKind["work"] ?: savedByKind["office"]) }
    var selectedCollege by remember(snapshot.savedPlaces) { mutableStateOf(savedByKind["college"]) }
    var role by remember { mutableStateOf(prefs.getString(KEY_USER_ROLE, "student") ?: "student") }
    var startTime by remember { mutableStateOf(prefs.getString(KEY_ROUTINE_START, "09:00") ?: "09:00") }
    var endTime by remember { mutableStateOf(prefs.getString(KEY_ROUTINE_END, "16:00") ?: "16:00") }
    var status by remember { mutableStateOf("Search and save places with real coordinates.") }
    val places = snapshot.savedPlaces

    GlassCard {
        Text("Saved Places", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Type a place name, tap a suggestion, then save. Blank fields keep the existing saved place.", color = CapeMuted)
        PlaceSearchField(
            label = "Home",
            value = home,
            selectedPlace = selectedHome,
            onValueChange = {
                home = it
                selectedHome = null
            },
            onPlaceSelected = {
                selectedHome = it
                home = it.label
            }
        )
        PlaceSearchField(
            label = "Work",
            value = work,
            selectedPlace = selectedWork,
            onValueChange = {
                work = it
                selectedWork = null
            },
            onPlaceSelected = {
                selectedWork = it
                work = it.label
            }
        )
        PlaceSearchField(
            label = "College",
            value = college,
            selectedPlace = selectedCollege,
            onValueChange = {
                college = it
                selectedCollege = null
            },
            onPlaceSelected = {
                selectedCollege = it
                college = it.label
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    status = "Resolving places..."
                    Thread {
                        val existing = snapshot.savedPlaces.associateBy { normalizedPlaceKind(it.kind) }
                        val updated = buildUpdatedFixedPlaces(
                            existingPlaces = snapshot.savedPlaces,
                            home = resolvePlaceForSave("home", home, selectedHome, existing["home"]),
                            work = resolvePlaceForSave("work", work, selectedWork, existing["work"] ?: existing["office"]),
                            college = resolvePlaceForSave("college", college, selectedCollege, existing["college"])
                        )
                        (context as? ComponentActivity)?.runOnUiThread {
                            savePlaces(context, updated)
                            status = if (updated.none { it.latitude != null && it.longitude != null }) {
                                "No places resolved. Check gateway and Maps key."
                            } else {
                                "Saved home, work, and college place settings."
                            }
                        }
                    }.start()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Save") }
            OutlinedButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(home.ifBlank { work.ifBlank { college.ifBlank { "near me" } } })}"))) },
                modifier = Modifier.weight(1f)
            ) { Text("Map") }
        }
        Text(status, color = CapeMuted)
        places.forEach { place ->
            MetricRow(
                place.kind.replaceFirstChar { it.titlecase() },
                place.latitude?.let { "%.4f, %.4f - %dm radius".format(it, place.longitude, place.radiusMeters) } ?: place.query
            )
        }
    }
    GlassCard {
        Text("Daily Routine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("CAPE uses this to suggest day blocks and add confirmed events to Calendar.", color = CapeMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(selected = role == "student", onClick = { role = "student" }, label = { Text("Student") })
            FilterChip(selected = role == "employee", onClick = { role = "employee" }, label = { Text("Employee") })
        }
        OutlinedTextField(value = startTime, onValueChange = { startTime = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Start time HH:mm") })
        OutlinedTextField(value = endTime, onValueChange = { endTime = it }, modifier = Modifier.fillMaxWidth(), label = { Text("End time HH:mm") })
        Button(
            onClick = {
                prefs.edit()
                    .putString(KEY_USER_ROLE, role)
                    .putString(KEY_ROUTINE_START, startTime)
                    .putString(KEY_ROUTINE_END, endTime)
                    .apply()
                status = "Routine saved."
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CapeAccent)
        ) { Text("Save Routine") }
    }
}

@Composable
private fun TodoSection(
    snapshot: ContextSnapshot,
    forceEditor: Boolean,
    onEditorConsumed: () -> Unit
) {
    val context = LocalContext.current
    var list by remember { mutableStateOf(loadTodoList(context)) }
    var draftTitle by remember { mutableStateOf("") }
    var draftDue by remember { mutableStateOf("") }
    var updateTimes by remember { mutableStateOf(loadTodoPromptTimes(context)) }
    var timeDraft by remember { mutableStateOf(updateTimes.joinToString(",")) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(forceEditor) {
        if (forceEditor) {
            status = "Update today's todos."
            onEditorConsumed()
        }
    }

    GlassCard {
        Text("Day Todo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Pending work affects CAPE stress scoring. Add due times like 14:30 when urgency matters.", color = CapeMuted)
        MetricRow("Pending", snapshot.todoPendingCount.toString())
        MetricRow("Urgent", snapshot.todoUrgentCount.toString())
        MetricRow("Overdue", snapshot.todoOverdueCount.toString())
        MetricRow("Todo pressure", "${snapshot.todoPressureScore}/100")
        if (list.items.isEmpty()) {
            Text("No todos for today yet.", color = CapeMuted)
        }
        list.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    Text(todoMetaLabel(item), color = CapeMuted)
                }
                FilterChip(
                    selected = item.completed,
                    onClick = {
                        list = list.copy(items = list.items.map { if (it.id == item.id) it.copy(completed = !it.completed, updatedAt = System.currentTimeMillis()) else it }, updatedAt = System.currentTimeMillis())
                        saveTodoList(context, list)
                        recordTodoEdit(context, list, "completed:${item.title}")
                    },
                    label = { Text(if (item.completed) "Done" else "Open") }
                )
            }
        }
        OutlinedTextField(value = draftTitle, onValueChange = { draftTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Todo") })
        OutlinedTextField(value = draftDue, onValueChange = { draftDue = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Due time HH:mm optional") })
        Button(
            onClick = {
                val title = draftTitle.trim()
                if (title.isBlank()) return@Button
                val now = System.currentTimeMillis()
                val item = TodoItem(
                    id = "todo_${now}",
                    title = title,
                    dueAt = parseTodoDueToday(draftDue),
                    completed = false,
                    createdAt = now,
                    updatedAt = now
                )
                list = ensureTodayTodoList(list).copy(items = ensureTodayTodoList(list).items + item, updatedAt = now)
                saveTodoList(context, list)
                recordTodoEdit(context, list, "added:$title")
                draftTitle = ""
                draftDue = ""
                status = "Todo saved and sent to OpenClaw."
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CapeAccent)
        ) { Text("Add Todo") }
        OutlinedTextField(value = timeDraft, onValueChange = { timeDraft = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Prompt times, comma-separated HH:mm") })
        Button(
            onClick = {
                updateTimes = parseTodoPromptTimes(timeDraft)
                saveTodoPromptTimes(context, updateTimes)
                status = "Todo update prompt times saved."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save Update Times") }
        val learned = loadLearnedTodoHours(context)
        if (learned.isNotEmpty()) {
            Text("Learned update windows: ${learned.joinToString { "%02d:00".format(it) }}", color = CapeMuted)
        }
        if (status.isNotBlank()) Text(status, color = CapeMuted)
    }
}

@Composable
private fun PlaceSearchField(
    label: String,
    value: String,
    selectedPlace: SavedPlace?,
    onValueChange: (String) -> Unit,
    onPlaceSelected: (SavedPlace) -> Unit
) {
    val context = LocalContext.current
    var suggestions by remember { mutableStateOf(emptyList<SavedPlace>()) }
    var searchStatus by remember { mutableStateOf("") }

    LaunchedEffect(value) {
        if (value.length < 3 || selectedPlace?.label == value) {
            suggestions = emptyList()
            searchStatus = ""
            return@LaunchedEffect
        }
        searchStatus = "Searching..."
        delay(350)
        Thread {
            val results = runCatching { GatewayClient().searchPlaces(value) }.getOrDefault(emptyList())
            (context as? ComponentActivity)?.runOnUiThread {
                suggestions = results
                searchStatus = if (results.isEmpty()) "No suggestions yet." else ""
            }
        }.start()
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) }
        )
        selectedPlace?.let { place ->
            if (place.latitude != null && place.longitude != null) {
                Text("Selected: ${place.label}", color = CapeGreen)
            }
        }
        if (searchStatus.isNotBlank()) {
            Text(searchStatus, color = CapeMuted)
        }
        suggestions.forEach { place ->
            OutlinedButton(
                onClick = {
                    onPlaceSelected(place)
                    suggestions = emptyList()
                    searchStatus = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(place.label, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun OpenClawSection(decision: CapeDecision) {
    GlassCard {
        Text("OpenClaw Runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val openclaw = decision.openclaw
        MetricRow("Orchestrator", openclaw?.orchestrator ?: "local demo")
        MetricRow("Runtime", openclaw?.runtime ?: "android-local")
        MetricRow("Agent", openclaw?.agentId ?: "cape")
        MetricRow("Session", openclaw?.sessionId ?: "not started")
        openclaw?.fallbackReason?.let { reason ->
            MetricRow("Fallback", reason)
        }
        decision.reasoningNote?.let { note ->
            Text(note, color = CapeMuted)
        }
        if (decision.agentTrace.isNotEmpty()) {
            Text("Agent Trace", fontWeight = FontWeight.SemiBold)
            decision.agentTrace.take(8).forEachIndexed { index, item ->
                Text("${index + 1}. ${item.agent} [${item.status}] - ${item.output}", color = CapeMuted)
            }
        }
    }
}

@Composable
private fun RouteMap(encodedPolyline: String, destination: String?) {
    GlassCard {
        Text("Live Route Map", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(240.dp),
            factory = { ctx ->
                MapView(ctx).apply {
                    onCreate(null)
                    onResume()
                    getMapAsync { map ->
                        val points = decodePolyline(encodedPolyline)
                        if (points.isNotEmpty()) {
                            map.addPolyline(PolylineOptions().addAll(points).color(CapeAccent.toArgb()).width(12f))
                            map.addMarker(MarkerOptions().position(points.first()).title("Current location"))
                            map.addMarker(MarkerOptions().position(points.last()).title(destination ?: "Destination"))
                            val bounds = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
                            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun StressGauge(score: Int, level: String) {
    Box(modifier = Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val trackStroke = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round)
            val progressStroke = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round)
            val arcSize = Size(size.width - 28.dp.toPx(), size.height - 28.dp.toPx())
            val topLeft = Offset(14.dp.toPx(), 14.dp.toPx())
            val startAngle = 135f
            val totalSweep = 270f
            val stressBrush = Brush.sweepGradient(
                colorStops = arrayOf(
                    0.00f to CapeGreen,
                    0.35f to CapeWarning,
                    0.60f to CapeOrange,
                    0.85f to CapeDanger,
                    1.00f to CapeDanger
                ),
                center = Offset(size.width / 2f, size.height / 2f)
            )

            // Always show the full gauge track so users can read range at any score.
            drawArc(
                color = Color(0xFFD8DEED),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = trackStroke
            )
            drawArc(
                brush = stressBrush,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                alpha = 0.35f,
                style = trackStroke
            )
            drawArc(
                brush = stressBrush,
                startAngle = startAngle,
                sweepAngle = (score.coerceIn(0, 100) / 100f) * totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = progressStroke
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
            Text(level.uppercase(), color = CapeMuted, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, container = GlassStrong) {
        Text(label, color = CapeMuted)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    container: Color = Glass,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.50f),
                            Color.White.copy(alpha = 0.18f)
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = CapeMuted, modifier = Modifier.weight(1f))
        Text(value, color = CapeText, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

private fun readableLocation(snapshot: ContextSnapshot): String {
    return if (snapshot.currentLatitude != null && snapshot.currentLongitude != null) {
        "${snapshot.locationState} · %.4f, %.4f".format(snapshot.currentLatitude, snapshot.currentLongitude)
    } else {
        snapshot.locationState
    }
}

private fun saveProfile(context: Context, profile: UserProfile, places: List<SavedPlace>) {
    context.getSharedPreferences("cape_context", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("onboarded", true)
        .putString("user_name", profile.name)
        .apply()
    savePlaces(context, places)
}

private fun savePlaces(context: Context, places: List<SavedPlace>) {
    val array = JSONArray()
    places.forEach { place ->
        array.put(
            JSONObject()
                .put("kind", place.kind)
                .put("label", place.label)
                .put("query", place.query)
                .put("lat", place.latitude)
                .put("lng", place.longitude)
                .put("radiusMeters", place.radiusMeters)
        )
    }
    context.getSharedPreferences("cape_context", Context.MODE_PRIVATE).edit().putString("saved_places", array.toString()).apply()
}

private fun resolvePlaceForSave(kind: String, query: String, selected: SavedPlace?, existing: SavedPlace?): SavedPlace? {
    if (query.isBlank()) return existing
    val resolved = selected?.takeIf { it.latitude != null && it.longitude != null }
        ?: runCatching { GatewayClient().geocodePlace(query) }.getOrNull()
        ?: existing
        ?: return null
    return resolved.copy(
        kind = kind,
        label = kind.replaceFirstChar { it.titlecase() },
        query = resolved.label.ifBlank { query },
        radiusMeters = fixedPlaceRadius(kind)
    )
}

private fun buildUpdatedFixedPlaces(
    existingPlaces: List<SavedPlace>,
    home: SavedPlace?,
    work: SavedPlace?,
    college: SavedPlace?
): List<SavedPlace> {
    val fixedKinds = setOf("home", "work", "office", "college")
    val otherPlaces = existingPlaces.filter { normalizedPlaceKind(it.kind) !in fixedKinds }
    return otherPlaces + listOfNotNull(home, work, college)
}

private fun normalizedPlaceKind(kind: String): String =
    when (kind.lowercase()) {
        "office" -> "work"
        else -> kind.lowercase()
    }

private fun fixedPlaceRadius(kind: String): Int =
    when (kind) {
        "home" -> 180
        "work", "office", "college" -> 220
        "relaxing" -> 250
        else -> 250
    }

private fun showCommuteNotification(context: Context, leaveBy: String, destination: String, duration: String) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    recordNotificationEvent(context)
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context,
        1001,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = NotificationCompat.Builder(context, COMMUTE_CHANNEL)
        .setSmallIcon(android.R.drawable.ic_dialog_map)
        .setContentTitle("Commute details are ready")
        .setContentText("Leave by $leaveBy for $destination. Estimated travel: $duration.")
        .setStyle(NotificationCompat.BigTextStyle().bigText("Leave by $leaveBy for $destination. Estimated travel: $duration. Tap to view route, traffic-aware timing, and directions."))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(1001, notification)
}

private fun showStressNotification(context: Context, score: Int, level: String) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    recordNotificationEvent(context)
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(context, 1002, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification = NotificationCompat.Builder(context, COMMUTE_CHANNEL)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("Stress spike detected")
        .setContentText("Current stress is $score/100 ($level). Tap to review context.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(1002, notification)
}

private fun showLateNotification(context: Context, destination: String) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    recordNotificationEvent(context)
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(context, 1003, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification = NotificationCompat.Builder(context, COMMUTE_CHANNEL)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("Running late for meeting")
        .setContentText("Departure time has passed. Leave now for $destination.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(1003, notification)
}

private fun decodePolyline(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        var shift = 0
        var result = 0
        var b: Int
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20 && index < encoded.length)
        lat += if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20 && index < encoded.length)
        lng += if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        poly.add(LatLng(lat / 1E5, lng / 1E5))
    }
    return poly
}

private fun normalizeMeetingTitleKey(title: String?): String =
    (title ?: "meeting").trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "meeting" }
        .take(48)

/** Stable per meeting: avoids cache misses when gateway geocodes a shorter address than Calendar. */
private fun buildMeetingKey(snapshot: ContextSnapshot): String? {
    val meetingStart = snapshot.nextMeetingStartEpochMs ?: return null
    return "${meetingStart}_${normalizeMeetingTitleKey(snapshot.nextMeetingTitle)}"
}

private fun resolveCommuteCacheEntry(
    snapshot: ContextSnapshot,
    cache: MutableMap<String, CommuteCacheEntry>
): Pair<String?, CommuteCacheEntry?> {
    val canonical = buildMeetingKey(snapshot)
    if (canonical != null) {
        cache[canonical]?.takeIf { !it.dismissed }?.let { return canonical to it }
    }
    val startMs = snapshot.nextMeetingStartEpochMs ?: return canonical to null
    val legacyPair = cache.entries.firstOrNull { (_, e) ->
        e.meetingStartEpochMs == startMs && !e.dismissed
    } ?: return canonical to null
    val (legacyKey, entry) = legacyPair
    if (canonical != null && legacyKey != canonical) {
        cache.remove(legacyKey)
        val migrated = entry.copy(meetingKey = canonical)
        cache[canonical] = migrated
        return canonical to migrated
    }
    return legacyKey to entry
}

private fun loadCommuteCache(context: Context): Map<String, CommuteCacheEntry> {
    val raw = context.getSharedPreferences("cape_context", Context.MODE_PRIVATE)
        .getString(KEY_COMMUTE_CACHE, "{}")
        ?: "{}"
    return runCatching {
        val json = JSONObject(raw)
        json.keys().asSequence().associateWith { key ->
            val item = json.getJSONObject(key)
            CommuteCacheEntry(
                meetingKey = key,
                plan = parseCachedPlan(item.getJSONObject("plan")),
                meetingTitle = item.optString("meetingTitle").takeIf { it.isNotBlank() && it != "null" },
                meetingStartEpochMs = item.optLong("meetingStartEpochMs").takeIf { it > 0L },
                proposedDepartureEpochMs = item.optLong("proposedDepartureEpochMs"),
                originLat = item.optDoubleOrNull("originLat"),
                originLng = item.optDoubleOrNull("originLng"),
                notified = item.optBoolean("notified"),
                dismissed = item.optBoolean("dismissed"),
                actualDepartureEpochMs = item.optLong("actualDepartureEpochMs").takeIf { it > 0L },
                departureFeedbackSent = item.optBoolean("departureFeedbackSent"),
                lateNotified = item.optBoolean("lateNotified")
            )
        }
    }.getOrDefault(emptyMap())
}

private fun saveCommuteCache(context: Context, cache: Map<String, CommuteCacheEntry>) {
    val json = JSONObject()
    cache.forEach { (key, entry) ->
        if (entry.dismissed) return@forEach
        json.put(
            key,
            JSONObject()
                .put("meetingTitle", entry.meetingTitle)
                .put("meetingStartEpochMs", entry.meetingStartEpochMs)
                .put("proposedDepartureEpochMs", entry.proposedDepartureEpochMs)
                .put("originLat", entry.originLat)
                .put("originLng", entry.originLng)
                .put("notified", entry.notified)
                .put("dismissed", entry.dismissed)
                .put("actualDepartureEpochMs", entry.actualDepartureEpochMs)
                .put("departureFeedbackSent", entry.departureFeedbackSent)
                .put("lateNotified", entry.lateNotified)
                .put("plan", planToJson(entry.plan))
        )
    }
    context.getSharedPreferences("cape_context", Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_COMMUTE_CACHE, json.toString())
        .apply()
}

private fun planToJson(plan: dev.rootcause.cape.core.CommutePlan): JSONObject {
    return JSONObject()
        .put("source", plan.source)
        .put("etaMinutes", plan.etaMinutes)
        .put("bufferMinutes", plan.bufferMinutes)
        .put("leaveInMinutes", plan.leaveInMinutes)
        .put("leaveByLocal", plan.leaveByLocal)
        .put("shouldAlert", plan.shouldAlert)
        .put("reason", plan.reason)
        .put("destination", plan.destination)
        .put("mapsUrl", plan.mapsUrl)
        .put("polyline", plan.polyline)
        .put(
            "modes",
            JSONArray(plan.modes.map {
                JSONObject()
                    .put("id", it.id)
                    .put("label", it.label)
                    .put("durationText", it.durationText)
                    .put("distanceText", it.distanceText)
                    .put("leaveByLocal", it.leaveByLocal)
                    .put("arrivalByLocal", it.arrivalByLocal)
            })
        )
        .put(
            "directions",
            JSONArray(plan.directions.map {
                JSONObject()
                    .put("instruction", it.instruction)
                    .put("distanceText", it.distanceText)
                    .put("durationText", it.durationText)
                    .put("travelMode", it.travelMode)
            })
        )
}

private fun parseCachedPlan(json: JSONObject): dev.rootcause.cape.core.CommutePlan {
    val modes = json.optJSONArray("modes") ?: JSONArray()
    val directions = json.optJSONArray("directions") ?: JSONArray()
    return dev.rootcause.cape.core.CommutePlan(
        source = json.optString("source"),
        etaMinutes = json.optInt("etaMinutes"),
        bufferMinutes = json.optInt("bufferMinutes"),
        leaveInMinutes = json.optInt("leaveInMinutes"),
        leaveByLocal = json.optString("leaveByLocal"),
        shouldAlert = json.optBoolean("shouldAlert"),
        reason = json.optString("reason"),
        destination = json.optString("destination").takeIf { it.isNotBlank() && it != "null" },
        mapsUrl = json.optString("mapsUrl").takeIf { it.isNotBlank() && it != "null" },
        polyline = json.optString("polyline").takeIf { it.isNotBlank() && it != "null" },
        modes = List(modes.length()) { index ->
            val item = modes.getJSONObject(index)
            dev.rootcause.cape.core.TravelModePlan(
                id = item.optString("id"),
                label = item.optString("label"),
                durationText = item.optString("durationText"),
                distanceText = item.optString("distanceText"),
                leaveByLocal = item.optString("leaveByLocal"),
                arrivalByLocal = item.optString("arrivalByLocal")
            )
        },
        directions = List(directions.length()) { index ->
            val item = directions.getJSONObject(index)
            dev.rootcause.cape.core.RouteStep(
                instruction = item.optString("instruction"),
                distanceText = item.optString("distanceText"),
                durationText = item.optString("durationText"),
                travelMode = item.optString("travelMode")
            )
        }
    )
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key) else null

private fun distanceBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
    val result = FloatArray(1)
    android.location.Location.distanceBetween(lat1, lng1, lat2, lng2, result)
    return result[0]
}

private fun modeIcon(mode: String): Int {
    return when (mode.lowercase()) {
        "walk", "walking", "pedestrian" -> android.R.drawable.ic_menu_myplaces
        "transit", "bus", "train" -> android.R.drawable.ic_menu_directions
        "bike", "bicycle", "cycle" -> android.R.drawable.ic_menu_compass
        else -> android.R.drawable.ic_menu_directions
    }
}

private fun modeEmoji(mode: String): String {
    return when (mode.lowercase()) {
        "walk", "walking", "pedestrian" -> "🚶"
        "transit", "bus", "train" -> "🚌"
        "bike", "bicycle", "cycle" -> "🚴"
        else -> "🚗"
    }
}

private fun startCapeSyncService(context: Context) {
    val intent = Intent(context, CapeSyncService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun shouldShowReflection(previousLocation: String, snapshot: ContextSnapshot, prefs: android.content.SharedPreferences): Boolean {
    if (wasReflectionShownToday(prefs)) return false
    val leftFocusLocation = (previousLocation == "office" || previousLocation == "college") &&
        snapshot.locationState != "office" &&
        snapshot.locationState != "college"
    val eveningFatigue = (snapshot.hourOfDay ?: 0) >= 18 && snapshot.screenTimeLast2hMinutes > 90
    return leftFocusLocation || eveningFatigue
}

private fun wasReflectionShownToday(prefs: android.content.SharedPreferences): Boolean {
    val today = java.time.LocalDate.now().toString()
    return prefs.getString(KEY_LAST_REFLECTION_DATE, "") == today
}

private fun markReflectionShown(prefs: android.content.SharedPreferences) {
    prefs.edit().putString(KEY_LAST_REFLECTION_DATE, java.time.LocalDate.now().toString()).apply()
}

private fun recordNotificationEvent(context: Context) {
    val prefs = context.getSharedPreferences("cape_context", Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    val cutoff = now - 30 * 60_000L
    val values = (prefs.getString("notification_events", "") ?: "")
        .split(',')
        .mapNotNull { it.toLongOrNull() }
        .filter { it >= cutoff } + now
    prefs.edit().putString("notification_events", values.joinToString(",")).apply()
}

private fun generateDailyPlan(context: Context, snapshot: ContextSnapshot): List<DailyPlanItem> {
    val prefs = context.getSharedPreferences("cape_context", Context.MODE_PRIVATE)
    val role = prefs.getString(KEY_USER_ROLE, "student") ?: "student"
    val start = parseClockTime(prefs.getString(KEY_ROUTINE_START, "09:00") ?: "09:00")
    val end = parseClockTime(prefs.getString(KEY_ROUTINE_END, "16:00") ?: "16:00")
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zone)
    val items = mutableListOf<DailyPlanItem>()
    val routineTitle = if (role == "employee") "Work block" else "College block"
    val routineKind = if (role == "employee") "work" else "college"
    val routineLocation = savedPlaceLabel(context, routineKind) ?: routineKind
    val startEpoch = today.atTime(start).atZone(zone).toInstant().toEpochMilli()
    val endEpoch = today.atTime(end).atZone(zone).toInstant().toEpochMilli()
    if (endEpoch > System.currentTimeMillis() && endEpoch > startEpoch) {
        items.add(planItem(routineTitle, routineLocation, startEpoch, endEpoch, zone))
    }
    val meetingStart = snapshot.nextMeetingStartEpochMs
    if (meetingStart != null && meetingStart >= System.currentTimeMillis()) {
        val title = snapshot.nextMeetingTitle ?: "Pending meeting"
        items.add(planItem(title, snapshot.nextMeetingLocation ?: "calendar", meetingStart, meetingStart + 60 * 60_000L, zone))
    }
    return items.distinctBy { it.id }.sortedBy { it.startEpochMs }
}

private fun planItem(title: String, location: String, startEpochMs: Long, endEpochMs: Long, zone: java.time.ZoneId): DailyPlanItem {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a")
    val startLabel = java.time.Instant.ofEpochMilli(startEpochMs).atZone(zone).format(formatter)
    val endLabel = java.time.Instant.ofEpochMilli(endEpochMs).atZone(zone).format(formatter)
    return DailyPlanItem(
        id = "${startEpochMs}_${title.lowercase().replace(Regex("[^a-z0-9]+"), "_")}",
        title = title,
        location = location,
        startEpochMs = startEpochMs,
        endEpochMs = endEpochMs,
        startLabel = startLabel,
        endLabel = endLabel
    )
}

private fun parseClockTime(value: String): java.time.LocalTime =
    runCatching { java.time.LocalTime.parse(value) }.getOrDefault(java.time.LocalTime.of(9, 0))

private fun addPlanItemsToCalendar(context: Context, items: List<DailyPlanItem>): Int {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) return 0
    val calendarId = findWritableCalendarId(context) ?: return 0
    var added = 0
    for (item in items) {
        if (wasPlanItemAdded(context, item)) continue
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, "CAPE: ${item.title}")
            put(CalendarContract.Events.EVENT_LOCATION, item.location)
            put(CalendarContract.Events.DTSTART, item.startEpochMs)
            put(CalendarContract.Events.DTEND, item.endEpochMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            put(CalendarContract.Events.DESCRIPTION, "Added by CAPE after user confirmation. Automation should prepare 5-10 minutes before this block.")
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        if (uri != null) {
            addCalendarReminder(context, uri)
            markPlanItemAdded(context, item)
            added += 1
        }
    }
    return added
}

private fun addCalendarReminder(context: Context, eventUri: Uri) {
    val eventId = eventUri.lastPathSegment?.toLongOrNull() ?: return
    val values = ContentValues().apply {
        put(CalendarContract.Reminders.EVENT_ID, eventId)
        put(CalendarContract.Reminders.MINUTES, 10)
        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
    }
    runCatching { context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values) }
}

private fun findWritableCalendarId(context: Context): Long? {
    val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
    context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
        val accessIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
        while (cursor.moveToNext()) {
            if (cursor.getInt(accessIndex) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                return cursor.getLong(idIndex)
            }
        }
    }
    return null
}

private fun wasPlanItemAdded(context: Context, item: DailyPlanItem): Boolean =
    context.getSharedPreferences("cape_context", Context.MODE_PRIVATE).getBoolean("plan_added_${item.id}", false)

private fun markPlanItemAdded(context: Context, item: DailyPlanItem) {
    context.getSharedPreferences("cape_context", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("plan_added_${item.id}", true)
        .apply()
}

private fun loadTodoList(context: Context): TodoList {
    val today = java.time.LocalDate.now().toString()
    val raw = context.getSharedPreferences("cape_context", Context.MODE_PRIVATE)
        .getString(KEY_DAY_TODOS, "{}") ?: "{}"
    return runCatching {
        val root = JSONObject(raw)
        val date = root.optString("date", today)
        val items = root.optJSONArray("items") ?: JSONArray()
        TodoList(
            date = date,
            items = List(items.length()) { index ->
                val item = items.getJSONObject(index)
                TodoItem(
                    id = item.optString("id"),
                    title = item.optString("title"),
                    dueAt = item.optLong("dueAt").takeIf { it > 0L },
                    completed = item.optBoolean("completed"),
                    createdAt = item.optLong("createdAt"),
                    updatedAt = item.optLong("updatedAt")
                )
            }.filter { it.title.isNotBlank() },
            updatedAt = root.optLong("updatedAt", System.currentTimeMillis())
        )
    }.getOrDefault(TodoList(today, emptyList(), System.currentTimeMillis()))
        .let { ensureTodayTodoList(it) }
}

private fun ensureTodayTodoList(list: TodoList): TodoList {
    val today = java.time.LocalDate.now().toString()
    return if (list.date == today) list else TodoList(today, emptyList(), System.currentTimeMillis())
}

private fun saveTodoList(context: Context, list: TodoList) {
    val json = JSONObject()
        .put("date", list.date)
        .put("updatedAt", list.updatedAt)
        .put(
            "items",
            JSONArray(list.items.map { item ->
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("dueAt", item.dueAt)
                    .put("completed", item.completed)
                    .put("createdAt", item.createdAt)
                    .put("updatedAt", item.updatedAt)
            })
        )
    context.getSharedPreferences("cape_context", Context.MODE_PRIVATE).edit()
        .putString(KEY_DAY_TODOS, json.toString())
        .apply()
}

private fun todoPressure(list: TodoList): Triple<Int, Int, Int> {
    val now = System.currentTimeMillis()
    var pending = 0
    var urgent = 0
    var overdue = 0
    list.items.forEach { item ->
        if (item.completed) return@forEach
        pending += 1
        val due = item.dueAt ?: return@forEach
        if (due < now) overdue += 1
        if (due <= now + 3 * 60 * 60_000L) urgent += 1
    }
    return Triple(pending, urgent, overdue)
}

private fun recordTodoEdit(context: Context, list: TodoList, note: String) {
    val now = System.currentTimeMillis()
    val hour = java.time.LocalDateTime.now().hour
    val prefs = context.getSharedPreferences("cape_context", Context.MODE_PRIVATE)
    val history = (prefs.getString(KEY_TODO_EDIT_HOURS, "") ?: "")
        .split(',')
        .mapNotNull { it.toIntOrNull() }
        .filter { it in 0..23 }
        .takeLast(19) + hour
    val learned = history.groupingBy { it }.eachCount()
        .entries
        .filter { it.value >= 2 }
        .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
        .map { it.key }
        .take(3)
    prefs.edit()
        .putString(KEY_TODO_EDIT_HOURS, history.joinToString(","))
        .putString(KEY_LEARNED_TODO_HOURS, learned.joinToString(","))
        .putLong(KEY_LAST_TODO_EDIT_AT, now)
        .apply()
    val (pending, urgent, overdue) = todoPressure(list)
    Thread {
        runCatching {
            GatewayClient().sendTodoUpdate(
                pending = pending,
                urgent = urgent,
                overdue = overdue,
                note = note,
                timestamp = java.time.OffsetDateTime.now().toString()
            )
        }
    }.start()
}

private fun parseTodoDueToday(value: String): Long? {
    val time = runCatching { java.time.LocalTime.parse(value.trim()) }.getOrNull() ?: return null
    return java.time.LocalDate.now()
        .atTime(time)
        .atZone(java.time.ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

private fun todoMetaLabel(item: TodoItem): String {
    val due = item.dueAt?.let {
        java.time.Instant.ofEpochMilli(it)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
    } ?: "no due time"
    return "${if (item.completed) "done" else "pending"} - due $due"
}

private fun parseTodoPromptTimes(value: String): List<String> =
    value.split(',', ';')
        .map { it.trim() }
        .mapNotNull { raw -> runCatching { java.time.LocalTime.parse(raw) }.getOrNull()?.let { "%02d:%02d".format(it.hour, it.minute) } }
        .distinct()
        .take(4)

private fun loadTodoPromptTimes(context: Context): List<String> {
    val raw = context.getSharedPreferences("cape_context", Context.MODE_PRIVATE).getString(KEY_TODO_PROMPT_TIMES, "09:00") ?: "09:00"
    return parseTodoPromptTimes(raw).ifEmpty { listOf("09:00") }
}

private fun saveTodoPromptTimes(context: Context, times: List<String>) {
    context.getSharedPreferences("cape_context", Context.MODE_PRIVATE).edit()
        .putString(KEY_TODO_PROMPT_TIMES, times.joinToString(","))
        .apply()
}

private fun loadLearnedTodoHours(context: Context): List<Int> =
    (context.getSharedPreferences("cape_context", Context.MODE_PRIVATE).getString(KEY_LEARNED_TODO_HOURS, "") ?: "")
        .split(',')
        .mapNotNull { it.toIntOrNull() }
        .filter { it in 0..23 }
        .distinct()

private fun shouldPromptForTodoUpdate(context: Context, snapshot: ContextSnapshot): Boolean {
    val prefs = context.getSharedPreferences("cape_context", Context.MODE_PRIVATE)
    val today = java.time.LocalDate.now().toString()
    val hour = snapshot.hourOfDay ?: java.time.LocalDateTime.now().hour
    val minute = java.time.LocalDateTime.now().minute
    val clock = "%02d:%02d".format(hour, minute)
    val configured = loadTodoPromptTimes(context).any { time ->
        val parsed = java.time.LocalTime.parse(time)
        parsed.hour == hour && kotlin.math.abs(parsed.minute - minute) <= 10
    }
    val learned = loadLearnedTodoHours(context).contains(hour)
    val morning = hour in 6..10
    if (!configured && !learned && !morning) return false
    val key = "${today}_$hour"
    if (prefs.getString(KEY_LAST_TODO_PROMPT_KEY, "") == key) return false
    return true
}

private fun markTodoPromptSeen(context: Context) {
    val hour = java.time.LocalDateTime.now().hour
    val today = java.time.LocalDate.now().toString()
    context.getSharedPreferences("cape_context", Context.MODE_PRIVATE).edit()
        .putString(KEY_LAST_TODO_PROMPT_KEY, "${today}_$hour")
        .apply()
}

private fun maybeShowTodoPromptNotification(context: Context, snapshot: ContextSnapshot) {
    if (!shouldPromptForTodoUpdate(context, snapshot)) return
    markTodoPromptSeen(context)
    val launch = PendingIntent.getActivity(
        context,
        4100,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = NotificationCompat.Builder(context, COMMUTE_CHANNEL)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Update today's todo list?")
        .setContentText("CAPE can use your todo pressure for safer decisions.")
        .setContentIntent(launch)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
    runCatching { NotificationManagerCompat.from(context).notify(2400, notification) }
}

private fun recordDecisionApproval(context: Context, decision: CapeDecision, signal: String) {
    val prefs = context.getSharedPreferences("cape_context", Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_DECISION_APPROVAL_EVENTS, "[]") ?: "[]"
    val events = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    events.put(
        JSONObject()
            .put("timestamp", java.time.OffsetDateTime.now().toString())
            .put("packId", decision.packId)
            .put("signal", signal)
            .put("confidence", decision.confidence)
            .put("actions", JSONArray(decision.actions))
    )
    val trimmed = JSONArray()
    val start = (events.length() - 50).coerceAtLeast(0)
    for (index in start until events.length()) trimmed.put(events.get(index))
    prefs.edit().putString(KEY_DECISION_APPROVAL_EVENTS, trimmed.toString()).apply()
}

private fun savedPlaceLabel(context: Context, kind: String): String? {
    val raw = context.getSharedPreferences("cape_context", Context.MODE_PRIVATE).getString("saved_places", null) ?: return null
    return runCatching {
        val array = JSONArray(raw)
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            if (item.optString("kind") == kind) {
                return@runCatching item.optString("label")
                    .takeIf { it.isNotBlank() }
                    ?: item.optString("query").takeIf { it.isNotBlank() }
            }
        }
        null
    }.getOrNull()
}

private fun wallpaperActionForDecision(decision: CapeDecision): String? {
    if (decision.actions.contains("WALLPAPER_COMMUTE")) return "WALLPAPER_COMMUTE"
    if (decision.actions.contains("WALLPAPER_FOCUS")) return "WALLPAPER_FOCUS"
    if (decision.actions.contains("WALLPAPER_RELAX")) return "WALLPAPER_RELAX"
    if (decision.actions.contains("WALLPAPER_RESET")) return "WALLPAPER_RESET"
    return when (decision.packId) {
        "commute_alert" -> "WALLPAPER_COMMUTE"
        "office_focus_high_stress" -> "WALLPAPER_FOCUS"
        "home_evening" -> "WALLPAPER_RELAX"
        "recovery_mode" -> if (decision.type == "APPLY_PACK") "WALLPAPER_RELAX" else null
        "observe_only" -> "WALLPAPER_RESET"
        else -> null
    }
}

private fun openNotificationPolicyAccessSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun openWriteSettingsPanel(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun openUsageAccessSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun readPermissionState(context: Context): PermissionState {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
    val hasUsageStats = !usage.queryUsageStats(
        android.app.usage.UsageStatsManager.INTERVAL_BEST,
        System.currentTimeMillis() - 5 * 60_000L,
        System.currentTimeMillis()
    ).isNullOrEmpty()
    return PermissionState(
        location = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        calendar = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED,
        calendarWrite = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED,
        notifications = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        notificationPolicyAccess = notificationManager.isNotificationPolicyAccessGranted,
        writeSettings = Settings.System.canWrite(context),
        usageStats = hasUsageStats
    )
}

private data class UserProfile(val name: String)

private data class DailyPlanItem(
    val id: String,
    val title: String,
    val location: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val startLabel: String,
    val endLabel: String
)

private data class TodoList(
    val date: String,
    val items: List<TodoItem>,
    val updatedAt: Long
)

private data class TodoItem(
    val id: String,
    val title: String,
    val dueAt: Long?,
    val completed: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

private data class PermissionState(
    val location: Boolean,
    val calendar: Boolean,
    val calendarWrite: Boolean,
    val notifications: Boolean,
    val notificationPolicyAccess: Boolean,
    val writeSettings: Boolean,
    val usageStats: Boolean
)

private enum class HomeSection(val label: String) {
    Dashboard("Home"),
    Commute("Commute"),
    Profile("Profile"),
    Plan("Plan")
}

private const val COMMUTE_CHANNEL = "cape_commute_alerts"
private const val SERVICE_SYNC_INTERVAL_MS = 12 * 60_000L
private const val ACTION_PAUSE_CAPE_SERVICE = "dev.rootcause.cape.PAUSE_SERVICE"
private const val ACTION_STOP_CAPE_SERVICE = "dev.rootcause.cape.STOP_SERVICE"
private const val KEY_COMMUTE_CACHE = "commute_plan_cache"
private const val KEY_LAST_REFLECTION_DATE = "last_reflection_date"
private const val KEY_LAST_DYNAMIC_WALLPAPER = "last_dynamic_wallpaper"
private const val KEY_USER_ROLE = "user_role"
private const val KEY_ROUTINE_START = "routine_start"
private const val KEY_ROUTINE_END = "routine_end"
private const val KEY_SERVICE_PAUSED_UNTIL = "service_paused_until"
private const val KEY_DAY_TODOS = "day_todos"
private const val KEY_TODO_PROMPT_TIMES = "todo_prompt_times"
private const val KEY_LAST_TODO_PROMPT_KEY = "last_todo_prompt_key"
private const val KEY_TODO_EDIT_HOURS = "todo_edit_hours"
private const val KEY_LEARNED_TODO_HOURS = "learned_todo_update_hours"
private const val KEY_LAST_TODO_EDIT_AT = "last_todo_edit_at"
private const val KEY_DECISION_APPROVAL_EVENTS = "decision_approval_events"
