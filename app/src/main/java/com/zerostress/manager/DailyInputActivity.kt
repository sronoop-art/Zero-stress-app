package com.zerostress.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSTopBar

/**
 * Daily stats input.
 *
 * - Opened from the PLAYER dashboard: logs stats for the signed-in player.
 * - Opened from the ADMIN dashboard: passes "playerId" + "playerName" extras,
 *   shows a PLAYER picker at the top and the stats are saved for that player.
 */
class DailyInputActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PLAYER_ID = "playerId"
        const val EXTRA_PLAYER_NAME = "playerName"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val presetPlayerId = intent.getStringExtra(EXTRA_PLAYER_ID)
        val presetPlayerName = intent.getStringExtra(EXTRA_PLAYER_NAME)
        setContent {
            com.zerostress.manager.ui.theme.ZeroStressTheme {
                DailyInputScreen(presetPlayerId, presetPlayerName)
            }
        }
    }
}

@Composable
private fun DailyInputScreen(presetPlayerId: String?, presetPlayerName: String?) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().uid

    // Which player the entry belongs to. Defaults to the signed-in user;
    // admins get a picker (preselected with the player they tapped).
    var targetPlayerId by remember { mutableStateOf(presetPlayerId ?: uid ?: "") }
    var targetPlayerName by remember { mutableStateOf(presetPlayerName ?: "") }
    var isAdminMode by remember { mutableStateOf(presetPlayerId != null) }

    var roster by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // uid to name
    var showPlayerPicker by remember { mutableStateOf(false) }

    var kills by remember { mutableStateOf("") }
    var deaths by remember { mutableStateOf("") }
    var assists by remember { mutableStateOf("") }
    var damage by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var matchType by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var recent by remember { mutableStateOf("") }

    val matchTypes = listOf("Casual", "Ranked", "Tournament", "Scrim")

    // Resolve the current target's display name when not preset by the admin.
    LaunchedEffect(targetPlayerId, presetPlayerName) {
        if (targetPlayerName.isNotEmpty()) return@LaunchedEffect
        if (targetPlayerId.isEmpty()) return@LaunchedEffect
        db.collection("players").document(targetPlayerId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) targetPlayerName = doc.getString("name") ?: "Player"
            }
    }

    // Recent entries of the selected player.
    LaunchedEffect(targetPlayerId, recent) {
        if (targetPlayerId.isEmpty()) return@LaunchedEffect
        db.collection("daily_stats")
            .whereEqualTo("playerId", targetPlayerId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { snap ->
                recent = if (snap.isEmpty) {
                    ""
                } else {
                    snap.documents.joinToString("\n") { doc ->
                        val k = doc.getLong("kills") ?: 0
                        val d = doc.getLong("deaths") ?: 0
                        val a = doc.getLong("assists") ?: 0
                        val dm = doc.getLong("damage") ?: 0
                        val h = doc.getLong("hours") ?: 0
                        val mt = doc.getString("matchType") ?: "-"
                        "$mt - ${k}K/${d}D/${a}A - $dm dmg - ${h}h"
                    }
                }
            }
    }

    // Admins can re-pick any player; load the roster lazily when needed.
    fun openPlayerPicker() {
        if (roster.isEmpty()) {
            db.collection("players").get()
                .addOnSuccessListener { snap ->
                    roster = snap.documents
                        .filter { it.getString("role") != "admin" }
                        .map { (it.id) to (it.getString("name") ?: "Unknown") }
                    showPlayerPicker = true
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to load players", Toast.LENGTH_SHORT).show()
                }
        } else {
            showPlayerPicker = true
        }
    }

    fun submit() {
        val k = kills.toIntOrNull()
        val d = deaths.toIntOrNull()
        val a = assists.toIntOrNull()
        val dmg = damage.toLongOrNull()
        val h = hours.toDoubleOrNull()
        if (k == null || d == null || a == null || dmg == null || h == null) {
            Toast.makeText(context, "Fill every field with a valid number", Toast.LENGTH_SHORT).show()
            return
        }
        if (targetPlayerId.isEmpty()) {
            Toast.makeText(context, "Select a player first", Toast.LENGTH_SHORT).show()
            return
        }
        loading = true
        val entry = mapOf(
            "playerId" to targetPlayerId,
            "kills" to k,
            "deaths" to d,
            "assists" to a,
            "damage" to dmg,
            "hours" to h,
            "matchType" to (matchType ?: "Casual"),
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("daily_stats").add(entry)
            .addOnSuccessListener {
                loading = false
                Toast.makeText(context, "Stats logged for $targetPlayerName", Toast.LENGTH_SHORT).show()
                kills = ""; deaths = ""; assists = ""; damage = ""; hours = ""
                matchType = null
                recent = "" // triggers the recent-entries reload
            }
            .addOnFailureListener { e ->
                loading = false
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    ZSBackground {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ZSTopBar(
                title = "Daily Input",
                onBack = { (context as? android.app.Activity)?.finish() }
            )
            Spacer(Modifier.height(16.dp))

            ZSCard {
                Text("TODAY'S PERFORMANCE", color = com.zerostress.manager.ui.theme.ZsTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))

                // Player picker (admin mode only)
                if (isAdminMode) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { openPlayerPicker() }
                            .background(
                                color = com.zerostress.manager.ui.theme.ZsBgMid,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "PLAYER",
                                color = com.zerostress.manager.ui.theme.ZsTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                targetPlayerName.ifEmpty { "Tap to select player" },
                                color = if (targetPlayerName.isNotEmpty()) com.zerostress.manager.ui.theme.ZsTextPrimary else com.zerostress.manager.ui.theme.ZsTextMuted,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text("CHANGE", color = com.zerostress.manager.ui.theme.ZsCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(14.dp))
                }

                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { ZSField(value = kills, onValueChange = { kills = it }, label = "Kills", keyboardType = KeyboardType.Number) }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) { ZSField(value = deaths, onValueChange = { deaths = it }, label = "Deaths", keyboardType = KeyboardType.Number) }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) { ZSField(value = assists, onValueChange = { assists = it }, label = "Assists", keyboardType = KeyboardType.Number) }
                }
                Spacer(Modifier.height(12.dp))
                ZSField(value = damage, onValueChange = { damage = it }, label = "Damage", keyboardType = KeyboardType.Number)
                Spacer(Modifier.height(12.dp))
                ZSField(value = hours, onValueChange = { hours = it }, label = "Hours played", keyboardType = KeyboardType.Decimal)
                Spacer(Modifier.height(12.dp))
                ZSDropdown(
                    label = "Match type",
                    items = matchTypes,
                    selected = matchType,
                    onSelect = { matchType = it }
                )
                Spacer(Modifier.height(20.dp))
                ZSButton(text = if (loading) "Saving..." else "Log stats", enabled = !loading, onClick = { submit() })
            }

            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                ZSCard {
                    Text("RECENT ENTRIES - ${targetPlayerName.ifEmpty { "selected player" }}", color = com.zerostress.manager.ui.theme.ZsTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(recent, color = com.zerostress.manager.ui.theme.ZsTextMuted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // --- Player selection dialog (admin) ---
    if (showPlayerPicker) {
        AlertDialog(
            onDismissRequest = { showPlayerPicker = false },
            title = { Text("Select Player") },
            text = {
                Column {
                    if (roster.isEmpty()) {
                        Text("No players found", color = com.zerostress.manager.ui.theme.ZsTextMuted)
                    } else {
                        Column(
                            Modifier
                                .verticalScroll(rememberScrollState())
                                .height(320.dp)
                        ) {
                            roster.forEach { (id, name) ->
                                TextButton(
                                    onClick = {
                                        targetPlayerId = id
                                        targetPlayerName = name
                                        recent = ""
                                        showPlayerPicker = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        name,
                                        color = if (id == targetPlayerId) com.zerostress.manager.ui.theme.ZsCyan else com.zerostress.manager.ui.theme.ZsTextPrimary,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlayerPicker = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Version-safe dropdown picker: a read-only field that opens a plain
 * [DropdownMenu] on click. Avoids the experimental ExposedDropdownMenuBox API,
 * which requires @OptIn annotations that differ across material3 versions.
 */
@Composable
private fun ZSDropdown(
    label: String,
    items: List<String>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .background(
                    color = com.zerostress.manager.ui.theme.ZsCard,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                label.uppercase(),
                color = com.zerostress.manager.ui.theme.ZsTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                selected ?: "Select...",
                color = if (selected != null) com.zerostress.manager.ui.theme.ZsTextPrimary else com.zerostress.manager.ui.theme.ZsTextMuted,
                fontSize = 15.sp
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(com.zerostress.manager.ui.theme.ZsCard)
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            item,
                            color = if (item == selected) com.zerostress.manager.ui.theme.ZsCyan else com.zerostress.manager.ui.theme.ZsTextPrimary
                        )
                    },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    }
                )
            }
        }
    }
}
