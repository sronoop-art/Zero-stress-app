package com.zerostress.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import com.zerostress.manager.ui.ZSAvatar
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSBadge
import com.zerostress.manager.ui.ZSBottomNav
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSFilterChips
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.ZSTrend
import com.zerostress.manager.ui.zsNavItems
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsBorder
import com.zerostress.manager.ui.theme.ZsBronze
import com.zerostress.manager.ui.theme.ZsCard
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsPurple
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
    var scope by remember { mutableIntStateOf(0) } // 0=Global 1=Friends 2=Local
    var players by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var isAdmin by remember { mutableStateOf(false) }
    var showResetTypeDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf<String?>(null) }
    val myUid = auth.uid
    var friendIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var myRegion by remember { mutableStateOf<String?>(null) }

    // Friend ids + my region for the scope filters
    LaunchedEffect(Unit) {
        val uid = auth.uid ?: return@LaunchedEffect
        db.collection("players").document(uid).get()
            .addOnSuccessListener { doc -> if (doc.exists()) myRegion = doc.getString("region") }
        db.collection("friendships").whereEqualTo("userId1", uid).get()
            .addOnSuccessListener { q1 ->
                val ids = q1.documents.mapNotNull { it.getString("userId2") }.toMutableSet()
                db.collection("friendships").whereEqualTo("userId2", uid).get()
                    .addOnSuccessListener { q2 ->
                        ids += q2.documents.mapNotNull { it.getString("userId1") }
                        friendIds = ids
                    }
            }
    }

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
                    // Server-side increment: rewards granted concurrently (season
                    // end, another admin) must not be overwritten by this write.
                    db.collection("players").document(playerId)
                        .update("coins", com.google.firebase.firestore.FieldValue.increment(rewardCoins))
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
                Toast.makeText(context, "Rewards distributed to top 3!", Toast.LENGTH_SHORT).show()
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
                                    "${type.uppercase()} leaderboard reset for $total players!",
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

    // Live listener: the leaderboard updates in realtime (e.g. the moment the
    // admin saves a Daily Input entry for a player). DisposableEffect removes
    // the listener when the screen closes so nothing leaks. Reads currentTab
    // through the delegate at callback time, so the sort always follows the
    // tab the user is on. Reward distribution stays a manual admin action.
    DisposableEffect(Unit) {
        val reg = db.collection("players")
            .whereEqualTo("status", "approved")
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                snap?.let {
                    loading = false
                    val list = it.documents.filter { d -> d.getString("role") != "admin" }
                    players = list.sortedByDescending { d -> scoreForTab(d) }
                }
            }
        onDispose { reg.remove() }
    }

    LaunchedEffect(Unit) {
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
                            Text("Reset", color = ZsDanger, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )

            // Tabs (v4 gradient chips)
            ZSFilterChips(periods, currentTab) { currentTab = it }
            Spacer(Modifier.height(8.dp))

            // Scope filter: Global / Friends / Local
            ZSFilterChips(listOf("Global", "Friends", "Local"), scope) { scope = it }
            Spacer(Modifier.height(8.dp))

            // Sort at render time by the active tab's metric: the live
            // snapshot listener keeps `players` fresh, so Daily/Weekly/Monthly
            // re-rank instantly on tab switch - no refetch, no loading flicker.
            val visiblePlayers = when (scope) {
                1 -> players.filter { it.id == myUid || it.id in friendIds }
                2 -> myRegion?.let { reg -> players.filter { (it.getString("region") ?: "") == reg } } ?: players
                else -> players
            }.sortedByDescending { scoreForTab(it) }

            if (loading) {
                LoadingBox(Modifier.weight(1f))
            } else if (visiblePlayers.isEmpty()) {
                EmptyState("No players in this scope yet", Modifier.weight(1f))
            } else {
                // v4 podium: top three players, gold-glow center card
                val podium = visiblePlayers.take(3)
                if (podium.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        PodiumCard(
                            podium.getOrNull(1), 2, ZsSilver,
                            podium.getOrNull(1)?.let { scoreForTab(it) } ?: 0,
                            podium.getOrNull(1)?.let { winsForTab(it) } ?: 0,
                            Modifier.weight(1f)
                        )
                        PodiumCard(
                            podium.getOrNull(0), 1, ZsGold,
                            podium.getOrNull(0)?.let { scoreForTab(it) } ?: 0,
                            podium.getOrNull(0)?.let { winsForTab(it) } ?: 0,
                            Modifier.weight(1f)
                        )
                        PodiumCard(
                            podium.getOrNull(2), 3, ZsBronze,
                            podium.getOrNull(2)?.let { scoreForTab(it) } ?: 0,
                            podium.getOrNull(2)?.let { winsForTab(it) } ?: 0,
                            Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 24.dp, top = 4.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visiblePlayers.drop(3), key = { it.id }) { doc ->
                        val index = visiblePlayers.indexOf(doc)
                        val name = doc.getString("name") ?: "Unknown"
                        val score = scoreForTab(doc)
                        val kills = killsForTab(doc)
                        val wins = winsForTab(doc)
                        val level = doc.getLong("level") ?: 1
                        val rank = doc.getString("rank") ?: "Iron"
                        val gameRole = doc.getString("gameRole")

                        val (medal, medalColor) = when (index) {
                            0 -> "#1 " to ZsGold
                            1 -> "#2 " to ZsSilver
                            2 -> "#3 " to ZsBronze
                            else -> "" to ZsTextPrimary
                        }
                        val gameRoleText = when (gameRole) {
                            "Rusher" -> "Rusher"
                            "Sniper" -> "Sniper"
                            "IGL" -> "IGL"
                            "Supporter" -> "Supporter"
                            "Bomber" -> "Bomber"
                            else -> ""
                        }

                        val isMe = doc.id == myUid
                        // Momentum arrow relative to the field (no history in schema)
                        val trend = when {
                            visiblePlayers.size < 3 -> 0
                            index < visiblePlayers.size / 3 -> 1
                            index < visiblePlayers.size * 2 / 3 -> 0
                            else -> -1
                        }
                        ZSCard(
                            highlight = when {
                                isMe -> ZsPurple
                                index == 0 -> ZsGold
                                else -> null
                            },
                            onClick = {
                                // Open the player's read-only profile view
                                val i = android.content.Intent(context, PlayerProfileViewActivity::class.java).apply {
                                    putExtra(PlayerProfileViewActivity.EXTRA_PLAYER_ID, doc.id)
                                    putExtra(PlayerProfileViewActivity.EXTRA_PLAYER_NAME, name)
                                }
                                context.startActivity(i)
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "#${index + 1}",
                                    color = if (index < 3) medalColor else ZsTextMuted,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                                ZSAvatar(
                                    name, size = 40.dp,
                                    ringColor = if (index < 3) medalColor else ZsBorder,
                                    avatarUrl = doc.getString("avatarUrl")
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "$medal$name",
                                            color = if (index < 3) medalColor else ZsTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            maxLines = 1
                                        )
                                        if (isMe) {
                                            Spacer(Modifier.width(6.dp))
                                            ZSBadge("YOU", ZsPurple)
                                        }
                                    }
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
                                ZSTrend(trend, Modifier.padding(horizontal = 6.dp))
                                Text(
                                    "$score pts",
                                    color = ZsCyan,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Neon Glass bottom navigation shell
            ZSBottomNav(zsNavItems(2, context))
        }
    }

    // Reset type selection
    if (showResetTypeDialog) {
        AlertDialog(
            onDismissRequest = { showResetTypeDialog = false },
            title = { Text("Reset Leaderboard") },
            text = {
                Column {
                    listOf(
                        "Daily Leaderboard" to "daily",
                        "Weekly Leaderboard" to "weekly",
                        "Monthly Leaderboard" to "monthly",
                        "Everything (All Time)" to "all"
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
            "daily" -> "Reset Daily Leaderboard" to "All DAILY stats (kills, wins, score) will be set to 0 for every player.\n\nWeekly and Monthly stats are NOT affected."
            "weekly" -> "Reset Weekly Leaderboard" to "All WEEKLY stats (kills, wins, score) will be set to 0 for every player.\n\nDaily and Monthly stats are NOT affected."
            "monthly" -> "Reset Monthly Leaderboard" to "All MONTHLY stats (kills, wins, score) will be set to 0 for every player.\n\nDaily and Weekly stats are NOT affected."
            else -> "Reset EVERYTHING" to "DANGER ZONE\n\nThis resets ALL stats for every player:\n- Total score, kills, wins, damage\n- XP to Level 1\n- Coins to 0\n- Rank to Iron\n- Daily, Weekly & Monthly stats\n\nThis cannot be undone!"
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

/**
 * v4 leaderboard podium tile: medal-ringed avatar, name, score and a rank
 * badge. The center card (place 1) is taller with a gold glow treatment.
 */
@Composable
private fun PodiumCard(
    doc: DocumentSnapshot?,
    place: Int,
    medalColor: Color,
    score: Long,
    wins: Long,
    modifier: Modifier = Modifier
) {
    if (doc == null) {
        Spacer(modifier)
        return
    }
    val name = doc.getString("name") ?: "?"
    val avatarUrl = doc.getString("avatarUrl")
    val isTop = place == 1
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ZsCard)
            .border(
                1.5.dp,
                if (isTop) ZsGold.copy(alpha = 0.55f) else medalColor.copy(alpha = 0.35f),
                RoundedCornerShape(16.dp)
            )
            .drawBehind {
                if (isTop) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(ZsGold.copy(alpha = 0.30f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.30f),
                            radius = size.width * 0.7f
                        )
                    )
                }
            }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = if (isTop) 14.dp else 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ZSAvatar(
                name, size = if (isTop) 34.dp else 28.dp,
                ringColor = if (isTop) ZsGold else medalColor,
                avatarUrl = avatarUrl
            )
            Spacer(Modifier.height(5.dp))
            Text(
                name,
                color = if (isTop) ZsGold else ZsTextPrimary,
                fontSize = if (isTop) 11.sp else 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                String.format(java.util.Locale.US, "%,d", score),
                color = if (isTop) ZsGold else ZsTextSecondary,
                fontSize = if (isTop) 12.sp else 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when (place) {
                            1 -> Brush.linearGradient(listOf(Color(0xFFFBBF24), Color(0xFFF59E0B)))
                            2 -> Brush.linearGradient(listOf(Color(0xFFD7DCE6), Color(0xFF9AA3B8)))
                            else -> Brush.linearGradient(listOf(Color(0xFFE0955C), Color(0xFFB0703A)))
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    "$place",
                    color = when (place) {
                        1 -> Color(0xFF231A00)
                        2 -> Color(0xFF1A1D26)
                        else -> Color(0xFF26130A)
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("$wins W", color = ZsTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}