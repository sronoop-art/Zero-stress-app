package com.zerostress.manager

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsChatReceived
import com.zerostress.manager.ui.theme.ZsChatSentEnd
import com.zerostress.manager.ui.theme.ZsChatSentStart
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Direct messages between two players.
 *
 * Data model:
 *  - dm_threads/{uidA_uidB}  (uids sorted ascending; see threadIdFor)
 *      fields: participants: [uidA, uidB], names: {uid: name},
 *              lastMessage: String, lastSenderId, lastAt: Long
 *  - dm_threads/{id}/messages/{msgId}
 *      fields: senderId, text, timestamp
 *
 * Rules: both participants read/write their own thread; creation is guarded
 * so a thread always carries exactly the two real participants (see
 * firestore.rules - dm_threads section added in this same change).
 *
 * Push: sending writes a notifications/{id} doc with uid = the other player
 * (type "dm") - the existing relay/bridge/function push pipeline delivers it
 * and the tap routes here via ZSFCMService's "dm" type.
 */
class DirectMessageActivity : ComponentActivity() {

    companion object {
        /** Deterministic, symmetric thread id: smaller uid first. */
        fun threadIdFor(a: String, b: String): String =
            if (a <= b) "${a}_${b}" else "${b}_${a}"

        /** Open (or create) the DM thread with [otherUid] from any screen. */
        fun launch(context: android.content.Context, otherUid: String, otherName: String) {
            context.startActivity(
                Intent(context, DirectMessageActivity::class.java).apply {
                    putExtra("peerUid", otherUid)
                    putExtra("peerName", otherName)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                DmScreen()
            }
        }
    }
}

@Composable
private fun DmScreen() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val myUid = auth.uid

    val peerUid = remember {
        (context as? android.app.Activity)?.intent?.getStringExtra("peerUid") ?: ""
    }
    val peerName = remember {
        (context as? android.app.Activity)?.intent?.getStringExtra("peerName") ?: "Player"
    }

    var messages by remember { mutableStateOf<List<com.google.firebase.firestore.DocumentSnapshot>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Auto-scroll to the newest message as they arrive.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    DisposableEffect(myUid, peerUid) {
        if (myUid == null || peerUid.isBlank()) {
            onDispose { }
        } else {
            val threadId = DirectMessageActivity.threadIdFor(myUid, peerUid)
            val reg = db.collection("dm_threads").document(threadId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limitToLast(200)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        Log.w("DirectMessage", "thread listener: ${err.message}")
                        return@addSnapshotListener
                    }
                    messages = snap?.documents ?: emptyList()
                }
            onDispose { reg.remove() }
        }
    }

    fun send() {
        val me = myUid
        if (me == null || peerUid.isBlank()) return
        val t = text.trim()
        if (t.isEmpty() || sending) return
        sending = true
        val threadId = DirectMessageActivity.threadIdFor(me, peerUid)
        val threadRef = db.collection("dm_threads").document(threadId)
        val now = System.currentTimeMillis()

        val msg = mapOf(
            "senderId" to me,
            "text" to t,
            "timestamp" to now
        )
        val threadMeta = mapOf(
            "participants" to listOf(me, peerUid),
            "names" to mapOf(me to (auth.currentUser?.email?.substringBefore("@") ?: me)),
            "lastMessage" to t,
            "lastSenderId" to me,
            "lastAt" to now
        )
        // Thread metadata upsert + message write in one atomic batch, then the
        // push notification doc for the recipient (relay/bridge deliver it).
        db.runBatch { batch ->
            batch.set(threadRef, threadMeta, com.google.firebase.firestore.SetOptions.merge())
            batch.set(threadRef.collection("messages").document(), msg)
            batch.set(
                db.collection("notifications").document(),
                mapOf(
                    "uid" to peerUid,
                    "title" to "New direct message",
                    "message" to t,
                    "type" to "dm",
                    "timestamp" to now,
                    "senderId" to me,
                    "threadId" to threadId
                )
            )
        }.addOnSuccessListener {
            text = ""
            sending = false
        }.addOnFailureListener { e ->
            sending = false
            Toast.makeText(context, "Send failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = peerName,
                onBack = { (context as? android.app.Activity)?.finish() }
            )
            if (myUid == null || peerUid.isBlank()) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(80.dp))
                    Text(
                        "This conversation is unavailable",
                        color = ZsTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp, vertical = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { doc ->
                    val mine = doc.getString("senderId") == myUid
                    val t = doc.getString("text") ?: ""
                    val ts = doc.getLong("timestamp") ?: 0L
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            Modifier
                                .widthIn(max = 300.dp)
                                .clip(RoundedCornerShape(
                                    topStart = 16.dp, topEnd = 16.dp,
                                    bottomStart = if (mine) 16.dp else 4.dp,
                                    bottomEnd = if (mine) 4.dp else 16.dp
                                ))
                                .background(
                                    if (mine) androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(ZsChatSentStart, ZsChatSentEnd)
                                    ) else androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(ZsChatReceived, ZsChatReceived)
                                    )
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                t,
                                color = if (mine) Color(0xFF04101A) else ZsTextPrimary,
                                fontSize = 15.sp
                            )
                            Text(
                                time,
                                color = if (mine) Color(0xFF04101A).copy(alpha = 0.6f) else ZsTextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ZSField(
                    value = text,
                    onValueChange = { text = it },
                    label = "",
                    placeholder = "Message…",
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                ZSButton(
                    text = if (sending) "…" else "Send",
                    onClick = { send() },
                    modifier = Modifier.width(92.dp),
                    enabled = !sending && text.isNotBlank(),
                    height = 46.dp
                )
            }
            }
        }
    }
}


