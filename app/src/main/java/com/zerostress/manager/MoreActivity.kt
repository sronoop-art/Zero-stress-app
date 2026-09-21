package com.zerostress.manager

import android.content.Intent
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSMenuTile
import com.zerostress.manager.ui.ZSBottomNav
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.launchTab
import com.zerostress.manager.ui.zsNavItems
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextPrimary
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * "More" screen - the 6th bottom-nav destination. Hosts every destination that
 * used to sit in the home screen's 3-column menu grid (plus Logout), so HOME
 * stays a clean stats surface. Tiles launch via launchTab so each screen still
 * exists at most once (no layer stacking).
 */
class MoreActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                MoreScreen()
            }
        }
    }
}

private data class MoreItem(val iconRes: Int, val label: String, val target: Class<*>)

@Composable
private fun MoreScreen() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }

    // Same destinations the home grid had (bottom-nav tabs removed here).
    val moreItems = listOf(
        MoreItem(R.drawable.ic_menu_calendar, "Schedule", ScheduleActivity::class.java),
        MoreItem(R.drawable.ic_menu_chat, "Team Chat", ChatActivity::class.java),
        MoreItem(R.drawable.ic_menu_mic, "Voice Chat", VoiceActivity::class.java),
        MoreItem(R.drawable.ic_menu_friends, "Friends", FriendsActivity::class.java),
        MoreItem(R.drawable.ic_menu_medal, "Seasons", SeasonActivity::class.java),
        MoreItem(R.drawable.ic_menu_medal, "Achievements", AchievementsActivity::class.java),
        MoreItem(R.drawable.ic_menu_announce, "Announcements", AnnouncementsActivity::class.java),
        MoreItem(R.drawable.ic_menu_gift, "Daily Rewards", DailyLoginRewardsActivity::class.java),
        MoreItem(R.drawable.ic_menu_fire, "Daily Challenges", DailyChallengesActivity::class.java),
        MoreItem(R.drawable.ic_menu_ticket, "Battle Pass", BattlePassActivity::class.java),
        MoreItem(R.drawable.ic_menu_sparkles, "My Titles", PlayerTitlesActivity::class.java),
        MoreItem(R.drawable.ic_menu_bell, "Notifications", NotificationsActivity::class.java),
        MoreItem(R.drawable.ic_menu_settings, "Settings", SettingsActivity::class.java)
    )

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "More",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            Text(
                "ALL FEATURES",
                color = ZsTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
            ) {
                items(moreItems) { item ->
                    ZSMenuTile(
                        label = item.label,
                        accent = ZsPrimary,
                        iconRes = item.iconRes,
                        onClick = { context.launchTab(item.target) }
                    )
                }
                item {
                    ZSMenuTile(
                        label = "Logout",
                        accent = ZsDanger,
                        iconRes = R.drawable.ic_menu_logout,
                        onClick = {
                            auth.signOut()
                            // CLEAR_TASK wipes every activity behind the logout so
                            // the next login starts on a clean stack.
                            context.startActivity(Intent(context, LoginActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            })
                            (context as? android.app.Activity)?.finishAffinity()
                        }
                    )
                }
            }

            // Neon Glass bottom navigation shell (More = index 5)
            ZSBottomNav(zsNavItems(5, context))
        }
    }
}
