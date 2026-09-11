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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.zerostress.manager.VoiceCallPeer
import com.zerostress.manager.VoiceCallSignaling

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

private data class VoiceParticipant(
    val userId: String,
    val userName: String,
    val isMuted: Boolean,
    val isDeafened: Boolean,
    val isSpeaking: Boolean,
    val isHandRaised: Boolean,
    val status: String
)

private data class VoiceChatLine(
    val senderName: String,
    val text: String,
    val timestamp: Long
)

@Composable
private fun VoiceScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val userId = auth.uid

    var userName by remember { mutableStateOf("Unknown") }
    var isAdmin by remember { mutableStateOf(false) }
    var isInCall by remember { mutableStateOf(false) }
    var currentChannelId by remember { mutableStateOf<String?>(null) }
    var currentChannelName by remember { mutableStateOf("No channel") }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var participants by remember { mutableStateOf<List<VoiceParticipant>>(emptyList()) }
    var callChat by remember { mutableStateOf<List<VoiceChatLine>>(emptyList()) }
    var chatText by remember { mutableStateOf("") }
    var showChat by remember { mutableStateOf(false) }
    var showChannelPicker by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isDeafened by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isHandRaised by remember { mutableStateOf(false) }
    var micGranted by remember { mutableStateOf(false) }

    var activeCall by remember { mutableStateOf(false) }
    var callView by remember { mutableStateOf<VoiceCallPeer.CallView?>(null) }
    var remoteUid by remember { mutableStateOf<String?>(null) }
    var callStateText by remember { mutableStateOf("Not in call") }
    var pendingRemoteUid by remember { mutableStateOf<String?>(null) }
    val callJob = remember { AtomicBoolean(false) }

    val micLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            micGranted = granted
            if (granted) {
                if (currentChannelId != null) {
                    findAndStartCall(userId!!, currentChannelId!!)
                } else {
                    showChannelPicker = true
                }
            } else {
                Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
            }
        }

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
            .addOnFailureListener {
                Toast.makeText(context, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
    }

    LaunchedEffect(isInCall) {
        if (isInCall) {
            val start = System.currentTimeMillis()
            while (isInCall) {
                elapsedSeconds = (System.currentTimeMillis() - start) / 1000
                delay(1000)
            }
        }
        callStateText = if (activeCall) "In call" else "Not in call"
    }

    DisposableEffect(currentChannelId) {
        val channelId = currentChannelId
        if (channelId != null) {
            val participantListener = db.collection("voice_channels")
                .document(channelId)
                .collection("participants")
                .addSnapshotListener { snap, e ->
                    if (e != null || snap == null) return@addSnapshotListener
                    participants = snap.documents.map { doc ->
                        VoiceParticipant(
                            userId = doc.id,
                            userName = doc.getString("userName") ?: "Unknown",
                            isMuted = doc.getBoolean("muted") == true,
                            isDeafened = doc.getBoolean("deafened") == true,
                            isSpeaking = doc.getBoolean("speaking") == true,
                            isHandRaised = doc.getBoolean("handRaised") == true,
                            status = doc.getString("userStatus") ?: "ONLINE"
                        )
                    }
                }
            val chatListener = db.collection("voice_channels")
                .document(currentChannelId)
                .collection("call_chat")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .addSnapshotListener { snap, e ->
                    if (e != null || snap == null) return@addSnapshotListener
                    callChat = snap.documents.map { doc ->
                        VoiceChatLine(
                            senderName = doc.getString("senderName") ?: "Unknown",
                            text = doc.getString("text") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    }
                }
            DisposableEffect(Unit) {
                onDispose {
                    participantListener.remove()
                    chatListener.remove()
                }
            }
        }
        onDispose { }
    }

    fun joinChannel(channelId: String, channelName: String) {
        val uid = userId ?: return
        currentChannelId = channelId
        currentChannelName = channelName
        db.collection("voice_channels")
            .document(channelId)
            .collection("participants")
            .document(uid)
            .set(
                mapOf(
                    "userId" to uid,
                    "userName" to userName,
                    "joinedAt" to System.currentTimeMillis(),
                    "muted" to false,
                    "deafened" to false,
                    "speaking" to false,
                    "handRaised" to false,
                    "isAdmin" to isAdmin,
                    "userStatus" to "ONLINE"
                )
            )
            .addOnSuccessListener {
                isInCall = true
                Toast.makeText(context, "Joined $channelName", Toast.LENGTH_SHORT).show()
                // Start the live voice call with the first other participant found
                findAndStartCall(uid, channelId)
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to join: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun leaveCall() {
        if (activeCall) {
            val view = callView
            if (view != null) {
                try {
                    VoiceCallPeer.close(view)
                } catch (_: Throwable) {}
            }
            VoiceCallPeer.stopLocalMedia()
            activeCall = false
            callView = null
            remoteUid = null
            pendingRemoteUid = null
            callStateText = "Not in call"
        }
        currentChannelId = null
        currentChannelName = "No channel"
        isInCall = false
        isMuted = false
        isDeafened = false
        isSpeaking = false
        isHandRaised = false
        elapsedSeconds = 0
        participants = emptyList()
        callChat = emptyList()
    }

    fun sendCallChat() {
        val text = chatText.trim()
        val channelId = currentChannelId ?: return
        val uid = userId ?: return
        if (text.isEmpty()) return
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
        val statuses = listOf(
            "ONLINE" to "🟢 Online",
            "IDLE" to "🟡 Idle",
            "DO_NOT_DISTURB" to "🔴 Do not disturb"
        )
        val channelId = currentChannelId
        val uid = userId
        if (channelId == null || uid == null) {
            Toast.makeText(context, "Not in a call", Toast.LENGTH_SHORT).show()
            return
        }
        var currentCode: String? = null
        val listen = db.collection("voice_channels")
            .document(channelId)
            .collection("participants")
            .document(uid)
            .addListener { doc -> currentCode = doc.getString("userStatus") }
        val currentIndex = currentCode?.let { code -> statuses.indexOfFirst { it.first == code } } ?: 0
        val next = (currentIndex + 1) % statuses.size
        val (code, label) = statuses[next]
        listen.remove()
        db.collection("players").document(uid).update("status", code)
            .addOnFailureListener {
                Toast.makeText(context, "Failed to update status", Toast.LENGTH_SHORT).show()
            }
        db.collection("voice_channels")
            .document(channelId)
            .collection("participants")
            .document(uid)
            .update("userStatus", code)
            .addOnFailureListener {
                Toast.makeText(context, "Failed to update call status", Toast.LENGTH_SHORT).show()
            }
        Toast.makeText(context, "Status set to $label", Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(Unit) {
        onDispose {
            leaveCall()
        }
    }

    fun findAndStartCall(uid: String, channelId: String) {
        if (!callJob.compareAndSet(false, true)) return
        scope.launch {
            try {
                VoiceCallPeer.startLocalMedia(context)
            } catch (_: Throwable) {}
            val others = try {
                db.collection("voice_channels")
                    .document(channelId)
                    .collection("participants")
                    .whereNotEqualTo("userId", uid)
                    .get()
                    .await()
                    .documents
            } catch (_: Throwable) {
                emptyList()
            }
            val target = others.firstOrNull()
            if (target != null) {
                remoteUid = target.id
                pendingRemoteUid = remoteUid
                startCallWithRemote(uid, channelId, target.id)
            } else {
                callStateText = "No other participants"
                callJob.set(false)
            }
        }
    }

    suspend fun startCallWithRemote(localUid: String, channelId: String, remoteUid: String) {
        this.remoteUid = remoteUid
        pendingRemoteUid = null
        val view = VoiceCallPeer.createCall(context, db, channelId, localUid, remoteUid)
            ?: run {
                callStateText = "Call setup failed"
                return@startCallWithRemote
            }
        callView = view

        VoiceCallPeer.createOffer(view)
        voiceCallSignaling = VoiceCallSignaling(channelId, localUid, remoteUid, db).apply {
            startListening()
        }
        activeCall = true
        callStateText = "Starting call…"

        scope.launch {
            try {
                while (activeCall) {
                    val answer = try {
                        voiceCallSignaling?.waitForAnswer()
                    } catch (_: Throwable) {
                        null
                    }
                    if (answer != null) {
                        VoiceCallPeer.setRemoteAnswer(callView!!, answer)
                        callStateText = "In call"
                        break
                    }
                    delay(200)
                }
            } catch (e: Throwable) {
                Log.w(VoiceActivityTag, "answer wait failed", e)
                callStateText = "Call failed"
            } finally {
                if (!activeCall) {
                    callJob.set(false)
                }
            }
        }

        scope.launch {
            try {
                while (activeCall) {
                    val ice = try {
                        voiceCallSignaling?.waitForRemoteIce()
                    } catch (_: Throwable) {
                        null
                    }
                    if (ice != null) {
                        VoiceCallPeer.addRemoteIceCandidate(callView!!, ice)
                    } else {
                        delay(100)
                    }
                }
            } catch (e: Throwable) {
                Log.w(VoiceActivityTag, "remote ice loop failed", e)
            }
        }
    }

    private fun startVoiceCall() {
        findAndStartCall(userId!!, currentChannelId!!)
    }

    var voiceCallSignaling: VoiceCallSignaling? by remember { mutableStateOf(null) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Voice Call",
                onBack = { (context as? android.app.Activity)?.finish() },
                right = {
                    if (isAdmin) {
                        TextButton(onClick = { showCreateDialog = true }) {
                            Text("➕", color = ZsGold, fontSize = 20.sp)
                        }
                        TextButton(onClick = { showPermissionsDialog = true }) {
                            Text("👥", color = ZsCyan)
                        }
                    }
                }
            )

            ZSCard(
                highlight = if (isInCall) ZsGreen else ZsTextMuted
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isInCall) "🟢 In call" else "🔴 Not in call",
                            color = if (isInCall) ZsGreen else ZsTextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Call: $currentChannelName",
                            color = ZsTextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Duration: ${formatDuration(elapsedSeconds)} · ${participants.size} participants",
                            color = ZsTextMuted,
                            fontSize = 12.sp
                        )
                    }
                    ZSButton(
                        text = if (isInCall) "Leave call" else "Join call",
                        onClick = {
                            if (isInCall) {
                                leaveCall()
                            } else if (micGranted) {
                                if (currentChannelId != null) {
                                    findAndStartCall(userId!!, currentChannelId!!)
                                } else {
                                    showChannelPicker = true
                                }
                            } else {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        container = if (isInCall) ZsDanger else ZsAccent,
                        modifier = Modifier.width(150.dp)
                    )
                }
            }

            if (isInCall) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    VoiceChip("🔇", "Mute", isMuted, { isMuted = !isMuted })
                    VoiceChip("🔕", "Deafen", isDeafened, { isDeafened = !isDeafened })
                    VoiceChip("🗣️", "Talking", isSpeaking, { isSpeaking = !isSpeaking })
                    VoiceChip("✋", "Hand", isHandRaised, { isHandRaised = !isHandRaised })
                }
            }

            Column(Modifier.weight(1f).fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Participants (${participants.size})",
                        Modifier.weight(1f),
                        color = ZsTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showChat = !showChat }) {
                        Text(if (showChat) "Hide chat" else "Chat", color = ZsCyan)
                    }
                    TextButton(onClick = { showMoreOptions = true }) {
                        Text("⋯", color = ZsTextSecondary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }

                if (participants.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        Alignment.Center
                    ) {
                        Text("No one is in this call yet", color = ZsTextMuted, fontSize = 15.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(participants, key = { it.userId }) { participant ->
                            ZSCard(
                                onClick = {
                                    if (isAdmin && participant.userId != userId) {
                                        val channelId = currentChannelId ?: return@ZSCard
                                        db.collection("voice_channels")
                                            .document(channelId)
                                            .collection("participants")
                                            .document(participant.userId)
                                            .update("muted", true)
                                            .addOnSuccessListener {
                                                Toast.makeText(context, "${participant.userName} muted", Toast.LENGTH_SHORT).show()
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(context, "Failed to mute", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            buildString {
                                                if (participant.isSpeaking) append("🗣️ ")
                                                append(participant.userName)
                                            },
                                            color = ZsTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            buildString {
                                                if (participant.isMuted) append("🔇 ")
                                                if (participant.isDeafened) append("🔕 ")
                                                if (participant.isHandRaised) append("✋ ")
                                                if (participant.status != "ONLINE") append("${participant.status.lowercase().replaceFirstChar { it.uppercase() }} ")
                                                if (!participant.isMuted && !participant.isHandRaised && participant.status == "ONLINE") append("🟢")
                                            },
                                            color = ZsTextMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                    if (participant.userId == userId) {
                                        Text("(you)", color = ZsCyan, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                        items(callChat) { line ->
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

    if (showChannelPicker) {
        var channels by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
        LaunchedEffect(Unit) {
            if (userId == null) return@LaunchedEffect
            db.collection("voice_channels")
                .whereEqualTo("active", true)
                .get()
                .addOnSuccessListener { query -> channels = query.documents }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to load channels", Toast.LENGTH_SHORT).show()
                }
        }
        AlertDialog(
            onDismissRequest = { showChannelPicker = false },
            title = { Text("Select voice channel") },
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
                    } else {
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
                    }
                }) { Text("Create", color = ZsAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
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
                    players = it.documents.filter { it.id != userId }
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
                    Text("Only players you enable can join voice channels.", color = ZsTextMuted, fontSize = 12.sp)
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
                    showPermissionsDialog = false
                    Toast.makeText(context, "Voice access saved", Toast.LENGTH_SHORT).show()
                }) { Text("Done", color = ZsAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionsDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showMoreOptions) {
        AlertDialog(
            onDismissRequest = { showMoreOptions = false },
            title = { Text("More options") },
            text = {
                Column {
                    listOf("Set status", "My profile", "Call info", "Report").forEach { option ->
                        TextButton(
                            onClick = {
                                showMoreOptions = false
                                when (option) {
                                    "Set status" -> cycleStatus()
                                    "My profile" -> showProfile(context, db, userId)
                                    "Call info" -> Toast.makeText(
                                        context,
                                        "Call: $currentChannelName\nDuration: ${formatDuration(elapsedSeconds)}\nParticipants: ${participants.size}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    "Report" -> Toast.makeText(context, "Use the report flow in settings", Toast.LENGTH_SHORT).show()
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
}    private fun showProfile(
        context: android.content.Context,
        db: FirebaseFirestore,
        userId: String?
    ) {
        if (userId == null) {
            Toast.makeText(context, "Not signed in", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection("players").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    Toast.makeText(
                        context,
                        "Name: ${doc.getString("name")}\nLevel: ${doc.getLong("level") ?: 1}\nRank: ${doc.getString("rank") ?: "Unknown"}",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(context, "Profile not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun setStatusCycle(
        context: android.content.Context,
        _db: FirebaseFirestore,
        userId: String?,
        currentChannelId: String?
    ) {
        val statuses = listOf(
            "ONLINE" to "🟢 Online",
            "IDLE" to "🟡 Idle",
            "DO_NOT_DISTURB" to "🔴 Do not disturb"
        )
        if (currentChannelId == null || userId == null) {
            Toast.makeText(context, "Not in a call", Toast.LENGTH_SHORT).show()
            return
        }
        var currentCode: String? = null
        try {
            currentCode = _db.collection("voice_channels")
                .document(currentChannelId)
                .collection("participants")
                .document(userId)
                .get()
                .await()
                .getString("userStatus")
        } catch (_: Throwable) {}
        val currentIndex = currentCode?.let { code -> statuses.indexOfFirst { it.first == code } } ?: 0
        val next = (currentIndex + 1) % statuses.size
        val (code, label) = statuses[next]
        try {
            _db.collection("players").document(userId).update("status", code)
            _db.collection("voice_channels")
                .document(currentChannelId)
                .collection("participants")
                .document(userId)
                .update("userStatus", code)
        } catch (_: Throwable) {}
        Toast.makeText(context, "Status set to $label", Toast.LENGTH_SHORT).show()
    }

    @Composable
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
    return java.util.String.format(
        java.util.Locale.getDefault(),
        "%02d:%02d:%02d",
        hours,
        minutes,
        seconds
    )
}

@Composable
private fun VoiceChip(
    emoji: String,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val container = if (active) ZsCyan else ZsCard
    val content = if (active) Color.Companion(0xFF06251D) else ZsTextPrimary
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
