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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSTopBar
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
import com.zerostress.manager.voice.AgoraVoiceManager
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Model
// ---------------------------------------------------------------------------

private data class VoiceParticipant(
    val userId: String,
    val userName: String,
    val isMuted: Boolean,
    val isDeafened: Boolean,
    val isHandRaised: Boolean,
    val status: String,
    val isAdmin: Boolean
)

private data class VoiceChatLine(
    val id: String,
    val senderName: String,
    val text: String,
    val timestamp: Long
)

private data class VoiceChannelRow(
    val id: String,
    val name: String,
    val active: Boolean
)

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

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
private fun VoiceScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val userId = auth.uid

    var userName by remember { mutableStateOf("Unknown") }
    var isAdmin by remember { mutableStateOf(false) }

    var channels by remember { mutableStateOf<List<VoiceChannelRow>>(emptyList()) }
    var currentChannelId by remember { mutableStateOf<String?>(null) }
    var currentChannelName by remember { mutableStateOf("No channel") }
    var isInCall by remember { mutableStateOf(false) }
    var callError by remember { mutableStateOf<String?>(null) }
    var elapsedSeconds by remember { mutableStateOf(0L) }

    var participants by remember { mutableStateOf<List<VoiceParticipant>>(emptyList()) }
    var speakingUids by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var mutedUids by remember { mutableStateOf<Set<Int>>(emptySet()) }

    var callChat by remember { mutableStateOf<List<VoiceChatLine>>(emptyList()) }
    var chatText by remember { mutableStateOf("") }
    var showChat by remember { mutableStateOf(false) }

    var isMuted by remember { mutableStateOf(false) }
    var isDeafened by remember { mutableStateOf(false) }
    var isHandRaised by remember { mutableStateOf(false) }

    // Admin dialogs
    var showCreateDialog by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showManageChannelsDialog by remember { mutableStateOf(false) }
    var adminTarget by remember { mutableStateOf<VoiceParticipant?>(null) }

    // Agora listener bridged into Compose state — registered once for the screen's life.
    val agoraListener = remember {
        object : AgoraVoiceManager.Listener {
            override fun onJoinedChannel(channel: String) {
                isInCall = true
                callError = null
            }

            override fun onUserJoined(uid: Int) {
                mutedUids = mutedUids - uid
            }

            override fun onUserOffline(uid: Int) {
                speakingUids = speakingUids - uid
                mutedUids = mutedUids - uid
            }

            override fun onUserMuted(uid: Int, muted: Boolean) {
                mutedUids = if (muted) mutedUids + uid else mutedUids - uid
            }

            override fun onUserSpeaking(uid: Int, volume: Int) {
                // uid 0 == local user in the volume callback
                speakingUids = if (volume > 5) {
                    speakingUids + uid
                } else {
                    speakingUids - uid
                }
            }

            override fun onError(code: Int) {
                callError = "Agora error $code"
            }

            override fun onLeftChannel() {
                isInCall = false
                speakingUids = emptySet()
                mutedUids = emptySet()
            }
        }
    }

    DisposableEffect(Unit) {
        AgoraVoiceManager.setListener(agoraListener)
        onDispose {
            AgoraVoiceManager.setListener(null)
            AgoraVoiceManager.leave()
        }
    }

    // Mic permission
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        if (!granted) {
            Toast.makeText(context, "Microphone permission required for voice", Toast.LENGTH_LONG).show()
        }
    }

    // Profile
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

    // Live channel list (players see only active ones)
    LaunchedEffect(isAdmin) {
        val query = db.collection("voice_channels")
        query.addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            channels = snap.documents.mapNotNull { doc ->
                val active = doc.getBoolean("active") ?: true
                if (!isAdmin && !active) return@mapNotNull null
                VoiceChannelRow(
                    id = doc.id,
                    name = doc.getString("name") ?: doc.id,
                    active = active
                )
            }
        }
    }

    // Call timer
    LaunchedEffect(isInCall) {
        if (isInCall) {
            val start = System.currentTimeMillis()
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - start) / 1000
                delay(1000)
            }
        } else {
            elapsedSeconds = 0
        }
    }

    // Participants + chat for the current channel
    DisposableEffect(currentChannelId) {
        val channelId = currentChannelId
        if (channelId == null) {
            participants = emptyList()
            callChat = emptyList()
            return@DisposableEffect onDispose { }
        }
        val participantListener = db.collection("voice_channels")
            .document(channelId)
            .collection("participants")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                participants = snap.documents.map { doc ->
                    VoiceParticipant(
                        userId = doc.id,
                        userName = doc.getString("userName") ?: "Unknown",
                        isMuted = doc.getBoolean("muted") == true,
                        isDeafened = doc.getBoolean("deafened") == true,
                        isHandRaised = doc.getBoolean("handRaised") == true,
                        status = doc.getString("userStatus") ?: "ONLINE",
                        isAdmin = doc.getBoolean("isAdmin") == true
                    )
                }
            }
        val chatListener = db.collection("voice_channels")
            .document(channelId)
            .collection("call_chat")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                callChat = snap.documents.map { doc ->
                    VoiceChatLine(
                        id = doc.id,
                        senderName = doc.getString("senderName") ?: "Unknown",
                        text = doc.getString("text") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                }
            }
        onDispose {
            participantListener.remove()
            chatListener.remove()
        }
    }

    // Admin enforcement: watch MY participant doc for mute/kick set by an admin.
    DisposableEffect(currentChannelId, userId) {
        val channelId = currentChannelId
        val uid = userId
        if (channelId == null || uid == null) {
            return@DisposableEffect onDispose { }
        }
        val enforcement = db.collection("voice_channels")
            .document(channelId)
            .collection("participants")
            .document(uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) {
                    // Kicked by an admin (doc removed) — leave locally too.
                    if (isInCall) {
                        AgoraVoiceManager.leave()
                        isInCall = false
                        currentChannelId = null
                        currentChannelName = "No channel"
                        Toast.makeText(context, "You were removed from the call", Toast.LENGTH_LONG).show()
                    }
                    return@addSnapshotListener
                }
                val forceMuted = snap.getBoolean("muted") == true
                if (forceMuted && !AgoraVoiceManager.isMuted()) {
                    AgoraVoiceManager.setMuted(true)
                    isMuted = true
                }
            }
        onDispose { enforcement.remove() }
    }

    // --- Actions ------------------------------------------------------------

    fun doJoin(channel: VoiceChannelRow) {
        val uid = userId ?: return
        currentChannelId = channel.id
        currentChannelName = channel.name
        db.collection("voice_channels")
            .document(channel.id)
            .collection("participants")
            .document(uid)
            .set(
                mapOf(
                    "userId" to uid,
                    "userName" to userName,
                    "joinedAt" to System.currentTimeMillis(),
                    "muted" to false,
                    "deafened" to false,
                    "handRaised" to false,
                    "isAdmin" to isAdmin,
                    "userStatus" to "ONLINE"
                )
            )
            .addOnSuccessListener {
                // Agora channel name: prefix avoids collisions with other apps' projects.
                val agoraChannel = "zs_${channel.id}"
                val agoraUid = AgoraVoiceManager.uidFor(uid)
                val ok = AgoraVoiceManager.join(context, agoraChannel, agoraUid, token = null)
                if (ok) {
                    isInCall = true
                    isMuted = false
                    isDeafened = false
                    isHandRaised = false
                    Toast.makeText(context, "Joined ${channel.name}", Toast.LENGTH_SHORT).show()
                } else {
                    callError = "Could not reach voice servers. Is AGORA_APP_ID set in gradle.properties?"
                    Toast.makeText(context, callError!!, Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to join: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun joinChannel(channel: VoiceChannelRow) {
        val uid = userId ?: return
        if (!micGranted) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!isAdmin) {
            // Voice-access check — admins always allowed.
            db.collection("players").document(uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.getBoolean("voiceAllowed") == false) {
                        Toast.makeText(
                            context,
                            "🚫 Voice access denied. Ask an admin to enable it.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        doJoin(channel)
                    }
                }
                .addOnFailureListener { doJoin(channel) } // fail open on lookup error
            return
        }
        doJoin(channel)
    }

    fun leaveCall() {
        val uid = userId
        val channelId = currentChannelId
        if (uid != null && channelId != null) {
            db.collection("voice_channels")
                .document(channelId)
                .collection("participants")
                .document(uid)
                .delete()
        }
        AgoraVoiceManager.leave()
        currentChannelId = null
        currentChannelName = "No channel"
        isInCall = false
        isMuted = false
        isDeafened = false
        isHandRaised = false
        speakingUids = emptySet()
        mutedUids = emptySet()
        callChat = emptyList()
    }

    fun sendCallChat() {
        val text = chatText.trim()
        val channelId = currentChannelId
        val uid = userId
        if (text.isEmpty() || channelId == null || uid == null) return
        db.collection("voice_channels")
            .document(channelId)
            .collection("call_chat")
            .add(
                mapOf(
                    "senderId" to uid,
                    "senderName" to userName,
                    "text" to text,
                    "timestamp" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener { chatText = "" }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun cycleStatus() {
        val channelId = currentChannelId
        val uid = userId
        if (channelId == null || uid == null) return
        val current = participants.firstOrNull { it.userId == uid }?.status ?: "ONLINE"
        val order = listOf("ONLINE", "IDLE", "DO_NOT_DISTURB")
        val next = order[(order.indexOf(current).coerceAtLeast(0) + 1) % order.size]
        db.collection("voice_channels")
            .document(channelId)
            .collection("participants")
            .document(uid)
            .update("userStatus", next)
    }

    DisposableEffect(Unit) {
        onDispose { if (isInCall) leaveCall() }
    }

    // --- UI ------------------------------------------------------------------

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Voice Channels",
                onBack = { (context as? android.app.Activity)?.finish() },
                right = {
                    if (isAdmin) {
                        TextButton(onClick = { showCreateDialog = true }) {
                            Text("➕", color = ZsGold, fontSize = 20.sp)
                        }
                        TextButton(onClick = { showPermissionsDialog = true }) {
                            Text("👥", color = ZsCyan, fontSize = 18.sp)
                        }
                        TextButton(onClick = { showManageChannelsDialog = true }) {
                            Text("⚙️", color = ZsCyan, fontSize = 18.sp)
                        }
                    }
                }
            )

            // Channel list
            if (!isInCall) {
                Text(
                    "TEXT CHANNELS",
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = ZsTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (channels.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 30.dp),
                        Alignment.Center
                    ) {
                        Text(
                            if (isAdmin) "No voice channels yet — tap ➕ to create one"
                            else "No voice channels yet — ask an admin to create one",
                            color = ZsTextMuted,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 16.dp, bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(channels, key = { it.id }) { ch ->
                            ZSCard(onClick = { joinChannel(ch) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🔊", fontSize = 20.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            ch.name,
                                            color = ZsTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            if (ch.active) "Tap to join" else "Disabled",
                                            color = ZsTextMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text("▶", color = ZsAccent, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
                if (callError != null) {
                    Text(
                        callError!!,
                        Modifier.padding(horizontal = 16.dp),
                        color = ZsDanger,
                        fontSize = 13.sp
                    )
                }
            }

            // In-call view
            if (isInCall) {
                ZSCard(highlight = ZsGreen, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "🟢 ${currentChannelName}",
                                color = ZsGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "Live · ${formatDuration(elapsedSeconds)} · ${participants.size} in call",
                                color = ZsTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        ZSButton(
                            text = "Leave",
                            onClick = { leaveCall() },
                            container = ZsDanger,
                            modifier = Modifier.width(110.dp),
                            height = 42.dp
                        )
                    }
                }

                // Quick controls
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    VoiceChip("🔇", "Mute", isMuted) {
                        isMuted = !isMuted
                        AgoraVoiceManager.setMuted(isMuted)
                        updateSelf(currentChannelId, userId, db) { it["muted"] = isMuted }
                    }
                    VoiceChip("🔕", "Deafen", isDeafened) {
                        isDeafened = !isDeafened
                        // Deafen = mute remote playback AND your own mic.
                        AgoraVoiceManager.setMuted(isDeafened)
                        updateSelf(currentChannelId, userId, db) { it["deafened"] = isDeafened }
                    }
                    VoiceChip("✋", "Hand", isHandRaised) {
                        isHandRaised = !isHandRaised
                        updateSelf(currentChannelId, userId, db) { it["handRaised"] = isHandRaised }
                    }
                    VoiceChip("💬", "Chat", showChat) { showChat = !showChat }
                }

                // Participants
                Text(
                    "IN THIS CALL — ${participants.size}",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = ZsTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(participants, key = { it.userId }) { p ->
                        val agoraUid = AgoraVoiceManager.uidFor(p.userId)
                        val isSpeaking = agoraUid in speakingUids ||
                            (p.userId == userId && 0 in speakingUids && !isMuted)
                        val pMuted = p.isMuted || agoraUid in mutedUids
                        ZSCard(onClick = {
                            if (isAdmin && p.userId != userId) adminTarget = p
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ParticipantAvatar(
                                    speaking = isSpeaking,
                                    muted = pMuted,
                                    accent = if (p.isAdmin) ZsGold else ZsCyan
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        buildString {
                                            append(p.userName)
                                            if (p.userId == userId) append("  (you)")
                                            if (p.isAdmin) append("  👑")
                                        },
                                        color = ZsTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        buildString {
                                            if (isSpeaking) append("🗣️ speaking")
                                            else if (pMuted) append("🔇 muted")
                                            else append("🟢 online")
                                            if (p.isDeafened) append(" · 🔕 deafened")
                                            if (p.isHandRaised) append(" · ✋ hand raised")
                                            if (p.status != "ONLINE") append(
                                                " · " + p.status.lowercase().replace('_', ' ')
                                            )
                                        },
                                        color = ZsTextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // In-call chat panel
            if (showChat && isInCall) {
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
                        items(callChat, key = { it.id }) { line ->
                            Column {
                                Row {
                                    Text(
                                        line.senderName,
                                        color = ZsCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        formatTime(line.timestamp),
                                        color = ZsTextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(line.text, color = ZsTextPrimary, fontSize = 14.sp)
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
                        TextButton(onClick = { sendCallChat() }) {
                            Text("➤", color = ZsAccent, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs -------------------------------------------------------------

    AdminActionsHost(
        target = adminTarget,
        channelId = currentChannelId,
        onDismiss = { adminTarget = null }
    )

    if (showCreateDialog) {
        var channelName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create voice channel") },
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
                    showCreateDialog = false
                    if (name.isEmpty()) {
                        Toast.makeText(context, "Channel name required", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    val uid = userId ?: return@TextButton
                    db.collection("voice_channels").document(name).set(
                        mapOf(
                            "name" to name,
                            "active" to true,
                            "createdAt" to System.currentTimeMillis(),
                            "createdBy" to uid
                        )
                    ).addOnSuccessListener {
                        Toast.makeText(context, "Channel \"$name\" created", Toast.LENGTH_SHORT).show()
                    }.addOnFailureListener { e ->
                        Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Create", color = ZsAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showManageChannelsDialog) {
        AlertDialog(
            onDismissRequest = { showManageChannelsDialog = false },
            title = { Text("Manage voice channels") },
            text = {
                Column {
                    if (channels.isEmpty()) {
                        Text("No channels yet.", color = ZsTextMuted)
                    } else {
                        channels.forEach { ch ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    ch.name,
                                    Modifier.weight(1f),
                                    color = ZsTextPrimary,
                                    fontSize = 14.sp
                                )
                                TextButton(onClick = {
                                    db.collection("voice_channels")
                                        .document(ch.id)
                                        .update("active", !ch.active)
                                }) {
                                    Text(if (ch.active) "✅" else "🚫")
                                }
                                TextButton(onClick = {
                                    db.collection("voice_channels")
                                        .document(ch.id)
                                        .delete()
                                    if (currentChannelId == ch.id) leaveCall()
                                }) {
                                    Text("🗑️", color = ZsDanger)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageChannelsDialog = false }) { Text("Done", color = ZsAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showManageChannelsDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPermissionsDialog) {
        var players by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
        var loaded by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            db.collection("players")
                .whereEqualTo("status", "approved")
                .get()
                .addOnSuccessListener {
                    players = it.documents.filter { p -> p.id != userId }
                    loaded = true
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to load players", Toast.LENGTH_SHORT).show()
                    loaded = true
                }
        }
        AlertDialog(
            onDismissRequest = { showPermissionsDialog = false },
            title = { Text("Manage voice access") },
            text = {
                Column {
                    Text(
                        "Only players you enable can join voice channels.",
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
                                            Toast.makeText(
                                                context, "Failed to update", Toast.LENGTH_SHORT
                                            ).show()
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
                    showPermissionsDialog = false
                    Toast.makeText(context, "Voice access saved", Toast.LENGTH_SHORT).show()
                }) { Text("Done", color = ZsAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionsDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Admin per-player actions (mute / kick / ban)
// ---------------------------------------------------------------------------

@Composable
private fun AdminActionsHost(
    target: VoiceParticipant?,
    channelId: String?,
    onDismiss: () -> Unit
) {
    if (target == null || channelId == null) return
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    var confirmingBan by remember { mutableStateOf(false) }

    if (confirmingBan) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Ban ${target.userName} from voice?") },
            text = {
                Text(
                    "They will be removed from this call and blocked from joining any " +
                        "voice channel until you re-enable their access."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    db.collection("voice_channels").document(channelId)
                        .collection("participants").document(target.userId)
                        .delete()
                    db.collection("players").document(target.userId)
                        .update("voiceAllowed", false)
                        .addOnSuccessListener {
                            Toast.makeText(
                                context, "🚫 ${target.userName} banned from voice", Toast.LENGTH_LONG
                            ).show()
                        }
                    onDismiss()
                }) { Text("🚫 Ban", color = ZsDanger) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.userName) },
        text = {
            Column {
                TextButton(
                    onClick = {
                        db.collection("voice_channels").document(channelId)
                            .collection("participants").document(target.userId)
                            .update("muted", true)
                            .addOnSuccessListener {
                                Toast.makeText(context, "${target.userName} muted", Toast.LENGTH_SHORT).show()
                            }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("🔇 Mute player", color = ZsWarning) }

                TextButton(
                    onClick = {
                        db.collection("voice_channels").document(channelId)
                            .collection("participants").document(target.userId)
                            .delete()
                            .addOnSuccessListener {
                                Toast.makeText(context, "${target.userName} kicked", Toast.LENGTH_SHORT).show()
                            }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("👢 Kick from call", color = ZsCyan) }

                TextButton(
                    onClick = { confirmingBan = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("🚫 Ban from voice", color = ZsDanger) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun updateSelf(
    channelId: String?,
    userId: String?,
    db: FirebaseFirestore,
    mutate: (MutableMap<String, Any?>) -> Unit
) {
    if (channelId == null || userId == null) return
    val data = mutableMapOf<String, Any?>()
    mutate(data)
    if (data.isEmpty()) return
    db.collection("voice_channels")
        .document(channelId)
        .collection("participants")
        .document(userId)
        .update(data)
}

// ---------------------------------------------------------------------------
// Small UI pieces
// ---------------------------------------------------------------------------

@Composable
private fun ParticipantAvatar(
    speaking: Boolean,
    muted: Boolean,
    accent: Color
) {
    val ringColor = when {
        speaking -> ZsGreen
        muted -> ZsTextMuted
        else -> accent.copy(alpha = 0.4f)
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .border(
                width = if (speaking) 2.5.dp else 1.5.dp,
                color = ringColor,
                shape = CircleShape
            )
            .background(ZsCard, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(if (muted) "🔇" else "🎙️", fontSize = 16.sp)
    }
}

@Composable
private fun VoiceChip(
    emoji: String,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val container = if (active) ZsCyan else ZsCard
    val content = if (active) Color(0xFF06251D) else ZsTextPrimary
    Column(
        modifier = Modifier
            .background(container, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 18.sp)
        Text(label, color = content, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        else -> "${diff / 3_600_000}h ago"
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return java.lang.String.format(
        java.util.Locale.getDefault(),
        "%02d:%02d:%02d",
        hours,
        minutes,
        seconds
    )
}
