package com.zerostress.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.EmptyState
import com.zerostress.manager.ui.LoadingBox
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsBronze
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsSilver
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import com.zerostress.manager.ui.theme.ZsWarning

class LeaderboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                LeaderboardScreen()
            }
        }
    }
}

private val periods = listOf("Daily", "Weekly", "Monthly")

@Composable
private fun LeaderboardScreen() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var currentTab by remember { mutableIntStateOf(0) }
    var players by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var isAdmin by remember { mutableStateOf(false) }
    var showResetTypeDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf<String?>(null) }

    fun scoreForTab(doc: DocumentSnapshot): Long = when (currentTab) {
        0 -> doc.getLong("dailyScore") ?: 0
        1 -> doc.getLong("weeklyScore") ?: 0
        2 -> doc.getLong("monthlyScore") ?: 0
        else -> doc.getLong("score") ?: 0
    }

    fun killsForTab(doc: DocumentSnapshot): Long = when (currentTab) {
        0 -> doc.getLong("dailyKills") ?: 0
        1 -> doc.getLong("weeklyKills") ?: 0
        2 -> doc.getLong("monthlyKills") ?: 0
        else -> doc.getLong("kills") ?: 0
    }

    fun winsForTab(doc: DocumentSnapshot): Long = when (currentTab) {
        0 -> doc.getLong("dailyWins") ?: 0
        1 -> doc.getLong("weeklyWins") ?: 0
        2 -> doc.getLong("monthlyWins") ?: 0
        else -> doc.getLong("wins") ?: 0
    }

    fun loadLeaderboard() {
        loading = true
        db.collection("players")
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { query ->
                loading = false
                val list = query.documents.filter { it.getString("role") != "admin" }
                players = list.sortedByDescending { scoreForTab(it) }
            }
            .addOnFailureListener {
                loading = false
                Toast.makeText(context, "Error loading leaderboard", Toast.LENGTH_SHORT).show()
            }
    }

    fun awardRewards(type: String) {
        val (rewardCoins, rewardTitle) = when (type) {
            "daily" -> 50L to "Daily Champion"
            "weekly" -> 200L to "Weekly Champion"
            "monthly" -> 500L to "Monthly Champion"
            else -> 1000L to "All-Time Legend"
        }

        db.collection("players")
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { query ->
                val list = query.documents
                    .filter { it.getString("role") != "admin" }
                    .sortedByDescending { scoreForTab(it) }
                    .take(3)

                list.forEach { player ->
                    val playerId = player.id
                    val playerName = player.getString("name") ?: "?"
                    val currentCoins = player.getLong("coins") ?: 0
                    db.collection("players").document(playerId)
                        .update("coins", currentCoins + rewardCoins)
                        .addOnSuccessListener {
                            android.util.Log.d("Leaderboard", "Awarded $rewardCoins coins to $playerName")
                        }
                    // Award title to level 5+ players
                    if ((player.getLong("level") ?: 1) >= 5) {
                        db.collection("player_titles")
                            .document("${playerId}_$rewardTitle")
                            .set(
                                mapOf(
                                    "title" to rewardTitle,
                                    "awardedAt" to System.currentTimeMillis(),
                                    "awardedFor" to type
                                )
                            )
                    }
                }
                Toast.makeText(context, "🏆 Rewards distributed to top 3!", Toast.LENGTH_SHORT).show()
            }
    }

    fun performReset(type: String) {
        db.collection("players").get()
            .addOnSuccessListener { query ->
                val total = query.size()
                if (total == 0) return@addOnSuccessListener
                var done = 0
                for (doc in query.documents) {
                    val resetData = mutableMapOf<String, Any>()
                    when (type) {
                        "daily" -> {
                            resetData["dailyKills"] = 0; resetData["dailyWins"] = 0
                            resetData["dailyAssists"] = 0; resetData["dailyDamage"] = 0
                            resetData["dailyMatches"] = 0; resetData["dailyScore"] = 0
                            resetData["lastDailyReset"] = System.currentTimeMillis()
                        }
                        "weekly" -> {
                            resetData["weeklyKills"] = 0; resetData["weeklyWins"] = 0
                            resetData["weeklyAssists"] = 0; resetData["weeklyDamage"] = 0
                            resetData["weeklyMatches"] = 0; resetData["weeklyScore"] = 0
                            resetData["lastWeeklyReset"] = System.currentTimeMillis()
                        }
                        "monthly" -> {
                            resetData["monthlyKills"] = 0; resetData["monthlyWins"] = 0
                            resetData["monthlyAssists"] = 0; resetData["monthlyDamage"] = 0
                            resetData["monthlyMatches"] = 0; resetData["monthlyScore"] = 0
                            resetData["lastMonthlyReset"] = System.currentTimeMillis()
                        }
                        else -> {
                            resetData["score"] = 0; resetData["kills"] = 0; resetData["wins"] = 0
                            resetData["assists"] = 0; resetData["damage"] = 0; resetData["matches"] = 0
                            resetData["xp"] = 0; resetData["level"] = 1; resetData["rank"] = "Iron"
                            resetData["coins"] = 0
                            resetData["dailyKills"] = 0; resetData["dailyWins"] = 0
                            resetData["dailyAssists"] = 0; resetData["dailyDamage"] = 0
                            resetData["dailyMatches"] = 0; resetData["dailyScore"] = 0
                            resetData["weeklyKills"] = 0; resetData["weeklyWins"] = 0
                            resetData["weeklyAssists"] = 0; resetData["weeklyDamage"] = 0
                            resetData["weeklyMatches"] = 0; resetData["weeklyScore"] = 0
                            resetData["monthlyKills"] = 0; resetData["monthlyWins"] = 0
                            resetData["monthlyAssists"] = 0; resetData["monthlyDamage"] = 0
                            resetData["monthlyMatches"] = 0; resetData["monthlyScore"] = 0
                            resetData["lastDailyReset"] = System.currentTimeMillis()
                            resetData["lastWeeklyReset"] = System.currentTimeMillis()
                            resetData["lastMonthlyReset"] = System.currentTimeMillis()
                        }
                    }
                    db.collection("players").document(doc.id).update(resetData)
                        .addOnSuccessListener {
                            done++
                            if (done >= total) {
                                Toast.makeText(
                                    context,
                                    "✅ ${type.uppercase()} leaderboard reset for $total players!",
                                    Toast.LENGTH_LONG
                                ).show()
                                awardRewards(type)
                                loadLeaderboard()
                            }
                        }
                }
            }
    }

    LaunchedEffect(Unit) {
        auth.uid?.let { uid ->
            db.collection("players").document(uid).get()
                .addOnSuccessListener { doc ->
                    isAdmin = doc.getString("role") == "admin"
                }
        }
    }

    LaunchedEffect(currentTab) {
        loadLeaderboard()
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "${periods[currentTab]} Leaderboard",
                onBack = { (context as? android.app.Activity)?.finish() },
                right = {
                    if (isAdmin) {
                        TextButton(onClick = { showResetTypeDialog = true }) {
                            Text("♻️ Reset", color = ZsDanger, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )

            // Tabs
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                periods.forEachIndexed { index, period ->
                    val selected = index == currentTab
                    Text(
                        text = period,
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (selected) ZsPrimary else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { currentTab = index }
                            .padding(vertical = 10.dp),
                        color = if (selected) Color.White else ZsTextMuted,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (loading) {
                LoadingBox()
            } else if (players.isEmpty()) {
                EmptyState("No approved players yet")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 24.dp, top = 4.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(players, key = { it.id }) { doc ->
                        val index = players.indexOf(doc)
                        val name = doc.getString("name") ?: "Unknown"
                        val score = scoreForTab(doc)
                        val kills = killsForTab(doc)
                        val wins = winsForTab(doc)
                        val level = doc.getLong("level") ?: 1
                        val rank = doc.getString("rank") ?: "Iron"
                        val gameRole = doc.getString("gameRole")

                        val (medal, medalColor) = when (index) {
                            0 -> "👑 " to ZsGold
                            1 -> "🥈 " to ZsSilver
                            2 -> "🥉 " to ZsBronze
                            else -> "" to ZsTextPrimary
                        }
                        val gameRoleText = when (gameRole) {
                            "Rusher" -> "⚔️ Rusher"
                            "Sniper" -> "🎯 Sniper"
                            "IGL" -> "👑 IGL"
                            "Supporter" -> "🛡️ Supporter"
                            "Bomber" -> "💣 Bomber"
                            else -> ""
                        }

                        ZSCard(highlight = if (index == 0) ZsGold else null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "#${index + 1}",
                                    color = if (index < 3) medalColor else ZsTextMuted,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "$medal$name",
                                        color = if (index < 3) medalColor else ZsTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        buildString {
                                            if (gameRoleText.isNotEmpty()) append("$gameRoleText | ")
                                            append("Lv.$level • $rank | K:$kills W:$wins")
                                        },
                                        color = ZsTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    "$score pts",
                                    color = ZsCyan,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Reset type selection
    if (showResetTypeDialog) {
        AlertDialog(
            onDismissRequest = { showResetTypeDialog = false },
            title = { Text("⚠️ Reset Leaderboard") },
            text = {
                Column {
                    listOf(
                        "📅 Daily Leaderboard" to "daily",
                        "📆 Weekly Leaderboard" to "weekly",
                        "🗓️ Monthly Leaderboard" to "monthly",
                        "💣 Everything (All Time)" to "all"
                    ).forEach { (label, value) ->
                        TextButton(onClick = {
                            showResetTypeDialog = false
                            showResetConfirmDialog = value
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showResetTypeDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Reset confirmation
    showResetConfirmDialog?.let { type ->
        val (title, message) = when (type) {
            "daily" -> "📅 Reset Daily Leaderboard" to "All DAILY stats (kills, wins, score) will be set to 0 for every player.\n\nWeekly and Monthly stats are NOT affected."
            "weekly" -> "📆 Reset Weekly Leaderboard" to "All WEEKLY stats (kills, wins, score) will be set to 0 for every player.\n\nDaily and Monthly stats are NOT affected."
            "monthly" -> "🗓️ Reset Monthly Leaderboard" to "All MONTHLY stats (kills, wins, score) will be set to 0 for every player.\n\nDaily and Weekly stats are NOT affected."
            else -> "💣 Reset EVERYTHING" to "⚠️ DANGER ZONE ⚠️\n\nThis resets ALL stats for every player:\n• Total score, kills, wins, damage\n• XP → Level 1\n• Coins → 0\n• Rank → Iron\n• Daily, Weekly & Monthly stats\n\nThis cannot be undone!"
        }
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = null },
            title = { Text(title) },
            text = { Text(message, color = ZsTextSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirmDialog = null
                    performReset(type)
                }) { Text("RESET NOW", color = ZsDanger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = null }) { Text("Cancel") }
            }
        )
    }
}