package com.zerostress.manager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.EmptyState
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.formatTime
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCard
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsGreen
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import com.zerostress.manager.ui.theme.ZsWarning
import kotlinx.coroutines.delay

class VoiceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                VoiceScreen()
            }
        }
    }
}

private data class VoiceUserInfo(
    val userId: String,
    val userName: String,
    val isMuted: Boolean,
    val isDeafened: Boolean,
    val isScreenSharing: Boolean,
    val isHandRaised: Boolean,
    val isSpeaking: Boolean
)

private data class VoiceChatMsg(
    val senderName: String,
    val text: String,
    val timestamp: Long
)

@Composable
private fun VoiceScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = FirebaseAuth.getInstance().uid

    var userName by remember { mutableStateOf("Unknown") }
    var userStatus by remember { mutableStateOf("ONLINE") }
    var isInVoice by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isDeafened by remember { mutableStateOf(false) }
    var isScreenSharing by remember { mutableStateOf(false) }
    var isHandRaised by remember { mutableStateOf(false) }
    var isPushToTalk by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isAdmin by remember { mutableStateOf(false) }
    var currentChannelId by remember { mutableStateOf<String?>(null) }
    var currentChannelName by remember { mutableStateOf("Select Channel") }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var users by remember { mutableStateOf<List<VoiceUserInfo>>(emptyList()) }
    var chatMessages by remember { mutableStateOf<List<VoiceChatMsg>>(emptyList()) }
    var chatText by remember { mutableStateOf("") }
    var showChat by remember { mutableStateOf(false) }
    var showChannelPicker by remember { mutableStateOf(false) }
    var showCreateChannelDialog by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showChannelPicker = true
        else Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
    }

    // Load user role
    LaunchedEffect(Unit) {
        if (userId == null) {
            Toast.makeText(context, "Please login first", Toast.LENGTH_SHORT).show()
            (context as? android.app.Activity)?.finish()
            return@LaunchedEffect
        }
        db.collection("players").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    userName = doc.getString("name") ?: "Unknown"
                    isAdmin = doc.getString("role") == "admin"
                }
            }
    }

    // Timer while in voice
    LaunchedEffect(isInVoice) {
        if (isInVoice) {
            val start = System.currentTimeMillis()
            while (isInVoice) {
                elapsedSeconds = (System.currentTimeMillis() - start) / 1000
                delay(1000)
            }
        }
    }

    // Live participants listener when connected
    DisposableEffect(currentChannelId) {
        val channelId = currentChannelId
        val listener = if (channelId != null) {
            db.collection("voice_channels").document(channelId)
                .collection("participants")
                .addSnapshotListener { snap, e ->
                    if (e != null || snap == null) return@addSnapshotListener
                    users = snap.documents.map { doc ->
                        VoiceUserInfo(
                            userId = doc.id,
                            userName = doc.getString("userName") ?: "Unknown",
                            isMuted = doc.getBoolean("muted") == true,
                            isDeafened = doc.getBoolean("deafened") == true,
                            isScreenSharing = doc.getBoolean("screenSharing") == true,
                            isHandRaised = doc.getBoolean("handRaised") == true,
                            isSpeaking = doc.getBoolean("speaking") == true
                        )
                    }
                }
        } else null
        onDispose { listener?.remove() }
    }

    // Live chat listener when connected
    DisposableEffect(currentChannelId) {
        val channelId = currentChannelId
        val listener = if (channelId != null) {
            db.collection("voice_channels").document(channelId)
                .collection("chat")
                .orderBy("timestamp")
                .addSnapshotListener { snap, e ->
                    if (e != null || snap == null) return@addSnapshotListener
                    chatMessages = snap.documents.map { doc ->
                        VoiceChatMsg(
                            senderName = doc.getString("senderName") ?: "Unknown",
                            text = doc.getString("text") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    }
                }
        } else null
        onDispose { listener?.remove() }
    }

    fun updateField(field: String, value: Boolean) {
        val channelId = currentChannelId ?: return
        db.collection("voice_channels").document(channelId)
            .collection("participants").document(userId ?: "")
            .update(field, value)
    }

    fun joinChannel(channelId: String, channelName: String) {
        currentChannelId = channelId
        currentChannelName = channelName
        val participant = mapOf(
            "userId" to userId,
            "userName" to userName,
            "joinedAt" to System.currentTimeMillis(),
            "muted" to false,
            "deafened" to false,
            "screenSharing" to false,
            "handRaised" to false,
            "speaking" to false,
            "isAdmin" to isAdmin,
            "userStatus" to userStatus
        )
        db.collection("voice_channels").document(channelId)
            .collection("participants").document(userId ?: "")
            .set(participant)
            .addOnSuccessListener {
                isInVoice = true
                Toast.makeText(context, "Joined $channelName", Toast.LENGTH_SHORT).show()
            }
    }

    fun leaveVoice() {
        val channelId = currentChannelId
        if (channelId != null && userId != null) {
            db.collection("voice_channels").document(channelId)
                .collection("participants").document(userId).delete()
        }
        isInVoice = false
        isMuted = false
        isDeafened = false
        isScreenSharing = false
        isHandRaised = false
        currentChannelId = null
        currentChannelName = "Select Channel"
        elapsedSeconds = 0
        users = emptyList()
        chatMessages = emptyList()
    }

    fun sendChat() {
        val text = chatText.trim()
        if (text.isEmpty() || currentChannelId == null || userId == null) return
        db.collection("voice_channels").document(currentChannelId!!)
            .collection("chat").add(
                mapOf(
                    "senderId" to userId,
                    "senderName" to userName,
                    "text" to text,
                    "timestamp" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener { chatText = "" }
    }

    DisposableEffect(Unit) {
        onDispose {
            OnOnlineStatusHelper.updateOnlineStatus(false)
            leaveVoice()
        }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Voice Chat",
                onBack = { (context as? android.app.Activity)?.finish() },
                right = {
                    if (isAdmin) {
                        TextButton(onClick = { showCreateChannelDialog = true }) {
                            Text("➕", color = ZsGold, fontSize = 20.sp)
                        }
                        TextButton(onClick = { showPermissionsDialog = true }) {
                            Text("🛡️", color = ZsCyan)
                        }
                    }
                }
            )

            // Status header
            ZSCard(highlight = if (isInVoice) ZsGreen else ZsTextMuted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isInVoice) "🟢 Connected" else "🔴 Not connected",
                            color = if (isInVoice) ZsGreen else ZsTextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Channel: $currentChannelName",
                            color = ZsTextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Time: ${formatDuration(elapsedSeconds)} • ${users.size} users",
                            color = ZsTextMuted,
                            fontSize = 12.sp
                        )
                    }
                    ZSButton(
                        text = if (isInVoice) "🔴 Leave" else "🎙️ Join Voice",
                        onClick = {
                            if (isInVoice) {
                                leaveVoice()
                            } else {
                                // Check voice permission for non-admins
                                if (isAdmin || userId == null) {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    db.collection("players").document(userId).get()
                                        .addOnSuccessListener { doc ->
                                            val allowed = doc.getBoolean("voiceAllowed")
                                            if (allowed == false) {
                                                Toast.makeText(
                                                    context,
                                                    "🚫 Voice chat permission denied.\nAsk the admin to enable it.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                        .addOnFailureListener {
                                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                }
                            }
                        },
                        container = if (isInVoice) ZsDanger else ZsAccent,
                        modifier = Modifier.width(150.dp)
                    )
                }
            }

            // Controls (visible when connected)
            if (isInVoice) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ControlChip("🔇", isMuted, "Mute") { 
                        isMuted = !isMuted
                        updateField("muted", isMuted)
                    }
                    ControlChip("🔕", isDeafened, "Deafen") {
                        isDeafened = !isDeafened
                        if (isDeafened && !isMuted) {
                            isMuted = true
                            updateField("muted", true)
                        }
                        updateField("deafened", isDeafened)
                    }
                    ControlChip("📺", isScreenSharing, "Screen") {
                        isScreenSharing = !isScreenSharing
                        updateField("screenSharing", isScreenSharing)
                    }
                    ControlChip(
                        if (isPushToTalk) "🗣️" else "🎤",
                        isPushToTalk,
                        "Push to Talk",
                        holdable = true,
                        onHoldStart = {
                            isPushToTalk = true
                            isSpeaking = true
                            updateField("speaking", true)
                        },
                        onHoldEnd = {
                            isPushToTalk = false
                            isSpeaking = false
                            updateField("speaking", false)
                        }
                    )
                    ControlChip("✋", isHandRaised, "Hand") {
                        isHandRaised = !isHandRaised
                        updateField("handRaised", isHandRaised)
                    }
                }
            }

            // User list
            Column(Modifier.weight(1f).fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Participants (${users.size})",
                        Modifier.weight(1f),
                        color = ZsTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showChat = !showChat }) {
                        Text(if (showChat) "💬 Hide chat" else "💬 Chat", color = ZsCyan)
                    }
                    TextButton(onClick = { showMoreOptions = true }) {
                        Text("⋯", color = ZsTextSecondary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }

                if (users.isEmpty()) {
                    EmptyState("No one is connected yet")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(users, key = { it.userId }) { user ->
                            ZSCard(onClick = {
                                // Admin moderation on tap
                                if (isAdmin && user.userId != userId) {
                                    db.collection("voice_channels").document(currentChannelId ?: "")
                                        .collection("participants").document(user.userId).update("muted", true)
                                    Toast.makeText(context, "${user.userName} muted", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "${if (user.isSpeaking) "🗣️ " else ""}${user.userName}",
                                            color = ZsTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            buildString {
                                                if (user.isMuted) append("🔇 ")
                                                if (user.isDeafened) append("🔕 ")
                                                if (user.isScreenSharing) append("📺 ")
                                                if (user.isHandRaised) append("✋ ")
                                                if (isEmpty()) append("🟢")
                                            },
                                            color = ZsTextMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                    if (user.userId == userId) {
                                        Text("(you)", color = ZsCyan, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Channel chat
            if (showChat && isInVoice) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(ZsCard)
                        .imePadding()
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(chatMessages) { msg ->
                            Column {
                                Row {
                                    Text(
                                        msg.senderName,
                                        color = ZsCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        formatTime(msg.timestamp),
                                        color = ZsTextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(msg.text, color = ZsTextPrimary, fontSize = 14.sp)
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ZSField(
                            value = chatText,
                            onValueChange = { chatText = it },
                            label = "",
                            placeholder = "Message...",
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { sendChat() }) {
                            Text("➤", color = ZsAccent, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }

    // Channel picker
    if (showChannelPicker) {
        var channels by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
        LaunchedEffect(Unit) {
            db.collection("voice_channels").get()
                .addOnSuccessListener { query -> channels = query.documents }
        }
        AlertDialog(
            onDismissRequest = { showChannelPicker = false },
            title = { Text("🎙️ Select Voice Channel") },
            text = {
                Column {
                    if (channels.isEmpty()) {
                        Text("No voice channels yet.", color = ZsTextMuted)
                    } else {
                        channels.forEach { doc ->
                            val name = doc.getString("name") ?: doc.id
                            TextButton(
                                onClick = {
                                    showChannelPicker = false
                                    joinChannel(doc.id, name)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showChannelPicker = false }) { Text("Cancel") }
            }
        )
    }

    // Create channel (admin)
    if (showCreateChannelDialog) {
        var channelName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateChannelDialog = false },
            title = { Text("➕ Create Voice Channel") },
            text = {
                ZSField(
                    value = channelName,
                    onValueChange = { channelName = it },
                    label = "Channel name",
                    placeholder = "e.g., Squad Alpha"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = channelName.trim()
                    showCreateChannelDialog = false
                    if (name.isEmpty()) {
                        Toast.makeText(context, "Channel name required", Toast.LENGTH_SHORT).show()
                    } else {
                        db.collection("voice_channels").document(name).set(
                            mapOf(
                                "name" to name,
                                "active" to true,
                                "createdAt" to System.currentTimeMillis(),
                                "createdBy" to userId
                            )
                        ).addOnSuccessListener {
                            Toast.makeText(context, "✅ Channel \"$name\" created!", Toast.LENGTH_SHORT).show()
                        }.addOnFailureListener { e ->
                            Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Create", color = ZsAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateChannelDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Manage permissions (admin)
    if (showPermissionsDialog) {
        var players by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            db.collection("players").whereEqualTo("status", "approved").get()
                .addOnSuccessListener {
                    players = it.documents.filter { p -> p.id != userId }
                    loading = false
                }
        }
        AlertDialog(
            onDismissRequest = { showPermissionsDialog = false },
            title = { Text("👥 Manage Voice Permissions") },
            text = {
                Column {
                    Text(
                        "Tick = can join voice chat",
                        color = ZsTextMuted,
                        fontSize = 12.sp
                    )
                    if (loading) {
                        Text("Loading...", color = ZsTextMuted, modifier = Modifier.padding(8.dp))
                    } else {
                        players.forEach { player ->
                            var allowed by remember(player.id) { mutableStateOf(player.getBoolean("voiceAllowed") != false) }
                            TextButton(
                                onClick = {
                                    allowed = !allowed
                                    db.collection("players").document(player.id)
                                        .update("voiceAllowed", allowed)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "${if (allowed) "✅" else "❌"} ${player.getString("name") ?: "Unknown"}",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionsDialog = false
                    Toast.makeText(context, "✅ Voice permissions saved", Toast.LENGTH_SHORT).show()
                }) { Text("Done", color = ZsAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionsDialog = false }) { Text("Cancel") }
            }
        )
    }

    // More options
    if (showMoreOptions) {
        AlertDialog(
            onDismissRequest = { showMoreOptions = false },
            title = { Text("More Options") },
            text = {
                Column {
                    listOf("Set Status", "User Profile", "Channel Info", "Report User").forEach { option ->
                        TextButton(
                            onClick = {
                                showMoreOptions = false
                                when (option) {
                                    "Set Status" -> {
                                        val statuses = listOf("ONLINE", "IDLE", "DO_NOT_DISTURB")
                                        val emojis = listOf("🟢 Online", "🟡 Idle", "🔴 Do Not Disturb")
                                        val idx = statuses.indexOf(userStatus).coerceAtLeast(0)
                                        val next = (idx + 1) % statuses.size
                                        userStatus = statuses[next]
                                        Toast.makeText(
                                            context,
                                            "Status set to ${emojis[next]}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        val channelId = currentChannelId
                                        if (channelId != null && userId != null) {
                                            db.collection("voice_channels").document(channelId)
                                                .collection("participants").document(userId)
                                                .update("userStatus", userStatus)
                                        }
                                    }
                                    "User Profile" -> {
                                        db.collection("players").document(userId ?: "").get()
                                            .addOnSuccessListener { doc ->
                                                if (doc.exists()) {
                                                    Toast.makeText(
                                                        context,
                                                        "Name: ${doc.getString("name")}\nLevel: ${doc.getLong("level")}\nRank: ${doc.getString("rank")}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                    }
                                    "Channel Info" -> {
                                        Toast.makeText(
                                            context,
                                            "Channel: $currentChannelName\nUsers: ${users.size}\nDuration: ${formatDuration(elapsedSeconds)}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    "Report User" -> {
                                        Toast.makeText(context, "User reported", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(option, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoreOptions = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ControlChip(
    emoji: String,
    active: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    holdable: Boolean = false,
    onHoldStart: () -> Unit = {},
    onHoldEnd: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val bg = when {
        active -> ZsAccent
        label == "Push to Talk" -> ZsCard
        else -> ZsCard
    }
    val contentColor = if (active) Color(0xFF06251D) else ZsTextPrimary

    Column(
        modifier = modifier
            .background(bg, RoundedCornerShape(12.dp))
            .then(
                if (holdable) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onHoldStart()
                                tryAwaitRelease()
                                onHoldEnd()
                            }
                        )
                    }
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 18.sp)
        Text(label, color = contentColor, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}