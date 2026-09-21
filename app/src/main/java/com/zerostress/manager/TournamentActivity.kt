package com.zerostress.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.zerostress.manager.ui.ZSBottomNav
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSFilterChips
import com.zerostress.manager.ui.ZSStat
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.ZsPngIcon
import com.zerostress.manager.ui.zsNavItems
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsCardAlt
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsGreen
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsPurple
import com.zerostress.manager.ui.theme.ZsTextMuted
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
    var all by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var filter by remember { mutableIntStateOf(0) } // 0=Live 1=Upcoming 2=Mine
    var isAdmin by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var viewDoc by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    fun load() {
        // Small collection: fetch and sort client-side, no composite index needed.
        db.collection("tournaments").get()
            .addOnSuccessListener { q ->
                all = q.documents
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

    val mine = if (uid != null) all.filter { uid in joinedIds(it) } else emptyList()
    val visible = when (filter) {
        1 -> all.filter { (it.getLong("startsAt") ?: 0L) > nowMs }
        2 -> mine
        else -> all
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Tournaments",
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

            // v4: quiet section header + status filter chips
            Box(Modifier.padding(horizontal = 16.dp)) {
                ZSFilterChips(listOf("Live", "Upcoming", "Mine"), filter) { filter = it }
            }
            Spacer(Modifier.height(4.dp))

            if (loading) {
                LoadingBox(Modifier.weight(1f))
            } else if (visible.isEmpty()) {
                Column(Modifier.weight(1f)) {
                    EmptyState(
                        when {
                            all.isEmpty() -> "No active tournaments yet"
                            filter == 2 -> "You have not joined a tournament yet"
                            else -> "Nothing in this view right now"
                        }
                    )
                    if (isAdmin && all.isEmpty()) {
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
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { doc ->
                        TournamentCard(
                            doc = doc,
                            uid = uid,
                            nowMs = nowMs,
                            onOpen = { viewDoc = doc },
                            onJoin = {
                                if (uid != null) {
                                    db.collection("tournaments").document(doc.id)
                                        .update("joinedIds", FieldValue.arrayUnion(uid))
                                        .addOnSuccessListener {
                                            Toast.makeText(context, "Joined!", Toast.LENGTH_SHORT).show()
                                            load()
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            }
                        )
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
        var slots by remember { mutableStateOf("") }
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
                        slots, { slots = it }, "Max slots (optional)", "e.g. 64",
                        keyboardType = KeyboardType.Number
                    )
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
                    val maxSlots = slots.trim().toLongOrNull() ?: 0L
                    db.collection("tournaments").add(
                        mapOf(
                            "name" to n,
                            "prize" to prize.trim(),
                            "description" to desc.trim(),
                            "startsAt" to startsAt,
                            "maxSlots" to maxSlots,
                            "status" to "Active",
                            "joinedIds" to emptyList<String>(),
                            "standings" to emptyMap<String, Any>(),
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

    // --- Tournament details (bracket view) ---
    viewDoc?.let { doc ->
        val ids = (doc.get("joinedIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val standings = doc.get("standings") as? Map<*, *>
        val myPos = uid?.let { standings?.get(it)?.toString() }
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
                    Text("${ids.size} players joined", color = ZsTextSecondary)
                    if (myPos != null) {
                        Spacer(Modifier.height(4.dp))
                        Text("Your position: #$myPos", color = ZsPrimary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Bracket updates as admins log results.",
                        color = ZsTextMuted,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewDoc = null }) { Text("Close", color = ZsPrimary) }
            }
        )
    }
}

/**
 * v4 tournament card: status tag header, segmented HRS/MIN/SEC countdown,
 * prize/players/position stat grid and the JOIN NOW + VIEW BRACKET CTA row.
 */
@Composable
private fun TournamentCard(
    doc: DocumentSnapshot,
    uid: String?,
    nowMs: Long,
    onOpen: () -> Unit,
    onJoin: () -> Unit
) {
    val name = doc.getString("name") ?: "Tournament"
    val prize = doc.getString("prize") ?: ""
    val startsAt = doc.getLong("startsAt") ?: 0L
    val maxSlots = doc.getLong("maxSlots") ?: 0L
    val ids = (doc.get("joinedIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    val joined = uid != null && uid in ids
    val standings = doc.get("standings") as? Map<*, *>
    val myPos = uid?.let { standings?.get(it)?.toString() }
    val remain = startsAt - nowMs
    val live = remain <= 0

    ZSCard(highlight = ZsPrimary) {
        // Tag + name
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusTag(if (live) "LIVE" else "UPCOMING", if (live) ZsGreen else ZsPurple)
            Spacer(Modifier.width(6.dp))
            Text(
                if (live) "IN PROGRESS" else "REGISTRATION OPEN",
                color = ZsTextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            name,
            color = ZsTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
        if (prize.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(prize, color = ZsTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        // Segmented countdown (v4 timerbox)
        Spacer(Modifier.height(10.dp))
        if (live) {
            Text(
                "LIVE NOW",
                color = ZsGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        } else {
            val h = remain / 3_600_000
            val m = (remain % 3_600_000) / 60_000
            val s = (remain % 60_000) / 1000
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimerBox("$h", "HRS")
                Spacer(Modifier.width(5.dp))
                TimerBox("$m", "MIN")
                Spacer(Modifier.width(5.dp))
                TimerBox("$s", "SEC")
                Spacer(Modifier.width(8.dp))
                Text(
                    "UNTIL START",
                    color = ZsTextMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Prize / Players / Your pos grid
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ZSStat("Prize", prize.ifEmpty { "—" }, ZsGold, Modifier.weight(1f))
            ZSStat(
                "Players",
                "${ids.size}${if (maxSlots > 0) "/$maxSlots" else ""}",
                ZsTextPrimary, Modifier.weight(1f)
            )
            ZSStat("Your pos", myPos?.let { "#$it" } ?: "—", ZsPrimary, Modifier.weight(1f))
        }

        // CTA row (v4): primary join + glass bracket
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (joined) {
                ZSButton(
                    "Registered",
                    onOpen,
                    Modifier.weight(1f),
                    container = ZsCardAlt,
                    textColor = ZsTextPrimary,
                    height = 42.dp
                )
            } else {
                ZSButton("Join now", onJoin, Modifier.weight(1f), height = 42.dp)
            }
            ZSButton(
                "View bracket",
                onOpen,
                Modifier.weight(1f),
                container = ZsCardAlt,
                textColor = ZsTextPrimary,
                height = 42.dp
            )
        }
    }
}

/** Small colored status tag (LIVE / UPCOMING). */
@Composable
private fun StatusTag(text: String, color: Color) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp
    )
}

/** One segmented countdown cell of the v4 timerbox. */
@Composable
private fun TimerBox(value: String, label: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = ZsPrimary, fontSize = 14.sp, fontWeight = FontWeight.Black)
        Text(
            label,
            color = ZsTextMuted,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}
