package com.zerostress.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSProgress
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsGreen
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

class DailyChallengesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                DailyChallengesScreen()
            }
        }
    }
}

private data class Challenge(
    val title: String,
    val description: String,
    val target: Int,
    val current: Int,
    val reward: String
) {
    val isCompleted: Boolean get() = current >= target
    val progress: Int get() = ((current.toFloat() / target) * 100).toInt().coerceIn(0, 100)
}

@Composable
private fun DailyChallengesScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = FirebaseAuth.getInstance().uid

    var challenges by remember { mutableStateOf<List<Challenge>>(emptyList()) }
    var summary by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (userId != null) {
            db.collection("players").document(userId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val kills = (doc.getLong("kills") ?: 0).toInt()
                        val wins = (doc.getLong("wins") ?: 0).toInt()
                        val matches = (doc.getLong("matches") ?: 0).toInt()
                        val damage = (doc.getLong("damage") ?: 0).toInt()

                        challenges = listOf(
                            Challenge("Get 10 kills", "Kill 10 enemies today", 10, kills, "50 XP"),
                            Challenge("Win 3 matches", "Win 3 matches today", 3, wins, "100 XP"),
                            Challenge("Play 5 matches", "Complete 5 matches", 5, matches, "75 XP"),
                            Challenge("Deal 5000 damage", "Deal 5000 total damage", 5000, damage, "150 XP"),
                            Challenge("Login today", "Open the app", 1, 1, "25 Coins")
                        )
                        val completed = challenges.count { it.isCompleted }
                        summary = "$completed/5 Completed • Bonus: ${completed * 50} XP"
                    }
                }
        }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Daily Challenges",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            Text(
                summary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = ZsGold,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(challenges) { challenge ->
                    ZSCard(
                        highlight = if (challenge.isCompleted) ZsAccent else null,
                        modifier = Modifier.alpha(if (challenge.isCompleted) 0.85f else 1f)
                    ) {
                        Text(challenge.title, color = ZsTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(challenge.description, color = ZsTextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        ZSProgress(
                            fraction = challenge.progress / 100f,
                            label = "${challenge.current}/${challenge.target}",
                            color = if (challenge.isCompleted) ZsGreen else ZsPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (challenge.isCompleted) "✓ COMPLETED" else "IN PROGRESS",
                            color = if (challenge.isCompleted) ZsGreen else ZsPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text("Reward: ${challenge.reward}", color = ZsGold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}