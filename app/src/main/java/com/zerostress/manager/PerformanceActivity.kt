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
import androidx.compose.runtime.DisposableEffect
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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.EmptyState
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.formatShortDateTime
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

class PerformanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                PerformanceScreen()
            }
        }
    }
}

@Composable
private fun PerformanceScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = FirebaseAuth.getInstance().uid

    var logs by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var summary by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val listener = if (userId != null) {
            db.collection("match_logs").whereEqualTo("playerId", userId)
                .orderBy("date").limit(50)
                .addSnapshotListener { snap, e ->
                    if (e != null || snap == null) return@addSnapshotListener
                    val list = snap.documents.reversed()
                    logs = list
                    var totalKills = 0L
                    var totalDamage = 0L
                    var totalWins = 0L
                    for (doc in list) {
                        totalKills += doc.getLong("kills") ?: 0
                        totalDamage += doc.getLong("damage") ?: 0
                        if (doc.getBoolean("win") == true) totalWins++
                    }
                    summary = "Last ${list.size} matches: $totalKills kills, $totalDamage damage, $totalWins wins"
                }
        } else null
        onDispose { listener?.remove() }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Performance",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            Text(
                summary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = ZsCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            if (logs.isEmpty()) {
                EmptyState("No match logs yet")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(logs, key = { it.id }) { doc ->
                        val win = doc.getBoolean("win") == true
                        val kills = doc.getLong("kills") ?: 0
                        val damage = doc.getLong("damage") ?: 0
                        val date = doc.getLong("date") ?: 0L
                        val type = doc.getString("matchType") ?: "Classic"

                        ZSCard(highlight = if (win) ZsAccent else null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${if (win) "🏆" else "❌"} K:$kills  Dmg:$damage",
                                        color = ZsTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        formatShortDateTime(date),
                                        color = ZsTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    type,
                                    color = ZsTextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}