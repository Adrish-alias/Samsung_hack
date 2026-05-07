package dev.rootcause.cape

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.TimePickerDialog
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Room
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
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
import dev.rootcause.cape.core.StressResult
import dev.rootcause.cape.execution.PackExecutor
import dev.rootcause.cape.gateway.GatewayClient
import dev.rootcause.cape.sensing.ContextCollector
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject



// ─────────────────────────────────────────────────────────────────────────────
// Design Tokens — Trust Blue / Glassmorphism / Material 3
// Source: Stitch project "CAPE Adaptive Assistant UI" (id: 16936172592145115519)
// ─────────────────────────────────────────────────────────────────────────────

// Primary — Trust Blue
private val CapePrimary           = Color(0xFF1A56DB)
private val CapePrimaryDark       = Color(0xFF003FB1)
private val CapeOnPrimary         = Color(0xFFFFFFFF)
private val CapePrimaryContainer  = Color(0xFFDBE1FF)
private val CapeOnPrimaryContainer = Color(0xFF00174D)

// Secondary — Calm Slate
private val CapeSecondary         = Color(0xFF515F74)
private val CapeOnSecondary       = Color(0xFFFFFFFF)
private val CapeSecondaryContainer = Color(0xFFD5E3FC)

// Tertiary
private val CapeTertiary          = Color(0xFF00544C)
private val CapeTertiaryContainer = Color(0xFF006E65)

// Background & Surface
private val CapeBg                = Color(0xFFF7F9FB)
private val CapeGlass             = Color(0xBFFFFFFF)   // 75% white — primary glass card
private val CapeGlassStrong       = Color(0xE6F2F4F6)   // elevated glass surface
private val CapeGlassBorder       = Color(0x14000000)   // 8% black border
private val GlassWhite            = Color(0xFFFFFFFF)   // pure surface

// Text
private val CapeText              = Color(0xFF191C1E)
private val CapeMuted             = Color(0xFF434654)
private val CapeOutline           = Color(0xFF737686)
private val CapeOutlineVariant    = Color(0xFFC3C5D7)

// Semantic Stress States
private val StressLow             = Color(0xFF22C55E)
private val StressMedium          = Color(0xFFF59E0B)
private val StressHigh            = Color(0xFFEF4444)
private val StressLowBg           = Color(0xFFDCFCE7)
private val StressMediumBg        = Color(0xFFFEF9C3)
private val StressHighBg          = Color(0xFFFFE4E6)

// Accent helpers
private val CapeAccent            = Color(0xFF1A56DB)   // alias → Trust Blue
private val CapeBlue              = Color(0xFF2F80ED)
private val CapeGreen             = Color(0xFF22C55E)
private val CapeOrange            = Color(0xFFF97316)
private val CapeWarning           = Color(0xFFF59E0B)
private val CapeDanger            = Color(0xFFEF4444)

// Legacy compat aliases (keep so helper fns compile unchanged)
private val Glass                 = GlassWhite
private val GlassStrong           = CapeGlassStrong


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
    private var hasScheduledSync = false
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
                maybeShowContextModePrompt(applicationContext, snapshot)
                maybeShowTodoPromptNotification(applicationContext, snapshot)
            }.start()
            hasScheduledSync = true
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
        if (!hasScheduledSync) {
            handler.removeCallbacks(syncRunnable)
            handler.post(syncRunnable)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(syncRunnable)
        hasScheduledSync = false
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

class CapeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_APPLY_MODE -> {
                val packId = intent.getStringExtra(EXTRA_PACK_ID) ?: return
                val actions = intent.getStringArrayListExtra(EXTRA_ACTIONS).orEmpty()
                val decision = CapeDecision(
                    type = "APPLY_PACK",
                    packId = packId,
                    stress = StressResult(0, "USER_APPROVED", emptyList()),
                    actions = actions,
                    blockedByPermission = emptyList(),
                    explanation = "Applied from CAPE notification.",
                    confidence = 1.0
                )
                val status = PackExecutor(context.applicationContext).apply(decision)
                recordNotificationDecision(context, packId, "accepted", status, actions)
                dismissNotification(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
            }
            ACTION_REJECT_MODE -> {
                val packId = intent.getStringExtra(EXTRA_PACK_ID) ?: "unknown"
                recordNotificationDecision(context, packId, "rejected", "Rejected from CAPE notification.")
                dismissNotification(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
            }
            ACTION_TODO_QUICK_ADD -> {
                val reply = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_TODO_REMOTE_INPUT)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                if (reply.isNotBlank()) {
                    val list = loadTodoList(context)
                    val now = System.currentTimeMillis()
                    val item = TodoItem(
                        id = "todo_$now",
                        title = reply,
                        startAt = now,
                        endAt = null,
                        completed = false,
                        createdAt = now,
                        updatedAt = now
                    )
                    val updated = ensureTodayTodoList(list).copy(items = ensureTodayTodoList(list).items + item, updatedAt = now)
                    saveTodoList(context, updated)
                    recordTodoEdit(context, updated, "quick_added:$reply")
                    markTodoPromptSeen(context)
                }
                dismissNotification(context, TODO_PROMPT_NOTIFICATION_ID)
            }
            ACTION_TODO_DISMISS -> {
                markTodoPromptSeen(context)
                dismissNotification(context, TODO_PROMPT_NOTIFICATION_ID)
            }
        }
    }
}

@Composable
fun CapeApp(onRequestRuntimePermissions: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cape_context", Context.MODE_PRIVATE) }
    var isOnboarded by remember { mutableStateOf(prefs.getBoolean("onboarded", false)) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary          = CapePrimary,
            onPrimary        = CapeOnPrimary,
            primaryContainer = CapePrimaryContainer,
            secondary        = CapeSecondary,
            onSecondary      = CapeOnSecondary,
            secondaryContainer = CapeSecondaryContainer,
            tertiary         = CapeTertiary,
            tertiaryContainer = CapeTertiaryContainer,
            background       = CapeBg,
            surface          = GlassWhite,
            onBackground     = CapeText,
            onSurface        = CapeText,
            outline          = CapeOutline,
            outlineVariant   = CapeOutlineVariant
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = CapeBg) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFF0F4FF), Color(0xFFECF0FB), CapeBg)
                        )
                    )
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

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(280.dp).background(
                Brush.verticalGradient(listOf(CapePrimaryDark, CapePrimary, Color(0xFF3D72E8)))
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("C", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.height(14.dp))
                Text("CAPE", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Text("Your stress-aware adaptive assistant", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(100.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text("Privacy-first · Data stays on device", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 236.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = GlassWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Set up your profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Places are optional — add later. Permissions help CAPE detect meetings and plan commutes.",
                        color = CapeMuted, fontSize = 13.sp
                    )
                    StyledTextField(value = name, onValueChange = { name = it }, label = "Your name")
                    StyledTextField(value = home, onValueChange = { home = it }, label = "Home address (optional)")
                    StyledTextField(value = work, onValueChange = { work = it }, label = "Work address (optional)")
                    StyledTextField(value = college, onValueChange = { college = it }, label = "College address (optional)")
                    OutlinedButton(onClick = onRequestRuntimePermissions, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Grant Permissions", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            status = "Resolving places…"
                            Thread {
                                val places = listOf("home" to home, "work" to work, "college" to college)
                                    .filter { it.second.isNotBlank() }
                                    .mapNotNull { (kind, query) ->
                                        runCatching {
                                            val place = GatewayClient().geocodePlace(query)
                                            place.copy(kind = kind, label = kind.replaceFirstChar { it.titlecase() }, radiusMeters = fixedPlaceRadius(kind))
                                        }.getOrNull()
                                    }
                                (context as? ComponentActivity)?.runOnUiThread { onComplete(UserProfile(name.ifBlank { "User" }), places) }
                            }.start()
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CapePrimary)
                    ) { Text("Get Started", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                    if (status.isNotBlank()) Text(status, color = CapeMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun StyledTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 13.sp) }, shape = RoundedCornerShape(12.dp), singleLine = true,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CapePrimary, unfocusedBorderColor = CapeOutlineVariant,
            focusedLabelColor = CapePrimary, cursorColor = CapePrimary
        )
    )
}




@OptIn(ExperimentalMaterial3Api::class)
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
                maybeShowContextModePrompt(context, fresh.snapshot)
                maybeShowTodoPromptNotification(context, fresh.snapshot)
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
        syncOnce()
    }

    Scaffold(
        containerColor = CapeBg,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        val greeting = when ((java.time.LocalTime.now().hour)) {
                            in 5..11 -> "Good morning"
                            in 12..16 -> "Good afternoon"
                            else -> "Good evening"
                        }
                        Text(greeting, style = MaterialTheme.typography.labelMedium, color = CapeMuted)
                        Text("CAPE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = CapeText)
                    }
                },
                actions = {
                    // Sync status pill
                    val synced = syncStatus.startsWith("Synced")
                    Box(
                        modifier = Modifier
                            .background(
                                if (synced) StressLowBg else CapeSecondaryContainer,
                                RoundedCornerShape(100.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (synced) "● Live" else "● Syncing",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (synced) StressLow else CapeSecondary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GlassWhite,
                    scrolledContainerColor = GlassWhite
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = GlassWhite,
                contentColor = CapePrimary,
                tonalElevation = 0.dp
            ) {
                data class NavItem(val section: HomeSection, val icon: ImageVector, val label: String)
                val items = listOf(
                    NavItem(HomeSection.Dashboard, Icons.Outlined.Home, "Home"),
                    NavItem(HomeSection.Commute, Icons.Outlined.DirectionsCar, "Commute"),
                    NavItem(HomeSection.Plan, Icons.Outlined.DateRange, "Plan"),
                    NavItem(HomeSection.Profile, Icons.Outlined.Person, "Profile")
                )
                items.forEach { item ->
                    NavigationBarItem(
                        selected = selectedSection == item.section,
                        onClick = { selectedSection = item.section },
                        icon = { Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(22.dp)) },
                        label = { Text(item.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CapePrimary,
                            selectedTextColor = CapePrimary,
                            unselectedIconColor = CapeMuted,
                            unselectedTextColor = CapeMuted,
                            indicatorColor = CapePrimaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                HomeSection.Plan -> TodoSection(snapshot = collection.snapshot)
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApprovalDialog(
    decision: CapeDecision,
    onDecision: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onDecision(false) },
        sheetState = sheetState,
        containerColor = GlassWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Handle
            Box(modifier = Modifier.width(40.dp).height(4.dp).background(CapeOutlineVariant, RoundedCornerShape(100.dp)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = CapePrimary, modifier = Modifier.size(22.dp))
                Text("Apply CAPE Decision?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            // Pack badge
            Box(
                modifier = Modifier.background(CapePrimaryContainer, RoundedCornerShape(100.dp)).padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(decision.packId.ifBlank { "no pack" }, color = CapePrimaryDark, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            // Confidence
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Confidence", color = CapeMuted, fontSize = 13.sp)
                    Text("%.0f%%".format(decision.confidence * 100), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                LinearProgressIndicator(
                    progress = { decision.confidence.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(100.dp)),
                    color = CapePrimary,
                    trackColor = CapePrimaryContainer
                )
            }
            // Actions
            val actions = decision.actions.ifEmpty { decision.suggestedActions }
            if (actions.isNotEmpty()) {
                Text("Actions", color = CapeMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.take(4).forEach { action ->
                        Box(
                            modifier = Modifier.background(CapePrimaryContainer, RoundedCornerShape(100.dp)).padding(horizontal = 10.dp, vertical = 5.dp)
                        ) { Text(action.replace("_", " "), fontSize = 12.sp, color = CapePrimaryDark) }
                    }
                }
            }
            HorizontalDivider(color = CapeOutlineVariant, thickness = 0.5.dp)
            Text(
                "CAPE will apply these changes to your device now. You can always reset from the Pack Execution card.",
                color = CapeMuted, fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onDecision(false) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("No, Skip", fontWeight = FontWeight.SemiBold) }
                Button(
                    onClick = { onDecision(true) },
                    modifier = Modifier.weight(2f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CapePrimary)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Yes, Apply", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GlassWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Handle
            Box(
                modifier = Modifier
                    .width(40.dp).height(4.dp)
                    .background(CapeOutlineVariant, RoundedCornerShape(100.dp))
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(2.dp))

            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(CapePrimaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🌙", fontSize = 22.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Quick Reflection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Help CAPE learn what today felt like.", color = CapeMuted, fontSize = 13.sp)
                }
            }

            HorizontalDivider(color = CapeOutlineVariant, thickness = 0.5.dp)

            // Tag chips (wrap layout)
            Text("How was today?", color = CapeMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(options.take(3), options.drop(3)).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { option ->
                            val isSelected = selected.contains(option)
                            Box(
                                modifier = Modifier
                                    .clickable { selected = toggleSelected(selected, option) }
                                    .background(
                                        if (isSelected) CapePrimary else CapeSecondaryContainer,
                                        RoundedCornerShape(100.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) CapePrimary.copy(alpha = 0.3f) else CapeOutlineVariant,
                                        RoundedCornerShape(100.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    option,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else CapeText
                                )
                            }
                        }
                    }
                }
            }

            // Note field
            StyledTextField(value = note, onValueChange = { note = it }, label = "Optional note for CAPE…")

            // Status
            if (status.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StressLowBg, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(status, color = CapeTertiary, fontSize = 13.sp)
                }
            }

            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Later", fontWeight = FontWeight.SemiBold) }
                Button(
                    onClick = { onSubmit(selected.toList(), note) },
                    modifier = Modifier.weight(2f).height(52.dp),
                    enabled = selected.isNotEmpty() || note.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CapePrimary)
                ) {
                    Text("Save Reflection", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}


private fun toggleSelected(current: Set<String>, value: String): Set<String> =
    if (current.contains(value)) current - value else current + value


// SectionTabs removed — navigation now provided by BottomNavigationBar in HomeShell




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
    val context = LocalContext.current

    // ── 1. Stress Gauge Card ─────────────────────────────────────────────────
    GlassCard {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StressGauge(score = decision.stress.score, level = decision.stress.level)
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StressLevelChip(decision.stress.level)
        }
        if (decision.explanation.isNotBlank()) {
            Text(
                decision.explanation,
                color = CapeMuted,
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // ── 2. Context Mode Banner ────────────────────────────────────────────────
    ContextModeBanner(snapshot.locationState)

    // ── 3. Metric Grid 2×2 ───────────────────────────────────────────────────
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MetricTile(
            icon = Icons.Outlined.Bedtime,
            label = "Sleep Debt",
            value = "${snapshot.sleepDebtMinutes} min",
            tint = CapeBlue,
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            icon = Icons.Filled.Schedule,
            label = "Meetings",
            value = snapshot.meetingLoadToday.toString(),
            tint = CapeTertiary,
            modifier = Modifier.weight(1f)
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MetricTile(
            icon = Icons.Filled.TrendingUp,
            label = "Todo Pressure",
            value = "${snapshot.todoPressureScore}/100",
            tint = if ((snapshot.todoPressureScore ?: 0) > 60) StressHigh else CapeGreen,
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            icon = Icons.Filled.Warning,
            label = "Overdue",
            value = snapshot.todoOverdueCount.toString(),
            tint = if ((snapshot.todoOverdueCount ?: 0) > 0) CapeOrange else CapeGreen,
            modifier = Modifier.weight(1f)
        )
    }

    // ── 4. Today's Context ───────────────────────────────────────────────────
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = CapePrimary, modifier = Modifier.size(18.dp))
            Text("Today's Context", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = CapeOutlineVariant, thickness = 0.5.dp)
        MetricRow("Location", readableLocation(snapshot))
        MetricRow("Next meeting", snapshot.nextMeetingTitle ?: "No meeting found")
        MetricRow("Destination", snapshot.nextMeetingLocation ?: "No destination")
        MetricRow("Starts in", snapshot.nextMeetingMinutes?.let { "$it min" } ?: "none")
    }

    // ── 5. Permissions Card (compact status) ─────────────────────────────────
    var permExpanded by remember { mutableStateOf(false) }
    val permissions = readPermissionState(context)
    val allGranted = permissions.location && permissions.calendar && permissions.notifications
    GlassCard(container = if (allGranted) StressLowBg.copy(alpha = 0.3f) else StressHighBg.copy(alpha = 0.3f)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { permExpanded = !permExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Shield, contentDescription = null,
                    tint = if (allGranted) StressLow else StressHigh, modifier = Modifier.size(18.dp))
                Text("Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .background(if (allGranted) StressLowBg else StressHighBg, RoundedCornerShape(100.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (allGranted) "All Granted" else "Action Needed",
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = if (allGranted) StressLow else StressHigh
                    )
                }
                Text(if (permExpanded) "▲" else "▼", color = CapeMuted, fontSize = 12.sp)
            }
        }
        if (permExpanded) {
            HorizontalDivider(color = CapeOutlineVariant, thickness = 0.5.dp)
            PermissionStatusRow("Location", permissions.location)
            PermissionStatusRow("Calendar read", permissions.calendar)
            PermissionStatusRow("Calendar write", permissions.calendarWrite)
            PermissionStatusRow("Notifications", permissions.notifications)
            PermissionStatusRow("DND access", permissions.notificationPolicyAccess)
            PermissionStatusRow("Brightness control", permissions.writeSettings)
            PermissionStatusRow("Usage access", permissions.usageStats)
            Button(
                onClick = onRequestRuntimePermissions,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CapePrimary)
            ) { Text("Update Permissions", fontWeight = FontWeight.SemiBold) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onRequestDndAccess, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("DND") }
                OutlinedButton(onClick = onRequestWriteSettings, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("Brightness") }
                OutlinedButton(onClick = onRequestUsageAccess, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("Usage") }
            }
        }
    }

    // ── 6. Pack Execution Card ───────────────────────────────────────────────
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = CapePrimary, modifier = Modifier.size(18.dp))
            Text("CAPE Decision", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .background(CapePrimaryContainer, RoundedCornerShape(100.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(decision.type.replace("_", " "), fontSize = 11.sp, color = CapePrimaryDark, fontWeight = FontWeight.SemiBold)
            }
        }
        HorizontalDivider(color = CapeOutlineVariant, thickness = 0.5.dp)
        MetricRow("Pack", decision.packId.ifBlank { "none" })
        // Confidence bar
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Confidence", color = CapeMuted, fontSize = 13.sp)
                Text("%.0f%%".format(decision.confidence * 100), color = CapeText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            LinearProgressIndicator(
                progress = { decision.confidence.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(100.dp)),
                color = CapePrimary,
                trackColor = CapePrimaryContainer
            )
        }
        val actions = decision.actions.ifEmpty { decision.suggestedActions }
        if (actions.isNotEmpty()) {
            Text("Actions", color = CapeMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                actions.take(3).forEach { action ->
                    Box(
                        modifier = Modifier
                            .background(CapePrimaryContainer, RoundedCornerShape(100.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(action.replace("_", " "), fontSize = 11.sp, color = CapePrimaryDark)
                    }
                }
                if (actions.size > 3) Text("+ ${actions.size - 3} more", color = CapeMuted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        Text(
            "CAPE always asks before applying changes to your device.",
            color = CapeMuted, fontSize = 12.sp
        )
        Button(
            onClick = onApplyPack,
            enabled = decision.type == "APPLY_PACK" || decision.type == "SUGGEST_PACK",
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CapePrimary)
        ) {
            Text(
                if (decision.type == "SUGGEST_PACK") "Apply Suggested Pack" else "Apply Pack",
                fontWeight = FontWeight.SemiBold
            )
        }
        if (executionStatus.isNotBlank()) {
            Box(modifier = Modifier.fillMaxWidth().background(StressLowBg, RoundedCornerShape(8.dp)).padding(10.dp)) {
                Text(executionStatus, color = CapeTertiary, fontSize = 13.sp)
            }
        }
    }

    // ── 7. OpenClaw / Intelligence ───────────────────────────────────────────
    OpenClawSection(decision)

    // ── 8. Wallpaper Demo ────────────────────────────────────────────────────
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Wallpaper Demo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text("Manually test each wallpaper mode CAPE applies.", color = CapeMuted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            WallpaperDemoButton("Focus", CapePrimary, Modifier.weight(1f)) { onApplyDemoWallpaper("WALLPAPER_FOCUS") }
            WallpaperDemoButton("Relax", CapeGreen, Modifier.weight(1f)) { onApplyDemoWallpaper("WALLPAPER_RELAX") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            WallpaperDemoButton("Commute", CapeBlue, Modifier.weight(1f)) { onApplyDemoWallpaper("WALLPAPER_COMMUTE") }
            OutlinedButton(onClick = { onApplyDemoWallpaper("WALLPAPER_RESET") }, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(10.dp)) {
                Text("Reset", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun WallpaperDemoButton(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) { Text(label, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun PermissionStatusRow(label: String, granted: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = CapeMuted, fontSize = 13.sp)
        Box(
            modifier = Modifier
                .background(if (granted) StressLowBg else StressHighBg, RoundedCornerShape(100.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(if (granted) "✓ Granted" else "✗ Missing", fontSize = 11.sp,
                color = if (granted) StressLow else StressHigh, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StressLevelChip(level: String) {
    val (bg, fg) = when (level.lowercase()) {
        "low"      -> StressLowBg to StressLow
        "medium"   -> StressMediumBg to StressMedium
        "high"     -> StressHighBg to StressHigh
        "critical" -> StressHighBg to StressHigh
        else       -> CapeSecondaryContainer to CapeSecondary
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(100.dp))
            .padding(horizontal = 18.dp, vertical = 7.dp)
    ) {
        Text(
            "● ${level.uppercase()} STRESS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun ContextModeBanner(locationState: String) {
    val (icon, label, bg, fg) = when (locationState.lowercase()) {
        "office", "college" -> listOf(Icons.Filled.TrendingUp, "Focus Mode Active", CapePrimaryContainer, CapePrimary)
        "home", "relaxing"  -> listOf(Icons.Outlined.Home, "Relax Mode Active", StressLowBg, StressLow)
        "commuting"         -> listOf(Icons.Outlined.DirectionsCar, "Commute Mode Active", CapeSecondaryContainer, CapeSecondary)
        else                -> return
    }
    @Suppress("UNCHECKED_CAST")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg as Color, RoundedCornerShape(12.dp))
            .border(1.dp, (fg as Color).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon as ImageVector, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(label as String, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Box(modifier = Modifier.background(fg.copy(alpha = 0.12f), RoundedCornerShape(100.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text("Active", color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetricTile(icon: ImageVector, label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(GlassWhite.copy(0.95f), CapeGlass)), RoundedCornerShape(14.dp))
                .border(1.dp, CapeGlassBorder, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = CapeText)
            Text(label, color = CapeMuted, fontSize = 12.sp)
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
    val context = LocalContext.current

    // ── ETA Banner (NEW — from Stitch Commute Intelligence screen) ────────────
    if (hasPlan && plan != null) {
        val isLate = plan.leaveInMinutes <= 0
        val bannerBg = if (isLate) StressHighBg else CapePrimaryContainer
        val bannerFg = if (isLate) StressHigh else CapePrimary
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bannerBg, RoundedCornerShape(16.dp))
                .border(1.dp, bannerFg.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (isLate) "Leave now — running late!" else "Leave in ${plan.leaveInMinutes} min",
                    color = bannerFg,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp
                )
                plan.destination?.let {
                    Text("→ $it", color = bannerFg.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (plan.etaMinutes > 0) {
                        Column {
                            Text("ETA", color = bannerFg.copy(alpha = 0.6f), fontSize = 11.sp)
                            Text("${plan.etaMinutes} min", color = bannerFg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    if (plan.leaveByLocal.isNotBlank()) {
                        Column {
                            Text("Depart by", color = bannerFg.copy(alpha = 0.6f), fontSize = 11.sp)
                            Text(plan.leaveByLocal, color = bannerFg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    } else {
        // No commute plan — informative empty state
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(CapePrimaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = CapePrimary, modifier = Modifier.size(22.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("No commute planned", fontWeight = FontWeight.Bold, color = CapeText)
                    Text(
                        "CAPE will calculate a route when a calendar meeting with a location is detected.",
                        color = CapeMuted, fontSize = 12.sp
                    )
                }
            }
        }
    }

    // ── Commute Info + Actions ────────────────────────────────────────────────
    if (hasPlan && plan != null) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = CapePrimary, modifier = Modifier.size(18.dp))
                Text("Commute Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.background(CapePrimaryContainer, RoundedCornerShape(100.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
                ) { Text(plan.source.take(20), fontSize = 10.sp, color = CapePrimaryDark) }
            }
            HorizontalDivider(color = CapeOutlineVariant, thickness = 0.5.dp)
            MetricRow("Destination", plan.destination ?: "Calendar destination")
            MetricRow("Reason", plan.reason.take(80).ifBlank { "Live route calculation" })
            if (plan.leaveInMinutes <= 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(StressHighBg, RoundedCornerShape(8.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = StressHigh, modifier = Modifier.size(16.dp))
                    Text("Leave immediately — departure time has passed!", color = StressHigh, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
            if (meetingKey != null) {
                OutlinedButton(onClick = { onCloseRoute(meetingKey) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Text("Dismiss Route")
                }
            }
            plan.mapsUrl?.let { url ->
                Button(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CapePrimary)
                ) {
                    Icon(Icons.Outlined.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open in Google Maps", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // ── Map ──────────────────────────────────────────────────────────────────
    if (hasMapRoute && plan?.polyline != null) {
        RouteMap(plan.polyline, plan.destination)
    }

    // ── Transport Mode Cards ─────────────────────────────────────────────────
    if (hasPlan) plan?.modes?.forEach { mode ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(GlassWhite.copy(0.95f), CapeGlass)), RoundedCornerShape(14.dp))
                    .border(1.dp, CapeGlassBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier.size(40.dp).background(CapePrimaryContainer, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text(modeEmoji(mode.id), fontSize = 20.sp) }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(mode.label, fontWeight = FontWeight.Bold, color = CapeText)
                        Text(mode.distanceText.ifBlank { "Route available" }, color = CapeMuted, fontSize = 12.sp)
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(mode.durationText, color = CapePrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text("Leave ${mode.leaveByLocal}", color = CapeMuted, fontSize = 12.sp)
                }
            }
        }
    }

    // ── Step-by-step directions (timeline style) ─────────────────────────────
    if (hasPlan && plan?.directions?.isNotEmpty() == true) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Route Guidance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = CapeOutlineVariant, thickness = 0.5.dp)
            plan.directions.forEachIndexed { index, step ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Step indicator
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(24.dp).background(CapePrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text("${index + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        if (index < plan.directions.size - 1) {
                            Box(modifier = Modifier.width(2.dp).height(24.dp).background(CapeOutlineVariant))
                        }
                    }
                    Column(modifier = Modifier.weight(1f).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${modeEmoji(step.travelMode)} ${step.instruction}", color = CapeText, fontSize = 13.sp)
                        Text("${step.distanceText} · ${step.durationText}", color = CapeMuted, fontSize = 11.sp)
                    }
                }
            }
            plan.mapsUrl?.let { url ->
                Button(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CapePrimary)
                ) { Text("Full Route in Google Maps", fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    // ── Feedback Card ────────────────────────────────────────────────────────
    if (hasPlan) GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = CapePrimary, modifier = Modifier.size(18.dp))
            Text("Feedback for CAPE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text("Tell CAPE what worked — future departure estimates will adapt to your routine.", color = CapeMuted, fontSize = 13.sp)
        StyledTextField(value = feedbackNote, onValueChange = { feedbackNote = it }, label = "What happened today?")
        Button(
            onClick = {
                onSendFeedback("neutral", feedbackNote.ifBlank { "Commute feedback submitted without explicit rating." })
                feedbackNote = ""
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CapePrimary)
        ) { Text("Send Feedback", fontWeight = FontWeight.SemiBold) }
        if (feedbackStatus.isNotBlank()) {
            Box(modifier = Modifier.fillMaxWidth().background(StressLowBg, RoundedCornerShape(8.dp)).padding(10.dp)) {
                Text(feedbackStatus, color = CapeTertiary, fontSize = 13.sp)
            }
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
    var status by remember { mutableStateOf("") }
    var places by remember(snapshot.savedPlaces) { mutableStateOf(snapshot.savedPlaces) }

    // ── Avatar / Profile Header ───────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(CapePrimary, CapePrimaryDark)),
                RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    role.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("My Profile", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(100.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        role.replaceFirstChar { it.titlecase() },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "${snapshot.locationState} · ${if (snapshot.currentLatitude != null) "GPS active" else "No GPS"}",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp
                )
            }
        }
    }

    // ── Role Selector (Segmented) ─────────────────────────────────────────────
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = CapePrimary, modifier = Modifier.size(18.dp))
            Text("Daily Role", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text("Affects how CAPE interprets your schedule and stress context.", color = CapeMuted, fontSize = 13.sp)
        HorizontalDivider(color = CapeOutlineVariant, thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CapeSecondaryContainer, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("student" to "🎓 Student", "employee" to "💼 Employee").forEach { (key, label) ->
                val selected = role == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { role = key }
                        .background(
                            if (selected) CapePrimary else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (selected) Color.White else CapeText,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    // ── Routine Times ─────────────────────────────────────────────────────────
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = CapePrimary, modifier = Modifier.size(18.dp))
            Text("Daily Routine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text("CAPE uses this to suggest day blocks and add events to Calendar.", color = CapeMuted, fontSize = 13.sp)
        HorizontalDivider(color = CapeOutlineVariant, thickness = 0.5.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Start time", color = CapeMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                StyledTextField(value = startTime, onValueChange = { startTime = it }, label = "HH:mm")
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("End time", color = CapeMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                StyledTextField(value = endTime, onValueChange = { endTime = it }, label = "HH:mm")
            }
        }
        Button(
            onClick = {
                prefs.edit()
                    .putString(KEY_USER_ROLE, role)
                    .putString(KEY_ROUTINE_START, startTime)
                    .putString(KEY_ROUTINE_END, endTime)
                    .apply()
                status = "Routine saved."
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CapePrimary)
        ) { Text("Save Routine", fontWeight = FontWeight.SemiBold) }
    }

    // ── Saved Places ──────────────────────────────────────────────────────────
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Room, contentDescription = null, tint = CapePrimary, modifier = Modifier.size(18.dp))
            Text("Saved Places", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text("Type a place name, tap a suggestion, then save.", color = CapeMuted, fontSize = 13.sp)
        HorizontalDivider(color = CapeOutlineVariant, thickness = 0.5.dp)

        // Home
        PlaceSectionLabel(emoji = "🏠", label = "Home", place = selectedHome)
        PlaceSearchField(
            label = "Search home address…",
            value = home,
            selectedPlace = selectedHome,
            onValueChange = { home = it; selectedHome = null },
            onPlaceSelected = { selectedHome = it; home = it.label }
        )

        // Work
        PlaceSectionLabel(emoji = "💼", label = "Work", place = selectedWork)
        PlaceSearchField(
            label = "Search work address…",
            value = work,
            selectedPlace = selectedWork,
            onValueChange = { work = it; selectedWork = null },
            onPlaceSelected = { selectedWork = it; work = it.label }
        )

        // College
        PlaceSectionLabel(emoji = "🎓", label = "College", place = selectedCollege)
        PlaceSearchField(
            label = "Search college address…",
            value = college,
            selectedPlace = selectedCollege,
            onValueChange = { college = it; selectedCollege = null },
            onPlaceSelected = { selectedCollege = it; college = it.label }
        )

        // Status message
        if (status.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (status.startsWith("Saved") || status.startsWith("Routine")) StressLowBg else StressHighBg,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp)
            ) {
                Text(
                    status,
                    fontSize = 13.sp,
                    color = if (status.startsWith("Saved") || status.startsWith("Routine")) CapeTertiary else StressHigh
                )
            }
        }

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    status = "Resolving places…"
                    Thread {
                        val existing = places.associateBy { normalizedPlaceKind(it.kind) }
                        val updated = buildUpdatedFixedPlaces(
                            existingPlaces = places,
                            home = resolvePlaceForSave("home", home, selectedHome, existing["home"]),
                            work = resolvePlaceForSave("work", work, selectedWork, existing["work"] ?: existing["office"]),
                            college = resolvePlaceForSave("college", college, selectedCollege, existing["college"])
                        )
                        (context as? ComponentActivity)?.runOnUiThread {
                            savePlaces(context, updated)
                            places = updated
                            selectedHome = updated.firstOrNull { normalizedPlaceKind(it.kind) == "home" }
                            selectedWork = updated.firstOrNull { normalizedPlaceKind(it.kind) == "work" }
                            selectedCollege = updated.firstOrNull { normalizedPlaceKind(it.kind) == "college" }
                            home = selectedHome?.query ?: home
                            work = selectedWork?.query ?: work
                            college = selectedCollege?.query ?: college
                            val resolvedCount = updated.count { it.latitude != null && it.longitude != null }
                            status = if (resolvedCount == 0) {
                                "Saved place names. Exact map coordinates could not be resolved right now."
                            } else {
                                "Saved places ($resolvedCount resolved to map coordinates)."
                            }
                        }
                    }.start()
                },
                modifier = Modifier.weight(2f).height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CapePrimary)
            ) { Text("Save Places", fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = {
                    val query = home.ifBlank { work.ifBlank { college.ifBlank { "near me" } } }
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}")))
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Map") }
        }
    }

    // ── Saved Places Summary ──────────────────────────────────────────────────
    if (places.isNotEmpty()) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📍", fontSize = 16.sp)
                Text("Place Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            places.forEach { place ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val emoji = when (place.kind.lowercase()) {
                            "home" -> "🏠"; "work", "office" -> "💼"; "college" -> "🎓"; else -> "📍"
                        }
                        Text(emoji, fontSize = 14.sp)
                        Text(place.kind.replaceFirstChar { it.titlecase() }, color = CapeMuted, fontSize = 13.sp)
                    }
                    Text(
                        place.latitude?.let { "%.3f, %.3f".format(it, place.longitude) } ?: place.query.take(24),
                        color = if (place.latitude != null) CapeGreen else CapeMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceSectionLabel(emoji: String, label: String, place: SavedPlace?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text(emoji, fontSize = 14.sp)
        Text(label, color = CapeMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (place?.latitude != null) {
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .background(StressLowBg, RoundedCornerShape(100.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("✓ Set", color = StressLow, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}




@Composable
private fun TodoSection(
    snapshot: ContextSnapshot
) {
    val context = LocalContext.current
    var list by remember { mutableStateOf(loadTodoList(context)) }
    var draftTitle by remember { mutableStateOf("") }
    var draftStart by remember { mutableStateOf(java.time.LocalTime.now().withSecond(0).withNano(0).toString().take(5)) }
    var draftEnd by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var updateTimes by remember { mutableStateOf(loadTodoPromptTimes(context)) }
    var timeDraft by remember { mutableStateOf(updateTimes.joinToString(",")) }
    var status by remember { mutableStateOf("") }
    var showTimesConfig by remember { mutableStateOf(false) }

    // ── Stats summary bar ─────────────────────────────────────────────────────
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        listOf(
            Triple("Pending", snapshot.todoPendingCount.toString(), CapePrimary),
            Triple("Urgent", snapshot.todoUrgentCount.toString(), CapeOrange),
            Triple("Overdue", snapshot.todoOverdueCount.toString(), StressHigh),
            Triple("Pressure", "${snapshot.todoPressureScore}%", CapeTertiary)
        ).forEach { (label, value, color) ->
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GlassWhite.copy(0.9f), RoundedCornerShape(12.dp))
                        .border(1.dp, CapeGlassBorder, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = color)
                    Text(label, fontSize = 10.sp, color = CapeMuted)
                }
            }
        }
    }

    // ── Add / Edit Form ───────────────────────────────────────────────────────
    GlassCard(container = if (editingId != null) CapePrimaryContainer.copy(alpha = 0.4f) else CapeGlass) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val taskIcon = if (editingId != null) Icons.Filled.Edit else Icons.Filled.Add
            Icon(
                taskIcon,
                contentDescription = null,
                tint = CapePrimary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                if (editingId != null) "Edit Task" else "Add New Task",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (editingId != null) {
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(CapePrimaryContainer, RoundedCornerShape(100.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) { Text("Editing", fontSize = 11.sp, color = CapePrimaryDark, fontWeight = FontWeight.SemiBold) }
            }
        }
        HorizontalDivider(color = CapeOutlineVariant, thickness = 0.5.dp)
        StyledTextField(value = draftTitle, onValueChange = { draftTitle = it }, label = "Task title…")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Start time", color = CapeMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                ClockTimeField(label = "HH:mm", value = draftStart, optional = true, onChange = { draftStart = it })
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("End time (optional)", color = CapeMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                ClockTimeField(label = "HH:mm", value = draftEnd, optional = true, onChange = { draftEnd = it })
            }
        }
        if (status.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (status.startsWith("Todo saved") || status.startsWith("Todo removed")) StressLowBg else StressHighBg.copy(alpha = 0.4f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp)
            ) {
                Text(status, color = if (status.startsWith("Todo saved") || status.startsWith("Todo removed")) CapeTertiary else StressHigh, fontSize = 13.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (editingId != null) {
                OutlinedButton(
                    onClick = {
                        editingId = null; draftTitle = ""; draftStart = java.time.LocalTime.now().withSecond(0).withNano(0).toString().take(5); draftEnd = ""; status = ""
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Cancel") }
            }
            Button(
                onClick = {
                    val title = draftTitle.trim()
                    if (title.isBlank()) return@Button
                    val startAt = parseTodoTimeToday(draftStart)
                    if (startAt == null) { status = "Start time is required."; return@Button }
                    val endAt = parseTodoTimeToday(draftEnd)
                    val now = System.currentTimeMillis()
                    val item = TodoItem(
                        id = editingId ?: "todo_${now}",
                        title = title, startAt = startAt, endAt = endAt,
                        completed = false, createdAt = now, updatedAt = now
                    )
                    val todayList = ensureTodayTodoList(list)
                    list = if (editingId == null) {
                        todayList.copy(items = todayList.items + item, updatedAt = now)
                    } else {
                        todayList.copy(items = todayList.items.map { existing ->
                            if (existing.id == editingId) item.copy(createdAt = existing.createdAt, completed = existing.completed) else existing
                        }, updatedAt = now)
                    }
                    saveTodoList(context, list)
                    recordTodoEdit(context, list, if (editingId == null) "added:$title" else "updated:$title")
                    draftTitle = ""; draftStart = java.time.LocalTime.now().withSecond(0).withNano(0).toString().take(5); draftEnd = ""; editingId = null
                    status = "Todo saved and sent to OpenClaw."
                },
                modifier = Modifier.weight(if (editingId != null) 2f else 1f).height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CapePrimary)
            ) { Text(if (editingId == null) "Add Task" else "Update Task", fontWeight = FontWeight.SemiBold) }
        }
    }

    // ── Day Timeline ──────────────────────────────────────────────────────────
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.DateRange, contentDescription = null, tint = CapePrimary, modifier = Modifier.size(18.dp))
                Text("Today's Timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text("${list.items.count { !it.completed }} left", color = CapeMuted, fontSize = 12.sp)
        }
        HorizontalDivider(color = CapeOutlineVariant, thickness = 0.5.dp)

        if (list.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("✅", fontSize = 28.sp)
                    Text("All clear — no tasks yet.", color = CapeMuted, fontSize = 14.sp)
                    Text("Add a task above to get started.", color = CapeMuted, fontSize = 12.sp)
                }
            }
        }

        // Timeline items
        list.items.sortedBy { it.startAt }.forEachIndexed { index, item ->
            val isLast = index == list.items.size - 1
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Timeline rail
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp)) {
                    // Time badge
                    Box(
                        modifier = Modifier
                            .background(
                                if (item.completed) CapeSecondaryContainer else CapePrimaryContainer,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            formatTodoClock(item.startAt) ?: "?",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.completed) CapeSecondary else CapePrimary
                        )
                    }
                    // Connecting line
                    if (!isLast) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(32.dp)
                                .background(CapeOutlineVariant)
                        )
                    }
                }
                // Task card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (isLast) 0.dp else 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            item.title,
                            fontWeight = FontWeight.SemiBold,
                            color = if (item.completed) CapeMuted else CapeText,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Done toggle
                        Box(
                            modifier = Modifier
                                .clickable {
                                    list = list.copy(
                                        items = list.items.map {
                                            if (it.id == item.id) it.copy(completed = !it.completed, updatedAt = System.currentTimeMillis()) else it
                                        },
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    saveTodoList(context, list)
                                    recordTodoEdit(context, list, "completed:${item.title}")
                                }
                                .background(
                                    if (item.completed) StressLowBg else CapeSecondaryContainer,
                                    RoundedCornerShape(100.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (item.completed) "✓ Done" else "Open",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (item.completed) StressLow else CapeMuted
                            )
                        }
                    }
                    // Meta + actions row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(todoMetaLabel(item), color = CapeMuted, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Edit",
                                color = CapePrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    editingId = item.id
                                    draftTitle = item.title
                                    draftStart = formatTodoClock(item.startAt) ?: java.time.LocalTime.now().toString().take(5)
                                    draftEnd = formatTodoClock(item.endAt) ?: ""
                                    status = "Editing ${item.title}"
                                }
                            )
                            Text("·", color = CapeMuted, fontSize = 12.sp)
                            Text(
                                "Delete",
                                color = StressHigh,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    list = list.copy(
                                        items = list.items.filterNot { it.id == item.id },
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    saveTodoList(context, list)
                                    recordTodoEdit(context, list, "removed:${item.title}")
                                    if (editingId == item.id) editingId = null
                                    status = "Todo removed."
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Update times config (collapsible) ─────────────────────────────────────
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { showTimesConfig = !showTimesConfig },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = CapePrimary, modifier = Modifier.size(18.dp))
                Text("Prompt Schedule", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(if (showTimesConfig) "▲" else "▼", color = CapeMuted, fontSize = 12.sp)
        }
        if (showTimesConfig) {
            HorizontalDivider(color = CapeOutlineVariant, thickness = 0.5.dp)
            Text("Times when CAPE asks you to update your todo list (HH:mm, comma-separated)", color = CapeMuted, fontSize = 12.sp)
            StyledTextField(value = timeDraft, onValueChange = { timeDraft = it }, label = "e.g. 09:00,13:00,18:00")
            val learned = loadLearnedTodoHours(context)
            if (learned.isNotEmpty()) {
                Text(
                    "Learned windows: ${learned.joinToString { "%02d:00".format(it) }}",
                    color = CapeMuted, fontSize = 12.sp
                )
            }
            Button(
                onClick = {
                    updateTimes = parseTodoPromptTimes(timeDraft)
                    saveTodoPromptTimes(context, updateTimes)
                    status = "Prompt times saved."
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CapePrimary)
            ) { Text("Save Prompt Times", fontWeight = FontWeight.SemiBold) }
        }
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
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(GlassWhite.copy(0.95f), CapeGlass)), RoundedCornerShape(14.dp))
                .border(1.dp, CapeGlassBorder, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, color = CapeMuted, fontSize = 12.sp)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = CapeText)
        }
    }
}



@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    container: Color = CapeGlass,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(GlassWhite.copy(alpha = 0.92f), container.copy(alpha = 0.82f))
                    ),
                    RoundedCornerShape(16.dp)
                )
                .border(1.dp, CapeGlassBorder, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}



@Composable
private fun ClockTimeField(
    label: String,
    value: String,
    optional: Boolean = false,
    onChange: (String) -> Unit
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.SemiBold)
            if (optional) TextButton(onClick = { onChange("") }) { Text("Clear") }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("HH:mm") }
        )
        OutlinedButton(
            onClick = {
                val parsed = runCatching { java.time.LocalTime.parse(value.ifBlank { "09:00" }) }
                    .getOrDefault(java.time.LocalTime.of(9, 0))
                TimePickerDialog(
                    context,
                    { _, h, m -> onChange("%02d:%02d".format(h, m)) },
                    parsed.hour,
                    parsed.minute,
                    true
                ).show()
            },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(10.dp)
        ) { Text("Pick time") }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = CapeMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = CapeText,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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
    val fallback = existing?.copy(
        kind = kind,
        label = kind.replaceFirstChar { it.titlecase() },
        query = query.trim(),
        radiusMeters = fixedPlaceRadius(kind)
    ) ?: SavedPlace(
        kind = kind,
        label = kind.replaceFirstChar { it.titlecase() },
        query = query.trim(),
        latitude = null,
        longitude = null,
        radiusMeters = fixedPlaceRadius(kind)
    )
    return (resolved ?: fallback).copy(
        kind = kind,
        label = kind.replaceFirstChar { it.titlecase() },
        query = query.trim(),
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

private fun maybeShowContextModePrompt(context: Context, snapshot: ContextSnapshot) {
    val now = System.currentTimeMillis()
    val meetingStart = snapshot.nextMeetingStartEpochMs
    val meetingEnd = snapshot.nextMeetingEndEpochMs
    val mode = when {
        meetingStart != null && meetingEnd != null && now in meetingStart..meetingEnd -> ModePrompt(
            key = "meeting_${meetingStart}",
            packId = "office_focus_high_stress",
            title = "Meeting in progress",
            body = "Apply focus mode until this meeting ends?",
            actions = listOf("DND_ON", "RINGER_VIBRATE", "BRIGHTNESS_40", "WALLPAPER_FOCUS")
        )
        snapshot.locationState == "home" || snapshot.locationState == "relaxing" -> ModePrompt(
            key = "relax_${java.time.LocalDate.now()}_${snapshot.hourOfDay}",
            packId = "home_evening",
            title = "You are home",
            body = "Apply relax mode now?",
            actions = listOf("DND_OFF", "RINGER_NORMAL", "BRIGHTNESS_AUTO", "WALLPAPER_RELAX")
        )
        snapshot.locationState == "commuting" -> ModePrompt(
            key = "commute_${java.time.LocalDate.now()}_${snapshot.hourOfDay}",
            packId = "commute_alert",
            title = "Commute detected",
            body = "Apply commute mode now?",
            actions = listOf("DND_OFF", "RINGER_NORMAL", "BRIGHTNESS_65", "WALLPAPER_COMMUTE")
        )
        else -> null
    } ?: return
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val prefs = context.getSharedPreferences("cape_context", Context.MODE_PRIVATE)
    val prefKey = "mode_prompt_${mode.key}"
    if (prefs.getBoolean(prefKey, false)) return
    prefs.edit().putBoolean(prefKey, true).apply()
    showModePromptNotification(context, mode)
}

private fun showModePromptNotification(context: Context, prompt: ModePrompt) {
    recordNotificationEvent(context)
    val notificationId = MODE_PROMPT_BASE_ID + kotlin.math.abs(prompt.key.hashCode() % 1000)
    val yesIntent = Intent(context, CapeActionReceiver::class.java)
        .setAction(ACTION_APPLY_MODE)
        .putExtra(EXTRA_PACK_ID, prompt.packId)
        .putStringArrayListExtra(EXTRA_ACTIONS, ArrayList(prompt.actions))
        .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
    val noIntent = Intent(context, CapeActionReceiver::class.java)
        .setAction(ACTION_REJECT_MODE)
        .putExtra(EXTRA_PACK_ID, prompt.packId)
        .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
    val notification = NotificationCompat.Builder(context, COMMUTE_CHANNEL)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(prompt.title)
        .setContentText(prompt.body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(prompt.body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .addAction(0, "Yes", PendingIntent.getBroadcast(context, notificationId + 10_000, yesIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .addAction(0, "No", PendingIntent.getBroadcast(context, notificationId + 20_000, noIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .build()
    NotificationManagerCompat.from(context).notify(notificationId, notification)
}

private fun recordNotificationDecision(context: Context, packId: String, signal: String, note: String, actions: List<String> = emptyList()) {
    Thread {
        runCatching {
            GatewayClient().sendDecisionApproval(
                packId = packId,
                signal = signal,
                note = note,
                actions = actions,
                confidence = 1.0
            )
        }
    }.start()
}

private fun dismissNotification(context: Context, id: Int) {
    if (id <= 0) return
    runCatching { NotificationManagerCompat.from(context).cancel(id) }
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
        val meetingEnd = snapshot.nextMeetingEndEpochMs?.takeIf { it > meetingStart } ?: meetingStart + 60 * 60_000L
        items.add(planItem(title, snapshot.nextMeetingLocation ?: "calendar", meetingStart, meetingEnd, zone))
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
                    startAt = item.optLong("startAt", item.optLong("dueAt", 0L)).takeIf { it > 0L },
                    endAt = item.optLong("endAt").takeIf { it > 0L },
                    completed = item.optBoolean("completed"),
                    createdAt = item.optLong("createdAt"),
                    updatedAt = item.optLong("updatedAt")
                )
            }.filter { it.title.isNotBlank() },
            updatedAt = root.optLong("updatedAt", System.currentTimeMillis())
        )
    }.getOrDefault(TodoList(today, emptyList(), System.currentTimeMillis()))
        .let { ensureTodayTodoList(it) }
        .let { current ->
            val completed = autoCompleteExpiredTodos(current)
            if (completed != current) saveTodoList(context, completed)
            completed
        }
}

private fun ensureTodayTodoList(list: TodoList): TodoList {
    val today = java.time.LocalDate.now().toString()
    return if (list.date == today) list else TodoList(today, emptyList(), System.currentTimeMillis())
}

private fun autoCompleteExpiredTodos(list: TodoList): TodoList {
    val now = System.currentTimeMillis()
    var changed = false
    val items = list.items.map { item ->
        if (!item.completed && item.endAt != null && item.endAt < now) {
            changed = true
            item.copy(completed = true, updatedAt = now)
        } else {
            item
        }
    }
    return if (changed) list.copy(items = items, updatedAt = now) else list
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
                    .put("startAt", item.startAt)
                    .put("endAt", item.endAt)
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
        val start = item.startAt ?: return@forEach
        if (start < now) overdue += 1
        if (start <= now + 3 * 60 * 60_000L) urgent += 1
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

private fun parseTodoTimeToday(value: String): Long? {
    val time = runCatching { java.time.LocalTime.parse(value.trim()) }.getOrNull() ?: return null
    return java.time.LocalDate.now()
        .atTime(time)
        .atZone(java.time.ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

private fun formatTodoClock(value: Long?): String? =
    value?.let {
        java.time.Instant.ofEpochMilli(it)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalTime()
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    }

private fun todoMetaLabel(item: TodoItem): String {
    val start = item.startAt?.let {
        java.time.Instant.ofEpochMilli(it)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
    } ?: "no start time"
    val end = item.endAt?.let {
        java.time.Instant.ofEpochMilli(it)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
    }
    return "${if (item.completed) "done" else "pending"} - $start${end?.let { " to $it" } ?: ""}"
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
    val remoteInput = RemoteInput.Builder(KEY_TODO_REMOTE_INPUT)
        .setLabel("Todo title")
        .build()
    val addIntent = Intent(context, CapeActionReceiver::class.java).setAction(ACTION_TODO_QUICK_ADD)
    val dismissIntent = Intent(context, CapeActionReceiver::class.java).setAction(ACTION_TODO_DISMISS)
    val addAction = NotificationCompat.Action.Builder(0, "Add", PendingIntent.getBroadcast(context, 4101, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
        .addRemoteInput(remoteInput)
        .setAllowGeneratedReplies(true)
        .build()
    val notification = NotificationCompat.Builder(context, COMMUTE_CHANNEL)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Update today's todo list")
        .setContentText("Add a quick todo from here or dismiss for now.")
        .setStyle(NotificationCompat.BigTextStyle().bigText("Add a quick todo from this notification. More detailed start/end scheduling is available when you open CAPE."))
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .addAction(addAction)
        .addAction(0, "No", PendingIntent.getBroadcast(context, 4102, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .build()
    runCatching { NotificationManagerCompat.from(context).notify(TODO_PROMPT_NOTIFICATION_ID, notification) }
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
    val startAt: Long?,
    val endAt: Long?,
    val completed: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

private data class ModePrompt(
    val key: String,
    val packId: String,
    val title: String,
    val body: String,
    val actions: List<String>
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
private const val ACTION_APPLY_MODE = "dev.rootcause.cape.APPLY_MODE"
private const val ACTION_REJECT_MODE = "dev.rootcause.cape.REJECT_MODE"
private const val ACTION_TODO_QUICK_ADD = "dev.rootcause.cape.TODO_QUICK_ADD"
private const val ACTION_TODO_DISMISS = "dev.rootcause.cape.TODO_DISMISS"
private const val EXTRA_PACK_ID = "pack_id"
private const val EXTRA_ACTIONS = "actions"
private const val EXTRA_NOTIFICATION_ID = "notification_id"
private const val KEY_TODO_REMOTE_INPUT = "todo_remote_input"
private const val TODO_PROMPT_NOTIFICATION_ID = 2400
private const val MODE_PROMPT_BASE_ID = 3300
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
