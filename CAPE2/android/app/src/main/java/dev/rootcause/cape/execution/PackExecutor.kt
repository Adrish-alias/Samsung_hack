package dev.rootcause.cape.execution

import android.app.WallpaperManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.rootcause.cape.R
import dev.rootcause.cape.core.CapeDecision

class PackExecutor(private val context: Context) {
    fun applyWallpaperAction(action: String): Boolean {
        return when (action) {
            "WALLPAPER_FOCUS" -> setWallpaper(R.drawable.wallpaper_focus)
            "WALLPAPER_RELAX" -> setWallpaper(R.drawable.wallpaper_relax)
            "WALLPAPER_COMMUTE" -> setWallpaper(R.drawable.wallpaper_commute)
            "WALLPAPER_RESET" -> setWallpaper(R.drawable.wallpaper_reset)
            else -> false
        }
    }

    fun apply(decision: CapeDecision): String {
        if (decision.type != "APPLY_PACK") {
            return "No pack applied: ${decision.type}"
        }

        val applied = mutableListOf<String>()
        val blocked = mutableListOf<String>()

        for (action in decision.actions) {
            when (action) {
                "DND_ON" -> if (setDnd(true)) applied.add(action) else blocked.add(action)
                "DND_OFF" -> if (setDnd(false)) applied.add(action) else blocked.add(action)
                "RINGER_VIBRATE" -> if (setRinger(AudioManager.RINGER_MODE_VIBRATE)) applied.add(action) else blocked.add(action)
                "RINGER_NORMAL" -> if (setRinger(AudioManager.RINGER_MODE_NORMAL)) applied.add(action) else blocked.add(action)
                "BRIGHTNESS_40" -> if (setBrightness(102)) applied.add(action) else blocked.add(action)
                "BRIGHTNESS_50" -> if (setBrightness(128)) applied.add(action) else blocked.add(action)
                "BRIGHTNESS_65" -> if (setBrightness(166)) applied.add(action) else blocked.add(action)
                "BRIGHTNESS_AUTO" -> if (setAutomaticBrightness()) applied.add(action) else blocked.add(action)
                "WALLPAPER_FOCUS", "WALLPAPER_RELAX", "WALLPAPER_COMMUTE", "WALLPAPER_RESET" -> if (applyWallpaperAction(action)) applied.add(action) else blocked.add(action)
                "SEND_DEPARTURE_ALERT" -> if (sendNotification("CAPE commute alert", "Leave soon to stay on time.")) applied.add(action) else blocked.add(action)
                "SOFT_NOTIFICATIONS" -> if (sendNotification("CAPE recovery mode", "Using low-intrusion behavior today.")) applied.add(action) else blocked.add(action)
                "BREAK_REMINDER" -> if (sendNotification("CAPE break reminder", "Take a short reset when you can.")) applied.add(action) else blocked.add(action)
                else -> blocked.add(action)
            }
        }

        return when {
            applied.isNotEmpty() && blocked.isEmpty() -> "Applied: ${applied.joinToString()}"
            applied.isNotEmpty() -> "Applied ${applied.joinToString()}; blocked ${blocked.joinToString()}"
            else -> "Blocked: ${blocked.joinToString()}"
        }
    }

    private fun setDnd(enabled: Boolean): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.isNotificationPolicyAccessGranted) return false
        return try {
            manager.setInterruptionFilter(
                if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun setRinger(mode: Int): Boolean {
        return try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.ringerMode = mode
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun setBrightness(value: Int): Boolean {
        if (!Settings.System.canWrite(context)) return false
        return try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(1, 255))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun setAutomaticBrightness(): Boolean {
        if (!Settings.System.canWrite(context)) return false
        return try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun setWallpaper(resourceId: Int): Boolean {
        return try {
            WallpaperManager.getInstance(context).setResource(resourceId)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun sendNotification(title: String, body: String): Boolean {
        recordNotificationEvent()
        val channelId = "cape_demo"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "CAPE Demo", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun recordNotificationEvent() {
        val prefs = context.getSharedPreferences("cape_context", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val cutoff = now - 30 * 60_000L
        val values = (prefs.getString("notification_events", "") ?: "")
            .split(',')
            .mapNotNull { it.toLongOrNull() }
            .filter { it >= cutoff } + now
        prefs.edit().putString("notification_events", values.joinToString(",")).apply()
    }
}
