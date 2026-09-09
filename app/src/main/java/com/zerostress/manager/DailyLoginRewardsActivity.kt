package com.zerostress.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsAccentDark
import com.zerostress.manager.ui.theme.ZsCard
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import java.util.Calendar

class DailyLoginRewardsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                DailyLoginRewardsScreen()
            }
        }
    }
}

private data class RewardDay(
    val day: Int,
    val reward: String,
    val coins: Int,
    var unlocked: Boolean
)

@Composable
private fun DailyLoginRewardsScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = FirebaseAuth.getInstance().uid

    var currentStreak by remember { mutableStateOf(0) }
    var claimedToday by remember { mutableStateOf(false) }
    var rewards by remember { mutableStateOf<List<RewardDay>>(emptyList()) }
    var showClaimDialog by remember { mutableStateOf(false) }

    fun loadRewards() {
        if (userId == null) return
        db.collection("players").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    currentStreak = (doc.getLong("loginStreak") ?: 0).toInt()
                    val lastLogin = doc.getLong("lastLoginDate")

                    val today = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    claimedToday = lastLogin != null && lastLogin >= today.timeInMillis

                    val list = listOf(
                        RewardDay(1, "50 Coins", 50, false),
                        RewardDay(2, "100 Coins", 100, false),
                        RewardDay(3, "200 Coins", 200, false),
                        RewardDay(4, "500 Coins", 500, false),
                        RewardDay(5, "1000 Coins", 1000, false),
                        RewardDay(6, "2000 Coins", 2000, false),
                        RewardDay(7, "5000 Coins + Title", 5000, false)
                    )
                    for (i in 0 until minOf(currentStreak, 7)) {
                        list[i].unlocked = true
                    }
                    rewards = list
                }
            }
    }

    fun claimReward() {
        if (claimedToday) {
            Toast.makeText(context, "Already claimed today!", Toast.LENGTH_SHORT).show()
            return
        }
        showClaimDialog = true
    }

    LaunchedEffect(Unit) {
        loadRewards()
    }

    val dayIndex = currentStreak % 7
    val todayReward = rewards.getOrNull(dayIndex)

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Daily Rewards",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            Text(
                "🔥 Streak: $currentStreak days",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = ZsGold,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (claimedToday) "✓ Claimed today" else "Tap to claim!",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = if (claimedToday) ZsAccentDark else ZsAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(rewards.size) { i ->
                    val reward = rewards[i]
                    val isToday = i == dayIndex && !claimedToday
                    val bg = when {
                        isToday -> ZsPrimary
                        reward.unlocked -> ZsAccentDark.copy(alpha = 0.25f)
                        else -> ZsCard
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (reward.unlocked || isToday) 1f else 0.5f)
                            .background(bg, RoundedCornerShape(14.dp))
                            .clickable(enabled = isToday) { claimReward() }
                            .padding(vertical = 14.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Day ${reward.day}",
                            color = if (isToday) androidx.compose.ui.graphics.Color.White else ZsTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            reward.reward,
                            color = if (isToday) androidx.compose.ui.graphics.Color.White else ZsTextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (reward.unlocked) "✓" else if (isToday) "CLAIM" else "🔒",
                            color = if (reward.unlocked) ZsAccent else if (isToday) androidx.compose.ui.graphics.Color.White else ZsTextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showClaimDialog && todayReward != null) {
        AlertDialog(
            onDismissRequest = { showClaimDialog = false },
            title = { Text("🎁 Claim Daily Reward") },
            text = {
                Text(
                    "Claim your Day ${dayIndex + 1} reward?",
                    color = ZsTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClaimDialog = false
                    val rewardCoins = todayReward.coins
                    val newStreak = if (currentStreak + 1 > 7) 1 else currentStreak + 1
                    db.collection("players").document(userId ?: "").get()
                        .addOnSuccessListener { doc ->
                            val currentCoins = doc.getLong("coins") ?: 0
                            db.collection("players").document(userId ?: "").update(
                                mapOf(
                                    "loginStreak" to newStreak,
                                    "lastLoginDate" to System.currentTimeMillis(),
                                    "coins" to currentCoins + rewardCoins
                                )
                            ).addOnSuccessListener {
                                Toast.makeText(context, "+$rewardCoins Coins!", Toast.LENGTH_SHORT).show()
                                claimedToday = true
                                loadRewards()
                            }
                        }
                }) { Text("Claim", color = ZsAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showClaimDialog = false }) { Text("Cancel") }
            }
        )
    }
}