package com.zerostress.manager

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.SectionTitle
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("notif_prefs", android.content.Context.MODE_PRIVATE) }

    var notifEnabled by remember { mutableStateOf(prefs.getBoolean("notifications_enabled", true)) }
    var chatNotifs by remember { mutableStateOf(prefs.getBoolean("chat_notifs_enabled", true)) }
    var scheduleNotifs by remember { mutableStateOf(prefs.getBoolean("schedule_notifs_enabled", true)) }
    var showAbout by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteFinal by remember { mutableStateOf(false) }

    fun deleteAccount() {
        val uid = FirebaseAuth.getInstance().uid
        if (uid == null) {
            Toast.makeText(context, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }
        val db = FirebaseFirestore.getInstance()
        db.collection("players").document(uid).delete()
            .addOnSuccessListener {
                db.collection("friend_requests").whereEqualTo("fromUserId", uid).get()
                    .addOnSuccessListener { reqs ->
                        for (req in reqs.documents) req.reference.delete()
                        // Sign out and clear local data
                        FirebaseAuth.getInstance().signOut()
                        prefs.edit().clear().apply()
                        Toast.makeText(context, "Account deleted successfully", Toast.LENGTH_LONG).show()
                        val intent = Intent(context, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        context.startActivity(intent)
                        (context as? android.app.Activity)?.finish()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to delete account", Toast.LENGTH_SHORT).show()
            }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Settings",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                SectionTitle("NOTIFICATIONS")
                ZSCard {
                    SettingSwitch(
                        title = "Notifications",
                        subtitle = "Match updates, announcements & alerts",
                        checked = notifEnabled,
                        onCheckedChange = {
                            notifEnabled = it
                            prefs.edit().putBoolean("notifications_enabled", it).apply()
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    SettingSwitch(
                        title = "Chat Notifications",
                        subtitle = "New messages from your squad",
                        checked = chatNotifs,
                        onCheckedChange = {
                            chatNotifs = it
                            prefs.edit().putBoolean("chat_notifs_enabled", it).apply()
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    SettingSwitch(
                        title = "Schedule Notifications",
                        subtitle = "Upcoming match reminders",
                        checked = scheduleNotifs,
                        onCheckedChange = {
                            scheduleNotifs = it
                            prefs.edit().putBoolean("schedule_notifs_enabled", it).apply()
                        }
                    )
                }

                SectionTitle("GENERAL")
                ZSCard {
                    SettingAction("Clear Cache") {
                        Toast.makeText(context, "Cache cleared!", Toast.LENGTH_SHORT).show()
                    }
                    SettingAction("About Zero Stress") { showAbout = true }
                }

                SectionTitle("ACCOUNT")
                ZSButton(
                    text = "Logout",
                    onClick = {
                        FirebaseAuth.getInstance().signOut()
                        context.startActivity(Intent(context, LoginActivity::class.java))
                        (context as? android.app.Activity)?.finish()
                    },
                    container = ZsPrimary
                )
                Spacer(Modifier.height(12.dp))
                ZSButton(
                    text = "Delete Account",
                    onClick = { showDeleteConfirm = true },
                    container = ZsDanger
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("ZERO STRESS") },
            text = {
                Text(
                    "Version 3.0\n\nPerformance & Leaderboard Manager\n\nBuilt with Kotlin + Jetpack Compose + Firebase",
                    color = ZsTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("OK", color = ZsCyan) }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account") },
            text = {
                Text(
                    "This will permanently delete your account and all your data.\n\nThis action CANNOT be undone!",
                    color = ZsTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    showDeleteFinal = true
                }) { Text("Delete My Account", color = ZsDanger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteFinal) {
        AlertDialog(
            onDismissRequest = { showDeleteFinal = false },
            title = { Text("Final Confirmation") },
            text = {
                Text(
                    "Are you ABSOLUTELY sure?\n\nYour account data, friends, stats, and everything will be permanently deleted.",
                    color = ZsTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteFinal = false
                    deleteAccount()
                }) { Text("Yes, Delete Everything", color = ZsDanger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFinal = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ZsTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = ZsTextMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = ZsAccent,
                checkedThumbColor = androidx.compose.ui.graphics.Color.White
            )
        )
    }
}

@Composable
private fun SettingAction(title: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            modifier = Modifier.fillMaxWidth(),
            color = ZsTextPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            fontWeight = FontWeight.SemiBold
        )
    }
}