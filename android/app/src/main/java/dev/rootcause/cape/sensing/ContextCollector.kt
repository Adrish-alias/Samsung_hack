package dev.rootcause.cape.sensing

import android.Manifest
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.provider.CalendarContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.rootcause.cape.core.ContextCollectionResult
import dev.rootcause.cape.core.ContextSnapshot
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
        val sleepDebt = readSleepProxyDebt(notes)
        val locationReading = readLocationState(notes)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val now = Calendar.getInstance()

        val snapshot = ContextSnapshot(
            locationState = locationReading.state,
            sleepDebtMinutes = sleepDebt,
            meetingLoadToday = meetingStats.meetingLoadToday,
            commuteDelayMinutes = commuteDelayFor(locationReading.state, meetingStats.nextMeetingMinutes),
            screenTimeLast2hMinutes = usageMinutes,
            nextMeetingMinutes = meetingStats.nextMeetingMinutes,
            nextMeetingLocation = meetingStats.nextMeetingLocation,
            currentLatitude = locationReading.latitude,
            currentLongitude = locationReading.longitude,
            notificationPolicyAccess = notificationManager.isNotificationPolicyAccessGranted,
            writeSettings = Settings.System.canWrite(context),
            nextMeetingTitle = meetingStats.nextMeetingTitle,
            currentTimeIso = isoFormatter().format(now.time),
            dayOfWeek = now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US)?.lowercase(Locale.US),
            hourOfDay = now.get(Calendar.HOUR_OF_DAY),
            timezone = TimeZone.getDefault().id
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

    private fun readMeetings(notes: MutableList<String>): MeetingStats {
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) {
            notes.add("Calendar permission missing; meeting load is 0.")
            return MeetingStats(0, null, null, null)
        }

        val now = System.currentTimeMillis()
        val endOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION
        )
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(endOfDay.toString())
            .build()

        var count = 0
        var nextStart: Long? = null
        var nextLocation: String? = null
        var nextTitle: String? = null
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val locationIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            while (cursor.moveToNext()) {
                val begin = cursor.getLong(beginIndex)
                count += 1
                if (begin >= now && nextStart == null) {
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

    private fun readSleepProxyDebt(notes: MutableList<String>): Int {
        val estimatedSleep = prefs().getLong(KEY_LAST_SLEEP_MINUTES, DEFAULT_SLEEP_MINUTES)
        val debt = (TARGET_SLEEP_MINUTES - estimatedSleep).coerceAtLeast(0).toInt()
        notes.add("Sleep proxy: last long screen-off window ${estimatedSleep}m; debt ${debt}m.")
        return debt
    }

    private fun readLocationState(notes: MutableList<String>): LocationReading {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) && !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            notes.add("Location permission missing; location state unknown.")
            return LocationReading("unknown", null, null)
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
            return LocationReading("unknown", null, null)
        }

        val speedKmh = location.speed * 3.6f
        val state = if (speedKmh >= 12f) "commuting" else "unknown"
        notes.add("Location read: speed approx ${speedKmh.roundToInt()} km/h; state $state.")
        return LocationReading(state, location.latitude, location.longitude)
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

    private fun prefs() = context.getSharedPreferences("cape_context", Context.MODE_PRIVATE)

    private data class MeetingStats(
        val meetingLoadToday: Int,
        val nextMeetingMinutes: Int?,
        val nextMeetingLocation: String?,
        val nextMeetingTitle: String?
    )

    private data class LocationReading(
        val state: String,
        val latitude: Double?,
        val longitude: Double?
    )

    companion object {
        private const val KEY_LAST_SCREEN_OFF = "last_screen_off"
        private const val KEY_LAST_SLEEP_MINUTES = "last_sleep_minutes"
        private const val DEFAULT_SLEEP_MINUTES = 420L
        private const val TARGET_SLEEP_MINUTES = 480L
    }

    private fun isoFormatter(): SimpleDateFormat {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }
}
