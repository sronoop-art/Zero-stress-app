package com.zerostress.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsTextSecondary

class SendNotificationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                SendNotificationScreen()
            }
        }
    }
}

@Composable
private fun SendNotificationScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val quickAlerts = listOf(
        "Match starting in 5 minutes!",
        "Tournament begins now!",
        "Server maintenance in 10 minutes",
        "Double XP event active!",
        "New season coming soon!"
    )

    fun sendNotification(t: String, m: String) {
        if (t.trim().isEmpty()) {
            Toast.makeText(context, "Enter title", Toast.LENGTH_SHORT).show()
            return
        }
        if (m.trim().isEmpty()) {
            Toast.makeText(context, "Enter message", Toast.LENGTH_SHORT).show()
            return
        }
        loading = true
        db.collection("notifications").add(
            mapOf(
                "title" to t.trim(),
                "message" to m.trim(),
                "type" to "admin",
                "timestamp" to System.currentTimeMillis(),
                "sentBy" to "Admin"
            )
        ).addOnSuccessListener { docRef ->
            docRef.update("id", docRef.id)
            loading = false
            Toast.makeText(
                context,
                "✅ Notification sent!\n\n• Foreground: instant in-app display\n• Background/Killed: FCM push via Cloud Function",
                Toast.LENGTH_LONG
            ).show()
            title = ""
            message = ""
        }.addOnFailureListener { e ->
            loading = false
            Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        FirebaseMessaging.getInstance().subscribeToTopic("all_players")
        FirebaseMessaging.getInstance().subscribeToTopic("match_updates")
        FirebaseMessaging.getInstance().subscribeToTopic("announcements")
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Send Notification",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                ZSCard {
                    ZSField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Title",
                        placeholder = "e.g., 📢 Quick Alert"
                    )
                    Spacer(Modifier.height(12.dp))
                    ZSField(
                        value = message,
                        onValueChange = { message = it },
                        label = "Message",
                        placeholder = "Write your announcement...",
                        minLines = 3
                    )
                    Spacer(Modifier.height(16.dp))
                    if (loading) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = ZsCyan)
                        }
                    } else {
                        ZSButton(text = "📤 SEND TO ALL PLAYERS", onClick = { sendNotification(title, message) }, container = ZsAccent)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("QUICK ALERTS", color = ZsTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                quickAlerts.forEach { alert ->
                    TextButton(onClick = { sendNotification("📢 Quick Alert", alert) }, modifier = Modifier.fillMaxWidth()) {
                        Text(alert, modifier = Modifier.fillMaxWidth(), color = ZsTextSecondary)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}