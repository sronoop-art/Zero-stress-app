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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsGreen
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

class AchievementsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                AchievementsScreen()
            }
        }
    }
}

private data class AchievementDef(
    val id: String,
    val name: String,
    val description: String,
    val xp: String,
    val coins: String
)

private val achievements = listOf(
    AchievementDef("first_blood", "First Blood", "Get your first kill", "10", "50"),
    AchievementDef("kill_100", "Century Killer", "Get 100 total kills", "50", "200"),
    AchievementDef("kill_500", "Rampage", "Get 500 total kills", "100", "500"),
    AchievementDef("win_10", "Winner", "Win 10 matches", "50", "250"),
    AchievementDef("win_50", "Champion", "Win 50 matches", "200", "1000"),
    AchievementDef("damage_10000", "Damage Dealer", "Deal 10,000 total damage", "100", "300"),
    AchievementDef("level_5", "Rising Star", "Reach Level 5", "50", "150"),
    AchievementDef("level_10", "Veteran", "Reach Level 10", "150", "500"),
    AchievementDef("level_25", "Legend", "Reach Level 25", "500", "2000")
)

@Composable
private fun AchievementsScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = FirebaseAuth.getInstance().uid

    var unlockedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        if (userId != null) {
            db.collection("player_achievements")
                .whereEqualTo("playerId", userId)
                .get()
                .addOnSuccessListener { query ->
                    unlockedIds = query.documents.mapNotNull { it.getString("achievementId") }.toSet()
                }
        }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Achievements (${unlockedIds.size}/${achievements.size})",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(achievements, key = { it.id }) { ach ->
                    val unlocked = ach.id in unlockedIds
                    ZSCard(
                        highlight = if (unlocked) ZsAccent else null,
                        modifier = Modifier.alpha(if (unlocked) 1f else 0.55f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    ach.name,
                                    color = ZsTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(ach.description, color = ZsTextSecondary, fontSize = 13.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "+${ach.xp} XP, +${ach.coins} Coins",
                                    color = ZsGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(                                 if (unlocked) "Unlocked" else "Locked",
                                color = if (unlocked) ZsGreen else ZsTextMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}