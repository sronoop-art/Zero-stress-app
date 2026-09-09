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
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCard
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsPrimary
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

private data class TitleItem(
    val name: String,
    val requirement: String,
    val id: String
) {
    var unlocked: Boolean = false
}

@Composable
private fun PlayerTitlesScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = FirebaseAuth.getInstance().uid

    val titles = remember {
        listOf(
            TitleItem("Iron Warrior", "Begin your journey", "iron"),
            TitleItem("Bronze Fighter", "Reach 100 kills", "bronze"),
            TitleItem("Silver Striker", "Reach 500 kills", "silver"),
            TitleItem("Gold Champion", "Reach 1000 kills", "gold"),
            TitleItem("Diamond Legend", "Reach 5000 kills", "diamond"),
            TitleItem("Master Chief", "Reach 10000 kills", "master"),
            TitleItem("Winning Streak", "Win 5 matches in a row", "streak"),
            TitleItem("Untouchable", "Win without dying", "untouchable"),
            TitleItem("Damage King", "Deal 50000 total damage", "damage"),
            TitleItem("Team Player", "Play 100 matches", "team"),
            TitleItem("Rising Star", "Reach Level 10", "star"),
            TitleItem("Legendary", "Reach Mythic Rank", "legendary")
        )
    }

    var currentTitle by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }
    var pendingSelect by remember { mutableStateOf<TitleItem?>(null) }

    fun checkUnlocks() {
        if (userId == null) return
        db.collection("players").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val kills = doc.getLong("kills") ?: 0
                    val wins = doc.getLong("wins") ?: 0
                    val damage = doc.getLong("damage") ?: 0
                    val matches = doc.getLong("matches") ?: 0
                    val level = doc.getLong("level") ?: 1
                    val rank = doc.getString("rank") ?: "Iron"
                    currentTitle = doc.getString("title") ?: ""

                    for (title in titles) {
                        title.unlocked = when (title.id) {
                            "iron" -> true
                            "bronze" -> kills >= 100
                            "silver" -> kills >= 500
                            "gold" -> kills >= 1000
                            "diamond" -> kills >= 5000
                            "master" -> kills >= 10000
                            "streak" -> wins >= 5
                            "damage" -> damage >= 50000
                            "team" -> matches >= 100
                            "star" -> level >= 10
                            "legendary" -> rank == "Mythic"
                            else -> false
                        }
                    }
                }
            }
    }

    LaunchedEffect(refreshKey) {
        checkUnlocks()
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
                items(titles.size) { i ->
                    val title = titles[i]
                    val unlocked = title.unlocked
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (unlocked) 1f else 0.5f)
                            .background(ZsCard, RoundedCornerShape(14.dp))
                            .clickable {
                                if (!unlocked) {
                                    Toast.makeText(
                                        context,
                                        "Title locked! ${title.requirement}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    pendingSelect = title
                                }
                            }
                            .padding(14.dp)
                    ) {
                        Text(title.name, color = ZsTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(title.requirement, color = ZsTextSecondary, fontSize = 11.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            when {
                                currentTitle == title.name -> "✓ EQUIPPED"
                                unlocked -> "UNLOCKED"
                                else -> "🔒 LOCKED"
                            },
                            color = when {
                                currentTitle == title.name -> com.zerostress.manager.ui.theme.ZsAccentDark
                                unlocked -> ZsPrimary
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