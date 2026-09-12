package com.zerostress.manager

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import com.zerostress.manager.fcm.FCMConfig
import com.zerostress.manager.fcm.ZSFCMService
import com.zerostress.manager.ui.ZsPngIcon
import com.zerostress.manager.ui.EmptyState
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSMenuTile
import com.zerostress.manager.ui.ZSProgress
import com.zerostress.manager.ui.ZSStat
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

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

    val menuItems = remember {
        listOf(
            MenuItem(R.drawable.ic_menu_calendar, "Schedule", ScheduleActivity::class.java),
            MenuItem(R.drawable.ic_menu_trophy, "Leaderboard", LeaderboardActivity::class.java),
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
                    loaded = true
                }
            }
    }

    LaunchedEffect(Unit) {
        loadProfile()
        ZSFCMService.saveTokenToFirestoreWithRetry(context)
        FCMConfig.checkFCMConfiguration(context as android.app.Activity)
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            // Header
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Welcome back,", color = ZsTextMuted, fontSize = 13.sp)
                        Text(
                            name,
                            color = ZsTextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    ZsPngIcon(R.drawable.ic_menu_bell, size = 20.dp, tint = ZsGold, modifier = Modifier.padding(end = 4.dp))                }
                Spacer(Modifier.height(12.dp))

                // Stat cards
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ZSStat("Score", "$score", ZsGold, Modifier.weight(1f))
                    ZSStat("Level", "$level", ZsCyan, Modifier.weight(1f))
                    ZSStat("Rank", rank, ZsAccent, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ZSStat("Coins", "$coins", ZsGold, Modifier.weight(1f))
                    ZSStat("XP", "$xp / ${level * 500}", ZsCyan, Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                ZSProgress(
                    fraction = xp.toFloat() / (level * 500).toFloat(),
                    label = "XP progress to level ${level + 1}"
                )
            }

            // Menu grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
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
                            else -> androidx.compose.ui.graphics.Color(0xFF667EEA)
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
        }
    }
}