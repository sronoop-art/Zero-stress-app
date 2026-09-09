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
import com.zerostress.manager.ui.ZSProgress
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsAccentDark
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

class BattlePassActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                BattlePassScreen()
            }
        }
    }
}

private data class BattlePassTier(
    val level: Int,
    val freeReward: String,
    val premiumReward: String,
    val unlocked: Boolean
)

@Composable
private fun BattlePassScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = FirebaseAuth.getInstance().uid

    var currentLevel by remember { mutableStateOf(1) }
    var currentXp by remember { mutableStateOf(0) }
    var tiers by remember { mutableStateOf<List<BattlePassTier>>(emptyList()) }

    LaunchedEffect(Unit) {
        if (userId != null) {
            db.collection("players").document(userId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        currentLevel = (doc.getLong("level") ?: 1).toInt()
                        currentXp = (doc.getLong("xp") ?: 0).toInt()

                        val freeRewards = listOf(
                            "50 XP", "100 Coins", "Bronze Title", "200 XP", "500 Coins",
                            "Silver Title", "500 XP", "1000 Coins", "Gold Title", "1000 XP",
                            "2000 Coins", "Diamond Title", "2000 XP", "5000 Coins", "Mythic Title"
                        )
                        val premiumRewards = listOf(
                            "100 XP", "200 Coins", "Rare Skin", "400 XP", "1000 Coins",
                            "Epic Skin", "1000 XP", "2000 Coins", "Legendary Skin", "2000 XP",
                            "5000 Coins", "Mythic Skin", "5000 XP", "10000 Coins", "Exclusive Skin"
                        )
                        tiers = (0 until 15).map { i ->
                            BattlePassTier(i + 1, freeRewards[i], premiumRewards[i], i + 1 <= currentLevel)
                        }
                    }
                }
        }
    }

    val xpToNext = currentLevel * 500
    val progress = if (xpToNext > 0) currentXp.toFloat() / xpToNext else 0f

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Battle Pass",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    "Season 1 • Level $currentLevel",
                    color = ZsTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                ZSProgress(
                    fraction = progress,
                    label = "$currentXp / $xpToNext XP to next level",
                    color = ZsGold
                )
            }
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tiers) { tier ->
                    ZSCard(
                        highlight = if (tier.unlocked) ZsAccent else null,
                        modifier = Modifier.alpha(if (tier.unlocked) 1f else 0.55f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Tier ${tier.level}",
                                    color = ZsTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "FREE: ${tier.freeReward}",
                                    color = ZsCyan,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "PREMIUM: ${tier.premiumReward}",
                                    color = ZsGold,
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                if (tier.unlocked) "✓ UNLOCKED" else "LOCKED",
                                color = if (tier.unlocked) ZsAccentDark else ZsTextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}