package com.zerostress.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.LoadingBox
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSKeyValue
import com.zerostress.manager.ui.ZSStat
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import java.util.Locale

class PerformanceGraphsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                PerformanceGraphsScreen()
            }
        }
    }
}

@Composable
private fun PerformanceGraphsScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = FirebaseAuth.getInstance().uid

    var loading by remember { mutableStateOf(true) }
    var kd by remember { mutableStateOf(0.0) }
    var winRate by remember { mutableStateOf(0.0) }
    var avgDamage by remember { mutableStateOf(0.0) }
    var avgKills by remember { mutableStateOf(0.0) }
    var matches by remember { mutableStateOf(0L) }
    var kills by remember { mutableStateOf(0L) }
    var deaths by remember { mutableStateOf(0L) }
    var wins by remember { mutableStateOf(0L) }
    var damage by remember { mutableStateOf(0L) }
    var level by remember { mutableStateOf(1L) }
    var xp by remember { mutableStateOf(0L) }
    var coins by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        if (userId != null) {
            db.collection("players").document(userId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        kills = doc.getLong("kills") ?: 0
                        deaths = doc.getLong("deaths") ?: 0
                        wins = doc.getLong("wins") ?: 0
                        matches = doc.getLong("matches") ?: 0
                        damage = doc.getLong("damage") ?: 0
                        xp = doc.getLong("xp") ?: 0
                        level = doc.getLong("level") ?: 1
                        coins = doc.getLong("coins") ?: 0
                        kd = if (deaths > 0) kills.toDouble() / deaths else kills.toDouble()
                        winRate = if (matches > 0) wins * 100.0 / matches else 0.0
                        avgDamage = if (matches > 0) damage.toDouble() / matches else 0.0
                        avgKills = if (matches > 0) kills.toDouble() / matches else 0.0
                    }
                    loading = false
                }
        } else {
            loading = false
        }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Performance Stats",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            if (loading) {
                LoadingBox()
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                    ) {
                        ZSStat("K/D", String.format(Locale.getDefault(), "%.2f", kd), ZsGold, Modifier.weight(1f))
                        ZSStat("Win Rate", String.format(Locale.getDefault(), "%.1f%%", winRate), ZsAccent, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                    ) {
                        ZSStat("Avg DMG", String.format(Locale.getDefault(), "%.0f", avgDamage), ZsCyan, Modifier.weight(1f))
                        ZSStat("Avg Kills", String.format(Locale.getDefault(), "%.1f", avgKills), ZsPrimary, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                    ZSCard {
                        ZSKeyValue("Total Matches", "$matches")
                        Spacer(Modifier.height(8.dp))
                        ZSKeyValue("Total Kills", "$kills")
                        Spacer(Modifier.height(8.dp))
                        ZSKeyValue("Total Deaths", "$deaths")
                        Spacer(Modifier.height(8.dp))
                        ZSKeyValue("Total Wins", "$wins")
                        Spacer(Modifier.height(8.dp))
                        ZSKeyValue("Total Damage", "$damage")
                    }
                    Spacer(Modifier.height(12.dp))
                    ZSCard {
                        ZSKeyValue("Level", "$level", ZsCyan)
                        Spacer(Modifier.height(8.dp))
                        ZSKeyValue("XP", "$xp")
                        Spacer(Modifier.height(8.dp))
                        ZSKeyValue("Coins", "$coins", ZsGold)
                    }
                }
            }
        }
    }
}