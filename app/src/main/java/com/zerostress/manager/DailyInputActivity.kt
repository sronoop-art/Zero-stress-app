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
import androidx.compose.foundation.text.KeyboardOptions
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

class DailyInputActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            com.zerostress.manager.ui.theme.ZeroStressTheme {
                DailyInputScreen()
            }
        }
    }
}

@Composable
private fun DailyInputScreen() {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().uid

    var kills by remember { mutableStateOf("") }
    var deaths by remember { mutableStateOf("") }
    var assists by remember { mutableStateOf("") }
    var damage by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var matchType by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var recent by remember { mutableStateOf("") }

    val matchTypes = listOf("Casual", "Ranked", "Tournament", "Scrim")

    LaunchedEffect(Unit) {
        if (uid == null) return@LaunchedEffect
        db.collection("daily_stats")
            .whereEqualTo("playerId", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { snap ->
                recent = if (snap.isEmpty) {
                    "No entries yet"
                } else {
                    snap.documents.joinToString("\n") { doc ->
                        val k = doc.getLong("kills") ?: 0
                        val d = doc.getLong("deaths") ?: 0
                        val a = doc.getLong("assists") ?: 0
                        val dm = doc.getLong("damage") ?: 0
                        val h = doc.getLong("hours") ?: 0
                        val mt = doc.getString("matchType") ?: "-"
                        "$mt — ${k}K/${d}D/${a}A · $dm dmg · ${h}h"
                    }
                }
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
        if (uid == null) {
            Toast.makeText(context, "Not signed in", Toast.LENGTH_SHORT).show()
            return
        }
        loading = true
        val entry = mapOf(
            "playerId" to uid,
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
                Toast.makeText(context, "✅ Stats logged", Toast.LENGTH_SHORT).show()
                kills = ""; deaths = ""; assists = ""; damage = ""; hours = ""
                matchType = null
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
                ZSButton(text = if (loading) "Saving…" else "Log stats", enabled = !loading, onClick = { submit() })
            }

            Spacer(Modifier.height(20.dp))
            ZSCard {
                Text("RECENT ENTRIES", color = com.zerostress.manager.ui.theme.ZsTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(recent.ifEmpty { "No entries yet" }, color = com.zerostress.manager.ui.theme.ZsTextMuted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
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
                selected ?: "Select…",
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
