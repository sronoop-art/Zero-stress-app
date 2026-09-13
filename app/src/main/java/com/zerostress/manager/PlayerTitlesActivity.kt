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
import com.zerostress.manager.models.ZsRankTitles
import com.zerostress.manager.models.ZsRankTitles.RankTitle
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
                items(ZsRankTitles.ALL.size) { i ->
                    val title = ZsRankTitles.ALL[i]
                    // Unlock threshold is OTA-tunable via Remote Config
                    // (title_<id>_score) and falls back to the built-in value.
                    val threshold = com.zerostress.manager.ota.ZsRemoteConfig.titleScore(title.id, title.unlockScore)
                    val unlocked = score >= threshold
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
                        TitleBadge(context, title, unlocked, equipped)
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
 * Rank badge: shows ic_title_<id>.png if added to res/drawable/,
 * otherwise a colored medallion with the rank's first letter.
 * Equipped titles get an accent ring.
 */
@Composable
private fun TitleBadge(context: android.content.Context, title: RankTitle, unlocked: Boolean, equipped: Boolean) {
    val resId = remember(title.id) { ZsRankTitles.badgeRes(context, title) }
    val badgeColor = Color(title.color)
    val displayColor = if (unlocked) badgeColor else ZsTextMuted.copy(alpha = 0.6f)

    Box(
        modifier = Modifier
            .size(64.dp)
            .alpha(if (unlocked) 1f else 0.45f)
            .clip(CircleShape)
            .background(displayColor.copy(alpha = 0.15f))
            .border(
                width = 2.dp,
                color = if (equipped) ZsAccent else displayColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (resId != 0) {
            ZsPngIcon(resId, size = 40.dp)
        } else {
            Text(
                title.name.take(1),
                color = displayColor,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp
            )
        }
    }
}
