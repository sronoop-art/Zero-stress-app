package com.zerostress.manager

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.fcm.ZSFCMService
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.formatTime
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsCard
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.R
import com.zerostress.manager.ui.ZsPngIcon
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsPrimaryDark
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val fcmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                ChatScreen()
            }
        }
    }
}

private const val TYPING_PREFIX = "__typing_"

@Composable
private fun ChatScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = FirebaseAuth.getInstance().uid

    var messages by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var chatLimit by remember { mutableIntStateOf(50) } // grows as the user scrolls up
    var players by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var userName by remember { mutableStateOf("Unknown") }
    var messageText by remember { mutableStateOf("") }
    var typingCount by remember { mutableStateOf(0) }
    var isAdmin by remember { mutableStateOf(false) }
    var isModerator by remember { mutableStateOf(false) }
    var showMentionDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var blockedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var msgAction by remember { mutableStateOf<DocumentSnapshot?>(null) }

    val typingRef = if (userId != null) db.collection("chat_typing").document(TYPING_PREFIX + userId) else null
    val typingSent = remember { AtomicBoolean(false) }

    // Live message listener - newest `chatLimit` messages, re-attached when
    // the limit grows so older history loads on demand (pagination).
    DisposableEffect(chatLimit) {
        val listener = db.collection("chat_messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(chatLimit.toLong())
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(context, "Chat load error: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                val list = snapshots?.documents.orEmpty()
                    .filter { it.getBoolean("deleted") != true }
                    // Hide messages from players this user has blocked
                    .filter { (it.getString("senderId")) !in blockedIds }
                    .sortedBy { it.getLong("timestamp") ?: 0L }
                messages = list
            }
        onDispose { listener.remove() }
    }

    // Typing indicator listener
    DisposableEffect(Unit) {
        val listener = db.collection("chat_typing").addSnapshotListener { snap, e ->
            if (e != null || snap == null) return@addSnapshotListener
            var count = 0
            for (doc in snap.documents) {
                val uid = doc.id.removePrefix(TYPING_PREFIX)
                if (uid != userId && (doc.getLong("timestamp") ?: 0L) > System.currentTimeMillis() - 5000) {
                    count++
                }
            }
            typingCount = count
        }
        onDispose {
            listener.remove()
            typingRef?.delete()
        }
    }

    fun setTyping(active: Boolean) {
        if (userId == null || typingRef == null) return
        if (active && !typingSent.get()) {
            typingRef.set(mapOf("userId" to userId, "timestamp" to System.currentTimeMillis()))
            typingSent.set(true)
        } else if (!active && typingSent.get()) {
            typingRef.delete()
            typingSent.set(false)
        }
    }

    LaunchedEffect(Unit) {
        // Load this user's blocked list (kept in a personal subcollection)
        if (userId != null) {
            db.collection("players").document(userId).collection("blocked_users").get()
                .addOnSuccessListener { q ->
                    blockedIds = q.documents.map { it.id }.toSet()
                }
        }
        // Load user name + admin status
        if (userId != null) {
            db.collection("players").document(userId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        userName = doc.getString("name") ?: "Unknown"
                        isAdmin = doc.getString("role") == "admin"
                        isModerator = doc.getString("role") == "moderator"
                    }
                }
        }
        // Load players for mentions
        db.collection("players").whereEqualTo("status", "approved").get()
            .addOnSuccessListener { query -> players = query.documents }
            .addOnFailureListener {
                db.collection("players").get()
                    .addOnSuccessListener { query -> players = query.documents }
            }
    }

    fun sendMessage() {
        val text = messageText.trim()
        if (text.isEmpty() || userId == null) return

        val mentions = players
            .mapNotNull { it.getString("name") }
            .filter { text.contains("@$it") }

        val msg = mutableMapOf<String, Any>(
            "text" to text,
            "senderId" to userId,
            "senderName" to userName,
            "timestamp" to System.currentTimeMillis(),
            "deleted" to false
        )
        if (mentions.isNotEmpty()) msg["mentions"] = mentions

                db.collection("chat_messages").add(msg)
            .addOnSuccessListener {
                messageText = ""
                setTyping(false)
                // ---------- Targeted push notifications (no more spam) ----------
                // Only the players actually involved get pushed: mentioned players
                // and anyone whose recent message the sender is replying to.
                // Docs with a "uid" push to that one device; admin docs written
                // with uid:null broadcast to everyone - the senders
                // (functions/index.js and the free cron relay) honor both.
                val now = System.currentTimeMillis()

                // Ensure a token exists for the current user before we expect other
                // people's pushes to be instant. Without this, the first chat of a
                // session is frequently lost because the recipient has no token yet.
                fcmScope.launch {
                    ZSFCMService.saveTokenToFirestoreRetry(context)
                }

                val notifs = db.collection("notifications")

                // 1) Mentioned players
                val mentionedUids = players.filter { it.getString("name") in mentions }.map { it.id }
                mentionedUids.filter { it != userId }.forEach { target ->
                    notifs.add(
                        mapOf(
                            "uid" to target,
                            "title" to "You were mentioned in chat",
                            "message" to "$userName: $text",
                            "type" to "mention",
                            "timestamp" to now,
                            "senderId" to userId
                        )
                    )
                }

                // 2) Reply-to-me: notify each author whose message appears in the
                //    last 10, and is being directly replied to (message references
                //    their text) - plus, as a light heuristic, the previous speaker.
                val last = messages.takeLast(10)
                val replyTargets = last
                    .filter { it.getString("senderId") != userId }
                    .filter { prev ->
                        val prevText = prev.getString("text") ?: ""
                        prevText.isNotEmpty() && text.contains(prevText.take(24), ignoreCase = true)
                    }
                    .mapNotNull { it.getString("senderId") }
                    .distinct()
                replyTargets.filter { it != userId && it !in mentionedUids }.forEach { target ->
                    notifs.add(
                        mapOf(
                            "uid" to target,
                            "title" to "$userName replied to you",
                            "message" to text,
                            "type" to "chat",
                            "timestamp" to now,
                            "senderId" to userId
                        )
                    )
                }

                // 3) Team-wide: every other player on the roster is notified of
                // any chat message, so Team Chat behaves like a real team
                // channel rather than a mention-only inbox. Anyone already
                // notified above (mentioned / replied to) is skipped so one
                // message never produces two notifications on the same device,
                // and the sender never notifies themselves.
                val alreadyNotified = mentionedUids + replyTargets
                players.map { it.id }
                    .filter { it != userId && it !in alreadyNotified }
                    .forEach { target ->
                        notifs.add(
                            mapOf(
                                "uid" to target,
                                "title" to "$userName in Team Chat",
                                "message" to text,
                                "type" to "chat",
                                "timestamp" to now,
                                "senderId" to userId
                            )
                        )
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Send failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun clearAllMessages() {
        db.collection("chat_messages").get()
            .addOnSuccessListener { query ->
                if (query.isEmpty()) {
                    Toast.makeText(context, "Chat is already empty", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val batch = db.batch()
                var count = 0
                for (doc in query.documents) {
                    batch.delete(doc.reference)
                    count++
                }
                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Chat cleared! $count messages deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Pagination: near the top of a full page, load older messages.
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                if (index < 5 && messages.size >= chatLimit && chatLimit < 300) {
                    chatLimit = minOf(chatLimit + 50, 300)
                }
            }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Team Chat",
                onBack = { (context as? android.app.Activity)?.finish() },
                right = {
                    if (isAdmin || isModerator) {
                        TextButton(onClick = { showClearChatDialog = true }) {
                            ZsPngIcon(R.drawable.ic_action_delete, size = 20.dp, tint = ZsDanger)
                        }
                    }
                }
            )

            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { doc ->
                    val isSent = doc.getString("senderId") == userId
                    val sender = doc.getString("senderName") ?: "Unknown"
                    val text = doc.getString("text") ?: ""
                    val ts = doc.getLong("timestamp") ?: 0L

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            Modifier
                                .widthIn(max = 320.dp)
                                // Long-press any message (not your own) to report or block
                                .pointerInput(doc.id, isSent) {
                                    if (!isSent) {
                                        detectTapGestures(onLongPress = { msgAction = doc })
                                    }
                                }
                                .then(
                                    if (isSent) {
                                        // Sent: racing-red gradient pill
                                        Modifier.background(
                                            brush = Brush.horizontalGradient(listOf(ZsPrimaryDark, ZsPrimary)),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                    } else {
                                        // Received: dark steel pill
                                        Modifier.background(
                                            color = ZsCard,
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                    }
                                )
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            if (!isSent) {
                                Text(
                                    sender,
                                    color = ZsPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic
                                )
                                Spacer(Modifier.height(2.dp))
                            }
                            Text(
                                text = highlightMentions(text, players, isSent),
                                color = if (isSent) Color.White else ZsTextPrimary,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                formatTime(ts),
                                color = if (isSent) Color.White.copy(alpha = 0.7f) else ZsTextMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }

            // Typing indicator
            if (typingCount > 0) {
                Text(
                    "${typingCount} person${if (typingCount > 1) "s" else ""} typing",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = ZsTextMuted,
                    fontSize = 12.sp
                )
            }

            // Input bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (players.isEmpty()) {
                        Toast.makeText(context, "No players found. Try again in a moment.", Toast.LENGTH_SHORT).show()
                    } else {
                        showMentionDialog = true
                    }
                }) {
                    Text("@", color = ZsPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                ZSField(
                    value = messageText,
                    onValueChange = {
                        messageText = it
                        setTyping(it.isNotEmpty())
                    },
                    label = "",
                    placeholder = "Type a message...",
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { sendMessage() }) {
                    ZsPngIcon(R.drawable.ic_action_send, size = 22.dp, tint = ZsPrimary)
                }
            }
        }
    }

    // Mention dialog
    if (showMentionDialog) {
        AlertDialog(
            onDismissRequest = { showMentionDialog = false },
            title = { Text("Mention a Player") },
            text = {
                Column {
                    players.forEach { player ->
                        val name = player.getString("name") ?: "Unknown"
                        val role = player.getString("role")
                        TextButton(
                            onClick = {
                                showMentionDialog = false
                                messageText += "@$name "
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (role == "admin") "$name (admin)"
                                else if (role == "moderator") "$name (mod)"
                                else name,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMentionDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Report / Block dialog (long-press a received message)
    msgAction?.let { target ->
        val senderId = target.getString("senderId") ?: ""
        val senderName = target.getString("senderName") ?: "Unknown"
        val isBlocked = senderId in blockedIds
        AlertDialog(
            onDismissRequest = { msgAction = null },
            title = { Text("$senderName's message") },
            text = {
                Text(
                    target.getString("text") ?: "",
                    color = ZsTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    msgAction = null
                    db.collection("chat_reports").add(
                        mapOf(
                            "reporterId" to userId,
                            "reporterName" to userName,
                            "messageId" to target.id,
                            "senderId" to senderId,
                            "senderName" to senderName,
                            "text" to (target.getString("text") ?: ""),
                            "timestamp" to System.currentTimeMillis(),
                            "status" to "open"
                        )
                    ).addOnSuccessListener {
                        Toast.makeText(
                            context,
                            "Reported — moderators will review it",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.addOnFailureListener { e ->
                        Toast.makeText(context, "Report failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Report", color = ZsPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                if (isBlocked) {
                    TextButton(onClick = {
                        msgAction = null
                        userId?.let { uid ->
                            db.collection("players").document(uid)
                                .collection("blocked_users").document(senderId).delete()
                                .addOnSuccessListener {
                                    blockedIds = blockedIds - senderId
                                    Toast.makeText(context, "$senderName unblocked", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }) { Text("Unblock", color = ZsTextMuted) }
                } else {
                    TextButton(onClick = {
                        msgAction = null
                        userId?.let { uid ->
                            db.collection("players").document(uid)
                                .collection("blocked_users").document(senderId)
                                .set(mapOf("name" to senderName, "blockedAt" to System.currentTimeMillis()))
                                .addOnSuccessListener {
                                    blockedIds = blockedIds + senderId
                                    Toast.makeText(
                                        context,
                                        "$senderName blocked — their messages are hidden",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                    }) { Text("Block $senderName", color = ZsDanger, fontWeight = FontWeight.Bold) }
                }
            }
        )
    }

    // Clear chat confirmation
    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text("Clear All Chat Messages") },
            text = {
                Text(
                    "This will permanently delete ALL chat messages.\n\nThis action cannot be undone!",
                    color = ZsTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearChatDialog = false
                    clearAllMessages()
                }) { Text("Clear All", color = ZsDanger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun highlightMentions(
    text: String,
    players: List<DocumentSnapshot>,
    isSent: Boolean
): androidx.compose.ui.text.AnnotatedString {
    val mentionColor = if (isSent) Color.White else ZsPrimary
    return buildAnnotatedString {
        var remaining = text
        var offset = 0
        val mentionNames = players.mapNotNull { it.getString("name") }
        // Find earliest mention in the remaining text
        while (remaining.isNotEmpty()) {
            val next = mentionNames
                .mapNotNull { name ->
                    val idx = remaining.indexOf("@$name")
                    if (idx >= 0) name to idx else null
                }
                .minByOrNull { it.second }

            if (next == null) {
                append(remaining)
                break
            }
            val (name, idx) = next
            append(remaining.substring(0, idx))
            withStyle(SpanStyle(color = mentionColor, fontWeight = FontWeight.Bold)) {
                append("@$name")
            }
            offset += idx + name.length + 1
            remaining = remaining.substring(idx + name.length + 1)
        }
    }
}