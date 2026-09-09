package com.zerostress.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsBorder
import com.zerostress.manager.ui.theme.ZsCard
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

class DailyInputActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                DailyInputScreen()
            }
        }
    }
}

private const val KILL_POINTS = 10
private const val WIN_POINTS = 25
private const val ASSIST_POINTS = 5
private const val DAMAGE_PER_POINT = 100L
private const val SURVIVAL_POINTS_PER_SEC = 1

@Composable
private fun DailyInputScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    var players by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var selectedPlayer by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var showPlayerPicker by remember { mutableStateOf(false) }
    var showTypePicker by remember { mutableStateOf(false) }
    var matchType by remember { mutableStateOf(0) }
    var kills by remember { mutableStateOf("") }
    var assists by remember { mutableStateOf("") }
    var damage by remember { mutableStateOf("") }
    var wins by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var recent by remember { mutableStateOf("No entries yet") }

    val matchTypes = listOf("Classic", "Ranked", "Tournament", "Custom")

    fun loadRecent() {
        db.collection("daily_logs").orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(10).get()
            .addOnSuccessListener { query ->
                recent = if (query.isEmpty()) {
                    "No entries yet"
                } else {
                    query.documents.joinToString("\n") { doc ->
                        "${doc.getString("playerName")}: ${doc.getLong("kills") ?: 0}K " +
                            "${doc.getLong("wins") ?: 0}W ${doc.getLong("damage") ?: 0}DMG " +
                            "${doc.getLong("score") ?: 0}pts"
                    }
                }
            }
    }

    fun calculateRank(totalScore: Long): String = when {
        totalScore >= 50000 -> "Predator"
        totalScore >= 30000 -> "Master"
        totalScore >= 20000 -> "Diamond"
        totalScore >= 10000 -> "Platinum"
        totalScore >= 5000 -> "Gold"
        totalScore >= 2000 -> "Silver"
        else -> "Iron"
    }

    fun submit() {
        val player = selectedPlayer ?: run {
            Toast.makeText(context, "Please select a player", Toast.LENGTH_SHORT).show()
            return
        }
        val k = kills.trim().toIntOrNull()
        val dmg = damage.trim().toLongOrNull()
        if (k == null) { Toast.makeText(context, "Kills required", Toast.LENGTH_SHORT).show(); return }
        if (dmg == null) { Toast.makeText(context, "Damage required", Toast.LENGTH_SHORT).show(); return }

        val w = wins.trim().toIntOrNull() ?: 0
        val a = assists.trim().toIntOrNull() ?: 0
        val mins = minutes.trim().toIntOrNull() ?: 0
        val secs = seconds.trim().toIntOrNull() ?: 0
        val survival = mins * 60 + secs

        val entryScore = k * KILL_POINTS + w * WIN_POINTS + a * ASSIST_POINTS +
            (dmg / DAMAGE_PER_POINT).toInt() + survival * SURVIVAL_POINTS_PER_SEC

        val playerName = player.getString("name") ?: "Unknown"
        val playerId = player.id

        loading = true
        db.collection("daily_logs").add(
            mapOf(
                "playerName" to playerName,
                "playerId" to playerId,
                "kills" to k,
                "wins" to w,
                "assists" to a,
                "damage" to dmg,
                "survivalSeconds" to survival,
                "score" to entryScore,
                "matchType" to matchTypes[matchType],
                "timestamp" to System.currentTimeMillis()
            )
        ).addOnSuccessListener {
            db.collection("players").document(playerId).get()
                .addOnSuccessListener { doc ->
                    val currentScore = doc.getLong("score") ?: 0
                    val newTotalScore = currentScore + entryScore
                    val newRank = calculateRank(newTotalScore)
                    val currentXP = doc.getLong("xp") ?: 0
                    val newTotalXP = currentXP + entryScore
                    val newLevel = (newTotalXP / 500).toInt() + 1
                    val coinsEarned = k * 5 + w * 10

                    val updates = mutableMapOf<String, Any>()
                    fun addPeriod(prefix: String) {
                        updates["${prefix}Kills"] = FieldValue.increment(k.toLong())
                        updates["${prefix}Wins"] = FieldValue.increment(w.toLong())
                        updates["${prefix}Assists"] = FieldValue.increment(a.toLong())
                        updates["${prefix}Damage"] = FieldValue.increment(dmg)
                        updates["${prefix}Matches"] = FieldValue.increment(1)
                        updates["${prefix}Score"] = FieldValue.increment(entryScore.toLong())
                    }
                    // Totals
                    updates["kills"] = FieldValue.increment(k.toLong())
                    updates["wins"] = FieldValue.increment(w.toLong())
                    updates["assists"] = FieldValue.increment(a.toLong())
                    updates["damage"] = FieldValue.increment(dmg)
                    updates["survivalSeconds"] = FieldValue.increment(survival.toLong())
                    updates["matches"] = FieldValue.increment(1)
                    updates["score"] = FieldValue.increment(entryScore.toLong())
                    // Periods
                    addPeriod("daily")
                    addPeriod("weekly")
                    addPeriod("monthly")
                    // Rank / XP / level / coins
                    updates["rank"] = newRank
                    updates["xp"] = FieldValue.increment(entryScore.toLong())
                    updates["level"] = newLevel
                    updates["coins"] = FieldValue.increment(coinsEarned.toLong())

                    db.collection("players").document(playerId).update(updates)
                        .addOnSuccessListener {
                            loading = false
                            Toast.makeText(
                                context,
                                "✅ $playerName: +$entryScore pts, +$coinsEarned coins\nLevel: $newLevel | Rank: $newRank",
                                Toast.LENGTH_LONG
                            ).show()
                            kills = ""; assists = ""; damage = ""; wins = ""; minutes = ""; seconds = ""
                            selectedPlayer = null
                            loadRecent()
                        }
                        .addOnFailureListener { e ->
                            loading = false
                            Toast.makeText(context, "Error updating stats: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
        }.addOnFailureListener { e ->
            loading = false
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        db.collection("players").whereEqualTo("status", "approved").get()
            .addOnSuccessListener { query -> players = query.documents }
        loadRecent()
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Daily Input",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                ZSCard {
                    // Player dropdown
                    ZSDropdown(
                        label = "Select Player",
                        items = players,
                        display = { it.getString("name") ?: "Unknown" },
                        selected = selectedPlayer,
                        onSelect = { selectedPlayer = it }
                    )
                    Spacer(Modifier.height(14.dp))
                    Row {
                        ZSField(
                            value = kills,
                            onValueChange = { kills = it },
                            label = "Kills",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        ZSField(
                            value = assists,
                            onValueChange = { assists = it },
                            label = "Assists",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        ZSField(
                            value = damage,
                            onValueChange = { damage = it },
                            label = "Damage",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        ZSField(
                            value = wins,
                            onValueChange = { wins = it },
                            label = "Wins",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        ZSField(
                            value = minutes,
                            onValueChange = { minutes = it },
                            label = "Minutes",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        ZSField(
                            value = seconds,
                            onValueChange = { seconds = it },
                            label = "Seconds",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    ZSDropdown(
                        label = "Match Type",
                        items = matchTypes,
                        display = { it },
                        selected = matchTypes[matchType],
                        onSelect = { matchType = matchTypes.indexOf(it).coerceAtLeast(0) }
                    )
                }

                Spacer(Modifier.height(16.dp))
                ZSButton(
                    text = if (loading) "Submitting..." else "SUBMIT ENTRY",
                    onClick = { submit() },
                    container = ZsAccent,
                    enabled = !loading
                )

                Spacer(Modifier.height(20.dp))
                ZSCard {
                    Text("RECENT ENTRIES", color = ZsTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(recent, color = ZsTextMuted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Dropdown picker using the official ExposedDropdownMenuBox API. */
@Composable
private fun <T> ZSDropdown(
    label: String,
    items: List<T>,
    display: (T) -> String,
    selected: T?,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = if (selected != null) display(selected) else "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ZsPrimary,
                unfocusedBorderColor = ZsBorder,
                focusedLabelColor = ZsCyan,
                unfocusedLabelColor = ZsTextMuted,
                focusedTextColor = ZsTextPrimary,
                unfocusedTextColor = ZsTextPrimary,
                cursorColor = ZsCyan,
                focusedContainerColor = ZsCard,
                unfocusedContainerColor = ZsCard
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(display(item)) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    }
                )
            }
        }
    }
}