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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsGreen
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

class FriendsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                FriendsScreen()
            }
        }
    }
}

private data class FriendRow(
    val id: String,
    val name: String,
    val detail: String,
    val online: Boolean?,
    val isRequest: Boolean
)

@Composable
private fun FriendsScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = FirebaseAuth.getInstance().uid

    var userName by remember { mutableStateOf("Player") }
    var rows by remember { mutableStateOf<List<FriendRow>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    fun loadData() {
        if (userId == null) return
        val pending = mutableListOf<FriendRow>()
        val friendDocs = mutableListOf<DocumentSnapshot>()

        fun finish() {
            val friendRows = friendDocs.distinct().mapNotNull { doc ->
                val friendId = if (doc.getString("userId1") == userId) {
                    doc.getString("userId2")
                } else {
                    doc.getString("userId1")
                } ?: return@mapNotNull null
                FriendRow(friendId, "Loading...", "", null, false)
            }
            // Fetch friend details + online status
            rows = pending + friendRows
            friendRows.forEachIndexed { index, row ->
                db.collection("players").document(row.id).get()
                    .addOnSuccessListener { playerDoc ->
                        if (playerDoc.exists()) {
                            val rank = playerDoc.getString("rank") ?: "Player"
                            val gameRole = playerDoc.getString("gameRole")
                            val detail = if (!gameRole.isNullOrEmpty()) "$gameRole • $rank" else rank
                            val lastSeen = playerDoc.getLong("lastSeen")
                            val online = lastSeen != null && System.currentTimeMillis() - lastSeen < 300000
                            rows = rows.mapIndexed { i, r ->
                                if (i == pending.size + index) {
                                    r.copy(
                                        name = playerDoc.getString("name") ?: "Unknown",
                                        detail = detail,
                                        online = online
                                    )
                                } else r
                            }
                        }
                    }
            }
        }

        db.collection("friend_requests")
            .whereEqualTo("toUserId", userId)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { query ->
                pending.addAll(query.documents.map { req ->
                    FriendRow(
                        id = req.id,
                        name = req.getString("fromUserName") ?: "Unknown",
                        detail = "Wants to be your friend",
                        online = null,
                        isRequest = true
                    )
                })
                // Load friendships
                db.collection("friendships").whereEqualTo("userId1", userId).get()
                    .addOnSuccessListener { s1 ->
                        friendDocs.addAll(s1.documents)
                        db.collection("friendships").whereEqualTo("userId2", userId).get()
                            .addOnSuccessListener { s2 ->
                                friendDocs.addAll(s2.documents)
                                finish()
                            }
                    }
            }
    }

    LaunchedEffect(refreshKey) {
        loadData()
    }

    DisposableEffect(Unit) {
        // Live updates for pending requests
        val listener = if (userId != null) {
            db.collection("friend_requests")
                .whereEqualTo("toUserId", userId)
                .whereEqualTo("status", "pending")
                .addSnapshotListener { _, _ -> refreshKey++ }
        } else null
        onDispose { listener?.remove() }
    }

    fun sendFriendRequest(targetName: String) {
        db.collection("players").whereEqualTo("name", targetName).get()
            .addOnSuccessListener { query ->
                if (query.isEmpty()) {
                    Toast.makeText(context, "Player \"$targetName\" not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val target = query.documents[0]
                val targetId = target.id
                if (targetId == userId) {
                    Toast.makeText(context, "Can't add yourself!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                db.collection("friend_requests").document("${userId}_$targetId").set(
                    mapOf(
                        "fromUserId" to userId,
                        "fromUserName" to userName,
                        "toUserId" to targetId,
                        "status" to "pending",
                        "timestamp" to System.currentTimeMillis()
                    )
                ).addOnSuccessListener {
                    Toast.makeText(context, "Friend request sent to $targetName!", Toast.LENGTH_SHORT).show()
                    db.collection("notifications").add(
                        mapOf(
                            "title" to "🤝 Friend Request",
                            "message" to "$userName sent you a friend request!\nOpen Friends to accept.",
                            "type" to "general",
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }.addOnFailureListener { e ->
                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Search failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun acceptRequest(row: FriendRow) {
        db.collection("friend_requests").document(row.id).get()
            .addOnSuccessListener { req ->
                val fromUserId = req.getString("fromUserId")
                val fromUserName = req.getString("fromUserName")
                if (fromUserId != null) {
                    db.collection("friendships").document("${fromUserId}_$userId").set(
                        mapOf(
                            "userId1" to fromUserId,
                            "userId2" to userId,
                            "createdAt" to System.currentTimeMillis()
                        )
                    ).addOnSuccessListener {
                        req.reference.update("status", "accepted")
                        Toast.makeText(context, "✅ $fromUserName is now your friend!", Toast.LENGTH_SHORT).show()
                        refreshKey++
                    }
                }
            }
    }

    fun rejectRequest(row: FriendRow) {
        db.collection("friend_requests").document(row.id).update("status", "rejected")
            .addOnSuccessListener {
                Toast.makeText(context, "Request rejected", Toast.LENGTH_SHORT).show()
                refreshKey++
            }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Friends",
                onBack = { (context as? android.app.Activity)?.finish() },
                right = {
                    TextButton(onClick = { showAddDialog = true }) {
                        Text("+ Add", color = ZsCyan, fontWeight = FontWeight.Bold)
                    }
                }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Spacer(Modifier.height(4.dp))
                    SectionTitle("FRIEND REQUESTS")
                }
                if (rows.none { it.isRequest }) {
                    item { EmptyState("No pending requests") }
                } else {
                    items(rows.filter { it.isRequest }, key = { it.id }) { row ->
                        ZSCard(highlight = ZsAccent) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("📨 ${row.name}", color = ZsTextPrimary, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(3.dp))
                                    Text(row.detail, color = ZsTextMuted, fontSize = 12.sp)
                                }
                                TextButton(onClick = { acceptRequest(row) }) {
                                    Text("Accept", color = ZsGreen, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { rejectRequest(row) }) {
                                    Text("Reject", color = ZsDanger)
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    SectionTitle("MY FRIENDS (${rows.count { !it.isRequest }})")
                }
                if (rows.none { !it.isRequest }) {
                    item { EmptyState("No friends yet — add some above!") }
                } else {
                    items(rows.filter { !it.isRequest }, key = { it.id }) { row ->
                        ZSCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.name, color = ZsTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(Modifier.height(3.dp))
                                    Text(row.detail, color = ZsTextSecondary, fontSize = 12.sp)
                                }
                                Text(
                                    when (row.online) {
                                        true -> "🟢 Online"
                                        false -> "⚫ Offline"
                                        null -> "..."
                                    },
                                    color = if (row.online == true) ZsGreen else ZsTextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var inputName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("🤝 Add Friend") },
            text = {
                ZSField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    label = "Player name"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    if (inputName.trim().isNotEmpty()) sendFriendRequest(inputName.trim())
                }) { Text("Send Request", color = ZsAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}