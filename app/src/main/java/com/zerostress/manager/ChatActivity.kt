package com.zerostress.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.formatTime
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCard
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.R
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import java.util.concurrent.atomic.AtomicBoolean

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
    var players by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var userName by remember { mutableStateOf("Unknown") }
    var messageText by remember { mutableStateOf("") }
    var typingCount by remember { mutableStateOf(0) }
    var isAdmin by remember { mutableStateOf(false) }
    var showMentionDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }

    val typingRef = if (userId != null) db.collection("chat_typing").document(TYPING_PREFIX + userId) else null
    val typingSent = remember { AtomicBoolean(false) }

    // Live message listener
    DisposableEffect(Unit) {
        val listener = db.collection("chat_messages")
            .limit(200)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(context, "Chat load error: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                val list = snapshots?.documents.orEmpty()
                    .filter { it.getBoolean("deleted") != true }
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
            typingRef!!.set(mapOf("userId" to userId, "timestamp" to System.currentTimeMillis()))
            typingSent.set(true)
        } else if (!active && typingSent.get()) {
            typingRef!!.delete()
            typingSent.set(false)
        }
    }

    LaunchedEffect(Unit) {
        // Load user name + admin status
        if (userId != null) {
            db.collection("players").document(userId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        userName = doc.getString("name") ?: "Unknown"
                        isAdmin = doc.getString("role") == "admin"
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
                if (mentions.isNotEmpty()) {
                    db.collection("notifications").add(
                        mapOf(
                            "title" to "You were mentioned in chat!",
                            "message" to "$userName mentioned ${mentions.joinToString(", ")}: $text",
                            "type" to "mention",
                            "timestamp" to System.currentTimeMillis(),
                            "mentions" to mentions,
                            "senderId" to userId
                        )
                    )
                }
                db.collection("notifications").add(
                    mapOf(
                        "title" to "New Chat Message",
                        "message" to "$userName: $text",
                        "type" to "chat",
                        "timestamp" to System.currentTimeMillis(),
                        "sentBy" to userName,
                        "senderId" to userId
                    )
                )
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

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Team Chat",
                onBack = { (context as? android.app.Activity)?.finish() },
                right = {
                    if (isAdmin) {
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
                    val bubbleColor = if (isSent) com.zerostress.manager.ui.theme.ZsChatSentEnd else ZsCard

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            Modifier
                                .widthIn(max = 320.dp)
                                .background(
                                    color = bubbleColor,
                                    shape = RoundedCornerShape(
                                        topStart = 14.dp, topEnd = 14.dp,
                                        bottomStart = if (isSent) 14.dp else 4.dp,
                                        bottomEnd = if (isSent) 4.dp else 14.dp
                                    )
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            if (!isSent) {
                                Text(
                                    sender,
                                    color = ZsCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                            }
                            Text(
                                text = highlightMentions(text, players, isSent),
                                color = ZsTextPrimary,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                formatTime(ts),
                                color = ZsTextMuted,
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
                    Text("@", color = ZsCyan, fontSize = 22.sp, fontWeight = FontWeight.Black)
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
                    ZsPngIcon(R.drawable.ic_action_send, size = 22.dp, tint = ZsAccent)
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
                                if (role == "admin") "$name (admin)" else name,
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
    val mentionColor = if (isSent) Color(0xFF0B1220) else ZsCyan
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