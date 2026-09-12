package com.zerostress.manager

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.EmptyState
import com.zerostress.manager.ui.SectionTitle
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSBadge
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.R
import com.zerostress.manager.ui.ZSMenuTile
import com.zerostress.manager.ui.ZsPngIcon
import com.zerostress.manager.ui.ZSStat
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsGreen
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import com.zerostress.manager.ui.theme.ZsWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                AdminDashboardScreen()
            }
        }
    }
}

@Composable
private fun AdminDashboardScreen() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var adminName by remember { mutableStateOf("Admin") }
    var totalPlayers by remember { mutableStateOf(0) }
    var totalMatches by remember { mutableStateOf(0) }
    var players by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // Dialog states
    var targetPlayer by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var showAnnouncementOrSchedule by remember { mutableStateOf(false) }
    var showAnnouncementDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }

    fun loadPlayers() {
        db.collection("players").get()
            .addOnSuccessListener { query ->
                players = query.documents
                loading = false
            }
            .addOnFailureListener { e ->
                loading = false
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun loadAdminInfo() {
        val userId = auth.uid ?: return
        db.collection("players").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) adminName = doc.getString("name") ?: "Admin"
            }
        db.collection("players").get()
            .addOnSuccessListener { query -> totalPlayers = query.size() }
        db.collection("match_logs").get()
            .addOnSuccessListener { query -> totalMatches = query.size() }
    }

    LaunchedEffect(Unit) {
        loadAdminInfo()
        loadPlayers()
    }

    fun updateStatus(uid: String, status: String) {
        db.collection("players").document(uid).update("status", status)
            .addOnSuccessListener {
                Toast.makeText(context, "Status updated!", Toast.LENGTH_SHORT).show()
                loadPlayers()
            }
    }

    fun deletePlayer(uid: String, name: String?) {
        db.collection("players").document(uid).delete()
            .addOnSuccessListener {
                Toast.makeText(context, "Player deleted!", Toast.LENGTH_SHORT).show()
                loadPlayers()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(title = "Admin Dashboard", right = {
                ZsPngIcon(R.drawable.ic_menu_crown, size = 20.dp, tint = ZsGold, modifier = Modifier.padding(end = 8.dp))
            })

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Welcome back, $adminName",
                        color = ZsTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ZSStat("Players", "$totalPlayers", ZsCyan, Modifier.weight(1f))
                        ZSStat("Matches", "$totalMatches", ZsGold, Modifier.weight(1f))
                    }
                }

                item { SectionTitle("QUICK ACTIONS") }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ZSMenuTile("", "Daily Input", { context.startActivity(Intent(context, DailyInputActivity::class.java)) }, Modifier.weight(1f), ZsCyan, iconRes = R.drawable.ic_menu_edit)
                        ZSMenuTile("", "Broadcast", { showAnnouncementOrSchedule = true }, Modifier.weight(1f), ZsAccent, iconRes = R.drawable.ic_menu_announce)
                        ZSMenuTile("", "Leaderboard", { context.startActivity(Intent(context, LeaderboardActivity::class.java)) }, Modifier.weight(1f), ZsGold, iconRes = R.drawable.ic_menu_trophy)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ZSMenuTile("", "Chat", { context.startActivity(Intent(context, ChatActivity::class.java)) }, Modifier.weight(1f), ZsAccent, iconRes = R.drawable.ic_menu_chat)
                        ZSMenuTile("", "Voice Call", { context.startActivity(Intent(context, VoiceActivity::class.java)) }, Modifier.weight(1f), ZsCyan, iconRes = R.drawable.ic_menu_call)
                        ZSMenuTile("", "Seasons", { context.startActivity(Intent(context, ManageSeasonsActivity::class.java)) }, Modifier.weight(1f), ZsPrimary, iconRes = R.drawable.ic_menu_calendar)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ZSMenuTile("", "All Stats", { context.startActivity(Intent(context, ViewAllPlayersStatsActivity::class.java)) }, Modifier.weight(1f), ZsCyan, iconRes = R.drawable.ic_menu_people)
                        ZSMenuTile("", "Notify", { context.startActivity(Intent(context, SendNotificationActivity::class.java)) }, Modifier.weight(1f), ZsGold, iconRes = R.drawable.ic_menu_bell)
                        ZSMenuTile("", "Voice Channels", { context.startActivity(Intent(context, ManageVoiceChannelsActivity::class.java)) }, Modifier.weight(1f), ZsAccent, iconRes = R.drawable.ic_menu_channels)
                    }
                }

                item { SectionTitle("PLAYER MANAGEMENT") }

                if (loading) {
                    item { EmptyState("Loading players...") }
                } else if (players.isEmpty()) {
                    item { EmptyState("No players found") }
                } else {
                    items(players, key = { it.id }) { doc ->
                        val role = doc.getString("role") ?: "player"
                        val status = doc.getString("status") ?: "pending"
                        val gameRole = doc.getString("gameRole")
                        val roleEmoji = when (gameRole) {
                            "Rusher" -> "Rusher • "
                            "Sniper" -> "Sniper • "
                            "IGL" -> "IGL • "
                            "Supporter" -> "Supporter • "
                            "Bomber" -> "Bomber • "
                            else -> ""
                        }
                        val statusColor = when (status) {
                            "approved" -> ZsGreen
                            "pending" -> ZsWarning
                            "rejected" -> ZsDanger
                            "banned" -> ZsDanger
                            else -> ZsTextMuted
                        }

                        ZSCard(onClick = { targetPlayer = doc }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        doc.getString("name") ?: "Unknown",
                                        color = ZsTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "$roleEmoji$role • ${doc.getLong("score") ?: 0} pts",
                                        color = ZsTextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                                ZSBadge(status, statusColor)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Player management dialog ---
    targetPlayer?.let { player ->
        val playerName = player.getString("name") ?: "Unknown"
        var selectedAction by remember { mutableStateOf<Int?>(null) }

        if (selectedAction == null) {
            AlertDialog(
                onDismissRequest = { targetPlayer = null },
                title = { Text(playerName) },
                text = {
                    Column {
                        listOf(
                            "Edit Name" to 0,
                            "Change Admin Role" to 1,
                            "Set Game Role" to 2,
                            "Approve" to 3,
                            "Reject" to 4,
                            "Ban" to 5,
                            "Delete Player" to 6
                        ).forEach { (label, idx) ->
                            TextButton(
                                onClick = { selectedAction = idx },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(label, color = ZsTextPrimary, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { targetPlayer = null }) { Text("Cancel") }
                }
            )
        }

        when (selectedAction) {
            0 -> EditNameDialog(player = player, onDone = {
                selectedAction = null
                targetPlayer = null
                loadPlayers()
            })
            1 -> ChangeRoleDialog(player = player, onDone = {
                selectedAction = null
                targetPlayer = null
                loadPlayers()
            })
            2 -> GameRoleDialog(player = player, onDone = {
                selectedAction = null
                targetPlayer = null
                loadPlayers()
            })
            else -> {
                // Status changes & deletion run in a coroutine (no side effects during composition)
                val action = selectedAction
                if (action != null) {
                    LaunchedEffect(action) {
                        when (action) {
                            3 -> updateStatus(player.id, "approved")
                            4 -> updateStatus(player.id, "rejected")
                            5 -> updateStatus(player.id, "banned")
                            6 -> db.collection("players").document(player.id).delete()
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Player deleted!", Toast.LENGTH_SHORT).show()
                                    loadPlayers()
                                }
                        }
                        selectedAction = null
                        targetPlayer = null
                    }
                }
            }
        }
    }

    // --- Announcement or schedule picker ---
    if (showAnnouncementOrSchedule) {
        AlertDialog(
            onDismissRequest = { showAnnouncementOrSchedule = false },
            title = { Text("What do you want to do?") },
            text = {
                Column {
                    TextButton(onClick = {
                        showAnnouncementOrSchedule = false
                        showScheduleDialog = true
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Create Match Schedule", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = {
                        showAnnouncementOrSchedule = false
                        showAnnouncementDialog = true
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Broadcast Announcement", modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAnnouncementOrSchedule = false }) { Text("Cancel") }
            }
        )
    }

    // --- Announcement dialog ---
    if (showAnnouncementDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAnnouncementDialog = false },
            title = { Text("Broadcast Announcement") },
            text = {
                ZSField(
                    value = text,
                    onValueChange = { text = it },
                    label = "Announcement",
                    placeholder = "Type announcement...",
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.trim().isNotEmpty()) {
                        db.collection("announcements").add(
                            mapOf(
                                "text" to text.trim(),
                                "author" to "Admin",
                                "timestamp" to System.currentTimeMillis()
                            )
                        ).addOnSuccessListener {
                            Toast.makeText(context, "Announcement sent to all players!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showAnnouncementDialog = false
                }) { Text("Send", color = ZsAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showAnnouncementDialog = false }) { Text("Cancel") }
            }
        )
    }

    // --- Schedule dialog ---
    if (showScheduleDialog) {
        var title by remember { mutableStateOf("") }
        var timeStr by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            title = { Text("Add Match Schedule") },
            text = {
                Column {
                    ZSField(value = title, onValueChange = { title = it }, label = "Match Title", placeholder = "e.g., Squad Battle #1")
                    Spacer(Modifier.height(10.dp))
                    ZSField(value = timeStr, onValueChange = { timeStr = it }, label = "Date & Time", placeholder = "2026-09-10 20:00")
                    Spacer(Modifier.height(10.dp))
                    ZSField(value = type, onValueChange = { type = it }, label = "Type", placeholder = "Ranked / Custom / Tournament / Friendly")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showScheduleDialog = false
                    if (title.trim().isEmpty()) {
                        Toast.makeText(context, "Title required", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    var matchTimeMs = System.currentTimeMillis()
                    if (timeStr.trim().isNotEmpty()) {
                        try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            sdf.parse(timeStr.trim())?.let { matchTimeMs = it.time }
                        } catch (e: Exception) {
                            matchTimeMs = System.currentTimeMillis() + 24 * 60 * 60 * 1000
                        }
                    }
                    val t = type.trim()
                    db.collection("match_schedules").add(
                        mapOf(
                            "title" to title.trim(),
                            "description" to (if (t.isEmpty()) "Custom Match" else t),
                            "matchTime" to matchTimeMs,
                            "dateTime" to (if (timeStr.trim().isEmpty()) "TBD" else timeStr.trim()),
                            "status" to "Upcoming",
                            "type" to (if (t.isEmpty()) "Custom" else t),
                            "createdAt" to System.currentTimeMillis(),
                            "createdBy" to auth.uid
                        )
                    ).addOnSuccessListener {
                        Toast.makeText(context, "Schedule created!", Toast.LENGTH_SHORT).show()
                    }.addOnFailureListener { e ->
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Create", color = ZsAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showScheduleDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EditNameDialog(player: DocumentSnapshot, onDone: () -> Unit) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    var name by remember { mutableStateOf(player.getString("name") ?: "") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Edit Player Name") },
        text = { ZSField(value = name, onValueChange = { name = it }, label = "Name") },
        confirmButton = {
            TextButton(onClick = {
                if (name.trim().isNotEmpty()) {
                    db.collection("players").document(player.id).update("name", name.trim())
                        .addOnSuccessListener {
                            Toast.makeText(context, "Name updated to: ${name.trim()}", Toast.LENGTH_SHORT).show()
                            onDone()
                        }
                }
            }) { Text("Save", color = ZsAccent) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

@Composable
private fun ChangeRoleDialog(player: DocumentSnapshot, onDone: () -> Unit) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val roles = listOf("player", "moderator", "admin")
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Change Role for ${player.getString("name")}") },
        text = {
            Column {
                roles.forEach { r ->
                    TextButton(onClick = {
                        db.collection("players").document(player.id).update("role", r)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Role updated to: $r", Toast.LENGTH_SHORT).show()
                                onDone()
                            }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(r, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

@Composable
private fun GameRoleDialog(player: DocumentSnapshot, onDone: () -> Unit) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val gameRoles = listOf(
        "Rusher" to "Rusher",
        "Sniper" to "Sniper",
        "IGL" to "IGL",
        "Supporter" to "Supporter",
        "Bomber" to "Bomber",
        "None" to ""
    )
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Set Game Role for ${player.getString("name")}") },
        text = {
            Column {
                gameRoles.forEach { (label, value) ->
                    TextButton(onClick = {
                        db.collection("players").document(player.id).update("gameRole", value)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Game role set!", Toast.LENGTH_SHORT).show()
                                onDone()
                            }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}