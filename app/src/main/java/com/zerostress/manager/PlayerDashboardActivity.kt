package com.zerostress.manager

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.R
import com.zerostress.manager.fcm.FCMConfig
import com.zerostress.manager.fcm.ZSFCMService
import com.zerostress.manager.ui.ZSAvatar
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSBadge
import com.zerostress.manager.ui.ZSBarChart
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSBottomNav
import com.zerostress.manager.ui.launchTab
import com.zerostress.manager.ui.zsNavItems
import com.zerostress.manager.ui.ZsAvatarFrame
import com.zerostress.manager.ui.ZSProgress
import com.zerostress.manager.ui.ZSRing
import com.zerostress.manager.ui.ZSSparkline
import com.zerostress.manager.ui.ZSStat
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsPurple
import com.zerostress.manager.ui.theme.ZsSuccess
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

private const val TAG = "PlayerDashboard"

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

@Composable
private fun PlayerDashboardScreen() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = auth.uid

    // Player state
    var name by remember { mutableStateOf("Player") }
    var score by remember { mutableStateOf(0L) }
    var level by remember { mutableStateOf(1L) }
    var rank by remember { mutableStateOf("Iron") }
    var coins by remember { mutableStateOf(0L) }
    var xp by remember { mutableStateOf(0L) }
    var totalKills by remember { mutableStateOf(0L) }
    var totalDeaths by remember { mutableStateOf(0L) }
    var totalWins by remember { mutableStateOf(0L) }
    var totalMatches by remember { mutableStateOf(0L) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var equippedTitle by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    // Leaderboard position + recent form
    var position by remember { mutableStateOf<Int?>(null) }
    var recentScores by remember { mutableStateOf<List<Float>>(emptyList()) }
    var dailyKills by remember { mutableStateOf(0L) }
    var dailyScore by remember { mutableStateOf(0L) }
    // Same-day totals derived from match_logs, used when the player doc has no
    // Daily Input aggregates yet.
    var todayKillsFromLogs by remember { mutableStateOf(0L) }
    var todayScoreFromLogs by remember { mutableStateOf(0L) }

    // Next match
    var nextMatchTitle by remember { mutableStateOf<String?>(null) }
    var nextMatchTime by remember { mutableStateOf(0L) }

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    fun loadPosition() {
        if (uid == null) return
        db.collection("players").whereEqualTo("status", "approved").get()
            .addOnSuccessListener { q ->
                val sorted = q.documents.sortedByDescending { it.getLong("score") ?: 0 }
                val idx = sorted.indexOfFirst { it.id == uid }
                position = if (idx >= 0) idx + 1 else null
            }
            .addOnFailureListener { position = null }
    }

    fun loadRecentForm() {
        if (uid == null) return
        val startOfToday = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        db.collection("match_logs").whereEqualTo("playerId", uid)
            .orderBy("date").limitToLast(12)
            .get()
            .addOnSuccessListener { q ->
                val docs = q.documents
                // match_logs written through the MatchLog model never stored a
                // "score" field, so the score is recomputed from each log's own
                // stats when it is missing - otherwise this always came out
                // empty and the PERFORMANCE card never charted anything.
                recentScores = docs.map { ZsScore.logScore(it).toFloat() }
                val todays = docs.filter { (it.getLong("date") ?: 0L) >= startOfToday }
                todayScoreFromLogs = todays.sumOf { ZsScore.logScore(it) }
                todayKillsFromLogs = todays.sumOf { it.getLong("kills") ?: 0L }
            }
            .addOnFailureListener { e ->
                // Almost always the match_logs(playerId, date) composite index
                // has not been deployed - surface it instead of showing nothing.
                Log.w(TAG, "match_logs query failed: ${e.message}")
                recentScores = emptyList()
            }
    }

    // Live player-doc subscription. Three jobs: (1) hero updates in realtime
    // - an admin's Daily Input entry shows up here the instant it lands;
    // (2) the loading flag clears on success AND failure (the old one-shot
    // get() left the screen skeletonized forever when the read failed);
    // (3) admins landing here get bounced to the manager console.
    DisposableEffect(uid) {
        if (uid == null) return@DisposableEffect onDispose { }
        val reg = db.collection("players").document(uid).addSnapshotListener { doc, err ->
            if (err != null) {
                loaded = true
                return@addSnapshotListener
            }
            if (doc != null && doc.exists()) {
                // Admins live in the manager console. If one ends up here
                // (notification deep-link, back press, stale stack), bounce
                // them to the admin dashboard instead of leaving them on a
                // player-only screen with missing data.
                if (doc.getString("role") == "admin") {
                    val intent = Intent(context, AdminDashboardActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(intent)
                    (context as? android.app.Activity)?.finish()
                    return@addSnapshotListener
                }
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
                avatarUrl = doc.getString("avatarUrl")
                equippedTitle = doc.getString("title") ?: ""
                dailyKills = doc.getLong("dailyKills") ?: 0
                dailyScore = doc.getLong("dailyScore") ?: 0
                loaded = true
            } else {
                loaded = true
            }
        }
        onDispose { reg.remove() }
    }

    // Live subscription to the soonest upcoming match.
    // Requires the composite index on match_schedules(status, matchTime) to be
    // deployed; if it is missing, the snapshot listener will just stay empty and
    // the countdown card will not show.
    DisposableEffect(Unit) {
        val listener = db.collection("match_schedules")
            .whereEqualTo("status", "Upcoming")
            .orderBy("matchTime")
            .limit(1)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "upcoming-match listener error: ${err.message}")
                    return@addSnapshotListener
                }
                val doc = snap?.documents?.firstOrNull()
                nextMatchTitle = doc?.getString("title")
                nextMatchTime = doc?.getLong("matchTime") ?: 0L
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
        loadPosition()
        loadRecentForm()
        ZSFCMService.saveTokenToFirestore(context)
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
                // Coin balance - loaded from the player doc, always visible up top.
                ZSBadge("$coins coins", ZsGold)
                Spacer(Modifier.width(8.dp))
                ZSBadge(rank, ZsGold)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
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
                        // Rank-title frame around the hero avatar. Follows the
                        // title the player equipped in My Titles first, and
                        // only falls back to the score ladder when nothing is
                        // equipped - otherwise selecting a title never changed
                        // the dashboard avatar.
                        val heroTitle = com.zerostress.manager.models.ZsRankTitles
                            .byName(equippedTitle.takeIf { it.isNotEmpty() })
                            ?: com.zerostress.manager.models.ZsRankTitles.titleForScore(score)
                        ZsAvatarFrame(
                            heroTitle?.let {
                                com.zerostress.manager.models.ZsRankTitles.frameSource(context, it)
                            },
                            heroTitle?.let { Color(it.color) } ?: ZsPrimary,
                            40.dp
                        ) {
                            ZSAvatar(name, size = 40.dp, ringColor = Color.Transparent, avatarUrl = avatarUrl)
                        }
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

                // Performance graph (v4 home spec): last 12 match scores as bars,
                // plus a TODAY strip. Daily Input aggregates win when present,
                // otherwise the same totals are derived from today's match_logs
                // so the card shows a score even before an admin logs anything.
                val todayScore = if (dailyScore > 0L) dailyScore else todayScoreFromLogs
                val todayKills = if (dailyKills > 0L) dailyKills else todayKillsFromLogs
                ZSCard {
                    Text(
                        "PERFORMANCE",
                        color = ZsTextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    if (recentScores.isNotEmpty()) {
                        ZSBarChart(recentScores, color = ZsCyan)
                    } else {
                        Text(
                            "No matches logged yet - your last 12 games will chart here.",
                            color = ZsTextMuted,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ZSStat(
                            "Today",
                            if (todayScore > 0L) "$todayScore" else "—",
                            ZsCyan,
                            Modifier.weight(1f)
                        )
                        ZSStat(
                            "Today K",
                            if (todayKills > 0L) "$todayKills" else "—",
                            ZsSuccess,
                            Modifier.weight(1f)
                        )
                        ZSStat(
                            "K/D",
                            if (totalDeaths > 0)
                                String.format(java.util.Locale.US, "%.2f", totalKills.toDouble() / totalDeaths)
                            else if (totalKills > 0) "$totalKills"
                            else "—",
                            ZsTextPrimary,
                            Modifier.weight(1f)
                        )
                    }
                    if (todayScore == 0L && todayKills == 0L && totalMatches > 0) {
                        Text(
                            "Today’s stats appear once a match today is logged.",
                            color = ZsTextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
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
                ZSButton(
                    "View Leaderboard",
                    { context.launchTab(LeaderboardActivity::class.java) },
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
                        context.launchTab(ScheduleActivity::class.java)
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
                Spacer(Modifier.height(16.dp))
            }

            // Neon Glass bottom navigation shell (6 tabs - menu items live in More)
            ZSBottomNav(zsNavItems(0, context))
        }
    }
}
