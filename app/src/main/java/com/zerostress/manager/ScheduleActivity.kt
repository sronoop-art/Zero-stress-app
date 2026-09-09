package com.zerostress.manager

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.EmptyState
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsGreen
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import com.zerostress.manager.ui.theme.ZsWarning

class ScheduleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                ScheduleScreen()
            }
        }
    }
}

@Composable
private fun ScheduleScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }

    var schedules by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var isAdmin by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DocumentSnapshot?>(null) }

    DisposableEffect(Unit) {
        val listener = db.collection("match_schedules").orderBy("matchTime").limit(30)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) return@addSnapshotListener
                schedules = snap.documents
            }
        onDispose { listener.remove() }
    }

    LaunchedEffect(Unit) {
        auth.uid?.let { uid ->
            db.collection("players").document(uid).get()
                .addOnSuccessListener { doc ->
                    isAdmin = doc.getString("role") == "admin"
                }
        }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Match Schedule",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            if (schedules.isEmpty()) {
                EmptyState("No matches scheduled yet")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(schedules, key = { it.id }) { doc ->
                        val title = doc.getString("title") ?: "Match"
                        val desc = doc.getString("description")
                            ?: doc.getString("type")
                            ?: "No description"
                        val status = doc.getString("status") ?: "Upcoming"
                        val userDateTime = doc.getString("dateTime")
                        val timeText = if (!userDateTime.isNullOrEmpty() && userDateTime != "TBD") {
                            "🕐 $userDateTime"
                        } else {
                            "🕐 ${com.zerostress.manager.ui.formatDateTime(doc.getLong("matchTime") ?: 0L)}"
                        }

                        ZSCard(highlight = if (status == "Upcoming") ZsCyan else ZsGreen) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(title, color = ZsTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(Modifier.height(3.dp))
                                    Text(desc, color = ZsTextSecondary, fontSize = 13.sp)
                                    Spacer(Modifier.height(3.dp))
                                    Text(timeText, color = ZsTextMuted, fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        status,
                                        color = if (status == "Upcoming") ZsWarning else ZsGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isAdmin) {
                                        Spacer(Modifier.height(6.dp))
                                        TextButton(onClick = { deleteTarget = doc }) {
                                            Text("🗑️", color = ZsDanger)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("🗑️ Delete Schedule") },
            text = { Text("Are you sure you want to delete this schedule?", color = ZsTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    db.collection("match_schedules").document(doc.id).delete()
                        .addOnSuccessListener {
                            Toast.makeText(context, "Schedule deleted!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    deleteTarget = null
                }) { Text("Delete", color = ZsDanger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}