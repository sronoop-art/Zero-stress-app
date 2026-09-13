package com.zerostress.manager

import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.models.ZsRankTitles
import com.zerostress.manager.ui.EmptyState
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSKeyValue
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSStat
import com.zerostress.manager.ui.ZsAvatarFrame
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.ZsPngIcon
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import java.util.Locale

/**
 * Read-only profile view for ANY player.
 *
 * Opened with EXTRA_PLAYER_ID (required) and EXTRA_PLAYER_NAME (optional).
 * Viewers see the player's stats, rank, level, equipped title (with its
 * avatar frame) and online state. Nothing here can be edited.
 */
class PlayerProfileViewActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PLAYER_ID = "playerId"
        const val EXTRA_PLAYER_NAME = "playerName"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val playerId = intent.getStringExtra(EXTRA_PLAYER_ID)
        val playerName = intent.getStringExtra(EXTRA_PLAYER_NAME)
        setContent {
            ZeroStressTheme {
                PlayerProfileViewScreen(playerId, playerName)
            }
        }
    }
}

@Composable
private fun PlayerProfileViewScreen(playerId: String?, presetName: String?) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    var loading by remember { mutableStateOf(true) }
    var notFound by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf(presetName ?: "") }
    var equippedTitle by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(0L) }
    var level by remember { mutableStateOf(1L) }
    var rank by remember { mutableStateOf("Iron") }
    var kills by remember { mutableStateOf(0L) }
    var deaths by remember { mutableStateOf(0L) }
    var assists by remember { mutableStateOf(0L) }
    var damage by remember { mutableStateOf(0L) }
    var wins by remember { mutableStateOf(0L) }
    var matches by remember { mutableStateOf(0L) }
    var gameRole by remember { mutableStateOf("") }
    var online by remember { mutableStateOf(false) }

    LaunchedEffect(playerId) {
        if (playerId == null) {
            notFound = true
            loading = false
            return@LaunchedEffect
        }
        db.collection("players").document(playerId).get()
            .addOnSuccessListener { doc ->
                loading = false
                if (!doc.exists()) {
                    notFound = true
                    return@addOnSuccessListener
                }
                name = doc.getString("name") ?: "Unknown"
                equippedTitle = doc.getString("title") ?: ""
                score = doc.getLong("score") ?: 0
                level = doc.getLong("level") ?: 1
                rank = doc.getString("rank") ?: "Iron"
                kills = doc.getLong("kills") ?: 0
                deaths = doc.getLong("deaths") ?: 0
                assists = doc.getLong("assists") ?: 0
                damage = doc.getLong("damage") ?: 0
                wins = doc.getLong("wins") ?: 0
                matches = doc.getLong("matches") ?: 0
                gameRole = doc.getString("gameRole") ?: ""
                val lastSeen = doc.getLong("lastSeen")
                online = lastSeen != null && System.currentTimeMillis() - lastSeen < 300000
            }
            .addOnFailureListener {
                loading = false
                notFound = true
                Toast.makeText(context, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Player Profile",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            when {
                loading -> EmptyState("Loading profile...")
                notFound -> EmptyState("Player not found")
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(Modifier.height(8.dp))

                    // ---- Header: framed avatar, name, title, online ----
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val titleObj = ZsRankTitles.byName(equippedTitle)
                        val frameSource = if (titleObj != null) ZsRankTitles.frameSource(context, titleObj) else null
                        val frameColor = if (titleObj != null) Color(titleObj.color) else ZsPrimary
                        ZsAvatarFrame(frameSource, frameColor, 96.dp) {
                            Box(
                                Modifier
                                    .size(96.dp)
                                    .background(ZsPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                ZsPngIcon(
                                    R.drawable.ic_menu_person,
                                    size = 76.dp,
                                    tint = ZsTextSecondary
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            name,
                            color = ZsTextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                        if (equippedTitle.isNotEmpty()) {
                            Text(
                                equippedTitle,
                                color = frameColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (online) "Online" else "Offline",
                            color = if (online) ZsAccent else ZsTextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (gameRole.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                gameRole,
                                color = ZsCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ---- Key stats ----
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ZSStat("Score", "$score", ZsGold, Modifier.weight(1f))
                        ZSStat("Level", "$level", ZsCyan, Modifier.weight(1f))
                        ZSStat("Rank", rank, ZsAccent, Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(16.dp))

                    // ---- Detailed stats ----
                    ZSCard {
                        ZSKeyValue("Kills", "$kills", ZsTextPrimary)
                        Spacer(Modifier.height(8.dp))
                        ZSKeyValue("Deaths", "$deaths")
                        Spacer(Modifier.height(8.dp))
                        ZSKeyValue("Assists", "$assists")
                        Spacer(Modifier.height(8.dp))
                        ZSKeyValue("Damage", "$damage")
                        Spacer(Modifier.height(8.dp))
                        ZSKeyValue("Wins", "$wins")
                        Spacer(Modifier.height(8.dp))
                        ZSKeyValue("Matches", "$matches")
                        Spacer(Modifier.height(8.dp))
                        ZSKeyValue(
                            "Win Rate",
                            String.format(Locale.getDefault(), "%.1f%%", if (matches > 0) wins * 100.0 / matches else 0.0)
                        )
                        Spacer(Modifier.height(8.dp))
                        ZSKeyValue(
                            "Avg Damage",
                            String.format(Locale.getDefault(), "%.0f", if (matches > 0) damage * 1.0 / matches else 0.0)
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
