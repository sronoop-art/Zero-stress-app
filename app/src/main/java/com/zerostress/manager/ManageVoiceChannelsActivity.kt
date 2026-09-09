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
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsAccentDark
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

class ManageVoiceChannelsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                ManageVoiceChannelsScreen()
            }
        }
    }
}

@Composable
private fun ManageVoiceChannelsScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    var channels by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var participants by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DocumentSnapshot?>(null) }

    fun loadChannels() {
        db.collection("voice_channels").get()
            .addOnSuccessListener { query ->
                channels = query.documents
                // Count participants per channel
                query.documents.forEach { doc ->
                    db.collection("voice_channels").document(doc.id)
                        .collection("participants").get()
                        .addOnSuccessListener { snap ->
                            participants = participants + (doc.id to snap.size())
                        }
                }
            }
    }

    LaunchedEffect(Unit) {
        loadChannels()
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Voice Channels",
                onBack = { (context as? android.app.Activity)?.finish() },
                right = {
                    TextButton(onClick = { showAddDialog = true }) {
                        Text("+ Add", color = ZsCyan, fontWeight = FontWeight.Bold)
                    }
                }
            )

            if (channels.isEmpty()) {
                EmptyState("No voice channels yet")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(channels, key = { it.id }) { doc ->
                        val active = doc.getBoolean("active") != false
                        val count = participants[doc.id] ?: 0
                        ZSCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        doc.getString("name") ?: doc.id,
                                        color = ZsTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        "$count connected",
                                        color = ZsTextSecondary,
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
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("➕ Add Voice Channel") },
            text = {
                ZSField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Channel name",
                    placeholder = "e.g., Squad 1"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    val channelName = name.trim()
                    if (channelName.isEmpty()) {
                        Toast.makeText(context, "Name required", Toast.LENGTH_SHORT).show()
                    } else {
                        db.collection("voice_channels").document(channelName).set(
                            mapOf(
                                "name" to channelName,
                                "active" to true,
                                "createdAt" to System.currentTimeMillis()
                            )
                        ).addOnSuccessListener {
                            Toast.makeText(context, "Channel created!", Toast.LENGTH_SHORT).show()
                            loadChannels()
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
            title = { Text("Delete Channel") },
            text = { Text("Delete \"${doc.getString("name") ?: doc.id}\" channel?", color = ZsTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    db.collection("voice_channels").document(doc.id).delete()
                        .addOnSuccessListener {
                            Toast.makeText(context, "Channel deleted", Toast.LENGTH_SHORT).show()
                            loadChannels()
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