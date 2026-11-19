package asia.fasthandstudio.fastyalert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val ALERT_EMAIL = "fuoahgfiasgfiasfidhgafoahgfhacjakljdfhbcuasocgsadfga@fasthandstudio.asia"
private const val ALERT_BASE_URL = "https://fastnotifications.fasthand.workers.dev"
private const val CHANNEL_CRITICAL = "fasty_critical_v2"
private const val CHANNEL_MAJOR = "fasty_major_v2"
private const val CHANNEL_MINOR = "fasty_minor_v2"
private const val CHANNEL_INFO = "fasty_info_v2"
private const val CHANNEL_RECOVERY = "fasty_recovery_v2"

data class DeviceRegistration(val userId: String, val deviceId: String)

object AlertDelivery {
    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    fun registerDevice(context: Context): DeviceRegistration {
        val prefs = context.getSharedPreferences("fasty_alert", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null) ?: "and-${UUID.randomUUID()}".also {
            prefs.edit().putString("device_id", it).apply()
        }
        val payload = JSONObject().put("email", ALERT_EMAIL).put("deviceId", deviceId).put("platform", "android")
        val request = Request.Builder().url("$ALERT_BASE_URL/v1/devices/register")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        val userId = client.newCall(request).execute().use { response ->
            val result = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful) throw IOException("Device registration failed (${response.code})")
            result.optString("userId").takeIf(String::isNotBlank)
                ?: throw IOException("Device registration returned no user id")
        }
        prefs.edit().putString("user_id", userId).apply()
        return DeviceRegistration(userId, deviceId)
    }

    fun fetchPending(context: Context) {
        ensureChannels(context)
        val request = Request.Builder().url("$ALERT_BASE_URL/v1/notifications/pending")
            .header("X-User-Email", ALERT_EMAIL).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Pending alert request failed (${response.code})")
            val items = JSONArray(response.body?.string().orEmpty())
            for (i in 0 until items.length()) deliver(context, items.getJSONObject(i))
        }
    }

    fun deliver(context: Context, message: JSONObject) {
        val id = message.optString("id")
        val title = message.optString("title", "Fasty Alert")
        val body = message.optString("body", "A monitored service needs attention.")
        val payload = when (val raw = message.opt("data")) {
            is JSONObject -> raw
            is String -> runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
            else -> JSONObject()
        }
        val state = payload.optString("state", "info")
        val severity = payload.optString("severity", if (state == "recovered") "recovered" else "info")
        val createdAt = messageTime(message)
        val ttlDays = AlertRetention.get(context)
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ttlDays.toLong())
        val saved = AlertStore(context).use { store ->
            if (createdAt < cutoff) {
                store.purgeExpired(ttlDays)
                false
            } else {
                store.save(
                    AlertRecord(
                        id = id.ifBlank { "${createdAt}_${title.hashCode()}_${body.hashCode()}" },
                        title = title,
                        body = body,
                        entityType = payload.optString("entityType", "incident"),
                        entityId = payload.optString("entityId"),
                        event = payload.optString("event", "alert"),
                        severity = severity,
                        receivedAt = createdAt,
                    ),
                    ttlDays,
                )
            }
        }
        if (!saved) {
            acknowledge(id)
            return
        }

        val channel = channelFor(severity)
        val intent = PendingIntent.getActivity(
            context,
            id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                if (payload.optString("entityType") == "incident") {
                    putExtra(EXTRA_INCIDENT_ID, payload.optString("entityId"))
                }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val critical = severity == "critical"
        val major = severity == "major"
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(if (critical || major) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (critical || major) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_EVENT)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id.hashCode(), notification)
        acknowledge(id)
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        createChannel(context, manager, CHANNEL_CRITICAL, "Critical outage alarm", R.raw.alert_critical, true, NotificationManager.IMPORTANCE_HIGH)
        createChannel(context, manager, CHANNEL_MAJOR, "Major service alert", R.raw.alert_major, true, NotificationManager.IMPORTANCE_HIGH)
        createChannel(context, manager, CHANNEL_MINOR, "Minor service alert", R.raw.alert_minor, false, NotificationManager.IMPORTANCE_DEFAULT)
        createChannel(context, manager, CHANNEL_INFO, "Service updates", R.raw.alert_info, false, NotificationManager.IMPORTANCE_DEFAULT)
        createChannel(context, manager, CHANNEL_RECOVERY, "Service recovered", R.raw.alert_recovered, false, NotificationManager.IMPORTANCE_DEFAULT)
    }

    private fun createChannel(
        context: Context,
        manager: NotificationManager,
        id: String,
        name: String,
        soundId: Int,
        alarm: Boolean,
        importance: Int,
    ) {
        val sound = Uri.parse("android.resource://${context.packageName}/$soundId")
        val usage = if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT
        val audio = AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        manager.createNotificationChannel(NotificationChannel(id, name, importance).apply {
            description = name
            setSound(sound, audio)
            enableVibration(alarm)
            if (alarm) vibrationPattern = longArrayOf(0, 900, 180, 900, 180, 900)
        })
    }

    private fun acknowledge(id: String) {
        if (id.isBlank()) return
        val request = Request.Builder().url("$ALERT_BASE_URL/v1/notifications/$id/ack").post("".toRequestBody()).build()
        runCatching { client.newCall(request).execute().close() }
    }

    private fun messageTime(message: JSONObject): Long {
        val timestamp = message.optLong("timestamp", 0L)
        if (timestamp > 0L) return timestamp
        val raw = message.optString("created_at")
        if (raw.isBlank()) return System.currentTimeMillis()
        return runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrElse {
            runCatching {
                val value = raw.replace(' ', 'T').let { if (it.endsWith("Z")) it else "${it}Z" }
                java.time.Instant.parse(value).toEpochMilli()
            }.getOrDefault(System.currentTimeMillis())
        }
    }

    private fun channelFor(severity: String): String = when (severity) {
        "critical" -> CHANNEL_CRITICAL
        "major" -> CHANNEL_MAJOR
        "minor" -> CHANNEL_MINOR
        "recovered" -> CHANNEL_RECOVERY
        else -> CHANNEL_INFO
    }
}
