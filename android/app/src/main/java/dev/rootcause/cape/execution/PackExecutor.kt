package dev.rootcause.cape.execution

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.rootcause.cape.core.CapeDecision

class PackExecutor(private val context: Context) {
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
                "BRIGHTNESS_65" -> if (setBrightness(166)) applied.add(action) else blocked.add(action)
                "BRIGHTNESS_AUTO" -> if (setAutomaticBrightness()) applied.add(action) else blocked.add(action)
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
        manager.setInterruptionFilter(
            if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return true
    }

    private fun setRinger(mode: Int): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.ringerMode = mode
        return true
    }

    private fun setBrightness(value: Int): Boolean {
        if (!Settings.System.canWrite(context)) return false
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(1, 255))
        return true
    }

    private fun setAutomaticBrightness(): Boolean {
        if (!Settings.System.canWrite(context)) return false
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        return true
    }

    private fun sendNotification(title: String, body: String): Boolean {
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
        } catch (_: SecurityException) {
            false
        }
    }
}
