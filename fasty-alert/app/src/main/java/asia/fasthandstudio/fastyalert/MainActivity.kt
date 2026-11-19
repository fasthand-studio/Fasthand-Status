package asia.fasthandstudio.fastyalert

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) connect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FastyAlertScreen(onConnect = ::requestNotificationPermission) }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else connect()
    }

    private fun connect() {
        val intent = Intent(this, NotificationSyncService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }
}

@Composable
private fun FastyAlertScreen(onConnect: () -> Unit) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F4F0)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Fasty Alert", fontSize = 24.sp, color = Color(0xFF20231F))
                Text("FastHand service monitoring", fontSize = 17.sp, color = Color(0xFF686D67))
                Column(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFECEAE4)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("●", color = Color(0xFFD14836), fontSize = 15.sp)
                        Text("Alert delivery", fontSize = 17.sp, color = Color(0xFF20231F))
                    }
                    Text("Connect this device to receive outage and recovery alerts.", fontSize = 15.sp, color = Color(0xFF686D67))
                }
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF285C48)),
                ) {
                    Text("Connect alerts", fontSize = 17.sp, maxLines = 1)
                }
                Text("Notifications are bound to the FastHand operations account.", fontSize = 14.sp, color = Color(0xFF686D67))
            }
        }
    }
}
