package dev.rootcause.cape.sensing

import android.Manifest
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.CalendarContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.rootcause.cape.core.ContextCollectionResult
import dev.rootcause.cape.core.ContextSnapshot
import dev.rootcause.cape.core.SavedPlace
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class ContextCollector(private val context: Context) {
    fun collect(): ContextCollectionResult {
        val notes = mutableListOf<String>()
        val meetingStats = readMeetings(notes)
        val usageMinutes = readUsageMinutes(notes)
        val behaviorStats = readBehaviorStats(notes)
        val sleepDebt = readSleepProxyDebt(notes)
        val locationReading = readLocationState(notes)
        val savedPlaces = readSavedPlaces()
        val profileState = classifyPlace(locationReading, savedPlaces, notes)
        val locationState = profileState ?: locationReading.state
        val timeAtLocation = updateTimeAtLocation(locationState)
        val implicitWorkload = if (
            (locationState == "office" || locationState == "college") &&
            timeAtLocation > 120 &&
            meetingStats.meetingLoadToday == 0
        ) {
            "HIGH"
        } else if (behaviorStats.appSwitchCount >= 18 || behaviorStats.unlockCount >= 8) {
            "MEDIUM"
        } else {
            "LOW"
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val now = Calendar.getInstance()

        val snapshot = ContextSnapshot(
            locationState = locationState,
            sleepDebtMinutes = sleepDebt,
            meetingLoadToday = meetingStats.meetingLoadToday,
            commuteDelayMinutes = commuteDelayFor(locationReading.state, meetingStats.nextMeetingMinutes),
            screenTimeLast2hMinutes = usageMinutes,
            appSwitchCountLast30Min = behaviorStats.appSwitchCount,
            screenUnlockCountLast30Min = behaviorStats.unlockCount,
            notificationCountLast30Min = readNotificationCountLast30Min(),
            foregroundAppCategory = behaviorStats.foregroundCategory,
            timeAtLocationMinutes = timeAtLocation,
            implicitWorkload = implicitWorkload,
            nextMeetingMinutes = meetingStats.nextMeetingMinutes,
            nextMeetingStartEpochMs = meetingStats.nextMeetingStartEpochMs,
            nextMeetingLocation = meetingStats.nextMeetingLocation,
            currentLatitude = locationReading.latitude,
            currentLongitude = locationReading.longitude,
            currentSpeedMps = locationReading.speedMps,
            notificationPolicyAccess = notificationManager.isNotificationPolicyAccessGranted,
            writeSettings = Settings.System.canWrite(context),
            notifications = hasNotificationPermission(),
            calendarPermission = hasPermission(Manifest.permission.READ_CALENDAR),
            locationPermission = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION),
            usageStatsPermission = hasUsageStatsAccess(),
            nextMeetingTitle = meetingStats.nextMeetingTitle,
            currentTimeIso = isoFormatter().format(now.time),
            dayOfWeek = now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US)?.lowercase(Locale.US),
            hourOfDay = now.get(Calendar.HOUR_OF_DAY),
            timezone = TimeZone.getDefault().id,
            savedPlaces = savedPlaces
        )

        return ContextCollectionResult(snapshot, notes)
    }

    fun recordScreenOff() {
        prefs().edit().putLong(KEY_LAST_SCREEN_OFF, System.currentTimeMillis()).apply()
    }

    fun recordScreenOn() {
        val offAt = prefs().getLong(KEY_LAST_SCREEN_OFF, 0L)
        if (offAt <= 0L) return
        val durationMinutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - offAt)
        if (durationMinutes >= 120) {
            prefs().edit().putLong(KEY_LAST_SLEEP_MINUTES, durationMinutes).apply()
        }
    }

    fun recordUnlock() {
        appendTimestamp(KEY_UNLOCK_EVENTS, System.currentTimeMillis())
    }

    fun recordNotificationObserved() {
        appendTimestamp(KEY_NOTIFICATION_EVENTS, System.currentTimeMillis())
    }

    private fun readMeetings(notes: MutableList<String>): MeetingStats {
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) {
            notes.add("Calendar permission missing; meeting load is 0.")
            return MeetingStats(0, null, null, null, null)
        }

        val now = System.currentTimeMillis()
        val horizon = now + TimeUnit.HOURS.toMillis(24)

        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION
        )
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(horizon.toString())
            .build()

        var count = 0
        var nextStart: Long? = null
        var nextLocation: String? = null
        var nextTitle: String? = null
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val locationIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            while (cursor.moveToNext()) {
                val begin = cursor.getLong(beginIndex)
                val end = cursor.getLong(endIndex)
                if (end < now) continue
                if (begin < now) continue
                count += 1
                if (nextStart == null) {
                    nextStart = begin
                    nextLocation = cursor.getString(locationIndex)?.takeIf { it.isNotBlank() }
                    nextTitle = cursor.getString(titleIndex)?.takeIf { it.isNotBlank() }
                }
            }
        }

        notes.add(
            "Calendar read: $count meetings remaining/today" +
                (nextTitle?.let { "; next \"$it\"" } ?: "") +
                (nextLocation?.let { "; next location present" } ?: "") +
                "."
        )
        return MeetingStats(
            meetingLoadToday = count,
            nextMeetingMinutes = nextStart?.let { TimeUnit.MILLISECONDS.toMinutes(it - now).coerceAtLeast(0).toInt() },
            nextMeetingStartEpochMs = nextStart,
            nextMeetingLocation = nextLocation,
            nextMeetingTitle = nextTitle
        )
    }

    private fun readUsageMinutes(notes: MutableList<String>): Int {
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - TimeUnit.HOURS.toMillis(2)
        val stats = usage.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
        if (stats.isNullOrEmpty()) {
            notes.add("Usage access missing or empty; screen intensity is 0.")
            return 0
        }

        val totalForegroundMs = stats.sumOf { it.totalTimeInForeground }
        val minutes = TimeUnit.MILLISECONDS.toMinutes(totalForegroundMs).toInt().coerceIn(0, 120)
        notes.add("UsageStats read: $minutes foreground minutes in last 2h.")
        return minutes
    }

    private fun readBehaviorStats(notes: MutableList<String>): BehaviorStats {
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - TimeUnit.MINUTES.toMillis(30)
        val events = usage.queryEvents(start, end)
        val event = UsageEvents.Event()
        var switches = 0
        var previousPackage: String? = null
        var foregroundPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (previousPackage != null && previousPackage != event.packageName) switches += 1
                previousPackage = event.packageName
                foregroundPackage = event.packageName
            }
        }
        val category = foregroundPackage?.let { categorizePackage(it) } ?: "mixed"
        val unlocks = countRecentTimestamps(KEY_UNLOCK_EVENTS, end, TimeUnit.MINUTES.toMillis(30))
        notes.add("Behavior: $switches app switches, $unlocks unlocks, foreground category $category in last 30m.")
        return BehaviorStats(switches.coerceIn(0, 100), unlocks.coerceIn(0, 100), category)
    }

    private fun categorizePackage(packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (appInfo.category) {
                    android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "work"
                    android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "social"
                    android.content.pm.ApplicationInfo.CATEGORY_VIDEO,
                    android.content.pm.ApplicationInfo.CATEGORY_AUDIO,
                    android.content.pm.ApplicationInfo.CATEGORY_GAME -> "entertainment"
                    else -> fallbackCategory(packageName)
                }
            } else {
                fallbackCategory(packageName)
            }
        } catch (_: Exception) {
            fallbackCategory(packageName)
        }
    }

    private fun fallbackCategory(packageName: String): String {
        val lower = packageName.lowercase(Locale.US)
        return when {
            listOf("docs", "sheet", "slides", "meet", "zoom", "teams", "calendar", "gmail", "classroom").any { lower.contains(it) } -> "work"
            listOf("instagram", "facebook", "snapchat", "whatsapp", "telegram", "discord", "twitter", "x.").any { lower.contains(it) } -> "social"
            listOf("youtube", "netflix", "primevideo", "spotify", "game").any { lower.contains(it) } -> "entertainment"
            else -> "mixed"
        }
    }

    private fun readSleepProxyDebt(notes: MutableList<String>): Int {
        val estimatedSleep = prefs().getLong(KEY_LAST_SLEEP_MINUTES, DEFAULT_SLEEP_MINUTES)
        val debt = (TARGET_SLEEP_MINUTES - estimatedSleep).coerceAtLeast(0).toInt()
        notes.add("Sleep proxy: last long screen-off window ${estimatedSleep}m; debt ${debt}m.")
        return debt
    }

    private fun readLocationState(notes: MutableList<String>): LocationReading {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) && !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            notes.add("Location permission missing; location state unknown.")
            return LocationReading("unknown", null, null, null)
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider ->
                try {
                    if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
                } catch (_: SecurityException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            .maxByOrNull { it.time }

        if (location == null) {
            notes.add("No last known location available yet.")
            return LocationReading("unknown", null, null, null)
        }

        val speedKmh = location.speed * 3.6f
        val state = if (speedKmh >= 12f) "commuting" else "unknown"
        notes.add("Location read: speed approx ${speedKmh.roundToInt()} km/h; state $state.")
        return LocationReading(state, location.latitude, location.longitude, location.speed)
    }

    private fun commuteDelayFor(locationState: String, nextMeetingMinutes: Int?): Int {
        if (locationState != "commuting") return 0
        if (nextMeetingMinutes == null) return 0
        return when {
            nextMeetingMinutes <= 30 -> 30
            nextMeetingMinutes <= 90 -> 18
            else -> 8
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun hasUsageStatsAccess(): Boolean {
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - TimeUnit.MINUTES.toMillis(5)
        return !usage.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end).isNullOrEmpty()
    }

    private fun prefs() = context.getSharedPreferences("cape_context", Context.MODE_PRIVATE)

    private fun updateTimeAtLocation(locationState: String): Int {
        val now = System.currentTimeMillis()
        val prefs = prefs()
        val previous = prefs.getString(KEY_LAST_LOCATION_STATE, null)
        val since = if (previous == locationState) {
            prefs.getLong(KEY_LOCATION_STATE_SINCE, now)
        } else {
            prefs.edit()
                .putString(KEY_PREVIOUS_LOCATION_STATE, previous)
                .putString(KEY_LAST_LOCATION_STATE, locationState)
                .putLong(KEY_LOCATION_STATE_SINCE, now)
                .apply()
            now
        }
        return TimeUnit.MILLISECONDS.toMinutes(now - since).toInt().coerceAtLeast(0)
    }

    private fun readNotificationCountLast30Min(): Int =
        countRecentTimestamps(KEY_NOTIFICATION_EVENTS, System.currentTimeMillis(), TimeUnit.MINUTES.toMillis(30))

    private fun appendTimestamp(key: String, timestamp: Long) {
        val cutoff = timestamp - TimeUnit.MINUTES.toMillis(30)
        val current = prefs().getString(key, "") ?: ""
        val updated = (current.split(',')
            .mapNotNull { it.toLongOrNull() }
            .filter { it >= cutoff } + timestamp)
            .joinToString(",")
        prefs().edit().putString(key, updated).apply()
    }

    private fun countRecentTimestamps(key: String, now: Long, windowMs: Long): Int {
        val cutoff = now - windowMs
        val values = (prefs().getString(key, "") ?: "")
            .split(',')
            .mapNotNull { it.toLongOrNull() }
            .filter { it >= cutoff }
        prefs().edit().putString(key, values.joinToString(",")).apply()
        return values.size
    }

    private fun readSavedPlaces(): List<SavedPlace> {
        val raw = prefs().getString(KEY_SAVED_PLACES, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                SavedPlace(
                    kind = item.optString("kind"),
                    label = item.optString("label"),
                    query = item.optString("query"),
                    latitude = item.optDoubleOrNull("lat"),
                    longitude = item.optDoubleOrNull("lng"),
                    radiusMeters = item.optInt("radiusMeters", defaultRadiusForPlace(item.optString("kind")))
                )
            }.filter { it.label.isNotBlank() || it.query.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun classifyPlace(location: LocationReading, savedPlaces: List<SavedPlace>, notes: MutableList<String>): String? {
        val lat = location.latitude ?: return null
        val lng = location.longitude ?: return null
        val nearest = savedPlaces
            .filter { it.latitude != null && it.longitude != null }
            .minByOrNull { distanceMeters(lat, lng, it.latitude ?: lat, it.longitude ?: lng) }
            ?: return null
        val meters = distanceMeters(lat, lng, nearest.latitude ?: lat, nearest.longitude ?: lng)
        val radius = nearest.radiusMeters.coerceIn(80, 600)
        val speedMps = location.speedMps ?: 0f
        val mapped = when (nearest.kind) {
                "work" -> "office"
                "office" -> "office"
                "college" -> "college"
                "home" -> "home"
                "relaxing" -> "relaxing"
                else -> nearest.kind.takeIf { it.isNotBlank() }
        }
        return if (mapped != null && meters <= radius && speedMps < 8f) {
            notes.add("Arrived at saved ${nearest.kind} place '${nearest.label.ifBlank { nearest.query }}' (${meters.roundToInt()}m within ${radius}m); location state $mapped.")
            mapped
        } else {
            notes.add("Nearest saved place '${nearest.label.ifBlank { nearest.query }}' is ${meters.roundToInt()}m away; radius ${radius}m.")
            null
        }
    }

    private fun defaultRadiusForPlace(kind: String): Int =
        when (kind) {
            "home" -> 180
            "work", "office", "college" -> 220
            "relaxing" -> 250
            else -> 250
        }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, result)
        return result[0]
    }

    private fun org.json.JSONObject.optDoubleOrNull(name: String): Double? {
        return if (has(name) && !isNull(name)) optDouble(name) else null
    }

    private data class MeetingStats(
        val meetingLoadToday: Int,
        val nextMeetingMinutes: Int?,
        val nextMeetingStartEpochMs: Long?,
        val nextMeetingLocation: String?,
        val nextMeetingTitle: String?
    )

    private data class LocationReading(
        val state: String,
        val latitude: Double?,
        val longitude: Double?,
        val speedMps: Float?
    )

    private data class BehaviorStats(
        val appSwitchCount: Int,
        val unlockCount: Int,
        val foregroundCategory: String
    )

    companion object {
        private const val KEY_LAST_SCREEN_OFF = "last_screen_off"
        private const val KEY_LAST_SLEEP_MINUTES = "last_sleep_minutes"
        private const val KEY_SAVED_PLACES = "saved_places"
        private const val KEY_UNLOCK_EVENTS = "unlock_events"
        private const val KEY_NOTIFICATION_EVENTS = "notification_events"
        private const val KEY_LAST_LOCATION_STATE = "last_location_state"
        private const val KEY_PREVIOUS_LOCATION_STATE = "previous_location_state"
        private const val KEY_LOCATION_STATE_SINCE = "location_state_since"
        private const val DEFAULT_SLEEP_MINUTES = 420L
        private const val TARGET_SLEEP_MINUTES = 480L
    }

    private fun isoFormatter(): SimpleDateFormat {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }
}
