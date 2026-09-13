package com.zerostress.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.ZsPngIcon
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCard
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

class PlayerTitlesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                PlayerTitlesScreen()
            }
        }
    }
}

// 8 rank titles, unlocked by score (same ladder as Player.getRankTier, extended upward).
// id = PNG name suffix: the badge loads ic_title_<id>.png from app/src/main/res/drawable/.
private data class RankTitle(
    val id: String,
    val name: String,
    val requirement: String,
    val unlockScore: Long,
    val color: Color
)

private val RANK_TITLES = listOf(
    RankTitle("bronze", "Bronze", "Reach 600 score", 600, Color(0xFFCD7F32)),
    RankTitle("silver", "Silver", "Reach 1,200 score", 1200, Color(0xFFC0C0C0)),
    RankTitle("gold", "Gold", "Reach 2,000 score", 2000, Color(0xFFFFD700)),
    RankTitle("platinum", "Platinum", "Reach 3,000 score", 3000, Color(0xFFE5E4E2)),
    RankTitle("diamond", "Diamond", "Reach 4,000 score", 4000, Color(0xFF4FC3F7)),
    RankTitle("heroic", "Heroic", "Reach 5,000 score", 5000, Color(0xFFB388FF)),
    RankTitle("master", "Master", "Reach 7,000 score", 7000, Color(0xFFFF5252)),
    RankTitle("grandmaster", "Grandmaster", "Reach 10,000 score", 10000, Color(0xFFFFD54F))
)

@Composable
private fun PlayerTitlesScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = FirebaseAuth.getInstance().uid

    var currentTitle by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(0L) }
    var refreshKey by remember { mutableStateOf(0) }
    var pendingSelect by remember { mutableStateOf<RankTitle?>(null) }

    LaunchedEffect(refreshKey) {
        if (userId == null) return@LaunchedEffect
        db.collection("players").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    currentTitle = doc.getString("title") ?: ""
                    score = doc.getLong("score") ?: 0L
                }
            }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "My Titles",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            Text(
                if (currentTitle.isEmpty()) "No title equipped" else "Equipped: $currentTitle",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = ZsCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(RANK_TITLES.size) { i ->
                    val title = RANK_TITLES[i]
                    val unlocked = score >= title.unlockScore
                    val equipped = currentTitle == title.name
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ZsCard, RoundedCornerShape(14.dp))
                            .clickable {
                                if (!unlocked) {
                                    Toast.makeText(
                                        context,
                                        "Locked - ${title.requirement}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    pendingSelect = title
                                }
                            }
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TitleBadge(title, unlocked, equipped)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            title.name,
                            color = ZsTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            title.requirement,
                            color = ZsTextSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                equipped -> "EQUIPPED"
                                unlocked -> "UNLOCKED"
                                else -> "LOCKED"
                            },
                            color = when {
                                equipped -> ZsAccent
                                unlocked -> ZsCyan
                                else -> ZsTextMuted
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    pendingSelect?.let { title ->
        AlertDialog(
            onDismissRequest = { pendingSelect = null },
            title = { Text("Select Title") },
            text = { Text("Set \"${title.name}\" as your title?", color = ZsTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    pendingSelect = null
                    db.collection("players").document(userId ?: "").update("title", title.name)
                        .addOnSuccessListener {
                            currentTitle = title.name
                            Toast.makeText(context, "Title set: ${title.name}", Toast.LENGTH_SHORT).show()
                        }
                }) { Text("Select", color = ZsAccent) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSelect = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Rank badge: shows ic_title_<id>.png if you added it to res/drawable/,
 * otherwise a colored medallion with the rank's first letter.
 * Equipped titles get an accent ring.
 */
@Composable
private fun TitleBadge(title: RankTitle, unlocked: Boolean, equipped: Boolean) {
    val context = LocalContext.current
    val resId = remember(title.id) {
        context.resources.getIdentifier(
            "ic_title_${title.id}", "drawable", context.packageName
        )
    }
    val badgeColor = if (unlocked) title.color else ZsTextMuted.copy(alpha = 0.6f)

    Box(
        modifier = Modifier
            .size(64.dp)
            .alpha(if (unlocked) 1f else 0.45f)
            .clip(CircleShape)
            .background(badgeColor.copy(alpha = 0.15f))
            .border(
                width = 2.dp,
                color = if (equipped) ZsAccent else badgeColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (resId != 0) {
            ZsPngIcon(resId, size = 40.dp)
        } else {
            Text(
                title.name.take(1),
                color = badgeColor,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp
            )
        }
    }
}
