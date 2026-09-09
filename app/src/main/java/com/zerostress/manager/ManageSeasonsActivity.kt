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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.EmptyState
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.formatDate
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsAccentDark
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

class ManageSeasonsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                ManageSeasonsScreen()
            }
        }
    }
}

@Composable
private fun ManageSeasonsScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    var seasons by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DocumentSnapshot?>(null) }

    fun loadSeasons() {
        db.collection("seasons").orderBy("createdAt").get()
            .addOnSuccessListener { query -> seasons = query.documents }
    }

    LaunchedEffect(Unit) {
        loadSeasons()
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Manage Seasons",
                onBack = { (context as? android.app.Activity)?.finish() },
                right = {
                    TextButton(onClick = { showAddDialog = true }) {
                        Text("+ Add", color = ZsCyan, fontWeight = FontWeight.Bold)
                    }
                }
            )

            if (seasons.isEmpty()) {
                EmptyState("No seasons yet — tap + Add to create one")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(seasons, key = { it.id }) { doc ->
                        val active = doc.getBoolean("active") != false
                        val createdAt = doc.getLong("createdAt")
                        ZSCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        doc.getString("name") ?: "Season",
                                        color = ZsTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        doc.getString("description") ?: "",
                                        color = ZsTextSecondary,
                                        fontSize = 13.sp
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        buildString {
                                            append("Duration: ${doc.getString("duration") ?: "30"} days")
                                            if (createdAt != null) append(" • ${formatDate(createdAt)}")
                                        },
                                        color = ZsTextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        if (active) "● ACTIVE" else "○ INACTIVE",
                                        color = if (active) ZsAccentDark else ZsTextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
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

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var duration by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("➕ Add New Season") },
            text = {
                Column {
                    ZSField(value = name, onValueChange = { name = it }, label = "Season Name", placeholder = "e.g., Season 2")
                    Spacer(Modifier.height(10.dp))
                    ZSField(value = desc, onValueChange = { desc = it }, label = "Description")
                    Spacer(Modifier.height(10.dp))
                    ZSField(value = duration, onValueChange = { duration = it }, label = "Duration (days)")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    if (name.trim().isEmpty()) {
                        Toast.makeText(context, "Name required", Toast.LENGTH_SHORT).show()
                    } else {
                        db.collection("seasons").add(
                            mapOf(
                                "name" to name.trim(),
                                "description" to desc.trim(),
                                "duration" to (if (duration.trim().isEmpty()) "30" else duration.trim()),
                                "active" to true,
                                "createdAt" to System.currentTimeMillis()
                            )
                        ).addOnSuccessListener {
                            Toast.makeText(context, "Season created!", Toast.LENGTH_SHORT).show()
                            loadSeasons()
                        }
                    }
                }) { Text("Create", color = ZsAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Season") },
            text = { Text("Delete \"${doc.getString("name")}\"?", color = ZsTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    db.collection("seasons").document(doc.id).delete()
                        .addOnSuccessListener {
                            Toast.makeText(context, "Season deleted", Toast.LENGTH_SHORT).show()
                            loadSeasons()
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