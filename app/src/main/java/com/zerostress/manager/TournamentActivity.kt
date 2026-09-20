package com.zerostress.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.R
import com.zerostress.manager.ui.EmptyState
import com.zerostress.manager.ui.LoadingBox
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSBadge
import com.zerostress.manager.ui.ZSBottomNav
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSHeroHeader
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.ZsPngIcon
import com.zerostress.manager.ui.zsNavItems
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsGreen
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

class TournamentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                TournamentScreen()
            }
        }
    }
}

@Composable
private fun TournamentScreen() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = auth.uid

    var loading by remember { mutableStateOf(true) }
    var tournaments by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var isAdmin by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var viewDoc by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    fun load() {
        // Small collection: fetch and sort client-side, no composite index needed.
        db.collection("tournaments").get()
            .addOnSuccessListener { q ->
                tournaments = q.documents
                    .filter { (it.getString("status") ?: "Active") == "Active" }
                    .sortedBy { it.getLong("startsAt") ?: 0L }
                loading = false
            }
            .addOnFailureListener { loading = false }
    }

    LaunchedEffect(Unit) {
        load()
        if (uid != null) {
            db.collection("players").document(uid).get()
                .addOnSuccessListener { d -> isAdmin = d.getString("role") == "admin" }
        }
    }

    // 1-second tick drives the countdowns.
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }

    fun joinedIds(doc: DocumentSnapshot): List<String> =
        (doc.get("joinedIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Tournaments",
                onBack = { (context as? android.app.Activity)?.finish() },
                right = {
                    if (isAdmin) {
                        TextButton(onClick = { showCreate = true }) {
                            ZsPngIcon(
                                R.drawable.ic_add, size = 18.dp, tint = ZsPrimary,
                                contentDescription = "Create tournament"
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("New", color = ZsPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
            ZSHeroHeader(title = "COMPETITIONS", subtitle = "Join cups · climb · win rewards")

            if (loading) {
                LoadingBox(Modifier.weight(1f))
            } else if (tournaments.isEmpty()) {
                Column(Modifier.weight(1f)) {
                    EmptyState("No active tournaments yet")
                    if (isAdmin) {
                        ZSButton(
                            "Create the first tournament",
                            { showCreate = true },
                            Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tournaments, key = { it.id }) { doc ->
                        val name = doc.getString("name") ?: "Tournament"
                        val prize = doc.getString("prize") ?: ""
                        val startsAt = doc.getLong("startsAt") ?: 0L
                        val ids = joinedIds(doc)
                        val joined = uid != null && uid in ids
                        val standings = doc.get("standings") as? Map<*, *>
                        val myPos = uid?.let { standings?.get(it)?.toString() }
                        val remain = startsAt - nowMs
                        val countdown = when {
                            remain <= 0 -> "LIVE NOW"
                            remain < 3_600_000 -> "starts in ${remain / 60_000}m"
                            remain < 86_400_000 ->
                                "starts in ${remain / 3_600_000}h ${(remain % 3_600_000) / 60_000}m"
                            else ->
                                "starts in ${remain / 86_400_000}d ${(remain % 86_400_000) / 3_600_000}h"
                        }

                        ZSCard(highlight = ZsPrimary, onClick = { viewDoc = doc }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ZsPngIcon(
                                    R.drawable.ic_nav_tournament, size = 28.dp,
                                    tint = ZsGold, modifier = Modifier.padding(end = 10.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        name,
                                        color = ZsTextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    if (prize.isNotEmpty()) {
                                        Text(
                                            "Prize: $prize",
                                            color = ZsGold,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(2.dp))
                                    }
                                    Text(
                                        "${ids.size} players joined" +
                                            (myPos?.let { " • Your position: #$it" } ?: ""),
                                        color = ZsTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        countdown,
                                        color = if (remain <= 0) ZsGreen else ZsPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    if (joined) {
                                        ZSBadge("JOINED", ZsGreen)
                                    } else if (uid != null) {
                                        ZSButton(
                                            "Join",
                                            {
                                                db.collection("tournaments").document(doc.id)
                                                    .update("joinedIds", FieldValue.arrayUnion(uid))
                                                    .addOnSuccessListener {
                                                        Toast.makeText(context, "Joined $name!", Toast.LENGTH_SHORT).show()
                                                        load()
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                            },
                                            Modifier.width(88.dp),
                                            height = 36.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Neon Glass bottom navigation shell
            ZSBottomNav(zsNavItems(3, context))
        }
    }

    // --- Create tournament (admin only) ---
    if (showCreate) {
        var name by remember { mutableStateOf("") }
        var prize by remember { mutableStateOf("") }
        var starts by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create Tournament") },
            text = {
                Column {
                    ZSField(name, { name = it }, "Tournament name", "e.g. Midnight Cup #1")
                    Spacer(Modifier.height(10.dp))
                    ZSField(prize, { prize = it }, "Prize", "e.g. 5000 coins + Champion title")
                    Spacer(Modifier.height(10.dp))
                    ZSField(starts, { starts = it }, "Start (YYYY-MM-DD HH:MM)", "2026-09-25 20:00")
                    Spacer(Modifier.height(10.dp))
                    ZSField(
                        desc, { desc = it }, "Rules / description",
                        "Squad format, best of 3", minLines = 2, singleLine = false
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isEmpty()) {
                        Toast.makeText(context, "Name required", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    var startsAt = System.currentTimeMillis() + 86_400_000L
                    try {
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                            .parse(starts.trim())?.let { startsAt = it.time }
                    } catch (_: Exception) {
                    }
                    db.collection("tournaments").add(
                        mapOf(
                            "name" to n,
                            "prize" to prize.trim(),
                            "description" to desc.trim(),
                            "startsAt" to startsAt,
                            "status" to "Active",
                            "joinedIds" to emptyList<String>(),
                            "createdBy" to auth.uid,
                            "createdAt" to System.currentTimeMillis()
                        )
                    ).addOnSuccessListener {
                        Toast.makeText(context, "Tournament created!", Toast.LENGTH_SHORT).show()
                        showCreate = false
                        load()
                    }.addOnFailureListener { e ->
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Create", color = ZsPrimary) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }

    // --- Tournament details ---
    viewDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { viewDoc = null },
            title = { Text(doc.getString("name") ?: "Tournament") },
            text = {
                Column {
                    Text(
                        doc.getString("description") ?: "No description.",
                        color = ZsTextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Prize: ${doc.getString("prize")?.ifEmpty { "—" } ?: "—"}",
                        color = ZsGold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("${joinedIds(doc).size} players joined", color = ZsTextSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewDoc = null }) { Text("Close", color = ZsPrimary) }
            }
        )
    }
}
