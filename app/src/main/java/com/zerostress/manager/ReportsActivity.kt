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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.zerostress.manager.ui.EmptyState
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.formatDateTime
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsGreen
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

/**
 * Admin-only moderation queue: every chat message reported by players lands
 * here (collection: chat_reports). Admins can delete the offending message
 * from chat and resolve the report.
 */
class ReportsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                ReportsScreen()
            }
        }
    }
}

@Composable
private fun ReportsScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    var reports by remember { mutableStateOf<List<com.google.firebase.firestore.DocumentSnapshot>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var resolveTarget by remember { mutableStateOf<com.google.firebase.firestore.DocumentSnapshot?>(null) }

    DisposableEffect(Unit) {
        val reg = db.collection("chat_reports")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, e ->
                loading = false
                if (e != null || snap == null) return@addSnapshotListener
                reports = snap.documents
            }
        onDispose { reg.remove() }
    }

    fun deleteOffendingMessage(report: com.google.firebase.firestore.DocumentSnapshot) {
        val messageId = report.getString("messageId") ?: return
        db.collection("chat_messages").document(messageId).delete()
            .addOnSuccessListener {
                Toast.makeText(context, "Message deleted from chat", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Could not delete message: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Chat Reports (${reports.size})",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            if (loading) {
                EmptyState("Loading reports...")
            } else if (reports.isEmpty()) {
                EmptyState("No reports — chat is clean 🏁")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(reports, key = { it.id }) { doc ->
                        val sender = doc.getString("senderName") ?: "Unknown"
                        val reporter = doc.getString("reporterName") ?: "Unknown"
                        val text = doc.getString("text") ?: ""
                        val ts = doc.getLong("timestamp") ?: 0L
                        val status = doc.getString("status") ?: "open"

                        ZSCard(
                            highlight = if (status == "open") ZsDanger else null,
                            onClick = { resolveTarget = doc }
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        sender,
                                        Modifier.weight(1f),
                                        color = ZsPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        if (status == "open") "OPEN" else "RESOLVED",
                                        color = if (status == "open") ZsDanger else ZsGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "\"$text\"",
                                    color = ZsTextPrimary,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Reported by $reporter • ${formatDateTime(ts)}",
                                    color = ZsTextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    resolveTarget?.let { report ->
        AlertDialog(
            onDismissRequest = { resolveTarget = null },
            title = { Text("Handle report") },
            text = {
                Text(
                    "Reported message from ${report.getString("senderName")}:\n\n" +
                        "\"${report.getString("text")}\"\n\n" +
                        "Delete removes the message from chat for everyone and marks " +
                        "the report resolved. Dismiss only marks it resolved.",
                    color = ZsTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    resolveTarget = null
                    deleteOffendingMessage(report)
                    report.reference.update("status", "resolved")
                }) { Text("Delete message", color = ZsDanger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    resolveTarget = null
                    report.reference.update("status", "resolved")
                }) { Text("Dismiss") }
            }
        )
    }
}
