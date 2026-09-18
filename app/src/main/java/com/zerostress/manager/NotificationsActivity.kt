package com.zerostress.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.zerostress.manager.ui.EmptyState
import com.zerostress.manager.ui.ZsPngIcon
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.formatDateTime
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsBorder
import com.zerostress.manager.ui.theme.ZsCard
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import com.zerostress.manager.R

/**
 * Notification center.
 *
 * - Targeted + broadcast notifications, newest first, filtered per user.
 * - Type filter chips: All / Mentions / Chat / Schedules / Admin.
 * - Unread dot + "Mark all read": read state lives on the player doc
 *   (`lastSeenNotifTs`) so it syncs across devices.
 * - Tap a notification to open its related screen (deep link by type).
 * - Per-notification delete (own copy) + Clear all (visible list only).
 */
class NotificationsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                NotificationsScreen()
            }
        }
    }

    companion object {
        /** Opens the right screen for a notification type. */
        fun targetFor(type: String): Class<*> = when (type) {
            "chat", "mention" -> ChatActivity::class.java
            "schedule" -> ScheduleActivity::class.java
            "achievement" -> AchievementsActivity::class.java
            else -> PlayerDashboardActivity::class.java
        }
    }
}

@Composable
private fun NotificationsScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().uid

    var notifications by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var filter by remember { mutableIntStateOf(0) } // 0 all, 1 mention, 2 chat, 3 schedule, 4 admin
    var lastSeenTs by remember { mutableStateOf(0L) }

    // Live feed: broadcasts + anything addressed to this user.
    DisposableEffect(Unit) {
        val listener = db.collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                notifications = snap?.documents.orEmpty().filter {
                    it.getString("uid") == null || it.getString("uid") == uid
                }
            }
        onDispose { listener.remove() }
    }

    // Read state: everything at or before lastSeenNotifTs counts as read.
    DisposableEffect(Unit) {
        if (uid == null) return@DisposableEffect onDispose { }
        val listener = db.collection("players").document(uid)
            .addSnapshotListener { doc, _ ->
                lastSeenTs = doc?.getLong("lastSeenNotifTs") ?: 0L
            }
        onDispose { listener.remove() }
    }

    val filters = listOf("All", "Mentions", "Chat", "Schedules", "Admin")
    val visible = notifications.filter { doc ->
        val t = doc.getString("type") ?: "general"
        when (filter) {
            0 -> true
            1 -> t == "mention"
            2 -> t == "chat"
            3 -> t == "schedule"
            else -> t == "admin" || t == "achievement"
        }
    }
    val unreadCount = notifications.count {
        (it.getLong("timestamp") ?: 0L) > lastSeenTs
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Notifications",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            // Filter chips
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEachIndexed { i, label ->
                    val selected = filter == i
                    Text(
                        label,
                        color = if (selected) Color.White else ZsTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                color = if (selected) ZsPrimary else ZsCard,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(1.dp, ZsBorder.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .clickable { filter = i }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Unread banner + mark all read
            if (unreadCount > 0) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$unreadCount unread",
                        color = ZsPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Mark all read",
                        color = ZsTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                if (uid != null) {
                                    db.collection("players").document(uid)
                                        .update("lastSeenNotifTs", System.currentTimeMillis())
                                }
                            }
                            .padding(6.dp)
                    )
                }
            }

            // Clear all: deletes only THIS user's targeted copies (broadcasts
            // are shared, so they are managed by admins).
            val deletable = visible.filter { it.getString("uid") != null }
            if (deletable.isNotEmpty()) {
                Text(
                    "Clear all",
                    color = ZsTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            deletable.forEach { it.reference.delete() }
                        }
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (visible.isEmpty()) {
                EmptyState(if (notifications.isEmpty()) "No notifications yet" else "Nothing in this filter")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { doc ->
                        NotificationRow(
                            doc = doc,
                            isUnread = (doc.getLong("timestamp") ?: 0L) > lastSeenTs,
                            canDelete = doc.getString("uid") != null,
                            onClick = {
                                val type = doc.getString("type") ?: "general"
                                context.startActivity(
                                    android.content.Intent(context, NotificationsActivity.targetFor(type))
                                )
                            },
                            onDelete = { doc.reference.delete() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    doc: DocumentSnapshot,
    isUnread: Boolean,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val type = doc.getString("type") ?: "general"
    val typeLabel = when (type) {
        "chat" -> "Chat"
        "mention" -> "Mention"
        "schedule" -> "Match"
        "achievement" -> "Reward"
        "admin" -> "Admin"
        else -> "Alert"
    }
    val highlight = if (isUnread) ZsPrimary else ZsBorder.copy(alpha = 0.5f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        ZSCard(
            modifier = Modifier.weight(1f),
            highlight = highlight,
            onClick = onClick
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    typeLabel.uppercase(),
                    color = if (isUnread) ZsPrimary else ZsTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = 1.sp
                )
                if (isUnread) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(ZsPrimary, RoundedCornerShape(50))
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                doc.getString("title") ?: "Notification",
                color = ZsTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                doc.getString("message") ?: "",
                color = ZsTextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                formatDateTime(doc.getLong("timestamp") ?: 0L),
                color = ZsTextMuted,
                fontSize = 11.sp
            )
        }
        if (canDelete) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onDelete) {
                ZsPngIcon(R.drawable.ic_action_delete, size = 18.dp, tint = ZsTextMuted)
            }
        }
    }
}
