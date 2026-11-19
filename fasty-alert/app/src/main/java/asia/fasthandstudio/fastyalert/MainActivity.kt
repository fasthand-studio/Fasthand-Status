package asia.fasthandstudio.fastyalert

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

private val White = Color(0xFFFFFFFF)
private val Ink = Color(0xFF171A18)
private val Muted = Color(0xFF707570)
private val Green = Color(0xFF1E5A43)
private val Red = Color(0xFFC52828)
private val Orange = Color(0xFFB86610)
private val Blue = Color(0xFF315EAA)
internal const val EXTRA_INCIDENT_ID = "fasty_incident_id"

class MainActivity : ComponentActivity() {
    private val requestedIncidentId = mutableStateOf<String?>(null)
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) connect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        getSystemService(NotificationManager::class.java).cancel(100)
        requestedIncidentId.value = intent.getStringExtra(EXTRA_INCIDENT_ID)
        setContent {
            FastyAlertScreen(
                onConnect = ::requestNotificationPermission,
                requestedIncidentId = requestedIncidentId.value,
            )
        }
        AlertPolling.schedule(this)
        requestNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedIncidentId.value = intent.getStringExtra(EXTRA_INCIDENT_ID)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else connect()
    }

    private fun connect() {
        val intent = Intent(this, NotificationSyncService::class.java)
        startService(intent)
    }
}

@Composable
private fun FastyAlertScreen(onConnect: () -> Unit, requestedIncidentId: String?) {
    val context = LocalContext.current
    var ttlDays by remember { mutableIntStateOf(AlertRetention.get(context)) }
    var alerts by remember { mutableStateOf(emptyList<AlertRecord>()) }
    var connection by remember { mutableStateOf("Connecting to alert service") }
    var selectedIncidentId by remember { mutableStateOf(requestedIncidentId) }
    val store = remember(context) { AlertStore(context.applicationContext) }

    DisposableEffect(store) {
        onDispose { store.close() }
    }

    LaunchedEffect(ttlDays) {
        while (isActive) {
            val snapshot = withContext(Dispatchers.IO) { store.getAlerts(ttlDays) }
            alerts = snapshot
            connection = context.getSharedPreferences("fasty_alert", android.content.Context.MODE_PRIVATE)
                .getString("connection_state", "Not connected") ?: "Not connected"
            delay(3_000)
        }
    }

    LaunchedEffect(requestedIncidentId) {
        if (!requestedIncidentId.isNullOrBlank()) selectedIncidentId = requestedIncidentId
    }

    BackHandler(enabled = selectedIncidentId != null) { selectedIncidentId = null }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = White) {
            if (selectedIncidentId != null) {
                IncidentConversation(incidentId = selectedIncidentId!!, onBack = { selectedIncidentId = null })
            } else Column(
                modifier = Modifier.fillMaxSize().background(White).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Fasty Alert", fontSize = 24.sp, color = Ink)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("●", color = if (connection.startsWith("Connected")) Green else Orange, fontSize = 14.sp)
                    Text(connection, fontSize = 15.sp, color = Muted)
                }
                CapsuleControl(
                    label = if (connection.startsWith("Connected")) "Alerts connected" else "Connect alerts",
                    selected = true,
                    enabled = !connection.startsWith("Connected"),
                    height = 44,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onConnect,
                )
                Text("Alerts are saved on this device", fontSize = 17.sp, color = Ink)
                Text("Keep for", fontSize = 15.sp, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    AlertRetention.choices.forEach { days ->
                        CapsuleControl(
                            label = "${days}d",
                            selected = ttlDays == days,
                            height = 32,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                ttlDays = days
                                AlertRetention.set(context, days)
                            },
                        )
                    }
                }
                Text("Messages · ${alerts.size}", fontSize = 17.sp, color = Ink)
                if (alerts.isEmpty()) {
                    Text("Incident and maintenance updates will appear here.", fontSize = 15.sp, color = Muted)
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(alerts, key = { it.id }) { alert ->
                            AlertHistoryRow(alert) {
                                if (alert.entityType == "incident" && alert.entityId.isNotBlank()) {
                                    selectedIncidentId = alert.entityId
                                }
                            }
                        }
                    }
                }
                Text("FastHand service monitoring", fontSize = 14.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun CapsuleControl(
    label: String,
    selected: Boolean,
    height: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = remember { superellipseShape() }
    Box(
        modifier = modifier.height(height.dp).clip(shape)
            .background(if (selected) Green else White)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (selected) White else Ink, maxLines = 1)
    }
}

@Composable
private fun AlertHistoryRow(alert: AlertRecord, onClick: () -> Unit) {
    val (accent, label) = when (alert.severity) {
        "critical" -> Red to "Critical"
        "major" -> Orange to "Major"
        "minor" -> Blue to "Minor"
        "none" -> Muted to "No impact"
        "recovered" -> Green to "Recovered"
        else -> Muted to "Update"
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth().clip(superellipseShape())
            .background(Color(0xFFF3F4F3))
            .clickable(enabled = alert.entityType == "incident" && alert.entityId.isNotBlank(), onClick = onClick)
            .padding(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("●", color = accent, fontSize = 12.sp)
            Text(label, fontSize = 14.sp, color = accent)
            Text(alert.entityType.replaceFirstChar { it.uppercase() }, fontSize = 14.sp, color = Muted)
        }
        Text(alert.title, fontSize = 17.sp, color = Ink)
        Text(alert.body, fontSize = 17.sp, color = Muted)
        Text(android.text.format.DateFormat.format("MMM d, yyyy · h:mm a", alert.receivedAt).toString(), fontSize = 13.sp, color = Muted)
    }
}

@Composable
private fun IncidentConversation(incidentId: String, onBack: () -> Unit) {
    var incident by remember(incidentId) { mutableStateOf<IncidentDetails?>(null) }
    var error by remember(incidentId) { mutableStateOf<String?>(null) }
    var retry by remember(incidentId) { mutableIntStateOf(0) }

    LaunchedEffect(incidentId, retry) {
        while (isActive) {
            val loaded = withContext(Dispatchers.IO) { runCatching { IncidentApi.load(incidentId) } }
            loaded.onSuccess {
                incident = it
                error = null
            }.onFailure {
                error = it.message ?: "Could not load incident details."
            }
            if (incident?.resolvedAt != null) break
            delay(20_000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(White).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CapsuleControl(label = "‹ Messages", selected = false, height = 32, onClick = onBack)
            Text("Incident", fontSize = 20.sp, color = Ink)
        }
        val detail = incident
        if (detail == null) {
            Text(error ?: "Loading incident…", fontSize = 17.sp, color = Muted)
            if (error != null) CapsuleControl(label = "Try again", selected = true, height = 44, onClick = { retry += 1 })
            Spacer(modifier = Modifier.weight(1f))
        } else {
            val (impactColor, impactLabel) = when (detail.impact) {
                "critical" -> Red to "Critical impact"
                "major" -> Orange to "Major impact"
                "minor" -> Blue to "Minor impact"
                else -> Muted to "No impact"
            }
            Text(detail.title, fontSize = 24.sp, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(statusLabel(detail.status), fontSize = 15.sp, color = Muted)
                Text(impactLabel, fontSize = 15.sp, color = impactColor)
            }
            Text("Started ${formatIncidentTime(detail.startedAt)}", fontSize = 14.sp, color = Muted)
            if (detail.services.isNotEmpty()) {
                Text("Affected: ${detail.services.joinToString()}", fontSize = 15.sp, color = Muted)
            }
            Text("Conversation", fontSize = 17.sp, color = Ink)
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(detail.updates) { update ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(superellipseShape())
                            .background(Color(0xFFF3F4F3)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(statusLabel(update.status), fontSize = 14.sp, color = Green)
                            Text(update.author, fontSize = 14.sp, color = Muted)
                        }
                        Text(update.body, fontSize = 17.sp, color = Ink)
                        Text(formatIncidentTime(update.createdAt), fontSize = 13.sp, color = Muted)
                    }
                }
            }
        }
        Text("FastHand service monitoring", fontSize = 14.sp, color = Muted)
    }
}

private fun statusLabel(status: String): String = when (status) {
    "investigating" -> "Investigating"
    "identified" -> "Identified"
    "monitoring" -> "Monitoring"
    "resolved" -> "Resolved"
    else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun formatIncidentTime(value: String): String {
    val timestamp = runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
    return if (timestamp == null) value else android.text.format.DateFormat.format("MMM d, yyyy · h:mm a", timestamp).toString()
}

private fun superellipseShape(exponent: Double = 4.0): Shape = GenericShape { size: Size, _ ->
    val radius = minOf(size.width, size.height) / 2f
    val width = size.width
    val height = size.height
    moveTo(radius, 0f)
    lineTo(width - radius, 0f)
    appendCorner(this, width - radius, radius, radius, -Math.PI / 2.0, 0.0, exponent)
    lineTo(width, height - radius)
    appendCorner(this, width - radius, height - radius, radius, 0.0, Math.PI / 2.0, exponent)
    lineTo(radius, height)
    appendCorner(this, radius, height - radius, radius, Math.PI / 2.0, Math.PI, exponent)
    lineTo(0f, radius)
    appendCorner(this, radius, radius, radius, Math.PI, Math.PI * 1.5, exponent)
    close()
}

private fun appendCorner(path: Path, cx: Float, cy: Float, radius: Float, start: Double, end: Double, exponent: Double) {
    val steps = 12
    for (step in 1..steps) {
        val angle = start + (end - start) * step / steps
        val cos = kotlin.math.cos(angle)
        val sin = kotlin.math.sin(angle)
        val x = cx + radius * sign(cos).toFloat() * abs(cos).pow(2.0 / exponent).toFloat()
        val y = cy + radius * sign(sin).toFloat() * abs(sin).pow(2.0 / exponent).toFloat()
        path.lineTo(x, y)
    }
}
