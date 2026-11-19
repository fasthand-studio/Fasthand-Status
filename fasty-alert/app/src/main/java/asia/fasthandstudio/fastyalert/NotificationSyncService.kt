package asia.fasthandstudio.fastyalert

import android.app.Service
import android.content.Intent
import android.os.IBinder
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val ALERT_BASE_URL = "https://fastnotifications.fasthand.workers.dev"

class NotificationSyncService : Service() {
    private val client = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private lateinit var deviceId: String
    @Volatile private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        AlertDelivery.ensureChannels(this)
        Thread { registerAndConnect() }.start()
    }

    private fun registerAndConnect() {
        try {
            val registration = AlertDelivery.registerDevice(this)
            deviceId = registration.deviceId
            AlertDelivery.fetchPending(this)
            val wsUrl = "$ALERT_BASE_URL/v1/ws?userId=${registration.userId}&deviceId=$deviceId"
            socket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    setConnectionState("Connected")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching {
                        val message = JSONObject(text)
                        if (message.optString("type") == "push") AlertDelivery.deliver(this@NotificationSyncService, message)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    setConnectionState("Reconnecting")
                    retryConnection()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    setConnectionState("Reconnecting")
                    retryConnection()
                }
            })
        } catch (_: Exception) {
            setConnectionState("Waiting for network")
            retryConnection()
        }
    }

    private fun retryConnection() {
        Thread {
            Thread.sleep(5_000)
            if (!destroyed) registerAndConnect()
        }.start()
    }

    private fun setConnectionState(value: String) {
        getSharedPreferences("fasty_alert", MODE_PRIVATE).edit().putString("connection_state", value).apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        destroyed = true
        socket?.close(1000, "service stopped")
        client.dispatcher.executorService.shutdown()
        setConnectionState("Not connected")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
