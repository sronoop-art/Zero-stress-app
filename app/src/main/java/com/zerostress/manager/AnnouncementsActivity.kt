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

class AnnouncementsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                AnnouncementsScreen()
            }
        }
    }
}

@Composable
private fun AnnouncementsScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    var announcements by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }

    DisposableEffect(Unit) {
        val listener = db.collection("announcements").orderBy("timestamp").limit(50)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) return@addSnapshotListener
                announcements = snap.documents
            }
        onDispose { listener.remove() }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Announcements",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            if (announcements.isEmpty()) {
                EmptyState("No announcements yet")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(announcements, key = { it.id }) { doc ->
                        ZSCard(highlight = ZsCyan) {
                            Text(
                                doc.getString("text") ?: "",
                                color = ZsTextPrimary,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${doc.getString("author") ?: "Admin"} • ${
                                    formatDateTime(doc.getLong("timestamp") ?: 0L)
                                }",
                                color = ZsTextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}