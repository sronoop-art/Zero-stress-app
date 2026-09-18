package com.zerostress.manager

import android.os.Bundle
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.EmptyState
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.formatDate
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsBorder
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

class SeasonActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                SeasonScreen()
            }
        }
    }
}

@Composable
private fun SeasonScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    var seasons by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }

    DisposableEffect(Unit) {
        // No orderBy: avoids needing a Firestore composite index — sort client-side
        val listener = db.collection("seasons").limit(50)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) return@addSnapshotListener
                seasons = snap.documents.sortedBy { it.getLong("createdAt") ?: 0L }
            }
        onDispose { listener.remove() }
    }

    val current = seasons.lastOrNull()
    val currentText = if (current != null) {
        val active = current.getBoolean("active") != false
        "Current: ${current.getString("name")} (${if (active) "Active" else "Ended"})"
    } else {
        "No seasons yet"
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Seasons",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            Text(
                currentText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = ZsAccent,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )

            if (seasons.isEmpty()) {
                EmptyState("No seasons yet")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(seasons, key = { it.id }) { doc ->
                        val createdAt = doc.getLong("createdAt")
                        val duration = doc.getString("duration")
                        val desc = doc.getString("description")
                        val info = buildString {
                            if (createdAt != null) append("${formatDate(createdAt)}")
                            if (!duration.isNullOrEmpty()) append(" • $duration days")
                            if (!desc.isNullOrEmpty()) append(" • $desc")
                        }
                        val active = doc.getBoolean("active") != false

                        ZSCard(highlight = if (active) ZsPrimary else null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Letter badge: first letter of the season name, red for the active season
                                Box(
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(38.dp)
                                        .background(
                                            color = if (active) ZsPrimary else ZsBorder.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(6.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        (doc.getString("name") ?: "S").take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontStyle = FontStyle.Italic,
                                        fontSize = 16.sp
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        doc.getString("name") ?: "Season",
                                        color = ZsTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        if (info.isEmpty()) "Season" else info,
                                        color = ZsTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    if (active) "ACTIVE" else "Ended",
                                    color = if (active) ZsPrimary else ZsTextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic,
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