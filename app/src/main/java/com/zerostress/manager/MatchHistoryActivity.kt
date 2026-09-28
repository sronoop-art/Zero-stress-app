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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.zerostress.manager.ui.EmptyState
import com.zerostress.manager.ui.LoadingBox
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSBadge
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsSuccess
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Match history: a read-only list of the signed-in player's match_logs,
 * newest first. Stats are still admin-entered (DailyInputActivity) - this
 * screen only visualizes what already exists. Score is shown from the log's
 * own "score" field, falling back to the shared recomputation for legacy
 * logs that never stored one (same rule as the dashboard chart).
 */
class MatchHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                MatchHistoryScreen()
            }
        }
    }
}

@Composable
private fun MatchHistoryScreen() {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = auth.uid

    var loading by remember { mutableStateOf(true) }
    var logs by remember { mutableStateOf<List<com.google.firebase.firestore.DocumentSnapshot>>(emptyList()) }

    LaunchedEffect(uid) {
        if (uid == null) {
            loading = false
            return@LaunchedEffect
        }
        // Same query shape the dashboard/performance screens use:
        // match_logs(playerId, date) composite index.
        db.collection("match_logs")
            .whereEqualTo("playerId", uid)
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { q -> logs = q.documents; loading = false }
            .addOnFailureListener { loading = false }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Match History",
                onBack = { (androidx.compose.ui.platform.LocalContext.current as? android.app.Activity)?.finish() }
            )
            if (loading) {
                LoadingBox(Modifier.weight(1f))
            } else if (logs.isEmpty()) {
                EmptyState("No matches logged yet", Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 24.dp, top = 4.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(logs, key = { it.id }) { doc ->
                        val kills = doc.getLong("kills") ?: 0L
                        val deaths = doc.getLong("deaths") ?: 0L
                        val assists = doc.getLong("assists") ?: 0L
                        val damage = doc.getLong("damage") ?: 0L
                        val win = doc.getBoolean("win") == true
                        val matchType = doc.getString("matchType") ?: "Casual"
                        val date = doc.getLong("date") ?: 0L
                        val score = doc.getLong("score") ?: ZsScore.logScore(doc)
                        val dateText = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                            .format(Date(date))

                        ZSCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (win) "VICTORY" else "DEFEAT",
                                            color = if (win) ZsSuccess else ZsDanger,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        ZSBadge(matchType, ZsCyan)
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        dateText,
                                        color = ZsTextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "$kills K  ·  $deaths D  ·  $assists A  ·  ${damage} dmg",
                                        color = ZsTextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "+$score",
                                        color = ZsCyan,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        "SCORE",
                                        color = ZsTextMuted,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
