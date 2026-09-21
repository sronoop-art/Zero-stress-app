package com.zerostress.manager

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Text
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
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.R
import com.zerostress.manager.fcm.FCMConfig
import com.zerostress.manager.fcm.ZSFCMService
import com.zerostress.manager.ui.ZSAvatar
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSBadge
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSMenuTile
import com.zerostress.manager.ui.ZSProgress
import com.zerostress.manager.ui.ZSRing
import com.zerostress.manager.ui.ZSSparkline
import com.zerostress.manager.ui.ZSStat
import com.zerostress.manager.ui.ZSBottomNav
import com.zerostress.manager.ui.zsNavItems
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsSuccess

class PlayerDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                PlayerDashboardScreen()
            }
        }
    }
}

private data class MenuItem(val iconRes: Int, val label: String, val target: Class<*>)

@Composable
private fun PlayerDashboardScreen() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = auth.uid

    var name by remember { mutableStateOf("Player") }
    var score by remember { mutableStateOf(0L) }
    var level by remember { mutableStateOf(1L) }
    var rank by remember { mutableStateOf("Iron") }
    var coins by remember { mutableStateOf(0L) }
    var xp by remember { mutableStateOf(0L) }
    var loaded by remember { mutableStateOf(false) }
    var totalKills by remember { mutableStateOf(0L) }
    var totalDeaths by remember { mutableStateOf(0L) }
    var totalWins by remember { mutableStateOf(0L) }
    var totalMatches by remember { mutableStateOf(0L) }
    var position by remember { mutableStateOf<Int?>(null) }
    var recentScores by remember { mutableStateOf<List<Float>>(emptyList()) }

    // Next-match countdown (soonest upcoming scheduled match)
    var nextMatchTitle by remember { mutableStateOf<String?>(null) }
    var nextMatchTime by remember { mutableStateOf(0L) }
    var nextMatchId by remember { mutableStateOf<String?>(null) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    val menuItems = remember {
        listOf(
            MenuItem(R.drawable.ic_menu_calendar, "Schedule", ScheduleActivity::class.java),
            MenuItem(R.drawable.ic_menu_trophy, "Leaderboard", LeaderboardActivity::class.java),
            MenuItem(R.drawable.ic_nav_tournament, "Tournaments", TournamentActivity::class.java),
            MenuItem(R.drawable.ic_menu_chat, "Team Chat", ChatActivity::class.java),
            MenuItem(R.drawable.ic_menu_mic, "Voice Chat", VoiceActivity::class.java),
            MenuItem(R.drawable.ic_menu_person, "My Profile", ProfileActivity::class.java),
            MenuItem(R.drawable.ic_menu_friends, "Friends", FriendsActivity::class.java),
            MenuItem(R.drawable.ic_menu_medal, "Seasons", SeasonActivity::class.java),
            MenuItem(R.drawable.ic_menu_medal, "Achievements", AchievementsActivity::class.java),
            MenuItem(R.drawable.ic_menu_announce, "Announcements", AnnouncementsActivity::class.java),
            MenuItem(R.drawable.ic_menu_gift, "Daily Rewards", DailyLoginRewardsActivity::class.java),
            MenuItem(R.drawable.ic_menu_fire, "Daily Challenges", DailyChallengesActivity::class.java),
            MenuItem(R.drawable.ic_menu_ticket, "Battle Pass", BattlePassActivity::class.java),
            MenuItem(R.drawable.ic_menu_sparkles, "My Titles", PlayerTitlesActivity::class.java),
            MenuItem(R.drawable.ic_menu_chart, "Performance", PerformanceGraphsActivity::class.java),
            MenuItem(R.drawable.ic_menu_bell, "Notifications", NotificationsActivity::class.java),
            MenuItem(R.drawable.ic_menu_settings, "Settings", SettingsActivity::class.java)
        )
    }

    fun loadProfile() {
        if (uid == null) return
        db.collection("players").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    name = doc.getString("name") ?: "Player"
                    score = doc.getLong("score") ?: 0
                    level = doc.getLong("level") ?: 1
                    rank = doc.getString("rank") ?: "Iron"
                    coins = doc.getLong("coins") ?: 0
                    xp = doc.getLong("xp") ?: 0
                    totalKills = doc.getLong("kills") ?: 0
                    totalDeaths = doc.getLong("deaths") ?: 0
                    totalWins = doc.getLong("wins") ?: 0
                    totalMatches = doc.getLong("matches") ?: 0
                    loaded = true
                }
            }
    }

    // Leaderboard position: rank my doc among all approved players by score.
    fun loadPosition() {
        if (uid == null) return
        db.collection("players").whereEqualTo("status", "approved").get()
            .addOnSuccessListener { q ->
                val sorted = q.documents.sortedByDescending { it.getLong("score") ?: 0 }
                val idx = sorted.indexOfFirst { it.id == uid }
                position = if (idx >= 0) idx + 1 else null
            }
    }

    // Recent form: last 12 logged match scores for the sparkline.
    fun loadRecentForm() {
        if (uid == null) return
        db.collection("match_logs").whereEqualTo("playerId", uid)
            .orderBy("date").limitToLast(12)
            .get()
            .addOnSuccessListener { q ->
                recentScores = q.documents.mapNotNull { it.getLong("score")?.toFloat() }
            }
    }

    // Live subscription to the soonest upcoming match.
    DisposableEffect(Unit) {
        val listener = db.collection("match_schedules")
            .whereEqualTo("status", "Upcoming")
            .orderBy("matchTime")
            .limit(1)
            .addSnapshotListener { snap, _ ->
                val doc = snap?.documents?.firstOrNull()
                nextMatchTitle = doc?.getString("title")
                nextMatchTime = doc?.getLong("matchTime") ?: 0L
                nextMatchId = doc?.id
            }
        onDispose { listener.remove() }
    }

    // 1-second tick drives the countdown text.
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }

    LaunchedEffect(Unit) {
        loadProfile()
        loadPosition()
        loadRecentForm()
        ZSFCMService.saveTokenToFirestoreWithRetry(context)
        FCMConfig.checkFCMConfiguration(context as android.app.Activity)
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            // v4 header: quiet section title row (no gradient band in the design)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "HOME",
                    color = ZsTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.weight(1f))
                ZSBadge(rank, ZsGold)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Player hero: level number inside the XP ring, avatar on the right (v4)
                ZSCard(highlight = ZsPrimary) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZSRing(
                            fraction = xp.toFloat() / (level * 500).toFloat(),
                            modifier = Modifier.size(54.dp),
                            stroke = 4.dp
                        ) {
                            Text(
                                "$level",
                                color = ZsPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    name,
                                    color = ZsTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                                Spacer(Modifier.width(6.dp))
                                ZSBadge("LV $level", ZsPrimary)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                position?.let { "#$it on leaderboard" } ?: "Unranked",
                                color = ZsCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            ZSProgress(
                                fraction = xp.toFloat() / (level * 500).toFloat(),
                                label = "XP $xp / ${level * 500} to level ${level + 1}"
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        ZSAvatar(name, size = 40.dp, ringColor = ZsPrimary)
                    }
                }
                Spacer(Modifier.height(10.dp))

                // Signature v4 hero: score ring + 2x2 stat grid (Rank/Matches/Win rate/K-D)
                ZSCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZSRing(
                            fraction = (score % 10000L).toFloat() / 10000f,
                            modifier = Modifier.size(88.dp),
                            stroke = 6.dp
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "SCORE",
                                    color = ZsTextMuted,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    "$score",
                                    color = ZsTextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                ZSStat("Rank", position?.let { "#$it" } ?: "—", ZsGold, Modifier.weight(1f))
                                ZSStat("Matches", "$totalMatches", ZsCyan, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(7.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                ZSStat(
                                    "Win rate",
                                    if (totalMatches > 0) "${totalWins * 100 / totalMatches}%" else "0%",
                                    ZsSuccess, Modifier.weight(1f)
                                )
                                ZSStat(
                                    "K/D",
                                    if (totalDeaths > 0)
                                        String.format(java.util.Locale.US, "%.2f", totalKills.toDouble() / totalDeaths)
                                    else "$totalKills",
                                    ZsTextPrimary, Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                // Recent form sparkline
                ZSCard {
                    Text(
                        "RECENT FORM",
                        color = ZsTextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    ZSSparkline(recentScores)
                }
                Spacer(Modifier.height(10.dp))

                // Primary CTA (v4): full-width gradient leaderboard button
                // (Add Match removed by request — match logging is admin-only now)
                ZSButton(
                    "View Leaderboard",
                    { context.startActivity(Intent(context, LeaderboardActivity::class.java)) },
                    Modifier.fillMaxWidth(),
                    height = 46.dp
                )

                // Next-match countdown card
                if (nextMatchTitle != null) {
                    Spacer(Modifier.height(12.dp))
                    val remain = nextMatchTime - nowMs
                    val countdown = when {
                        remain <= 0 -> "STARTING NOW"
                        remain < 60_000 -> "starts in ${remain / 1000}s"
                        remain < 3_600_000 -> "starts in ${remain / 60_000}m ${(remain % 60_000) / 1000}s"
                        remain < 86_400_000 -> "starts in ${remain / 3_600_000}h ${(remain % 3_600_000) / 60_000}m"
                        else -> "starts in ${remain / 86_400_000}d ${(remain % 86_400_000) / 3_600_000}h"
                    }
                    ZSCard(highlight = ZsPrimary, onClick = {
                        context.startActivity(Intent(context, ScheduleActivity::class.java))
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "NEXT MATCH",
                                    color = ZsPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    nextMatchTitle ?: "",
                                    color = ZsTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                countdown,
                                color = if (remain <= 60_000) ZsPrimary else ZsCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Menu grid (fills the space above the bottom navigation)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp).weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(menuItems.size) { i ->
                    val item = menuItems[i]
                    ZSMenuTile(
                        label = item.label,
                        accent = when (i) {
                            0 -> ZsCyan
                            1 -> ZsGold
                            2 -> ZsAccent
                            else -> ZsPrimary
                        },
                        iconRes = item.iconRes,
                        onClick = { context.startActivity(Intent(context, item.target)) }
                    )
                }
                item {
                    ZSMenuTile(
                        label = "Logout",
                        accent = ZsDanger,
                        iconRes = R.drawable.ic_menu_logout,
                        onClick = {
                            auth.signOut()
                            context.startActivity(Intent(context, LoginActivity::class.java))
                            (context as? android.app.Activity)?.finish()
                        }
                    )
                }
            }

            // Neon Glass bottom navigation shell
            ZSBottomNav(zsNavItems(0, context))
        }
    }
}
