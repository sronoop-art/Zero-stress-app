package com.zerostress.manager

import android.os.Bundle
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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.EmptyState
import com.zerostress.manager.ui.LoadingBox
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsBronze
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsSilver
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

class ViewAllPlayersStatsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                ViewAllPlayersStatsScreen()
            }
        }
    }
}

@Composable
private fun ViewAllPlayersStatsScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    var players by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        db.collection("players")
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { query ->
                loading = false
                val list = query.documents.sortedByDescending { it.getLong("score") ?: 0 }
                players = list
                val totalPlayers = list.size
                var totalKills = 0L
                var totalWins = 0L
                var totalMatches = 0L
                for (doc in list) {
                    totalKills += doc.getLong("kills") ?: 0
                    totalWins += doc.getLong("wins") ?: 0
                    totalMatches += doc.getLong("matches") ?: 0
                }
                summary = "👥 $totalPlayers Players | ⚔️ $totalKills Kills | 🏆 $totalWins Wins | 🎮 $totalMatches Matches"
            }
    }

    fun roleColor(role: String?): Color = when (role) {
        "admin" -> ZsPrimary
        "moderator" -> Color(0xFFA855F7)
        else -> ZsAccent
    }

    fun roleText(role: String?, gameRole: String?): String {
        val displayRole = when (role) {
            "admin" -> "👑 ADMIN"
            "moderator" -> "🛡️ MOD"
            else -> (role ?: "player").uppercase()
        }
        val emoji = when (gameRole) {
            "Rusher" -> "⚔️ "
            "Sniper" -> "🎯 "
            "IGL" -> "👑 "
            "Supporter" -> "🛡️ "
            "Bomber" -> "💣 "
            else -> ""
        }
        return if (!gameRole.isNullOrEmpty()) "$emoji$gameRole • $displayRole" else displayRole
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "All Players Stats",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            Text(
                summary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = ZsCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            if (loading) {
                LoadingBox()
            } else if (players.isEmpty()) {
                EmptyState("No approved players")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(players, key = { it.id }) { doc ->
                        val index = players.indexOf(doc)
                        val name = doc.getString("name") ?: "Unknown"
                        val role = doc.getString("role")
                        val gameRole = doc.getString("gameRole")
                        val score = doc.getLong("score") ?: 0
                        val kills = doc.getLong("kills") ?: 0
                        val wins = doc.getLong("wins") ?: 0
                        val matches = doc.getLong("matches") ?: 0
                        val level = doc.getLong("level") ?: 1
                        val playerRank = doc.getString("rank") ?: "Iron"

                        val rankColor = when (index) {
                            0 -> ZsGold
                            1 -> ZsSilver
                            2 -> ZsBronze
                            else -> ZsTextMuted
                        }

                        ZSCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "#${index + 1}",
                                    modifier = Modifier.padding(end = 10.dp),
                                    color = rankColor,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(name, color = ZsTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        roleText(role, gameRole),
                                        color = roleColor(role),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        "Lv.$level $playerRank | K:$kills W:$wins M:$matches",
                                        color = ZsTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    "$score pts",
                                    color = ZsGold,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}