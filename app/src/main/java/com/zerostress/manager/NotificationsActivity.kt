package com.zerostress.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.zerostress.manager.ui.EmptyState
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.formatDateTime
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

class NotificationsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                NotificationsScreen()
            }
        }
    }
}

@Composable
private fun NotificationsScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    var notifications by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }

    DisposableEffect(Unit) {
        val listener = db.collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                notifications = snap?.documents.orEmpty()
            }
        onDispose { listener.remove() }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Notifications",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            if (notifications.isEmpty()) {
                EmptyState("No notifications yet")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications, key = { it.id }) { doc ->
                        val type = doc.getString("type") ?: "general"
                        val typeLabel = when (type) {
                            "chat" -> "Chat"
                            "mention" -> "Mention"
                            "admin" -> "Admin"
                            else -> "Alert"
                        }
                        ZSCard(highlight = ZsCyan) {
                            Text(
                                "$typeLabel: ${doc.getString("title") ?: "Notification"}",
                                color = ZsTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.height(4.dp))
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
                    }
                }
            }
        }
    }
}