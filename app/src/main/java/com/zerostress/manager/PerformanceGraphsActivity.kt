package com.zerostress.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.EmptyState
import com.zerostress.manager.ui.LoadingBox
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSBarChart
import com.zerostress.manager.ui.ZSBottomNav
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSFilterChips
import com.zerostress.manager.ui.ZSKeyValue
import com.zerostress.manager.ui.ZSProgress
import com.zerostress.manager.ui.ZSRing
import com.zerostress.manager.ui.ZSSparkline
import com.zerostress.manager.ui.ZSStat
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.zsNavItems
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsGreen
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsPurple
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PerformanceGraphsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                PerformanceGraphsScreen()
            }
        }
    }
}

private val perfPeriods = listOf("Today", "7 Days", "30 Days", "All Time")

@Composable
private fun PerformanceGraphsScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = FirebaseAuth.getInstance().uid

    var loading by remember { mutableStateOf(true) }
    var period by remember { mutableIntStateOf(3) } // default: All Time
    var logs by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }

    // Lifetime totals from the players doc
    var lifeKills by remember { mutableStateOf(0L) }
    var lifeDeaths by remember { mutableStateOf(0L) }
    var lifeWins by remember { mutableStateOf(0L) }
    var lifeMatches by remember { mutableStateOf(0L) }
    var lifeDamage by remember { mutableStateOf(0L) }
    var level by remember { mutableStateOf(1L) }
    var xp by remember { mutableStateOf(0L) }
    var coins by remember { mutableStateOf(0L) }
    var accuracyField by remember { mutableStateOf<Long?>(null) }
    var headshotsField by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        if (userId != null) {
            db.collection("players").document(userId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        lifeKills = doc.getLong("kills") ?: 0
                        lifeDeaths = doc.getLong("deaths") ?: 0
                        lifeWins = doc.getLong("wins") ?: 0
                        lifeMatches = doc.getLong("matches") ?: 0
                        lifeDamage = doc.getLong("damage") ?: 0
                        level = doc.getLong("level") ?: 1
                        xp = doc.getLong("xp") ?: 0
                        coins = doc.getLong("coins") ?: 0
                        // Optional fields — shown as "—" until data exists
                        accuracyField = doc.getLong("accuracy")
                        headshotsField = doc.getLong("headshots")
                    }
                    loading = false
                }
                .addOnFailureListener { loading = false }
            db.collection("match_logs").whereEqualTo("playerId", userId)
                .orderBy("date").limitToLast(60)
                .get()
                .addOnSuccessListener { q -> logs = q.documents }
        } else {
            loading = false
        }
    }

    // ---- Period filtering (Today / 7D / 30D / All) ----
    val now = System.currentTimeMillis()
    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val periodStart = when (period) {
        0 -> startOfToday
        1 -> now - 7L * 86_400_000
        2 -> now - 30L * 86_400_000
        else -> 0L
    }
    val filtered = logs.filter { (it.getLong("date") ?: 0L) >= periodStart }

    val m = filtered.size
    val k = filtered.sumOf { it.getLong("kills") ?: 0 }
    val d = filtered.sumOf { it.getLong("deaths") ?: 0 }
    val a = filtered.sumOf { it.getLong("assists") ?: 0 }
    val w = filtered.count { it.getBoolean("win") == true }
    val dmg = filtered.sumOf { it.getLong("damage") ?: 0 }
    val sc = filtered.sumOf { it.getLong("score") ?: 0 }
    val kd = if (d > 0) k.toDouble() / d else k.toDouble()
    val wr = if (m > 0) w * 100.0 / m else 0.0
    val avgDmg = if (m > 0) dmg.toDouble() / m else 0.0
    val avgKills = if (m > 0) k.toDouble() / m else 0.0
    val avgScore = if (m > 0) sc.toDouble() / m else 0.0

    // Composite performance score: K/D (40) + win rate (30) + avg score (30)
    val perf = ((kd.coerceAtMost(3.0) / 3.0) * 40 +
        (wr / 100.0) * 30 +
        (avgScore.coerceAtMost(500.0) / 500.0) * 30).toInt()

    val chartLogs = filtered.sortedBy { it.getLong("date") ?: 0 }.takeLast(20)
    val killBars = chartLogs.map { (it.getLong("kills") ?: 0).toFloat() }
    val scoreLine = chartLogs.map { (it.getLong("score") ?: 0).toFloat() }
    val history = filtered.sortedByDescending { it.getLong("date") ?: 0 }.take(8)
    val dateFmt = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val accText = accuracyField?.let { "$it%" } ?: "—"
    val hsText = headshotsField?.takeIf { lifeKills > 0 }
        ?.let { "${it * 100 / lifeKills}%" } ?: "—"

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Performance Stats",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            if (loading) {
                LoadingBox(Modifier.weight(1f))
            } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp).weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // v4 hero: big centered performance ring
                    ZSCard(highlight = ZsPrimary) {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ZSRing(perf / 100f, Modifier.size(118.dp), stroke = 9.dp) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$perf",
                                        color = ZsPrimary,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        "PERF SCORE",
                                        color = ZsTextMuted,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                perfPeriods[period],
                                color = ZsTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // Period filter
                    ZSFilterChips(perfPeriods, period) { period = it }
                    Spacer(Modifier.height(12.dp))

                    // KDA row (v4 4-tile grid)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        ZSStat("Kills", "$k", ZsCyan, Modifier.weight(1f))
                        ZSStat("Deaths", "$d", ZsTextPrimary, Modifier.weight(1f))
                        ZSStat("Assists", "$a", ZsPurple, Modifier.weight(1f))
                        ZSStat(
                            "K/D", String.format(Locale.getDefault(), "%.2f", kd),
                            ZsTextPrimary, Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // Aim profile bars (v4)
                    ZSCard {
                        Text(
                            "AIM PROFILE",
                            color = ZsTextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        AimBar("Win rate", (wr / 100.0).toFloat(), String.format(Locale.getDefault(), "%.0f%%", wr))
                        Spacer(Modifier.height(6.dp))
                        AimBar(
                            "Avg damage",
                            (avgDmg.coerceAtMost(1500.0) / 1500.0).toFloat(),
                            String.format(Locale.getDefault(), "%.0f", avgDmg)
                        )
                        Spacer(Modifier.height(6.dp))
                        AimBar("Accuracy", (accuracyField?.toFloat() ?: 0f) / 100f, accText)
                        Spacer(Modifier.height(6.dp))
                        AimBar("Headshot", (headshotsField?.toFloat() ?: 0f) / 100f, hsText)
                    }
                    Spacer(Modifier.height(12.dp))

                    // Period totals
                    ZSCard {
                        ZSKeyValue("Matches", "$m")
                        Spacer(Modifier.height(6.dp))
                        ZSKeyValue("Kills", "$k", ZsCyan)
                        Spacer(Modifier.height(6.dp))
                        ZSKeyValue("Deaths", "$d", ZsDanger)
                        Spacer(Modifier.height(6.dp))
                        ZSKeyValue("Assists", "$a", ZsAccent)
                        Spacer(Modifier.height(6.dp))
                        ZSKeyValue("Wins", "$w", ZsGreen)
                        Spacer(Modifier.height(6.dp))
                        ZSKeyValue("Damage", "$dmg", ZsGold)
                        Spacer(Modifier.height(6.dp))
                        ZSKeyValue("Avg Score", String.format(Locale.getDefault(), "%.0f", avgScore), ZsPrimary)
                    }
                    Spacer(Modifier.height(12.dp))

                    // Charts
                    ZSCard {
                        Text(
                            "KILLS PER MATCH",
                            color = ZsTextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        ZSBarChart(killBars, color = ZsCyan)
                    }
                    Spacer(Modifier.height(10.dp))
                    ZSCard {
                        Text(
                            "SCORE TREND",
                            color = ZsTextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        ZSSparkline(scoreLine, color = ZsPrimary)
                    }
                    Spacer(Modifier.height(12.dp))

                    // Lifetime card
                    ZSCard {
                        Text(
                            "LIFETIME",
                            color = ZsTextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        ZSKeyValue("Matches", "$lifeMatches")
                        Spacer(Modifier.height(6.dp))
                        ZSKeyValue("Kills", "$lifeKills", ZsCyan)
                        Spacer(Modifier.height(6.dp))
                        ZSKeyValue("Deaths", "$lifeDeaths", ZsDanger)
                        Spacer(Modifier.height(6.dp))
                        ZSKeyValue("Wins", "$lifeWins", ZsGreen)
                        Spacer(Modifier.height(6.dp))
                        ZSKeyValue("Damage", "$lifeDamage", ZsGold)
                        Spacer(Modifier.height(6.dp))
                        ZSKeyValue("Level", "$level", ZsAccent)
                        Spacer(Modifier.height(6.dp))
                        ZSKeyValue("XP", "$xp")
                        Spacer(Modifier.height(6.dp))
                        ZSKeyValue("Coins", "$coins", ZsGold)
                    }
                    Spacer(Modifier.height(12.dp))

                    // Match history
                    if (history.isEmpty()) {
                        EmptyState("No matches in this period")
                    } else {
                        history.forEach { doc ->
                            val win = doc.getBoolean("win") == true
                            val lk = doc.getLong("kills") ?: 0
                            val ld = doc.getLong("deaths") ?: 0
                            val la = doc.getLong("assists") ?: 0
                            val lDmg = doc.getLong("damage") ?: 0
                            val lScore = doc.getLong("score") ?: 0
                            val whenMs = doc.getLong("date") ?: 0L
                            ZSCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (whenMs > 0) dateFmt.format(Date(whenMs)) else "—",
                                        color = ZsTextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(46.dp)
                                    )
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(percent = 50))
                                            .background(if (win) ZsGreen else ZsDanger)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "K $lk  ·  D $ld  ·  A $la",
                                            color = ZsTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "$lDmg dmg",
                                            color = ZsTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            if (win) "WIN" else "LOSS",
                                            color = if (win) ZsGreen else ZsDanger,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            "+$lScore",
                                            color = ZsGold,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Neon Glass bottom navigation shell
            ZSBottomNav(zsNavItems(1, context))
        }
    }
}

/** v4 aim-profile row: label, neon progress bar, value. */
@Composable
private fun AimBar(label: String, fraction: Float, valueText: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = ZsTextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.width(86.dp)
        )
        ZSProgress(fraction, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(valueText, color = ZsTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
