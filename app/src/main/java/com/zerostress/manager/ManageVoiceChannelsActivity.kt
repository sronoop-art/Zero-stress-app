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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import com.zerostress.manager.ui.theme.ZsWarning

class ManageVoiceChannelsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ManageVoiceChannelsScreen()
        }
    }
}

data class ChannelItem(
    val doc: DocumentSnapshot,
    val enabled: Boolean,
    val participantCount: Int
)

@Composable
private fun ManageVoiceChannelsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = FirebaseAuth.getInstance().uid

    var channels by remember { mutableStateOf<List<ChannelItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDisableDialog by remember { mutableStateOf(false) }
    var showPlayerDialog by remember { mutableStateOf(false) }
    var selectedChannel by remember { mutableStateOf<ChannelItem?>(null) }

    ZSBackground {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            ZSTopBar(
                title = "Voice Channels",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            if (!loaded) {
                Text("Loading...", color = ZsTextMuted)
            } else if (channels.isEmpty()) {
                Text("No voice channels yet.", color = ZsTextMuted)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(channels, key = { it.doc.id }) { channel ->
                        SelectionCard(
                            item = channel,
                            onSelect = { selectedChannel = channel },
                            onDisable = { showDisableDialog = true },
                            onDelete = { showDeleteDialog = true },
                            onManagePlayers = { showPlayerDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showDisableDialog && selectedChannel != null) {
        AlertDialog(
            onDismissRequest = {
                showDisableDialog = false
                selectedChannel = null
            },
            title = { Text("Toggle channel") },
            text = {
                Text(
                    if (selectedChannel!!.enabled)
                        "Disable \"${selectedChannel!!.doc.getString("name") ?: selectedChannel!!.doc.id}\" channel?"
                    else
                        "Enable \"${selectedChannel!!.doc.getString("name") ?: selectedChannel!!.doc.id}\" channel?",
                    color = ZsTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val enable = !selectedChannel!!.enabled
                    selectedChannel!!.doc?.let { doc ->
                        db.collection("voice_channels").document(doc.id).update("active", enable)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    context,
                                    if (enable) "Channel enabled" else "Channel disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                                showDisableDialog = false
                                selectedChannel = null
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }) {
                    Text(
                        if (selectedChannel!!.enabled) "Disable" else "Enable",
                        color = ZsAccent
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDisableDialog = false
                    selectedChannel = null
                }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialog && selectedChannel != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                selectedChannel = null
            },
            title = { Text("Delete channel") },
            text = {
                Text(
                    "Delete \"${selectedChannel!!.doc.getString("name") ?: selectedChannel!!.doc.id}\"?\n\nThis removes the channel and all call presence data.",
                    color = ZsTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedChannel!!.doc?.let { doc ->
                            db.collection("voice_channels").document(doc.id).delete()
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Channel deleted", Toast.LENGTH_SHORT).show()
                                    showDeleteDialog = false
                                    selectedChannel = null
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                ) {
                    Text("Delete", color = ZsDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    selectedChannel = null
                }) { Text("Cancel") }
            }
        )
    }

    if (showPlayerDialog && selectedChannel != null) {
        var players by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
        var loaded by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            db.collection("players")
                .whereEqualTo("status", "approved")
                .get()
                .addOnSuccessListener {
                    players = it.documents.filter { it.id != userId }
                    loaded = true
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to load players", Toast.LENGTH_SHORT).show()
                    loaded = true
                }
        }
        AlertDialog(
            onDismissRequest = {
                showPlayerDialog = false
                selectedChannel = null
            },
            title = { Text("Manage voice access") },
            text = {
                Column {
                    Text(
                        "Allow which players can join voice channels?",
                        color = ZsTextMuted,
                        fontSize = 12.sp
                    )
                    if (!loaded) {
                        Text("Loading...", color = ZsTextMuted, modifier = Modifier.padding(8.dp))
                    } else if (players.isEmpty()) {
                        Text("No approved players found.", color = ZsTextMuted)
                    } else {
                        players.forEach { player ->
                            var enabled by remember(player.id) {
                                mutableStateOf(player.getBoolean("voiceAllowed") != false)
                            }
                            TextButton(
                                onClick = {
                                    enabled = !enabled
                                    db.collection("players")
                                        .document(player.id)
                                        .update("voiceAllowed", enabled)
                                        .addOnFailureListener {
                                            Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show()
                                        }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "${if (enabled) "✅" else "❌"} ${player.getString("name") ?: "Unknown"}",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPlayerDialog = false
                    selectedChannel = null
                    Toast.makeText(context, "Voice access saved", Toast.LENGTH_SHORT).show()
                }) { Text("Done", color = ZsAccent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPlayerDialog = false
                    selectedChannel = null
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SelectionCard(
    item: ChannelItem,
    onSelect: () -> Unit,
    onDisable: () -> Unit,
    onDelete: () -> Unit,
    onManagePlayers: () -> Unit
) {
    ZSCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.doc.getString("name") ?: item.doc.id,
                    color = ZsTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${item.participantCount} in call · ${if (item.enabled) "active" else "offline"}",
                    color = ZsTextSecondary,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onManagePlayers) {
                        Text("👥", color = ZsCyan, fontSize = 16.sp)
                    }
                    TextButton(onClick = onSelect) {
                        Text("✏️", color = ZsTextSecondary, fontSize = 16.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.enabled) {
                        TextButton(onClick = onDisable) {
                            Text("⏸️", color = ZsWarning, fontSize = 16.sp)
                        }
                    }
                    TextButton(onClick = onDelete) {
                        Text("🗑️", color = ZsDanger, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
