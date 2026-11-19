package asia.fasthandstudio.fastyalert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val RECIPIENT = "fuoahgfiasgfiasfidhgafoahgfhacjakljdfhbcuasocgsadfga@fasthandstudio.asia"
private const val BASE_URL = "https://fastnotifications.fasthand.workers.dev"
private const val CHANNEL_ALERT = "fasty_outage_alerts"
private const val CHANNEL_SERVICE = "fasty_connection"

class NotificationSyncService : Service() {
    private val client = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var userId: String? = null
    private lateinit var deviceId: String

    override fun onCreate() {
        super.onCreate()
        createChannels()
        val prefs = getSharedPreferences("fasty_alert", MODE_PRIVATE)
        deviceId = prefs.getString("device_id", null) ?: "and-${UUID.randomUUID()}".also {
            prefs.edit().putString("device_id", it).apply()
        }
        startForeground(100, serviceNotification("Connecting to alert service"))
        Thread { registerAndConnect() }.start()
    }

    private fun registerAndConnect() {
        try {
            val payload = JSONObject().put("email", RECIPIENT).put("deviceId", deviceId).put("platform", "android")
            val request = Request.Builder().url("$BASE_URL/v1/devices/register")
                .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                val result = JSONObject(response.body?.string().orEmpty())
                if (!response.isSuccessful) throw IllegalStateException("Device registration failed (${response.code})")
                userId = result.getString("userId")
            }
            fetchPending()
            val wsUrl = "wss://fastnotifications.fasthand.workers.dev/v1/ws?userId=$userId&deviceId=$deviceId"
            socket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    updateServiceNotification("Connected · listening for outages")
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val message = JSONObject(text)
                        if (message.optString("type") == "push") deliver(message)
                    } catch (_: Exception) { }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    updateServiceNotification("Connection interrupted · retrying")
                    Thread.sleep(3_000)
                    registerAndConnect()
                }
            })
        } catch (_: Exception) {
            updateServiceNotification("Connection unavailable · retrying")
            Thread { Thread.sleep(5_000); registerAndConnect() }.start()
        }
    }

    private fun fetchPending() {
        val request = Request.Builder().url("$BASE_URL/v1/notifications/pending")
            .header("X-User-Email", RECIPIENT).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            val items = org.json.JSONArray(response.body?.string().orEmpty())
            for (i in 0 until items.length()) deliver(items.getJSONObject(i))
        }
    }

    private fun deliver(message: JSONObject) {
        val id = message.optString("id")
        val title = message.optString("title", "Fasty Alert")
        val body = message.optString("body", "A monitored service needs attention.")
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM).build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(id.hashCode(), notification)
        if (id.isNotBlank()) {
            val request = Request.Builder().url("$BASE_URL/v1/notifications/$id/ack").post("".toRequestBody()).build()
            Thread { runCatching { client.newCall(request).execute().close() } }.start()
        }
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val sound = Uri.parse("android.resource://$packageName/${R.raw.alert_tone}")
            val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ALERT, "Outage alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Urgent service outage and recovery notices"
                setSound(sound, audio)
                enableVibration(true)
            })
            manager.createNotificationChannel(NotificationChannel(CHANNEL_SERVICE, "Alert connection", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun serviceNotification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL_SERVICE)
        .setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("Fasty Alert").setContentText(text)
        .setOngoing(true).build()

    private fun updateServiceNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(100, serviceNotification(text))
    }

    override fun onDestroy() { socket?.close(1000, "service stopped"); client.dispatcher.executorService.shutdown(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
