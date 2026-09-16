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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.zerostress.manager.R
import com.zerostress.manager.ui.EmptyState
import com.zerostress.manager.ui.ZsPngIcon
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.formatDate
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsAccentDark
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

class ManageSeasonsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                ManageSeasonsScreen()
            }
        }
    }
}

@Composable
private fun ManageSeasonsScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    var seasons by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var resetTarget by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var showResetBoards by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun loadSeasons() {
        db.collection("seasons").orderBy("createdAt").get()
            .addOnSuccessListener { query -> seasons = query.documents }
    }

    LaunchedEffect(Unit) {
        loadSeasons()
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Manage Seasons",
                onBack = { (context as? android.app.Activity)?.finish() },
                right = {
                    Row {
                        TextButton(onClick = { showResetBoards = true }) {
                            Text("Reset Boards", color = ZsDanger, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { showAddDialog = true }) {
                            Text("+ Add", color = ZsCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )

            if (seasons.isEmpty()) {
                EmptyState("No seasons yet — tap + Add to create one")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(seasons, key = { it.id }) { doc ->
                        val active = doc.getBoolean("active") != false
                        val createdAt = doc.getLong("createdAt")
                        ZSCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        doc.getString("name") ?: "Season",
                                        color = ZsTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        doc.getString("description") ?: "",
                                        color = ZsTextSecondary,
                                        fontSize = 13.sp
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        buildString {
                                            append("Duration: ${doc.getString("duration") ?: "30"} days")
                                            if (createdAt != null) append(" • ${formatDate(createdAt)}")
                                        },
                                        color = ZsTextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        if (active) "● ACTIVE" else "○ INACTIVE",
                                        color = if (active) ZsAccentDark else ZsTextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    TextButton(
                                        enabled = active && !busy,
                                        onClick = { resetTarget = doc }
                                    ) {
                                        Text("End & Reset", color = ZsDanger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(onClick = { deleteTarget = doc }) {
                                        ZsPngIcon(R.drawable.ic_action_delete, size = 18.dp, tint = ZsDanger)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var duration by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add New Season") },
            text = {
                Column {
                    ZSField(value = name, onValueChange = { name = it }, label = "Season Name", placeholder = "e.g., Season 2")
                    Spacer(Modifier.height(10.dp))
                    ZSField(value = desc, onValueChange = { desc = it }, label = "Description")
                    Spacer(Modifier.height(10.dp))
                    ZSField(value = duration, onValueChange = { duration = it }, label = "Duration (days)")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    if (name.trim().isEmpty()) {
                        Toast.makeText(context, "Name required", Toast.LENGTH_SHORT).show()
                    } else {
                        db.collection("seasons").add(
                            mapOf(
                                "name" to name.trim(),
                                "description" to desc.trim(),
                                "duration" to (if (duration.trim().isEmpty()) "30" else duration.trim()),
                                "active" to true,
                                "createdAt" to System.currentTimeMillis()
                            )
                        ).addOnSuccessListener {
                            Toast.makeText(context, "Season created!", Toast.LENGTH_SHORT).show()
                            loadSeasons()
                        }
                    }
                }) { Text("Create", color = ZsAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Season") },
            text = { Text("Delete \"${doc.getString("name")}\"?", color = ZsTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    db.collection("seasons").document(doc.id).delete()
                        .addOnSuccessListener {
                            Toast.makeText(context, "Season deleted", Toast.LENGTH_SHORT).show()
                            loadSeasons()
                        }
                    deleteTarget = null
                }) { Text("Delete", color = ZsDanger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Manual "End Season & Reset" (replaces the auto-reset Cloud Function,
    // which requires the paid Blaze plan): rewards the top 3, ends the
    // season and zeroes the daily/weekly/monthly leaderboards.
    // Callback-style Firestore chaining - no coroutines needed.
    fun failReset(e: Exception) {
        busy = false
        resetTarget = null
        Toast.makeText(context, "Reset failed: ${e.message}", Toast.LENGTH_LONG).show()
    }

    fun runSeasonReset(doc: DocumentSnapshot) {
        busy = true
        val now = System.currentTimeMillis()
        val seasonName = doc.getString("name") ?: "the season"
        val rewards = listOf(doc.getLong("topRewardCoins") ?: 500L, 300L, 150L)

        // 1) Reward the top-3 approved players by all-time score.
        db.collection("players")
            .whereEqualTo("status", "approved")
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(3)
            .get()
            .addOnFailureListener { failReset(it) }
            .addOnSuccessListener { top ->
                val batch = db.batch()
                top.documents.forEachIndexed { index, p ->
                    val reward = rewards.getOrElse(index) { 0L }
                    batch.update(p.reference, "coins", FieldValue.increment(reward))
                    batch.set(
                        db.collection("notifications").document(),
                        mapOf(
                            "uid" to p.id,
                            "title" to "Season ended - you placed #${index + 1}!",
                            "message" to "You earned $reward coins in $seasonName.",
                            "type" to "achievement",
                            "timestamp" to now
                        )
                    )
                }
                // 2) End the season.
                batch.update(doc.reference, mapOf("active" to false, "endedAt" to now))
                batch.commit()
                    .addOnFailureListener { failReset(it) }
                    .addOnSuccessListener {
                        // 3) Zero every leaderboard tier, one batch at a time.
                        db.collection("players")
                            .whereEqualTo("status", "approved")
                            .get()
                            .addOnFailureListener { failReset(it) }
                            .addOnSuccessListener { approved ->
                                val tiers = listOf(
                                    listOf("dailyScore", "dailyWins", "dailyKills"),
                                    listOf("weeklyScore", "weeklyWins", "weeklyKills"),
                                    listOf("monthlyScore", "monthlyWins", "monthlyKills")
                                )
                                fun commitTier(i: Int) {
                                    if (i >= tiers.size) {
                                        busy = false
                                        resetTarget = null
                                        Toast.makeText(context, "Season ended & leaderboards reset", Toast.LENGTH_LONG).show()
                                        loadSeasons()
                                        return
                                    }
                                    val f = tiers[i]
                                    val rb = db.batch()
                                    for (p in approved.documents) {
                                        rb.update(p.reference, mapOf(f[0] to 0L, f[1] to 0L, f[2] to 0L))
                                    }
                                    rb.commit().addOnCompleteListener { commitTier(i + 1) }
                                }
                                commitTier(0)
                            }
                    }
            }
    }

    resetTarget?.let { doc ->
        AlertDialog(
            onDismissRequest = { if (!busy) resetTarget = null },
            title = { Text("End Season & Reset") },
            text = {
                Text(
                    "End \"${doc.getString("name")}\"?\n\n" +
                        "• Top 3 players get coin rewards\n" +
                        "• Daily, weekly & monthly leaderboards reset to 0\n" +
                        "• The season becomes inactive\n\n" +
                        "All-time scores are kept.",
                    color = ZsTextSecondary
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    runSeasonReset(doc)
                }) {
                    Text(if (busy) "Resetting…" else "End & Reset", color = ZsDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { resetTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Standalone leaderboard reset - clears daily/weekly/monthly boards
    // WITHOUT ending the season (replaces the reset Cloud Function).
    fun runBoardsReset() {
        busy = true
        db.collection("players")
            .whereEqualTo("status", "approved")
            .get()
            .addOnFailureListener {
                busy = false
                showResetBoards = false
                Toast.makeText(context, "Reset failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
            .addOnSuccessListener { approved ->
                val tiers = listOf(
                    listOf("dailyScore", "dailyWins", "dailyKills"),
                    listOf("weeklyScore", "weeklyWins", "weeklyKills"),
                    listOf("monthlyScore", "monthlyWins", "monthlyKills")
                )
                fun commitTier(i: Int) {
                    if (i >= tiers.size) {
                        busy = false
                        showResetBoards = false
                        Toast.makeText(context, "Leaderboards reset", Toast.LENGTH_LONG).show()
                        return
                    }
                    val f = tiers[i]
                    val rb = db.batch()
                    for (p in approved.documents) {
                        rb.update(p.reference, mapOf(f[0] to 0L, f[1] to 0L, f[2] to 0L))
                    }
                    rb.commit().addOnCompleteListener { commitTier(i + 1) }
                }
                commitTier(0)
            }
    }

    if (showResetBoards) {
        AlertDialog(
            onDismissRequest = { if (!busy) showResetBoards = false },
            title = { Text("Reset Leaderboards") },
            text = {
                Text(
                    "Zero the daily, weekly and monthly leaderboards for all " +
                        "approved players?\n\nAll-time scores and coins are kept.",
                    color = ZsTextSecondary
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = { runBoardsReset() }) {
                    Text(if (busy) "Resetting…" else "Reset", color = ZsDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { showResetBoards = false }) { Text("Cancel") }
            }
        )
    }
}